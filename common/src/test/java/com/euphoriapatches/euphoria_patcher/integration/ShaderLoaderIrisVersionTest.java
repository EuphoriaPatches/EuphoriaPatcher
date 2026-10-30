package com.euphoriapatches.euphoria_patcher.integration;

import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how the version of Iris is read from the name of its jar. Names are as they are on Modrinth.
 */
class ShaderLoaderIrisVersionTest {

    @Test
    void readsTheVersionOfNeoForgeAndFabricNames() {
        assertEquals("1.11.7", ShaderLoader.extractIrisVersion("iris-neoforge-1.11.7+mc26.3.jar"));
        assertEquals("1.11.2", ShaderLoader.extractIrisVersion("iris-neoforge-1.11.2+mc26.1.2.jar"));
        assertEquals("1.10.7", ShaderLoader.extractIrisVersion("iris-neoforge-1.10.7+mc1.21.11.jar"));
        assertEquals("1.8.8", ShaderLoader.extractIrisVersion("iris-fabric-1.8.8+mc1.21.4.jar"));
    }

    @Test
    void aBetaSuffixIsLeftOut() {
        assertEquals("1.8.14", ShaderLoader.extractIrisVersion("iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"));
        assertEquals("1.8.0", ShaderLoader.extractIrisVersion("iris-fabric-1.8.0-beta.2+mc1.21.1.jar"));
    }

    @Test
    void aNameWithoutTheLoaderPartIsRead() {
        assertEquals("1.9.1", ShaderLoader.extractIrisVersion("iris-1.9.1+mc1.21.5.jar"));
    }

    @Test
    void capitalisationDoesNotMatter() {
        assertEquals("1.8.8", ShaderLoader.extractIrisVersion("Iris-Fabric-1.8.8+mc1.21.4.jar"));
    }

    @Test
    void namesWithoutAVersionGiveNull() {
        assertNull(ShaderLoader.extractIrisVersion("iris-fabric.jar"));
        assertNull(ShaderLoader.extractIrisVersion("iris-fabric-latest+mc1.21.4.jar"));
        assertNull(ShaderLoader.extractIrisVersion("sodium-fabric-0.6.0+mc1.21.4.jar"));
        assertNull(ShaderLoader.extractIrisVersion("iris-fabric-1.8+mc1.21.4.jar"), "two numbers are no version here");
    }

    @Test
    void theMinecraftPartIsNotTakenForTheIrisVersion() {
        // The first three numbers after the loader are the version of Iris, never the ones after +mc
        assertEquals("1.11.7", ShaderLoader.extractIrisVersion("iris-neoforge-1.11.7+mc1.21.11.jar"));
    }

    @Test
    void theVersionsOfTheNamesCompareAsExpectedForTheMacOSCheck() {
        int neoForge26 = VersionComparator.toPackedInt(ShaderLoader.extractIrisVersion("iris-neoforge-1.11.7+mc26.3.jar"));
        int beta = VersionComparator.toPackedInt(ShaderLoader.extractIrisVersion("iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"));

        assertEquals(11107, neoForge26);
        assertEquals(10814, beta);
        assertFalse(VersionComparator.isNewerOrEqual(neoForge26, "1.12.0"));
        assertTrue(VersionComparator.isNewerOrEqual(VersionComparator.toPackedInt("1.12.0"), "1.12.0"));
    }

    @Test
    void readsTheVersionOfTheOlderNameScheme() {
        assertEquals("1.6.9", ShaderLoader.extractIrisVersion("iris-mc1.20.1-1.6.9.jar"));
        assertEquals("1.6.4", ShaderLoader.extractIrisVersion("iris-mc1.19.2-1.6.4.jar"));
        assertEquals("1.6.11", ShaderLoader.extractIrisVersion("iris-mc1.18.2-1.6.11.jar"), "two digits in the last number");
        assertEquals("1.6.9", ShaderLoader.extractIrisVersion("Iris-MC1.20.1-1.6.9.jar"));
    }

    @Test
    void theMinecraftVersionComesFromEitherNameScheme() {
        assertEquals("1.21.4", ShaderLoader.extractIrisMinecraftVersion("iris-fabric-1.8.8+mc1.21.4.jar"));
        assertEquals("1.21.1", ShaderLoader.extractIrisMinecraftVersion("iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"));
        assertEquals("26.3", ShaderLoader.extractIrisMinecraftVersion("iris-neoforge-1.11.7+mc26.3.jar"));
        assertEquals("1.20.1", ShaderLoader.extractIrisMinecraftVersion("iris-mc1.20.1-1.6.9.jar"));
        assertEquals("1.19.2", ShaderLoader.extractIrisMinecraftVersion("Iris-MC1.19.2-1.6.4.jar"));
        assertEquals("1.18.2", ShaderLoader.extractIrisMinecraftVersion("iris-mc1.18.2-1.6.11.jar"));
        assertNull(ShaderLoader.extractIrisMinecraftVersion("iris-fabric.jar"));
        assertNull(ShaderLoader.extractIrisMinecraftVersion("iris-fabric-1.8.8.jar"), "no +mc part");
    }

    @Test
    void bothIrisNameSchemesAreFoundAsShaderLoader() {
        assertTrue(ShaderLoader.isShaderLoaderFileName("iris-fabric-1.8.8+mc1.21.4.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("iris-neoforge-1.11.7+mc26.3.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("iris-mc1.20.1-1.6.9.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("iris-mc1.18.2-1.6.11.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("Iris-MC1.20.1-1.6.9.jar"));
    }

    @Test
    void otherJarsAreNotTakenForAShaderLoader() {
        assertFalse(ShaderLoader.isShaderLoaderFileName("irisflags-1.0.jar"), "some other mod that starts with iris");
        assertFalse(ShaderLoader.isShaderLoaderFileName("sodium-fabric-0.6.0+mc1.21.4.jar"));
        assertFalse(ShaderLoader.isShaderLoaderFileName("EuphoriaPatcher-1.10.5-r5.9.3-fabric.jar"));
    }

    @Test
    void theOtherShaderLoadersAreStillFound() {
        assertTrue(ShaderLoader.isShaderLoaderFileName("oculus-mc1.20.1-1.8.0.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("mekalus-mc1.20.1-1.7.0.3.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("emberlens-mc1.20.1-1.8.6.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("neoculus-mc1.21.1-1.8.7.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("OptiFine_1.18.1_HD_U_H6.jar"));
        assertTrue(ShaderLoader.isShaderLoaderFileName("angelica-1.0.0-beta15.jar"));
    }

    @Test
    void theOlderSchemeVersionIsPackedAndComparedLikeAnyOther() {
        int version = VersionComparator.toPackedInt(ShaderLoader.extractIrisVersion("iris-mc1.18.2-1.6.11.jar"));

        assertEquals(10611, version);
        assertTrue(version > VersionComparator.toPackedInt(ShaderLoader.extractIrisVersion("iris-mc1.20.1-1.6.9.jar")), "1.6.11 is newer than 1.6.9");
        assertFalse(VersionComparator.isNewerOrEqual(version, "1.12.0"));
    }

    @Test
    void aSnapshotBuildNameGivesTheIrisAndTheMinecraftVersion() {
        String name = "iris-fabric-1.12.0-snapshot+mc26.3-local.jar";

        assertTrue(ShaderLoader.isShaderLoaderFileName(name));
        assertEquals("1.12.0", ShaderLoader.extractIrisVersion(name));
        assertEquals("26.3", ShaderLoader.extractIrisMinecraftVersion(name), "the -local ending is no part of the version");
        assertTrue(VersionComparator.isNewerOrEqual(VersionComparator.toPackedInt(ShaderLoader.extractIrisVersion(name)), "1.12.0"));
    }

    @Test
    void textAfterTheMinecraftVersionIsLeftOut() {
        assertEquals("26.1.2", ShaderLoader.extractIrisMinecraftVersion("iris-neoforge-1.11.2+mc26.1.2.jar"));
        assertEquals("1.21.4", ShaderLoader.extractIrisMinecraftVersion("iris-fabric-1.8.8+mc1.21.4-dev.jar"));
        assertEquals("1.21.4", ShaderLoader.extractIrisMinecraftVersion("iris-fabric-1.8.8+mc1.21.4"));
    }

    @Test
    void minecraftVersionsHaveTwoOrThreeNumbers() {
        assertTrue(ShaderLoader.isMinecraftVersion("1.21.1"));
        assertTrue(ShaderLoader.isMinecraftVersion("1.7.10"));
        assertTrue(ShaderLoader.isMinecraftVersion("26.3"));
        assertTrue(ShaderLoader.isMinecraftVersion("26.1.2"));
        assertFalse(ShaderLoader.isMinecraftVersion("26"));
        assertFalse(ShaderLoader.isMinecraftVersion("26.3-local"));
        assertFalse(ShaderLoader.isMinecraftVersion("1.21.1.1"));
        assertFalse(ShaderLoader.isMinecraftVersion("unknown"));
        assertFalse(ShaderLoader.isMinecraftVersion(""));
        assertFalse(ShaderLoader.isMinecraftVersion(null));
    }

    @Test
    void theVersionsOf2026AreNewerThanTheOlderOnes() {
        assertTrue(VersionComparator.isNewerOrEqual("26.3", "1.21.1"));
        assertTrue(VersionComparator.isNewerOrEqual("26.1.2", "26.1"));
        assertFalse(VersionComparator.isNewerOrEqual("1.21.11", "26.1"));
    }
}
