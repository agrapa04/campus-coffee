package de.seuhd.campuscoffee.data.util

import jakarta.persistence.Table

/**
 * Utility for JPA-related reflection.
 */
object JpaUtils {
    /**
     * Extracts the table name from the entity's [Table] annotation.
     *
     * @throws IllegalArgumentException if the class is not annotated with [Table] or the name is empty
     */
    fun extractTableNameFromEntity(entityClass: Class<*>): String {
        require(entityClass.isAnnotationPresent(Table::class.java)) {
            "${entityClass.simpleName} is not annotated with @Table"
        }
        val tableName = entityClass.getAnnotation(Table::class.java).name
        require(tableName.isNotEmpty()) { "@Table annotation must specify a table name." }
        return tableName
    }
}
