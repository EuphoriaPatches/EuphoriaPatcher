package com.euphoriapatches.euphoria_patcher.features.properties;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesOrderTest {

    private static List<String> topLevelNames() {
        List<String> names = new ArrayList<>();
        for (PropertiesOrder.OrderEntry entry : PropertiesOrder.getMergeOrder()) {
            names.add(entry.getName());
        }
        return names;
    }

    @Test
    void theTopLevelOrderIsTheDocumentedOne() {
        // Changing this order changes the merged block.properties of every user, so it is pinned on purpose
        assertEquals(java.util.Arrays.asList("instructions", "tags", "1.13+", "1.8+", "1.7.10", "renderlayers"), topLevelNames());
    }

    @Test
    void everyTopLevelNameIsUnique() {
        List<String> names = topLevelNames();
        assertEquals(names.size(), new HashSet<>(names).size());
    }

    @Test
    void theOrderCannotBeChangedFromOutside() {
        assertThrows(UnsupportedOperationException.class, () -> PropertiesOrder.getMergeOrder().clear());
        assertThrows(UnsupportedOperationException.class, () -> PropertiesOrder.getMergeOrder().add(new PropertiesOrder.OrderEntry("x")));
    }

    @Test
    void orderIndexIsThePositionAndMinusOneWhenUnknown() {
        assertEquals(0, PropertiesOrder.getOrderIndex("instructions"));
        assertEquals(2, PropertiesOrder.getOrderIndex("1.13+"));
        assertEquals(5, PropertiesOrder.getOrderIndex("renderlayers"));
        assertEquals(-1, PropertiesOrder.getOrderIndex("unknown"));
        assertEquals(-1, PropertiesOrder.getOrderIndex(null));
    }

    @Test
    void indexesFollowTheOrderOfTheList() {
        List<String> names = topLevelNames();
        for (int i = 0; i < names.size(); i++) {
            assertEquals(i, PropertiesOrder.getOrderIndex(names.get(i)));
        }
    }

    @Test
    void isInMergeOrderAlsoFindsNestedEntries() {
        assertTrue(PropertiesOrder.isInMergeOrder("tags"));
        assertTrue(PropertiesOrder.isInMergeOrder("block\\.5000-.*\\.properties"), "child of 1.13+");
        assertFalse(PropertiesOrder.isInMergeOrder("unknown"));
    }

    @Test
    void theFolderFor113ListsTheBlockFilePatternAsAChild() {
        PropertiesOrder.OrderEntry entry = null;
        for (PropertiesOrder.OrderEntry candidate : PropertiesOrder.getMergeOrder()) {
            if (candidate.getName().equals("1.13+")) entry = candidate;
        }
        assertNotNull(entry);
        assertTrue(entry.hasChildren());
        Set<String> childNames = new HashSet<>();
        for (PropertiesOrder.OrderEntry child : entry.getChildren()) {
            childNames.add(child.getName());
        }
        assertTrue(childNames.contains("block\\.5000-.*\\.properties"));
    }

    @Test
    void entriesWithoutChildrenReportNone() {
        PropertiesOrder.OrderEntry plain = new PropertiesOrder.OrderEntry("plain");
        assertFalse(plain.hasChildren());
        assertTrue(plain.getChildren().isEmpty());

        PropertiesOrder.OrderEntry parent = new PropertiesOrder.OrderEntry("parent", plain);
        assertTrue(parent.hasChildren());
        assertEquals("plain", parent.getChildren().get(0).getName());
    }
}
