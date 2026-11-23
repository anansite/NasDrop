package com.nasdrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Surface
import com.nasdrop.hasAudio
import com.nasdrop.hasMemo
import com.nasdrop.audioDurationText
import com.nasdrop.videoDurationText
import coil.compose.AsyncImage
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory
import com.nasdrop.NasFolderIcon

/**
 * 夸克风格智能文件缩略图 / 图标：
 * - 图片/视频：尝试加载高清缩略图与海报，带精致圆角（微小圆角）；视频带半透明播放角标；
 * - 文档：支持专属视觉徽标（PDF 红标、Word 蓝标 W、Excel 绿标 X、PPT 橙标 P、APK 绿色安卓标、ZIP 压缩包）；
 * - 文件夹：暖黄微渐变网盘文件夹。
 */
@Composable
fun FileThumbnail(
	entry: DavEntry,
	urlOf: (String) -> String?,
	directUrlOf: ((String) -> String?)? = null,
	onThumbnailError: ((String) -> Unit)? = null,
	size: Dp = 44.dp,
	customIconKey: String? = null,
	modifier: Modifier = Modifier,
) {
	val cornerRadius = 6.dp

	when {
		// 1. 图片真实缩略图（仅加载 20KB 微缩图，严禁在列表拉取几十MB原图）
		entry.isImage -> {
			val thumbTarget = urlOf(entry.path)
			var thumbFailed by remember(entry.path) { mutableStateOf(false) }
			val actualData = if (!thumbFailed) thumbTarget else null

			if (!actualData.isNullOrBlank()) {
				Box(
					modifier = modifier
						.size(size)
						.clip(RoundedCornerShape(cornerRadius))
						.background(Color(0xFFF2F3F5)),
					contentAlignment = Alignment.Center,
				) {
					// 占位图标：在图片未加载完成前展示轻量矢量标，绝无黑块空缺感
					Icon(
						imageVector = Icons.Rounded.Image,
						contentDescription = null,
						tint = Color(0xFFC9CDD4),
						modifier = Modifier.size(size * 0.48f),
					)
					AsyncImage(
						model = ImageRequest.Builder(LocalContext.current)
							.data(actualData)
							.memoryCacheKey("thumb:${entry.path.trim('/')}")
							.diskCacheKey("thumb:${entry.path.trim('/')}")
							.size(240, 240)
							.precision(coil.size.Precision.INEXACT)
							.allowRgb565(true)
							.crossfade(false)
							.build(),
						contentDescription = entry.name,
						contentScale = ContentScale.Crop,
						onError = {
							thumbFailed = true
							onThumbnailError?.invoke(entry.path)
						},
						modifier = Modifier.fillMaxSize(),
					)
					if (size >= 36.dp) {
						MediaThumbBadges(
							entry = entry,
							modifier = Modifier.fillMaxSize().padding(if (size >= 64.dp) 4.dp else 2.dp),
							compact = size < 64.dp,
						)
					}
				}
			} else {
				FileIconByEntry(entry = entry, size = size, customIconKey = customIconKey, urlOf = urlOf, directUrlOf = directUrlOf, modifier = modifier)
			}
		}

		// 2. 视频缩略图：优先用服务端生成的缩略图（OpenList 的 /d/...?type=thumb，真图片、零视频下载）；
		//    加载中/失败回退渐变播放图标，绝不黑块、绝不卡列表。urlFor 会返回服务端缩略图直链，
		//    这里只需按普通图片加载即可，无需客户端视频帧解码。
		entry.isVideo -> {
			val url = urlOf(entry.path)
			var isError by remember(entry.path) { mutableStateOf(false) }

			if (!isError && !url.isNullOrBlank()) {
				Box(
					modifier = modifier
						.size(size)
						.clip(RoundedCornerShape(cornerRadius))
						.background(Brush.linearGradient(listOf(Color(0xFF391085), Color(0xFF1D2129)))),
					contentAlignment = Alignment.Center,
				) {
					// 占位：播放图标，缩略图未就绪时可见
					Icon(
						imageVector = Icons.Rounded.PlayArrow,
						contentDescription = null,
						tint = Color(0xB3FFFFFF),
						modifier = Modifier.size(size * 0.58f),
					)
					AsyncImage(
						model = ImageRequest.Builder(LocalContext.current)
							.data(url)
							.memoryCacheKey("thumb:${entry.path.trim('/')}")
							.diskCacheKey("thumb:${entry.path.trim('/')}")
							.size(240, 240)
							.precision(coil.size.Precision.INEXACT)
							.allowRgb565(true)
							.crossfade(false)
							.build(),
						contentDescription = entry.name,
						contentScale = ContentScale.Crop,
						onError = {
							isError = true
							onThumbnailError?.invoke(entry.path)
						},
						modifier = Modifier.fillMaxSize(),
					)
					// 视频播放指示标与徽章：置于 AsyncImage 之上，缩略图加载完成后永不消失！
					MediaThumbBadges(
						entry = entry,
						modifier = Modifier.fillMaxSize().padding(if (size >= 64.dp) 4.dp else 2.dp),
						compact = size < 64.dp,
					)
				}
			} else {
				FileIconByEntry(entry = entry, size = size, customIconKey = customIconKey, urlOf = urlOf, directUrlOf = directUrlOf, modifier = modifier)
			}
		}

		// 3. 其它文件类型：秒级专属高辨识度徽标，零网络流量消耗
		else -> {
			FileIconByEntry(entry = entry, size = size, customIconKey = customIconKey, urlOf = urlOf, directUrlOf = directUrlOf, modifier = modifier)
		}
	}
}

/**
 * 依据 DavEntry 的具体后缀渲染夸克同款高辨识度专属卡片图标
 */
@Composable
fun FileIconByEntry(
	entry: DavEntry,
	size: Dp = 44.dp,
	customIconKey: String? = null,
	urlOf: ((String) -> String?)? = null,
	directUrlOf: ((String) -> String?)? = null,
	modifier: Modifier = Modifier,
) {
	val ext = entry.ext.lowercase()

	when {
		// 文件夹（支持 NAS 常用预置自定义图标与本地上传自定义图标）
		entry.isDir -> {
			val customKey = customIconKey ?: ""
			if (customKey.startsWith("http") || customKey.contains(".nasdrop_icons") || customKey.endsWith(".png") || customKey.endsWith(".jpg")) {
				val iconUrl = if (customKey.startsWith("http")) customKey else (directUrlOf?.invoke(customKey) ?: urlOf?.invoke(customKey) ?: customKey)
				var loadFailed by remember(iconUrl) { mutableStateOf(false) }
				if (!loadFailed) {
					Box(
						modifier = modifier
							.size(size)
							.clip(RoundedCornerShape(8.dp))
							.background(Color(0xFFE8EEF5)),
						contentAlignment = Alignment.Center,
					) {
						AsyncImage(
							model = ImageRequest.Builder(LocalContext.current)
								.data(iconUrl)
								.crossfade(true)
								.build(),
							contentDescription = entry.displayName,
							contentScale = ContentScale.Crop,
							onError = { loadFailed = true },
							modifier = Modifier.fillMaxSize()
						)
					}
				} else {
					Box(
						modifier = modifier
							.size(size)
							.clip(RoundedCornerShape(8.dp))
							.background(Color(0xFFFFB6C1).copy(alpha = 0.22f)),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							imageVector = Icons.Rounded.Folder,
							contentDescription = entry.displayName,
							tint = Color(0xFFFF69B4),
							modifier = Modifier.size(size * 0.58f),
						)
					}
				}
			} else {
				val custom = if (customKey.isNotEmpty()) NasFolderIcon.fromKey(customKey) else null
				if (custom != null && custom != NasFolderIcon.Default) {
					Box(
						modifier = modifier
							.size(size)
							.clip(RoundedCornerShape(8.dp))
							.background(custom.bgColor),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							imageVector = custom.icon,
							contentDescription = custom.label,
							tint = custom.tintColor,
							modifier = Modifier.size(size * 0.58f),
						)
					}
				} else if (entry.isAlbum) {
					val brush = Brush.linearGradient(listOf(Color(0xFFFF6B8B), Color(0xFFFF3D71)))
					GradientBadge(size = size, brush = brush, modifier = modifier) {
						Icon(Icons.Rounded.CameraAlt, contentDescription = "相册", tint = Color.White, modifier = Modifier.size(size * 0.54f))
					}
				} else {
					FolderVectorIcon(size = size, modifier = modifier)
				}
			}
		}

		// PDF 夸克同款红白卡片
		ext == "pdf" -> {
			QuarkDocBadge(
				size = size,
				bgColor = Color(0xFFFFF0F0),
				borderColor = Color(0xFFFFCDD2),
				brandColor = Color(0xFFE53935),
				label = "PDF",
				modifier = modifier,
			)
		}

		// Word 夸克同款蓝白卡片 W
		ext in listOf("doc", "docx") -> {
			QuarkDocBadge(
				size = size,
				bgColor = Color(0xFFF0F5FF),
				borderColor = Color(0xFFADC6FF),
				brandColor = Color(0xFF2F54EB),
				label = "W",
				modifier = modifier,
			)
		}

		// Excel 夸克同款绿白卡片 X
		ext in listOf("xls", "xlsx", "csv") -> {
			QuarkDocBadge(
				size = size,
				bgColor = Color(0xFFF6FFED),
				borderColor = Color(0xFFB7EB8F),
				brandColor = Color(0xFF52C41A),
				label = "X",
				modifier = modifier,
			)
		}

		// PPT 夸克同款橙白卡片 P
		ext in listOf("ppt", "pptx") -> {
			QuarkDocBadge(
				size = size,
				bgColor = Color(0xFFFFF7E6),
				borderColor = Color(0xFFFFD591),
				brandColor = Color(0xFFFA8C16),
				label = "P",
				modifier = modifier,
			)
		}

		// APK 安卓应用专属卡片
		ext == "apk" -> {
			Box(
				modifier = modifier
					.size(size)
					.clip(RoundedCornerShape(6.dp))
					.background(Color(0xFFE8F8F2))
					.border(0.5.dp, Color(0xFFB5ECD7), RoundedCornerShape(6.dp)),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = Icons.Rounded.Android,
					contentDescription = "APK",
					tint = Color(0xFF00B578),
					modifier = Modifier.size(size * 0.62f),
				)
			}
		}

		// 代码与网页（php, js, html, css, py, json, xml, sh）
		ext in listOf("php", "js", "html", "htm", "css", "py", "java", "kt", "go", "json", "xml", "sh") -> {
			Box(
				modifier = modifier
					.size(size)
					.clip(RoundedCornerShape(6.dp))
					.background(Color(0xFFFFFBE6))
					.border(0.5.dp, Color(0xFFFFE58F), RoundedCornerShape(6.dp)),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = Icons.Rounded.Code,
					contentDescription = "代码",
					tint = Color(0xFFD48806),
					modifier = Modifier.size(size * 0.54f),
				)
			}
		}

		// 视频（若无缩略图回退为深邃海报蓝卡片）
		entry.isVideo -> {
			val brush = Brush.linearGradient(listOf(Color(0xFF391085), Color(0xFF1D2129)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.PlayArrow, contentDescription = "视频", tint = Color.White, modifier = Modifier.size(size * 0.58f))
			}
		}

		// 音频音乐
		entry.isAudio -> {
			val brush = Brush.linearGradient(listOf(Color(0xFF52C41A), Color(0xFF237804)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.MusicNote, contentDescription = "音频", tint = Color.White, modifier = Modifier.size(size * 0.54f))
			}
		}

		// 压缩包
		entry.isArchive -> {
			val brush = Brush.linearGradient(listOf(Color(0xFFB37FEB), Color(0xFF722ED1)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.FolderZip, contentDescription = "压缩包", tint = Color.White, modifier = Modifier.size(size * 0.54f))
			}
		}

		// 通用文件
		else -> {
			FileIcon(category = entry.category, isDir = false, isAlbum = false, size = size, modifier = modifier)
		}
	}
}

/**
 * 夸克网盘同款经典文档标签卡（白底、微边框、彩色主品牌字母）
 */
@Composable
private fun QuarkDocBadge(
	size: Dp,
	bgColor: Color,
	borderColor: Color,
	brandColor: Color,
	label: String,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.size(size)
			.clip(RoundedCornerShape(6.dp))
			.background(bgColor)
			.border(0.5.dp, borderColor, RoundedCornerShape(6.dp)),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = label,
			color = brandColor,
			fontWeight = FontWeight.Black,
			fontSize = if (label.length > 2) (size.value * 0.28f).sp else (size.value * 0.44f).sp,
			fontFamily = FontFamily.SansSerif,
		)
	}
}

/**
 * 渐变底板徽章容器
 */
@Composable
private fun GradientBadge(
	size: Dp,
	brush: Brush,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	Box(
		modifier = modifier
			.size(size)
			.clip(RoundedCornerShape(6.dp))
			.background(brush),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}

/**
 * 兼容旧组件的 FileIcon
 */
@Composable
fun FileIcon(
	category: FileCategory,
	isDir: Boolean,
	isAlbum: Boolean = false,
	size: Dp = 44.dp,
	modifier: Modifier = Modifier,
) {
	when {
		isDir && isAlbum -> {
			val brush = Brush.linearGradient(listOf(Color(0xFFFF6B8B), Color(0xFFFF3D71)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.CameraAlt, contentDescription = "相册", tint = Color.White, modifier = Modifier.size(size * 0.54f))
			}
		}
		isDir -> {
			FolderVectorIcon(size = size, modifier = modifier)
		}
		category == FileCategory.Video -> {
			val brush = Brush.linearGradient(listOf(Color(0xFF391085), Color(0xFF1D2129)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.PlayArrow, contentDescription = "视频", tint = Color.White, modifier = Modifier.size(size * 0.58f))
			}
		}
		category == FileCategory.Image -> {
			val brush = Brush.linearGradient(listOf(Color(0xFF13C2C2), Color(0xFF08979C)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.Image, contentDescription = "图片", tint = Color.White, modifier = Modifier.size(size * 0.54f))
			}
		}
		category == FileCategory.Document -> {
			QuarkDocBadge(
				size = size,
				bgColor = Color(0xFFF0F5FF),
				borderColor = Color(0xFFADC6FF),
				brandColor = Color(0xFF2F54EB),
				label = "DOC",
				modifier = modifier,
			)
		}
		category == FileCategory.Audio -> {
			val brush = Brush.linearGradient(listOf(Color(0xFF52C41A), Color(0xFF237804)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.MusicNote, contentDescription = "音频", tint = Color.White, modifier = Modifier.size(size * 0.54f))
			}
		}
		category == FileCategory.Archive -> {
			val brush = Brush.linearGradient(listOf(Color(0xFFB37FEB), Color(0xFF722ED1)))
			GradientBadge(size = size, brush = brush, modifier = modifier) {
				Icon(Icons.Rounded.FolderZip, contentDescription = "压缩包", tint = Color.White, modifier = Modifier.size(size * 0.54f))
			}
		}
		else -> {
			Box(
				modifier = modifier
					.size(size)
					.clip(RoundedCornerShape(6.dp))
					.background(Color(0xFFF2F3F5))
					.border(0.5.dp, Color(0xFFE5E6EB), RoundedCornerShape(6.dp)),
				contentAlignment = Alignment.Center,
			) {
				Icon(Icons.Rounded.Description, contentDescription = "文件", tint = Color(0xFF86909C), modifier = Modifier.size(size * 0.54f))
			}
		}
	}
}

/**
 * 夸克同款清爽网盘文件夹矢量图标（带柔和折角与内衬）
 */
@Composable
fun FolderVectorIcon(
	size: Dp,
	modifier: Modifier = Modifier,
) {
	val backBrush = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFF57C00)))
	val frontBrush = Brush.linearGradient(listOf(Color(0xFFFFCA28), Color(0xFFFFA000)))

	Box(
		modifier = modifier
			.size(size)
			.clip(RoundedCornerShape(6.dp))
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
				color = Color.White.copy(alpha = 0.5f),
				start = Offset(w * 0.08f, h * 0.38f),
				end = Offset(w * 0.40f, h * 0.38f),
				strokeWidth = 1.5.dp.toPx(),
			)
		}
	}
}

/**
 * 媒体缩略图精致角标：
 * - 视频：右下角常驻半透明深色微胶囊（播放图标 + 时长）
 * - 伴随录音：左下角浅蓝高辨识徽标（麦克风图标 + 时长）
 * - 记事备忘：橙色笔记本微徽标
 */
@Composable
fun MediaThumbBadges(
	entry: DavEntry,
	modifier: Modifier = Modifier,
	compact: Boolean = false,
) {
	val hasAudio = entry.hasAudio
	val hasMemo = entry.hasMemo
	val isVideo = entry.isVideo

	if (!hasAudio && !hasMemo && !isVideo) return

	Box(modifier = modifier) {
		// 1. 视频角标：右下角常驻圆润半透明深色微胶囊（播放图标 + 时长）
		if (isVideo) {
			val durationText = if (!compact) entry.videoDurationText else null
			Surface(
				shape = RoundedCornerShape(10.dp),
				color = Color.Black.copy(alpha = 0.68f),
				border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.20f)),
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(bottom = 2.dp, end = 2.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(
						horizontal = if (durationText != null) 5.dp else 4.dp,
						vertical = 2.dp,
					),
				) {
					Icon(
						imageVector = Icons.Rounded.PlayArrow,
						contentDescription = "视频",
						tint = Color.White,
						modifier = Modifier.size(if (compact) 11.dp else 13.dp),
					)
					if (durationText != null) {
						Text(
							text = durationText,
							color = Color.White,
							fontSize = 9.5.sp,
							fontWeight = FontWeight.SemiBold,
							modifier = Modifier.padding(start = 2.dp),
						)
					}
				}
			}
		}

		// 2. 伴随录音与文字备忘：左下角一体化圆润毛玻璃胶囊（排版统一规范）
		if (hasAudio || hasMemo) {
			Surface(
				shape = RoundedCornerShape(10.dp),
				color = Color.Black.copy(alpha = 0.68f),
				border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.20f)),
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(bottom = 2.dp, start = 2.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
					horizontalArrangement = Arrangement.spacedBy(3.dp),
				) {
					if (hasAudio) {
						val audioDur = if (!compact) entry.audioDurationText else null
						Icon(
							imageVector = Icons.Rounded.Mic,
							contentDescription = "伴随录音",
							tint = Color(0xFF38BDF8),
							modifier = Modifier.size(if (compact) 10.dp else 12.dp),
						)
						if (audioDur != null) {
							Text(
								text = audioDur,
								color = Color.White,
								fontSize = 9.5.sp,
								fontWeight = FontWeight.SemiBold,
							)
						}
					}

					if (hasAudio && hasMemo) {
						Box(
							modifier = Modifier
								.width(1.dp)
								.height(9.dp)
								.background(Color.White.copy(alpha = 0.25f)),
						)
					}

					if (hasMemo) {
						Icon(
							imageVector = Icons.Rounded.EditNote,
							contentDescription = "文字备忘",
							tint = Color(0xFF34D399),
							modifier = Modifier.size(if (compact) 11.dp else 13.dp),
						)
					}
				}
			}
		}
	}
}
