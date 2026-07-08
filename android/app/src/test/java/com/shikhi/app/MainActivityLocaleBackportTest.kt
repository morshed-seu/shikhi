package com.shikhi.app

import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tripwire for the E13 per-app language switch. On API < 33,
 * `AppCompatDelegate.setApplicationLocales` only STORES the chosen locale — it is APPLIED
 * through `AppCompatActivity.attachBaseContext` (and the automatic recreate of tracked
 * AppCompatActivities). If MainActivity is ever "simplified" back to a plain
 * ComponentActivity, the profile locale switch silently does nothing, permanently, on the
 * PRD's reference devices (Android 9–13, mostly pre-33). This test makes that mistake loud.
 */
class MainActivityLocaleBackportTest {

	@Test
	fun `MainActivity must stay an AppCompatActivity or the pre-33 locale backport dies silently`() {
		assertTrue(
			"MainActivity must extend AppCompatActivity: setApplicationLocales only applies " +
				"locales via AppCompatActivity.attachBaseContext on API < 33",
			AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java),
		)
	}
}
