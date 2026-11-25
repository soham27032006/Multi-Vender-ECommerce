package com.marketplace.dao;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD contract. Each DAO fixes T (entity) and ID (key type),
 * so the same method names work for every table.
 */
public interface Repository<T, ID> {

    /** Inserts and returns the entity with its generated id set. */
    T save(T entity);

    /** Optional instead of null, so callers must handle "not found". */
    Optional<T> findById(ID id);

    List<T> findAll();

    boolean update(T entity);

    boolean delete(ID id);

    /** Bounded wildcard: accepts List<T> or a list of any subtype of T. */
    default void saveAll(List<? extends T> entities) {
        for (T entity : entities) {
            save(entity);
        }
    }
}
