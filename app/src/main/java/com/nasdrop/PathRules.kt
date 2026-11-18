package com.nasdrop

/** 按完整目录段判断归属；“旅行备份”不属于“旅行”，空根目录包含全部路径。 */
fun isPathWithin(path: String, directory: String): Boolean {
	val root = directory.trim('/')
	val target = path.trim('/')
	return root.isEmpty() || target == root || target.startsWith("$root/")
}

/** 同名检查覆盖远端、当前批次和尚未出现在远端列表的其他批次，且隔离不同服务器。 */
fun findUploadConflicts(
	names: List<String>, remoteNames: Set<String>, queued: List<UploadTask>, directory: String, owner: String,
): List<String> {
	val pendingNames = queued.filter { task ->
		task.dir == directory && (task.ownerKey.isBlank() || task.ownerKey == owner) &&
			!task.cancelled && task.error == null && task.listingActive && (!task.done || !task.listed)
	}.map { it.name }.toSet()
	val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
	return names.filter { it in remoteNames || it in pendingNames || it in duplicates }.distinct()
}
