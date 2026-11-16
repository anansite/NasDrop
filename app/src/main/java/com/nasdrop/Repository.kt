package com.nasdrop

import android.content.Context
import com.nasdrop.tailscale.TsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
			label = "未连接",
		)
	)
	val routeInfo: StateFlow<RouteInfo> = _routeInfo

	private var currentSettings: Settings? = null
	private var currentClient: WebDavClient? = null

	@Synchronized
	fun client(s: Settings): WebDavClient {
		if (currentClient != null && currentSettings == s) {
			return currentClient!!
		}
		val okhttp = buildOkHttp(s)
		val dav = WebDavClient(s.baseUrl, s.user, s.password, okhttp)
		currentSettings = s
		currentClient = dav
		return dav
	}

	fun triggerHolePunch() {
		if (!TsBridge.available) return
		val s = currentSettings ?: return
		val host = s.targetHost()
		if (host.isNotEmpty()) {
			TsBridge.pingDisco(host)
		}
	}

	suspend fun pollPeerStatus() = withContext(Dispatchers.IO) {
		if (!TsBridge.available) {
			_routeInfo.value = RouteInfo(
				isDirect = true,
				isRelay = false,
				endpoint = "",
				peerName = "local",
				label = "直连",
			)
			return@withContext
		}

		val s = currentSettings
		val host = s?.targetHost().orEmpty()
		val info = TsBridge.peerRouteInfo(host)
		_routeInfo.value = RouteInfo(
			isDirect = info.isDirect,
			isRelay = info.isRelay,
			endpoint = info.curAddr,
			peerName = info.hostName,
			label = when {
				info.isDirect -> "专线直连 (${info.curAddr})"
				info.isRelay -> "中继顺畅"
				else -> "离线"
			},
		)
	}

	private fun buildOkHttp(s: Settings): OkHttpClient {
		val builder = OkHttpClient.Builder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(60, TimeUnit.SECONDS)

		if (s.trustAllCerts) {
			val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
				override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
				override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
				override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
			})
			val sslContext = SSLContext.getInstance("TLS").apply {
				init(null, trustAll, SecureRandom())
			}
			builder.sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
			builder.hostnameVerifier { _, _ -> true }
		}

		if (s.useTailscale && TsBridge.available) {
			builder.socketFactory(TsBridge.socketFactory())
		}

		return builder.build()
	}
}
