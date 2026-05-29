package de.seuhd.campuscoffee.data.integration

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import de.seuhd.campuscoffee.domain.tests.TestFixtures
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

/**
 * Verifies that the data service turns a database uniqueness violation into a domain
 * [DuplicationException] by mapping the violated named constraint to its domain field, and propagates
 * other integrity violations unchanged.
 */
class CrudDataServiceDuplicationTest : AbstractDataIntegrationTest() {
    @Autowired
    private lateinit var posDataService: PosDataService

    @Autowired
    private lateinit var userDataService: UserDataService

    @Test
    fun `upsert throws DuplicationException for a duplicate POS name`() {
        val pos = TestFixtures.getPosFixturesForInsertion().first()
        posDataService.upsert(pos)

        assertThatThrownBy { posDataService.upsert(pos) }.isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `upsert throws DuplicationException for a duplicate user email`() {
        val user = TestFixtures.getUserFixturesForInsertion().first()
        userDataService.upsert(user)
        // same email but a different login name, so the email uniqueness constraint is the one violated
        val sameEmail = user.copy(loginName = user.loginName + "_other")

        assertThatThrownBy { userDataService.upsert(sameEmail) }.isInstanceOf(DuplicationException::class.java)
    }

    @Test
    fun `upsert propagates a non-uniqueness violation instead of mapping it to DuplicationException`() {
        // an empty description violates a CHECK constraint, not a uniqueness constraint, so the data
        // service must propagate the original exception rather than wrap it as a DuplicationException
        val invalid = TestFixtures.getPosFixturesForInsertion().first().copy(description = "")

        assertThatThrownBy { posDataService.upsert(invalid) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .isNotInstanceOf(DuplicationException::class.java)
    }
}
