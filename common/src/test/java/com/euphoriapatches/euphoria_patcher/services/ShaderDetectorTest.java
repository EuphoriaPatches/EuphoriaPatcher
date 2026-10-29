package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import com.euphoriapatches.euphoria_patcher.io.JsonUtilReader;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector.ShaderInfo;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderPropertyReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how the detector finds out what is installed in a shaderpacks folder, with real files in a temporary folder.
 */
class ShaderDetectorTest {
    private static final String COMMON = "shaders/lib/common.glsl";
    private static final String MY_FILE = "shaders/lib/misc/myFile.glsl";
    private static final String PATCHED_REIMAGINED = "ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5";
    private static final String PATCHED_UNBOUND = "ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5";

    @TempDir
    Path shaderpacks;

    private ShaderDetector detector;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    void setUp() throws Exception {
        ShaderPropertyReader.clearCache();
        // Remembered for the whole run of the game once a dev version was seen, so every test starts without that
        Field hasAnyDevVersion = ShaderDetector.class.getDeclaredField("hasAnyDevVersion");
        hasAnyDevVersion.setAccessible(true);
        hasAnyDevVersion.setBoolean(null, false);

        detector = new ShaderDetector("Complementary", "EuphoriaPatches", "_r5.9.3", "_1.10.5", COMMON, MY_FILE, shaderpacks);
    }

    /**
     * The earlyDev tests compare with the build date that the build writes into randomUtil.json while it processes the
     * resources. Without that (a test run that skipped the Gradle build) the date is still the placeholder.
     */
    private void requireKnownBuildDate() {
        assertNotNull(detector.parseBuildDate(JsonUtilReader.getString("buildDate")),
                "randomUtil.json still has the buildDate placeholder, run the tests through Gradle");
    }

    private Path file(String name) throws IOException {
        Path file = shaderpacks.resolve(name);
        Files.write(file, "x".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private Path folder(String name) throws IOException {
        return Files.createDirectories(shaderpacks.resolve(name));
    }

    private Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private Path zipWith(String name, String entryName) throws IOException {
        Path zip = shaderpacks.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write("content".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    // ---- isBrandNameShader ----

    @Test
    void recognisesTheBaseShadersByName() throws IOException {
        assertTrue(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3.zip"), true));
        assertTrue(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryUnbound_r5.9.3.zip"), true));
        assertTrue(detector.isBrandNameShader(folder("ComplementaryReimagined_r5.9.3"), false));
    }

    @Test
    void rejectsOtherBrandsAndOtherVersions() throws IOException {
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("BSL_r5.9.3.zip"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.8.0.zip"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r6.0.zip"), true));
    }

    @Test
    void rejectsAlreadyPatchedPacksAndDevOrPreReleaseNames() throws IOException {
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve(PATCHED_REIMAGINED + ".zip"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3 copy EuphoriaPatches.zip"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3 + something.zip"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3_dev1.zip"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3_pre2.zip"), true));
    }

    @Test
    void zipsNeedTheZipEndingAndFoldersNeedToBeFolders() throws IOException {
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3.rar"), true));
        assertFalse(detector.isBrandNameShader(shaderpacks.resolve("ComplementaryReimagined_r5.9.3"), false), "does not exist as a folder");
        assertFalse(detector.isBrandNameShader(file("ComplementaryUnbound_r5.9.3"), false), "a file is not a folder");
    }

    // ---- dev versions ----

    @Test
    void aNumberedDevVersionNewerThanThePatchIsRecognisedAndMarksTheShaderAsInstalled() throws IOException {
        Path dev = file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.11.0-dev3.zip");
        ShaderInfo info = new ShaderInfo();

        assertTrue(detector.isNewerDevVersion(dev, info));

        assertTrue(info.isAlreadyInstalled);
        assertEquals(dev, info.installedDir);
    }

    @Test
    void aNumberedDevVersionThatIsNotNewerIsNotRecognised() throws IOException {
        assertFalse(detector.isNewerDevVersion(file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5-dev3.zip")), "same version as the patch");
        assertFalse(detector.isNewerDevVersion(file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.9.0-dev3.zip")), "older than the patch");
    }

    @Test
    void comparesDevVersionsNumericallyNotAsText() throws IOException {
        // 1.10.5 is the patch, a text comparison would call 1.9.9 newer than 1.10.5
        assertFalse(detector.isNewerDevVersion(file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.9.9-dev1.zip")));
        assertTrue(detector.isNewerDevVersion(file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.6-dev1.zip")));
    }

    @Test
    void anEarlyDevVersionFromTheFutureIsNewerAndOneFromThePastIsNot() throws IOException {
        requireKnownBuildDate();
        assertTrue(detector.isNewerDevVersion(file("EuphoriaPatches_earlyDev_2099-01-01.zip")));
        assertFalse(detector.isNewerDevVersion(file("EuphoriaPatches_earlyDev_2000-01-01.zip")));
    }

    @Test
    void earlyDevVersionsSetTheInstalledInfoToo() throws IOException {
        requireKnownBuildDate();
        Path dev = file("EuphoriaPatches_earlyDev_2099-01-01.zip");
        ShaderInfo info = new ShaderInfo();

        assertTrue(detector.isNewerDevVersion(dev, info));

        assertTrue(info.isAlreadyInstalled);
        assertEquals(dev, info.installedDir);
    }

    @Test
    void foldersAndOtherNamesAreNeverDevVersions() throws IOException {
        assertFalse(detector.isNewerDevVersion(folder("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.11.0-dev3.zip")), "a folder is not a file");
        assertFalse(detector.isNewerDevVersion(file("SomeOtherShader_v2.zip")));
        assertFalse(detector.isNewerDevVersion(file("ComplementaryReimagined_r5.9.3.zip")));
    }

    @Test
    void anInfoObjectIsLeftAloneWhenNothingIsFound() throws IOException {
        ShaderInfo info = new ShaderInfo();

        assertFalse(detector.isNewerDevVersion(file("SomeOtherShader_v2.zip"), info));

        assertFalse(info.isAlreadyInstalled);
        assertNull(info.installedDir);
    }

    @Test
    void noDevVersionsInstalledIsTrueForAnEmptyFolderAndFalseOnceOneIsPresent() throws IOException {
        assertTrue(detector.noDevVersionsInstalled());

        file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.11.0-dev3.zip");

        assertFalse(detector.noDevVersionsInstalled());
    }

    @Test
    void noDevVersionsInstalledIgnoresFoldersAndOrdinaryFiles() throws IOException {
        file("ComplementaryReimagined_r5.9.3.zip");
        folder("EuphoriaPatches_earlyDev_2099-01-01.zip");

        assertTrue(detector.noDevVersionsInstalled());
    }

    // ---- hasShaderpackNamed ----

    @Test
    void findsAShaderpackByItsNameWithOrWithoutZip() throws IOException {
        file("ComplementaryReimagined-Testing.zip");
        folder("FolderPack");

        assertTrue(detector.hasShaderpackNamed("ComplementaryReimagined-Testing"));
        assertTrue(detector.hasShaderpackNamed("FolderPack"));
    }

    @Test
    void theZipEndingIsMatchedIgnoringCaseButTheNameIsMatchedExactly() throws IOException {
        file("Upper.ZIP");

        assertTrue(detector.hasShaderpackNamed("Upper"));
        assertFalse(detector.hasShaderpackNamed("upper"));
        assertFalse(detector.hasShaderpackNamed("Upp"));
        assertFalse(detector.hasShaderpackNamed("Upper.zip.zip"));
        assertFalse(detector.hasShaderpackNamed("Missing"));
    }

    // ---- isEuphoriaPatchesShader ----

    @Test
    void aFolderWithTheMarkerFileIsAnEuphoriaPatchesShader() throws IOException {
        Path pack = folder("pack");
        write(pack.resolve(MY_FILE), "// Euphoria Patches 1.10.5");

        assertTrue(detector.isEuphoriaPatchesShader(pack));
    }

    @Test
    void aFolderWithoutTheMarkerFileIsNot() throws IOException {
        assertFalse(detector.isEuphoriaPatchesShader(folder("plain")));
    }

    @Test
    void zipFilesAreCheckedToo() throws IOException {
        assertTrue(detector.isEuphoriaPatchesShader(zipWith("patched.zip", MY_FILE)));
        assertFalse(detector.isEuphoriaPatchesShader(zipWith("base.zip", "shaders/other.glsl")));
    }

    @Test
    void missingPathsAndNullAreNotEuphoriaPatchesShaders() {
        assertFalse(detector.isEuphoriaPatchesShader(shaderpacks.resolve("does-not-exist")));
        assertFalse(detector.isEuphoriaPatchesShader(null));
    }

    @Test
    void theVersionIsOnlyReadFromEuphoriaPatchesShaders() throws IOException {
        Path ep = folder("ep");
        write(ep.resolve(MY_FILE), "// Euphoria Patches 1.10.5");
        write(ep.resolve("shaders/pack.json"), "{\"version\": \"1.10.5\"}");
        Path other = folder("other");
        write(other.resolve("shaders/pack.json"), "{\"version\": \"9.9.9\"}");

        assertEquals("1.10.5", detector.getEuphoriaPatchesVersionFromShader(ep));
        assertNull(detector.getEuphoriaPatchesVersionFromShader(other));
        assertEquals("9.9.9", detector.readVersionFromPackJson(other), "reading the plain version does not check for the marker");
    }

    // ---- finding already installed patched shaders ----

    @Test
    void findsACompleteInstallationByItsName() throws IOException {
        Path installed = folder(PATCHED_REIMAGINED);
        write(installed.resolve("shaders/lib/EuphoriaPatches_settings.glsl"), "// real file");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertTrue(info.isAlreadyInstalled);
        assertEquals(installed, info.installedDir);
        assertTrue(info.styleReimagined);
        assertFalse(info.styleUnbound);
    }

    @Test
    void theStyleOfAnInstallationComesFromItsName() throws IOException {
        Path installed = folder(PATCHED_UNBOUND);
        write(installed.resolve("shaders/lib/EuphoriaPatches_settings.glsl"), "// real file");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertTrue(info.isAlreadyInstalled);
        assertTrue(info.styleUnbound);
        assertFalse(info.styleReimagined);
    }

    @Test
    void anIncompleteInstallationIsCleanedUpAndNotCountedAsInstalled() throws IOException {
        Path incomplete = folder(PATCHED_REIMAGINED);
        write(incomplete.resolve("shaders/lib/common.glsl"), "half copied");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertFalse(info.isAlreadyInstalled);
        assertFalse(Files.exists(incomplete), "a patched folder without any Euphoria Patches file is removed");
    }

    @Test
    void aRenamedFolderIsFoundByItsMarkerFileAndVersion() throws IOException {
        Path renamed = folder("My Favourite Shader");
        write(renamed.resolve(MY_FILE), "// Euphoria Patches 1.10.5\nmore");
        write(renamed.resolve(COMMON), "#define SHADER_STYLE 4");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertTrue(info.isAlreadyInstalled);
        assertEquals(renamed, info.installedDir);
        assertTrue(info.styleUnbound, "no style in the name, so common.glsl decides");
        assertFalse(info.styleReimagined);
    }

    @Test
    void aRenamedFolderWithStyleInItsNameDoesNotNeedCommonGlsl() throws IOException {
        Path renamed = folder("My Reimagined Copy");
        write(renamed.resolve(MY_FILE), "// Euphoria Patches 1.10.5");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertTrue(info.isAlreadyInstalled);
        assertTrue(info.styleReimagined);
    }

    @Test
    void aRenamedFolderWithANewerVersionCountsAsInstalled() throws IOException {
        write(folder("Newer Reimagined").resolve(MY_FILE), "// Euphoria Patches 1.12.0");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertTrue(info.isAlreadyInstalled);
    }

    @Test
    void aRenamedFolderWithAnOlderVersionDoesNotCountAsInstalled() throws IOException {
        write(folder("Old Reimagined").resolve(MY_FILE), "// Euphoria Patches 1.9.0");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertFalse(info.isAlreadyInstalled, "the old version still has to be patched to the current one");
    }

    @Test
    void aMarkerFileThatDoesNotStartWithTheEuphoriaPatchesLineIsIgnored() throws IOException {
        write(folder("Copied Reimagined").resolve(MY_FILE), "// something else 1.10.5");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertFalse(info.isAlreadyInstalled);
    }

    @Test
    void devFoldersAreNotCountedAsTheInstalledRelease() throws IOException {
        write(folder("Euphoria-Patches").resolve(MY_FILE), "// Euphoria Patches 1.10.5");
        write(folder("dev3").resolve(MY_FILE), "// Euphoria Patches 1.10.5");
        write(folder("Reimagined earlyDev").resolve(MY_FILE), "// Euphoria Patches 1.10.5");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertFalse(info.isAlreadyInstalled);
    }

    @Test
    void foldersWithoutTheMarkerFileAreIgnored() throws IOException {
        folder("Complementary Reimagined base");
        write(folder("Other Shader").resolve("shaders/final.fsh"), "void main() {}");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertFalse(info.isAlreadyInstalled);
        assertTrue(Files.isDirectory(shaderpacks.resolve("Complementary Reimagined base")), "unrelated folders are never touched");
    }

    @Test
    void aNewerDevVersionCountsAsInstalledBeforeAnythingElseIsLookedAt() throws IOException {
        Path dev = file("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.11.0-dev3.zip");
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertTrue(info.isAlreadyInstalled);
        assertEquals(dev, info.installedDir);
    }

    @Test
    void anEmptyShaderpacksFolderHasNothingInstalled() {
        ShaderInfo info = new ShaderInfo();

        detector.checkForExistingPatchedShaders(info);

        assertFalse(info.isAlreadyInstalled);
        assertNull(info.installedDir);
    }
}
