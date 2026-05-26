package de.seuhd.campuscoffee.data.implementations;

import de.seuhd.campuscoffee.data.mapper.EntityMapper;
import de.seuhd.campuscoffee.data.persistence.entities.Entity;
import de.seuhd.campuscoffee.data.constraints.ConstraintMapping;
import de.seuhd.campuscoffee.data.persistence.repositories.ResettableSequenceRepository;
import de.seuhd.campuscoffee.data.constraints.ConstraintRetriever;
import de.seuhd.campuscoffee.domain.exceptions.DuplicationException;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.model.objects.DomainModel;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base implementation of CRUD data service operations.
 * This abstract class provides common CRUD functionality that can be reused across
 * different entity types, reducing code duplication.
 * <p>
 * Subclasses must provide the repository, mapper, and domain class type via the constructor.
 * This follows the hexagonal architecture pattern where the data layer acts as an adapter
 * to the domain layer's port interface.
 *
 * @param <DOMAIN>     the domain model type (must implement Identifiable)
 * @param <ENTITY>     the JPA entity type
 * @param <ID>         the type of the unique identifier (e.g., Long, UUID, String)
 * @param <REPOSITORY> the repository type (must extend both JpaRepository and ResettableSequenceRepository)
 */
@RequiredArgsConstructor
public abstract class CrudDataServiceImpl<
        DOMAIN extends DomainModel<ID>,
        ENTITY extends Entity,
        REPOSITORY extends JpaRepository<ENTITY, ID> & ResettableSequenceRepository,
        ID>
        implements CrudDataService<DOMAIN, ID> {

    /*
     * JPA repository for entity persistence.
     */
    protected final REPOSITORY repository;
    /*
     * Mapper for converting between domain and entity objects.
     */
    protected final EntityMapper<DOMAIN, ENTITY> mapper;
    /*
     * The domain class type (used for exception messages).
     */
    protected final Class<DOMAIN> domainClass;
    /*
     * The entity class type (used for constraint extraction).
     */
    protected final Class<ENTITY> entityClass;
    /*
     * Constraint extractor for automatic unique field constraint discovery.
     */
    protected final ConstraintRetriever<DOMAIN, ENTITY> databaseConstraintExtractor;

    @Override
    public void clear() {
        repository.deleteAllInBatch();
        repository.flush();
        repository.resetSequence(); // ensure consistent IDs after clearing (for local testing)
    }

    @Override
    @NonNull
    public List<DOMAIN> getAll() {
        return repository.findAll().stream()
                .map(mapper::fromEntity)
                .toList();
    }

    @Override
    @NonNull
    public DOMAIN getById(@NonNull ID id) {
        return repository.findById(id)
                .map(mapper::fromEntity)
                .orElseThrow(() -> new NotFoundException(domainClass, id));
    }

    /**
     * Upserts a domain object with automatic constraint violation handling,
     * converting database constraint violations into domain-specific DuplicationExceptions.
     * <p>
     * The constraint-to-field mapping is provided by subclasses, allowing entity-specific
     * validation while keeping the exception handling logic centralized.
     *
     * @param domain the domain object to upsert
     * @throws DuplicationException if a uniqueness constraint is violated
     * @throws DataIntegrityViolationException if an unhandled constraint violation occurs
     */
    @Override
    @NonNull
    public DOMAIN upsert(@NonNull DOMAIN domain) {
        // extract constraints automatically from entity annotations
        Set<ConstraintMapping<DOMAIN>> fieldConstraints =
                databaseConstraintExtractor.extractConstraintsFromEntity(entityClass);

        try {
            ID id = domain.getId();

            if (id == null) {
                // create new entity
                return mapper.fromEntity(
                        repository.saveAndFlush(mapper.toEntity(domain))
                );
            }

            // update existing entity
            ENTITY entity = repository.findById(id)
                    .orElseThrow(() -> new NotFoundException(domainClass, id));

            // use mapper to update entity fields automatically
            // note: timestamps are managed by JPA lifecycle callbacks (@PreUpdate)
            mapper.updateEntity(domain, entity);

            return mapper.fromEntity(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Check each registered constraint to see if it was violated
            for (var fieldConstraint : fieldConstraints) {
                if (isConstraintViolation(e, fieldConstraint.constraintName())) {
                    Object fieldValue = fieldConstraint.extractValue(domain);
                    throw new DuplicationException(domainClass, fieldConstraint.columnName(), String.valueOf(fieldValue));
                }
            }
            // if no constraint matched, re-throw the original exception
            throw e;
        }
    }

    @Override
    public void delete(@NonNull ID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException(domainClass, id);
        }
        repository.deleteById(id);
    }

    /**
     * Generic helper method for querying by a unique field.
     * Follows the common pattern: repository.findByX() -> map -> orElseThrow.
     * This reduces code duplication across data service implementations that need to
     * query entities by unique fields other than the primary key.
     *
     * @param queryFunction function that queries the repository and returns Optional&lt;ENTITY&gt;
     * @param fieldName     the name of the field being queried (for the exception message)
     * @param fieldValue    the value being queried for (for the exception message)
     * @return the domain object if found
     * @throws NotFoundException if no entity matches the query
     */
    protected DOMAIN findByFieldOrThrow(
            Supplier<Optional<ENTITY>> queryFunction,
            String fieldName,
            String fieldValue) {
        return queryFunction.get()
                .map(mapper::fromEntity)
                .orElseThrow(() -> new NotFoundException(domainClass, fieldName, fieldValue));
    }

    /**
     * Checks if the exception is due to a specific constraint violation.
     * Checks both the exception message and root cause for the constraint name.
     *
     * @param exception              the DataIntegrityViolationException to check
     * @param constraintName the database constraint name to look for
     * @return true if the exception is due to the specified constraint violation
     */
    // package-private so CrudDataServiceImplTest can drive the message- and root-cause-matching branches
    // directly with crafted exceptions, which a black-box test cannot distinguish
    static boolean isConstraintViolation(DataIntegrityViolationException exception, String constraintName) {
        // check the exception message for the constraint name
        String message = exception.getMessage();
        if (message != null && message.contains(constraintName)) {
            return true;
        }

        // also check root-cause for constraint violations
        Throwable cause = exception.getRootCause();
        if (cause != null) {
            String causeMessage = cause.getMessage();
            return causeMessage != null && causeMessage.contains(constraintName);
        }

        return false;
    }
}
