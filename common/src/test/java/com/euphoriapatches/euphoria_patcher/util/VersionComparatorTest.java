package com.euphoriapatches.euphoria_patcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionComparatorTest {

    @Test
    void comparesVersionStringsNumerically() {
        assertTrue(VersionComparator.compareVersionStrings("1.7.4", "1.7.3") > 0);
        assertTrue(VersionComparator.compareVersionStrings("1.7.3", "1.7.4") < 0);
        assertEquals(0, VersionComparator.compareVersionStrings("1.7.4", "1.7.4"));
        // 10 is bigger than 9, which a text comparison would get wrong
        assertTrue(VersionComparator.compareVersionStrings("1.10.0", "1.9.9") > 0);
    }

    @Test
    void longerVersionWithEqualStartIsNewer() {
        assertTrue(VersionComparator.compareVersionStrings("1.2.3", "1.2") > 0);
        assertTrue(VersionComparator.compareVersionStrings("1.2", "1.2.3") < 0);
    }

    @Test
    void isNewerVersionIsStrict() {
        assertTrue(VersionComparator.isNewerVersion("1.7.5", "1.7.4"));
        assertFalse(VersionComparator.isNewerVersion("1.7.4", "1.7.4"));
        assertFalse(VersionComparator.isNewerVersion("1.7.3", "1.7.4"));
    }

    @Test
    void isNewerOrEqualIncludesEqual() {
        assertTrue(VersionComparator.isNewerOrEqual("1.7.5", "1.7.4"));
        assertTrue(VersionComparator.isNewerOrEqual("1.7.4", "1.7.4"));
        assertFalse(VersionComparator.isNewerOrEqual("1.7.3", "1.7.4"));
    }

    @Test
    void nonNumericVersionStringsThrow() {
        // Callers rely on this, the update check catches it and reports a failed check
        assertThrows(NumberFormatException.class, () -> VersionComparator.compareVersionStrings("1.x.4", "1.7.4"));
    }

    @Test
    void comparesVersionArrays() {
        assertTrue(VersionComparator.compareVersionArrays(new int[]{5, 3, 2}, new int[]{5, 1, 0}) > 0);
        assertTrue(VersionComparator.compareVersionArrays(new int[]{5, 1, 0}, new int[]{5, 3, 2}) < 0);
        assertEquals(0, VersionComparator.compareVersionArrays(new int[]{5, 1, 0}, new int[]{5, 1, 0}));
        assertTrue(VersionComparator.compareVersionArrays(new int[]{5, 1, 0}, new int[]{5, 1}) > 0);
    }

    @Test
    void parsesVersionToIntArray() {
        assertArrayEquals(new int[]{1, 7, 4}, VersionComparator.parseVersionToIntArray("1.7.4"));
        assertArrayEquals(new int[]{1, 10, 0}, VersionComparator.parseVersionToIntArray("1.10.0"));
        assertArrayEquals(new int[]{5, 9}, VersionComparator.parseVersionToIntArray("5.9"));
    }

    @Test
    void parseVersionToIntArrayReturnsNullInsteadOfThrowing() {
        assertNull(VersionComparator.parseVersionToIntArray(null));
        assertNull(VersionComparator.parseVersionToIntArray(""));
        assertNull(VersionComparator.parseVersionToIntArray("7"));
        assertNull(VersionComparator.parseVersionToIntArray("1.x.3"));
        assertNull(VersionComparator.parseVersionToIntArray("1.7.4-dev"));
    }
}
