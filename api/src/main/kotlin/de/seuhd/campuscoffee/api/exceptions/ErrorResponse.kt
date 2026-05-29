package de.seuhd.campuscoffee.api.exceptions

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

/**
 * Standardized error response for all API exceptions.
 *
 * @param errorCode     machine-readable code based on the exception class name (e.g. NotFoundException)
 * @param message       human-readable error message; null is allowed and omitted from the JSON
 * @param statusCode    HTTP status code (e.g. 400, 404, 500)
 * @param statusMessage HTTP status message (e.g. "Bad Request", "Not Found")
 * @param timestamp     when the error occurred
 * @param path          request path that caused the error
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // excludes null fields from JSON
data class ErrorResponse(
    val errorCode: String,
    val message: String?,
    val statusCode: Int,
    val statusMessage: String? = null,
    val timestamp: LocalDateTime? = null,
    val path: String? = null,
) {
    // --- temporary bridges so the still-Java GlobalExceptionHandler and tests keep compiling ---
    fun errorCode() = errorCode
    fun message() = message
    fun statusCode() = statusCode
    fun statusMessage() = statusMessage
    fun timestamp() = timestamp
    fun path() = path

    class Builder {
        private var errorCode: String? = null
        private var message: String? = null
        private var statusCode: Int? = null
        private var statusMessage: String? = null
        private var timestamp: LocalDateTime? = null
        private var path: String? = null

        fun errorCode(v: String?) = apply { errorCode = v }
        fun message(v: String?) = apply { message = v }
        fun statusCode(v: Int?) = apply { statusCode = v }
        fun statusMessage(v: String?) = apply { statusMessage = v }
        fun timestamp(v: LocalDateTime?) = apply { timestamp = v }
        fun path(v: String?) = apply { path = v }

        fun build() = ErrorResponse(errorCode!!, message, statusCode!!, statusMessage, timestamp, path)
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}
