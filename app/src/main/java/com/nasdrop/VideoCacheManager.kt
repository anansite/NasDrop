package com.nasdrop

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File

/**
 * 视频播放专用 LRU 磁盘缓存管理器：
 * 采用 Media3 CacheDataSource 对 HTTP Range 请求进行分片缓存，
 * 避免微卡顿、倒退/复看重复拉流，极大降低 NAS 与隧道网络抖动对播放流畅度的影响。
 */
@OptIn(UnstableApi::class)
object VideoCacheManager {
	@Volatile
	private var simpleCache: SimpleCache? = null

	@Synchronized
	fun getCache(context: Context): SimpleCache {
		if (simpleCache == null) {
			val cacheDir = File(context.cacheDir, "exoplayer_video_cache").apply { mkdirs() }
			val evictor = LeastRecentlyUsedCacheEvictor(256 * 1024 * 1024L) // 256MB LRU 缓存
			val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
			simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
		}
		return simpleCache!!
	}

	fun createDataSourceFactory(context: Context, okHttpClient: OkHttpClient): DataSource.Factory {
		val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
		val cache = getCache(context)
		return CacheDataSource.Factory()
			.setCache(cache)
			.setUpstreamDataSourceFactory(upstreamFactory)
			.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
	}
}
