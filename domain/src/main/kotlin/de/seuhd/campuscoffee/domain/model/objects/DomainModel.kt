package de.seuhd.campuscoffee.domain.model.objects

import java.io.Serializable

/**
 * Base type for all domain model objects. Extends Serializable so domain objects can be cloned
 * (see TestFixtures) and Identifiable so they expose their identifier.
 */
interface DomainModel<ID> : Serializable, Identifiable<ID>
