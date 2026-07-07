package com.shikhi.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.shikhi.app.R

/**
 * Noto Sans Bengali everywhere (NFR-AN6): the bundled variable font covers Bengali and
 * Latin, matching the web's `--font-sans` stack. Material 3's default type scale is kept;
 * only the family is swapped.
 */
val NotoSansBengali = FontFamily(Font(R.font.noto_sans_bengali))

private val default = Typography()

val ShikhiTypography = Typography(
	displayLarge = default.displayLarge.copy(fontFamily = NotoSansBengali),
	displayMedium = default.displayMedium.copy(fontFamily = NotoSansBengali),
	displaySmall = default.displaySmall.copy(fontFamily = NotoSansBengali),
	headlineLarge = default.headlineLarge.copy(fontFamily = NotoSansBengali),
	headlineMedium = default.headlineMedium.copy(fontFamily = NotoSansBengali),
	headlineSmall = default.headlineSmall.copy(fontFamily = NotoSansBengali),
	titleLarge = default.titleLarge.copy(fontFamily = NotoSansBengali),
	titleMedium = default.titleMedium.copy(fontFamily = NotoSansBengali),
	titleSmall = default.titleSmall.copy(fontFamily = NotoSansBengali),
	bodyLarge = default.bodyLarge.copy(fontFamily = NotoSansBengali),
	bodyMedium = default.bodyMedium.copy(fontFamily = NotoSansBengali),
	bodySmall = default.bodySmall.copy(fontFamily = NotoSansBengali),
	labelLarge = default.labelLarge.copy(fontFamily = NotoSansBengali),
	labelMedium = default.labelMedium.copy(fontFamily = NotoSansBengali),
	labelSmall = default.labelSmall.copy(fontFamily = NotoSansBengali),
)
