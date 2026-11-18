package com.nasdrop

import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** 凭据仅允许发往配置服务的同源地址，路径和签名参数不影响同源判定。 */
internal fun HttpUrl.matchesOrigin(origin: HttpUrl?): Boolean =
	origin != null && scheme == origin.scheme && host == origin.host && port == origin.port

/** 内嵌桥只监听当前有效端口的 IPv4 回环 HTTP 地址。 */
internal fun HttpUrl.isBridgeOrigin(activePort: Int): Boolean =
	activePort > 0 && scheme == "http" && host == "127.0.0.1" && port == activePort

/** 从默认安全 Builder 派生证书策略；不安全连接池独立，避免切回校验模式时复用。 */
internal fun OkHttpClient.Builder.withCertificatePolicy(trustAllCerts: Boolean): OkHttpClient.Builder {
	if (!trustAllCerts) return this
	val manager = object : X509TrustManager {
		override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
		override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
		override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
	}
	val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(manager), SecureRandom()) }
	return connectionPool(ConnectionPool()).sslSocketFactory(ssl.socketFactory, manager).hostnameVerifier { _, _ -> true }
}
