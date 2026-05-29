package de.seuhd.campuscoffee.domain.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the approval process, in particular the minimum number of approvals a review needs.
 */
@ConfigurationProperties("campus-coffee.approval")
data class ApprovalConfiguration(val minCount: Int?) {
    // temporary bridge so the still-Java tests keep compiling; removed once the tests are Kotlin
    fun minCount() = minCount
}
