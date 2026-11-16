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

private val LightColors = lightColorScheme(
	primary = Color(0xFF3D5AFE),
	onPrimary = Color.White,
	primaryContainer = Color(0xFFDDE1FF),
	onPrimaryContainer = Color(0xFF00105C),
	secondary = Color(0xFF5B5D72),
	onSecondary = Color.White,
	secondaryContainer = Color(0xFFE6E6F5),
	onSecondaryContainer = Color(0xFF181A2C),
	tertiary = Color(0xFF00696E),
	onTertiary = Color.White,
	tertiaryContainer = Color(0xFF9CF1F6),
	onTertiaryContainer = Color(0xFF002022),
	background = Color(0xFFFBF8FD),
	onBackground = Color(0xFF1B1B1F),
	surface = Color(0xFFFBF8FD),
	onSurface = Color(0xFF1B1B1F),
	surfaceVariant = Color(0xFFE4E1EC),
	onSurfaceVariant = Color(0xFF46464F),
	outline = Color(0xFF777680),
	outlineVariant = Color(0xFFC7C5D0),
	error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFFB9C3FF),
	onPrimary = Color(0xFF00208B),
	primaryContainer = Color(0xFF1B3AA8),
	onPrimaryContainer = Color(0xFFDDE1FF),
	secondary = Color(0xFFC4C5DD),
	onSecondary = Color(0xFF2D2F42),
	secondaryContainer = Color(0xFF434559),
	onSecondaryContainer = Color(0xFFE0E1F9),
	tertiary = Color(0xFF80D4DA),
	onTertiary = Color(0xFF003739),
	tertiaryContainer = Color(0xFF004F53),
	onTertiaryContainer = Color(0xFF9CF1F6),
	background = Color(0xFF131316),
	onBackground = Color(0xFFE4E1E6),
	surface = Color(0xFF131316),
	onSurface = Color(0xFFE4E1E6),
	surfaceVariant = Color(0xFF46464F),
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
	dynamicColor: Boolean = true,
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
