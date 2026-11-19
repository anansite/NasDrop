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
import androidx.compose.ui.text.style.TextOverflow
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
						modifier = Modifier.weight(1f),
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

						Column(modifier = Modifier.weight(1f)) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(
									text = "当前版本 v${vm.currentVersionName}",
									fontSize = 14.sp,
									fontWeight = FontWeight.Bold,
									color = colors.onSurface,
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								if (vm.hasUpdate) {
									Spacer(modifier = Modifier.width(6.dp))
									Surface(
										shape = RoundedCornerShape(8.dp),
										color = colors.error,
									) {
										Text(
											text = "可升级至 v${vm.latestUpdateInfo?.versionName}",
											fontSize = 9.sp,
											fontWeight = FontWeight.ExtraBold,
											color = colors.onPrimary,
											modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
										)
									}
								}
							}
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = if (vm.hasUpdate) "发现新版本，点击立即自动更新" else "当前已是最新版本，可随时检查更新",
								fontSize = 11.sp,
								color = colors.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}

					Spacer(modifier = Modifier.width(8.dp))

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
						modifier = Modifier.defaultMinSize(minWidth = 78.dp),
						shape = RoundedCornerShape(16.dp),
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
					) {
						if (checkingUpdate) {
							CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.primary)
						} else {
							Text(
								text = if (vm.hasUpdate) "立即更新" else "检查更新",
								fontSize = 12.sp,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
								softWrap = false,
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

			// ==================== 卡片 3: 直连与局域网传输 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "直连与局域网传输",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = colors.onSurface,
					)
					Text(
						text = "优先本地局域网高速传输，物理低延迟跑满带宽",
						fontSize = 12.sp,
						color = colors.onSurfaceVariant,
					)
					Spacer(modifier = Modifier.height(14.dp))

					// 核心直连 / 局域网状态大卡片
					val isLan = routeInfo.isLan || routeInfo.isLanDirect
					val isDirect = routeInfo.isDirect && !routeInfo.isRelay
					val heroBgColor = when {
						isLan -> Color(0xFFE8F5E9)
						isDirect -> Color(0xFFE0F2F1)
						routeInfo.isRelay -> Color(0xFFFFF3E0)
						else -> Color(0xFFF5F5F5)
					}
					val heroBorderColor = when {
						isLan -> Color(0xFF81C784)
						isDirect -> Color(0xFF4DB6AC)
						routeInfo.isRelay -> Color(0xFFFFB74D)
						else -> colors.outlineVariant.copy(alpha = 0.5f)
					}
					val heroTextColor = when {
						isLan -> Color(0xFF2E7D32)
						isDirect -> Color(0xFF00695C)
						routeInfo.isRelay -> Color(0xFFE65100)
						else -> colors.onSurface
					}

					Surface(
						shape = RoundedCornerShape(14.dp),
						color = heroBgColor,
						border = BorderStroke(1.dp, heroBorderColor),
						modifier = Modifier.fillMaxWidth(),
					) {
						Column(modifier = Modifier.padding(14.dp)) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Icon(
										imageVector = when {
											isLan -> Icons.Rounded.Home
											isDirect -> Icons.Rounded.FlashOn
											routeInfo.isRelay -> Icons.Rounded.Sync
											else -> Icons.Rounded.WifiOff
										},
										contentDescription = null,
										tint = heroTextColor,
										modifier = Modifier.size(22.dp),
									)
									Text(
										text = when {
											isLan -> "本地局域网直连 (LAN)"
											isDirect -> "原生系统直连 (Direct)"
											routeInfo.isRelay -> "远程中继连接"
											else -> "未建立直连"
										},
										fontSize = 15.sp,
										fontWeight = FontWeight.Bold,
										color = heroTextColor,
									)
								}

								if (routeInfo.latencyMs > 0) {
									Surface(
										shape = RoundedCornerShape(8.dp),
										color = heroTextColor.copy(alpha = 0.15f),
									) {
										Text(
											text = "${routeInfo.latencyMs} ms",
											fontSize = 12.sp,
											fontWeight = FontWeight.ExtraBold,
											color = heroTextColor,
											modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
										)
									}
								}
							}

							Spacer(modifier = Modifier.height(8.dp))

							Text(
								text = when {
									isLan -> "⚡ 物理延迟极低，已跑满本地 Wi-Fi / 千兆局域网最高带宽，视频与大文件无损秒开。"
									isDirect -> "⚡ 直接与 NAS 服务器建立通信，端到端高速传输，不经过任何第三方服务器中继。"
									routeInfo.isRelay -> "当前处于中继连接，若在同一局域网内请点击下方「刷新直连检测」以切回极速局域网直连。"
									else -> routeInfo.label.ifBlank { "当前未连入家庭 Wi-Fi 或 WebDAV 服务尚未连通。" }
								},
								fontSize = 12.sp,
								color = heroTextColor.copy(alpha = 0.9f),
								lineHeight = 17.sp,
							)

							if (routeInfo.endpoint.isNotBlank()) {
								Spacer(modifier = Modifier.height(4.dp))
								Text(
									text = "连接端点: ${routeInfo.endpoint}",
									fontSize = 11.sp,
									color = heroTextColor.copy(alpha = 0.7f),
								)
							}

							Row(
								modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
								horizontalArrangement = Arrangement.End,
								verticalAlignment = Alignment.CenterVertically,
							) {
								TextButton(
									onClick = { vm.reloadSettings() },
									enabled = !saving && !testing,
								) {
									Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = heroTextColor)
									Spacer(modifier = Modifier.width(4.dp))
									Text("刷新直连检测", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = heroTextColor)
								}
								if (!isLan && !isDirect) {
									Spacer(modifier = Modifier.width(6.dp))
									FilledTonalButton(
										onClick = {
											vm.punchHole { result ->
												Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
											}
										},
										shape = RoundedCornerShape(10.dp),
										contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
									) {
										Icon(Icons.Rounded.FlashOn, contentDescription = null, modifier = Modifier.size(14.dp))
										Spacer(modifier = Modifier.width(4.dp))
										Text("直连打洞", fontSize = 11.sp, fontWeight = FontWeight.Bold)
									}
								}
							}
						}
					}

					Spacer(modifier = Modifier.height(14.dp))

					// 模式选择：聚焦直连与智能模式
					Text(
						text = "传输模式偏好",
						fontSize = 13.sp,
						fontWeight = FontWeight.Bold,
						color = colors.onSurface,
					)
					Spacer(modifier = Modifier.height(6.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						FilterChip(
							selected = connMode == ConnectionMode.Direct,
							onClick = { connMode = ConnectionMode.Direct },
							enabled = !saving,
							label = { Text("⚡ 强制直连", fontSize = 12.sp) },
							modifier = Modifier.weight(1f),
						)
						FilterChip(
							selected = connMode != ConnectionMode.Direct,
							onClick = { connMode = ConnectionMode.Auto },
							enabled = !saving,
							label = { Text("🏠 智能自适应", fontSize = 12.sp) },
							modifier = Modifier.weight(1f),
						)
					}
					Text(
						text = if (connMode == ConnectionMode.Direct) "仅使用本地局域网或公网直接访问 WebDAV，杜绝任何中继代理" else "在局域网内自动锁定极速直连；网络变化时自适应连通",
						style = MaterialTheme.typography.bodySmall,
						color = colors.onSurfaceVariant,
						modifier = Modifier.padding(top = 2.dp),
					)

					HorizontalDivider(color = colors.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))

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

			// 底部品牌与版本常驻标识
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 18.dp, bottom = 12.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text(
					text = "NasDrop v${vm.currentVersionName}",
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
					color = colors.onSurfaceVariant.copy(alpha = 0.85f),
				)
				Spacer(modifier = Modifier.height(3.dp))
				Text(
					text = "极速私有云与局域网文件流转",
					fontSize = 11.sp,
					color = colors.outline,
				)
			}

			Spacer(modifier = Modifier.height(20.dp))
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
