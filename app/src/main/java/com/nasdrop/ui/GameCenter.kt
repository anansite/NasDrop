package com.nasdrop.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nasdrop.BrowserViewModel
import com.nasdrop.DavEntry
import com.nasdrop.hasAudio
import com.nasdrop.hasMemo
import com.nasdrop.isPathWithin
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 家庭互动游戏中心选择抽屉：
 * 提供游戏清单供用户挑选。本次首发包含「时光漫游抽取」，并为后续游戏留好扩展入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameCenterSheet(
	onDismiss: () -> Unit,
	onSelectRandomPhotoGame: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		containerColor = MaterialTheme.colorScheme.surface,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 24.dp)
				.padding(bottom = 36.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.primaryContainer,
						modifier = Modifier.size(38.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								Icons.Rounded.SportsEsports,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(22.dp),
							)
						}
					}
					Column {
						Text(
							"时光杂货铺",
							style = MaterialTheme.typography.titleLarge,
							fontWeight = FontWeight.Bold,
						)
						Text(
							"挑选互动小玩意，与家人重温时光乐趣",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
				IconButton(onClick = onDismiss) {
					Icon(Icons.Rounded.Close, contentDescription = "关闭")
				}
			}

			// 游戏 1：时光漫游抽取（支持选定相册、随机抽取无限滑动、即时定位）
			Surface(
				shape = RoundedCornerShape(18.dp),
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(18.dp))
					.clickable {
						onDismiss()
						onSelectRandomPhotoGame()
					},
			) {
				Row(
					modifier = Modifier.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(14.dp),
				) {
					Surface(
						shape = CircleShape,
						color = Color(0xFF38BDF8).copy(alpha = 0.2f),
						modifier = Modifier.size(48.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								Icons.Rounded.Casino,
								contentDescription = null,
								tint = Color(0xFF0284C7),
								modifier = Modifier.size(26.dp),
							)
						}
					}
					Column(modifier = Modifier.weight(1f)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								"时光漫游抽取",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
							)
							Surface(
								shape = RoundedCornerShape(8.dp),
								color = Color(0xFF10B981).copy(alpha = 0.15f),
							) {
								Text(
									"可定位",
									fontSize = 11.sp,
									fontWeight = FontWeight.Bold,
									color = Color(0xFF059669),
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								)
							}
						}
						Spacer(Modifier.height(4.dp))
						Text(
							"选定相册，随机抽取照片无限滑动，探索被遗忘的美好瞬间，随时精准定位原相册",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Icon(
						Icons.Rounded.ChevronRight,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			// 预告游戏：猜猜这是哪一年
			Surface(
				shape = RoundedCornerShape(18.dp),
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier.padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(14.dp),
				) {
					Surface(
						shape = CircleShape,
						color = MaterialTheme.colorScheme.surfaceVariant,
						modifier = Modifier.size(48.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								Icons.Rounded.HelpOutline,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(24.dp),
							)
						}
					}
					Column(modifier = Modifier.weight(1f)) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Text(
								"猜猜这是哪一年",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
							)
							Surface(
								shape = RoundedCornerShape(8.dp),
								color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
							) {
								Text(
									"筹备中",
									fontSize = 11.sp,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
								)
							}
						}
						Spacer(Modifier.height(4.dp))
						Text(
							"遮住拍摄年份，全家人一起竞猜当年回忆，敬请期待！",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
						)
					}
					Icon(
						Icons.Rounded.HourglassEmpty,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
					)
				}
			}
		}
	}
}

/**
 * 随机照片漫游游戏主界面：
 * 1. 支持挑选特定相册（或全部照片）；
 * 2. 随机打乱抽取，支持无限左右滑动；
 * 3. 伴随日期隐藏小眼睛交互；
 * 4. 右下角强力「定位」按钮，一键直达原相册对应位置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomPhotoGameScreen(
	vm: BrowserViewModel,
	onBack: () -> Unit,
	onLocate: (entry: DavEntry, album: DavEntry?) -> Unit,
	urlOf: (String) -> String?,
	directUrlOf: (String) -> String?,
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	// 选定相册路径集合（空代表从全部照片中抽取）
	var selectedAlbumPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
	var shuffleSeed by remember { mutableIntStateOf(0) }
	var showAlbumPickerSheet by remember { mutableStateOf(false) }
	var hideDateGuessMode by remember { mutableStateOf(false) }

	val validFolders = remember(vm.allAlbumFolders) {
		vm.allAlbumFolders.filter { it.path.isNotBlank() }
	}

	// 依据选定相册集合过滤出的候选照片池
	val candidatePhotos = remember(selectedAlbumPaths, vm.allPhotos, shuffleSeed) {
		val pool = if (selectedAlbumPaths.isEmpty()) {
			vm.allPhotos.filter { it.isImage }
		} else {
			vm.allPhotos.filter { photo ->
				photo.isImage && selectedAlbumPaths.any { albumPath -> isPathWithin(photo.path, albumPath) }
			}
		}
		pool.shuffled()
	}

	val currentAlbumName = remember(selectedAlbumPaths, validFolders) {
		when {
			selectedAlbumPaths.isEmpty() || selectedAlbumPaths.size == validFolders.size -> "全部照片"
			selectedAlbumPaths.size == 1 -> {
				val single = selectedAlbumPaths.first()
				validFolders.firstOrNull { it.path == single }?.name ?: "已选 1 个相册"
			}
			else -> "已选 ${selectedAlbumPaths.size} 个相册"
		}
	}

	// 虚拟无限滑动页码（总共 100,000 页，从 50,000 起始，左右滑均可无限循环）
	val totalPageCount = if (candidatePhotos.isEmpty()) 1 else 100_000
	val initialPage = if (candidatePhotos.isEmpty()) 0 else 50_000
	val pagerState = rememberPagerState(initialPage = initialPage) { totalPageCount }

	BackHandler(onBack = onBack)

	Dialog(
		onDismissRequest = onBack,
		properties = DialogProperties(
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
		),
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black),
		) {
			if (candidatePhotos.isEmpty()) {
				// 空相册状态提示
				Column(
					modifier = Modifier
						.align(Alignment.Center)
						.padding(32.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Surface(
						shape = CircleShape,
						color = Color.White.copy(alpha = 0.1f),
						modifier = Modifier.size(64.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								Icons.Rounded.FolderOpen,
								contentDescription = null,
								tint = Color.White.copy(alpha = 0.7f),
								modifier = Modifier.size(32.dp),
							)
						}
					}
					Text(
						"「$currentAlbumName」暂无可抽取的照片",
						color = Color.White,
						fontSize = 16.sp,
						fontWeight = FontWeight.Bold,
					)
					Text(
						"该相册内暂无图片内容，请点击下方按钮切换到其他相册",
						color = Color.White.copy(alpha = 0.6f),
						fontSize = 13.sp,
						textAlign = TextAlign.Center,
					)
					Button(
						onClick = { showAlbumPickerSheet = true },
						colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
						shape = RoundedCornerShape(12.dp),
					) {
						Text("挑选其他相册")
					}
				}
			} else {
				// 无限循环滑动照片画卷
				HorizontalPager(
					state = pagerState,
					modifier = Modifier.fillMaxSize(),
				) { page ->
					val index = ((page % candidatePhotos.size) + candidatePhotos.size) % candidatePhotos.size
					val entry = candidatePhotos[index]
					val imgUrl = directUrlOf(entry.path) ?: urlOf(entry.path) ?: ""

					Box(
						modifier = Modifier.fillMaxSize(),
						contentAlignment = Alignment.Center,
					) {
						SubcomposeAsyncImage(
							model = ImageRequest.Builder(context)
								.data(imgUrl)
								.crossfade(true)
								.build(),
							contentDescription = entry.name,
							contentScale = ContentScale.Fit,
							modifier = Modifier.fillMaxSize(),
							loading = {
								Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
									CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.dp)
								}
							},
						)
					}
				}

				// 当前页照片数据与归属相册定位解析
				val currentIndex = ((pagerState.currentPage % candidatePhotos.size) + candidatePhotos.size) % candidatePhotos.size
				val currentEntry = candidatePhotos[currentIndex]
				val matchedAlbum = remember(currentEntry.path, vm.allAlbumFolders) {
					vm.allAlbumFolders.firstOrNull { it.path.isNotBlank() && isPathWithin(currentEntry.path, it.path) }
				}

				// 底部信息卡片与定位操作
				Box(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.fillMaxWidth()
						.background(
							Brush.verticalGradient(
								colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
							),
						)
						.navigationBarsPadding()
						.padding(horizontal = 16.dp, vertical = 16.dp),
				) {
					Surface(
						shape = RoundedCornerShape(20.dp),
						color = Color(0xFF1E293B).copy(alpha = 0.8f),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Column(
								modifier = Modifier.weight(1f),
								verticalArrangement = Arrangement.spacedBy(4.dp),
							) {
								// 拍摄时间与遮罩小眼睛
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									val dateStr = if (currentEntry.effectiveDate > 0) {
										SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(currentEntry.effectiveDate))
									} else "未知时间"

									Text(
										text = if (hideDateGuessMode) "❓❓ 猜猜哪一年" else dateStr,
										color = if (hideDateGuessMode) Color(0xFF38BDF8) else Color.White,
										fontSize = 14.sp,
										fontWeight = FontWeight.Bold,
									)

									// 小眼睛切换猜年份模式
									IconButton(
										onClick = { hideDateGuessMode = !hideDateGuessMode },
										modifier = Modifier.size(24.dp),
									) {
										Icon(
											imageVector = if (hideDateGuessMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
											contentDescription = if (hideDateGuessMode) "查看真实日期" else "隐藏年份猜一猜",
											tint = if (hideDateGuessMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.6f),
											modifier = Modifier.size(16.dp),
										)
									}
								}

								// 文件名与相册归属
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Text(
										text = "相册: ${matchedAlbum?.name ?: "全部照片"} · ${currentEntry.name}",
										color = Color.White.copy(alpha = 0.7f),
										fontSize = 11.sp,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										modifier = Modifier.weight(1f, fill = false),
									)

									if (currentEntry.hasAudio || currentEntry.hasMemo) {
										Surface(
											shape = RoundedCornerShape(6.dp),
											color = Color(0xFF38BDF8).copy(alpha = 0.25f),
										) {
											Row(
												modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.spacedBy(2.dp),
											) {
												Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(10.dp))
												Text("伴生记忆", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
											}
										}
									}
								}
							}

							Spacer(Modifier.width(12.dp))

							// 核心「定位」按钮：点击直接跳转相册并定位该照片
							Button(
								onClick = {
									onLocate(currentEntry, matchedAlbum)
								},
								colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
								shape = RoundedCornerShape(14.dp),
								contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(4.dp),
								) {
									Icon(
										Icons.Rounded.MyLocation,
										contentDescription = "定位",
										modifier = Modifier.size(16.dp),
									)
									Text("定位", fontSize = 13.sp, fontWeight = FontWeight.Bold)
								}
							}
						}
					}
				}
			}

			// 顶部悬浮控制栏（返回、相册选择胶囊、重新洗牌）
			Box(
				modifier = Modifier
					.align(Alignment.TopCenter)
					.fillMaxWidth()
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
						),
					)
					.statusBarsPadding()
					.padding(horizontal = 12.dp, vertical = 8.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					// 退出游戏返回
					IconButton(onClick = onBack) {
						Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
					}

					// 选定相册切换胶囊（点击呼出相册选择抽屉）
					Surface(
						shape = RoundedCornerShape(20.dp),
						color = Color.White.copy(alpha = 0.2f),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
						modifier = Modifier
							.clip(RoundedCornerShape(20.dp))
							.clickable { showAlbumPickerSheet = true },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Icon(
								Icons.Rounded.FolderOpen,
								contentDescription = null,
								tint = Color.White,
								modifier = Modifier.size(16.dp),
							)
							Text(
								text = currentAlbumName,
								color = Color.White,
								fontSize = 13.sp,
								fontWeight = FontWeight.Bold,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Icon(
								Icons.Rounded.KeyboardArrowDown,
								contentDescription = null,
								tint = Color.White.copy(alpha = 0.8f),
								modifier = Modifier.size(16.dp),
							)
						}
					}

					// 重新洗牌抽奖
					Surface(
						shape = CircleShape,
						color = Color.White.copy(alpha = 0.2f),
						modifier = Modifier
							.size(38.dp)
							.clip(CircleShape)
							.clickable {
								shuffleSeed++
								Toast.makeText(context, "已重新洗牌打乱", Toast.LENGTH_SHORT).show()
							},
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								Icons.Rounded.Shuffle,
								contentDescription = "重新洗牌",
								tint = Color.White,
								modifier = Modifier.size(20.dp),
							)
						}
					}
				}
			}
		}
	}

	// 选定相册底部抽屉（支持多相册勾选、全选与反选）
	if (showAlbumPickerSheet) {
		var tempSelectedPaths by remember(showAlbumPickerSheet) {
			mutableStateOf(selectedAlbumPaths)
		}
		val totalPhotosCount = remember(vm.allPhotos) { vm.allPhotos.count { it.isImage } }
		val tempCandidateCount = remember(tempSelectedPaths, vm.allPhotos) {
			if (tempSelectedPaths.isEmpty()) totalPhotosCount
			else vm.allPhotos.count { photo -> photo.isImage && tempSelectedPaths.any { isPathWithin(photo.path, it) } }
		}

		ModalBottomSheet(
			onDismissRequest = {
				selectedAlbumPaths = tempSelectedPaths
				showAlbumPickerSheet = false
			},
			containerColor = MaterialTheme.colorScheme.surface,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 24.dp)
					.padding(bottom = 36.dp),
				verticalArrangement = Arrangement.spacedBy(14.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Column {
						Text(
							"选定抽取相册",
							style = MaterialTheme.typography.titleLarge,
							fontWeight = FontWeight.Bold,
						)
						Text(
							"支持多相册勾选组合，打造专属时光漫游池",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					IconButton(onClick = {
						selectedAlbumPaths = tempSelectedPaths
						showAlbumPickerSheet = false
					}) {
						Icon(Icons.Rounded.Close, contentDescription = "关闭")
					}
				}

				// 顶部快捷控制胶囊栏：全选、全部/重置、选中统计
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					// 快捷全选
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
						modifier = Modifier
							.clip(RoundedCornerShape(10.dp))
							.clickable {
								tempSelectedPaths = validFolders.map { it.path }.toSet()
							},
					) {
						Text(
							"全选相册",
							fontSize = 12.sp,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.primary,
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
						)
					}

					// 全部照片 / 清空重置
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
						modifier = Modifier
							.clip(RoundedCornerShape(10.dp))
							.clickable {
								tempSelectedPaths = emptySet()
							},
					) {
						Text(
							"全部照片",
							fontSize = 12.sp,
							fontWeight = FontWeight.Medium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
						)
					}

					Spacer(Modifier.weight(1f))

					// 选中计数气泡
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = Color(0xFF10B981).copy(alpha = 0.15f),
					) {
						Text(
							text = if (tempSelectedPaths.isEmpty()) "全部相册 · $tempCandidateCount 张"
								else "已勾选 ${tempSelectedPaths.size} 个相册 · $tempCandidateCount 张",
							fontSize = 12.sp,
							fontWeight = FontWeight.Bold,
							color = Color(0xFF059669),
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
						)
					}
				}

				LazyColumn(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f, fill = false),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					// 选项 1：全部照片（点击重置为全量相册）
					item {
						val isAllActive = tempSelectedPaths.isEmpty() || (validFolders.isNotEmpty() && tempSelectedPaths.size == validFolders.size)
						Surface(
							shape = RoundedCornerShape(14.dp),
							color = if (isAllActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
							border = BorderStroke(
								1.dp,
								if (isAllActive) MaterialTheme.colorScheme.primary else Color.Transparent,
							),
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(14.dp))
								.clickable {
									tempSelectedPaths = emptySet()
								},
						) {
							Row(
								modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(10.dp),
								) {
									Icon(
										Icons.Rounded.Casino,
										contentDescription = null,
										tint = if (isAllActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(20.dp),
									)
									Text(
										"全部相册 (全局混抽)",
										fontWeight = if (isAllActive) FontWeight.Bold else FontWeight.Medium,
										color = if (isAllActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
									)
								}
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Text(
										"$totalPhotosCount 张",
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
									Surface(
										shape = RoundedCornerShape(6.dp),
										color = if (isAllActive) MaterialTheme.colorScheme.primary else Color.Transparent,
										border = BorderStroke(1.5.dp, if (isAllActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
										modifier = Modifier.size(20.dp),
									) {
										Box(contentAlignment = Alignment.Center) {
											if (isAllActive) {
												Icon(
													Icons.Rounded.Check,
													contentDescription = null,
													tint = MaterialTheme.colorScheme.onPrimary,
													modifier = Modifier.size(14.dp),
												)
											}
										}
									}
								}
							}
						}
					}

					// 选项 2+：每个独立的子相册（支持多选勾选）
					items(validFolders, key = { it.path }) { folder ->
						val isChecked = folder.path in tempSelectedPaths
						val albumPhotoCount = vm.allPhotos.count { it.isImage && isPathWithin(it.path, folder.path) }

						Surface(
							shape = RoundedCornerShape(14.dp),
							color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
							border = BorderStroke(
								1.dp,
								if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent,
							),
							modifier = Modifier
								.fillMaxWidth()
								.clip(RoundedCornerShape(14.dp))
								.clickable {
									tempSelectedPaths = if (isChecked) {
										tempSelectedPaths - folder.path
									} else {
										tempSelectedPaths + folder.path
									}
								},
						) {
							Row(
								modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(10.dp),
									modifier = Modifier.weight(1f, fill = false),
								) {
									Icon(
										Icons.Rounded.FolderOpen,
										contentDescription = null,
										tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(20.dp),
									)
									Text(
										folder.name,
										fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
										color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Text(
										"$albumPhotoCount 张",
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
									Surface(
										shape = RoundedCornerShape(6.dp),
										color = if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent,
										border = BorderStroke(1.5.dp, if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
										modifier = Modifier.size(20.dp),
									) {
										Box(contentAlignment = Alignment.Center) {
											if (isChecked) {
												Icon(
													Icons.Rounded.Check,
													contentDescription = null,
													tint = MaterialTheme.colorScheme.onPrimary,
													modifier = Modifier.size(14.dp),
												)
											}
										}
									}
								}
							}
						}
					}
				}

				// 底部显式确认抽取大按钮
				Button(
					onClick = {
						selectedAlbumPaths = tempSelectedPaths
						shuffleSeed++
						showAlbumPickerSheet = false
					},
					modifier = Modifier
						.fillMaxWidth()
						.height(48.dp),
					shape = RoundedCornerShape(14.dp),
					colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
						Text(
							text = "确定抽取 (${if (tempSelectedPaths.isEmpty()) "全部相册" else "已选 ${tempSelectedPaths.size} 个相册"} · ${tempCandidateCount} 张)",
							fontWeight = FontWeight.Bold,
							fontSize = 15.sp,
						)
					}
				}
			}
		}
	}
}
