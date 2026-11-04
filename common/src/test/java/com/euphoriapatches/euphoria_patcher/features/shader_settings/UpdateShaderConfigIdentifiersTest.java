package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the markers that are written into the settings files of Euphoria Patches shaders: one line that says it is
 * such a file, and one that says for which version. The files are read back as lines, so the line separator of the
 * system does not matter.
 */
class UpdateShaderConfigIdentifiersTest {
    private static final String MAIN = "AAA_THIS_IS_A_EUPHORIA_PATCHES_SETTINGS_FILE=true";
    private static final String V1_9_0 = "AAB_FOR_EUPHORIA_PATCHES_VERSION_1_9_0=true";
    private static final String V1_10_5 = "AAB_FOR_EUPHORIA_PATCHES_VERSION_1_10_5=true";
    private static final FileTime LONG_AGO = FileTime.fromMillis(1_000_000_000_000L);

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    private Path settings(String... lines) throws IOException {
        Path file = tempDir.resolve("settings.txt");
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, LONG_AGO);
        return file;
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    private static void assertUntouched(Path file) throws IOException {
        assertEquals(LONG_AGO, Files.getLastModifiedTime(file), "the file must not be written when nothing changes");
    }

    // ---- adding the markers ----

    @Test
    void aPlainSettingsFileGetsBothMarkersAtTheTopAndKeepsItsSettingsInOrder() throws IOException {
        Path file = settings("SHADOW_QUALITY=2", "CLOUD_STYLE=3", "", "WAVING_LEAVES=true");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2", "CLOUD_STYLE=3", "", "WAVING_LEAVES=true"), lines(file));
    }

    @Test
    void theCommentsStayAboveTheMarkers() throws IOException {
        Path file = settings("#Tue Oct 28 12:00:00 CET 2026", "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList("#Tue Oct 28 12:00:00 CET 2026", MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void withoutAVersionToAddOnlyTheMainMarkerIsAdded() throws IOException {
        Path file = settings("SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, null, false);

        assertEquals(Arrays.asList(MAIN, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void withoutAVersionToAddTheExistingVersionMarkerIsKept() throws IOException {
        Path file = settings("SHADOW_QUALITY=2", V1_9_0);

        UpdateShaderConfig.addIdentifierToSettingsFile(file, null, false);

        assertEquals(Arrays.asList(MAIN, V1_9_0, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void aNewVersionReplacesTheOldVersionMarker() throws IOException {
        Path file = settings(MAIN, V1_9_0, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void markersWithSpacesAroundThemStillCountAsPresent() throws IOException {
        Path file = settings("  " + MAIN + "  ", "  " + V1_10_5, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertUntouched(file);
    }

    @Test
    void markersWithSpacesAroundThemAreNotCopiedWhenTheFileIsRewrittenAnyway() throws IOException {
        Path file = settings("SHADOW_QUALITY=2", "  " + V1_10_5 + "  ");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void markersThatAreNotAtTheTopAreLeftWhereTheyAre() throws IOException {
        // Iris saves the settings in any order, so the order of the markers does not matter
        Path file = settings("SHADOW_QUALITY=2", V1_10_5, "CLOUD_STYLE=3", MAIN);

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertUntouched(file);
    }

    @Test
    void severalVersionMarkersAreCollapsedToTheHighestOne() throws IOException {
        // 1.10.5 is higher than 1.9.0 although "9" is higher than "1" as text, and it is not the first one in the file
        Path file = settings(MAIN, V1_9_0, V1_10_5, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, null, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void severalVersionMarkersAreCollapsedToTheGivenVersion() throws IOException {
        Path file = settings(MAIN, V1_10_5, V1_9_0, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_9_0, false);

        assertEquals(Arrays.asList(MAIN, V1_9_0, "SHADOW_QUALITY=2"), lines(file));
    }

    // ---- leaving a correct file alone ----

    @Test
    void aFileThatIsAlreadyRightIsNotWritten() throws IOException {
        Path file = settings("#comment", MAIN, V1_10_5, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertUntouched(file);
    }

    @Test
    void aFileWithTheMainMarkerIsNotWrittenWhenNoVersionIsWanted() throws IOException {
        Path file = settings(MAIN, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, null, false);

        assertUntouched(file);
    }

    @Test
    void aFileWithTheOnlySettingBeingTheMarkerIsAlsoRight() throws IOException {
        Path file = settings(MAIN);

        UpdateShaderConfig.addIdentifierToSettingsFile(file, null, false);

        assertUntouched(file);
    }

    @Test
    void aMissingFileIsNotCreated() {
        Path missing = tempDir.resolve("missing.txt");

        UpdateShaderConfig.addIdentifierToSettingsFile(missing, V1_10_5, true);

        assertFalse(Files.exists(missing));
    }

    @Test
    void aReadOnlyFileIsNotChanged() throws IOException {
        Path file = settings("SHADOW_QUALITY=2");
        assertTrue(file.toFile().setReadOnly());
        try {
            Assumptions.assumeFalse(Files.isWritable(file), "this system still lets the file be written, e.g. when running as root");

            UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

            assertEquals(Collections.singletonList("SHADOW_QUALITY=2"), lines(file));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    // ---- converting old settings on the way ----

    @Test
    void oldSettingsAreConvertedWhenAskedFor() throws IOException {
        Path file = settings("BLOOM=false", "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, true);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "BLOOM_ENABLED=-1", "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void oldSettingsAreLeftAloneWhenNotAskedToConvert() throws IOException {
        Path file = settings("BLOOM=false");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "BLOOM=false"), lines(file));
    }

    @Test
    void aConvertedSettingIsWrittenEvenWhenTheMarkersWereAlreadyRight() throws IOException {
        Path file = settings(MAIN, V1_10_5, "BLOOM=false");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, true);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "BLOOM_ENABLED=-1"), lines(file));
    }

    @Test
    void aFileWithNothingToConvertIsNotWrittenWhenConverting() throws IOException {
        Path file = settings(MAIN, V1_10_5, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, true);

        assertUntouched(file);
    }

    @Test
    void nonAsciiTextInTheFileSurvives() throws IOException {
        String text = "# caf" + (char) 0xE9 + " " + (char) 0x65E5 + (char) 0x672C;
        Path file = settings(text, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(text, MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    // ---- a file that Iris is not done with, or that is not a settings file at all ----

    @Test
    void anEmptyFileIsNotWritten() throws IOException {
        Path file = settings();

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(0, Files.size(file));
        assertUntouched(file);
    }

    @Test
    void aFileWithOnlyCommentsAndBlankLinesIsNotWritten() throws IOException {
        Path file = settings("#Tue Oct 28 12:00:00 CET 2026", "", "  # another comment");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList("#Tue Oct 28 12:00:00 CET 2026", "", "  # another comment"), lines(file));
        assertUntouched(file);
    }

    @Test
    void noTempFileIsLeftBehind() throws IOException {
        Path file = settings("SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(Collections.singletonList("settings.txt"),
                files.map(p -> p.getFileName().toString()).collect(Collectors.toList()));
        }
    }

    // ---- reading the version back out of a file ----

    @Test
    void theVersionIsReadFromAFileWhateverTheOrderOfTheMarkers() throws IOException {
        assertEquals("_1.10.5", UpdateShaderConfig.getEuphoriaPatchesVersionFromFile(settings(MAIN, V1_10_5, "A=1")));
        assertEquals("_1.10.5", UpdateShaderConfig.getEuphoriaPatchesVersionFromFile(settings("A=1", V1_10_5, "B=2", MAIN)));
        assertEquals("_1.10.5", UpdateShaderConfig.getEuphoriaPatchesVersionFromFile(settings(V1_9_0, MAIN, V1_10_5)));
    }

    @Test
    void aFileWithTheMainMarkerButNoVersionIsVersionOnePointZero() throws IOException {
        assertEquals("_1.0.0", UpdateShaderConfig.getEuphoriaPatchesVersionFromFile(settings("A=1", MAIN)));
    }

    @Test
    void aFileWithoutTheMainMarkerHasNoVersion() throws IOException {
        assertNull(UpdateShaderConfig.getEuphoriaPatchesVersionFromFile(settings("A=1", V1_10_5)));
    }

    // ---- the settings that Iris is about to save ----

    private static Properties properties(String... keysAndValues) {
        Properties properties = new Properties();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            properties.setProperty(keysAndValues[i], keysAndValues[i + 1]);
        }
        return properties;
    }

    private static final String MAIN_KEY = "AAA_THIS_IS_A_EUPHORIA_PATCHES_SETTINGS_FILE";
    private static final String V1_9_0_KEY = "AAB_FOR_EUPHORIA_PATCHES_VERSION_1_9_0";
    private static final String V1_10_5_KEY = "AAB_FOR_EUPHORIA_PATCHES_VERSION_1_10_5";

    @Test
    void bothIdentifiersAreAddedToTheSettingsThatIrisSaves() {
        Properties settings = properties("SHADOW_QUALITY", "2");

        UpdateShaderConfig.addIdentifiersToProperties(settings, V1_10_5);

        assertEquals(properties("SHADOW_QUALITY", "2", MAIN_KEY, "true", V1_10_5_KEY, "true"), settings);
    }

    @Test
    void anOldVersionIdentifierIsReplacedInTheSettingsThatIrisSaves() {
        Properties settings = properties("SHADOW_QUALITY", "2", MAIN_KEY, "true", V1_9_0_KEY, "true");

        UpdateShaderConfig.addIdentifiersToProperties(settings, V1_10_5);

        assertEquals(properties("SHADOW_QUALITY", "2", MAIN_KEY, "true", V1_10_5_KEY, "true"), settings);
    }

    @Test
    void theHighestExistingVersionIdentifierIsKeptWhenThereIsNoVersionToAdd() {
        Properties settings = properties("SHADOW_QUALITY", "2", V1_9_0_KEY, "true", V1_10_5_KEY, "true");

        UpdateShaderConfig.addIdentifiersToProperties(settings, null);

        assertEquals(properties("SHADOW_QUALITY", "2", MAIN_KEY, "true", V1_10_5_KEY, "true"), settings);
    }

    @Test
    void onlyTheMainIdentifierIsAddedWithoutAnyVersion() {
        Properties settings = properties("SHADOW_QUALITY", "2");

        UpdateShaderConfig.addIdentifiersToProperties(settings, null);

        assertEquals(properties("SHADOW_QUALITY", "2", MAIN_KEY, "true"), settings);
    }

    @Test
    void settingsThatIrisSavesAreLeftAloneWhenTheyAreEmpty() {
        // Iris deletes the file for empty settings, so it must not be created with only the identifiers in it
        Properties settings = new Properties();

        UpdateShaderConfig.addIdentifiersToSavedSettings(tempDir.resolve("Euphoria-Patches.txt"), settings);

        assertTrue(settings.isEmpty());
    }

    @Test
    void identifiersLoadedFromTheOldFileAreRemovedWhenNoSettingsAreLeft() {
        // Iris loads the old file and drops the settings that are back to default, our identifiers are all that remain
        Properties settings = properties(MAIN_KEY, "true", V1_10_5_KEY, "true");

        UpdateShaderConfig.addIdentifiersToSavedSettings(tempDir.resolve("Euphoria-Patches.txt"), settings);

        assertTrue(settings.isEmpty());
    }

    // ---- the version marker text ----

    @Test
    void theVersionMarkerIsMadeFromTheVersionWithUnderscores() {
        assertEquals(V1_10_5, UpdateShaderConfig.createVersionIdentifier("1.10.5"));
        assertEquals("AAB_FOR_EUPHORIA_PATCHES_VERSION_2_0_0=true", UpdateShaderConfig.createVersionIdentifier("2.0.0"));
    }

    @Test
    void theVersionComesBackOutOfAMarkerInTheFormatOfThePatchVersion() {
        assertEquals("_1.10.5", UpdateShaderConfig.extractVersionFromIdentifier(V1_10_5));
        assertEquals("_1.9.0", UpdateShaderConfig.extractVersionFromIdentifier(V1_9_0));
    }

    @Test
    void textThatIsNoVersionMarkerHasNoVersion() {
        assertNull(UpdateShaderConfig.extractVersionFromIdentifier(MAIN));
        assertNull(UpdateShaderConfig.extractVersionFromIdentifier("AAB_FOR_EUPHORIA_PATCHES_VERSION_1_10_5=false"));
        assertNull(UpdateShaderConfig.extractVersionFromIdentifier("SHADOW_QUALITY=2"));
        assertNull(UpdateShaderConfig.extractVersionFromIdentifier(""));
    }
}
