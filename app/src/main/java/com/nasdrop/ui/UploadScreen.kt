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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nasdrop.UploadTask
import com.nasdrop.Uploads
import kotlinx.coroutines.delay

/**
 * 分享之后的上传进度页。
 *
 * 它存在的意义不只是好看：ACTION_SEND 带过来的 content:// 读取授权只在这个
 * Activity 的任务还活着的时候有效，所以这里要一直停在前台，等全部传完再自动关闭。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressScreen(count: Int, onDone: () -> Unit) {
	val tasks by Uploads.tasks.collectAsStateWithLifecycle()
	val finished = tasks.isNotEmpty() && tasks.all { it.done }
	val failed = tasks.count { it.error != null }

	LaunchedEffect(finished) {
		if (finished && failed == 0) {
			delay(1200)
			onDone()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(if (finished) "已存入" else "正在存入 NAS") },
			)
		},
	) { padding ->
		Column(Modifier.padding(padding).fillMaxSize()) {
			Text(
				if (finished) {
					if (failed == 0) "全部完成，窗口即将关闭" else "$failed 个失败，可返回重试"
				} else {
					"共 $count 个文件，请保持在此页面直到完成"
				},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
			)

			LazyColumn(Modifier.fillMaxSize()) {
				items(tasks) { task -> TaskRow(task) }
			}
		}
	}
}

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
				task.error != null -> Text(
					task.error,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)

				task.done -> Text(
					"已完成",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				else -> LinearProgressIndicator(
					progress = { task.progress },
					modifier = Modifier.fillMaxWidth().height(4.dp),
				)
			}
		}
		Spacer(Modifier.width(14.dp))
		when {
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
