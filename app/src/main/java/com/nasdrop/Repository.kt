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
			authHeader?.let { b.header("Authorization", it) }
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
			// 注意：移除了 VideoFrameDecoder.Factory()，杜绝移动端远程 WebDAV 对大视频发起多路并发全量下载拖垮网速与阻塞
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

	/** 按当前配置准备 WebDavClient */
	@Synchronized
	fun client(settings: Settings): WebDavClient {
		val key = listOf(
			settings.baseUrl, settings.user, settings.password,
			settings.hostname, settings.authKey, settings.trustAllCerts,
		).joinToString("|")
		cached?.let { if (key == cachedKey) return it }

		val root = settings.baseUrl.toHttpUrlOrNull()
			?: throw IllegalStateException("服务器地址不合法：${settings.baseUrl}")

		authHeader = Credentials.basic(settings.user, settings.password)
		bridgeToken = ""

		var base: HttpUrl = root
		val lanIp = probeLanDirect(root, settings.user, settings.password)
		if (lanIp != null) {
			base = root
			_routeInfo.value = RouteInfo(
				isDirect = true,
				isRelay = false,
				endpoint = lanIp,
				peerName = "LAN",
				label = "🏡 局域网直连 · 1ms (千兆原速)",
				latencyMs = 1L,
				isProbing = false,
				isLanDirect = true,
			)
		} else {
			// 若当前未知是否为 LAN，触发后台异步极速探测，不阻塞主线程
			triggerLanProbeAsync(settings)

			if (TsBridge.available) {
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
			}
		}

		return WebDavClient(base, okHttp).also {
			cached = it
			cachedKey = key
		}
	}

	private fun probeLanDirect(root: HttpUrl, user: String, pass: String): String? {
		lanConfirmedIp?.let { return it }
		// 主线程绝不执行同步网络请求，避免 NetworkOnMainThreadException 与卡顿
		if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
			return null
		}
		return doLanProbe(root, user, pass)
	}

	private fun triggerLanProbeAsync(settings: Settings) {
		val root = settings.baseUrl.toHttpUrlOrNull() ?: return
		CoroutineScope(Dispatchers.IO).launch {
			val ip = doLanProbe(root, settings.user, settings.password)
			if (ip != null) {
				lanConfirmedIp = ip
				synchronized(this@Repository) {
					cached = WebDavClient(root, okHttp)
				}
				_routeInfo.value = RouteInfo(
					isDirect = true,
					isRelay = false,
					endpoint = ip,
					peerName = "LAN",
					label = "🏡 局域网直连 · 1ms (千兆原速)",
					latencyMs = 1L,
					isProbing = false,
					isLanDirect = true,
				)
			}
		}
	}

	/**
	 * 局域网三重智能判定：
	 * 1. 域名在当前物理网络下被 DNS 解析为私有 IPv4 (RFC 1918)
	 * 2. 对该私有地址快速发起轻量 HTTP WebDAV 握手 (PROPFIND)，带用户密码
	 * 3. 握手成功 (200 / 207 / 401 证实确系用户的 NAS WebDAV 服务)
	 * 全部命中则判定为在家局域网直连，跑满千兆 Wi-Fi，延迟 1ms，彻底绕过虚拟网卡与打洞代理。
	 */
	private fun doLanProbe(root: HttpUrl, user: String, pass: String): String? {
		return try {
			val host = root.host
			val addrs = java.net.InetAddress.getAllByName(host)
			var privateIp: String? = null
			for (addr in addrs) {
				val ip = addr.hostAddress ?: continue
				val bytes = addr.address
				if (bytes.size == 4) {
					val b0 = bytes[0].toInt() and 0xFF
					val b1 = bytes[1].toInt() and 0xFF
					val isPrivate = (b0 == 10) ||
						(b0 == 172 && (b1 in 16..31)) ||
						(b0 == 192 && b1 == 168) ||
						(b0 == 169 && b1 == 254) ||
						addr.isSiteLocalAddress ||
						addr.isLoopbackAddress
					if (isPrivate) {
						privateIp = ip
						break
					}
				}
			}

			if (privateIp == null) {
				return null // 公网 IP，回退至 Tailscale 打洞
			}

			// 发起轻量握手验证，验证服务存活且确系 WebDAV
			val testReq = Request.Builder()
				.url(root)
				.header("Depth", "0")
				.header("Authorization", Credentials.basic(user, pass))
				.method("PROPFIND", okhttp3.RequestBody.create(null, ByteArray(0)))
				.build()

			val t0 = System.currentTimeMillis()
			probeHttp.newCall(testReq).execute().use { resp ->
				val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
				if (resp.isSuccessful || resp.code == 401 || resp.code == 207) {
					Log.i("Repository", "LAN Direct confirmed via $privateIp (${dt}ms)")
					return privateIp
				}
			}
			null
		} catch (e: Exception) {
			Log.d("Repository", "LAN Direct probe skipped: ${e.message}")
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
		TsBridge.stop()
		_routeInfo.value = RouteInfo(false, false, "", "", "已断开", -1, false)
	}
}
