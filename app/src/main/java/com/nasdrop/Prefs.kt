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

class Prefs(private val context: Context) {
	companion object {
		@Volatile var lastSettings: Settings? = null
	}

	private object Keys {
		val baseUrl = stringPreferencesKey("base_url")
		val navUrl = stringPreferencesKey("nav_url")
		val user = stringPreferencesKey("user")
		val password = stringPreferencesKey("password")
		val lastDir = stringPreferencesKey("last_dir")
		val useTailscale = booleanPreferencesKey("use_tailscale")
		val connectionMode = stringPreferencesKey("connection_mode")
		val hostname = stringPreferencesKey("hostname")
		val authKey = stringPreferencesKey("auth_key")
		val trustAllCerts = booleanPreferencesKey("trust_all_certs")
		val themePalette = stringPreferencesKey("theme_palette")
		val viewMode = stringPreferencesKey("view_mode")
		val sortOrder = stringPreferencesKey("sort_order")
		val albumOrder = stringPreferencesKey("album_order")
		val mediaOrder = stringPreferencesKey("media_order")
	}

	val settings: Flow<Settings> = context.dataStore.data.map {
		val s = it.toSettings()
		lastSettings = s
		s
	}

	suspend fun current(): Settings = settings.first()

	/** 一次事务保存完整配置，观察者只会收到完整的新状态；失败时保留旧配置。 */
	suspend fun save(settings: Settings) {
		val normalized = settings.normalized()
		context.dataStore.edit {
			it[Keys.baseUrl] = normalized.baseUrl
			it[Keys.navUrl] = normalized.navUrl
			it[Keys.user] = normalized.user
			it[Keys.password] = normalized.password
			it[Keys.lastDir] = normalized.lastDir
			it[Keys.connectionMode] = normalized.connectionMode.name
			it[Keys.useTailscale] = normalized.useTailscale
			it[Keys.hostname] = normalized.hostname
			it[Keys.authKey] = normalized.authKey
			it[Keys.trustAllCerts] = normalized.trustAllCerts
			it[Keys.themePalette] = normalized.themePalette
		}
	}

	suspend fun setServer(baseUrl: String, user: String, password: String) {
		val server = Settings(baseUrl = baseUrl, user = user, password = password).normalized()
		context.dataStore.edit {
			it[Keys.baseUrl] = server.baseUrl
			it[Keys.user] = server.user
			it[Keys.password] = password
		}
	}

	suspend fun setConnectionMode(mode: ConnectionMode) {
		context.dataStore.edit {
			it[Keys.connectionMode] = mode.name
			it[Keys.useTailscale] = (mode != ConnectionMode.Direct)
		}
	}

	suspend fun setTailscale(enabled: Boolean, hostname: String, authKey: String) {
		context.dataStore.edit {
			// 旧入口只在尚未选择模式时迁移开关；不得覆盖明确选择的 Tailscale 模式。
			val mode = it[Keys.connectionMode]?.let { name -> ConnectionMode.entries.firstOrNull { mode -> mode.name == name } }
				?: if (enabled) ConnectionMode.Auto else ConnectionMode.Direct
			it[Keys.connectionMode] = mode.name
			it[Keys.useTailscale] = mode != ConnectionMode.Direct
			it[Keys.hostname] = hostname.trim().ifBlank { "nasdrop-android" }
			it[Keys.authKey] = authKey.trim()
		}
	}

	suspend fun setTrustAllCerts(enable: Boolean) {
		context.dataStore.edit { it[Keys.trustAllCerts] = enable }
	}

	suspend fun setLastDir(dir: String) {
		context.dataStore.edit { it[Keys.lastDir] = dir.trim('/') }
	}

	suspend fun getViewMode(): String? = context.dataStore.data.map { it[Keys.viewMode] }.first()
	suspend fun setViewMode(mode: String) {
		context.dataStore.edit { it[Keys.viewMode] = mode }
	}

	suspend fun getSortOrder(): String? = context.dataStore.data.map { it[Keys.sortOrder] }.first()
	suspend fun setSortOrder(order: String) {
		context.dataStore.edit { it[Keys.sortOrder] = order }
	}

	suspend fun getAlbumOrder(): List<String> = runCatching {
		val json = context.dataStore.data.map { it[Keys.albumOrder] }.first()
		if (json.isNullOrBlank()) emptyList()
		else org.json.JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
	}.getOrDefault(emptyList())

	suspend fun setAlbumOrder(order: List<String>) {
		context.dataStore.edit {
			val arr = org.json.JSONArray()
			order.forEach { arr.put(it) }
			it[Keys.albumOrder] = arr.toString()
		}
	}

	suspend fun getMediaOrder(): List<String> = runCatching {
		val json = context.dataStore.data.map { it[Keys.mediaOrder] }.first()
		if (json.isNullOrBlank()) emptyList()
		else org.json.JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
	}.getOrDefault(emptyList())

	suspend fun setMediaOrder(order: List<String>) {
		context.dataStore.edit {
			val arr = org.json.JSONArray()
			order.forEach { arr.put(it) }
			it[Keys.mediaOrder] = arr.toString()
		}
	}

	private fun Preferences.toSettings(): Settings {
		val modeStr = this[Keys.connectionMode]
		val mode = try {
			if (modeStr != null) ConnectionMode.valueOf(modeStr)
			else if (this[Keys.useTailscale] == false) ConnectionMode.Direct
			else ConnectionMode.Auto
		} catch (_: Exception) {
			ConnectionMode.Auto
		}

		return Settings(
			baseUrl = this[Keys.baseUrl] ?: Settings().baseUrl,
			navUrl = this[Keys.navUrl] ?: Settings().navUrl,
			user = this[Keys.user] ?: Settings().user,
			password = this[Keys.password] ?: "admin",
			lastDir = this[Keys.lastDir] ?: "",
			useTailscale = mode != ConnectionMode.Direct,
			connectionMode = mode,
			hostname = this[Keys.hostname] ?: "nasdrop-android",
			authKey = this[Keys.authKey] ?: "",
			trustAllCerts = this[Keys.trustAllCerts] ?: true,
			themePalette = this[Keys.themePalette] ?: "AURORA",
		)
	}

}
