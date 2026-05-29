package de.seuhd.campuscoffee.domain.implementation

import de.seuhd.campuscoffee.domain.model.objects.User
import de.seuhd.campuscoffee.domain.ports.api.UserService
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService
import de.seuhd.campuscoffee.domain.ports.data.UserDataService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Implementation of the User service that handles business logic related to user entities.
 */
@Service
class UserServiceImpl(
    private val userDataService: UserDataService,
) : CrudServiceImpl<User, Long>(User::class.java), UserService {

    override fun dataService(): CrudDataService<User, Long> = userDataService

    override fun getByLoginName(loginName: String): User {
        log.debug("Retrieving user with login name: {}", loginName)
        return userDataService.getByLoginName(loginName)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(UserServiceImpl::class.java)
    }
}
