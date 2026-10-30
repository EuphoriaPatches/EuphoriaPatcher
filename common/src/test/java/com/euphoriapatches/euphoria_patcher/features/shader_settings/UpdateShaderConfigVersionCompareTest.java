package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how the version of a settings file is compared, for picking the newest one and for the markers inside a file.
 * Dev versions look like {@code 1.10.5-dev3}, a version in the format {@code patchVersion|baseVersion}.
 * Tests have been mostly written with the help of LLMs but they have been reviewed manually.
 */
class UpdateShaderConfigVersionCompareTest {

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    private static int sign(int value) {
        return Integer.signum(value);
    }

    // ---- compareVersionParts ----

    @Test
    void equalVersionsAreEqual() {
        assertEquals(0, UpdateShaderConfig.compareVersionParts("1.10.5", "1.10.5"));
        assertEquals(0, UpdateShaderConfig.compareVersionParts("1.10.5-dev3", "1.10.5-dev3"));
        assertEquals(0, UpdateShaderConfig.compareVersionParts("1.10.5-dev0", "1.10.5-dev0"));
    }

    @Test
    void partsAreComparedAsNumbersNotAsText() {
        assertEquals(1, sign(UpdateShaderConfig.compareVersionParts("1.10", "1.9")));
        assertEquals(-1, sign(UpdateShaderConfig.compareVersionParts("1.9.9", "1.10.0")));
    }

    @Test
    void missingPartsCountAsZero() {
        assertEquals(0, UpdateShaderConfig.compareVersionParts("1.10", "1.10.0"));
        assertEquals(1, sign(UpdateShaderConfig.compareVersionParts("1.10.1", "1.10")));
    }

    @Test
    void aHigherDevNumberIsNewer() {
        assertEquals(-1, sign(UpdateShaderConfig.compareVersionParts("1.10.5-dev3", "1.10.5-dev4")));
        assertEquals(1, sign(UpdateShaderConfig.compareVersionParts("1.10.5-dev4", "1.10.5-dev3")));
        assertEquals(1, sign(UpdateShaderConfig.compareVersionParts("1.10.5-dev10", "1.10.5-dev9")));
    }

    @Test
    void aDevVersionIsOlderThanTheReleaseItBelongsTo() {
        assertEquals(-1, sign(UpdateShaderConfig.compareVersionParts("1.10.5-dev3", "1.10.5")));
        assertEquals(1, sign(UpdateShaderConfig.compareVersionParts("1.10.5", "1.10.5-dev3")));
    }

    @Test
    void whenTheDevNumbersAreEqualTheFollowingPartsDecide() {
        assertEquals(-1, sign(UpdateShaderConfig.compareVersionParts("1.10.5-dev3-1", "1.10.5-dev3-2")));
        assertEquals(1, sign(UpdateShaderConfig.compareVersionParts("1.10.5-dev3-2", "1.10.5-dev3-1")));
    }

    // ---- compareConfigFileVersions ----

    @Test
    void thePatchVersionDecidesBeforeTheBaseVersion() {
        assertEquals(1, sign(UpdateShaderConfig.compareConfigFileVersions("1.10.6|5.9.0", "1.10.5|5.9.3")));
        assertEquals(-1, sign(UpdateShaderConfig.compareConfigFileVersions("1.10.5|5.9.3", "1.10.6|5.9.0")));
    }

    @Test
    void theBaseVersionDecidesWhenThePatchVersionsAreEqual() {
        assertEquals(-1, sign(UpdateShaderConfig.compareConfigFileVersions("1.10.5|5.9.2", "1.10.5|5.9.3")));
        assertEquals(1, sign(UpdateShaderConfig.compareConfigFileVersions("1.10.5|5.9.3", "1.10.5|5.9.2")));
    }

    @Test
    void theBaseVersionStillDecidesWhenThePatchVersionsAreTheSameDevVersion() {
        assertEquals(-1, sign(UpdateShaderConfig.compareConfigFileVersions("1.10.5-dev3|5.9.2", "1.10.5-dev3|5.9.3")));
        assertEquals(1, sign(UpdateShaderConfig.compareConfigFileVersions("1.10.5-dev3|5.9.3", "1.10.5-dev3|5.9.2")));
        assertEquals(0, UpdateShaderConfig.compareConfigFileVersions("1.10.5-dev3|5.9.3", "1.10.5-dev3|5.9.3"));
    }

    @Test
    void comparingIsConsistentSoSortingNeverThrows() {
        List<String> versions = new ArrayList<>(Arrays.asList(
                "1.10.5-dev3|5.9.3", "1.10.5-dev3|5.9.2", "1.10.5-dev3|5.9.3", "1.10.5-dev2|5.9.3", "1.10.5|5.9.1",
                "1.10.4|5.9.3", "1.10.5-dev3|5.9.2", "0|5.9.3", "1.10.5-dev10|5.9.0", "1.10.5-dev3|5.9.3"));
        // Many shuffled entries so that the sort has to compare lots of pairs, a broken comparator would show up
        for (int i = 0; i < 5; i++) {
            versions.addAll(new ArrayList<>(versions));
        }
        Collections.shuffle(versions, new java.util.Random(7));

        assertDoesNotThrow(() -> versions.sort(UpdateShaderConfig::compareConfigFileVersions));

        for (int i = 1; i < versions.size(); i++) {
            assertTrue(UpdateShaderConfig.compareConfigFileVersions(versions.get(i - 1), versions.get(i)) <= 0,
                    versions.get(i - 1) + " has to come before " + versions.get(i));
        }
        assertEquals("1.10.5|5.9.1", versions.get(versions.size() - 1), "the release is newer than any of its dev versions");
    }
}
