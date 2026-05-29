package de.seuhd.campuscoffee.data.persistence.generators

import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.enhanced.SequenceStyleGenerator
import java.util.Properties

/**
 * Hibernate id generator that derives the sequence name from the entity's table name (table "users"
 * uses sequence "users_seq") and sets the increment to 1 to match the Flyway-created sequences.
 */
class CustomSequenceGenerator : SequenceStyleGenerator() {
    override fun configure(
        creationContext: GeneratorCreationContext,
        parameters: Properties
    ) {
        val tableName = creationContext.value.table.name
        parameters.setProperty(SEQUENCE_PARAM, tableName + "_seq")
        parameters.setProperty(INCREMENT_PARAM, "1")
        super.configure(creationContext, parameters)
    }
}
