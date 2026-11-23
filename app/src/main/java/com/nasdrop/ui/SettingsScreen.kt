package com.nasdrop.ui

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasdrop.BrowserViewModel
import com.nasdrop.ConnectionMode
import com.nasdrop.NasSystemInfo
import com.nasdrop.Settings
import com.nasdrop.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	vm: BrowserViewModel,
	onBack: () -> Unit,
	isInsideTab: Boolean = false,
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val app = context.app
	val colors = MaterialTheme.colorScheme
	val routeInfo by vm.routeInfo.collectAsState()

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
	var currentPalette by remember { mutableStateOf(Settings().themePalette) }

	var testing by remember { mutableStateOf(false) }
	var logs by remember { mutableStateOf<String?>(null) }
	var checkingUpdate by remember { mutableStateOf(false) }
	var cacheSizeStr by remember { mutableStateOf("计算中...") }
	var saving by remember { mutableStateOf(false) }
	var clearingCache by remember { mutableStateOf(false) }
	var saveError by remember { mutableStateOf<String?>(null) }

	BackHandler(enabled = saving || (!isInsideTab)) {
		if (!saving) onBack()
	}

	fun calculateCacheSize() {
		scope.launch {
			val size = withContext(Dispatchers.IO) {
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
		currentPalette = s.themePalette
		loaded = true
		calculateCacheSize()
		vm.refreshSystemInfo()
	}

	fun save(s: Settings) {
		if (saving) return
		saving = true
		saveError = null
		scope.launch {
			try {
				withContext(Dispatchers.IO) {
					app.prefs.save(s.copy(lastDir = app.prefs.current().lastDir))
				}
				Toast.makeText(context, "配置已保存并生效", Toast.LENGTH_SHORT).show()
				vm.refreshSystemInfo()
				if (!isInsideTab) onBack()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				saveError = e.message ?: "保存失败，请重试"
				Log.e("SettingsScreen", "配置保存失败", e)
			} finally {
				saving = false
			}
		}
	}

	val content = @Composable {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 10.dp)
				.padding(bottom = if (isInsideTab) 96.dp else 24.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			// ==================== 分组 1: NAS 硬件与系统监控看板 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					// 标题行
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Box(
								modifier = Modifier
									.size(32.dp)
									.clip(RoundedCornerShape(8.dp))
									.background(colors.primary.copy(alpha = 0.12f)),
								contentAlignment = Alignment.Center,
							) {
								Icon(
									imageVector = Icons.Rounded.Dns,
									contentDescription = null,
									tint = colors.primary,
									modifier = Modifier.size(18.dp),
								)
							}
							Column {
								Text(
									text = "NAS 硬件与系统看板",
									fontSize = 14.sp,
									fontWeight = FontWeight.Bold,
									color = colors.onSurface,
								)
								Text(
									text = if (vm.systemInfo != null) "已连接 · 实时动态更新" else "正在获取系统资源指标…",
									fontSize = 11.sp,
									color = colors.onSurfaceVariant,
								)
							}
						}

						Surface(
							shape = RoundedCornerShape(8.dp),
							color = if (vm.systemInfo != null) Color(0xFF10B981).copy(alpha = 0.15f) else colors.surfaceVariant.copy(alpha = 0.5f),
							modifier = Modifier.clickable { vm.refreshSystemInfo() },
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
								modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
							) {
								Box(
									modifier = Modifier
										.size(6.dp)
										.clip(CircleShape)
										.background(if (vm.systemInfo != null) Color(0xFF10B981) else colors.onSurfaceVariant),
								)
								Text(
									text = if (vm.systemInfo != null) "在线" else "探测中",
									fontSize = 10.5.sp,
									fontWeight = FontWeight.SemiBold,
									color = if (vm.systemInfo != null) Color(0xFF059669) else colors.onSurfaceVariant,
								)
							}
						}
					}

					val info = vm.systemInfo
					if (info != null) {
						// 主机设备与系统摘要
						Surface(
							shape = RoundedCornerShape(10.dp),
							color = colors.surfaceVariant.copy(alpha = 0.35f),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text("主机: ${info.hostname.ifBlank { "iStoreOS" }}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
									Text("${info.os} (${info.arch})", fontSize = 11.sp, color = colors.onSurfaceVariant)
								}
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text("持续运行: ${info.formattedUptime()}", fontSize = 11.sp, color = colors.onSurfaceVariant)
									Text("服务: ${info.service} v${info.version.ifBlank { "1.0.0" }}", fontSize = 11.sp, color = colors.outline)
								}
							}
						}

						// 存储空间进度条
						val disk = info.disk
						val diskPct = (disk.percent / 100.0).toFloat().coerceIn(0f, 1f)
						Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Text("存储空间池", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
								Text(
									String.format("%.1f%%", disk.percent),
									fontSize = 12.5.sp,
									fontWeight = FontWeight.Bold,
									color = if (disk.percent > 90.0) colors.error else colors.primary,
								)
							}
							LinearProgressIndicator(
								progress = { diskPct },
								modifier = Modifier
									.fillMaxWidth()
									.height(6.dp)
									.clip(RoundedCornerShape(3.dp)),
								color = if (disk.percent > 90.0) colors.error else colors.primary,
								trackColor = colors.outlineVariant.copy(alpha = 0.3f),
							)
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Text("已用 ${NasSystemInfo.formatBytes(disk.used)} / ${NasSystemInfo.formatBytes(disk.total)}", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
								Text("剩余 ${NasSystemInfo.formatBytes(disk.free)} 可用", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = colors.primary)
							}
						}

						// CPU 与内存双行指标
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							// CPU
							Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text("CPU 负载", fontSize = 11.sp, color = colors.onSurfaceVariant)
									Text(String.format("%.1f%%", info.cpuPercent), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.primary)
								}
								LinearProgressIndicator(
									progress = { (info.cpuPercent / 100.0).toFloat().coerceIn(0f, 1f) },
									modifier = Modifier
										.fillMaxWidth()
										.height(4.dp)
										.clip(RoundedCornerShape(2.dp)),
									color = colors.primary,
									trackColor = colors.outlineVariant.copy(alpha = 0.3f),
								)
								Text("${info.cpuCores} 核 · 负载 ${info.loadAvg.firstOrNull()?.let { String.format("%.2f", it) } ?: "--"}", fontSize = 10.sp, color = colors.outline)
							}

							// RAM
							Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text("运行内存 RAM", fontSize = 11.sp, color = colors.onSurfaceVariant)
									Text(String.format("%.1f%%", info.memory.percent), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.primary)
								}
								LinearProgressIndicator(
									progress = { (info.memory.percent / 100.0).toFloat().coerceIn(0f, 1f) },
									modifier = Modifier
										.fillMaxWidth()
										.height(4.dp)
										.clip(RoundedCornerShape(2.dp)),
									color = colors.primary,
									trackColor = colors.outlineVariant.copy(alpha = 0.3f),
								)
								Text("${NasSystemInfo.formatBytes(info.memory.used)} / ${NasSystemInfo.formatBytes(info.memory.total)}", fontSize = 10.sp, color = colors.outline)
							}
						}
					} else {
						Surface(
							shape = RoundedCornerShape(10.dp),
							color = colors.surfaceVariant.copy(alpha = 0.3f),
							modifier = Modifier.fillMaxWidth(),
						) {
							Row(
								modifier = Modifier.padding(12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text("点击探测实时拉取 NAS 宿主机指标", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
									Text("支持 CPU、内存、存储空间池与运行时间监控", fontSize = 10.5.sp, color = colors.onSurfaceVariant)
								}
								TextButton(onClick = { vm.refreshSystemInfo() }) {
									Text("立即探测", fontSize = 12.sp, fontWeight = FontWeight.Bold)
								}
							}
						}
					}
				}
			}

			// ==================== 分组 2: WebDAV 服务接入配置 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(RoundedCornerShape(8.dp))
								.background(colors.primary.copy(alpha = 0.12f)),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Rounded.Cloud,
								contentDescription = null,
								tint = colors.primary,
								modifier = Modifier.size(18.dp),
							)
						}
						Column {
							Text(
								text = "WebDAV 服务接入配置",
								fontSize = 14.sp,
								fontWeight = FontWeight.Bold,
								color = colors.onSurface,
							)
							Text(
								text = "私有云接入地址与鉴权信息",
								fontSize = 11.sp,
								color = colors.onSurfaceVariant,
							)
						}
					}

					// 服务地址
					OutlinedTextField(
						value = baseUrl,
						enabled = !saving,
						onValueChange = { baseUrl = it },
						label = { Text("WebDAV 服务接入地址", fontSize = 12.sp) },
						placeholder = { Text("https://nas.205002.xyz/") },
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

					// 账号与密码同行并列
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

					saveError?.let { Text(it, color = colors.error, fontSize = 11.5.sp) }

					// 测试与保存并排紧凑按钮组
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						OutlinedButton(
							onClick = {
								testing = true
								scope.launch {
									val result = withTimeoutOrNull(12_000) {
										withContext(Dispatchers.IO) {
											val effective = app.prefs.current()
											val dirty = effective.baseUrl != baseUrl || effective.user != user ||
												effective.password != password || effective.useTailscale != useTs ||
												effective.connectionMode != connMode || effective.hostname != hostname ||
												effective.authKey != authKey.trim() || effective.trustAllCerts != trustAll
											if (dirty) {
												"配置已修改，请先点「保存配置」再测试"
											} else {
												runCatching { app.repo.probeConnection(effective) }
													.fold(
														onSuccess = { "连接正常，WebDAV 服务响应成功" },
														onFailure = { "连接失败：${it.message ?: it.javaClass.simpleName}" },
													)
											}
										}
									} ?: "连接超时（12秒），请检查网络或服务地址"
									testing = false
									Toast.makeText(context, result, Toast.LENGTH_LONG).show()
								}
							},
							enabled = loaded && !testing && !saving,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.weight(1f)
								.height(42.dp),
						) {
							if (testing) {
								CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.primary)
								Spacer(modifier = Modifier.width(6.dp))
							}
							Text(if (testing) "测试中…" else "测试连通性", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
						}

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
									themePalette = currentPalette,
								)
								save(s)
							},
							enabled = loaded && !saving && !testing && baseUrl.isNotBlank() && user.isNotBlank(),
							colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier
								.weight(1f)
								.height(42.dp),
						) {
							if (saving) {
								CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.onPrimary)
								Spacer(Modifier.width(6.dp))
							}
							Text(if (saving) "保存中…" else "保存配置", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary)
						}
					}
				}
			}

			// ==================== 分组 3: 传输模式与网络链路 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
					val isLan = routeInfo.isLan || routeInfo.isLanDirect
					val isDirect = routeInfo.isDirect && !routeInfo.isRelay
					val statusDotColor = when {
						isLan -> Color(0xFF2E7D32)
						isDirect -> Color(0xFF00796B)
						routeInfo.isRelay -> Color(0xFFE65100)
						else -> colors.onSurfaceVariant
					}
					val statusBgColor = when {
						isLan -> Color(0xFFE8F5E9)
						isDirect -> Color(0xFFE0F2F1)
						routeInfo.isRelay -> Color(0xFFFFF3E0)
						else -> colors.surfaceVariant.copy(alpha = 0.5f)
					}
					val statusLabel = when {
						isLan -> "局域网直连"
						isDirect -> "极速直连 (P2P)"
						routeInfo.isRelay -> "远程中继"
						else -> "未连接"
					}

					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Box(
								modifier = Modifier
									.size(32.dp)
									.clip(RoundedCornerShape(8.dp))
									.background(colors.primary.copy(alpha = 0.12f)),
								contentAlignment = Alignment.Center,
							) {
								Icon(
									imageVector = Icons.Rounded.Speed,
									contentDescription = null,
									tint = colors.primary,
									modifier = Modifier.size(18.dp),
								)
							}
							Text(
								text = "传输模式与网络链路",
								fontSize = 14.sp,
								fontWeight = FontWeight.Bold,
								color = colors.onSurface,
							)
						}

						// 紧凑链路状态胶囊
						Box(
							modifier = Modifier
								.clip(RoundedCornerShape(6.dp))
								.background(statusBgColor)
								.clickable { vm.reloadSettings() }
								.padding(horizontal = 7.dp, vertical = 2.5.dp),
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Box(
									modifier = Modifier
										.size(5.dp)
										.clip(CircleShape)
										.background(statusDotColor),
								)
								Text(
									text = statusLabel,
									fontSize = 10.5.sp,
									fontWeight = FontWeight.Medium,
									color = statusDotColor,
								)
							}
						}
					}

					// 模式选择
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

					HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

					// 信任自签 SSL 证书
					SettingItemRow(
						icon = Icons.Rounded.Security,
						iconBg = colors.primary,
						title = "放行自签 SSL 证书",
						desc = "仅用于可信私有自建 WebDAV 服务",
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

			// ==================== 分组 4: 本地离线与外观偏好 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = colors.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(RoundedCornerShape(8.dp))
								.background(colors.primary.copy(alpha = 0.12f)),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Rounded.FolderSpecial,
								contentDescription = null,
								tint = colors.primary,
								modifier = Modifier.size(18.dp),
							)
						}
						Text(
							text = "本地离线与外观偏好",
							fontSize = 14.sp,
							fontWeight = FontWeight.Bold,
							color = colors.onSurface,
						)
					}

					// 清理缩略图
					SettingItemRow(
						icon = Icons.Rounded.CleaningServices,
						iconBg = colors.primary,
						title = "清理本地缩略图缓存",
						desc = "当前已占用 $cacheSizeStr",
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
										Toast.makeText(context, "清理失败，请重试", Toast.LENGTH_SHORT).show()
									} finally {
										clearingCache = false
									}
								}
							}) {
								Text(if (clearingCache) "清理中…" else "立即清理", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
							}
						},
					)

					HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)

					// 主题配色
					Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
						Text(text = "全局主题配色方案", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							ThemePalette.entries.forEach { p ->
								val isSelected = currentPalette.equals(p.id, ignoreCase = true)
								Column(
									horizontalAlignment = Alignment.CenterHorizontally,
									modifier = Modifier
										.clip(RoundedCornerShape(8.dp))
										.clickable {
											currentPalette = p.id
											scope.launch {
												val cur = app.prefs.current()
												app.prefs.save(cur.copy(themePalette = p.id))
											}
										}
										.padding(4.dp),
								) {
									Box(
										modifier = Modifier
											.size(30.dp)
											.clip(CircleShape)
											.background(Color(p.primaryHex))
											.then(
												if (isSelected) Modifier.border(2.5.dp, colors.onSurface, CircleShape)
												else Modifier
											),
										contentAlignment = Alignment.Center,
									) {
										if (isSelected) {
											Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
										}
									}
									Spacer(modifier = Modifier.height(2.dp))
									Text(
										text = p.title,
										fontSize = 10.sp,
										fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
										color = if (isSelected) colors.primary else colors.onSurfaceVariant,
									)
								}
							}
						}
					}
				}
			}

			// ==================== 分组 5: 关于与单独设计的更新模块 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(
					containerColor = if (vm.hasUpdate) colors.primaryContainer.copy(alpha = 0.6f) else colors.surface
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
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Box(
							modifier = Modifier
								.size(36.dp)
								.clip(RoundedCornerShape(10.dp))
								.background(colors.primary.copy(alpha = 0.12f)),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Rounded.RocketLaunch,
								contentDescription = null,
								tint = colors.primary,
								modifier = Modifier.size(20.dp),
							)
						}

						Column {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(
									text = "NasDrop v${vm.currentVersionName}",
									fontSize = 13.5.sp,
									fontWeight = FontWeight.Bold,
									color = colors.onSurface,
								)
								if (vm.hasUpdate) {
									Spacer(modifier = Modifier.width(6.dp))
									Surface(
										shape = RoundedCornerShape(6.dp),
										color = colors.error,
									) {
										Text(
											text = "新版本 v${vm.latestUpdateInfo?.versionName}",
											fontSize = 9.sp,
											fontWeight = FontWeight.ExtraBold,
											color = colors.onPrimary,
											modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
										)
									}
								}
							}
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = if (vm.hasUpdate) "发现新版本，点击立即在线升级" else "已是最新版本 · 已开启国内加速",
								fontSize = 10.5.sp,
								color = colors.onSurfaceVariant,
							)
						}
					}

					Spacer(modifier = Modifier.width(8.dp))

					// 单独设计的升级按钮：使用 Upgrade 图标，与下载/上传彻底区分
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
							containerColor = if (vm.hasUpdate) colors.primary else colors.outlineVariant.copy(alpha = 0.5f),
							contentColor = if (vm.hasUpdate) colors.onPrimary else colors.onSurfaceVariant,
						),
						shape = RoundedCornerShape(12.dp),
						contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
						modifier = Modifier.height(34.dp),
					) {
						if (checkingUpdate) {
							CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = colors.primary)
						} else {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(
									imageVector = Icons.Rounded.Upgrade,
									contentDescription = null,
									modifier = Modifier.size(15.dp),
								)
								Text(
									text = if (vm.hasUpdate) "立即更新" else "检查更新",
									fontSize = 12.sp,
									fontWeight = FontWeight.Bold,
								)
							}
						}
					}
				}
			}
		}
	}

	if (isInsideTab) {
		// 作为底部 Tab 页面时，使用平滑透明内嵌渲染
		content()
	} else {
		// 作为独立路由弹出时，带 TopAppBar 与 Back 按钮
		Scaffold(
			containerColor = colors.background,
			topBar = {
				TopAppBar(
					title = {
						Text(
							text = "系统与配置",
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
						IconButton(
							onClick = { vm.refreshSystemInfo() },
							enabled = !vm.isRefreshingSystem,
						) {
							if (vm.isRefreshingSystem) {
								CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
							} else {
								Icon(Icons.Rounded.Refresh, contentDescription = "刷新系统指标", tint = colors.onSurfaceVariant)
							}
						}
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
			Box(modifier = Modifier.padding(padding)) {
				content()
			}
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
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier.weight(1f),
		) {
			Box(
				modifier = Modifier
					.size(30.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(iconBg.copy(alpha = 0.12f)),
				contentAlignment = Alignment.Center,
			) {
				Icon(imageVector = icon, contentDescription = null, tint = iconBg, modifier = Modifier.size(16.dp))
			}
			Column {
				Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
				Text(text = desc, fontSize = 10.5.sp, color = colors.onSurfaceVariant)
			}
		}
		trailing()
	}
}
