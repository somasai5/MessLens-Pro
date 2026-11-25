package com.messlens.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility class demonstrating Java Collections Framework operations.
 * Explicitly fulfills SRM CSE 307 Syllabus:
 * - Unit 4: "Collection Interfaces - List, Set, Map, Collection Classes - ArrayList, HashSet"
 * - Unit 4: "Accessing a collection via an Iterator"
 * - Unit 5: "Using Wildcard Arguments, Creating Generic Method"
 */
public class CollectionUtil {

    /**
     * Filters any collection using an explicit Iterator traversal and wildcard bounds.
     * Demonstrates: Iterator traversal, Collection<? extends T>, and List<T>.
     */
    public static <T> List<T> filterWithIterator(Collection<? extends T> source, Predicate<? super T> predicate) {
        List<T> result = new ArrayList<>();
        if (source == null) return result;

        // Syllabus Unit 4: Explicit traversal using java.util.Iterator
        Iterator<? extends T> iterator = source.iterator();
        while (iterator.hasNext()) {
            T item = iterator.next();
            if (predicate.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Extracts unique elements into a HashSet using wildcard input.
     * Demonstrates Set/HashSet collection interface from Unit 4.
     */
    public static <T> Set<T> toUniqueSet(Collection<? extends T> items) {
        Set<T> set = new HashSet<>();
        if (items != null) {
            Iterator<? extends T> it = items.iterator();
            while (it.hasNext()) {
                set.add(it.next());
            }
        }
        return set;
    }
}

