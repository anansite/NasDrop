package com.nasdrop.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

enum class ThemePalette(val id: String, val title: String, val primaryHex: Long, val isDarkDefault: Boolean = false) {
	Aurora("AURORA", "极光蓝", 0xFF2563EB),
	Emerald("EMERALD", "松石绿", 0xFF059669),
	Amber("AMBER", "琥珀橙", 0xFFD97706),
	Titanium("TITANIUM", "钛极灰", 0xFF475569),
	Obsidian("OBSIDIAN", "曜石黑", 0xFF60A5FA, isDarkDefault = true);

	companion object {
		fun fromId(id: String?): ThemePalette = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Aurora
	}
}

private fun createLightScheme(palette: ThemePalette): ColorScheme {
	val primary = Color(palette.primaryHex)
	val (container, onContainer) = when (palette) {
		ThemePalette.Aurora -> Color(0xFFEFF6FF) to Color(0xFF1D4ED8)
		ThemePalette.Emerald -> Color(0xFFECFDF5) to Color(0xFF047857)
		ThemePalette.Amber -> Color(0xFFFFFBEB) to Color(0xFFB45309)
		ThemePalette.Titanium -> Color(0xFFF1F5F9) to Color(0xFF334155)
		ThemePalette.Obsidian -> Color(0xFFE2E8F0) to Color(0xFF0F172A)
	}

	return lightColorScheme(
		primary = primary,
		onPrimary = Color.White,
		primaryContainer = container,
		onPrimaryContainer = onContainer,
		secondary = primary.copy(alpha = 0.8f),
		onSecondary = Color.White,
		secondaryContainer = container,
		onSecondaryContainer = onContainer,
		tertiary = Color(0xFF10B981),
		onTertiary = Color.White,
		tertiaryContainer = Color(0xFFECFDF5),
		onTertiaryContainer = Color(0xFF047857),
		background = Color(0xFFF8FAFC),
		onBackground = Color(0xFF0F172A),
		surface = Color(0xFFFFFFFF),
		onSurface = Color(0xFF0F172A),
		surfaceVariant = Color(0xFFF1F5F9),
		onSurfaceVariant = Color(0xFF475569),
		outline = Color(0xFFCBD5E1),
		outlineVariant = Color(0xFFE2E8F0),
		error = Color(0xFFEF4444),
	)
}

private fun createDarkScheme(palette: ThemePalette): ColorScheme {
	val primary = if (palette == ThemePalette.Obsidian) Color(0xFF60A5FA) else Color(palette.primaryHex)
	return darkColorScheme(
		primary = primary,
		onPrimary = Color(0xFF0F172A),
		primaryContainer = Color(0xFF1E293B),
		onPrimaryContainer = Color(0xFFF8FAFC),
		secondary = primary.copy(alpha = 0.8f),
		onSecondary = Color(0xFF0F172A),
		secondaryContainer = Color(0xFF243047),
		onSecondaryContainer = Color(0xFFF8FAFC),
		tertiary = Color(0xFF34D399),
		onTertiary = Color(0xFF064E3B),
		tertiaryContainer = Color(0xFF065F46),
		onTertiaryContainer = Color(0xFFA7F3D0),
		background = Color(0xFF0F172A),
		onBackground = Color(0xFFF8FAFC),
		surface = Color(0xFF1E293B),
		onSurface = Color(0xFFF8FAFC),
		surfaceVariant = Color(0xFF243047),
		onSurfaceVariant = Color(0xFFCBD5E1),
		outline = Color(0xFF475569),
		outlineVariant = Color(0xFF334155),
		error = Color(0xFFF87171),
	)
}

private val NasDropTypography = Typography(
	titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
	titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
	titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
	bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
	bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
	bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
	labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
	labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val NasDropShapes = Shapes(
	extraSmall = RoundedCornerShape(6.dp),
	small = RoundedCornerShape(10.dp),
	medium = RoundedCornerShape(14.dp),
	large = RoundedCornerShape(18.dp),
	extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun NasDropTheme(
	palette: ThemePalette = ThemePalette.Aurora,
	darkTheme: Boolean = isSystemInDarkTheme() || palette.isDarkDefault,
	dynamicColor: Boolean = false,
	content: @Composable () -> Unit,
) {
	val context = LocalContext.current
	val scheme = when {
		dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
			if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		darkTheme -> createDarkScheme(palette)
		else -> createLightScheme(palette)
	}
	MaterialTheme(
		colorScheme = scheme,
		typography = NasDropTypography,
		shapes = NasDropShapes,
		content = content,
	)
}
