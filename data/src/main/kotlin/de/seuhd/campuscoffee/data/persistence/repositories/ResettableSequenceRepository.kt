package de.seuhd.campuscoffee.data.persistence.repositories

import jakarta.transaction.Transactional

/**
 * Repositories that support resetting their ID sequence. Primarily used in tests to ensure
 * consistent, predictable IDs.
 */
interface ResettableSequenceRepository {
    /**
     * Resets the database sequence for this entity's ID generation. The concrete implementation
     * is provided by the base repository class.
     */
    @Transactional
    fun resetSequence()
}
