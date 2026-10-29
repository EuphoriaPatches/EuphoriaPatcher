package com.euphoriapatches.euphoria_patcher.integration.iris;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DimensionWildcardMapTest {

    private static DimensionKey key(String namespace, String name) {
        return new DimensionKey(namespace, name);
    }

    @Test
    void anExactEntryIsFound() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("minecraft:overworld", "OVERWORLD");

        assertEquals("OVERWORLD", map.get(key("minecraft", "overworld")));
        assertTrue(map.containsKey(key("minecraft", "overworld")));
    }

    @Test
    void aMissingNamespaceMeansMinecraft() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("the_nether", "NETHER");

        assertEquals("NETHER", map.get(key("minecraft", "the_nether")));
        assertNull(map.get(key("other", "the_nether")));
    }

    @Test
    void aStarInTheNameMatchesAnyText() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mymod:nether_*", "MODDED_NETHER");

        assertEquals("MODDED_NETHER", map.get(key("mymod", "nether_deep")));
        assertEquals("MODDED_NETHER", map.get(key("mymod", "nether_")));
        assertNull(map.get(key("mymod", "overworld")));
        assertNull(map.get(key("mymod", "the_nether_deep")), "the star only covers what follows the prefix");
    }

    @Test
    void aStarInTheNamespaceMatchesAnyNamespace() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("*:the_end", "END");

        assertEquals("END", map.get(key("minecraft", "the_end")));
        assertEquals("END", map.get(key("anything", "the_end")));
        assertNull(map.get(key("minecraft", "overworld")));
    }

    @Test
    void aStarInTheMiddleMatchesInsideTheName() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mod:a*z", "AZ");

        assertEquals("AZ", map.get(key("mod", "az")));
        assertEquals("AZ", map.get(key("mod", "abcz")));
        assertNull(map.get(key("mod", "abc")));
    }

    @Test
    void textThatLooksLikeRegexIsTakenLiterally() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mod:a.b", "DOT");
        map.registerEntry("mod:x+y", "PLUS");

        assertEquals("DOT", map.get(key("mod", "a.b")));
        assertNull(map.get(key("mod", "axb")), "the dot is a dot, not 'any character'");
        assertEquals("PLUS", map.get(key("mod", "x+y")));
        assertNull(map.get(key("mod", "xxy")));
    }

    @Test
    void textThatLooksLikeRegexIsAlsoTakenLiterallyNextToAStar() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mod:a.b*", "DOT_STAR");
        map.registerEntry("mod:(x)*", "BRACKETS");

        assertEquals("DOT_STAR", map.get(key("mod", "a.bcd")));
        assertNull(map.get(key("mod", "axbcd")), "the dot is a dot, not 'any character'");
        assertEquals("BRACKETS", map.get(key("mod", "(x)yz")));
        assertNull(map.get(key("mod", "xyz")), "the brackets are text, not a regex group");
    }

    @Test
    void laterEntriesWinOverEarlierOnes() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mod:*", "GENERAL");
        map.registerEntry("mod:cave", "SPECIFIC");

        assertEquals("SPECIFIC", map.get(key("mod", "cave")));
        assertEquals("GENERAL", map.get(key("mod", "sky")));
    }

    @Test
    void whenTheGeneralEntryComesLastItWinsEvenOverASpecificOne() {
        // The order in dimension.properties decides, a specific rule has to be written after the general one
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mod:cave", "SPECIFIC");
        map.registerEntry("mod:*", "GENERAL");

        assertEquals("GENERAL", map.get(key("mod", "cave")));
    }

    @Test
    void theCatchAllIsAlwaysTheLastResortWhereverItIsWritten() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("*:*", "CATCH_ALL");
        map.registerEntry("minecraft:overworld", "OVERWORLD");

        assertEquals("OVERWORLD", map.get(key("minecraft", "overworld")));
        assertEquals("CATCH_ALL", map.get(key("mod", "anything")));

        DimensionWildcardMap catchAllLast = new DimensionWildcardMap();
        catchAllLast.registerEntry("minecraft:overworld", "OVERWORLD");
        catchAllLast.registerEntry("*:*", "CATCH_ALL");

        assertEquals("OVERWORLD", catchAllLast.get(key("minecraft", "overworld")), "a catch-all written last must still lose");
        assertEquals("CATCH_ALL", catchAllLast.get(key("mod", "anything")));
    }

    @Test
    void nothingMatchesWithoutEntries() {
        DimensionWildcardMap map = new DimensionWildcardMap();

        assertNull(map.get(key("minecraft", "overworld")));
        assertFalse(map.containsKey(key("minecraft", "overworld")));
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    void anEntryAddedAfterAMissIsFoundOnTheNextLookup() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        assertNull(map.get(key("mod", "late")));

        map.registerEntry("mod:late", "LATE");

        assertEquals("LATE", map.get(key("mod", "late")), "the earlier 'no match' must not be remembered");
    }

    @Test
    void anEntryAddedAfterAHitCanTakeOver() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("mod:*", "GENERAL");
        assertEquals("GENERAL", map.get(key("mod", "cave")));

        map.registerEntry("mod:cave", "SPECIFIC");

        assertEquals("SPECIFIC", map.get(key("mod", "cave")));
    }

    @Test
    void putReadsTheKeyLikeIrisGivesIt() {
        DimensionWildcardMap map = new DimensionWildcardMap();

        assertNull(map.put(key("mymod", "sky_*"), "SKY"));

        assertEquals("SKY", map.get(key("mymod", "sky_island")));
        assertEquals(1, map.size());
    }

    @Test
    void putIgnoresKeysThatAreNotDimensionIds() {
        DimensionWildcardMap map = new DimensionWildcardMap();

        map.put("minecraft:overworld", "TEXT_KEY");
        map.put(null, "NULL_KEY");

        assertTrue(map.isEmpty());
        assertNull(map.get("minecraft:overworld"));
        assertNull(map.get(null));
        assertFalse(map.containsKey("minecraft:overworld"));
    }

    @Test
    void putAllAddsEverythingInOrder() {
        Map<Object, Object> source = new java.util.LinkedHashMap<>();
        source.put(key("mod", "*"), "GENERAL");
        source.put(key("mod", "cave"), "SPECIFIC");
        DimensionWildcardMap map = new DimensionWildcardMap();

        map.putAll(source);

        assertEquals(2, map.size());
        assertEquals("SPECIFIC", map.get(key("mod", "cave")));
    }

    @Test
    void valuesAndContainsValueSeeTheRegisteredValues() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("a:b", "ONE");
        map.registerEntry("c:d", "TWO");

        assertTrue(map.containsValue("ONE"));
        assertFalse(map.containsValue("THREE"));
        assertEquals(2, map.values().size());
        assertTrue(map.values().contains("TWO"));
    }

    @Test
    void clearRemovesEverythingIncludingRememberedMatches() {
        DimensionWildcardMap map = new DimensionWildcardMap();
        map.registerEntry("a:b", "ONE");
        assertEquals("ONE", map.get(key("a", "b")));

        map.clear();

        assertTrue(map.isEmpty());
        assertNull(map.get(key("a", "b")));
    }

    @Test
    void theBuilderIsKeptPerThreadUntilItIsFinished() {
        assertNull(DimensionWildcardMap.currentBuilder());

        DimensionWildcardMap.beginBuilding();
        try {
            DimensionWildcardMap builder = DimensionWildcardMap.currentBuilder();
            assertNotNull(builder);
            assertSame(builder, DimensionWildcardMap.currentBuilder());
        } finally {
            DimensionWildcardMap.finishBuilding();
        }

        assertNull(DimensionWildcardMap.currentBuilder());
    }

    @Test
    void worksThroughTheMapInterfaceWhereIrisExpectsOne() {
        Map<Object, Object> asMap = new DimensionWildcardMap();
        asMap.put(key("minecraft", "overworld"), "OVERWORLD");

        assertEquals("OVERWORLD", asMap.get(key("minecraft", "overworld")));
    }
}
