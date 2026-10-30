package com.euphoriapatches.euphoria_patcher.util.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
}
