import { useTranslation } from 'react-i18next'
import { LegalPageHeader } from '../components/LegalPageHeader'
import './legal.css'

const CONTACT_EMAIL = 'rajuseu5@gmail.com'
const EFFECTIVE_DATE = '2026-07-10'

/**
 * Public privacy policy at /privacy (Play Store data-safety requirement, US-PS.1).
 * Directly linkable, no router — App.tsx renders this standalone based on pathname.
 */
export function PrivacyPolicyPage() {
  const { t } = useTranslation()

  return (
    <main className="legal">
      <LegalPageHeader current="privacy" />

      <article className="legal__doc">
        <h1>{t('legal.privacy.title')}</h1>
        <p className="legal__effective-date">
          {t('legal.privacy.effectiveDate', { date: EFFECTIVE_DATE })}
        </p>
        <p>{t('legal.privacy.intro')}</p>

        <section>
          <h2>{t('legal.privacy.sections.dataTitle')}</h2>
          <p>{t('legal.privacy.sections.dataBody')}</p>
        </section>

        <section>
          <h2>{t('legal.privacy.sections.useTitle')}</h2>
          <p>{t('legal.privacy.sections.useBody')}</p>
        </section>

        <section>
          <h2>{t('legal.privacy.sections.sharingTitle')}</h2>
          <p>{t('legal.privacy.sections.sharingBody')}</p>
        </section>

        <section>
          <h2>{t('legal.privacy.sections.securityTitle')}</h2>
          <p>{t('legal.privacy.sections.securityBody')}</p>
        </section>

        <section>
          <h2>{t('legal.privacy.sections.deletionTitle')}</h2>
          <p>{t('legal.privacy.sections.deletionBody')}</p>
          <p>
            <a href="/delete-account">{t('legal.deleteAccountLink')}</a>
          </p>
        </section>

        <section>
          <h2>{t('legal.privacy.sections.childrenTitle')}</h2>
          <p>{t('legal.privacy.sections.childrenBody')}</p>
        </section>

        <section>
          <h2>{t('legal.privacy.sections.changesTitle')}</h2>
          <p>{t('legal.privacy.sections.changesBody')}</p>
        </section>

        <p className="legal__contact">{t('legal.privacy.contact', { email: CONTACT_EMAIL })}</p>
      </article>
    </main>
  )
}
