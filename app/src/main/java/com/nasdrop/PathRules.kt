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

/** 生成不与 [occupiedNamesLower]（全小写文件名集合）冲突的新文件名，如 `photo (1).jpg`。 */
fun resolveUniqueUploadName(name: String, occupiedNamesLower: Set<String>): String {
	if (name.lowercase() !in occupiedNamesLower) return name
	val dot = name.lastIndexOf('.')
	val base = if (dot > 0) name.substring(0, dot) else name
	val ext = if (dot > 0) name.substring(dot) else ""
	var index = 1
	while (true) {
		val candidate = "$base ($index)$ext"
		if (candidate.lowercase() !in occupiedNamesLower) return candidate
		index++
	}
}
