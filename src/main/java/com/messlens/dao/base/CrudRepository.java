package com.messlens.dao.base;

import java.util.List;

/**
 * Generic CRUD Repository interface.
 * Satisfies SRM CSE 307 Syllabus:
 * - Unit 4: "Performing various operations (Create, Update, Delete and Retrieve) on tables"
 * - Unit 5: "Generic Interface, Generic Class Hierarchies"
 *
 * @param <T> Domain entity type
 * @param <ID> Primary key identifier type
 */
public interface CrudRepository<T, ID> {

    boolean insert(T entity);

    T findById(ID id);

    List<T> findAll();

    boolean deleteById(ID id);
}

