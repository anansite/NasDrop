package com.nasdrop

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory

class NasDropApp : Application(), ImageLoaderFactory {

	lateinit var prefs: Prefs
		private set
	lateinit var repo: Repository
		private set

	override fun onCreate() {
		super.onCreate()
		prefs = Prefs(this)
		repo = Repository(this)
		PhotoMetadataStore.init(this)
	}

	override fun newImageLoader(): ImageLoader = repo.imageLoader
}

/** 便捷访问 */
val Context.app: NasDropApp
	get() = applicationContext as NasDropApp
