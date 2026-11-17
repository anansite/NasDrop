package com.nasdrop

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
	private var cachedKey: String = ""

	@Volatile
	private var cached: WebDavClient? = null

	@Volatile
	private var lanConfirmedIp: String? = null

	private val trustAllCertsManager = object : X509TrustManager {
		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
		override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
	}

	private val sslContext = SSLContext.getInstance("TLS").apply {
		init(null, arrayOf<TrustManager>(trustAllCertsManager), SecureRandom())
	}

	private val okHttp: OkHttpClient = OkHttpClient.Builder()
		.dispatcher(Dispatcher().apply {
			maxRequests = 128
			maxRequestsPerHost = 64
		})
		.connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
		.addInterceptor { chain ->
			val b = chain.request().newBuilder()
			// WebDAV 请求统一注入 Basic；OpenList 私有 API 自带 token 时不覆盖。
			if (chain.request().header("Authorization").isNullOrBlank()) {
				authHeader?.let { b.header("Authorization", it) }
			}
			if (bridgeToken.isNotEmpty()) b.header("X-Bridge-Token", bridgeToken)
			chain.proceed(b.build())
		}
		.sslSocketFactory(sslContext.socketFactory, trustAllCertsManager)
		.hostnameVerifier { _, _ -> true }
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(5, TimeUnit.MINUTES)
		.writeTimeout(5, TimeUnit.MINUTES)
		.build()

	private val probeHttp: OkHttpClient = okHttp.newBuilder()
		.connectTimeout(1500, TimeUnit.MILLISECONDS)
		.readTimeout(1500, TimeUnit.MILLISECONDS)
		.build()

	private val imageOkHttp: OkHttpClient by lazy {
		okHttp.newBuilder()
			.dispatcher(Dispatcher().apply {
				maxRequests = 24
				maxRequestsPerHost = 16
			})
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.build()
	}

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
			.okHttpClient { imageOkHttp }
			.crossfade(true)
			.build()
	}

	fun clearImageCache() {
		imageLoader.memoryCache?.clear()
		imageLoader.diskCache?.clear()
	}

	fun http(): OkHttpClient = okHttp

	data class DirectProbeResult(val ip: String, val latencyMs: Long, val isLan: Boolean)

	@Volatile
	private var directConfirmed: DirectProbeResult? = null

	/** 按当前配置准备 WebDavClient */
	@Synchronized
	fun client(settings: Settings): WebDavClient {
		val key = listOf(
			settings.baseUrl, settings.user, settings.password,
			settings.hostname, settings.authKey, settings.trustAllCerts,
			settings.useTailscale,
		).joinToString("|")
		cached?.let { if (key == cachedKey) return it }

		val root = settings.baseUrl.toHttpUrlOrNull()
			?: throw IllegalStateException("服务器地址不合法：${settings.baseUrl}")

		authHeader = Credentials.basic(settings.user, settings.password)
		bridgeToken = ""

		var base: HttpUrl = root
		val direct = probeDirect(root, settings.user, settings.password)
		if (direct != null) {
			base = root
			_routeInfo.value = RouteInfo(
				isDirect = true,
				isRelay = false,
				endpoint = direct.ip,
				peerName = if (direct.isLan) "LAN" else "Direct",
				label = if (direct.isLan) "🏡 局域网直连 · ${direct.latencyMs}ms (千兆原速)" else "⚡ 系统专线直连 · ${direct.latencyMs}ms",
				latencyMs = direct.latencyMs,
				isProbing = false,
				isLanDirect = true,
			)
		} else {
			// 若当前未知是否为直连，触发后台异步极速探测，不阻塞主线程
			triggerDirectProbeAsync(settings)

			if (settings.useTailscale && TsBridge.available) {
				val origin = root.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
				// 避免在 Android UI 主线程阻塞 JNI / libhoudini 导致 ANR
				if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
					TsBridge.setInsecureTls(settings.trustAllCerts)
					val p = TsBridge.start(context, settings.hostname, settings.authKey, origin)
					if (p > 0) {
						bridgeToken = TsBridge.token
						base = "http://127.0.0.1:$p${root.encodedPath}".toHttpUrlOrNull() ?: root
						updateRouteInfo()
					}
				} else {
					CoroutineScope(Dispatchers.IO).launch {
						TsBridge.setInsecureTls(settings.trustAllCerts)
						val p = TsBridge.start(context, settings.hostname, settings.authKey, origin)
						if (p > 0) {
							bridgeToken = TsBridge.token
							updateRouteInfo()
						}
					}
				}
			} else {
				_routeInfo.value = RouteInfo(
					isDirect = false,
					isRelay = false,
					endpoint = "",
					peerName = "",
					label = if (!settings.useTailscale) "🌐 直连模式" else "🔴 离线未连接",
					latencyMs = -1L,
					isProbing = false,
					isLanDirect = false,
				)
			}
		}

		val apiOrigin = base.newBuilder().encodedPath("/").query(null).fragment(null).build()
		return WebDavClient(base, okHttp, apiOrigin, settings.user, settings.password).also {
			cached = it
			cachedKey = key
		}
	}

	private fun probeDirect(root: HttpUrl, user: String, pass: String): DirectProbeResult? {
		directConfirmed?.let { return it }
		// 主线程绝不执行同步网络请求，避免 NetworkOnMainThreadException 与卡顿
		if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
			return null
		}
		return doDirectProbe(root, user, pass)
	}

	private fun triggerDirectProbeAsync(settings: Settings) {
		val root = settings.baseUrl.toHttpUrlOrNull() ?: return
		CoroutineScope(Dispatchers.IO).launch {
			val res = doDirectProbe(root, settings.user, settings.password)
			if (res != null) {
				directConfirmed = res
				synchronized(this@Repository) {
					val apiOrigin = root.newBuilder().encodedPath("/").query(null).fragment(null).build()
					cached = WebDavClient(root, okHttp, apiOrigin, settings.user, settings.password)
				}
				_routeInfo.value = RouteInfo(
					isDirect = true,
					isRelay = false,
					endpoint = res.ip,
					peerName = if (res.isLan) "LAN" else "Direct",
					label = if (res.isLan) "🏡 局域网直连 · ${res.latencyMs}ms (千兆原速)" else "⚡ 系统专线直连 · ${res.latencyMs}ms",
					latencyMs = res.latencyMs,
					isProbing = false,
					isLanDirect = true,
				)
			}
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
			val addrs = java.net.InetAddress.getAllByName(host)
			var anyIp = host
			var isLan = false
			for (addr in addrs) {
				val ip = addr.hostAddress ?: continue
				anyIp = ip
				val bytes = addr.address
				if (bytes.size == 4) {
					val b0 = bytes[0].toInt() and 0xFF
					val b1 = bytes[1].toInt() and 0xFF
					// 100.64.0.0/10 是 CGNAT 保留段，Tailscale 的 tailnet 节点地址就落在这里。
					// 域名解析到该段说明目标是 tailnet 内节点——必须交给内嵌 tsnet 打洞，
					// 不能被原生 OkHttp 当成"系统专线直连"，否则等于绕过了隧道。
					if (b0 == 100 && b1 in 64..127) {
						Log.i("Repository", "Tailnet address detected ($ip), skip direct probe; let tsnet handle it")
						return null
					}
					isLan = (b0 == 10) ||
						(b0 == 172 && (b1 in 16..31)) ||
						(b0 == 192 && b1 == 168) ||
						(b0 == 169 && b1 == 254) ||
						addr.isSiteLocalAddress ||
						addr.isLoopbackAddress
					if (isLan) break
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
					Log.i("Repository", "Direct confirmed via $anyIp (${dt}ms), status=${resp.code}")
					return DirectProbeResult(anyIp, dt, isLan)
				}
			}
			null
		} catch (e: Exception) {
			Log.d("Repository", "Direct probe skipped: ${e.message}")
			null
		}
	}

	/** 主动触发 NAT 打洞，促使链路从 DERP 中继切换为 P2P 直连 */
	fun triggerHolePunch(): String {
		if (_routeInfo.value.isLanDirect) {
			return "当前已处于千兆局域网原生直连状态"
		}
		val result = TsBridge.punchHole()
		updateRouteInfo()
		return result
	}

	/** 刷新链路状态（判断是 P2P 直连打洞还是 DERP 中继代理） */
	fun updateRouteInfo() {
		if (_routeInfo.value.isLanDirect) {
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
			}
			else -> {
				val st = TsBridge.status()
				_routeInfo.value = RouteInfo(
					isDirect = false,
					isRelay = false,
					endpoint = "",
					peerName = "",
					label = if (st.startsWith("running")) "🛡️ Tailscale 已连接" else "🛡️ Tailscale 准备中",
					isProbing = false,
					isLanDirect = false,
				)
			}
		}
	}

	/** 配置变了要丢弃旧的 client，让 bridge 用新参数重启 */
	@Synchronized
	fun invalidate() {
		cached = null
		cachedKey = ""
		lanConfirmedIp = null
		directConfirmed = null
		TsBridge.stop()
		_routeInfo.value = RouteInfo(false, false, "", "", "已断开", -1, false)
	}
}
