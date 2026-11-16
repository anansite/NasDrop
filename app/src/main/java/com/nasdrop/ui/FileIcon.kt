package com.nasdrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory

/**
 * 智能文件缩略图 / 图标：
 * - 若为图片，尝试加载真实高清缩略图，并带优雅圆角与阴影；
 * - 加载中或失败时自动平滑回退至商业级矢量微渐变图标；
 * - 文件夹与其他文件类型渲染定制微渐变图标。
 */
@Composable
fun FileThumbnail(
	entry: DavEntry,
	urlOf: (String) -> String?,
	size: Dp = 44.dp,
	modifier: Modifier = Modifier,
) {
	val cornerRadius = size * 0.28f

	if (entry.isImage) {
		val url = urlOf(entry.path)
		var isError by remember(entry.path) { mutableStateOf(false) }

		if (!isError && !url.isNullOrBlank()) {
			Box(
				modifier = modifier
					.size(size)
					.shadow(elevation = 2.dp, shape = RoundedCornerShape(cornerRadius), ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
					.clip(RoundedCornerShape(cornerRadius))
					.background(Color(0xFFE5E6EB)),
				contentAlignment = Alignment.Center,
			) {
				AsyncImage(
					model = url,
					contentDescription = entry.name,
					contentScale = ContentScale.Crop,
					onError = { isError = true },
					modifier = Modifier.fillMaxSize(),
				)
			}
		} else {
			FileIcon(category = FileCategory.Image, isDir = false, size = size, modifier = modifier)
		}
	} else {
		FileIcon(
			category = entry.category,
			isDir = entry.isDir,
			isAlbum = entry.isAlbum,
			size = size,
			modifier = modifier,
		)
	}
}

/**
 * 商业级 2.5D / Duotone 微渐变网盘矢量图标组件。
 * 彻底告别丑陋廉价的系统 Emoji，对标一线商业网盘质感。
 */
@Composable
fun FileIcon(
	category: FileCategory,
	isDir: Boolean,
	isAlbum: Boolean = false,
	size: Dp = 44.dp,
	modifier: Modifier = Modifier,
) {
	val cornerRadius = size * 0.28f

	when {
		// 1. 专属智能相册
		isDir && isAlbum -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFFFF6B8B), Color(0xFFFF3D71)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFFFF3D71).copy(alpha = 0.25f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.CameraAlt,
					contentDescription = "相册",
					tint = Color.White,
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}

		// 2. 常规文件夹（琥珀暖黄渐变 + 圆润折角质感）
		isDir -> {
			FolderVectorIcon(size = size, modifier = modifier)
		}

		// 3. 高清视频（火粉渐变 + 居中播放三角胶囊）
		category == FileCategory.Video -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFFFF85C0), Color(0xFFEB2F96)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFFEB2F96).copy(alpha = 0.25f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.PlayArrow,
					contentDescription = "视频",
					tint = Color.White,
					modifier = Modifier.size(size * 0.58f),
				)
			}
		}

		// 4. 图片文件（极光青蓝渐变 + 山川太阳）
		category == FileCategory.Image -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFF36CFC9), Color(0xFF08979C)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFF08979C).copy(alpha = 0.25f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.Image,
					contentDescription = "图片",
					tint = Color.White,
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}

		// 5. 办公文档（商务钴蓝渐变 + 规整文本条）
		category == FileCategory.Document -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFF597EF7), Color(0xFF1D39C4)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFF1D39C4).copy(alpha = 0.25f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.Description,
					contentDescription = "文档",
					tint = Color.White,
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}

		// 6. 音乐音频（薄荷翠绿渐变 + 动感音符）
		category == FileCategory.Audio -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFF52C41A), Color(0xFF237804)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFF52C41A).copy(alpha = 0.25f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.MusicNote,
					contentDescription = "音频",
					tint = Color.White,
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}

		// 7. 压缩文件（神秘典雅紫渐变 + 压缩包拉链）
		category == FileCategory.Archive -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFFB37FEB), Color(0xFF722ED1)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFF722ED1).copy(alpha = 0.25f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.FolderZip,
					contentDescription = "压缩包",
					tint = Color.White,
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}

		// 8. 其他普通文件（柔和银灰渐变）
		else -> {
			val brush = Brush.linearGradient(
				colors = listOf(Color(0xFF8C9BAE), Color(0xFF5F6E82)),
				start = Offset.Zero,
				end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
			)
			GradientIconContainer(size = size, cornerRadius = cornerRadius, brush = brush, shadowColor = Color(0xFF5F6E82).copy(alpha = 0.2f), modifier = modifier) {
				Icon(
					imageVector = Icons.Rounded.Description,
					contentDescription = "文件",
					tint = Color.White,
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}
	}
}

/**
 * 渐变底板容器封装
 */
@Composable
private fun GradientIconContainer(
	size: Dp,
	cornerRadius: Dp,
	brush: Brush,
	shadowColor: Color,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	Box(
		modifier = modifier
			.size(size)
			.shadow(elevation = 3.dp, shape = RoundedCornerShape(cornerRadius), ambientColor = shadowColor, spotColor = shadowColor)
			.clip(RoundedCornerShape(cornerRadius))
			.background(brush),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}

/**
 * 定制高保真微渐变网盘文件夹矢量图标（带圆润折角与内衬）
 */
@Composable
private fun FolderVectorIcon(
	size: Dp,
	modifier: Modifier = Modifier,
) {
	val cornerRadius = size * 0.26f
	val backBrush = Brush.linearGradient(
		colors = listOf(Color(0xFFFFB300), Color(0xFFF57C00)),
	)
	val frontBrush = Brush.linearGradient(
		colors = listOf(Color(0xFFFFCA28), Color(0xFFFFA000)),
	)

	Box(
		modifier = modifier
			.size(size)
			.shadow(elevation = 3.dp, shape = RoundedCornerShape(cornerRadius), ambientColor = Color(0xFFFFA000).copy(alpha = 0.3f), spotColor = Color(0xFFFFA000).copy(alpha = 0.3f))
			.clip(RoundedCornerShape(cornerRadius))
			.background(backBrush),
		contentAlignment = Alignment.Center,
	) {
		Canvas(modifier = Modifier.fillMaxSize()) {
			val w = this.size.width
			val h = this.size.height

			// 前盖折角路径
			val path = Path().apply {
				moveTo(0f, h * 0.36f)
				lineTo(w * 0.42f, h * 0.36f)
				cubicTo(w * 0.48f, h * 0.36f, w * 0.52f, h * 0.44f, w * 0.58f, h * 0.44f)
				lineTo(w, h * 0.44f)
				lineTo(w, h)
				lineTo(0f, h)
				close()
			}
			drawPath(path, brush = frontBrush, style = Fill)

			// 顶部微高光线条
			drawLine(
				color = Color.White.copy(alpha = 0.45f),
				start = Offset(w * 0.08f, h * 0.38f),
				end = Offset(w * 0.40f, h * 0.38f),
				strokeWidth = 2.dp.toPx(),
			)
		}
	}
}
