package com.nasdrop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nasdrop.UpdateInfo

@Composable
fun UpdateDialog(
	info: UpdateInfo,
	isDownloading: Boolean,
	downloadProgress: Float,
	speedText: String,
	currentVersionName: String = "",
	onStartUpdate: () -> Unit,
	onDismiss: () -> Unit,
) {
	val cleanedNotes = formatReleaseHighlights(info.releaseNotes)

	Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
		Surface(
			shape = RoundedCornerShape(24.dp),
			color = Color.White,
			shadowElevation = 16.dp,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp),
		) {
			Column(
				modifier = Modifier
					.padding(24.dp)
					.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				// 顶部动态极光蓝徽章
				Box(
					modifier = Modifier
						.size(56.dp)
						.clip(RoundedCornerShape(18.dp))
						.background(
							Brush.linearGradient(
								colors = listOf(Color(0xFF2A72F6), Color(0xFF00B5A6))
							)
						),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = Icons.Rounded.CloudDownload,
						contentDescription = null,
						tint = Color.White,
						modifier = Modifier.size(28.dp),
					)
				}

				Spacer(modifier = Modifier.height(14.dp))

				Text(
					text = "发现新版本发布",
					fontSize = 18.sp,
					fontWeight = FontWeight.ExtraBold,
					color = Color(0xFF1D2129),
				)

				Spacer(modifier = Modifier.height(4.dp))

				val versionTransition = if (currentVersionName.isNotBlank()) {
					"当前 v$currentVersionName  →  新版本 v${info.versionName}"
				} else {
					"NasDrop v${info.versionName} 正式版"
				}

				Text(
					text = "$versionTransition (${formatSize(info.apkSize)})",
					fontSize = 12.sp,
					fontWeight = FontWeight.Bold,
					color = Color(0xFF2A72F6),
				)

				Spacer(modifier = Modifier.height(16.dp))

				// 更新日志卡片
				Surface(
					shape = RoundedCornerShape(14.dp),
					color = Color(0xFFF7F8FA),
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(max = 170.dp),
				) {
					Column(
						modifier = Modifier
							.padding(14.dp)
							.verticalScroll(rememberScrollState())
					) {
						Text(
							text = "更新亮点：",
							fontSize = 12.sp,
							fontWeight = FontWeight.Bold,
							color = Color(0xFF1D2129),
						)
						Spacer(modifier = Modifier.height(6.dp))
						cleanedNotes.forEach { note ->
							Row(
								modifier = Modifier.padding(vertical = 2.dp),
								verticalAlignment = Alignment.Top,
							) {
								Box(
									modifier = Modifier
										.padding(top = 6.dp, end = 6.dp)
										.size(4.dp)
										.clip(CircleShape)
										.background(Color(0xFF2A72F6))
								)
								Text(
									text = note,
									fontSize = 12.sp,
									color = Color(0xFF4E5969),
									lineHeight = 18.sp,
								)
							}
						}
					}
				}

				Spacer(modifier = Modifier.height(20.dp))

				// 一键更新主操作胶囊（平滑进度条）
				if (isDownloading) {
					val animatedProgress by animateFloatAsState(targetValue = downloadProgress, label = "progress")
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(46.dp)
							.clip(RoundedCornerShape(23.dp))
							.background(Color(0xFFEAF2FF)),
						contentAlignment = Alignment.CenterStart,
					) {
						Box(
							modifier = Modifier
								.fillMaxHeight()
								.fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
								.background(
									Brush.horizontalGradient(
										colors = listOf(Color(0xFF2A72F6), Color(0xFF00B5A6))
									)
								)
						)

						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text(
								text = if (downloadProgress >= 1f) "正在调起系统安装..." else "正在高速下载 ${(animatedProgress * 100).toInt()}%",
								fontSize = 12.sp,
								fontWeight = FontWeight.Bold,
								color = if (animatedProgress > 0.4f) Color.White else Color(0xFF2A72F6),
							)
							if (speedText.isNotEmpty() && downloadProgress < 1f) {
								Text(
									text = speedText,
									fontSize = 11.sp,
									fontWeight = FontWeight.Medium,
									color = if (animatedProgress > 0.8f) Color.White else Color(0xFF2A72F6),
								)
							}
						}
					}
				} else {
					Button(
						onClick = onStartUpdate,
						colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
						shape = RoundedCornerShape(23.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(46.dp),
					) {
						Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
						Spacer(modifier = Modifier.width(6.dp))
						Text(
							text = "立即一键自动更新",
							fontWeight = FontWeight.Bold,
							fontSize = 14.sp,
							color = Color.White,
						)
					}
				}

				if (!isDownloading) {
					Spacer(modifier = Modifier.height(10.dp))
					Text(
						text = "稍后再说",
						fontSize = 13.sp,
						fontWeight = FontWeight.Medium,
						color = Color(0xFF86909C),
						modifier = Modifier
							.clickable(onClick = onDismiss)
							.padding(vertical = 4.dp, horizontal = 12.dp),
					)
				}
			}
		}
	}
}

/**
 * 清洗并格式化 GitHub Release 更新说明，剔除无实质内容的链接与英文模板
 */
private fun formatReleaseHighlights(raw: String): List<String> {
	val defaultHighlights = listOf(
		"支持照片左右滑动连续切换预览与顺滑手势",
		"深度优化上传流程与即时写入确认，消除等待感",
		"多处常驻显示当前版本号与即时连接状态",
		"去除弹窗闪烁与视觉感知卡滞，体验更加细腻",
	)
	if (raw.isBlank()) return defaultHighlights

	val lines = raw.lines()
		.map { it.trim() }
		.filter { line ->
			line.isNotBlank() &&
			!line.contains("Full Changelog", ignoreCase = true) &&
			!line.startsWith("http://") &&
			!line.startsWith("https://") &&
			!line.startsWith("## ") &&
			!line.startsWith("# ")
		}
		.map { line ->
			line.removePrefix("•")
				.removePrefix("-")
				.removePrefix("*")
				.trim()
		}
		.filter { it.isNotBlank() && !it.startsWith("http") }

	return if (lines.isEmpty()) defaultHighlights else lines
}
