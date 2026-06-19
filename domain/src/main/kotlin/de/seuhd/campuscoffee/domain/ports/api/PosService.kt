package de.seuhd.campuscoffee.domain.ports.api

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException
import de.seuhd.campuscoffee.domain.exceptions.ExternalServiceException
import de.seuhd.campuscoffee.domain.exceptions.MissingFieldException
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException
import de.seuhd.campuscoffee.domain.exceptions.ValidationException
import de.seuhd.campuscoffee.domain.model.enums.CampusType
import de.seuhd.campuscoffee.domain.model.objects.Pos
import de.seuhd.campuscoffee.domain.ports.data.OsmDataService
import de.seuhd.campuscoffee.domain.ports.data.PosDataService
import java.util.UUID

/**
 * Service interface for POS (Point of Sale) operations.
 *
 * This is a port in the hexagonal architecture pattern, implemented by the domain layer
 * and consumed by the API layer. It encapsulates business rules and orchestrates
 * data operations through the [PosDataService] port.
 *
 * Extends [CrudService] to inherit common CRUD operations and adds POS-specific operations.
 */
interface PosService : CrudService<Pos, UUID> {
    /**
     * Retrieves a specific Point of Sale by its unique name.
     *
     * @param name the unique name of the POS to retrieve
     * @return the POS with the specified name
     * @throws NotFoundException if no POS exists with the given name
     */
    fun getByName(name: String): Pos

    /**
     * Imports a Point of Sale from an OpenStreetMap node.
     * Fetches POS data from OpenStreetMap using the [OsmDataService], converts it to a POS entity,
     * and saves it as a new POS. Re-importing a node whose name matches an existing POS is rejected;
     * there is no update-by-import.
     *
     * @param nodeId     the OpenStreetMap node ID to import
     * @param campusType the campus type to assign to the imported POS
     * @return the created POS
     * @throws NotFoundException if the OSM node with the given ID does not exist
     * @throws ExternalServiceException if the OpenStreetMap API cannot be reached or fails
     * @throws MissingFieldException if the OSM node lacks required fields for creating a valid POS
     * @throws ValidationException if the OSM node's house number or postal code is invalid
     * @throws DuplicationException if a POS with the same name already exists
     */
    fun importFromOsmNode(
        nodeId: Long,
        campusType: CampusType
    ): Pos
}
