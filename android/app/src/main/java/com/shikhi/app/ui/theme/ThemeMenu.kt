package com.shikhi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R
import com.shikhi.app.data.prefs.ThemeMode

@Composable
fun ThemeMenu(modifier: Modifier = Modifier, viewModel: ThemeViewModel = hiltViewModel()) {
	val mode by viewModel.mode.collectAsStateWithLifecycle()
	var expanded by remember { mutableStateOf(false) }
	// The glyph shows where a tap leads, not the current state — same convention as the web
	// header toggle (frontend/src/App.tsx): sun while dark is in effect, moon while light is.
	val icon = if (isSystemInDarkTheme()) R.drawable.ic_theme_light else R.drawable.ic_theme_dark

	// The Box is the popup's anchor. Left as a bare sibling of the IconButton, the menu is
	// positioned by whatever the parent's layout does with a zero-size child — which in a Box
	// parent throws it to the opposite corner of the screen.
	Box(modifier) {
		IconButton(onClick = { expanded = true }) {
			Icon(painterResource(icon), contentDescription = stringResource(R.string.theme_menu_open))
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			ThemeMenuItem(ThemeMode.SYSTEM, R.string.theme_system, mode) {
				viewModel.setMode(ThemeMode.SYSTEM)
				expanded = false
			}
			ThemeMenuItem(ThemeMode.LIGHT, R.string.theme_light, mode) {
				viewModel.setMode(ThemeMode.LIGHT)
				expanded = false
			}
			ThemeMenuItem(ThemeMode.DARK, R.string.theme_dark, mode) {
				viewModel.setMode(ThemeMode.DARK)
				expanded = false
			}
		}
	}
}

@Composable
private fun ThemeMenuItem(mode: ThemeMode, labelRes: Int, current: ThemeMode, onClick: () -> Unit) {
	val isSelected = mode == current
	DropdownMenuItem(
		text = { Text(stringResource(labelRes)) },
		trailingIcon = { if (isSelected) Icon(Icons.Filled.Check, contentDescription = null) },
		onClick = onClick,
		modifier = Modifier.semantics { selected = isSelected },
	)
}
