package de.seuhd.campuscoffee.data.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * Database entity for a review of a point-of-sale.
 */
@jakarta.persistence.Entity
@Table(name = "reviews")
class ReviewEntity : Entity() {
    @field:ManyToOne
    @field:JoinColumn(name = "pos_id", nullable = false)
    var pos: PosEntity? = null

    @field:ManyToOne
    @field:JoinColumn(name = "author_id", nullable = false)
    var author: UserEntity? = null

    var review: String? = null

    @field:Column(name = "approval_count")
    var approvalCount: Int? = null

    @field:Column(name = "approved")
    var approved: Boolean? = null

    // Optimistic locking version; the losing side of a concurrent update returns 409 instead of silently
    // overwriting. Defaults to 0 (not null) across these entities so a detached copy built by the mapper is
    // read as detached, not transient: a null version reads as transient and breaks a @ManyToOne to it, such
    // as a review's reference to its POS and author.
    @field:Version
    @field:Column(name = "version")
    var version: Long? = 0

    companion object {
        /** Name of the unique constraint on (pos_id, author_id), declared in the Flyway migration. */
        const val POS_AUTHOR_UNIQUE_CONSTRAINT = "uq_reviews_pos_author"
    }
}
