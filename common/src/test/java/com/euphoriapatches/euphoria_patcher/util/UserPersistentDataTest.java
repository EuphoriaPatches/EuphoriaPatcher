package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import com.euphoriapatches.euphoria_patcher.config.Config;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData.DataField;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData.PersistentShaderData;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData.SaveData;
import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the small data file that remembers things about the user. It lives in a private temporary config folder, see
 * {@link TestEnvironment}.
 */
class UserPersistentDataTest {

    private static Path dataFile() {
        return Config.CONFIG_DIR.resolve(".data.json");
    }

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    @AfterEach
    void removeTheDataFile() {
        UserPersistentData.deleteDataFile();
    }

    private static String readDataFile() throws IOException {
        return new String(Files.readAllBytes(dataFile()), StandardCharsets.UTF_8);
    }

    // ---- loading and saving ----

    @Test
    void withoutAFileEverythingIsUnset() {
        assertFalse(UserPersistentData.dataFileExists());

        PersistentShaderData data = UserPersistentData.load();

        assertNull(data.styleReimagined);
        assertNull(data.styleUnbound);
        assertNull(data.shaderHash);
        assertNull(data.EPVersion);
        assertNull(data.timesSettingsChanged);
        assertFalse(UserPersistentData.dataFileExists(), "loading must not create the file");
    }

    @Test
    void savedValuesOfEveryTypeComeBack() {
        UserPersistentData.save(
                SaveData.of(DataField.STYLE_REIMAGINED, true),
                SaveData.of(DataField.STYLE_UNBOUND, false),
                SaveData.of(DataField.SHADER_HASH, "abc123"),
                SaveData.of(DataField.TIMES_SETTINGS_CHANGED, 7));

        PersistentShaderData data = UserPersistentData.load();

        assertEquals(Boolean.TRUE, data.styleReimagined);
        assertEquals(Boolean.FALSE, data.styleUnbound);
        assertEquals("abc123", data.shaderHash);
        assertEquals(Integer.valueOf(7), data.timesSettingsChanged);
        assertTrue(UserPersistentData.dataFileExists());
    }

    @Test
    void savingOneFieldKeepsTheOthers() {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, "abc"), SaveData.of(DataField.STYLE_UNBOUND, true));

        UserPersistentData.save(SaveData.of(DataField.EP_VERSION, "1.10.5"));

        PersistentShaderData data = UserPersistentData.load();
        assertEquals("abc", data.shaderHash);
        assertEquals(Boolean.TRUE, data.styleUnbound);
        assertEquals("1.10.5", data.EPVersion);
    }

    @Test
    void savingAgainOverwritesAFieldAndCanBeDoneRepeatedly() {
        for (int i = 1; i <= 3; i++) {
            UserPersistentData.save(SaveData.of(DataField.TIMES_SETTINGS_CHANGED, i));
            assertEquals(Integer.valueOf(i), UserPersistentData.load().timesSettingsChanged);
        }
    }

    @Test
    void savingNothingDoesNotCreateTheFile() {
        UserPersistentData.save();
        UserPersistentData.save((SaveData[]) null);

        assertFalse(UserPersistentData.dataFileExists());
    }

    @Test
    void unknownKeysInTheFileAreRemovedWhenSaving() throws IOException {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, "abc"));
        String withExtra = readDataFile().replace("{", "{\n  \"oldRemovedSetting\": 5,");
        Files.write(dataFile(), withExtra.getBytes(StandardCharsets.UTF_8));

        UserPersistentData.save(SaveData.of(DataField.STYLE_UNBOUND, true));

        String saved = readDataFile();
        assertFalse(saved.contains("oldRemovedSetting"));
        assertTrue(saved.contains("shaderHash"));
    }

    @Test
    void aBrokenFileLoadsAsEmptyDataInsteadOfFailing() throws IOException {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, "abc"));
        Files.write(dataFile(), "{ this is not json".getBytes(StandardCharsets.UTF_8));

        PersistentShaderData data = UserPersistentData.load();

        assertNull(data.shaderHash);
    }

    @Test
    void aBrokenFileIsReplacedByTheNextSave() throws IOException {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, "abc"));
        Files.write(dataFile(), "[1, 2, 3]".getBytes(StandardCharsets.UTF_8));

        UserPersistentData.save(SaveData.of(DataField.STYLE_UNBOUND, true));

        assertEquals(Boolean.TRUE, UserPersistentData.load().styleUnbound);
    }

    @Test
    void aSaveIsVisibleToTheNextLoadEvenAfterAnEarlierLoad() {
        UserPersistentData.save(SaveData.of(DataField.EP_VERSION, "1.0.0"));
        assertEquals("1.0.0", UserPersistentData.load().EPVersion);

        UserPersistentData.save(SaveData.of(DataField.EP_VERSION, "2.0.0"));

        assertEquals("2.0.0", UserPersistentData.load().EPVersion);
    }

    @Test
    void deletingTheFileResetsEverything() {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, "abc"));
        assertEquals("abc", UserPersistentData.load().shaderHash);

        UserPersistentData.deleteDataFile();

        assertFalse(UserPersistentData.dataFileExists());
        assertNull(UserPersistentData.load().shaderHash);
    }

    @Test
    void deletingWithoutAFileDoesNotFail() {
        assertDoesNotThrow(UserPersistentData::deleteDataFile);
    }

    @Test
    void theOldVisibleDataFileIsRemoved() throws IOException {
        Path legacy = Config.CONFIG_DIR.resolve("data.json");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, "{}".getBytes(StandardCharsets.UTF_8));

        UserPersistentData.dataFileExists();

        assertFalse(Files.exists(legacy));
    }

    // ---- convenience methods ----

    @Test
    void savesAndResetsTheShaderStyles() {
        UserPersistentData.saveShaderStyles(true, false);
        assertEquals(Boolean.TRUE, UserPersistentData.load().styleReimagined);
        assertEquals(Boolean.FALSE, UserPersistentData.load().styleUnbound);

        UserPersistentData.resetShaderStyles();

        assertEquals(Boolean.FALSE, UserPersistentData.load().styleReimagined);
        assertEquals(Boolean.FALSE, UserPersistentData.load().styleUnbound);
    }

    @Test
    void countsHowOftenSettingsWereChanged() {
        assertEquals(0, UserPersistentData.getTimesSettingsChanged());

        UserPersistentData.incrementTimesSettingsChanged();
        UserPersistentData.incrementTimesSettingsChanged();

        assertEquals(2, UserPersistentData.getTimesSettingsChanged());
    }

    @Test
    void theFirstOptionsTimestampIsOnlyRecordedOnce() {
        UserPersistentData.recordFirstEPOptionsTimestampIfAbsent();
        String first = UserPersistentData.load().firstEPOptionsTimestamp;
        assertNotNull(first);

        UserPersistentData.recordFirstEPOptionsTimestampIfAbsent();

        assertEquals(first, UserPersistentData.load().firstEPOptionsTimestamp);
    }

    // ---- alternative shader names ----

    @Test
    void rememberedAlternativeNamesAreFoundAgain() {
        UserPersistentData.rememberAlternativeShaderNames("Name One", "Name Two");

        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("Name One"));
        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("Name Two"));
        assertFalse(UserPersistentData.hasKnownAlternativeShaderName("Name Three"));
        assertEquals(new HashSet<>(Arrays.asList("Name One", "Name Two")), UserPersistentData.getKnownAlternativeShaderNames());
    }

    @Test
    void alternativeNamesIgnoreSurroundingSpacesAndBlankNames() {
        UserPersistentData.rememberAlternativeShaderNames("  Spaced  ", "", "   ", null);

        assertTrue(UserPersistentData.hasKnownAlternativeShaderName("Spaced"));
        assertTrue(UserPersistentData.hasKnownAlternativeShaderName(" Spaced "));
        assertFalse(UserPersistentData.hasKnownAlternativeShaderName(""));
        assertFalse(UserPersistentData.hasKnownAlternativeShaderName(null));
        assertEquals(1, UserPersistentData.getKnownAlternativeShaderNames().size());
    }

    @Test
    void rememberingTheSameNameAgainDoesNotChangeAnything() {
        UserPersistentData.rememberAlternativeShaderNames("Same");
        UserPersistentData.rememberAlternativeShaderNames("Same");

        assertEquals(1, UserPersistentData.getKnownAlternativeShaderNames().size());
    }

    @Test
    void rememberingNothingDoesNotCreateTheFile() {
        UserPersistentData.rememberAlternativeShaderNames();
        UserPersistentData.rememberAlternativeShaderNames((String[]) null);

        assertFalse(UserPersistentData.dataFileExists());
    }

    @Test
    void pruningKeepsOnlyTheNamesThatAreStillConfigured() {
        UserPersistentData.rememberAlternativeShaderNames("Keep", "Drop", "Also Keep");

        UserPersistentData.pruneAlternativeShaderNames(new HashSet<>(Arrays.asList("Keep", "Also Keep", "Never Remembered")));

        Set<String> expected = new HashSet<>(Arrays.asList("Keep", "Also Keep"));
        assertEquals(expected, UserPersistentData.getKnownAlternativeShaderNames());
    }

    @Test
    void pruningWithNoConfiguredNamesForgetsAll() {
        UserPersistentData.rememberAlternativeShaderNames("A", "B");

        UserPersistentData.pruneAlternativeShaderNames(null);

        assertTrue(UserPersistentData.getKnownAlternativeShaderNames().isEmpty());
    }

    @Test
    void pruningWhenNothingChangesDoesNotCreateTheFile() {
        UserPersistentData.pruneAlternativeShaderNames(new HashSet<String>());

        assertFalse(UserPersistentData.dataFileExists());
    }

    // ---- patch version check ----

    @Test
    void theFirstVersionCheckStoresTheVersionAndReportsNoChange() {
        assertFalse(UserPersistentData.isIncorrectVersion());

        assertEquals(EuphoriaPatcher.PATCH_VERSION.replace("_", ""), UserPersistentData.load().EPVersion);
    }

    @Test
    void theSameVersionIsReportedAsCorrect() {
        UserPersistentData.isIncorrectVersion();

        assertFalse(UserPersistentData.isIncorrectVersion());
    }

    @Test
    void aDifferentStoredVersionIsReportedOnceAndUpdated() {
        UserPersistentData.save(SaveData.of(DataField.EP_VERSION, "0.0.1"));

        assertTrue(UserPersistentData.isIncorrectVersion());
        assertEquals(EuphoriaPatcher.PATCH_VERSION.replace("_", ""), UserPersistentData.load().EPVersion);
        assertFalse(UserPersistentData.isIncorrectVersion(), "the second check sees the updated version");
    }

    @Test
    void aFileWithoutAVersionIsReportedAsIncorrect() {
        UserPersistentData.save(SaveData.of(DataField.STYLE_UNBOUND, true));

        assertTrue(UserPersistentData.isIncorrectVersion());
        assertEquals(EuphoriaPatcher.PATCH_VERSION.replace("_", ""), UserPersistentData.load().EPVersion);
    }

    // ---- shaderpacks folder hash ----

    private static String expectedHash() {
        return HashUtils.calculateSHA256(ModLoaderSpecifics.defaultShaderpacks().toString());
    }

    @Test
    void theFirstHashCheckStoresTheHash() {
        UserPersistentData.validateShaderDataHash();

        assertEquals(expectedHash(), UserPersistentData.load().shaderHash);
    }

    @Test
    void aMatchingHashKeepsAllTheData() {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, expectedHash()), SaveData.of(DataField.STYLE_UNBOUND, true));

        UserPersistentData.validateShaderDataHash();

        assertEquals(Boolean.TRUE, UserPersistentData.load().styleUnbound);
    }

    @Test
    void aFileWithoutAHashGetsOneAndKeepsItsData() {
        UserPersistentData.save(SaveData.of(DataField.STYLE_UNBOUND, true));

        UserPersistentData.validateShaderDataHash();

        assertEquals(expectedHash(), UserPersistentData.load().shaderHash);
        assertEquals(Boolean.TRUE, UserPersistentData.load().styleUnbound);
    }

    @Test
    void aRedirectedShaderpacksFolderIsNotTakenForAnotherUser() {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, expectedHash()), SaveData.of(DataField.STYLE_UNBOUND, true));
        TestEnvironment.redirectShaderpacksTo(TestEnvironment.configDirectory().resolve("redirected-shaderpacks"));
        try {
            assertNotEquals(ModLoaderSpecifics.defaultShaderpacks(), ModLoaderSpecifics.shaderpacks(), "the redirect has to be active, or this test proves nothing");

            UserPersistentData.validateShaderDataHash();

            assertEquals(Boolean.TRUE, UserPersistentData.load().styleUnbound, "profiles sharing a game folder must not reset each other's data");
            assertEquals(expectedHash(), UserPersistentData.load().shaderHash);
        } finally {
            TestEnvironment.clearShaderpacksRedirect();
        }
    }

    @Test
    void aHashFromAnotherUserDropsTheirDataAndStartsFresh() {
        UserPersistentData.save(SaveData.of(DataField.SHADER_HASH, "hash-of-some-other-users-folder"), SaveData.of(DataField.STYLE_UNBOUND, true));

        UserPersistentData.validateShaderDataHash();

        PersistentShaderData data = UserPersistentData.load();
        assertEquals(expectedHash(), data.shaderHash);
        assertNull(data.styleUnbound, "everything from the other user's file has to be gone");
    }
}
