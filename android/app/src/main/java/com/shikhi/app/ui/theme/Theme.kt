package com.shikhi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ported from the web design tokens (frontend/src/index.css): warm paper background,
// indigo brand, terracotta accent. Full palette lands with the MA4 theming pass.

private val Brand = Color(0xFF443DBF)
private val Accent = Color(0xFFC05A34)
private val PaperLight = Color(0xFFFAF8F3)
private val InkLight = Color(0xFF23212E)
private val PaperDark = Color(0xFF17161F)
private val InkDark = Color(0xFFEDEBF5)

private val LightColors = lightColorScheme(
	primary = Brand,
	onPrimary = Color.White,
	secondary = Accent,
	onSecondary = Color.White,
	background = PaperLight,
	onBackground = InkLight,
	surface = PaperLight,
	onSurface = InkLight,
)

private val DarkColors = darkColorScheme(
	primary = Color(0xFF9B96E8),
	onPrimary = Color(0xFF1E1B4B),
	secondary = Color(0xFFE08A66),
	onSecondary = Color(0xFF3A1608),
	background = PaperDark,
	onBackground = InkDark,
	surface = PaperDark,
	onSurface = InkDark,
)

@Composable
fun ShikhiTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit,
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColors else LightColors,
		content = content,
	)
}
