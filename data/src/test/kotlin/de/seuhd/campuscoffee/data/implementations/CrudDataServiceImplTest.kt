package de.seuhd.campuscoffee.data.implementations

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException

/**
 * Unit tests for [CrudDataServiceImpl.constraintNameOf], which reads the violated constraint name from
 * the data-integrity violation's Hibernate cause rather than matching on database-specific message text.
 */
class CrudDataServiceImplTest {
    @Test
    fun readsConstraintNameFromHibernateCause() {
        val reportedName = "some_unique_constraint"
        val hibernateViolation = mock<ConstraintViolationException>()
        whenever(hibernateViolation.constraintName).thenReturn(reportedName)
        val exception = DataIntegrityViolationException("could not execute statement", hibernateViolation)

        assertThat(CrudDataServiceImpl.constraintNameOf(exception)).isEqualTo(reportedName)
    }

    @Test
    fun returnsNullWhenNoHibernateConstraintViolationInChain() {
        val exception =
            DataIntegrityViolationException(
                "could not execute statement",
                RuntimeException("some unrelated database error")
            )

        assertThat(CrudDataServiceImpl.constraintNameOf(exception)).isNull()
    }
}
