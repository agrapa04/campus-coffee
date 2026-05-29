package de.seuhd.campuscoffee.api.openapi;

import de.seuhd.campuscoffee.api.controller.PosController;
import de.seuhd.campuscoffee.api.controller.ReviewController;
import de.seuhd.campuscoffee.api.controller.UserController;
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper;
import de.seuhd.campuscoffee.api.mapper.ReviewDtoMapper;
import de.seuhd.campuscoffee.api.mapper.UserDtoMapper;
import de.seuhd.campuscoffee.domain.ports.api.PosService;
import de.seuhd.campuscoffee.domain.ports.api.ReviewService;
import de.seuhd.campuscoffee.domain.ports.api.UserService;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

/**
 * Tests how {@link CrudOperationCustomizer} builds the OpenAPI responses for a controller method from its
 * {@code @CrudOperation} annotation and return type. It drives every annotated method on the real
 * controllers, discovered reflectively so no method names are hard-coded, and checks the summary, the
 * response codes, the error schema, and that the success schema matches the return type (an array for a
 * list, a reference for a single object, none for void).
 */
class CrudOperationCustomizerTest {

    private final CrudOperationCustomizer customizer = new CrudOperationCustomizer();

    /** Every {@code @CrudOperation}-annotated handler method on the real controllers. */
    static Stream<Arguments> crudOperationMethods() {
        return Stream.of(new PosController(mock(PosService.class), mock(PosDtoMapper.class)),
                        new UserController(mock(UserService.class), mock(UserDtoMapper.class)),
                        new ReviewController(mock(ReviewService.class), mock(ReviewDtoMapper.class)))
                .flatMap(controller -> Arrays.stream(controller.getClass().getDeclaredMethods())
                        // skip the synthetic bridge methods the generic CrudController overrides generate;
                        // they carry the annotation but a raw return type, which springdoc never passes
                        .filter(method -> method.isAnnotationPresent(CrudOperation.class)
                                && !method.isBridge() && !method.isSynthetic())
                        .map(method -> arguments(named(
                                controller.getClass().getSimpleName() + "." + method.getName(),
                                new HandlerMethod(controller, method)))));
    }

    @ParameterizedTest
    @MethodSource("crudOperationMethods")
    void buildsSummaryAndResponsesMatchingTheOperationAndReturnType(HandlerMethod handlerMethod) {
        CrudOperation crudOperation = handlerMethod.getMethodAnnotation(CrudOperation.class);
        var specs = crudOperation.operation().getResponseSpecifications();

        Operation operation = customizer.customize(new Operation(), handlerMethod);

        assertThat(operation.getSummary()).isNotBlank();

        ApiResponses responses = operation.getResponses();
        assertThat(responses.keySet()).containsExactlyInAnyOrderElementsOf(
                specs.stream().map(spec -> String.valueOf(spec.getHttpStatus().value())).toList());

        for (var spec : specs) {
            ApiResponse response = responses.get(String.valueOf(spec.getHttpStatus().value()));
            assertThat(response.getDescription()).isNotBlank();
            if (spec.isErrorResponse()) {
                assertThat(jsonSchema(response.getContent()).get$ref()).contains("ErrorResponse");
            } else {
                assertSuccessContentMatchesReturnType(handlerMethod, response.getContent());
            }
        }
    }

    @Test
    void leavesOperationsWithoutCrudAnnotationUnchanged() throws NoSuchMethodException {
        // Object#toString carries no @CrudOperation, so the operation must pass through untouched
        HandlerMethod plain = new HandlerMethod(
                new PosController(mock(PosService.class), mock(PosDtoMapper.class)),
                Object.class.getMethod("toString"));

        Operation operation = customizer.customize(new Operation(), plain);

        assertThat(operation.getSummary()).isNull();
        assertThat(operation.getResponses()).isNull();
    }

    /** A list return gives an array schema with item references, a single object a reference, void no body. */
    private void assertSuccessContentMatchesReturnType(HandlerMethod handlerMethod, Content content) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());
        if (returnType.getRawClass() == ResponseEntity.class) {
            returnType = returnType.getGeneric(0);
        }
        Class<?> rawType = returnType.getRawClass();

        if (rawType == Void.class || rawType == void.class) {
            assertThat(content).isNull();
        } else if (rawType == List.class) {
            Schema<?> schema = jsonSchema(content);
            assertThat(schema.getType()).isEqualTo("array");
            assertThat(schema.getItems().get$ref()).isNotBlank();
        } else {
            assertThat(jsonSchema(content).get$ref()).isNotBlank();
        }
    }

    private Schema<?> jsonSchema(Content content) {
        assertThat(content).isNotNull();
        return content.get("application/json").getSchema();
    }
}
