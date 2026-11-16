package com.nasdrop.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.nasdrop.BrowserViewModel
import com.nasdrop.Settings
import com.nasdrop.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val app = context.app

	var loaded by remember { mutableStateOf(false) }
	var baseUrl by remember { mutableStateOf("") }
	var user by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var useTs by remember { mutableStateOf(true) }
	var hostname by remember { mutableStateOf("nasdrop-android") }
	var authKey by remember { mutableStateOf("") }
	var trustAll by remember { mutableStateOf(true) }

	var testing by remember { mutableStateOf(false) }
	var logs by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(Unit) {
		val s = withContext(Dispatchers.IO) { app.prefs.current() }
		baseUrl = s.baseUrl
		user = s.user
		password = s.password
		useTs = s.useTailscale
		hostname = s.hostname
		authKey = s.authKey
		trustAll = s.trustAllCerts
		loaded = true
	}

	fun save(s: Settings) {
		scope.launch {
			withContext(Dispatchers.IO) {
				app.prefs.setServer(s.baseUrl, s.user, s.password)
				app.prefs.setTailscale(s.useTailscale, s.hostname, s.authKey)
				app.prefs.setTrustAllCerts(s.trustAllCerts)
			}
			vm.reloadSettings()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("设置") },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
					}
				},
				actions = {
					IconButton(onClick = { logs = vm.logs().ifEmpty { "（暂无日志）" } }) {
						Icon(Icons.Rounded.BugReport, contentDescription = "诊断日志")
					}
				},
			)
		},
	) { padding ->
		Column(
			Modifier
				.padding(padding)
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp),
		) {
			Spacer(Modifier.height(8.dp))
			SectionTitle("服务器")
			OutlinedTextField(
				value = baseUrl,
				onValueChange = { baseUrl = it },
				label = { Text("WebDAV 地址") },
				placeholder = { Text("https://nas.205002.xyz/nas/") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			Spacer(Modifier.height(10.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				OutlinedTextField(
					value = user,
					onValueChange = { user = it },
					label = { Text("用户名") },
					singleLine = true,
					modifier = Modifier.weight(1f),
				)
				OutlinedTextField(
					value = password,
					onValueChange = { password = it },
					label = { Text("密码") },
					singleLine = true,
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier.weight(1f),
				)
			}
			Text(
				"注意路径要写到可写目录，例如 /nas/；写根目录只能读不能传。",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 6.dp),
			)

			Spacer(Modifier.height(24.dp))
			SectionTitle("Tailscale（已内嵌，无需另装）")

			Row(
				Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(Modifier.weight(1f)) {
					Text("随 App 自动组网", style = MaterialTheme.typography.bodyLarge)
					Text(
						"不申请 VPN 权限，也不占用系统 VPN 槽位；只有 NasDrop 自己的流量走 tailnet。",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				Switch(checked = useTs, onCheckedChange = { useTs = it })
			}

			Spacer(Modifier.height(10.dp))
			OutlinedTextField(
				value = hostname,
				onValueChange = { hostname = it },
				label = { Text("设备名（管理后台里显示的名字）") },
				singleLine = true,
				enabled = useTs,
				modifier = Modifier.fillMaxWidth(),
			)
			Spacer(Modifier.height(10.dp))
			OutlinedTextField(
				value = authKey,
				onValueChange = { authKey = it },
				label = { Text("预授权 Key（可选）") },
				placeholder = { Text("tskey-auth-… 填了就完全免登录") },
				singleLine = true,
				enabled = useTs,
				modifier = Modifier.fillMaxWidth(),
			)
			Text(
				"留空的话，第一次开 App 点一下“去登录”，在浏览器里授权一次即可，之后永久免登录。",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 6.dp),
			)

			Spacer(Modifier.height(14.dp))
			StatusCard(status = vm.linkStatus, needsLogin = vm.needsLogin) {
				val url = vm.loginUrl()
				if (url.isEmpty()) {
					Toast.makeText(context, "还没准备好，稍等几秒再点", Toast.LENGTH_SHORT).show()
				} else {
					runCatching {
						CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
					}.onFailure {
						Toast.makeText(context, "打不开浏览器：${it.message}", Toast.LENGTH_LONG).show()
					}
				}
			}

			Spacer(Modifier.height(14.dp))
			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text("放行自签证书", style = MaterialTheme.typography.bodyLarge)
					Text(
						"流量已经在内嵌 Tailscale 的加密隧道里；只有证书链不被系统信任时才需要开。",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				Switch(checked = trustAll, onCheckedChange = { trustAll = it })
			}

			Spacer(Modifier.height(24.dp))
			Button(
				onClick = {
					val s = Settings(
						baseUrl = baseUrl,
						user = user,
						password = password,
						useTailscale = useTs,
						hostname = hostname.ifBlank { "nasdrop-android" },
						authKey = authKey.trim(),
						trustAllCerts = trustAll,
					)
					save(s)
					Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
					onBack()
				},
				enabled = loaded && baseUrl.isNotBlank() && user.isNotBlank(),
				modifier = Modifier.fillMaxWidth().height(50.dp),
			) { Text("保存") }

			Spacer(Modifier.height(10.dp))
			OutlinedButton(
				onClick = {
					val s = Settings(
						baseUrl = baseUrl,
						user = user,
						password = password,
						useTailscale = useTs,
						hostname = hostname.ifBlank { "nasdrop-android" },
						authKey = authKey.trim(),
						trustAllCerts = trustAll,
					)
					save(s)
					testing = true
					scope.launch {
						val msg = withContext(Dispatchers.IO) {
							runCatching { app.repo.client(s).list("") }
								.fold(
									onSuccess = { "连上了，根目录 ${it.size} 项" },
									onFailure = { it.message ?: it.javaClass.simpleName },
								)
						}
						testing = false
						Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
					}
				},
				enabled = !testing,
				modifier = Modifier.fillMaxWidth().height(50.dp),
			) { Text(if (testing) "正在连接…" else "测试连接") }

			Spacer(Modifier.height(32.dp))
		}
	}

	logs?.let { text ->
		AlertDialog(
			onDismissRequest = { logs = null },
			title = { Text("诊断日志") },
			text = {
				Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
					Text(
						text,
						style = MaterialTheme.typography.bodySmall,
						fontFamily = FontFamily.Monospace,
					)
				}
			},
			confirmButton = { TextButton(onClick = { logs = null }) { Text("关闭") } },
		)
	}
}

@Composable
private fun SectionTitle(text: String) {
	Column(Modifier.fillMaxWidth()) {
		Text(
			text,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.primary,
		)
		Spacer(Modifier.height(10.dp))
	}
}

@Composable
private fun StatusCard(status: String, needsLogin: Boolean, onLogin: () -> Unit) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(Modifier.weight(1f)) {
			Text("连接状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Text(
				status.ifEmpty { "—" },
				style = MaterialTheme.typography.bodyLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		TextButton(onClick = onLogin) { Text(if (needsLogin) "去登录" else "重新授权") }
	}
}
