package de.seuhd.campuscoffee.api.exceptions

import de.seuhd.campuscoffee.domain.exceptions.ConcurrentUpdateException
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.LocalDateTime

/**
 * Global exception handler for all controllers, producing a standardized [ErrorResponse] body.
 *
 * Extends [ResponseEntityExceptionHandler] so the standard Spring MVC exceptions (wrong HTTP method,
 * unsupported/unacceptable media type, missing parameter, unknown path, ...) are mapped to their
 * correct status codes instead of the generic 500 fallback; [handleExceptionInternal] renders them
 * as [ErrorResponse]. Domain exceptions are mapped explicitly below.
 */
@ControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    /**
     * Unified handler for the mapped domain exceptions, returning the HTTP status configured for the
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
        return ResponseEntity
            .status(config.httpStatus)
            .body(errorBody(exception, config.httpStatus, request, exception.message))
    }

    /** Fallback handler for unexpected exceptions, returning HTTP 500. */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        exception: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error occurred", exception)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody(exception, HttpStatus.INTERNAL_SERVER_ERROR, request, "An unexpected error occurred."))
    }

    /**
     * Renders bean-validation failures on a request body as an [ErrorResponse], building the message
     * from the field-level binding errors. Overrides the base handler to keep the field names in the
     * message.
     */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field} ${it.defaultMessage}" }
        log.warn("Domain validation failed: {}", message)
        val body: Any = errorBody(ex, status, request, message)
        return ResponseEntity.status(status).headers(headers).body(body)
    }

    /** Renders every exception handled by [ResponseEntityExceptionHandler] as a standard [ErrorResponse]. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        log.warn("{} -> {}", ex.javaClass.simpleName, statusCode)
        val responseBody: Any = errorBody(ex, statusCode, request, ex.message)
        return ResponseEntity.status(statusCode).headers(headers).body(responseBody)
    }

    private fun errorBody(
        exception: Exception,
        status: HttpStatusCode,
        request: WebRequest,
        message: String?
    ): ErrorResponse =
        ErrorResponse(
            errorCode = exception.javaClass.simpleName,
            message = message,
            statusCode = status.value(),
            statusMessage = HttpStatus.valueOf(status.value()).reasonPhrase,
            timestamp = LocalDateTime.now(),
            path = extractPath(request)
        )

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
