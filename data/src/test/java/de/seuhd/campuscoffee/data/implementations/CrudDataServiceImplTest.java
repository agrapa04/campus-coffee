package de.seuhd.campuscoffee.data.implementations;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CrudDataServiceImpl#isConstraintViolation}, which decides whether a database
 * integrity violation refers to a named constraint. The constraint name can surface either in the
 * exception's own message or only in its root cause, so both detection paths are driven directly here;
 * a black-box test that only triggers a real violation cannot tell the two paths apart.
 */
class CrudDataServiceImplTest {

    private static final String CONSTRAINT = "uq_pos_name";

    @Test
    void detectsConstraintNamedInTheExceptionMessage() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate key value violates unique constraint " + CONSTRAINT);

        assertThat(CrudDataServiceImpl.isConstraintViolation(exception, CONSTRAINT)).isTrue();
    }

    @Test
    void detectsConstraintNamedOnlyInTheRootCause() {
        // the top-level message does not contain the constraint name; only the deepest cause does, so this
        // exercises the root-cause branch rather than the message branch
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("SQL error", new RuntimeException("violates constraint " + CONSTRAINT)));

        assertThat(exception.getMessage()).doesNotContain(CONSTRAINT); // guard: the message branch must miss
        assertThat(CrudDataServiceImpl.isConstraintViolation(exception, CONSTRAINT)).isTrue();
    }

    @Test
    void returnsFalseWhenNeitherMessageNorRootCauseNamesTheConstraint() {
        DataIntegrityViolationException withUnrelatedCause = new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("some unrelated database error"));

        assertThat(CrudDataServiceImpl.isConstraintViolation(withUnrelatedCause, CONSTRAINT)).isFalse();
    }

    @Test
    void returnsFalseWhenThereIsNoCauseAndTheMessageDoesNotMatch() {
        DataIntegrityViolationException withoutCause = new DataIntegrityViolationException("generic failure");

        assertThat(CrudDataServiceImpl.isConstraintViolation(withoutCause, CONSTRAINT)).isFalse();
    }
}
