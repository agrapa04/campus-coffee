package de.seuhd.campuscoffee.data.persistence.generators

import org.hibernate.annotations.IdGeneratorType

/**
 * Marks an id for table-based sequence generation; the sequence name is derived from the entity's
 * table name.
 */
@IdGeneratorType(CustomSequenceGenerator::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class CustomSequence
