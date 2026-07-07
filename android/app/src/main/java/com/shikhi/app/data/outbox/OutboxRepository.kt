package com.shikhi.app.data.outbox

import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.SyncBatchRequest
import com.shikhi.app.data.api.dto.SyncEvent
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Event types accepted by POST /progress/sync (web outbox.ts). */
object OutboxEventType {
	const val COMPLETE_LESSON = "COMPLETE_LESSON"
	const val ANSWER = "ANSWER"
}

/**
 * Offline outbox with the exact semantics of the web client (frontend/src/api/outbox.ts):
 * enqueue mints the idempotency key once; flush replays everything in one idempotent
 * batch and clears only on success. Flush triggers: app foreground and post-lesson
 * (WorkManager scheduling arrives in MA4).
 */
@Singleton
class OutboxRepository @Inject constructor(
	private val dao: OutboxDao,
	private val progressApi: dagger.Lazy<ProgressApi>,
) {

	suspend fun enqueue(type: String, payload: JsonObject) {
		dao.insert(
			OutboxEventEntity(
				idempotencyKey = UUID.randomUUID().toString(),
				type = type,
				payloadJson = payload.toString(),
				createdAt = System.currentTimeMillis(),
			),
		)
	}

	/** True when the outbox ends up empty (nothing to send, or the batch was accepted). */
	suspend fun flush(): Boolean {
		val events = dao.all()
		if (events.isEmpty()) return true
		return try {
			progressApi.get().sync(
				SyncBatchRequest(
					events.map {
						SyncEvent(
							idempotencyKey = it.idempotencyKey,
							type = it.type,
							payload = Json.parseToJsonElement(it.payloadJson).jsonObject,
						)
					},
				),
			)
			dao.deleteByIds(events.map { it.id })
			true
		} catch (e: Exception) {
			false
		}
	}
}
