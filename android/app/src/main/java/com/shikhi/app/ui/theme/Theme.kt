package com.shikhi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Ported from the web design tokens (frontend/src/index.css): warm manuscript paper,
// deep indigo ink (brand), Bengal terracotta accent, warmed semantic colors.

// ---- Light (primer page) ----
private val LightColors = lightColorScheme(
	primary = Color(0xFF443DBF), // --brand
	onPrimary = Color(0xFFFFFFFF), // --on-brand
	primaryContainer = Color(0xFFECEBFA), // --brand-tint
	onPrimaryContainer = Color(0xFF362FA6), // --brand-hover
	secondary = Color(0xFFC05A34), // --accent
	onSecondary = Color(0xFFFFFFFF),
	secondaryContainer = Color(0xFFF3E0D5), // --accent-soft
	onSecondaryContainer = Color(0xFF23223F),
	tertiary = Color(0xFF1F7A52), // --success
	tertiaryContainer = Color(0xFFE6F4EC), // --success-bg
	error = Color(0xFFC8362A), // --danger
	errorContainer = Color(0xFFFBEAE7), // --danger-bg
	background = Color(0xFFFAF8F3), // --bg
	onBackground = Color(0xFF23223F), // --fg
	surface = Color(0xFFFAF8F3),
	onSurface = Color(0xFF23223F),
	surfaceVariant = Color(0xFFF3EFE6), // --surface-2
	onSurfaceVariant = Color(0xFF5C586E), // --fg-muted
	outline = Color(0xFFD7CFBE), // --border-strong
	outlineVariant = Color(0xFFE8E2D6), // --border
)

// ---- Dark (warm ink-black, not cold slate) ----
private val DarkColors = darkColorScheme(
	primary = Color(0xFF8F88F7),
	onPrimary = Color(0xFF16151D),
	primaryContainer = Color(0xFF2A2852),
	onPrimaryContainer = Color(0xFFA9A3F9), // --brand-hover (dark)
	secondary = Color(0xFFE08A5F),
	onSecondary = Color(0xFF16151D),
	secondaryContainer = Color(0xFF3D2A1E),
	onSecondaryContainer = Color(0xFFF1EFE9),
	tertiary = Color(0xFF43D191),
	tertiaryContainer = Color(0xFF16382A),
	error = Color(0xFFF4796C),
	errorContainer = Color(0xFF3D1F1B),
	background = Color(0xFF16151D), // --bg (dark)
	onBackground = Color(0xFFF1EFE9), // --fg (dark)
	surface = Color(0xFF16151D),
	onSurface = Color(0xFFF1EFE9),
	surfaceVariant = Color(0xFF262530), // --surface-2 (dark)
	onSurfaceVariant = Color(0xFFA9A5B6), // --fg-muted (dark)
	outline = Color(0xFF423F4F), // --border-strong (dark)
	outlineVariant = Color(0xFF322F3D), // --border (dark)
)

/** The heart/streak color (web --heart), same role in both themes. */
val HeartLight = Color(0xFFD6456B)
val HeartDark = Color(0xFFFF6D92)

@Composable
fun ShikhiTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit,
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColors else LightColors,
		typography = ShikhiTypography,
		content = content,
	)
}
