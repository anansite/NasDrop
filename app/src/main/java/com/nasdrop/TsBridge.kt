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

	/**
	 * AAR 是否已打进包里。
	 *
	 * 注意这里必须写成 getter：第一次读取时才真正 Class.forName 探测。
	 * 早先写成 var + private set 的缓存字段，结果是 Repository 在加载 AAR 之前
	 * 就读它，永远是 false，于是整个 App 静默退化成"直连模式"（等于没内嵌 Tailscale），
	 * 而且再没有任何代码路径会去触发加载，永久卡死。
	 */
	val available: Boolean get() = load() != null

	@Volatile
	var port: Int = 0
		private set

	@Volatile
	var token: String = ""
		private set

	private var cls: Class<*>? = null
	private var probed = false

	@Synchronized
	private fun load(): Class<*>? {
		if (!probed) {
			probed = true
			// 注意：Class.forName 会触发静态初始化，而 gomobile 生成的类在静态块里
			// System.loadLibrary("gojni")。所以这里失败可能是“类不存在”，也可能是
			// “so 加载失败”（例如装在没有对应 ABI 的设备/模拟器上）。
			// 早期只打一句“未找到”，把两种原因混在一起，排查时毫无信息量。
			var err: Throwable? = null
			cls = runCatching { Class.forName(CLASS_NAME) }
				.onFailure { err = it }
				.getOrNull()
			if (cls == null) {
				val reason = when (err) {
					is ClassNotFoundException -> "包里没有 $CLASS_NAME（本次构建未内嵌 Tailscale 桥）"
					is UnsatisfiedLinkError -> "找不到 libgojni.so，设备 ABI 与包内库不匹配（abi=${android.os.Build.SUPPORTED_ABIS.joinToString()}）"
					else -> "${err?.javaClass?.simpleName ?: "未知错误"}: ${err?.message}"
				}
				Log.w(TAG, "内嵌 Tailscale 桥不可用 → 直连模式。原因：$reason")
			} else {
				Log.i(TAG, "内嵌 Tailscale 桥已加载（abi=${android.os.Build.SUPPORTED_ABIS.joinToString()}）")
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
	 * 把网卡清单推给 Go 侧。
	 *
	 * Android 不允许 Go 自己枚举网卡（netlink 被禁），不推这个 tsnet 起不来。
	 * 见 [IfaceReporter]。
	 */
	fun setInterfaces(json: String) {
		val c = load() ?: return
		find(c, "setinterfaces", 1)?.invoke(null, json)
	}

	/** 默认路由的网卡名与网关，Android 上同样取不到，由 Java 侧推给 Go */
	fun setDefaultRoute(ifName: String, gateway: String) {
		val c = load() ?: return
		find(c, "setdefaultroute", 2)?.invoke(null, ifName, gateway)
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
			// 必须在 Go 侧起节点之前把网卡清单推过去，否则 netmon 会因为
			// netlink 被禁而起不来（netlinkrib: permission denied）
			IfaceReporter.register(context)

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

	/** 返回对端链路模式：direct|39.163.49.110:20045|istoreos 或 relay|hongkong|istoreos */
	fun peerLinkInfo(): String = invokeString("peerlinkinfo")

	/** 显式主动向对端 Peer 发送 Disco Ping 进行 NAT 打洞 */
	fun punchHole(): String = invokeString("punchhole")

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
