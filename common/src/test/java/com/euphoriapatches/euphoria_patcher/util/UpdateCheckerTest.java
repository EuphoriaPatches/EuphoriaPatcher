package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import com.euphoriapatches.euphoria_patcher.config.ConfigHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests what the update checker decides from the version list. The network is replaced by a fake list of versions,
 * the way Modrinth returns it: newest first, one entry per mod loader.
 */
class UpdateCheckerTest {
    private static final String RECOMMENDED = "### Updated to Complementary Shaders 5.6.1 + Euphoria Patches 1.7.5 (Update recommended!)";

    private String originalMode;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    void rememberMode() {
        originalMode = ConfigHandler.updateMode;
    }

    @AfterEach
    void restoreMode() {
        ConfigHandler.updateMode = originalMode;
    }

    // ---- up to date / behind ----

    @Test
    void upToDateIsNotReportedAndNeverFetchesChangelogs() throws Exception {
        FakeVersions fake = new FakeVersions(version("1.7.4-r5.6.0-fabric", RECOMMENDED));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertFalse(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.isImportantUpdate());
        assertFalse(UpdateChecker.isPatchUpdateRecommended());
        assertFalse(UpdateChecker.shouldUserUpdate());
        assertEquals("1.7.4", UpdateChecker.getNewModVersion());
        assertEquals(1, fake.plainFetches);
        assertEquals(0, fake.changelogFetches);
    }

    @Test
    void aNewerMinorVersionIsImportantAndNeedsNoChangelog() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.8.0-r5.7.0-fabric", RECOMMENDED),
                version("1.7.5-r5.6.1-fabric", RECOMMENDED));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertTrue(UpdateChecker.isUpdateAvailable());
        assertTrue(UpdateChecker.isImportantUpdate());
        assertFalse(UpdateChecker.isPatchUpdateRecommended());
        assertTrue(UpdateChecker.shouldUserUpdate());
        assertEquals("1.8.0", UpdateChecker.getNewModVersion());
        assertEquals(0, fake.changelogFetches);
    }

    @Test
    void aNewerMajorVersionIsImportant() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.9.9", new FakeVersions(version("2.0.0-r6.0.0-fabric", null)));

        assertTrue(UpdateChecker.isImportantUpdate());
        assertTrue(UpdateChecker.shouldUserUpdate());
    }

    @Test
    void aRunningVersionAheadOfModrinthIsNotAnUpdate() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.11.0", new FakeVersions(version("1.10.5-r5.9.3-fabric", null)));

        assertFalse(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.shouldUserUpdate());
    }

    // ---- recommended patch updates ----

    @Test
    void aRecommendedPatchInTheCurrentCycleIsReported() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.7.6-r5.6.1-forge", "small fix"),
                version("1.7.5-r5.6.1-fabric", RECOMMENDED),
                version("1.7.4-r5.6.0-fabric", "older"));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertTrue(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.isImportantUpdate());
        assertTrue(UpdateChecker.isPatchUpdateRecommended());
        assertTrue(UpdateChecker.shouldUserUpdate());
        assertEquals("1.7.6", UpdateChecker.getNewModVersion());
        assertEquals(1, fake.plainFetches);
        assertEquals(1, fake.changelogFetches);
    }

    @Test
    void aPatchUpdateWithoutRecommendationIsNotReportedInImportantMode() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.7.5-r5.6.1-fabric", "another fix"));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertTrue(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.isPatchUpdateRecommended());
        assertFalse(UpdateChecker.shouldUserUpdate());
        assertEquals(1, fake.changelogFetches);
    }

    @Test
    void theRecommendationOfTheCurrentVersionItselfDoesNotCount() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.7.4-r5.6.0-fabric", RECOMMENDED));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertFalse(UpdateChecker.isPatchUpdateRecommended());
    }

    @Test
    void aRecommendationInAnotherCycleDoesNotCount() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.6.9-r5.5.0-fabric", RECOMMENDED));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertFalse(UpdateChecker.isPatchUpdateRecommended());
    }

    @Test
    void theRecommendationNeedsAllThreePartsOfTheMarker() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.7.5-r5.6.1-fabric", "This is an update recommended by nobody"));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertFalse(UpdateChecker.isPatchUpdateRecommended());
    }

    @Test
    void theMarkerIsFoundWhateverTheCapitalisationAndWhicheverLine() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.5-r5.6.1-fabric", "## Changes\n- stuff\n### UPDATED TO COMPLEMENTARY SHADERS 5.6.1 + EUPHORIA PATCHES 1.7.5 (UPDATE RECOMMENDED)\n- more"));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertTrue(UpdateChecker.isPatchUpdateRecommended());
    }

    @Test
    void aMissingChangelogIsTolerated() throws Exception {
        FakeVersions fake = new FakeVersions(version("1.7.5-r5.6.1-fabric", null));

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertTrue(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.isPatchUpdateRecommended());
    }

    // ---- update modes ----

    @Test
    void modeAllReportsEveryUpdateWithoutFetchingChangelogs() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.7.5-r5.6.1-fabric", RECOMMENDED));

        evaluate(ConfigHandler.UpdateMode.ALL, "1.7.4", fake);

        // shouldUserUpdate() is not read here: in this mode reading it would start a real check on the network
        assertTrue(UpdateChecker.isUpdateAvailable());
        assertEquals("1.7.6", UpdateChecker.getNewModVersion());
        assertEquals(1, fake.plainFetches);
        assertEquals(0, fake.changelogFetches);
    }

    @Test
    void modeAllStillSaysNothingWhenUpToDate() throws Exception {
        evaluate(ConfigHandler.UpdateMode.ALL, "1.7.4", new FakeVersions(version("1.7.4-r5.6.0-fabric", null)));

        assertFalse(UpdateChecker.isUpdateAvailable());
    }

    // ---- Complementary Shaders version ----

    @Test
    void readsTheComplementaryVersionFromTheVersionNumber() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", new FakeVersions(version("1.7.4-r5.6.1-fabric", null)));

        assertEquals("5.6.1", UpdateChecker.getComplementaryVersion());
    }

    @Test
    void comparesTheComplementaryVersionWithTheOneThisModIsBuiltFor() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", new FakeVersions(version("1.7.4-r99.0.0-fabric", null)));
        assertTrue(UpdateChecker.isNewerCompVersionAvailable());

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", new FakeVersions(version("1.7.4-r0.0.1-fabric", null)));
        assertFalse(UpdateChecker.isNewerCompVersionAvailable());
    }

    @Test
    void aVersionNumberWithoutComplementaryPartGivesNoComplementaryVersion() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", new FakeVersions(version("1.7.5", null)));

        assertNull(UpdateChecker.getComplementaryVersion());
        assertFalse(UpdateChecker.isNewerCompVersionAvailable());
        assertTrue(UpdateChecker.isUpdateAvailable());
    }

    // ---- failures ----

    @Test
    void anEmptyVersionListReportsNothing() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", new FakeVersions());

        assertFalse(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.shouldUserUpdate());
        assertNull(UpdateChecker.getNewModVersion());
        assertNull(UpdateChecker.getComplementaryVersion());
    }

    @Test
    void aFailedFetchReportsNothing() throws Exception {
        FakeVersions fake = new FakeVersions(version("1.9.0-r5.9.0-fabric", null));
        fake.failPlainFetch = true;

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertFalse(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.shouldUserUpdate());
        assertNull(UpdateChecker.getNewModVersion());
    }

    @Test
    void aFailedChangelogFetchStillReportsTheUpdateButNoRecommendation() throws Exception {
        FakeVersions fake = new FakeVersions(
                version("1.7.6-r5.6.1-fabric", "small fix"),
                version("1.7.5-r5.6.1-fabric", RECOMMENDED));
        fake.failChangelogFetch = true;

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", fake);

        assertTrue(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.isPatchUpdateRecommended());
        assertFalse(UpdateChecker.shouldUserUpdate());
    }

    @Test
    void aStaleResultIsReplacedByTheNextEvaluation() throws Exception {
        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.7.4", new FakeVersions(version("1.9.0-r5.9.0-fabric", null)));
        assertTrue(UpdateChecker.isUpdateAvailable());

        evaluate(ConfigHandler.UpdateMode.IMPORTANT, "1.9.0", new FakeVersions(version("1.9.0-r5.9.0-fabric", null)));

        assertFalse(UpdateChecker.isUpdateAvailable());
        assertFalse(UpdateChecker.isImportantUpdate());
    }

    // ---- helpers ----

    /**
     * Runs the evaluation the way the background check does. Afterwards the mode is set to NONE so that reading the
     * result never starts a real check on the network.
     */
    private static void evaluate(String mode, String currentVersion, FakeVersions fake) throws Exception {
        ConfigHandler.updateMode = mode;
        UpdateChecker.evaluate(currentVersion, fake);
        ConfigHandler.updateMode = ConfigHandler.UpdateMode.NONE;
    }

    private static JsonObject version(String versionNumber, String changelog) {
        JsonObject version = new JsonObject();
        version.addProperty("version_number", versionNumber);
        if (changelog == null) {
            version.add("changelog", JsonNull.INSTANCE);
        } else {
            version.addProperty("changelog", changelog);
        }
        return version;
    }

    /**
     * Hands out the versions like Modrinth does: without changelogs unless they are asked for.
     */
    private static final class FakeVersions implements UpdateChecker.VersionSource {
        private final JsonObject[] versions;
        int plainFetches = 0;
        int changelogFetches = 0;
        boolean failPlainFetch = false;
        boolean failChangelogFetch = false;

        FakeVersions(JsonObject... versions) {
            this.versions = versions;
        }

        @Override
        public JsonArray fetch(boolean includeChangelog) {
            if (includeChangelog) {
                changelogFetches++;
                if (failChangelogFetch) return null;
            } else {
                plainFetches++;
                if (failPlainFetch) return null;
            }

            JsonArray array = new JsonArray();
            for (JsonObject original : versions) {
                JsonObject copy = original.deepCopy();
                if (!includeChangelog) {
                    copy.remove("changelog");
                }
                array.add(copy);
            }
            return array;
        }
    }
}
