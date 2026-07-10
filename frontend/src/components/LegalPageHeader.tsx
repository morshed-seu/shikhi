import { useTranslation } from 'react-i18next'
import { changeLocale } from '../i18n'

/**
 * Shared chrome for the standalone /privacy and /delete-account pages (Play Store
 * compliance — US-PS.1/PS.2): app title, the same language toggle as the main app,
 * a link back to the app root, and a cross-link to the other compliance page.
 */
export function LegalPageHeader({ current }: { current: 'privacy' | 'delete-account' }) {
  const { t, i18n } = useTranslation()

  const toggleLanguage = () => {
    changeLocale(i18n.language === 'bn' ? 'en' : 'bn')
  }

  return (
    <header className="legal__header">
      <div className="legal__header-row">
        <a className="legal__brand" href="/">
          {t('app.title')}
        </a>
        <button
          type="button"
          className="app__lang"
          onClick={toggleLanguage}
          aria-label={t('app.toggleLanguage')}
        >
          {i18n.language === 'bn' ? 'English' : 'বাংলা'}
        </button>
      </div>
      <nav className="legal__nav" aria-label={t('legal.navLabel')}>
        <a href="/">{t('legal.backToApp')}</a>
        {current !== 'privacy' && <a href="/privacy">{t('legal.privacyLink')}</a>}
        {current !== 'delete-account' && <a href="/delete-account">{t('legal.deleteAccountLink')}</a>}
      </nav>
    </header>
  )
}
