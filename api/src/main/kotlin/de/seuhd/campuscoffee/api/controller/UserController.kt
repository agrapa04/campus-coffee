package de.seuhd.campuscoffee.api.controller

import de.seuhd.campuscoffee.api.dtos.OnCreate
import de.seuhd.campuscoffee.api.dtos.UserDto
import de.seuhd.campuscoffee.api.mapper.DtoMapper
import de.seuhd.campuscoffee.api.mapper.UserDtoMapper
import de.seuhd.campuscoffee.api.openapi.CrudOperation
import de.seuhd.campuscoffee.api.openapi.Operation.CREATE
import de.seuhd.campuscoffee.api.openapi.Operation.DELETE
import de.seuhd.campuscoffee.api.openapi.Operation.FILTER
import de.seuhd.campuscoffee.api.openapi.Operation.GET_ALL
import de.seuhd.campuscoffee.api.openapi.Operation.GET_BY_ID
import de.seuhd.campuscoffee.api.openapi.Operation.UPDATE
import de.seuhd.campuscoffee.api.openapi.Resource.USER
import de.seuhd.campuscoffee.api.security.CurrentUserProvider
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.model.objects.persistedId
import de.seuhd.campuscoffee.domain.ports.api.CrudService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.groups.Default
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@Tag(name = "Users", description = "Operations related to user management.")
@Controller
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
    private val userDtoMapper: UserDtoMapper,
    private val currentUserProvider: CurrentUserProvider
) : CrudController<User, UserDto, UUID>() {
    override fun service(): CrudService<User, UUID> = userService

    override fun mapper(): DtoMapper<User, UserDto> = userDtoMapper

    @Operation
    @CrudOperation(operation = GET_ALL, resource = USER)
    @GetMapping("")
    override fun getAll(): ResponseEntity<List<UserDto>> = super.getAll()

    // TODO (Exercise 2): reading a user is self-or-admin. Resolve the caller via CurrentUserProvider and let
    //  the domain decide (a non-admin may read only their own record); the same applies to the filter below.
    @Operation
    @CrudOperation(operation = GET_BY_ID, resource = USER)
    @GetMapping("/{id}")
    override fun getById(
        @Parameter(description = "Unique identifier of the user to retrieve.", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<UserDto> =
        // user data is not public; the domain enforces that only the user themselves or an admin may read it
        ResponseEntity.ok(userDtoMapper.fromDomain(userService.getById(id, currentUserProvider.currentUser())))

    @Operation
    @CrudOperation(operation = CREATE, resource = USER)
    @PostMapping("")
    override fun create(
        @Parameter(
            description =
                "Data of the user to register. The account is always created as a plain USER; " +
                    "any roles in the body are ignored.",
            required = true
        )
        @RequestBody
        @Validated(Default::class, OnCreate::class) dto: UserDto
    ): ResponseEntity<UserDto> {
        require(dto.id == null) { "ID must not be set when creating a new resource." }
        // registration is open and always yields a plain USER (the domain forces the role set)
        val created = userDtoMapper.fromDomain(userService.register(userDtoMapper.toDomain(dto)))
        return ResponseEntity.created(getLocation(created.persistedId)).body(created)
    }

    @Operation
    @CrudOperation(operation = UPDATE, resource = USER)
    @PutMapping("/{id}")
    override fun update(
        @Parameter(description = "Unique identifier of the user to update.", required = true)
        @PathVariable id: UUID,
        @Parameter(
            description =
                "Data of the user to update. A user may edit only their own account (an admin " +
                    "may edit anyone); only an admin may change roles.",
            required = true
        )
        @RequestBody
        @Valid dto: UserDto
    ): ResponseEntity<UserDto> {
        require(id == dto.id) { "ID in path and body do not match." }
        // the domain enforces self-service (own account only, unless admin) and the role-change rule
        val updated = userService.update(userDtoMapper.toDomain(dto), currentUserProvider.currentUser())
        return ResponseEntity.ok(userDtoMapper.fromDomain(updated))
    }

    @Operation
    @CrudOperation(operation = DELETE, resource = USER)
    @DeleteMapping("/{id}")
    override fun delete(
        @Parameter(description = "Unique identifier of the user to delete.", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<Void> = super.delete(id)

    @Operation
    @CrudOperation(operation = FILTER, resource = USER)
    @GetMapping("/filter")
    fun filter(
        @Parameter(description = "Login name of the user to retrieve.", required = true)
        @RequestParam("login_name") loginName: String
    ): ResponseEntity<UserDto> =
        ResponseEntity.ok(
            userDtoMapper.fromDomain(userService.getByLoginName(loginName, currentUserProvider.currentUser()))
        )
}
