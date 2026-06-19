package de.seuhd.campuscoffee.data.configuration

import de.seuhd.campuscoffee.domain.ports.IdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/**
 * Selects the [IdGenerator] from the `campus-coffee.id.seed` property: a number gives a
 * [SeededUuidGenerator], and `random` (or a blank value) gives random UUIDs.
 */
@Configuration
class IdGeneratorConfiguration {
    @Bean
    fun idGenerator(
        @Value("\${campus-coffee.id.seed:42}") seed: String
    ): IdGenerator =
        if (seed.isBlank() || seed.equals("random", ignoreCase = true)) {
            IdGenerator { UUID.randomUUID() }
        } else {
            SeededUuidGenerator(seed.trim().toLong())
        }
}
