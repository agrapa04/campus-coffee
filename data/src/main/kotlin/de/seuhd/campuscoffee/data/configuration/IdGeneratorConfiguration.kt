package de.seuhd.campuscoffee.data.configuration

import de.seuhd.campuscoffee.domain.ports.IdGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.util.UUID

/**
 * Builds the [IdGenerator]s from the configured seeds. A numeric seed gives a deterministic
 * [SeededUuidGenerator]; `random` (or a blank value) gives random UUIDs.
 *
 * There are two generators with independent seeds. The `@Primary` [entityIdGenerator] (the one every other
 * component injects) assigns the entity ids; [eventIdGenerator] assigns the event log's ids in
 * event-sourcing mode. They use separate seeds so the two id sequences do not coincide and the entity ids
 * do not depend on whether event sourcing is enabled.
 */
@Configuration
class IdGeneratorConfiguration {
    @Bean
    @Primary
    fun entityIdGenerator(properties: IdProperties): IdGenerator = generatorFor(properties.entitySeed)

    @Bean(EVENT_ID_GENERATOR)
    fun eventIdGenerator(properties: IdProperties): IdGenerator = generatorFor(properties.eventSeed)

    private fun generatorFor(seed: String): IdGenerator =
        if (seed.isBlank() || seed.equals("random", ignoreCase = true)) {
            IdGenerator { UUID.randomUUID() }
        } else {
            SeededUuidGenerator(seed.trim().toLong())
        }

    companion object {
        /** Bean name of the dedicated generator for event ids (the qualifier the event store injects). */
        const val EVENT_ID_GENERATOR = "eventIdGenerator"
    }
}
