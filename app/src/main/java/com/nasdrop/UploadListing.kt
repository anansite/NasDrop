package com.nasdrop

/**
 * 保存已发送、尚未被目录响应确认的文件。仅在 ViewModel 主线程调用；不执行网络请求。
 * 旧目录响应不能移除新上传项，失败只撤回该次上传的临时覆盖，历史状态不会重复插入。
 */
class UploadListing {
	private val phases = mutableMapOf<String, Int>()
	private val files = linkedMapOf<String, UploadTask>() // 以 ID 留住同名上传的有效前序候选。

	/** 接收可重放的上传快照，返回发生阶段变化的目标目录。 */
	fun update(tasks: List<UploadTask>): Set<String> {
		val changed = mutableSetOf<String>()
		for (task in tasks) {
			val phase = when {
				!task.listingActive -> 4
				task.error != null -> 3
				task.done && task.listed -> 5
				task.done -> 2
				task.bodySent -> 1
				else -> 0
			}
			if (phases.put(task.id, phase) == phase || phase == 0) continue
			if (!task.listingActive || task.error != null) {
				files.remove(task.id)
			} else if (task.listed) {
				// 其他页面已确认：已有临时项等自己的列表追上，新页面则不重放旧记录。
				if (task.id in files) files[task.id] = task
			} else {
				files[task.id] = task
			}
			changed += task.dir
		}
		return changed
	}

	/** PUT 已成功且远端目录包含相符大小时，撤销临时覆盖；未确认的文件继续显示。 */
	fun reconcile(dir: String, remoteSizes: Map<String, Long>): Set<String> {
		val matched = pending(dir).filter { task ->
			val path = pathOf(task)
			task.done && path in remoteSizes && (task.total < 0 || remoteSizes[path] == task.total)
		}
		val paths = matched.map { pathOf(it) }.toSet()
		val ids = files.values.filter { pathOf(it) in paths }.map { it.id }.toSet()
		files.keys.removeAll(ids)
		return ids
	}

	/** 目录中的待同步项；调用方与服务端原始列表合并，失败时自然恢复原列表。 */
	fun pending(dir: String): List<UploadTask> = files.values.filter { it.dir == dir }
		.groupBy { it.name }.values.map { candidates -> candidates.sortedBy { it.writeOrder }.last() }

	/** 用户删除或移动文件后取消对应临时覆盖，避免历史上传项重新出现。 */
	fun forget(paths: Collection<String>) {
		files.entries.removeAll { (_, task) -> paths.any { pathOf(task) == it || pathOf(task).startsWith("$it/") } }
	}

	private fun pathOf(task: UploadTask): String =
		listOf(task.dir, task.name).filter { it.isNotEmpty() }.joinToString("/")
}
