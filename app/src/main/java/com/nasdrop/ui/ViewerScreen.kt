package com.nasdrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nasdrop.DavEntry
import com.nasdrop.app

/**
 * 看图：左右滑动切换当前目录里的图片，双指缩放、双击复位。
 *
 * 图片走 Coil，用的是 [com.nasdrop.Repository] 里那个带认证头的 OkHttpClient，
 * 所以无论是直连还是内嵌 Tailscale 都不需要额外处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewer(
	images: List<DavEntry>,
	startIndex: Int,
	urlOf: (String) -> String?,
	onBack: () -> Unit,
) {
	if (images.isEmpty()) return
	val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex)) {
		images.size
	}
	val current = images[pager.currentPage.coerceIn(0, images.lastIndex)]

	Box(Modifier.fillMaxSize().background(Color.Black)) {
		HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
			val entry = images[page]
			ZoomableImage(url = urlOf(entry.path), name = entry.name)
		}

		TopAppBar(
			title = {
				Column {
					Text(
						current.name,
						style = MaterialTheme.typography.titleSmall,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						"${pager.currentPage + 1} / ${images.size}",
						style = MaterialTheme.typography.bodySmall,
					)
				}
			},
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
				}
			},
			colors = TopAppBarDefaults.topAppBarColors(
				containerColor = Color.Black.copy(alpha = 0.45f),
				titleContentColor = Color.White,
				navigationIconContentColor = Color.White,
			),
		)
	}
}

@Composable
private fun ZoomableImage(url: String?, name: String) {
	var scale by remember { mutableFloatStateOf(1f) }
	var dx by remember { mutableFloatStateOf(0f) }
	var dy by remember { mutableFloatStateOf(0f) }

	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		AsyncImage(
			model = url,
			contentDescription = name,
			contentScale = ContentScale.Fit,
			modifier = Modifier
				.fillMaxSize()
				.graphicsLayer {
					scaleX = scale
					scaleY = scale
					translationX = dx
					translationY = dy
				}
				.pointerInput(Unit) {
					detectTransformGestures { _, pan, zoom, _ ->
						scale = (scale * zoom).coerceIn(1f, 6f)
						if (scale > 1f) {
							dx += pan.x
							dy += pan.y
						} else {
							dx = 0f
							dy = 0f
						}
					}
				}
				.pointerInput(Unit) {
					detectTapGestures(
						onDoubleTap = {
							if (scale > 1f) {
								scale = 1f; dx = 0f; dy = 0f
							} else {
								scale = 2.5f
							}
						},
					)
				}.padding(0.dp),
		)
	}
}

/**
 * 在线播放：Media3 + OkHttp。
 *
 * 用 OkHttp 作为数据源而不是默认的 HttpURLConnection，是为了复用同一个
 * OkHttpClient——认证头、bridge token、以及内嵌 Tailscale 的本地端口都在那一层，
 * 否则播放器会绕过它们直接失败。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayer(entry: DavEntry, url: String?, onBack: () -> Unit) {
	val context = LocalContext.current
	val http = context.app.repo.http()

	val player = remember(url) {
		val factory = DefaultMediaSourceFactory(context)
			.setDataSourceFactory(OkHttpDataSource.Factory(http))
		ExoPlayer.Builder(context)
			.setMediaSourceFactory(factory)
			.build()
			.apply {
				playWhenReady = true
				setMediaItem(MediaItem.fromUri(url ?: ""))
				prepare()
			}
	}

	DisposableEffect(player) {
		onDispose { player.release() }
	}

	Box(Modifier.fillMaxSize().background(Color.Black)) {
		AndroidView(
			factory = { ctx ->
				PlayerView(ctx).apply {
					this.player = player
					useController = true
					setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
				}
			},
			modifier = Modifier.fillMaxSize(),
		)

		TopAppBar(
			title = {
				Text(
					entry.name,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			},
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
				}
			},
			colors = TopAppBarDefaults.topAppBarColors(
				containerColor = Color.Black.copy(alpha = 0.45f),
				titleContentColor = Color.White,
				navigationIconContentColor = Color.White,
			),
			modifier = Modifier.fillMaxWidth(),
		)
	}
}
