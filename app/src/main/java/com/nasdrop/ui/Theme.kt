package com.nasdrop.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 看图与播放采用固定暗色画布，避免系统主题改变媒体内容周围的对比度。
internal val MediaBackdrop = Color(0xFF080A0E)
internal val OnMediaBackdrop = Color(0xFFF7F8FC)
internal val MediaOverlay = Color(0xFF1E293B)
internal val MediaAccent = Color(0xFFAEC9FF)
internal val MediaDanger = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
	primary = Color(0xFF2864D8), // NasDrop 主操作蓝，浅深色保持同一色相。
	onPrimary = Color.White,
	primaryContainer = Color(0xFFE8F1FF),
	onPrimaryContainer = Color(0xFF003FA4),
	secondary = Color(0xFF7652AA),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFF0E5FF),
	onSecondaryContainer = Color(0xFF321353),
	tertiary = Color(0xFF137B61),
	onTertiary = Color.White,
	tertiaryContainer = Color(0xFFE6F8F1),
	onTertiaryContainer = Color(0xFF006642),
	background = Color(0xFFF7F8FA),
	onBackground = Color(0xFF1D2129),
	surface = Color(0xFFFFFFFF), // 纯白卡片
	onSurface = Color(0xFF1D2129),
	surfaceVariant = Color(0xFFF2F3F5),
	onSurfaceVariant = Color(0xFF5C6572),
	outline = Color(0xFFC9CDD4),
	outlineVariant = Color(0xFFE5E6EB),
	error = Color(0xFFF53F3F),
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFFACC7FF),
	onPrimary = Color(0xFF00316E),
	primaryContainer = Color(0xFF164987),
	onPrimaryContainer = Color(0xFFD8E6FF),
	secondary = Color(0xFFD9B8FF),
	onSecondary = Color(0xFF42235F),
	secondaryContainer = Color(0xFF593A77),
	onSecondaryContainer = Color(0xFFF0DEFF),
	tertiary = Color(0xFF80D4DA),
	onTertiary = Color(0xFF003739),
	tertiaryContainer = Color(0xFF004F53),
	onTertiaryContainer = Color(0xFF9CF1F6),
	background = Color(0xFF131316),
	onBackground = Color(0xFFE4E1E6),
	surface = Color(0xFF191B20),
	onSurface = Color(0xFFE4E1E6),
	surfaceVariant = Color(0xFF292D35),
	onSurfaceVariant = Color(0xFFC7C5D0),
	outline = Color(0xFF91909A),
	outlineVariant = Color(0xFF46464F),
	error = Color(0xFFFFB4AB),
)

private val NasDropTypography = Typography(
	titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
	titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
	titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
	bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
	bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
	bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
	labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
	labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val NasDropShapes = Shapes(
	extraSmall = RoundedCornerShape(6.dp),
	small = RoundedCornerShape(10.dp),
	medium = RoundedCornerShape(14.dp),
	large = RoundedCornerShape(20.dp),
	extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NasDropTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	dynamicColor: Boolean = false,
	content: @Composable () -> Unit,
) {
	val context = LocalContext.current
	val scheme = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		darkTheme -> DarkColors
		else -> LightColors
	}
	MaterialTheme(
		colorScheme = scheme,
		typography = NasDropTypography,
		shapes = NasDropShapes,
		content = content,
	)
}
