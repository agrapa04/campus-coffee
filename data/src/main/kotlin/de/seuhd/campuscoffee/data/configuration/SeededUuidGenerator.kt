package de.seuhd.campuscoffee.data.configuration

import de.seuhd.campuscoffee.domain.ports.IdGenerator
import java.util.Random
import java.util.UUID

/**
 * Generates UUIDs from a fixed seed, so the same seed always produces the same sequence of UUIDs. [reset]
 * starts the sequence over. [java.util.Random] is thread-safe and produces the same sequence on every
 * platform.
 */
class SeededUuidGenerator(
    private val seed: Long
) : IdGenerator {
    @Volatile
    private var random = Random(seed)

    override fun newId(): UUID = UUID(random.nextLong(), random.nextLong())

    override fun reset() {
        random = Random(seed)
    }
}
