package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the extra copies of the patched shader that the user can ask for with alternative names. The copies are real
 * folders in a temporary shaderpacks folder, what is remembered about them goes to the data file of
 * {@link TestEnvironment}.
 */
class ShaderNamingServiceAlternativeNamesTest {
    private static final String COMMON = "shaders/lib/common.glsl";
    private static final String MY_FILE = "shaders/lib/misc/myFile.glsl";
    private static final String PATCHED = "ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5";

    @TempDir
    Path shaderpacks;

    private ShaderNamingService service;
    private Path patched;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    @AfterEach
    void forgetEverythingRemembered() {
        UserPersistentData.deleteDataFile();
    }

    @BeforeEach
    void setUp() throws IOException {
        ShaderDetector detector = new ShaderDetector("Complementary", "EuphoriaPatches", "_r5.9.3", "_1.10.5", COMMON, MY_FILE, shaderpacks);
        service = new ShaderNamingService("Complementary", "EuphoriaPatches", "_r5.9.3", "_1.10.5", COMMON, MY_FILE, shaderpacks, detector);
        patched = Files.createDirectories(shaderpacks.resolve(PATCHED));
        write(patched.resolve(MY_FILE), "// Euphoria Patches 1.10.5\nsource-content");
        write(patched.resolve("shaders/lib/EuphoriaPatches_settings.glsl"), "settings");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private Path copy(String name) {
        return shaderpacks.resolve(name);
    }

    private long folderCount() throws IOException {
        try (java.util.stream.Stream<Path> children = Files.list(shaderpacks)) {
            return children.count();
        }
    }

    // ---- createAlternativeShaderNames ----

    @Test
    void everyConfiguredNameGetsACopyWithThePlaceholdersFilledIn() throws IOException {
        service.createAlternativeShaderNames(patched, false, "Mine {baseVersion}, Other {patchVersion}");

        // The underscores of the version fields are removed
        assertEquals("source-content", read(copy("Mine r5.9.3").resolve(MY_FILE)).split("\n")[1]);
        assertTrue(Files.isDirectory(copy("Other 1.10.5")));
        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("Mine r5.9.3"));
        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("Other 1.10.5"));
    }

    @Test
    void blankEntriesAndSurroundingSpacesAreIgnored() throws IOException {
        service.createAlternativeShaderNames(patched, false, "A, , ,   B  ,");

        assertTrue(Files.isDirectory(copy("A")));
        assertTrue(Files.isDirectory(copy("B")));
        assertEquals(3, folderCount(), "only the patched shader and the two copies");
        assertEquals(new HashSet<>(Arrays.asList("A", "B")), UserPersistentData.getKnownAlternativeShaderNames());
    }

    @Test
    void namesWithCharactersThatAreIllegalInFileNamesAreSkipped() throws IOException {
        service.createAlternativeShaderNames(patched, false, "bad:name,what?,a/b,quo\"te,pipe|name,star*,fine");

        assertTrue(Files.isDirectory(copy("fine")));
        assertEquals(2, folderCount(), "only the patched shader and the one valid copy");
        assertEquals(new HashSet<>(Arrays.asList("fine")), UserPersistentData.getKnownAlternativeShaderNames());
    }

    @Test
    void anEmptyConfigCreatesNothingAndForgetsTheEarlierNames() throws IOException {
        UserPersistentData.rememberAlternativeShaderNames("Old");

        service.createAlternativeShaderNames(patched, false, "");

        assertEquals(1, folderCount());
        assertTrue(UserPersistentData.getKnownAlternativeShaderNames().isEmpty());
    }

    @Test
    void namesThatAreNoLongerConfiguredAreForgotten() throws IOException {
        service.createAlternativeShaderNames(patched, false, "A,B");

        service.createAlternativeShaderNames(patched, false, "A");

        assertEquals(new HashSet<>(Arrays.asList("A")), UserPersistentData.getKnownAlternativeShaderNames());
        assertTrue(Files.isDirectory(copy("B")), "the copy itself is the user's to delete, it is not removed");
    }

    @Test
    void aCopyTheUserDeletedIsNotCreatedAgainOnceTheShaderIsInstalled() throws IOException {
        service.createAlternativeShaderNames(patched, false, "A");
        org.apache.commons.io.FileUtils.deleteDirectory(copy("A").toFile());

        service.createAlternativeShaderNames(patched, true, "A");

        assertFalse(Files.exists(copy("A")));
        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("A"), "the name stays remembered while it is configured");
    }

    @Test
    void aMissingCopyIsCreatedAgainWhenTheShaderIsNotInstalledYet() throws IOException {
        service.createAlternativeShaderNames(patched, false, "A");
        org.apache.commons.io.FileUtils.deleteDirectory(copy("A").toFile());

        service.createAlternativeShaderNames(patched, false, "A");

        assertTrue(Files.isDirectory(copy("A")));
    }

    @Test
    void aValidCopyIsLeftAloneOnceTheShaderIsInstalled() throws IOException {
        service.createAlternativeShaderNames(patched, false, "A");
        write(copy("A").resolve("user-file.txt"), "mine");

        service.createAlternativeShaderNames(patched, true, "A");

        assertEquals("mine", read(copy("A").resolve("user-file.txt")));
    }

    @Test
    void aCorruptedCopyIsRegeneratedOnceTheShaderIsInstalled() throws IOException {
        service.createAlternativeShaderNames(patched, false, "A");
        Files.delete(copy("A").resolve("shaders/lib/EuphoriaPatches_settings.glsl"));
        write(copy("A").resolve("stale.txt"), "left over");

        service.createAlternativeShaderNames(patched, true, "A");

        assertTrue(Files.exists(copy("A").resolve("shaders/lib/EuphoriaPatches_settings.glsl")), "copied again from the patched shader");
        assertFalse(Files.exists(copy("A").resolve("stale.txt")), "the broken copy was deleted first");
    }

    // ---- createShaderCopy ----

    @Test
    void copiesTheWholeShaderAndRemembersTheName() throws IOException {
        service.createShaderCopy(patched, "Copy");

        assertEquals(read(patched.resolve(MY_FILE)), read(copy("Copy").resolve(MY_FILE)));
        assertTrue(Files.exists(copy("Copy").resolve("shaders/lib/EuphoriaPatches_settings.glsl")));
        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("Copy"));
        assertTrue(Files.exists(patched.resolve(MY_FILE)), "the original stays");
    }

    @Test
    void aCopyWithAnOlderPatchVersionIsReplaced() throws IOException {
        write(copy("Copy").resolve(MY_FILE), "// Euphoria Patches 1.9.0\nold-content");
        write(copy("Copy").resolve("old-only.txt"), "old");

        service.createShaderCopy(patched, "Copy");

        assertEquals("source-content", read(copy("Copy").resolve(MY_FILE)).split("\n")[1]);
        assertFalse(Files.exists(copy("Copy").resolve("old-only.txt")), "the old copy is deleted, not merged");
    }

    @Test
    void aCopyWithTheSameOrANewerPatchVersionIsLeftAlone() throws IOException {
        write(copy("Same").resolve(MY_FILE), "// Euphoria Patches 1.10.5\nsame-content");
        write(copy("Newer").resolve(MY_FILE), "// Euphoria Patches 1.12.0\nnewer-content");

        service.createShaderCopy(patched, "Same");
        service.createShaderCopy(patched, "Newer");

        assertEquals("same-content", read(copy("Same").resolve(MY_FILE)).split("\n")[1]);
        assertEquals("newer-content", read(copy("Newer").resolve(MY_FILE)).split("\n")[1]);
    }

    @Test
    void anExistingFolderThatIsNotAnEuphoriaPatchesShaderIsReplaced() throws IOException {
        write(copy("Other").resolve(MY_FILE), "// not ours\nforeign");
        write(copy("NoMarker").resolve("shaders/final.fsh"), "void main() {}");

        service.createShaderCopy(patched, "Other");
        service.createShaderCopy(patched, "NoMarker");

        assertEquals("source-content", read(copy("Other").resolve(MY_FILE)).split("\n")[1]);
        assertEquals("source-content", read(copy("NoMarker").resolve(MY_FILE)).split("\n")[1]);
        assertFalse(Files.exists(copy("NoMarker").resolve("shaders/final.fsh")));
    }
}
