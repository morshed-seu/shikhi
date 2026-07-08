package com.shikhi.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.shikhi.app.data.api.dto.Bilingual

/** Picks the Bengali or English side of a bilingual string by the current UI locale. */
@Composable
fun Bilingual.localized(): String {
	val language = LocalConfiguration.current.locales[0]?.language ?: "bn"
	return if (language == "en") en else bn
}
