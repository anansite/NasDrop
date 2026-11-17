package com.nasdrop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * NAS 常用预置文件夹图标定义
 */
enum class NasFolderIcon(
	val key: String,
	val label: String,
	val icon: ImageVector,
	val tintColor: Color,
	val bgColor: Color,
) {
	Default("default", "默认", Icons.Rounded.Folder, Color(0xFF2A72F6), Color(0xFFE8F3FF)),
	Movie("movie", "电影影视", Icons.Rounded.Movie, Color(0xFF722ED1), Color(0xFFF3E8FF)),
	Music("music", "音乐音频", Icons.Rounded.MusicNote, Color(0xFFFF7D00), Color(0xFFFFF2E8)),
	Photo("photo", "照片相册", Icons.Rounded.PhotoLibrary, Color(0xFFFF3D71), Color(0xFFFFEBF1)),
	Download("download", "下载资源", Icons.Rounded.Download, Color(0xFF00B578), Color(0xFFE8FFEA)),
	Document("document", "办公文档", Icons.Rounded.Description, Color(0xFF009688), Color(0xFFE0F2F1)),
	Backup("backup", "备份归档", Icons.Rounded.FolderZip, Color(0xFF607D8B), Color(0xFFECEFF1)),
	Game("game", "游戏ROM", Icons.Rounded.SportsEsports, Color(0xFF3F51B5), Color(0xFFE8EAF6)),
	Book("book", "电子书籍", Icons.Rounded.MenuBook, Color(0xFF795548), Color(0xFFEFEBE9)),
	Private("private", "私密密码", Icons.Rounded.Lock, Color(0xFFF5222D), Color(0xFFFFECE8)),
	Code("code", "代码工程", Icons.Rounded.Code, Color(0xFF00BCD4), Color(0xFFE0F7FA)),
	Home("home", "家庭共享", Icons.Rounded.Home, Color(0xFFE91E63), Color(0xFFFCE4EC));

	companion object {
		fun fromKey(key: String?): NasFolderIcon =
			entries.find { it.key == key } ?: Default
	}
}
