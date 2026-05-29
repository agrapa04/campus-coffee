package de.seuhd.campuscoffee.api.controller

import de.seuhd.campuscoffee.api.dtos.DevSummaryDto
import de.seuhd.campuscoffee.domain.ports.api.PosService
import de.seuhd.campuscoffee.domain.ports.api.ReviewService
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Controller for the data management endpoints, available only in the `dev` profile.
 */
@Tag(name = "Dev data", description = "Dev-only endpoints to clear, load, and inspect test data in the database.")
@Controller
@Profile("dev")
@RequestMapping("/dev")
class DevController(
    private val posService: PosService,
    private val userService: UserService,
    private val reviewService: ReviewService
) {
    @Operation(summary = "Report the current number of users, POS, and reviews.")
    @GetMapping("/data")
    fun count(): ResponseEntity<DevSummaryDto> =
        ResponseEntity.ok(
            DevSummaryDto(userService.getAll().size, posService.getAll().size, reviewService.getAll().size)
        )

    @Operation(summary = "Replace all data with the test fixtures (users, POS, reviews).")
    @PutMapping("/data")
    fun load(): ResponseEntity<DevSummaryDto> {
        clearAll()
        val users = TestFixtures.createUserFixtures(userService).size
        val pos = TestFixtures.createPosFixtures(posService).size
        val reviews = TestFixtures.createReviewFixtures(reviewService).size
        return ResponseEntity.ok(DevSummaryDto(users, pos, reviews))
    }

    @Operation(summary = "Clear all data (users, POS, reviews).")
    @DeleteMapping("/data")
    fun clear(): ResponseEntity<Void> {
        clearAll()
        return ResponseEntity.noContent().build()
    }

    /** Clears all data, deleting reviews first because each review references a POS and a user. */
    private fun clearAll() {
        reviewService.clear()
        posService.clear()
        userService.clear()
    }
}
