package com.shikhi.app.ui.util

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

/**
 * Gate OF2: [Pronouncer]'s availability feature-detection and speak() behavior, using
 * Robolectric's `ShadowTextToSpeech` — this project has no androidTest source set (see
 * `ContentSeedImporterTest`'s own note), and a real device/emulator is the only way to verify
 * actual audio output, but the *logic* under test here (the SUCCESS + isLanguageAvailable gate,
 * the no-op-when-unavailable guard, QUEUE_FLUSH) is fully exercised by the shadow.
 *
 * One thing this shadow genuinely can't verify: that `stop()` runs strictly *before* `speak()`
 * on each call. `ShadowTextToSpeech.speak(...)` resets its own internal `stopped` flag to
 * `false` at the top of its body (mirroring the real engine accepting a new utterance), so by
 * the time a test can observe shadow state after `Pronouncer.speak()` returns, that signal is
 * already gone — this is a limitation of the fake, not something worth working around with a
 * bespoke `TextToSpeech` spy for one ordering assertion. [Pronouncer.speak]'s source itself
 * calls `engine.stop()` immediately before `engine.speak(...)`, matching the web's
 * `window.speechSynthesis.cancel()`-then-`speak()` order by inspection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PronouncerTest {

	@After
	fun tearDown() {
		ShadowTextToSpeech.reset()
	}

	private fun simulateInit(status: Int) {
		val instance = ShadowTextToSpeech.getLastTextToSpeechInstance()
		shadowOf(instance).onInitListener.onInit(status)
	}

	@Test
	fun `isAvailable becomes true once init succeeds and en-US is supported`() {
		ShadowTextToSpeech.addLanguageAvailability(Locale.US)
		val pronouncer = Pronouncer(ApplicationProvider.getApplicationContext<Context>())

		simulateInit(TextToSpeech.SUCCESS)

		assertTrue(pronouncer.isAvailable.value)
	}

	@Test
	fun `isAvailable stays false when the engine lacks the en-US voice pack`() {
		// No addLanguageAvailability(Locale.US) call: isLanguageAvailable returns LANG_NOT_SUPPORTED.
		val pronouncer = Pronouncer(ApplicationProvider.getApplicationContext<Context>())

		simulateInit(TextToSpeech.SUCCESS)

		assertFalse(pronouncer.isAvailable.value)
	}

	@Test
	fun `isAvailable stays false when OnInitListener reports failure`() {
		ShadowTextToSpeech.addLanguageAvailability(Locale.US)
		val pronouncer = Pronouncer(ApplicationProvider.getApplicationContext<Context>())

		simulateInit(TextToSpeech.ERROR)

		assertFalse(pronouncer.isAvailable.value)
	}

	@Test
	fun `speak flush-queues the text once available`() {
		ShadowTextToSpeech.addLanguageAvailability(Locale.US)
		val pronouncer = Pronouncer(ApplicationProvider.getApplicationContext<Context>())
		simulateInit(TextToSpeech.SUCCESS)

		pronouncer.speak("apple")

		val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
		assertEquals("apple", shadow.lastSpokenText)
		assertEquals(TextToSpeech.QUEUE_FLUSH, shadow.queueMode)
	}

	@Test
	fun `speak is a no-op while unavailable`() {
		// Never registers a supported locale, so isAvailable never flips true.
		val pronouncer = Pronouncer(ApplicationProvider.getApplicationContext<Context>())
		simulateInit(TextToSpeech.SUCCESS)

		pronouncer.speak("apple")

		val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
		assertNull(shadow.lastSpokenText)
	}
}
