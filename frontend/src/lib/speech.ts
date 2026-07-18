// Browser-native pronunciation for vocabulary words (Web Speech API). No backend/data
// changes: quality depends on the user's OS/browser voices, so callers must feature-detect
// via isSpeechSupported() before showing a "listen" control.

export function isSpeechSupported(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

/** Speaks `text` as English. Cancels any utterance already in flight first. */
export function speakEnglish(text: string): void {
  if (!isSpeechSupported()) return
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'en-US'
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utterance)
}
