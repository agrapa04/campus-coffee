package de.seuhd.campuscoffee.domain.model.objects

import java.time.LocalDateTime

/**
 * Immutable review domain model. A review is approved once it reaches a configurable number of
 * approvals; [approvalCount] and [approved] are maintained by the domain module.
 */
data class Review(
    override val id: Long? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val pos: Pos,
    val author: User,
    val review: String,
    val approvalCount: Int,
    val approved: Boolean,
) : DomainModel<Long> {

    // --- temporary bridges so the still-Java tests keep compiling; removed once the tests are Kotlin ---
    fun id() = id
    fun createdAt() = createdAt
    fun updatedAt() = updatedAt
    fun pos() = pos
    fun author() = author
    fun review() = review
    fun approvalCount() = approvalCount
    fun approved() = approved

    fun toBuilder() = Builder()
        .id(id).createdAt(createdAt).updatedAt(updatedAt)
        .pos(pos).author(author).review(review).approvalCount(approvalCount).approved(approved)

    class Builder {
        private var id: Long? = null
        private var createdAt: LocalDateTime? = null
        private var updatedAt: LocalDateTime? = null
        private var pos: Pos? = null
        private var author: User? = null
        private var review: String? = null
        private var approvalCount: Int? = null
        private var approved: Boolean? = null

        fun id(v: Long?) = apply { id = v }
        fun createdAt(v: LocalDateTime?) = apply { createdAt = v }
        fun updatedAt(v: LocalDateTime?) = apply { updatedAt = v }
        fun pos(v: Pos) = apply { pos = v }
        fun author(v: User) = apply { author = v }
        fun review(v: String) = apply { review = v }
        fun approvalCount(v: Int) = apply { approvalCount = v }
        fun approved(v: Boolean) = apply { approved = v }

        fun build() = Review(id, createdAt, updatedAt, pos!!, author!!, review!!, approvalCount!!, approved!!)
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
