import { apiFetch } from './client'
import type { Stats } from './sessions'

// Offline outbox (M5, D2): progress events that couldn't reach the server are buffered in
// localStorage and replayed to POST /v1/progress/sync when connectivity returns. Every event
// carries an idempotency key, so a replay is applied at most once server-side (M4).

export interface OutboxEvent {
  idempotencyKey: string
  type: 'COMPLETE_LESSON' | 'ANSWER'
  payload: Record<string, unknown>
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

export function pendingCount(): number {
  return read().length
}

/**
 * Flush buffered events to the server, clearing them on success. Returns true when the outbox
 * ends up empty (nothing to send, or the send succeeded); false if the send failed and events
 * remain buffered for the next attempt.
 */
export async function flushOutbox(token: string): Promise<boolean> {
  const events = read()
  if (events.length === 0) return true
  try {
    await apiFetch<Stats>('/progress/sync', {
      method: 'POST',
      token,
      body: { events },
      retry: true,
    })
    write([])
    return true
  } catch {
    return false
  }
}
