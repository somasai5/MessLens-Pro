package com.messlens.util;

/**
 * Generic class demonstrating two type parameters (K, V).
 * Satisfies SRM CSE 307 Syllabus Unit 5:
 * "Generic Class with Two types of parameter, General Form of a Generic Class".
 *
 * @param <K> Type of Key or First element
 * @param <V> Type of Value or Second element
 */
public class Pair<K, V> {
    private K key;
    private V value;

    public Pair() {}

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public void setKey(K key) {
        this.key = key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    /**
     * Generic factory method with wildcard / type parameter.
     */
    public static <T1, T2> Pair<T1, T2> of(T1 key, T2 value) {
        return new Pair<>(key, value);
    }

    @Override
    public String toString() {
        return "(" + key + " = " + value + ")";
    }
}

