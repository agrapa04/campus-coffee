package de.seuhd.campuscoffee.tests.acceptance

import de.seuhd.campuscoffee.api.dtos.PosDto
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.enums.PosType
import de.seuhd.campuscoffee.tests.SystemTestUtils.posRequests
import io.cucumber.java.DataTableType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

/**
 * Step definitions for the POS Cucumber tests. The Spring context, container, and cleanup hooks live
 * in [CucumberSpringConfiguration].
 */
class CucumberPosSteps {
    private lateinit var createdPosList: List<PosDto>
    private lateinit var updatedPos: PosDto

    /**
     * Register a Cucumber DataTable type for [PosDto].
     * @param row the DataTable row to map to a [PosDto]
     * @return the mapped [PosDto]
     */
    @DataTableType
    @Suppress("unused")
    fun toPosDto(row: Map<String, String>): PosDto =
        PosDto(
            name = row["name"],
            description = row["description"],
            type = PosType.valueOf(row["type"]!!),
            campus = CampusType.valueOf(row["campus"]!!),
            street = row["street"],
            houseNumber = row["houseNumber"],
            postalCode = row["postalCode"]!!.toInt(),
            city = row["city"]
        )

    // Given -----------------------------------------------------------------------

    @Given("an empty POS list")
    fun anEmptyPosList() {
        assertThat(posRequests.retrieveAll()).isEmpty()
    }

    @Given("a POS list with the following elements")
    fun aPosListWithTheFollowingElements(posList: List<PosDto>) {
        assertThat(posRequests.create(posList)).hasSize(posList.size)
    }

    // When -----------------------------------------------------------------------

    @When("I insert POS with the following elements")
    fun insertPosWithTheFollowingValues(posList: List<PosDto>) {
        createdPosList = posRequests.create(posList)
        assertThat(createdPosList).hasSize(posList.size)
    }

    @When("I update the description of the POS with name {string} to {string}")
    fun iUpdateTheDescriptionOfThePosWithNameTo(
        name: String,
        description: String
    ) {
        val posToUpdate = posRequests.retrieveByFilter("name", name)
        updatedPos = posToUpdate.copy(description = description)
        posRequests.update(listOf(updatedPos))
    }

    // Then -----------------------------------------------------------------------

    @Then("the POS list should contain the same elements in the same order")
    fun thePosListShouldContainTheSameElementsInTheSameOrder() {
        assertThat(posRequests.retrieveAll())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "createdAt", "updatedAt")
            .containsExactlyInAnyOrderElementsOf(createdPosList)
    }

    @Then("the updated POS should have the new description")
    fun theUpdatedPosShouldHaveTheNewDescription() {
        val retrievedPos = posRequests.retrieveById(updatedPos.id!!)
        assertThat(retrievedPos.description).isEqualTo(updatedPos.description)
    }
}
