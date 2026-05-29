package de.seuhd.campuscoffee.api.openapi

import de.seuhd.campuscoffee.api.exceptions.ErrorResponse
import de.seuhd.campuscoffee.api.openapi.Resource.NONE
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.core.ResolvableType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import java.util.Optional

/**
 * Populates OpenAPI operations from the [CrudOperation] annotation on the handler method: sets the
 * summary and the responses (status codes, descriptions, and success/error schemas).
 */
@Component
class CrudOperationCustomizer : OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val crudOperation = handlerMethod.getMethodAnnotation(CrudOperation::class.java)
        if (crudOperation != null) {
            val params = Parameters(
                crudOperation.operation,
                crudOperation.resource,
                Optional.ofNullable(crudOperation.externalResource.takeIf { it != NONE }),
            )
            operation.summary = crudOperation.operation.summaryTemplate(params)
            operation.responses = createResponses(params, handlerMethod)
        }
        return operation
    }

    /**
     * Creates the API responses from the operation's response specifications, attaching the
     * ErrorResponse schema for error responses and the return-type schema for success responses.
     */
    fun createResponses(params: Parameters, handlerMethod: HandlerMethod): ApiResponses {
        val responses = ApiResponses()
        for (spec in params.operation.responseSpecifications) {
            val response = ApiResponse().description(formatDescription(spec, params))
            response.content(
                if (spec.isErrorResponse) createErrorResponseContent() else createSuccessResponseContent(handlerMethod),
            )
            responses.addApiResponse(spec.httpStatus.value().toString(), response)
        }
        return responses
    }

    /**
     * Formats the description template with the regular or external resource name.
     */
    private fun formatDescription(spec: CrudResponseSpecification, params: Parameters): String {
        val substitution = if (spec.isExternalResource && params.externalResourceName.isPresent) {
            params.externalResourceName.get()
        } else {
            params.resourceName
        }
        return String.format(spec.descriptionTemplate, substitution)
    }

    private fun createErrorResponseContent(): Content {
        val errorSchema = Schema<Any>().`$ref`("#/components/schemas/" + ErrorResponse::class.java.simpleName)
        return Content().addMediaType("application/json", MediaType().schema(errorSchema))
    }

    /**
     * Builds the success-response content from the handler's return type: an array schema for a list,
     * a reference for a single object, or null for void.
     */
    private fun createSuccessResponseContent(handlerMethod: HandlerMethod): Content? {
        var returnType = ResolvableType.forMethodReturnType(handlerMethod.method)
        if (returnType.rawClass == ResponseEntity::class.java) {
            returnType = returnType.getGeneric(0)
        }
        val responseClass = returnType.rawClass!!

        if (responseClass == Void::class.java || responseClass == Void.TYPE) {
            return null
        }

        val schema: Schema<*> = if (responseClass == List::class.java) {
            val itemClass = returnType.getGeneric(0).rawClass!!
            Schema<Any>().apply {
                type = "array"
                items = Schema<Any>().`$ref`("#/components/schemas/" + itemClass.simpleName)
            }
        } else {
            Schema<Any>().`$ref`("#/components/schemas/" + responseClass.simpleName)
        }
        return Content().addMediaType("application/json", MediaType().schema(schema))
    }
}
