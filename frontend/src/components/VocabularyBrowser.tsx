import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type CefrLevel, fetchVocabulary, type VocabularyEntry } from '../api/vocabulary'
import { useAuth } from '../auth/useAuth'
import { SpeakButton } from './SpeakButton'

type LoadState = 'idle' | 'loading' | 'error'

const LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1']
// Page the rendered rows so a full band (hundreds of words) stays snappy; search narrows it.
const PAGE_SIZE = 40

export function VocabularyBrowser() {
  const { t } = useTranslation()
  const { user, getToken } = useAuth()
  const [level, setLevel] = useState<CefrLevel>('A1')
  const [entries, setEntries] = useState<VocabularyEntry[]>([])
  const [state, setState] = useState<LoadState>('idle')
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(1)

  useEffect(() => {
    if (!user || !open) return
    const token = getToken()
    if (!token) return

    let cancelled = false
    setState('loading')
    fetchVocabulary(token, level)
      .then((data) => {
        if (!cancelled) {
          setEntries(data)
          setState('idle')
        }
      })
      .catch(() => {
        if (!cancelled) setState('error')
      })
    return () => {
      cancelled = true
    }
  }, [user, getToken, level, open])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return entries
    return entries.filter(
      (e) => e.headword.toLowerCase().includes(q) || e.bnGloss.includes(query.trim()),
    )
  }, [entries, query])

  useEffect(() => {
    setPage(1)
  }, [level, query])

  if (!user) return null

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const start = (currentPage - 1) * PAGE_SIZE
  const shown = filtered.slice(start, start + PAGE_SIZE)

  return (
    <section className="vocab" aria-label={t('vocab.title')}>
      <div className="vocab__head">
        <h2>{t('vocab.title')}</h2>
        <button
          type="button"
          className="vocab__toggle"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
        >
          {open ? t('vocab.hide') : t('vocab.show')}
        </button>
      </div>

      {open && (
        <>
          <p className="vocab__subtitle">{t('vocab.subtitle')}</p>

          <div className="vocab__levels" role="tablist" aria-label={t('vocab.title')}>
            {LEVELS.map((lv) => (
              <button
                key={lv}
                type="button"
                role="tab"
                aria-selected={lv === level}
                className={`vocab__level${lv === level ? ' vocab__level--active' : ''}`}
                onClick={() => setLevel(lv)}
              >
                {lv}
              </button>
            ))}
          </div>

          <input
            className="vocab__search"
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('vocab.searchPlaceholder')}
            aria-label={t('vocab.searchPlaceholder')}
          />

          {state === 'loading' && <p>{t('vocab.loading')}</p>}
          {state === 'error' && <p className="auth__error">{t('vocab.error')}</p>}
          {state === 'idle' && entries.length === 0 && <p>{t('vocab.empty')}</p>}

          {state === 'idle' && entries.length > 0 && (
            <>
              <p className="vocab__count">
                {t('vocab.count', {
                  from: filtered.length === 0 ? 0 : start + 1,
                  to: Math.min(start + PAGE_SIZE, filtered.length),
                  total: filtered.length,
                })}
              </p>
              <ul className="vocab__list">
                {shown.map((e) => (
                  <li key={e.id} className="vocab__card">
                    <div className="vocab__term">
                      <span className="vocab__word">{e.headword}</span>
                      <SpeakButton
                        text={e.headword}
                        label={t('vocab.pronounce', { word: e.headword })}
                      />
                      <span className="vocab__pos">{e.partOfSpeech}</span>
                      {e.senseLabel && <span className="vocab__sense">({e.senseLabel})</span>}
                    </div>
                    <div className="vocab__gloss">{e.bnGloss}</div>
                    {e.exampleEn && (
                      <div className="vocab__example">
                        <span className="vocab__example-en">{e.exampleEn}</span>
                        {e.exampleBn && (
                          <span className="vocab__example-bn">{e.exampleBn}</span>
                        )}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
              {totalPages > 1 && (
                <nav className="vocab__pager" aria-label={t('vocab.pagerLabel')}>
                  <button
                    type="button"
                    className="vocab__pager-btn"
                    onClick={() => setPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage <= 1}
                  >
                    {t('vocab.prev')}
                  </button>
                  <span className="vocab__pager-status">
                    {t('vocab.page', { page: currentPage, pages: totalPages })}
                  </span>
                  <button
                    type="button"
                    className="vocab__pager-btn"
                    onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage >= totalPages}
                  >
                    {t('vocab.next')}
                  </button>
                </nav>
              )}
            </>
          )}
        </>
      )}
    </section>
  )
}
