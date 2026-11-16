package com.nasdrop

import android.content.Context
import android.util.Log
import java.io.File
import java.lang.reflect.Method

/**
 * 对 app/libs/bridge.aar 的薄封装。
 *
 * 这个 AAR 里装的是完整的 Tailscale（Go 的 tsnet 用户态节点），所以 App 装好之后
 * 自带组网能力，用户不需要再安装 Tailscale 客户端，也不需要授予 VPN 权限。
 *
 * 用反射而不是直接 import，是为了让工程在没有 AAR 的时候也能编译运行（退化成“直连模式”，
 * 也就是依赖手机自己装着的 Tailscale）。这样调 UI 的时候不必每次都背上 Go 编译。
 */
object TsBridge {

	private const val TAG = "TsBridge"
	private const val CLASS_NAME = "bridge.Bridge"

	/** AAR 是否已打进包里 */
	@Volatile
	var available: Boolean = false
		private set

	@Volatile
	var port: Int = 0
		private set

	@Volatile
	var token: String = ""
		private set

	private var cls: Class<*>? = null
	private var probed = false

	private fun load(): Class<*>? {
		if (!probed) {
			probed = true
			cls = runCatching { Class.forName(CLASS_NAME) }.getOrNull()
			available = cls != null
			if (!available) {
				Log.i(TAG, "未找到 $CLASS_NAME：本次构建没有内嵌 Tailscale，走直连模式")
			}
		}
		return cls
	}

	/** gomobile 生成的方法名大小写不完全固定，按名字忽略大小写匹配 */
	private fun find(c: Class<*>, name: String, argc: Int = -1): Method? =
		c.methods.firstOrNull {
			it.name.equals(name, ignoreCase = true) && (argc < 0 || it.parameterTypes.size == argc)
		}

	/** Go 侧证书校验开关，必须在 [start] 之前调用 */
	fun setInsecureTls(enable: Boolean) {
		val c = load() ?: return
		runCatching { find(c, "setinsecuretls", 1)?.invoke(null, enable) }
			.onFailure { Log.w(TAG, "SetInsecureTLS 调用失败", it) }
	}

	/**
	 * 启动内嵌 Tailscale 节点与本地代理，返回本地端口；失败或没有 AAR 时返回 0。
	 *
	 * @param targetUrl WebDAV 根地址（只取 scheme://host，路径由代理透传）
	 */
	@Synchronized
	fun start(context: Context, hostname: String, authKey: String, targetUrl: String): Int {
		if (port > 0) return port
		val c = load() ?: return 0
		return try {
			val stateDir = File(context.filesDir, "tsnet").apply { mkdirs() }.absolutePath
			val m = find(c, "start", 4) ?: run {
				Log.e(TAG, "AAR 里没有 Start(String,String,String,String)")
				return 0
			}
			val p = when (val r = m.invoke(null, stateDir, hostname, authKey, targetUrl)) {
				is Number -> r.toInt()
				else -> 0
			}
			port = p
			token = invokeString("token")
			Log.i(TAG, "bridge 就绪，本地端口 $p")
			p
		} catch (t: Throwable) {
			Log.e(TAG, "bridge 启动失败", unwrap(t))
			0
		}
	}

	/** idle / starting / needs-login / running:100.x.x.x / error: xxx */
	fun status(): String = invokeString("status")

	/** 需要登录时返回授权链接，否则空串 */
	fun authUrl(): String = invokeString("authurl")

	fun logs(): String = invokeString("logs")

	fun stop() {
		val c = load() ?: return
		runCatching { find(c, "stop")?.invoke(null) }
		port = 0
		token = ""
	}

	private fun invokeString(name: String): String {
		val c = load() ?: return ""
		return runCatching {
			find(c, name)?.invoke(null) as? String ?: ""
		}.getOrElse {
			Log.w(TAG, "$name 调用失败", unwrap(it))
			""
		}
	}

	private fun unwrap(t: Throwable): Throwable =
		if (t is java.lang.reflect.InvocationTargetException) t.targetException ?: t else t
}
