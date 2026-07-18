import { isSpeechSupported, speakEnglish } from '../lib/speech'

interface Props {
  text: string
  label: string
  className?: string
}

/** Small speaker icon that reads `text` aloud (Web Speech API). Renders nothing when
 * the browser has no speech synthesis support. */
export function SpeakButton({ text, label, className }: Props) {
  if (!isSpeechSupported()) return null
  return (
    <button
      type="button"
      className={`speak-btn${className ? ` ${className}` : ''}`}
      onClick={() => speakEnglish(text)}
      aria-label={label}
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
        <path d="M4 9v6h4l5 5V4L8 9H4z" />
        <path d="M16.5 12a4.5 4.5 0 0 0-2.2-3.9l.9-1.6A6.5 6.5 0 0 1 18.5 12a6.5 6.5 0 0 1-3.3 5.5l-.9-1.6A4.5 4.5 0 0 0 16.5 12z" />
      </svg>
    </button>
  )
}
