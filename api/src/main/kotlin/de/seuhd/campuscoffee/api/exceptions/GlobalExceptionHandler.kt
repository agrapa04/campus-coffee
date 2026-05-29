package de.seuhd.campuscoffee.api.exceptions

import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import java.time.LocalDateTime

/**
 * Global exception handler for all controllers, providing centralized handling and standardized
 * error responses.
 */
@ControllerAdvice
class GlobalExceptionHandler {
    /**
     * Unified handler for the mapped exceptions, returning the HTTP status configured for the
     * exception type and falling back to the generic handler for anything unmapped.
     */
    @ExceptionHandler(
        NotFoundException::class,
        DuplicationException::class,
        ConcurrentUpdateException::class,
        IllegalArgumentException::class,
        MissingFieldException::class,
        ValidationException::class
    )
    fun handleMappedException(
        exception: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val config =
            EXCEPTION_MAPPINGS[exception.javaClass]
                ?: return handleGenericException(exception, request)
        log.warn(config.logMessage, exception.message)
        return buildErrorResponse(exception, config.httpStatus, request, exception.message)
    }

    /**
     * Handles bean validation failures on request bodies, building the message from the field-level
     * binding errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        exception: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val message =
            exception.bindingResult.fieldErrors.joinToString("; ") {
                "${it.field} ${it.defaultMessage}"
            }
        log.warn("Domain validation failed: {}", message)
        return buildErrorResponse(exception, HttpStatus.BAD_REQUEST, request, message)
    }

    /** Fallback handler for unexpected exceptions, returning HTTP 500. */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        exception: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error occurred", exception)
        return buildErrorResponse(
            exception,
            HttpStatus.INTERNAL_SERVER_ERROR,
            request,
            "An unexpected error occurred."
        )
    }

    private fun buildErrorResponse(
        exception: Exception,
        status: HttpStatus,
        request: WebRequest,
        message: String?
    ): ResponseEntity<ErrorResponse> {
        val error =
            ErrorResponse(
                errorCode = exception.javaClass.simpleName,
                message = message,
                statusCode = status.value(),
                statusMessage = status.reasonPhrase,
                timestamp = LocalDateTime.now(),
                path = extractPath(request)
            )
        return ResponseEntity.status(status).body(error)
    }

    private fun extractPath(request: WebRequest): String =
        (request as? ServletWebRequest)?.request?.requestURI ?: "unknown"

    /**
     * Maps an exception type to the HTTP status to return and the log message template
     * (with `{}` as the placeholder for the exception message).
     */
    private data class ExceptionConfig(
        val httpStatus: HttpStatus,
        val logMessage: String
    )

    private companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

        private val EXCEPTION_MAPPINGS: Map<Class<out Exception>, ExceptionConfig> =
            mapOf(
                NotFoundException::class.java to ExceptionConfig(HttpStatus.NOT_FOUND, "Resource not found: {}"),
                DuplicationException::class.java to ExceptionConfig(HttpStatus.CONFLICT, "Duplicate resource: {}"),
                ConcurrentUpdateException::class.java to
                    ExceptionConfig(HttpStatus.CONFLICT, "Concurrent modification: {}"),
                IllegalArgumentException::class.java to ExceptionConfig(HttpStatus.BAD_REQUEST, "Bad request: {}"),
                MissingFieldException::class.java to ExceptionConfig(HttpStatus.BAD_REQUEST, "Bad request: {}"),
                ValidationException::class.java to
                    ExceptionConfig(HttpStatus.BAD_REQUEST, "Domain validation failed: {}")
            )
    }
}
