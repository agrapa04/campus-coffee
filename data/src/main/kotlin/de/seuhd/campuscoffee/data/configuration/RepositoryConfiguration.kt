package de.seuhd.campuscoffee.data.configuration

import de.seuhd.campuscoffee.data.persistence.repositories.ResettableSequenceRepositoryImpl
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Configures all repositories to use the custom base class that provides automatic sequence reset.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = ["de.seuhd.campuscoffee.data.persistence.repositories"],
    repositoryBaseClass = ResettableSequenceRepositoryImpl::class
)
class RepositoryConfiguration
