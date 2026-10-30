package com.euphoriapatches.euphoria_patcher.util.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how the comparator finds newer, older and dev versions of the base shader in a shaderpacks folder. The mod is
 * built for Complementary r5.9.3 here.
 */
class ShaderVersionComparatorFolderScanTest {

    @TempDir
    Path shaderpacks;

    private ShaderVersionComparator comparator() {
        return new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", shaderpacks);
    }

    private Path file(String name) throws IOException {
        Path file = shaderpacks.resolve(name);
        Files.write(file, "x".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private Path folder(String name) throws IOException {
        return Files.createDirectories(shaderpacks.resolve(name));
    }

    // ---- findNewerComplementaryVersion ----

    @Test
    void findsANewerZip() throws IOException {
        file("ComplementaryReimagined_r5.9.3.zip");
        Path newer = file("ComplementaryReimagined_r5.10.0.zip");

        assertEquals(newer, comparator().findNewerComplementaryVersion());
    }

    @Test
    void findsANewerFolder() throws IOException {
        Path newer = folder("ComplementaryUnbound_r6.0");

        assertEquals(newer, comparator().findNewerComplementaryVersion());
    }

    @Test
    void sameOlderPatchedDevOtherBrandAndOtherFileTypesAreNotNewerVersions() throws IOException {
        file("ComplementaryReimagined_r5.9.3.zip");
        file("ComplementaryReimagined_r5.8.0.zip");
        file("ComplementaryReimagined_r6.0.0 + EuphoriaPatches_1.11.0.zip");
        file("ComplementaryReimagined_r6.0.0_dev1.zip");
        file("ComplementaryReimagined_r6.0.0_pre2.zip");
        file("BSL_r9.0.0.zip");
        file("ComplementaryReimagined_r6.0.0.rar");
        file("Complementary-without-a-version.zip");

        assertNull(comparator().findNewerComplementaryVersion());
    }

    @Test
    void findingANewerVersionNeedsAnExistingFolder() {
        assertNull(comparator().findNewerComplementaryVersion(), "empty folder");
        assertNull(new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", shaderpacks.resolve("missing")).findNewerComplementaryVersion());
        assertNull(new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", null).findNewerComplementaryVersion());
    }

    // ---- findHighestOlderComplementaryVersion ----

    @Test
    void findsTheHighestOfSeveralOlderVersions() throws IOException {
        file("ComplementaryReimagined_r5.7.0.zip");
        Path highest = file("ComplementaryUnbound_r5.9.1.zip");
        file("ComplementaryReimagined_r5.8.2.zip");

        assertEquals(highest, comparator().findHighestOlderComplementaryVersion());
    }

    @Test
    void comparesOlderVersionsNumerically() throws IOException {
        file("ComplementaryReimagined_r4.9.zip");
        Path highest = file("ComplementaryReimagined_r4.10.zip");

        // A text comparison would rank 4.9 above 4.10
        assertEquals(highest, comparator().findHighestOlderComplementaryVersion());
    }

    @Test
    void olderFoldersCountAsWellAsZips() throws IOException {
        Path folder = folder("ComplementaryReimagined_r5.9.2");
        file("ComplementaryReimagined_r5.5.0.zip");

        assertEquals(folder, comparator().findHighestOlderComplementaryVersion());
    }

    @Test
    void sameNewerPatchedDevAndUnversionedPacksAreNotOlderVersions() throws IOException {
        file("ComplementaryReimagined_r5.9.3.zip");
        file("ComplementaryReimagined_r6.0.0.zip");
        file("ComplementaryReimagined_r5.8.0 + EuphoriaPatches_1.9.0.zip");
        file("ComplementaryReimagined_r5.8.0_dev1.zip");
        file("ComplementaryReimagined_r5.8.0.rar");
        file("OtherShader_r5.0.0.zip");
        file("Complementary-without-a-version.zip");

        assertNull(comparator().findHighestOlderComplementaryVersion());
    }

    @Test
    void noOlderVersionInAnEmptyOrMissingFolder() {
        assertNull(comparator().findHighestOlderComplementaryVersion());
        assertNull(new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", shaderpacks.resolve("missing")).findHighestOlderComplementaryVersion());
    }

    // ---- findDevOnlyComplementaryVersion ----

    @Test
    void findsTheHighestDevVersionWhenThereIsNoRelease() throws IOException {
        file("ComplementaryReimagined_r5.9.4_dev1.zip");
        Path highest = file("ComplementaryUnbound_r5.10_dev1.zip");
        file("ComplementaryReimagined_r5.9.0_test.zip");

        assertEquals(highest, comparator().findDevOnlyComplementaryVersion());
    }

    @Test
    void aReleaseVersionMeansItIsNotADevOnlySetup() throws IOException {
        file("ComplementaryReimagined_r5.10_dev1.zip");
        file("ComplementaryReimagined_r5.9.3.zip");

        assertNull(comparator().findDevOnlyComplementaryVersion());
    }

    @Test
    void aReleaseFolderAlsoMeansItIsNotADevOnlySetup() throws IOException {
        file("ComplementaryReimagined_r5.10_dev1.zip");
        folder("ComplementaryReimagined_r5.8.0");

        assertNull(comparator().findDevOnlyComplementaryVersion());
    }

    @Test
    void patchedPacksAndUnversionedPacksDoNotCountAsReleases() throws IOException {
        Path dev = file("ComplementaryReimagined_r5.10_dev1.zip");
        folder("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5");
        folder("Complementary Reimagined");

        assertEquals(dev, comparator().findDevOnlyComplementaryVersion());
    }

    @Test
    void otherShadersAndOtherFileTypesAreIgnoredForDevVersions() throws IOException {
        file("OtherShader_r5.10_dev1.zip");
        file("ComplementaryReimagined_r5.10_dev1.rar");
        file("ComplementaryReimagined_r5.10_dev1.txt");

        assertNull(comparator().findDevOnlyComplementaryVersion());
    }

    @Test
    void noDevVersionsAtAll() throws IOException {
        assertNull(comparator().findDevOnlyComplementaryVersion(), "empty folder");
        assertNull(new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", shaderpacks.resolve("missing")).findDevOnlyComplementaryVersion());
        assertNull(new ShaderVersionComparator("Complementary", "EuphoriaPatches", "_r5.9.3", null).findDevOnlyComplementaryVersion());
    }
}
