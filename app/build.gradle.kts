plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
}

android {
	namespace = "com.nasdrop"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.nasdrop"
		minSdk = 26
		targetSdk = 35
		val runNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
			?: providers.environmentVariable("BUILD_NUMBER").orNull?.toIntOrNull()
			?: 241
		versionCode = runNumber
		versionName = "1.0.$runNumber"

		val defaultWebDavUrl = providers.environmentVariable("DEFAULT_WEBDAV_URL").orNull
			?: "http://nas.205002.xyz/"
		val defaultWebDavUser = providers.environmentVariable("DEFAULT_WEBDAV_USER").orNull
			?: "admin"
		val defaultWebDavPass = providers.environmentVariable("DEFAULT_WEBDAV_PASS").orNull
			?: "admin"

		buildConfigField("String", "DEFAULT_WEBDAV_URL", "\"$defaultWebDavUrl\"")
		buildConfigField("String", "DEFAULT_WEBDAV_USER", "\"$defaultWebDavUser\"")
		buildConfigField("String", "DEFAULT_WEBDAV_PASS", "\"$defaultWebDavPass\"")

		ndk {
			abiFilters += listOf("arm64-v8a", "x86_64")
		}
	}

	splits {
		abi {
			isEnable = true
			reset()
			include("arm64-v8a", "x86_64")
			isUniversalApk = true
		}
	}

	// 仓库里放了一张固定的自签名证书，这样每次 CI 打出来的包签名一致，
	// 手机上可以直接覆盖安装升级。正式发布签名通过 GitHub Secrets 动态注入，私钥不入版本库。
	val personalStore = rootProject.file("signing/nasdrop.jks")
	if (personalStore.exists()) {
		signingConfigs.create("release") {
			storeFile = personalStore
			storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "nasdrop123"
			keyAlias = System.getenv("KEY_ALIAS") ?: "nasdrop"
			keyPassword = System.getenv("KEY_PASSWORD") ?: "nasdrop123"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	packaging {
		resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
	}
}

dependencies {
	testImplementation("junit:junit:4.13.2")
	testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.activity:activity-compose:1.9.3")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

	val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
	implementation(composeBom)
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.foundation:foundation")
	implementation("androidx.compose.animation:animation")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-extended")
	debugImplementation("androidx.compose.ui:ui-tooling")

	implementation("androidx.datastore:datastore-preferences:1.1.1")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("io.coil-kt:coil-compose:2.7.0")
	implementation("io.coil-kt:coil-video:2.7.0")

	// 一次性授权页用 Custom Tabs 打开，比丢给 ACTION_VIEW 体验好
	implementation("androidx.browser:browser:1.8.0")

	implementation("androidx.media3:media3-exoplayer:1.5.0")
	implementation("androidx.media3:media3-ui:1.5.0")
	// 让播放器和业务共用同一个 OkHttpClient（认证头、bridge token 都在那一层）
	implementation("androidx.media3:media3-datasource-okhttp:1.5.0")

	// Tailscale 桥。由 ./build-aar.sh 生成；文件不存在时 App 会自动退化成
	// “直连模式”（依赖手机自己装着的 Tailscale），因此没有 AAR 也能编译调试。
	val bridgeAar = file("libs/bridge.aar")
	if (bridgeAar.exists()) {
		implementation(files(bridgeAar))
	} else {
		logger.lifecycle("警告：app/libs/bridge.aar 不存在，本次构建不含内嵌 Tailscale。")
	}
}
