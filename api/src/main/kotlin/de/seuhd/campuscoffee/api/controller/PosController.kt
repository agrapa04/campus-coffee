package de.seuhd.campuscoffee.api.controller

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.api.mapper.DtoMapper
import de.seuhd.campuscoffee.api.mapper.PosDtoMapper
import de.seuhd.campuscoffee.api.openapi.CrudOperation
import de.seuhd.campuscoffee.api.openapi.Operation.CREATE
import de.seuhd.campuscoffee.api.openapi.Operation.DELETE
import de.seuhd.campuscoffee.api.openapi.Operation.FILTER
import de.seuhd.campuscoffee.api.openapi.Operation.GET_ALL
import de.seuhd.campuscoffee.api.openapi.Operation.GET_BY_ID
import de.seuhd.campuscoffee.api.openapi.Operation.IMPORT
import de.seuhd.campuscoffee.api.openapi.Operation.UPDATE
import de.seuhd.campuscoffee.api.openapi.Resource.OSM_NODE
import de.seuhd.campuscoffee.api.openapi.Resource.POS
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.model.objects.persistedId
import de.seuhd.campuscoffee.domain.ports.api.CrudService
import de.seuhd.campuscoffee.domain.ports.api.PosService
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

/**
 * Controller for handling POS-related API requests.
 */
@Tag(name = "Points of Sale (POS)", description = "Operations for managing coffee points of sale.")
@Controller
@RequestMapping("/pos")
class PosController(
    private val posService: PosService,
    private val posDtoMapper: PosDtoMapper
) : CrudController<Pos, PosDto, Long>() {
    override fun service(): CrudService<Pos, Long> = posService

    override fun mapper(): DtoMapper<Pos, PosDto> = posDtoMapper

    @Operation
    @CrudOperation(operation = GET_ALL, resource = POS)
    @GetMapping("")
    override fun getAll(): ResponseEntity<List<PosDto>> = super.getAll()

    @Operation
    @CrudOperation(operation = GET_BY_ID, resource = POS)
    @GetMapping("/{id}")
    override fun getById(
        @Parameter(description = "Unique identifier of the POS to retrieve.", required = true)
        @PathVariable id: Long
    ): ResponseEntity<PosDto> = super.getById(id)

    @Operation
    @CrudOperation(operation = CREATE, resource = POS)
    @PostMapping("")
    override fun create(
        @Parameter(description = "Data of the POS to create.", required = true)
        @RequestBody
        @Valid dto: PosDto
    ): ResponseEntity<PosDto> = super.create(dto)

    @Operation
    @CrudOperation(operation = UPDATE, resource = POS)
    @PutMapping("/{id}")
    override fun update(
        @Parameter(description = "Unique identifier of the POS to update.", required = true)
        @PathVariable id: Long,
        @Parameter(description = "Data of the POS to update.", required = true)
        @RequestBody
        @Valid dto: PosDto
    ): ResponseEntity<PosDto> = super.update(id, dto)

    @Operation
    @CrudOperation(operation = DELETE, resource = POS)
    @DeleteMapping("/{id}")
    override fun delete(
        @Parameter(description = "Unique identifier of the POS to delete.", required = true)
        @PathVariable id: Long
    ): ResponseEntity<Void> = super.delete(id)

    @Operation
    @CrudOperation(operation = FILTER, resource = POS)
    @GetMapping("/filter")
    fun filter(
        @Parameter(description = "Name of the POS to retrieve.", required = true)
        @RequestParam("name") name: String
    ): ResponseEntity<PosDto> = ResponseEntity.ok(posDtoMapper.fromDomain(posService.getByName(name)))

    @Operation
    @CrudOperation(operation = IMPORT, resource = POS, externalResource = OSM_NODE)
    @PostMapping("/import/osm/{nodeId}")
    fun importFromOsm(
        @Parameter(description = "Unique identifier of the OpenStreetMap node to import.", required = true)
        @PathVariable nodeId: Long,
        @Parameter(description = "Campus type to assign to the imported POS.", required = true)
        @RequestParam("campus_type") campusType: CampusType
    ): ResponseEntity<PosDto> {
        val createdPos = posDtoMapper.fromDomain(posService.importFromOsmNode(nodeId, campusType))
        return ResponseEntity.created(getLocation(createdPos.persistedId)).body(createdPos)
    }
}
