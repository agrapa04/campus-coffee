package de.seuhd.campuscoffee.api.controller

import de.seuhd.campuscoffee.api.dtos.ReviewDto
import de.seuhd.campuscoffee.api.mapper.DtoMapper
import de.seuhd.campuscoffee.api.mapper.ReviewDtoMapper
import de.seuhd.campuscoffee.api.openapi.CrudOperation
import de.seuhd.campuscoffee.api.openapi.Operation.CREATE
import de.seuhd.campuscoffee.api.openapi.Operation.DELETE
import de.seuhd.campuscoffee.api.openapi.Operation.FILTER
import de.seuhd.campuscoffee.api.openapi.Operation.GET_ALL
import de.seuhd.campuscoffee.api.openapi.Operation.GET_BY_ID
import de.seuhd.campuscoffee.api.openapi.Operation.UPDATE
import de.seuhd.campuscoffee.api.openapi.Resource.REVIEW
import de.seuhd.campuscoffee.api.security.CurrentUserProvider
import de.seuhd.campuscoffee.domain.model.objects.Review
import de.seuhd.campuscoffee.domain.model.objects.persistedId
import de.seuhd.campuscoffee.domain.ports.api.CrudService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
import java.util.UUID

/**
 * Controller for handling reviews for POS, authored by users.
 *
 * Unlike the generic CRUD controllers, the review write operations take the acting user from the
 * authenticated principal via [CurrentUserProvider] and pass it into the domain, where ownership and
 * roles combine (the author or a moderator may edit or delete) and the self-approval ban are decided.
 */
@Tag(name = "Reviews", description = "Operations for managing reviews for points of sale.")
@Controller
@RequestMapping("/reviews")
class ReviewController(
    private val reviewService: ReviewService,
    private val reviewDtoMapper: ReviewDtoMapper,
    private val currentUserProvider: CurrentUserProvider
) : CrudController<Review, ReviewDto, UUID>() {
    override fun service(): CrudService<Review, UUID> = reviewService

    override fun mapper(): DtoMapper<Review, ReviewDto> = reviewDtoMapper

    @Operation
    @CrudOperation(operation = GET_ALL, resource = REVIEW)
    @GetMapping("")
    override fun getAll(): ResponseEntity<List<ReviewDto>> = super.getAll()

    @Operation
    @CrudOperation(operation = GET_BY_ID, resource = REVIEW)
    @GetMapping("/{id}")
    override fun getById(
        @Parameter(description = "Unique identifier of the review to retrieve.", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<ReviewDto> = super.getById(id)

    // TODO (Exercise 2): the author of a created review must be the authenticated user (resolve it via
    //  CurrentUserProvider), never the request body; a body that carries an authorId must be rejected.
    @Operation
    @CrudOperation(operation = CREATE, resource = REVIEW)
    @PostMapping("")
    override fun create(
        @Parameter(description = "Data of the review to create. The author is the authenticated user.", required = true)
        @RequestBody
        @Valid dto: ReviewDto
    ): ResponseEntity<ReviewDto> {
        // the server assigns the id and takes the author from the authenticated user; a body carrying
        // either is rejected (400), so a client cannot post a review as someone else or pick an id
        require(dto.id == null) { "ID must not be set when creating a new resource." }
        require(dto.authorId == null) { "Author must not be set; the author is the authenticated user." }
        val actingUser = currentUserProvider.currentUser()
        val created =
            reviewDtoMapper.fromDomain(
                reviewService.create(reviewDtoMapper.toDomain(dto, actingUser), actingUser)
            )
        return ResponseEntity.created(getLocation(created.persistedId)).body(created)
    }

    @Operation
    @CrudOperation(operation = UPDATE, resource = REVIEW)
    @PutMapping("/{id}")
    override fun update(
        @Parameter(description = "Unique identifier of the review to update.", required = true)
        @PathVariable id: UUID,
        @Parameter(description = "Data of the review to update.", required = true)
        @RequestBody
        @Valid dto: ReviewDto
    ): ResponseEntity<ReviewDto> {
        require(id == dto.id) { "ID in path and body do not match." }
        val actingUser = currentUserProvider.currentUser()
        // the author cannot change on update; the acting user fills the mapper's author slot, but the
        // domain pins the original author and rejects the update with 403 unless the caller is the
        // author or a moderator
        val updated = reviewService.update(reviewDtoMapper.toDomain(dto, actingUser), actingUser)
        return ResponseEntity.ok(reviewDtoMapper.fromDomain(updated))
    }

    @Operation
    @CrudOperation(operation = DELETE, resource = REVIEW)
    @DeleteMapping("/{id}")
    override fun delete(
        @Parameter(description = "Unique identifier of the review to delete.", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        reviewService.delete(id, currentUserProvider.currentUser())
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves the reviews of a POS filtered by approval status.
     *
     * @param posId    the POS to retrieve reviews for
     * @param approved the approval status of the reviews to retrieve
     */
    @Operation
    @CrudOperation(operation = FILTER, resource = REVIEW)
    @GetMapping("/filter")
    fun filter(
        @Parameter(description = "Unique identifier of the POS to retrieve approved reviews for.", required = true)
        @RequestParam("pos_id") posId: UUID,
        @Parameter(description = "The approval status of the reviews to retrieve.", required = true)
        @RequestParam("approved") approved: Boolean
    ): ResponseEntity<List<ReviewDto>> =
        ResponseEntity.ok(reviewService.filter(posId, approved).map { reviewDtoMapper.fromDomain(it) })

    /**
     * Approves a review on behalf of the authenticated user and returns its recomputed approval state.
     *
     * @param id the review to approve
     */
    @Operation(summary = "Approve a review by ID. The approver is the authenticated user (never the author).")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "The review with its recomputed approval state."),
            ApiResponse(responseCode = "400", description = "The authenticated user is the review's own author."),
            ApiResponse(responseCode = "401", description = "Authentication is required."),
            ApiResponse(responseCode = "404", description = "No review with the provided ID could be found."),
            ApiResponse(responseCode = "409", description = "The authenticated user already approved this review.")
        ]
    )
    @PutMapping("/{id}/approve")
    fun approve(
        @Parameter(description = "Unique identifier of the review to approve.", required = true)
        @PathVariable id: UUID
    ): ResponseEntity<ReviewDto> =
        ResponseEntity.ok(
            reviewDtoMapper.fromDomain(reviewService.approve(id, currentUserProvider.currentUser()))
        )
}
