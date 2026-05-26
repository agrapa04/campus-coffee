package de.seuhd.campuscoffee.tests.acceptance;

import de.seuhd.campuscoffee.api.dtos.PosDto;
import de.seuhd.campuscoffee.domain.model.enums.CampusType;
import de.seuhd.campuscoffee.domain.model.enums.PosType;
import io.cucumber.java.DataTableType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static de.seuhd.campuscoffee.tests.SystemTestUtils.Requests.posRequests;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for the POS Cucumber tests. The Spring context, container, and cleanup hooks
 * live in {@link CucumberSpringConfiguration}.
 */
public class CucumberPosSteps {

    private List<PosDto> createdPosList;
    private PosDto updatedPos;

    /**
     * Register a Cucumber DataTable type for PosDto.
     * @param row the DataTable row to map to a PosDto object
     * @return the mapped PosDto object
     */
    @DataTableType
    @SuppressWarnings("unused")
    public PosDto toPosDto(Map<String,String> row) {
        return PosDto.builder()
                .name(row.get("name"))
                .description(row.get("description"))
                .type(PosType.valueOf(row.get("type")))
                .campus(CampusType.valueOf(row.get("campus")))
                .street(row.get("street"))
                .houseNumber(row.get("houseNumber"))
                .postalCode(Integer.parseInt(row.get("postalCode")))
                .city(row.get("city"))
                .build();
    }

    // Given -----------------------------------------------------------------------

    @Given("an empty POS list")
    public void anEmptyPosList() {
        List<PosDto> retrievedPosList = posRequests.retrieveAll();
        assertThat(retrievedPosList).isEmpty();
    }

    @Given("a POS list with the following elements")
    public void aPosListWithTheFollowingElements(List<PosDto> posList) {
        List<PosDto> createdPosList = posRequests.create(posList);
        assertThat(createdPosList).hasSize(posList.size());
    }

    // When -----------------------------------------------------------------------

    @When("I insert POS with the following elements")
    public void insertPosWithTheFollowingValues(List<PosDto> posList) {
        createdPosList = posRequests.create(posList);
        assertThat(createdPosList).size().isEqualTo(posList.size());
    }

    @When("I update the description of the POS with name {string} to {string}")
    public void iUpdateTheDescriptionOfThePosWithNameTo(String name, String description) {
        PosDto posToUpdate = posRequests.retrieveByFilter("name", name);
        updatedPos = posToUpdate
                .toBuilder()
                .description(description)
                .build();
        posRequests.update(List.of(updatedPos)).getFirst();
    }

    // Then -----------------------------------------------------------------------

    @Then("the POS list should contain the same elements in the same order")
    public void thePosListShouldContainTheSameElementsInTheSameOrder() {
        List<PosDto> retrievedPosList = posRequests.retrieveAll();
        assertThat(retrievedPosList)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "createdAt", "updatedAt")
                .containsExactlyInAnyOrderElementsOf(createdPosList);
    }

    @Then("the updated POS should have the new description")
    public void theUpdatedPosShouldHaveTheNewDescription() {
        PosDto retrievedPos = posRequests.retrieveById(updatedPos.getId());
        assertThat(retrievedPos.description()).isEqualTo(updatedPos.description());
    }
}
