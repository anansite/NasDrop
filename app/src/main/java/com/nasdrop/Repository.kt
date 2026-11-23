package com.nasdrop

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class RouteInfo(
	val isDirect: Boolean,     // 是否为 P2P 直连打洞或局域网直连
	val isRelay: Boolean,      // 是否为 DERP 中继代理模式
	val endpoint: String,      // 对端打洞端点或中继节点（如 "39.163.49.110:20045"）
	val peerName: String,      // 对端主机名（如 "istoreos" 或 "LAN"）
	val label: String,         // 状态标签
	val latencyMs: Long = -1L,
	val isProbing: Boolean = false,
	val isLanDirect: Boolean = false,
) {
	val isLan: Boolean
		get() = isLanDirect || (isDirect && endpoint.isEmpty())
}

/**
 * 全局唯一的网络出口。
 *
 * 识别 Tailscale P2P 直连打洞 vs DERP 中继，并支持主动触发 NAT 打洞。
 */
class Repository(private val context: Context) {

	private val _routeInfo = MutableStateFlow(
		RouteInfo(
			isDirect = false,
			isRelay = false,
			endpoint = "",
			peerName = "",
			label = "正在连接…",
			latencyMs = -1L,
			isProbing = true,
		),
	)
	val routeInfo: StateFlow<RouteInfo> = _routeInfo

	@Volatile
	private var authHeader: String? = null

	@Volatile
	private var bridgeToken: String = ""

	@Volatile
	private var cachedKey: Settings? = null

	@Volatile
	private var cached: WebDavClient? = null

	@Volatile
	private var authOrigin: HttpUrl? = null

	/** 验证与放行证书的连接池隔离，关闭放行后不复用之前未校验证书的连接。 */
	private val verifiedHttp: OkHttpClient = OkHttpClient.Builder()
		.dispatcher(Dispatcher().apply {
			maxRequests = 128
			maxRequestsPerHost = 64
		})
		.connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
		.addInterceptor { chain ->
			val b = chain.request().newBuilder()
			val url = chain.request().url
			val origin = authOrigin
			val isBridge = url.isBridgeOrigin(TsBridge.port)
			val isServer = url.matchesOrigin(origin)
			// 仅向已配置服务或本地桥注入凭据，避免外部图片/签名下载地址收到账号信息。
			if ((isServer || isBridge) && chain.request().header("Authorization").isNullOrBlank()) {
				authHeader?.let { b.header("Authorization", it) }
			}
			if (isBridge && bridgeToken.isNotEmpty()) b.header("X-Bridge-Token", bridgeToken)
			chain.proceed(b.build())
		}
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(5, TimeUnit.MINUTES)
		.writeTimeout(5, TimeUnit.MINUTES)
		.build()

	private val permissiveHttp by lazy { verifiedHttp.newBuilder().withCertificatePolicy(true).build() }

	@Volatile private var trustAllCerts = false
	private val okHttp: OkHttpClient get() = if (trustAllCerts) permissiveHttp else verifiedHttp
	private val verifiedProbe = verifiedHttp.newBuilder().connectTimeout(1200, TimeUnit.MILLISECONDS).readTimeout(1200, TimeUnit.MILLISECONDS).build()
	private val permissiveProbe by lazy { permissiveHttp.newBuilder().connectTimeout(1200, TimeUnit.MILLISECONDS).readTimeout(1200, TimeUnit.MILLISECONDS).build() }
	private val probeHttp: OkHttpClient get() = if (trustAllCerts) permissiveProbe else verifiedProbe

	private fun imageClient(client: OkHttpClient): OkHttpClient = client.newBuilder()
			.dispatcher(Dispatcher().apply {
				maxRequests = 64
				maxRequestsPerHost = 24
			})
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(45, TimeUnit.SECONDS)
			.addNetworkInterceptor { chain ->
				val response = chain.proceed(chain.request())
				if (response.isSuccessful) {
					// 突破带 Authorization 导致 RFC 强制禁止磁盘缓存的限制：
					// 将响应头改写为允许客户端本地磁盘缓存 7 天，确保打开过的图片与缩略图真正落盘，避免重复网络下载
					response.newBuilder()
						.removeHeader("Pragma")
						.removeHeader("Cache-Control")
						.header("Cache-Control", "public, max-age=604800")
						.build()
				} else {
					response
				}
			}
			.build()
	private val verifiedImage by lazy { imageClient(verifiedHttp) }
	private val permissiveImage by lazy { imageClient(permissiveHttp) }

	val imageLoader: ImageLoader by lazy {
		ImageLoader.Builder(context)
			// 缩略图统一走服务端生成（OpenList /d/...?type=thumb，真 PNG）；
			// 不再注册 VideoFrameDecoder——避免对远程大视频整段下载到磁盘再解码，拖垮列表与流量。
			.memoryCache {
				MemoryCache.Builder(context)
					.maxSizePercent(0.25)
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(context.cacheDir.resolve("image_cache"))
					.maxSizeBytes(300L * 1024 * 1024) // 扩大至 300MB，避免频繁逐出重下
					.build()
			}
			// Loader 和两个网络客户端只建一次；每个图片请求读取当前证书策略与认证信息。
			.callFactory(Call.Factory { request ->
				(if (trustAllCerts) permissiveImage else verifiedImage).newCall(request)
			})
			.build()
	}

	/** 仅清理可再生成的缩略图，保留进行中的分享文件和更新安装包。调用方须在 IO 等待完成。 */
	fun clearImageCache() {
		imageLoader.memoryCache?.clear()
		imageLoader.diskCache?.clear()
		LocalThumbs.clear(context)
	}

	fun http(): OkHttpClient = okHttp

	/**
	 * 只读连通性探测：复用已建立的 client 做一次轻量 PROPFIND。
	 * 绝不 invalidate / stop 隧道 / 重建 client——测试动作本身不能破坏正在用的连接，
	 * 这正是旧版「一点测试就卡死无响应」的根因。
	 * 必须在 IO 线程调用。s 仅在当前还没有 client 时用于一次性建连。
	 */
	fun probeConnection(s: Settings) {
		val c = client(s)
		c.probe()
	}

	data class DirectProbeResult(val ip: String, val latencyMs: Long, val isLan: Boolean)

	@Volatile
	private var directConfirmed: DirectProbeResult? = null
	val isDirectConfirmed: Boolean get() = directConfirmed != null

	/** 按当前配置准备 WebDavClient */
	@Synchronized
	fun client(settings: Settings): WebDavClient {
		val normalized = settings.normalized()
		val key = normalized.copy(lastDir = "")
		cached?.let { if (key == cachedKey) return it }
		check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
			"网络连接初始化须在后台线程执行"
		}
		// 配置切换时旧桥的目标地址、凭据及 TLS 策略都已失效，不能复用它的本地端口。
		if (cachedKey != null && cachedKey != key) {
			TsBridge.stop()
			cached = null
			cachedKey = null
		}

		val root = normalized.baseUrl.toHttpUrlOrNull() ?: error("服务器地址不合法")

		authHeader = Credentials.basic(normalized.user, normalized.password)
		authOrigin = root
		trustAllCerts = normalized.trustAllCerts
		bridgeToken = ""
		directConfirmed = null
		_routeInfo.value = RouteInfo(false, false, "", "", "正在连接…", isProbing = true)

		var base: HttpUrl = root
		// 直连与自动模式优先探测真实连通性；强制组网则始终走内嵌 Tailscale
		val direct = if (normalized.connectionMode != ConnectionMode.Tailscale) doDirectProbe(root, normalized.user, normalized.password) else null
		if (direct != null) {
			directConfirmed = direct
			_routeInfo.value = RouteInfo(
				isDirect = true,
				isRelay = false,
				endpoint = direct.ip,
				peerName = if (direct.isLan) "LAN" else "Direct",
				label = if (direct.isLan) "局域网直连 · ${direct.latencyMs}ms" else "系统直连 · ${direct.latencyMs}ms",
				latencyMs = direct.latencyMs,
				isProbing = false,
				isLanDirect = direct.isLan,
			)
		} else {
			// 直连不可达（或强制组网）：优先使用内嵌 Tailscale 隧道，绝不挂死应用
			if (TsBridge.available) {
				val origin = root.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
				TsBridge.setInsecureTls(normalized.trustAllCerts)
				val p = TsBridge.start(context, normalized.hostname, normalized.authKey, origin)
				if (p <= 0) {
					_routeInfo.value = RouteInfo(false, false, "", "", "Tailscale 尚未就绪", isProbing = false)
					Log.w("Repository", "隧道初始化失败，mode=${normalized.connectionMode}，host=${root.host}")
					throw IllegalStateException("Tailscale 隧道未就绪，请检查组网授权后重新连接")
				}
				bridgeToken = TsBridge.token
				base = "http://127.0.0.1:$p${root.encodedPath}".toHttpUrlOrNull() ?: error("本地隧道地址不合法")
				updateRouteInfo()
				if (normalized.connectionMode == ConnectionMode.Direct) {
					_routeInfo.value = RouteInfo(
						isDirect = false,
						isRelay = true,
						endpoint = "",
						peerName = "",
						label = "局域网不可达 · 自动启用组网",
						isProbing = false,
						isLanDirect = false,
					)
				}
			} else {
				if (normalized.connectionMode == ConnectionMode.Direct) {
					_routeInfo.value = RouteInfo(
						isDirect = false,
						isRelay = false,
						endpoint = "",
						peerName = "",
						label = "直连不可达（无法访问 ${root.host}）",
						isProbing = false,
						isLanDirect = false,
					)
				} else {
					_routeInfo.value = RouteInfo(false, false, "", "", "当前安装包未包含 Tailscale", isProbing = false)
					Log.w("Repository", "缺少内嵌 Tailscale，mode=${normalized.connectionMode}，host=${root.host}")
					throw IllegalStateException("当前安装包未包含内嵌 Tailscale，请安装完整版或在可直连网络下选择直连模式")
				}
			}
		}

		val apiOrigin = base.newBuilder().encodedPath("/").query(null).fragment(null).build()
		return WebDavClient(base, okHttp, apiOrigin, normalized.user, normalized.password).also {
			cached = it
			cachedKey = key
		}
	}

	/**
	 * 智能网络判定：
	 * 1. 优先尝试轻量 HTTP WebDAV 握手 (PROPFIND)，若本地或当前环境网络（Wi-Fi/系统VPN）可直接通达则直接采用直连通道；
	 * 2. 握手成功则直接使用原生连接，免去内嵌 tsnet 代理开销；
	 * 3. 只有无法直连且启用了 Tailscale 时才走内嵌 tsnet 隧道打洞。
	 */
	private fun doDirectProbe(root: HttpUrl, user: String, pass: String): DirectProbeResult? {
		return try {
			val host = root.host
			var anyIp = host
			var isLan = false
			val isIp = host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
			if (isIp) {
				val parts = host.split('.').mapNotNull { it.toIntOrNull() }
				if (parts.size == 4) {
					val b0 = parts[0]
					val b1 = parts[1]
					isLan = (b0 == 10) ||
						(b0 == 172 && (b1 in 16..31)) ||
						(b0 == 192 && b1 == 168) ||
						(b0 == 169 && b1 == 254) ||
						(b0 == 127)
				}
			} else {
				try {
					val addrs = java.net.InetAddress.getAllByName(host)
					for (addr in addrs) {
						val ip = addr.hostAddress ?: continue
						anyIp = ip
						val bytes = addr.address
						if (bytes.size == 4) {
							val b0 = bytes[0].toInt() and 0xFF
							val b1 = bytes[1].toInt() and 0xFF
							isLan = (b0 == 10) ||
								(b0 == 172 && (b1 in 16..31)) ||
								(b0 == 192 && b1 == 168) ||
								(b0 == 169 && b1 == 254) ||
								addr.isSiteLocalAddress ||
								addr.isLoopbackAddress
							if (isLan) break
						}
					}
				} catch (_: Exception) {
					return null
				}
			}

			val testReq = Request.Builder()
				.url(root)
				.header("Depth", "0")
				.header("Authorization", Credentials.basic(user, pass))
				.method("PROPFIND", okhttp3.RequestBody.create(null, ByteArray(0)))
				.build()

			val t0 = System.currentTimeMillis()
			probeHttp.newCall(testReq).execute().use { resp ->
				val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
				if (resp.isSuccessful || resp.code in 200..499) {
					// 4xx 只说明链路可达；账号与 WebDAV 权限由正式 probe() 检查。
					Log.i("Repository", "直连链路可达，host=$host，latencyMs=$dt，status=${resp.code}")
					return DirectProbeResult(anyIp, dt, isLan)
				}
			}
			null
		} catch (e: Exception) {
			Log.d("Repository", "直连探测失败，host=${root.host}，reason=${e.javaClass.simpleName}", e)
			null
		}
	}

	/**
	 * 隧道自愈：App 退到后台/进程被杀后，tsnet 节点会随进程断开；回到前台时
	 * 用 files/tsnet 里持久化的节点状态原地重连（通常无需重新去浏览器授权）。
	 * 必须在后台线程调用（JNI + 可能的网络等待）。返回 true 表示发生了重连、
	 * 本地端口可能变化，调用方必须丢弃旧 client 并重建。
	 */
	fun reconnectTunnelIfNeeded(settings: Settings): Boolean {
		if (!TsBridge.available) return false
		// 已确认局域网/公网原生直连，本就没走隧道，无需重连
		if (directConfirmed != null) return false

		val st = TsBridge.status()
		if (st.startsWith("running") && TsBridge.port > 0) {
			updateRouteInfo()
			return false
		}
		// 正在登录/启动中：交给既有异步流程，别打断
		if (st == "needs-login" || st == "starting") return false

		val root = settings.baseUrl.toHttpUrlOrNull() ?: return false
		val origin = root.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
		val p = if (TsBridge.port > 0) {
			TsBridge.retry()
		} else {
			TsBridge.setInsecureTls(settings.trustAllCerts)
			TsBridge.start(context, settings.hostname, settings.authKey, origin)
		}
		if (p > 0) {
			bridgeToken = TsBridge.token
			// 端口可能变了：丢弃缓存 client，下次 client() 用新端口重建 base（见 cachedKey 逻辑）
			synchronized(this) {
				cached = null
				cachedKey = null
			}
			updateRouteInfo()
			Log.i("Repository", "隧道自愈重连成功，新端口 $p")
			return true
		}
		return false
	}

	/** 主动触发 NAT 打洞，促使链路从 DERP 中继切换为 P2P 直连 */
	fun triggerHolePunch(): String {
		if (directConfirmed != null) {
			return "当前使用局域网直连 (延迟 ${directConfirmed?.latencyMs ?: 0}ms)"
		}
		val result = TsBridge.punchHole()
		updateRouteInfo()
		return result
	}

	/** 刷新链路状态（判断是 P2P 直连打洞还是 DERP 中继代理） */
	fun updateRouteInfo() {
		if (directConfirmed != null) {
			return // 局域网直连模式保持原样，不被 Tailscale 状态覆盖
		}
		val info = TsBridge.peerLinkInfo()
		// info 格式: "direct|39.163.49.110:20045|istoreos" 或 "relay|hongkong|istoreos"
		val parts = info.split('|')
		when {
			parts.firstOrNull() == "direct" -> {
				val ep = parts.getOrNull(1).orEmpty()
				val name = parts.getOrNull(2).orEmpty()
				_routeInfo.value = RouteInfo(
					isDirect = true,
					isRelay = false,
					endpoint = ep,
					peerName = name,
					label = "⚡ P2P 直连 · $ep",
					isProbing = false,
					isLanDirect = false,
				)
			}
			parts.firstOrNull() == "relay" -> {
				val relayNode = parts.getOrNull(1).orEmpty()
				val name = parts.getOrNull(2).orEmpty()
				_routeInfo.value = RouteInfo(
					isDirect = false,
					isRelay = true,
					endpoint = relayNode,
					peerName = name,
					label = "🔄 DERP 中继 · $relayNode",
					isProbing = false,
					isLanDirect = false,
				)
				// 积极直连巡检：在中继状态下立即触发后台积极穿透与局域网探活
				startAggressiveDirectAttemptIfNeeded()
			}
			parts.firstOrNull() == "running" -> {
				val st = TsBridge.status()
				val ip = st.removePrefix("running:").removePrefix("running").trim(':')
				_routeInfo.value = RouteInfo(
					isDirect = false,
					isRelay = true,
					endpoint = ip,
					peerName = "",
					label = "🛡️ Tailscale 隧道已连通",
					isProbing = false,
					isLanDirect = false,
				)
			}
			else -> {
				val st = TsBridge.status()
				val isStarting = st == "starting" || st == "idle" || st.isBlank()
				_routeInfo.value = RouteInfo(
					isDirect = false,
					isRelay = false,
					endpoint = "",
					peerName = "",
					label = when {
						st.startsWith("running") -> "🛡️ Tailscale 隧道已连通"
						st == "needs-login" -> "Tailscale 等待登录"
						st.startsWith("error") -> "Tailscale 异常: " + st.removePrefix("error:").trim()
						st == "stopped" -> "Tailscale 已停止"
						else -> "🛡️ 正在建立连接…"
					},
					isProbing = isStarting,
					isLanDirect = false,
				)
			}
		}
	}

	@Volatile
	private var aggressiveHolePunchJob: Job? = null

	/**
	 * 积极的直连尝试机制：
	 * 当网络处于中转 (DERP relay) 模式时，高频向对端发送 Disco Ping 并在自动模式下复测局域网直连，
	 * 促使连接尽快由慢速中继升级为高速 P2P 直连或原生局域网直连。
	 */
	fun startAggressiveDirectAttemptIfNeeded() {
		if (directConfirmed != null || cachedKey?.connectionMode == ConnectionMode.Direct) return
		if (aggressiveHolePunchJob?.isActive == true) return

		aggressiveHolePunchJob = CoroutineScope(Dispatchers.IO).launch {
			var attempts = 0
			while (isActive && attempts < 20) {
				val current = _routeInfo.value
				if (current.isDirect || current.isLan) {
					Log.i("Repository", "已成功建立直连，积极打洞收敛完成")
					break
				}

				// 1. 若为 Auto 模式，探测是否已接入同一局域网（Wi-Fi 直连最快）
				val settings = cachedKey
				if (settings != null && settings.connectionMode == ConnectionMode.Auto) {
					val root = settings.baseUrl.toHttpUrlOrNull()
					if (root != null) {
						val direct = doDirectProbe(root, settings.user, settings.password)
						if (direct != null) {
							directConfirmed = direct
							val apiOrigin = root.newBuilder().encodedPath("/").query(null).fragment(null).build()
							cached = WebDavClient(root, okHttp, apiOrigin, settings.user, settings.password)
							_routeInfo.value = RouteInfo(
								isDirect = true,
								isRelay = false,
								endpoint = direct.ip,
								peerName = if (direct.isLan) "LAN" else "Direct",
								label = if (direct.isLan) "局域网直连 · ${direct.latencyMs}ms" else "系统直连 · ${direct.latencyMs}ms",
								latencyMs = direct.latencyMs,
								isProbing = false,
								isLanDirect = direct.isLan,
							)
							Log.i("Repository", "后台探测成功发现局域网直连，直接切入原生高速通道！")
							break
						}
					}
				}

				// 2. 积极触发 Tailscale NAT 打洞
				TsBridge.punchHole()
				val info = TsBridge.peerLinkInfo()
				val parts = info.split('|')
				if (parts.firstOrNull() == "direct") {
					val ep = parts.getOrNull(1).orEmpty()
					val name = parts.getOrNull(2).orEmpty()
					_routeInfo.value = RouteInfo(
						isDirect = true,
						isRelay = false,
						endpoint = ep,
						peerName = name,
						label = "⚡ P2P 直连 · $ep",
						isProbing = false,
						isLanDirect = false,
					)
					Log.i("Repository", "积极打洞成功，链路已升级为 P2P 直连: $ep")
					break
				}

				attempts++
				delay(if (attempts <= 5) 2000L else 4000L)
			}
		}
	}

	/** 配置变了要丢弃旧的 client，让 bridge 用新参数重启 */
	@Synchronized
	fun invalidate() {
		aggressiveHolePunchJob?.cancel()
		cached = null
		cachedKey = null
		directConfirmed = null
		authHeader = null
		authOrigin = null
		bridgeToken = ""
		TsBridge.stop()
		_routeInfo.value = RouteInfo(false, false, "", "", "正在连接…", -1, true)
	}
}
