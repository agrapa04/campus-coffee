package de.seuhd.campuscoffee.api.controller

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
import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.CrudService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Users", description = "Operations related to user management.")
@Controller
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
    private val userDtoMapper: UserDtoMapper,
) : CrudController<User, UserDto, Long>() {

    override fun service(): CrudService<User, Long> = userService

    override fun mapper(): DtoMapper<User, UserDto> = userDtoMapper

    @Operation
    @CrudOperation(operation = GET_ALL, resource = USER)
    @GetMapping("")
    override fun getAll(): ResponseEntity<List<UserDto>> = super.getAll()

    @Operation
    @CrudOperation(operation = GET_BY_ID, resource = USER)
    @GetMapping("/{id}")
    override fun getById(
        @Parameter(description = "Unique identifier of the user to retrieve.", required = true)
        @PathVariable id: Long,
    ): ResponseEntity<UserDto> = super.getById(id)

    @Operation
    @CrudOperation(operation = CREATE, resource = USER)
    @PostMapping("")
    override fun create(
        @Parameter(description = "Data of the user to create.", required = true)
        @RequestBody @Valid userDto: UserDto,
    ): ResponseEntity<UserDto> = super.create(userDto)

    @Operation
    @CrudOperation(operation = UPDATE, resource = USER)
    @PutMapping("/{id}")
    override fun update(
        @Parameter(description = "Unique identifier of the user to update.", required = true)
        @PathVariable id: Long,
        @Parameter(description = "Data of the user to update.", required = true)
        @RequestBody @Valid userDto: UserDto,
    ): ResponseEntity<UserDto> = super.update(id, userDto)

    @Operation
    @CrudOperation(operation = DELETE, resource = USER)
    @DeleteMapping("/{id}")
    override fun delete(
        @Parameter(description = "Unique identifier of the user to delete.", required = true)
        @PathVariable id: Long,
    ): ResponseEntity<Void> = super.delete(id)

    @Operation
    @CrudOperation(operation = FILTER, resource = USER)
    @GetMapping("/filter")
    fun filter(
        @Parameter(description = "Login name of the user to retrieve.", required = true)
        @RequestParam("login_name") loginName: String,
    ): ResponseEntity<UserDto> =
        ResponseEntity.ok(userDtoMapper.fromDomain(userService.getByLoginName(loginName)))
}
