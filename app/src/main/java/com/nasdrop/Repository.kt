package com.nasdrop

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Credentials
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
	val isDirect: Boolean,     // 是否为 P2P 直连打洞
	val isRelay: Boolean,      // 是否为 DERP 中继代理模式
	val endpoint: String,      // 对端打洞端点或中继节点（如 "39.163.49.110:20045"）
	val peerName: String,      // 对端主机名（如 "istoreos"）
	val label: String,         // 状态标签
	val latencyMs: Long = -1L,
	val isProbing: Boolean = false,
) {
	val isLan: Boolean
		get() = isDirect && endpoint.isEmpty()
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

	private val trustAllCertsManager = object : X509TrustManager {
		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
		override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
	}

	private val sslContext = SSLContext.getInstance("TLS").apply {
		init(null, arrayOf<TrustManager>(trustAllCertsManager), SecureRandom())
	}

	private val okHttp: OkHttpClient = OkHttpClient.Builder()
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

	val imageLoader: ImageLoader by lazy {
		ImageLoader.Builder(context)
			.components {
				add(VideoFrameDecoder.Factory())
			}
			.okHttpClient { okHttp }
			.crossfade(true)
			.build()
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
		if (TsBridge.available) {
			val origin = root.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
			TsBridge.setInsecureTls(settings.trustAllCerts)
			val p = TsBridge.start(context, settings.hostname, settings.authKey, origin)
			if (p > 0) {
				bridgeToken = TsBridge.token
				base = "http://127.0.0.1:$p${root.encodedPath}".toHttpUrlOrNull() ?: root
				updateRouteInfo()
			}
		}

		return WebDavClient(base, okHttp).also {
			cached = it
			cachedKey = key
		}
	}

	/** 主动触发 NAT 打洞，促使链路从 DERP 中继切换为 P2P 直连 */
	fun triggerHolePunch(): String {
		val result = TsBridge.punchHole()
		updateRouteInfo()
		return result
	}

	/** 刷新链路状态（判断是 P2P 直连打洞还是 DERP 中继代理） */
	fun updateRouteInfo() {
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
				)
			}
		}
	}

	/** 配置变了要丢弃旧的 client，让 bridge 用新参数重启 */
	@Synchronized
	fun invalidate() {
		cached = null
		cachedKey = ""
		TsBridge.stop()
		_routeInfo.value = RouteInfo(false, false, "", "", "已断开", -1, false)
	}
}
