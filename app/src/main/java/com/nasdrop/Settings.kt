package com.nasdrop

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class ConnectionMode(val title: String, val desc: String) {
	Auto("自动选择", "优先直连，无法直连时使用内嵌 Tailscale"),
	Direct("直连模式", "直接访问 WebDAV 地址"),
	Tailscale("Tailscale 组网", "始终使用内嵌 Tailscale 隧道"),
}

/** 持久化的服务、上传目录及网络配置；密码保留用户原始输入。 */
data class Settings(
	val baseUrl: String = "https://nas.205002.xyz/",
	val navUrl: String = "https://nav.205002.xyz/",
	val user: String = "admin",
	val password: String = "admin",
	val lastDir: String = "",
	val useTailscale: Boolean = true,
	val connectionMode: ConnectionMode = ConnectionMode.Auto,
	val hostname: String = "nasdrop-android",
	val authKey: String = "",
	/** 开启后跳过服务端证书与主机名校验，仅用于明确可信的私有服务。 */
	val trustAllCerts: Boolean = true,
	/** 全局统一配色方案: AURORA, EMERALD, AMBER, TITANIUM, OBSIDIAN */
	val themePalette: String = "AURORA",
) {
	val ready: Boolean get() = baseUrl.isNotBlank() && user.isNotBlank()
	/** 上传结果归属绑定服务根路径与账号，不随网络模式、缓存目录或密码输入而串站。 */
	val serverIdentity: String get() = baseUrl.trim().trimEnd('/') + '\u0000' + user.trim()

	/** 保存前校验 HTTP(S) 地址；连接模式为唯一依据，兼容字段随模式同步。 */
	fun normalized(): Settings {
		val address = baseUrl.trim()
		require(address.isNotEmpty()) { "请输入 WebDAV 服务地址" }
		val url = (if ("://" in address) address else "https://$address").toHttpUrlOrNull()
			?: throw IllegalArgumentException("请输入有效的 HTTP 或 HTTPS 服务地址")
		require(url.username.isEmpty() && url.password.isEmpty()) { "请在账号和密码栏填写凭据" }
		require(url.query == null && url.fragment == null) { "服务地址不能包含查询参数或片段" }
		require(user.trim().isNotEmpty()) { "请输入账号" }
		val normalizedUrl = if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
		val navTrim = navUrl.trim().ifBlank { "https://nav.205002.xyz/" }
		val normalizedNav = if ("://" in navTrim) navTrim else "https://$navTrim"
		return copy(
			baseUrl = normalizedUrl.toString(), navUrl = normalizedNav, user = user.trim(), lastDir = lastDir.trim('/'),
			useTailscale = connectionMode != ConnectionMode.Direct,
			hostname = hostname.trim().ifBlank { "nasdrop-android" }, authKey = authKey.trim(),
			themePalette = themePalette.trim().ifBlank { "AURORA" },
		)
	}
}
