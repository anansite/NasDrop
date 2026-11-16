package com.nasdrop

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nasdrop")

data class Settings(
	/** WebDAV 根地址，必须带结尾斜杠，例如 https://nas.205002.xyz/nas/ */
	val baseUrl: String = "https://nas.205002.xyz/nas/",
	val user: String = "admin",
	val password: String = "admin",
	/** 上次选择的上传目录（相对根路径），分享流程默认落在这里 */
	val lastDir: String = "",
	val useTailscale: Boolean = true,
	val hostname: String = "nasdrop-android",
	/** 填了就零交互上线；留空则首次需要打开浏览器登录一次 */
	val authKey: String = "",
	/**
	 * 放行自签证书。流量本身已经跑在内嵌 Tailscale 的加密隧道里，
	 * 对端是自己 tailnet 内的设备；换成正规证书后可以关掉。
	 */
	val trustAllCerts: Boolean = true,
) {
	val ready: Boolean get() = baseUrl.isNotBlank() && user.isNotBlank()
}

class Prefs(private val context: Context) {

	private object Keys {
		val baseUrl = stringPreferencesKey("base_url")
		val user = stringPreferencesKey("user")
		val password = stringPreferencesKey("password")
		val lastDir = stringPreferencesKey("last_dir")
		val useTailscale = booleanPreferencesKey("use_tailscale")
		val hostname = stringPreferencesKey("hostname")
		val authKey = stringPreferencesKey("auth_key")
		val trustAllCerts = booleanPreferencesKey("trust_all_certs")
	}

	val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

	suspend fun current(): Settings = settings.first()

	suspend fun setServer(baseUrl: String, user: String, password: String) {
		context.dataStore.edit {
			it[Keys.baseUrl] = normalize(baseUrl)
			it[Keys.user] = user
			it[Keys.password] = password
		}
	}

	suspend fun setTailscale(enabled: Boolean, hostname: String, authKey: String) {
		context.dataStore.edit {
			it[Keys.useTailscale] = enabled
			it[Keys.hostname] = hostname
			it[Keys.authKey] = authKey
		}
	}

	suspend fun setTrustAllCerts(enable: Boolean) {
		context.dataStore.edit { it[Keys.trustAllCerts] = enable }
	}

	suspend fun setLastDir(dir: String) {
		context.dataStore.edit { it[Keys.lastDir] = dir.trim('/') }
	}

	private fun Preferences.toSettings() = Settings(
		baseUrl = this[Keys.baseUrl] ?: Settings().baseUrl,
		user = this[Keys.user] ?: Settings().user,
		password = this[Keys.password] ?: "admin",
		lastDir = this[Keys.lastDir] ?: "",
		useTailscale = this[Keys.useTailscale] ?: true,
		hostname = this[Keys.hostname] ?: "nasdrop-android",
		authKey = this[Keys.authKey] ?: "",
		trustAllCerts = this[Keys.trustAllCerts] ?: true,
	)

	private fun normalize(url: String): String {
		var s = url.trim()
		if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
		if (!s.endsWith("/")) s += "/"
		return s
	}
}
