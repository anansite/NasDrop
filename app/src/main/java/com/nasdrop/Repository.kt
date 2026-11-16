package com.nasdrop

import android.content.Context
import coil.ImageLoader
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局唯一的网络出口。
 *
 * 所有请求（WebDAV、图片缩略图、视频流）共用同一个 OkHttpClient，
 * Basic 认证头和 bridge token 由 interceptor 统一注入，业务代码里看不到凭据。
 */
class Repository(private val context: Context) {

	@Volatile
	private var authHeader: String? = null

	@Volatile
	private var bridgeToken: String = ""

	@Volatile
	private var cachedKey: String = ""

	@Volatile
	private var cached: WebDavClient? = null

	private val okHttp: OkHttpClient = OkHttpClient.Builder()
		.addInterceptor { chain ->
			val b = chain.request().newBuilder()
			authHeader?.let { b.header("Authorization", it) }
			if (bridgeToken.isNotEmpty()) b.header("X-Bridge-Token", bridgeToken)
			chain.proceed(b.build())
		}
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(5, TimeUnit.MINUTES)
		.writeTimeout(5, TimeUnit.MINUTES)
		.build()

	val imageLoader: ImageLoader by lazy {
		ImageLoader.Builder(context)
			.okHttpClient { okHttp }
			.crossfade(true)
			.build()
	}

	fun http(): OkHttpClient = okHttp

	/**
	 * 按当前配置准备一个 WebDavClient。
	 *
	 * 开了内嵌 Tailscale 且 AAR 在包里时，base 会变成 http://127.0.0.1:<port><原路径>，
	 * 后续所有请求都由 Go 侧经 tsnet 转发到真实服务器；否则直接用原地址（直连模式）。
	 */
	@Synchronized
	fun client(settings: Settings): WebDavClient {
		val key = listOf(
			settings.baseUrl, settings.user, settings.password,
			settings.useTailscale, settings.hostname, settings.authKey, settings.trustAllCerts,
		).joinToString("|")
		cached?.let { if (key == cachedKey) return it }

		val root = settings.baseUrl.toHttpUrlOrNull()
			?: throw IllegalStateException("服务器地址不合法：${settings.baseUrl}")

		authHeader = Credentials.basic(settings.user, settings.password)
		bridgeToken = ""

		var base: HttpUrl = root
		if (settings.useTailscale && TsBridge.available) {
			// 只取 scheme://host:port/，路径交给代理原样透传；注意别漏掉自定义端口
			val origin = root.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
			TsBridge.setInsecureTls(settings.trustAllCerts)
			val p = TsBridge.start(context, settings.hostname, settings.authKey, origin)
			if (p > 0) {
				bridgeToken = TsBridge.token
				base = "http://127.0.0.1:$p${root.encodedPath}".toHttpUrlOrNull() ?: root
			}
		}

		return WebDavClient(base, okHttp).also {
			cached = it
			cachedKey = key
		}
	}

	/** 配置变了要丢弃旧的 client，让 bridge 用新参数重启 */
	@Synchronized
	fun invalidate() {
		cached = null
		cachedKey = ""
		TsBridge.stop()
	}
}
