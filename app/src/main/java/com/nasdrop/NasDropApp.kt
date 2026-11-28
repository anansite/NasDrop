package com.nasdrop

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import java.util.concurrent.atomic.AtomicInteger

class NasDropApp : Application(), ImageLoaderFactory {

	lateinit var prefs: Prefs
		private set
	lateinit var repo: Repository
		private set

	private val startedActivities = AtomicInteger(0)
	val isInForeground: Boolean get() = startedActivities.get() > 0

	override fun onCreate() {
		super.onCreate()
		prefs = Prefs(this)
		repo = Repository(this)
		PhotoMetadataStore.init(this)
		registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
			override fun onActivityStarted(activity: Activity) { startedActivities.incrementAndGet() }
			override fun onActivityStopped(activity: Activity) {
				startedActivities.updateAndGet { cur -> (cur - 1).coerceAtLeast(0) }
			}
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
			override fun onActivityResumed(activity: Activity) {}
			override fun onActivityPaused(activity: Activity) {}
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
			override fun onActivityDestroyed(activity: Activity) {}
		})
	}

	override fun newImageLoader(): ImageLoader = repo.imageLoader
}

/** 便捷访问 */
val Context.app: NasDropApp
	get() = applicationContext as NasDropApp
