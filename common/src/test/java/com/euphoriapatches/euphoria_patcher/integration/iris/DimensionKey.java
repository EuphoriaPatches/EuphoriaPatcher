package com.euphoriapatches.euphoria_patcher.integration.iris;

/**
 * Stands in for the dimension id of Iris. {@link DimensionWildcardMap} only reads {@code getNamespace()} and
 * {@code getName()} from its keys by reflection, so any public class with those two methods works.
 */
public class DimensionKey {
    private final String namespace;
    private final String name;

    public DimensionKey(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }
}
