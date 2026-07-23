package com.shikhi.app.ui.util

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device word pronunciation (OF2, docs/93-offline-learning-design.md §3.5) — mirrors the
 * web frontend's Web Speech API wrapper (`frontend/src/lib/speech.ts`) exactly: feature-detect
 * before showing a "listen" control (some devices lack the en-US voice pack), cancel any
 * utterance already in flight before speaking a new one. No bundled/recorded audio, no
 * dependency on `ContentDatabase` or the (later) `PlaySource` abstraction — this only ever
 * speaks text already rendered to the learner.
 *
 * App-singleton, like the rest of `AppModule`'s providers: the underlying [TextToSpeech] engine
 * lives for the process. It is intentionally never [TextToSpeech.shutdown] — there's no natural
 * "done" moment for an app-scoped singleton short of process death, at which point the OS
 * reclaims the engine anyway. Wiring a shutdown hook (e.g. via `ProcessLifecycleOwner`) would
 * be over-engineering for a resource that's already cheap to leak for a process lifetime.
 *
 * `@Inject constructor` + `@Singleton` makes this Hilt-providable with no `AppModule`
 * boilerplate — the same idiom the rest of the app's singletons (`CachedContentRepository`,
 * `DashboardRepository`, `OutboxForegroundFlusher`, ...) already use.
 */
@Singleton
class Pronouncer @Inject constructor(@ApplicationContext context: Context) {

	private val _isAvailable = MutableStateFlow(false)

	/**
	 * False until [TextToSpeech.OnInitListener] reports [TextToSpeech.SUCCESS] *and* the
	 * engine reports it supports US English (`isLanguageAvailable(Locale.US)` isn't
	 * [TextToSpeech.LANG_MISSING_DATA]/[TextToSpeech.LANG_NOT_SUPPORTED]) — the Android analog
	 * of the web's `isSpeechSupported()`. Callers must hide the speaker control, not show a
	 * broken one, while this is false.
	 */
	val isAvailable: StateFlow<Boolean> = _isAvailable

	private var tts: TextToSpeech? = null

	init {
		tts = TextToSpeech(context) { status ->
			val engine = tts
			val supported = status == TextToSpeech.SUCCESS && engine != null && run {
				val availability = engine.isLanguageAvailable(Locale.US)
				availability != TextToSpeech.LANG_MISSING_DATA && availability != TextToSpeech.LANG_NOT_SUPPORTED
			}
			if (supported) engine?.setLanguage(Locale.US)
			_isAvailable.value = supported
		}
	}

	/** Speaks [text] as English. Cancels any utterance already in flight first. */
	fun speak(text: String) {
		if (!_isAvailable.value) return
		val engine = tts ?: return
		engine.stop()
		engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
	}
}
