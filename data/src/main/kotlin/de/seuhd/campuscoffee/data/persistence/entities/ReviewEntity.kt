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
    @field:JoinColumn(name = "pos_id")
    var pos: PosEntity? = null

    @field:ManyToOne
    @field:JoinColumn(name = "author_id")
    var author: UserEntity? = null

    var review: String? = null

    @field:Column(name = "approval_count")
    var approvalCount: Int? = null

    @field:Column(name = "approved")
    var approved: Boolean? = null

    /** Optimistic-locking version; prevents a lost update when the same review is approved concurrently. */
    @field:Version
    @field:Column(name = "version")
    var version: Long? = null
}
