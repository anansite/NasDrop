package com.nasdrop.ui

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.nasdrop.BrowserViewModel
import com.nasdrop.ConnectionMode
import com.nasdrop.Settings
import com.nasdrop.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val app = context.app
	val colors = MaterialTheme.colorScheme
	val routeInfo by vm.routeInfo.collectAsState()

	// 用 Settings 默认值预填，避免首次进入或异步加载前输入框一片空白的"没默认值"观感。
	// LaunchedEffect 加载持久化的真实配置后会覆盖。
	var loaded by remember { mutableStateOf(false) }
	var baseUrl by remember { mutableStateOf(Settings().baseUrl) }
	var user by remember { mutableStateOf(Settings().user) }
	var password by remember { mutableStateOf(Settings().password) }
	var passwordVisible by remember { mutableStateOf(false) }
	var connMode by remember { mutableStateOf(Settings().connectionMode) }
	val useTs = connMode != ConnectionMode.Direct
	var hostname by remember { mutableStateOf(Settings().hostname) }
	var authKey by remember { mutableStateOf("") }
	var trustAll by remember { mutableStateOf(Settings().trustAllCerts) }

	var testing by remember { mutableStateOf(false) }
	var logs by remember { mutableStateOf<String?>(null) }
	var checkingUpdate by remember { mutableStateOf(false) }
	var cacheSizeStr by remember { mutableStateOf("计算中...") }
	var saving by remember { mutableStateOf(false) }
	var clearingCache by remember { mutableStateOf(false) }
	var saveError by remember { mutableStateOf<String?>(null) }
	// 防止保存期间离开页面导致组合协程被取消，从而展示虚假的保存成功。
	BackHandler(enabled = saving) { }

	fun calculateCacheSize() {
		scope.launch {
			val size = withContext(Dispatchers.IO) {
				// 此处只统计清理操作实际负责的缓存，不包含分享文件及安装包。
				listOf("image_cache", "local_thumbs").sumOf { dir ->
					context.cacheDir.resolve(dir).walkTopDown().filter { it.isFile }.sumOf { it.length() }
				}
			}
			val mb = size / (1024.0 * 1024.0)
			cacheSizeStr = if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", size / 1024.0)
		}
	}

	LaunchedEffect(Unit) {
		val s = withContext(Dispatchers.IO) { app.prefs.current() }
		baseUrl = s.baseUrl
		user = s.user
		password = s.password
		connMode = s.connectionMode
		hostname = s.hostname
		authKey = s.authKey
		trustAll = s.trustAllCerts
		loaded = true
		calculateCacheSize()
	}

	fun save(s: Settings) {
		if (saving) return
		saving = true
		saveError = null
		scope.launch {
			try {
				withContext(Dispatchers.IO) {
					// 保留其他流程最近更新的上传目录，所有连接字段通过一次事务提交。
					app.prefs.save(s.copy(lastDir = app.prefs.current().lastDir))
				}
					// 所有存活页面观察同一配置流，由各自缓存失效流程重新加载。
					Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
				onBack()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				saveError = e.message ?: "保存失败，请重试"
				Log.e("SettingsScreen", "配置保存失败，reason=${e.javaClass.simpleName}", e)
			} finally {
				saving = false
			}
		}
	}

	Scaffold(
		containerColor = colors.background,
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "服务器与偏好设置",
						fontSize = 17.sp,
						fontWeight = FontWeight.Bold,
						color = colors.onSurface,
					)
				},
				navigationIcon = {
					IconButton(onClick = onBack, enabled = !saving) {
						Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.onSurface)
					}
				},
				actions = {
					IconButton(onClick = {
						scope.launch(Dispatchers.IO) {
							val text = vm.logs().ifEmpty { "（暂无日志）" }
							withContext(Dispatchers.Main) { logs = text }
						}
					}) {
						Icon(Icons.Rounded.BugReport, contentDescription = "诊断日志", tint = colors.onSurfaceVariant)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
			)
		},
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			// ==================== 卡片 1: 版本检测与一键更新 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(
					containerColor = if (vm.hasUpdate) colors.primaryContainer else colors.surface
				),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Box(
							modifier = Modifier
								.size(40.dp)
								.clip(RoundedCornerShape(12.dp))
								.background(
									Brush.linearGradient(
										colors = listOf(colors.primary, colors.tertiary)
									)
								),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Rounded.CloudDownload,
								contentDescription = null,
								tint = colors.onPrimary,
								modifier = Modifier.size(20.dp),
							)
						}

						Column {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(
									text = if (vm.hasUpdate) "发现新版本 v${vm.latestUpdateInfo?.versionName}" else "当前版本 v${vm.currentVersionName}",
									fontSize = 14.sp,
									fontWeight = FontWeight.Bold,
									color = colors.onSurface,
								)
								if (vm.hasUpdate) {
									Spacer(modifier = Modifier.width(6.dp))
									Surface(
										shape = RoundedCornerShape(8.dp),
										color = colors.error,
									) {
										Text(
											text = "NEW",
											fontSize = 9.sp,
											fontWeight = FontWeight.ExtraBold,
											color = colors.onPrimary,
											modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
										)
									}
								}
							}
							Spacer(modifier = Modifier.height(2.dp))
							Text(
							text = if (vm.hasUpdate) "下载并安装新版本" else "可手动检查是否有新版本",
								fontSize = 11.sp,
								color = colors.onSurfaceVariant,
							)
						}
					}

					Button(
						enabled = !checkingUpdate && !saving,
						onClick = {
							if (vm.hasUpdate) {
								vm.openUpdateDialog()
							} else {
								checkingUpdate = true
								vm.checkForUpdates(silent = false) { has ->
									checkingUpdate = false
									if (!has) {
										Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
									}
								}
							}
						},
						colors = ButtonDefaults.buttonColors(
							containerColor = if (vm.hasUpdate) colors.primary else colors.outlineVariant,
							contentColor = if (vm.hasUpdate) colors.onPrimary else colors.onSurfaceVariant,
						),
						shape = RoundedCornerShape(16.dp),
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
					) {
						if (checkingUpdate) {
							CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.primary)
						} else {
							Text(
								text = if (vm.hasUpdate) "立即更新" else "检查更新",
								fontSize = 12.sp,
								fontWeight = FontWeight.Bold,
							)
						}
					}
				}
			}

			// ==================== 卡片 2: NAS 主机与品牌预设 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "WebDAV 服务配置",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = colors.onSurface,
					)
					Spacer(modifier = Modifier.height(12.dp))

					// 服务器地址
					OutlinedTextField(
						value = baseUrl,
						enabled = !saving,
						onValueChange = { baseUrl = it },
						label = { Text("WebDAV 服务接入地址", fontSize = 12.sp) },
						placeholder = { Text("https://nas.205002.xyz/dav/") },
						singleLine = true,
						shape = RoundedCornerShape(12.dp),
						colors = OutlinedTextFieldDefaults.colors(
							focusedBorderColor = colors.primary,
							unfocusedBorderColor = colors.outlineVariant,
							focusedContainerColor = colors.surface,
							unfocusedContainerColor = colors.background,
						),
						modifier = Modifier.fillMaxWidth(),
					)

					Spacer(modifier = Modifier.height(10.dp))

					// 账号与密码
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						OutlinedTextField(
							value = user,
							enabled = !saving,
							onValueChange = { user = it },
							label = { Text("账号", fontSize = 12.sp) },
							singleLine = true,
							shape = RoundedCornerShape(12.dp),
							colors = OutlinedTextFieldDefaults.colors(
								focusedBorderColor = colors.primary,
								unfocusedBorderColor = colors.outlineVariant,
								focusedContainerColor = colors.surface,
								unfocusedContainerColor = colors.background,
							),
							modifier = Modifier.weight(1f),
						)

						OutlinedTextField(
							value = password,
							enabled = !saving,
							onValueChange = { password = it },
							label = { Text("访问密码", fontSize = 12.sp) },
							singleLine = true,
							visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
							trailingIcon = {
								IconButton(onClick = { passwordVisible = !passwordVisible }) {
									Icon(
										imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
										contentDescription = "显隐",
										tint = colors.onSurfaceVariant,
										modifier = Modifier.size(20.dp),
									)
								}
							},
							shape = RoundedCornerShape(12.dp),
							colors = OutlinedTextFieldDefaults.colors(
								focusedBorderColor = colors.primary,
								unfocusedBorderColor = colors.outlineVariant,
								focusedContainerColor = colors.surface,
								unfocusedContainerColor = colors.background,
							),
							modifier = Modifier.weight(1f),
						)
					}
				}
			}

			// ==================== 卡片 3: 安全通道与专线穿透 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "连接方式与证书",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = colors.onSurface,
					)
					Spacer(modifier = Modifier.height(12.dp))

					// 模式选择直接对应存储及网络出口，强制组网不会静默改成自动模式。
					Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						ConnectionMode.entries.forEach { mode ->
							FilterChip(selected = connMode == mode, onClick = { connMode = mode }, enabled = !saving,
								label = { Text(mode.title) })
						}
					}
					Text(connMode.desc, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

					HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

					// 节点名称
					AnimatedVisibility(visible = useTs) {
						Column {
							OutlinedTextField(
								value = hostname,
									enabled = !saving,
								onValueChange = { hostname = it },
								label = { Text("本设备节点名称", fontSize = 12.sp) },
								placeholder = { Text("nasdrop-android") },
								singleLine = true,
								shape = RoundedCornerShape(12.dp),
								colors = OutlinedTextFieldDefaults.colors(
									focusedBorderColor = colors.primary,
									unfocusedBorderColor = colors.outlineVariant,
									focusedContainerColor = colors.surface,
									unfocusedContainerColor = colors.background,
								),
								modifier = Modifier.fillMaxWidth(),
							)
							Spacer(modifier = Modifier.height(10.dp))
						}
					}

					// 信任自签 SSL 证书
					SettingItemRow(
						icon = Icons.Rounded.Security,
						iconBg = colors.primary,
						title = "放行自签 SSL 证书",
						desc = "开启后跳过证书与主机名校验，仅用于可信私有服务",
						trailing = {
							Switch(
								checked = trustAll,
								enabled = !saving,
								onCheckedChange = { trustAll = it },
								colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
							)
						},
					)

					HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

					// 连接状态：只告诉用户"连没连上"，直连/中继/打洞等由 App 自动处理，不打扰用户。
					val online = routeInfo.isDirect || routeInfo.isRelay || routeInfo.isLan
					val statusText = routeInfo.label
					val statusColor = if (online) colors.tertiary else colors.onSurfaceVariant
					Column(modifier = Modifier.fillMaxWidth()) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Box(
									modifier = Modifier
										.size(9.dp)
										.clip(CircleShape)
										.background(statusColor),
								)
								Text(
									text = "连接状态",
									fontSize = 13.sp,
									fontWeight = FontWeight.Bold,
									color = colors.onSurface,
								)
							}
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
									TextButton(onClick = { vm.reloadSettings() }, enabled = !saving && !testing) {
									Text("重新连接", fontSize = 12.sp, fontWeight = FontWeight.Bold)
								}
								if (vm.needsLogin) {
									Button(
										onClick = {
											val url = vm.loginUrl()
											if (url.isNotEmpty()) {
												CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
											}
										},
										colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
										shape = RoundedCornerShape(12.dp),
										contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
									) {
										Text("去授权", fontSize = 11.sp, fontWeight = FontWeight.Bold)
									}
								}
							}
						}
						Spacer(modifier = Modifier.height(8.dp))
						Surface(
							shape = RoundedCornerShape(10.dp),
							color = colors.background,
							modifier = Modifier.fillMaxWidth(),
						) {
							Text(
								text = statusText,
								fontSize = 12.sp,
								color = colors.onSurfaceVariant,
								modifier = Modifier.padding(12.dp),
							)
						}
					}
				}
			}

			// ==================== 卡片 4: 本地缓存管理 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "本地离线与缩略图缓存",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = colors.onSurface,
					)
					Spacer(modifier = Modifier.height(12.dp))

					SettingItemRow(
						icon = Icons.Rounded.CleaningServices,
						iconBg = colors.primary,
							title = "清理缩略图缓存",
						desc = "当前占用 $cacheSizeStr",
						trailing = {
							TextButton(enabled = !clearingCache && !saving, onClick = {
								clearingCache = true
								scope.launch {
									try {
										withContext(Dispatchers.IO) { app.repo.clearImageCache() }
										calculateCacheSize()
										Toast.makeText(context, "缩略图缓存已清理", Toast.LENGTH_SHORT).show()
									} catch (e: CancellationException) {
										throw e
									} catch (e: Exception) {
										Log.e("SettingsScreen", "缩略图缓存清理失败", e)
										Toast.makeText(context, "清理失败，请稍后重试", Toast.LENGTH_SHORT).show()
									} finally {
										clearingCache = false
									}
								}
							}) {
								Text(if (clearingCache) "正在清理…" else "立即清理", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
							}
						},
					)
				}
			}

			Spacer(modifier = Modifier.height(6.dp))
			saveError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }

			// ==================== 底部主操作按钮 ====================
			Button(
				onClick = {
					val s = Settings(
						baseUrl = baseUrl,
						user = user,
						password = password,
						useTailscale = useTs,
						connectionMode = connMode,
						hostname = hostname.ifBlank { "nasdrop-android" },
						authKey = authKey.trim(),
						trustAllCerts = trustAll,
					)
					save(s)
				},
				enabled = loaded && !saving && !testing && baseUrl.isNotBlank() && user.isNotBlank(),
				colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				if (saving) {
					CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimary)
					Spacer(Modifier.width(8.dp))
				}
				Text(if (saving) "正在保存…" else "保存并应用", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onPrimary)
			}

			OutlinedButton(
				onClick = {
					// 只读探测：绝不 save / invalidate / 停隧道。旧版一点测试就 save(s)，
					// 触发 invalidate→stop 隧道→重建 client→全量扫描，测试动作本身把连接搞断、
					// 让页面假死无响应。现在测试只验证"当前生效配置"能否连通。
					testing = true
					scope.launch {
						val result = withTimeoutOrNull(12_000) {
							withContext(Dispatchers.IO) {
								val effective = app.prefs.current()
								// 用户改了配置但没保存时，明确提示，避免"测的是旧配置"的错觉
								val dirty = effective.baseUrl != baseUrl || effective.user != user ||
									effective.password != password || effective.useTailscale != useTs ||
									effective.connectionMode != connMode || effective.hostname != hostname ||
									effective.authKey != authKey.trim() || effective.trustAllCerts != trustAll
								if (dirty) {
									"配置已修改但尚未保存，请先点「保存并立即应用」再测试"
								} else {
									runCatching { app.repo.probeConnection(effective) }
										.fold(
											onSuccess = { "连接正常，服务器响应成功" },
											onFailure = { "连接失败：${it.message ?: it.javaClass.simpleName}" },
										)
								}
							}
						} ?: "连接超时（12 秒），隧道尚未就绪或网络不通，请稍后重试"
						testing = false
						Toast.makeText(context, result, Toast.LENGTH_LONG).show()
					}
				},
				enabled = loaded && !testing && !saving,
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				if (testing) {
					CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
					Spacer(modifier = Modifier.width(8.dp))
				}
				Text(if (testing) "正在测试…" else "测试连通性", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
			}

			Spacer(modifier = Modifier.height(30.dp))
		}
	}

	// 诊断日志弹窗
	logs?.let { text ->
		AlertDialog(
			onDismissRequest = { logs = null },
			title = { Text("底层网络诊断日志", fontWeight = FontWeight.Bold) },
			text = {
				Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
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
private fun SettingItemRow(
	icon: ImageVector,
	iconBg: Color,
	title: String,
	desc: String,
	trailing: @Composable () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier.weight(1f),
		) {
			Box(
				modifier = Modifier
					.size(34.dp)
					.clip(RoundedCornerShape(10.dp))
					.background(iconBg),
				contentAlignment = Alignment.Center,
			) {
				Icon(imageVector = icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
			}
			Column {
				Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
				Text(text = desc, fontSize = 11.sp, color = colors.onSurfaceVariant)
			}
		}
		trailing()
	}
}
