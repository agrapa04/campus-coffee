package de.seuhd.campuscoffee.data.persistence.repositories

import de.seuhd.campuscoffee.data.util.JpaUtils
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean

/**
 * Base repository class that resets an entity's ID sequence, deriving the sequence name from the
 * entity's table name ({table_name}_seq). Configured as the base class for all CampusCoffee
 * repositories.
 */
@NoRepositoryBean
class ResettableSequenceRepositoryImpl<T : Any, ID : Any>(
    entityInformation: JpaEntityInformation<T, *>,
    private val entityManager: EntityManager
) : SimpleJpaRepository<T, ID>(entityInformation, entityManager),
    ResettableSequenceRepository {
    private val domainClass: Class<T> = entityInformation.javaType

    @Transactional
    override fun resetSequence() {
        val tableName = JpaUtils.extractTableNameFromEntity(domainClass)
        val sequenceName = "${tableName}_seq"
        entityManager.createNativeQuery("ALTER SEQUENCE $sequenceName RESTART WITH 1").executeUpdate()
    }
}
