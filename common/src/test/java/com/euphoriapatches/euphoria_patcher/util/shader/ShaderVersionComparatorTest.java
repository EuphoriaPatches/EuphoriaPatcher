package com.euphoriapatches.euphoria_patcher.util.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ShaderVersionComparatorTest {

    @TempDir
    Path shaderpacks;

    private ShaderVersionComparator comparator() {
        return new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", shaderpacks);
    }

    // ---- reading the version out of file names ----

    @Test
    void extractsTheVersionNumbersFromAFileName() {
        assertArrayEquals(new int[]{5, 9, 3}, comparator().extractComplementaryVersionNumbers("ComplementaryReimagined_r5.9.3.zip"));
        assertArrayEquals(new int[]{5, 1, 0}, comparator().extractComplementaryVersionNumbers("ComplementaryUnbound_r5.1"));
        assertArrayEquals(new int[]{5, 10, 0}, comparator().extractComplementaryVersionNumbers("_r5.10"));
    }

    @Test
    void aFileNameWithoutVersionGivesZeros() {
        assertArrayEquals(new int[]{0, 0, 0}, comparator().extractComplementaryVersionNumbers("SomeOtherShader.zip"));
    }

    @Test
    void formatsTheVersionTheWayItIsWrittenInFileNames() {
        assertEquals("r5.9.3", comparator().getComplementaryVersionFromFileName("ComplementaryReimagined_r5.9.3.zip"));
        assertEquals("r5.9", comparator().getComplementaryVersionFromFileName("ComplementaryReimagined_r5.9.zip"));
        assertEquals("r5.9", comparator().getComplementaryVersionFromFileName("ComplementaryReimagined_r5.9.0.zip"));
    }

    // ---- is it newer than what this mod is built for ----

    @Test
    void recognisesNewerShaderVersions() {
        ShaderVersionComparator comparator = comparator();

        assertTrue(comparator.isNewerShaderVersion("ComplementaryReimagined_r5.9.4.zip"));
        assertTrue(comparator.isNewerShaderVersion("ComplementaryUnbound_r5.10.0.zip"), "5.10 is newer than 5.9");
        assertTrue(comparator.isNewerShaderVersion("ComplementaryReimagined_r6.0.zip"));
    }

    @Test
    void sameOrOlderShaderVersionsAreNotNewer() {
        ShaderVersionComparator comparator = comparator();

        assertFalse(comparator.isNewerShaderVersion("ComplementaryReimagined_r5.9.3.zip"));
        assertFalse(comparator.isNewerShaderVersion("ComplementaryReimagined_r5.9.2.zip"));
        assertFalse(comparator.isNewerShaderVersion("ComplementaryReimagined_r5.8.9.zip"));
    }

    @Test
    void otherShadersAreNeverNewerWhateverTheirVersion() {
        assertFalse(comparator().isNewerShaderVersion("BSL_v8.2.09_r9.0.0.zip"));
    }

    // ---- dev and test versions ----

    @Test
    void recognisesTestAndDevVersionsByTheirName() {
        assertTrue(ShaderVersionComparator.isTestOrDevVersion("ComplementaryReimagined_r5.9.3_dev1.zip"));
        assertTrue(ShaderVersionComparator.isTestOrDevVersion("ComplementaryReimagined_Test.zip"));
        assertTrue(ShaderVersionComparator.isTestOrDevVersion("ComplementaryReimagined_r5.9.3_fix.zip"));
        assertTrue(ShaderVersionComparator.isTestOrDevVersion("ComplementaryReimagined_r5.9.3_pre1.zip"));
    }

    @Test
    void releaseNamesAreNotDevVersions() {
        assertFalse(ShaderVersionComparator.isTestOrDevVersion("ComplementaryReimagined_r5.9.3.zip"));
        assertFalse(ShaderVersionComparator.isTestOrDevVersion("ComplementaryUnbound_r5.9.3"));
    }

    // ---- comparing the versions of settings files, in the format patchVersion|baseVersion ----

    private static int sign(int value) {
        return Integer.signum(value);
    }

    @Test
    void thePatchVersionDecidesBeforeTheBaseVersion() {
        assertEquals(1, sign(ShaderVersionComparator.compareConfigFileVersions("1.10.6|5.9.0", "1.10.5|5.9.3")));
        assertEquals(-1, sign(ShaderVersionComparator.compareConfigFileVersions("1.10.5|5.9.3", "1.10.6|5.9.0")));
    }

    @Test
    void theBaseVersionDecidesWhenThePatchVersionsAreEqual() {
        assertEquals(-1, sign(ShaderVersionComparator.compareConfigFileVersions("1.10.5|5.9.2", "1.10.5|5.9.3")));
        assertEquals(1, sign(ShaderVersionComparator.compareConfigFileVersions("1.10.5|5.9.3", "1.10.5|5.9.2")));
    }

    @Test
    void theBaseVersionStillDecidesWhenThePatchVersionsAreTheSameDevVersion() {
        assertEquals(-1, sign(ShaderVersionComparator.compareConfigFileVersions("1.10.5-dev3|5.9.2", "1.10.5-dev3|5.9.3")));
        assertEquals(1, sign(ShaderVersionComparator.compareConfigFileVersions("1.10.5-dev3|5.9.3", "1.10.5-dev3|5.9.2")));
        assertEquals(0, ShaderVersionComparator.compareConfigFileVersions("1.10.5-dev3|5.9.3", "1.10.5-dev3|5.9.3"));
    }

    @Test
    void comparingSettingsFileVersionsIsConsistentSoSortingNeverThrows() {
        List<String> versions = new ArrayList<>(Arrays.asList(
                "1.10.5-dev3|5.9.3", "1.10.5-dev3|5.9.2", "1.10.5-dev3|5.9.3", "1.10.5-dev2|5.9.3", "1.10.5|5.9.1",
                "1.10.4|5.9.3", "1.10.5-dev3|5.9.2", "0|5.9.3", "1.10.5-dev10|5.9.0", "1.10.5-dev3|5.9.3"));
        // Many shuffled entries so that the sort has to compare lots of pairs, a broken comparator would show up
        for (int i = 0; i < 5; i++) {
            versions.addAll(new ArrayList<>(versions));
        }
        Collections.shuffle(versions, new Random(7));

        assertDoesNotThrow(() -> versions.sort(ShaderVersionComparator::compareConfigFileVersions));

        for (int i = 1; i < versions.size(); i++) {
            assertTrue(ShaderVersionComparator.compareConfigFileVersions(versions.get(i - 1), versions.get(i)) <= 0,
                    versions.get(i - 1) + " has to come before " + versions.get(i));
        }
        assertEquals("1.10.5|5.9.1", versions.get(versions.size() - 1), "the release is newer than any of its dev versions");
    }
}
