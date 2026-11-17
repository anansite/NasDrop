package com.nasdrop.ui

import android.widget.Toast
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val app = context.app
	val routeInfo by vm.routeInfo.collectAsState()

	var loaded by remember { mutableStateOf(false) }
	var baseUrl by remember { mutableStateOf("") }
	var user by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var passwordVisible by remember { mutableStateOf(false) }
	var useTs by remember { mutableStateOf(true) }
	var connMode by remember { mutableStateOf(ConnectionMode.Auto) }
	var hostname by remember { mutableStateOf("nasdrop-android") }
	var authKey by remember { mutableStateOf("") }
	var trustAll by remember { mutableStateOf(true) }

	var testing by remember { mutableStateOf(false) }
	var logs by remember { mutableStateOf<String?>(null) }
	var checkingUpdate by remember { mutableStateOf(false) }
	var cacheSizeStr by remember { mutableStateOf("计算中...") }

	fun calculateCacheSize() {
		scope.launch(Dispatchers.IO) {
			val cacheDir = context.cacheDir
			val size = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
			val mb = size / (1024.0 * 1024.0)
			cacheSizeStr = if (mb >= 1.0) String.format("%.1f MB", mb) else String.format("%.0f KB", size / 1024.0)
		}
	}

	LaunchedEffect(Unit) {
		val s = withContext(Dispatchers.IO) { app.prefs.current() }
		baseUrl = s.baseUrl
		user = s.user
		password = s.password
		useTs = s.useTailscale
		connMode = s.connectionMode
		hostname = s.hostname
		authKey = s.authKey
		trustAll = s.trustAllCerts
		loaded = true
		calculateCacheSize()
	}

	fun save(s: Settings) {
		scope.launch {
			withContext(Dispatchers.IO) {
				app.prefs.setServer(s.baseUrl, s.user, s.password)
				app.prefs.setConnectionMode(s.connectionMode)
				app.prefs.setTailscale(s.useTailscale, s.hostname, s.authKey)
				app.prefs.setTrustAllCerts(s.trustAllCerts)
			}
			vm.reloadSettings()
		}
	}

	Scaffold(
		containerColor = Color(0xFFF7F8FA),
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "服务器与偏好设置",
						fontSize = 17.sp,
						fontWeight = FontWeight.Bold,
						color = Color(0xFF1D2129),
					)
				},
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color(0xFF1D2129))
					}
				},
				actions = {
					IconButton(onClick = {
						scope.launch(Dispatchers.IO) {
							val text = vm.logs().ifEmpty { "（暂无日志）" }
							withContext(Dispatchers.Main) { logs = text }
						}
					}) {
						Icon(Icons.Rounded.BugReport, contentDescription = "诊断日志", tint = Color(0xFF86909C))
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7F8FA)),
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
					containerColor = if (vm.hasUpdate) Color(0xFFEAF2FF) else Color.White
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
										colors = listOf(Color(0xFF2A72F6), Color(0xFF00B5A6))
									)
								),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = Icons.Rounded.CloudDownload,
								contentDescription = null,
								tint = Color.White,
								modifier = Modifier.size(20.dp),
							)
						}

						Column {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Text(
									text = if (vm.hasUpdate) "发现新版本 v${vm.latestUpdateInfo?.versionName}" else "当前版本 v${vm.currentVersionName}",
									fontSize = 14.sp,
									fontWeight = FontWeight.Bold,
									color = Color(0xFF1D2129),
								)
								if (vm.hasUpdate) {
									Spacer(modifier = Modifier.width(6.dp))
									Surface(
										shape = RoundedCornerShape(8.dp),
										color = Color(0xFFFF4D4F),
									) {
										Text(
											text = "NEW",
											fontSize = 9.sp,
											fontWeight = FontWeight.ExtraBold,
											color = Color.White,
											modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
										)
									}
								}
							}
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = if (vm.hasUpdate) "点击立即一键平滑自动更新" else "已是最新版本，无需更新",
								fontSize = 11.sp,
								color = Color(0xFF86909C),
							)
						}
					}

					Button(
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
							containerColor = if (vm.hasUpdate) Color(0xFF2A72F6) else Color(0xFFF0F2F5),
							contentColor = if (vm.hasUpdate) Color.White else Color(0xFF4E5969),
						),
						shape = RoundedCornerShape(16.dp),
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
					) {
						if (checkingUpdate) {
							CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF2A72F6))
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
				colors = CardDefaults.cardColors(containerColor = Color.White),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "WebDAV 服务配置",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = Color(0xFF1D2129),
					)
					Spacer(modifier = Modifier.height(12.dp))

					// 服务器地址
					OutlinedTextField(
						value = baseUrl,
						onValueChange = { baseUrl = it },
						label = { Text("WebDAV 服务接入地址", fontSize = 12.sp) },
						placeholder = { Text("https://nas.205002.xyz/dav/") },
						singleLine = true,
						shape = RoundedCornerShape(12.dp),
						colors = OutlinedTextFieldDefaults.colors(
							focusedBorderColor = Color(0xFF2A72F6),
							unfocusedBorderColor = Color(0xFFE5E6EB),
							focusedContainerColor = Color.White,
							unfocusedContainerColor = Color(0xFFF7F8FA),
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
							onValueChange = { user = it },
							label = { Text("账号", fontSize = 12.sp) },
							singleLine = true,
							shape = RoundedCornerShape(12.dp),
							colors = OutlinedTextFieldDefaults.colors(
								focusedBorderColor = Color(0xFF2A72F6),
								unfocusedBorderColor = Color(0xFFE5E6EB),
								focusedContainerColor = Color.White,
								unfocusedContainerColor = Color(0xFFF7F8FA),
							),
							modifier = Modifier.weight(1f),
						)

						OutlinedTextField(
							value = password,
							onValueChange = { password = it },
							label = { Text("访问密码", fontSize = 12.sp) },
							singleLine = true,
							visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
							trailingIcon = {
								IconButton(onClick = { passwordVisible = !passwordVisible }) {
									Icon(
										imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
										contentDescription = "显隐",
										tint = Color(0xFF86909C),
										modifier = Modifier.size(20.dp),
									)
								}
							},
							shape = RoundedCornerShape(12.dp),
							colors = OutlinedTextFieldDefaults.colors(
								focusedBorderColor = Color(0xFF2A72F6),
								unfocusedBorderColor = Color(0xFFE5E6EB),
								focusedContainerColor = Color.White,
								unfocusedContainerColor = Color(0xFFF7F8FA),
							),
							modifier = Modifier.weight(1f),
						)
					}
				}
			}

			// ==================== 卡片 3: 安全通道与专线穿透 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = Color.White),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "安全通道与专线穿透",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = Color(0xFF1D2129),
					)
					Spacer(modifier = Modifier.height(12.dp))

					// Tailscale 隧道开关
					SettingItemRow(
						icon = Icons.Rounded.VpnLock,
						iconBg = Color(0xFF00B578),
						title = "Tailscale 专属隧道打洞",
						desc = "在移动网络下建立端到端加密直连",
						trailing = {
							Switch(
								checked = useTs,
								onCheckedChange = { useTs = it },
								colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00B578)),
							)
						},
					)

					HorizontalDivider(color = Color(0xFFF0F2F5), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

					// 节点名称
					AnimatedVisibility(visible = useTs) {
						Column {
							OutlinedTextField(
								value = hostname,
								onValueChange = { hostname = it },
								label = { Text("本设备节点名称", fontSize = 12.sp) },
								placeholder = { Text("nasdrop-android") },
								singleLine = true,
								shape = RoundedCornerShape(12.dp),
								colors = OutlinedTextFieldDefaults.colors(
									focusedBorderColor = Color(0xFF2A72F6),
									unfocusedBorderColor = Color(0xFFE5E6EB),
									focusedContainerColor = Color.White,
									unfocusedContainerColor = Color(0xFFF7F8FA),
								),
								modifier = Modifier.fillMaxWidth(),
							)
							Spacer(modifier = Modifier.height(10.dp))
						}
					}

					// 信任自签 SSL 证书
					SettingItemRow(
						icon = Icons.Rounded.Security,
						iconBg = Color(0xFF2A72F6),
						title = "放行自签 SSL 证书",
						desc = "允许私有内网 HTTPS 证书通过",
						trailing = {
							Switch(
								checked = trustAll,
								onCheckedChange = { trustAll = it },
								colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2A72F6)),
							)
						},
					)

					HorizontalDivider(color = Color(0xFFF0F2F5), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

					// 链路闭环诊断面板
					val peerEndpoint = if (routeInfo.endpoint.isEmpty()) "P2P直连节点" else routeInfo.endpoint
					val relayEndpoint = if (routeInfo.endpoint.isEmpty()) "官方DERP中继" else routeInfo.endpoint
					val diag = when {
						routeInfo.isLan -> RouteDiagnostic(
							title = "局域网千兆直连",
							color = Color(0xFF00B42A),
							bgColor = Color(0xFFE8FFEA),
							desc = "设备与私有云处于同一家庭局域网，直达千兆/百兆满速，0 外部流量消耗。",
							advice = "当前处于最佳连接模式，延迟极低（< 5ms）。",
						)
						routeInfo.isDirect -> RouteDiagnostic(
							title = "Tailscale P2P 打洞直连",
							color = Color(0xFF2A72F6),
							bgColor = Color(0xFFEAF2FF),
							desc = "端到端加密通道已直穿打通（对端端点: $peerEndpoint），不经中继节点转发。",
							advice = "连接健康，无中继带宽上限，传输速度由您的家庭宽带上行与手机网络决定。",
						)
						routeInfo.isRelay -> RouteDiagnostic(
							title = "Tailscale DERP 中继转发模式",
							color = Color(0xFFFF7D00),
							bgColor = Color(0xFFFFF7E8),
							desc = "因两端对称型 NAT 或防火墙限制，P2P 打洞暂未穿透，当前流量由 Tailscale 中转节点（$relayEndpoint）转发。",
							advice = "【闭环优化方案】\n1. 点击上方「重新握手打洞」尝试触发 STUN 双向穿透；\n2. 回家后连入家庭 Wi-Fi 可自动切换为绿点千兆直连；\n3. 长期优化：在家庭路由器开启 UPnP 或 Full Cone NAT，即可常态保持蓝点直连。",
						)
						else -> RouteDiagnostic(
							title = "离线或未建立连接",
							color = Color(0xFFF53F3F),
							bgColor = Color(0xFFFFECE8),
							desc = "未能与私有云建立有效连接，请核对接入地址或 Tailscale 登录状态。",
							advice = "请检查上方 WebDAV 服务地址及账密，或点击上方「去授权」完成 Tailscale 登录。",
						)
					}
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
										.size(10.dp)
										.clip(CircleShape)
										.background(diag.color),
								)
								Text(
									text = diag.title,
									fontSize = 13.sp,
									fontWeight = FontWeight.Bold,
									color = diag.color,
								)
							}
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
								TextButton(onClick = {
									vm.reloadSettings()
									Toast.makeText(context, "正在尝试重新握手打洞...", Toast.LENGTH_SHORT).show()
								}) {
									Text("重新握手打洞", fontSize = 12.sp, fontWeight = FontWeight.Bold)
								}
								if (vm.needsLogin) {
									Button(
										onClick = {
											val url = vm.loginUrl()
											if (url.isNotEmpty()) {
												CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
											}
										},
										colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
										shape = RoundedCornerShape(12.dp),
										contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
									) {
										Text("去授权", fontSize = 11.sp, fontWeight = FontWeight.Bold)
									}
								}
							}
						}
						Spacer(modifier = Modifier.height(8.dp))
						// 闭环诊断说明卡片
						Surface(
							shape = RoundedCornerShape(10.dp),
							color = diag.bgColor,
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(modifier = Modifier.padding(12.dp)) {
								Text(
									text = diag.desc,
									fontSize = 12.sp,
									color = Color(0xFF1D2129),
									lineHeight = 17.sp,
								)
								Spacer(modifier = Modifier.height(6.dp))
								Text(
									text = diag.advice,
									fontSize = 11.sp,
									color = Color(0xFF4E5969),
									lineHeight = 16.sp,
								)
							}
						}
						Spacer(modifier = Modifier.height(8.dp))
						Box(
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(8.dp))
								.background(Color(0xFFF7F8FA))
								.padding(horizontal = 10.dp, vertical = 8.dp),
						) {
							Text(
								text = vm.linkStatus.ifEmpty { "节点探测与测速中..." },
								fontSize = 11.sp,
								color = Color(0xFF86909C),
								lineHeight = 15.sp,
							)
						}
					}
				}
			}

			// ==================== 卡片 4: 本地缓存管理 ====================
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = Color.White),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text(
						text = "本地离线与缩略图缓存",
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
						color = Color(0xFF1D2129),
					)
					Spacer(modifier = Modifier.height(12.dp))

					SettingItemRow(
						icon = Icons.Rounded.CleaningServices,
						iconBg = Color(0xFFFF7A00),
						title = "清理图片/视频本地缓存",
						desc = "当前占用 $cacheSizeStr",
						trailing = {
							TextButton(onClick = {
								scope.launch(Dispatchers.IO) {
									app.repo.clearImageCache()
									context.cacheDir.deleteRecursively()
									context.cacheDir.mkdirs()
									calculateCacheSize()
								}
								Toast.makeText(context, "缓存已成功清理", Toast.LENGTH_SHORT).show()
							}) {
								Text("立即清理", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF7A00))
							}
						},
					)
				}
			}

			Spacer(modifier = Modifier.height(6.dp))

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
					Toast.makeText(context, "设置已保存并立即生效", Toast.LENGTH_SHORT).show()
					onBack()
				},
				enabled = loaded && baseUrl.isNotBlank() && user.isNotBlank(),
				colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				Text("保存并立即应用", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
			}

			OutlinedButton(
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
					testing = true
					scope.launch {
						val msg = withContext(Dispatchers.IO) {
							runCatching { app.repo.client(s).list("") }
								.fold(
									onSuccess = { "连上了！根目录共 ${it.size} 项文件" },
									onFailure = { it.message ?: it.javaClass.simpleName },
								)
						}
						testing = false
						Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
					}
				},
				enabled = !testing,
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				if (testing) {
					CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2A72F6))
					Spacer(modifier = Modifier.width(8.dp))
				}
				Text(if (testing) "正在双向测试连接..." else "测试连通性", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
				Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
			}
			Column {
				Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
				Text(text = desc, fontSize = 11.sp, color = Color(0xFF86909C))
			}
		}
		trailing()
	}
}

private data class RouteDiagnostic(
	val title: String,
	val color: Color,
	val bgColor: Color,
	val desc: String,
	val advice: String,
)
