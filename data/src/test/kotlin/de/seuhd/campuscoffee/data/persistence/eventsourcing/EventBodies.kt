package de.seuhd.campuscoffee.data.persistence.eventsourcing

import java.util.UUID

/**
 * Builders for the event-body maps the event sourcing tests feed to the [ReadModelProjector], in the shape
 * [EventJsonMapper] produces (ids and timestamps as strings, POS and author flattened to ids for a review).
 */
internal object EventBodies {
    fun review(
        posId: UUID,
        authorId: UUID,
        id: UUID = UUID.randomUUID(),
        review: String = "A review long enough to pass.",
        createdAt: String = "2026-01-01T00:00:00",
        updatedAt: String = "2026-01-01T00:00:00",
        approvalCount: Int = 0,
        approved: Boolean = false
    ): Map<String, Any?> =
        mapOf(
            "id" to id.toString(),
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "posId" to posId.toString(),
            "authorId" to authorId.toString(),
            "review" to review,
            "approvalCount" to approvalCount,
            "approved" to approved
        )
}
