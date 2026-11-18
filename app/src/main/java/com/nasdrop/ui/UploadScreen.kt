package com.nasdrop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nasdrop.UploadTask
import com.nasdrop.Uploads

/**
 * 分享之后的上传进度页。
 *
 * 它存在的意义不只是好看：ACTION_SEND 带过来的 content:// 读取授权只在这个
 * Activity 的任务还活着的时候有效，上传期间保留任务；完成后保留结果供用户查看。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressScreen(count: Int, batchId: String, interrupted: Boolean = false, onDone: () -> Unit, onBrowse: () -> Unit) {
	val allTasks by Uploads.tasks.collectAsStateWithLifecycle()
	val tasks = allTasks.filter { it.batchId == batchId }
	val finished = interrupted || (tasks.isNotEmpty() && tasks.all { it.done })
	val failed = tasks.count { it.error != null && !it.cancelled }
	val cancelled = tasks.count { it.cancelled }

	Scaffold(
		topBar = {
			TopAppBar(
					title = { Text(if (interrupted) "上传已中断" else if (finished) "上传结果" else "正在上传到 NAS", maxLines = 1, overflow = TextOverflow.Ellipsis) },
					actions = {
					if (finished) {
							TextButton(onClick = onBrowse) { Text("查看文件") }
							TextButton(onClick = onDone) { Text("关闭") }
						}
					},
			)
		},
	) { padding ->
		Column(Modifier.padding(padding).fillMaxSize()) {
			Text(
					if (interrupted) {
						"应用进程已重启，请重新选择未完成的文件上传。"
					} else if (finished) {
						"${tasks.size - failed - cancelled} 个完成，$failed 个失败，$cancelled 个取消"
				} else {
					"共 $count 个文件，完成前请保留此窗口；可取消单个任务。"
				},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
			)

			LazyColumn(Modifier.fillMaxSize()) {
					items(tasks, key = { it.id }) { task -> TaskRow(task) }
			}
		}
	}
}

/** 单项上传区分取消、失败和服务器确认；取消按钮终止该项实际请求。 */
@Composable
private fun TaskRow(task: UploadTask) {
	Row(
		Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f)) {
			Text(
				task.name,
				style = MaterialTheme.typography.bodyLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.height(6.dp))
			when {
				task.cancelled -> Text("已取消", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
				task.error != null -> Text(
					task.error,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
				)

				task.done -> Text(
					"已完成",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

					else -> {
						Text(
							when {
								task.bodySent -> "已发送，等待服务器确认"
								!task.started -> "排队等待中"
								else -> "正在上传 ${if (task.total > 0) "${(task.progress * 100).toInt()}%" else ""}"
							},
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						if (task.bodySent || task.total <= 0) {
							LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
						} else {
							LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
						}
					}
			}
		}
		Spacer(Modifier.width(14.dp))
		if (!task.done) {
			TextButton(onClick = { Uploads.cancel(task.id) }) { Text("取消", color = MaterialTheme.colorScheme.error) }
		}
		when {
			task.cancelled -> Unit
			task.error != null -> Icon(
				Icons.Rounded.ErrorOutline,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.error,
			)

			task.done -> Icon(
				Icons.Rounded.CheckCircle,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
			)
		}
	}
}
