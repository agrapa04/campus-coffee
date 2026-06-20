package de.seuhd.campuscoffee.domain.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the approval process, in particular the minimum number of approvals a review needs.
 * The property is required and must be positive; binding fails at startup otherwise, so a
 * misconfiguration cannot surface later as a failing approval request.
 *
 * @property minCount the minimum number of approvals a review needs to be approved; required and at least 1.
 */
@ConfigurationProperties("campus-coffee.approval")
data class ApprovalProperties(
    val minCount: Int
) {
    init {
        require(minCount >= 1) { "campus-coffee.approval.min-count must be at least 1, but was $minCount." }
    }
}
