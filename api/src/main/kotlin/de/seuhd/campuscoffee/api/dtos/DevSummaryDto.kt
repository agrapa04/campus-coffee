package de.seuhd.campuscoffee.api.dtos

/**
 * DTO reporting the number of users, POS, and reviews currently stored (used only in the `dev` profile).
 */
data class DevSummaryDto(
    val users: Int,
    val pos: Int,
    val reviews: Int
)
