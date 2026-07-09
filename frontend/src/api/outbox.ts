import { apiFetch } from './client'
import type { PracticeBatchResult } from './practice'
import type { Stats } from './sessions'

// Offline outbox (M5, D2): progress events that couldn't reach the server are buffered in
// localStorage and replayed to POST /v1/progress/sync when connectivity returns. Every event
// carries an idempotency key, so a replay is applied at most once server-side (M4).
//
// E12 batch grading extends this: a PRACTICE_ANSWERS event carries a whole round's worth of
// buffered practice answers for one session, and is routed to
// POST /practice/sessions/{sessionId}/answers/batch instead of /progress/sync. Per-answer
// idempotency keys inside the batch make a replay safe.

export interface OutboxEvent {
  idempotencyKey: string
  type: 'COMPLETE_LESSON' | 'ANSWER' | 'PRACTICE_ANSWERS'
  payload: Record<string, unknown>
}

export interface PracticeAnswerItem {
  idempotencyKey: string
  exerciseId: string
  answer: Record<string, unknown>
}

/** Payload shape of a `PRACTICE_ANSWERS` event's `payload` field. */
export interface PracticeAnswersPayload {
  sessionId: string
  items: PracticeAnswerItem[]
}

const KEY = 'shikhi.outbox'

function read(): OutboxEvent[] {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as OutboxEvent[]) : []
  } catch {
    return []
  }
}

function write(events: OutboxEvent[]): void {
  localStorage.setItem(KEY, JSON.stringify(events))
}

export function enqueue(event: OutboxEvent): void {
  const events = read()
  events.push(event)
  write(events)
}

function isPracticeAnswersEvent(e: OutboxEvent): boolean {
  return e.type === 'PRACTICE_ANSWERS'
}

function practicePayload(e: OutboxEvent): PracticeAnswersPayload {
  return e.payload as unknown as PracticeAnswersPayload
}

/**
 * Buffer (or replace) the practice answers pending for a session — called after every
 * locally-graded answer, so a crash or tab-close mid-round still leaves the round durably
 * queued. Replaces any prior snapshot for the same session, so the eventual flush is ONE
 * request carrying every buffered answer.
 */
export function enqueuePracticeAnswers(sessionId: string, items: PracticeAnswerItem[]): void {
  const rest = read().filter((e) => !(isPracticeAnswersEvent(e) && practicePayload(e).sessionId === sessionId))
  rest.push({
    idempotencyKey: crypto.randomUUID(),
    type: 'PRACTICE_ANSWERS',
    payload: { sessionId, items } satisfies PracticeAnswersPayload,
  })
  write(rest)
}

export function pendingCount(): number {
  return read().length
}

async function postPracticeBatch(token: string, event: OutboxEvent): Promise<PracticeBatchResult | null> {
  const payload = practicePayload(event)
  try {
    return await apiFetch<PracticeBatchResult>(`/practice/sessions/${payload.sessionId}/answers/batch`, {
      method: 'POST',
      token,
      body: { answers: payload.items },
      retry: true,
    })
  } catch {
    return null
  }
}

/**
 * Flush buffered events to the server, clearing them on success. Existing event types go to
 * POST /progress/sync (batched together, as before); PRACTICE_ANSWERS events are dispatched
 * individually to POST /practice/sessions/{sessionId}/answers/batch. Returns true when the
 * outbox ends up empty (nothing to send, or every send succeeded); false if anything failed
 * and remains buffered for the next attempt.
 */
export async function flushOutbox(token: string): Promise<boolean> {
  const events = read()
  if (events.length === 0) return true

  const progressEvents = events.filter((e) => !isPracticeAnswersEvent(e))
  const practiceEvents = events.filter(isPracticeAnswersEvent)
  const remaining: OutboxEvent[] = []

  if (progressEvents.length > 0) {
    try {
      await apiFetch<Stats>('/progress/sync', {
        method: 'POST',
        token,
        body: { events: progressEvents },
        retry: true,
      })
    } catch {
      remaining.push(...progressEvents)
    }
  }

  for (const event of practiceEvents) {
    const result = await postPracticeBatch(token, event)
    if (result === null) remaining.push(event)
  }

  write(remaining)
  return remaining.length === 0
}

/**
 * Flush just the buffered practice-answers batch for one session, returning the server's
 * stats/verdicts so the caller can reconcile local state (hearts, etc). Used by
 * PracticePlayer on every exit path instead of the generic flushOutbox, because it needs the
 * response body — flushOutbox only reports success/failure. Leaves the event buffered (for
 * the generic flushOutbox to retry later, e.g. after a reload) on failure.
 */
export async function flushPracticeAnswers(token: string, sessionId: string): Promise<PracticeBatchResult | null> {
  const events = read()
  const event = events.find((e) => isPracticeAnswersEvent(e) && practicePayload(e).sessionId === sessionId)
  if (!event) return null
  const result = await postPracticeBatch(token, event)
  if (result) write(events.filter((e) => e !== event))
  return result
}
