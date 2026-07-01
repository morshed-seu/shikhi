import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchHealth, type HealthStatus } from './api/health'
import './App.css'

type HealthState = HealthStatus | 'loading' | 'error'

function App() {
  const { t, i18n } = useTranslation()
  const [health, setHealth] = useState<HealthState>('loading')

  useEffect(() => {
    let cancelled = false
    fetchHealth()
      .then((h) => {
        if (!cancelled) setHealth(h)
      })
      .catch(() => {
        if (!cancelled) setHealth('error')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const toggleLanguage = () => {
    void i18n.changeLanguage(i18n.language === 'bn' ? 'en' : 'bn')
  }

  let statusText: string
  let badgeModifier: string
  if (health === 'loading') {
    statusText = t('status.loading')
    badgeModifier = 'app__badge--loading'
  } else if (health === 'error') {
    statusText = t('status.error')
    badgeModifier = 'app__badge--error'
  } else {
    statusText = t('status.ok', { status: health.status })
    badgeModifier = 'app__badge--ok'
  }

  return (
    <main className="app">
      <header className="app__header">
        <h1>{t('app.title')}</h1>
        <button
          type="button"
          className="app__lang"
          onClick={toggleLanguage}
          aria-label={t('app.toggleLanguage')}
        >
          {i18n.language === 'bn' ? 'English' : 'বাংলা'}
        </button>
      </header>
      <p className="app__tagline">{t('app.tagline')}</p>
      <section className="app__status" aria-live="polite">
        <span className={`app__badge ${badgeModifier}`}>{statusText}</span>
      </section>
    </main>
  )
}

export default App
