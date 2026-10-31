package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iris truncates and rewrites the settings file of the shaderpack when it saves, and reads an empty file as "all
 * defaults". These tests check that the markers are never written on top of a file that Iris is in the middle of
 * writing, which would leave a file with only the markers in it.
 */
class UpdateShaderConfigRaceTest {
    private static final String MAIN = "AAA_THIS_IS_A_EUPHORIA_PATCHES_SETTINGS_FILE=true";
    private static final String V1_10_5 = "AAB_FOR_EUPHORIA_PATCHES_VERSION_1_10_5=true";
    private static final FileTime LONG_AGO = FileTime.fromMillis(1_000_000_000_000L);

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    void useShortDelays() {
        UpdateShaderConfig.retryDelaysMs = new long[]{5, 5, 5};
        UpdateShaderConfig.settleMs = 0;
    }

    @AfterEach
    void restoreDefaults() {
        UpdateShaderConfig.retryDelaysMs = new long[]{250, 500, 1000};
        UpdateShaderConfig.settleMs = 500;
        UpdateShaderConfig.writeDelayMs = 1000;
        UpdateShaderConfig.beforeFinalCheckHook = null;
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

    private List<String> filesInDirectory() throws IOException {
        try (Stream<Path> stream = Files.list(tempDir)) {
            return stream.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    // ---- a file without settings is not rewritten ----

    @Test
    void anEmptyFileIsNotWritten() throws IOException {
        Path file = settings();

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(0, Files.size(file));
        assertEquals(LONG_AGO, Files.getLastModifiedTime(file));
    }

    @Test
    void anEmptyFileThatFillsUpWhileWaitingIsMarkedOnALaterAttempt() throws IOException {
        UpdateShaderConfig.retryDelaysMs = new long[]{300, 300, 300};
        Path file = settings();
        AtomicBoolean refilled = new AtomicBoolean();
        Thread irisSave = new Thread(() -> {
            try {
                Thread.sleep(100);
                Files.write(file, Arrays.asList("SHADOW_QUALITY=2", "CLOUD_STYLE=3"), StandardCharsets.UTF_8);
                refilled.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        irisSave.start();

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertTrue(refilled.get());
        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2", "CLOUD_STYLE=3"), lines(file));
    }

    @Test
    void aFileWithOnlyCommentsAndBlankLinesIsNotWritten() throws IOException {
        Path file = settings("#Tue Oct 28 12:00:00 CET 2026", "", "  # another comment");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList("#Tue Oct 28 12:00:00 CET 2026", "", "  # another comment"), lines(file));
        assertEquals(LONG_AGO, Files.getLastModifiedTime(file));
    }

    // ---- a file that was just modified is waited for ----

    @Test
    void aFileThatWasJustModifiedIsNotWrittenWhileItKeepsChanging() throws IOException {
        UpdateShaderConfig.settleMs = 60_000;
        Path file = settings("SHADOW_QUALITY=2");
        FileTime justNow = FileTime.fromMillis(System.currentTimeMillis());
        Files.setLastModifiedTime(file, justNow);

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Collections.singletonList("SHADOW_QUALITY=2"), lines(file));
        assertEquals(justNow, Files.getLastModifiedTime(file));
    }

    @Test
    void aFileThatWasJustModifiedIsWrittenOnceItHasSettled() throws IOException {
        UpdateShaderConfig.settleMs = 150;
        UpdateShaderConfig.retryDelaysMs = new long[]{100, 100, 100};
        Path file = settings("SHADOW_QUALITY=2");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    // ---- a file that changes while it is processed is left alone ----

    @Test
    void aFileThatChangesBetweenReadingAndWritingIsProcessedAgainWithItsNewContent() throws IOException {
        Path file = settings("SHADOW_QUALITY=2");
        AtomicInteger calls = new AtomicInteger();
        UpdateShaderConfig.beforeFinalCheckHook = () -> {
            if (calls.incrementAndGet() == 1) {
                changeFile(file, "SHADOW_QUALITY=2", "CLOUD_STYLE=3");
            }
        };

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(2, calls.get());
        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2", "CLOUD_STYLE=3"), lines(file));
    }

    @Test
    void aFileThatKeepsChangingIsNeverOverwritten() throws IOException {
        Path file = settings("SHADOW_QUALITY=2");
        AtomicInteger calls = new AtomicInteger();
        UpdateShaderConfig.beforeFinalCheckHook = () -> changeFile(file, "SHADOW_QUALITY=2", "CLOUD_STYLE=" + calls.incrementAndGet());

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(4, calls.get());
        assertEquals(Arrays.asList("SHADOW_QUALITY=2", "CLOUD_STYLE=4"), lines(file));
        assertEquals(Collections.singletonList("settings.txt"), filesInDirectory());
    }

    private static void changeFile(Path file, String... newLines) {
        try {
            Files.write(file, Arrays.asList(newLines), StandardCharsets.UTF_8);
            // The size can stay the same, so make sure the time differs
            Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- the write itself ----

    @Test
    void noTempFileIsLeftBehindAfterAWrite() throws IOException {
        Path file = settings("SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
        assertEquals(Collections.singletonList("settings.txt"), filesInDirectory());
    }

    @Test
    void noTempFileIsLeftBehindWhenNothingNeedsToBeWritten() throws IOException {
        Path file = settings(MAIN, V1_10_5, "SHADOW_QUALITY=2");

        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        assertEquals(Collections.singletonList("settings.txt"), filesInDirectory());
    }

    // ---- one update per file ----

    @Test
    void schedulingTheSameFileTwiceLeavesOnePendingUpdate() throws Exception {
        UpdateShaderConfig.writeDelayMs = 300;
        Path file = settings("SHADOW_QUALITY=2");

        ScheduledFuture<?> first = UpdateShaderConfig.scheduleSettingsFileUpdate(file, V1_10_5, false);
        ScheduledFuture<?> second = UpdateShaderConfig.scheduleSettingsFileUpdate(file, V1_10_5, false);

        assertTrue(first.isCancelled());
        assertFalse(second.isCancelled());
        assertEquals(1, UpdateShaderConfig.pendingWriteCount());

        second.get(5, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
        assertEquals(0, UpdateShaderConfig.pendingWriteCount());
    }

    @Test
    void schedulingAgainRestartsTheDelay() throws Exception {
        UpdateShaderConfig.writeDelayMs = 400;
        Path file = settings("SHADOW_QUALITY=2");

        UpdateShaderConfig.scheduleSettingsFileUpdate(file, V1_10_5, false);
        Thread.sleep(250);
        ScheduledFuture<?> second = UpdateShaderConfig.scheduleSettingsFileUpdate(file, V1_10_5, false);
        Thread.sleep(250);

        // 500 ms since the first call, but only 250 ms since the second one
        assertEquals(Collections.singletonList("SHADOW_QUALITY=2"), lines(file));

        second.get(5, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(file));
    }

    @Test
    void differentFilesGetTheirOwnUpdate() throws Exception {
        UpdateShaderConfig.writeDelayMs = 200;
        Path first = settings("SHADOW_QUALITY=2");
        Path other = tempDir.resolve("other.txt");
        Files.write(other, Collections.singletonList("CLOUD_STYLE=3"), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(other, LONG_AGO);

        ScheduledFuture<?> firstUpdate = UpdateShaderConfig.scheduleSettingsFileUpdate(first, V1_10_5, false);
        ScheduledFuture<?> otherUpdate = UpdateShaderConfig.scheduleSettingsFileUpdate(other, V1_10_5, false);

        assertFalse(firstUpdate.isCancelled());
        firstUpdate.get(5, TimeUnit.SECONDS);
        otherUpdate.get(5, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(MAIN, V1_10_5, "SHADOW_QUALITY=2"), lines(first));
        assertEquals(Arrays.asList(MAIN, V1_10_5, "CLOUD_STYLE=3"), lines(other));
    }

    // ---- Iris saving while the markers are added ----

    @Test
    void theSettingsSurviveWhileIrisKeepsTruncatingAndRewritingTheFile() throws Exception {
        List<String> irisSettings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            irisSettings.add("SETTING_" + i + "=" + i);
        }
        byte[] irisContent = (String.join("\n", irisSettings) + "\n").getBytes(StandardCharsets.UTF_8);
        Path file = settings(irisSettings.toArray(new String[0]));

        AtomicBoolean irisDone = new AtomicBoolean();
        Thread iris = new Thread(() -> {
            try {
                for (int i = 0; i < 300; i++) {
                    try (OutputStream out = Files.newOutputStream(file)) { // truncates, like Properties.store
                        Thread.sleep(0, 200_000); // the file is empty for a moment
                        out.write(irisContent);
                    } catch (IOException e) {
                        // the file was being replaced at that moment, Iris would try again on its next save
                    }
                }
                Files.write(file, irisContent);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                irisDone.set(true);
            }
        });
        iris.start();

        while (!irisDone.get()) {
            UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);
        }
        iris.join();
        UpdateShaderConfig.addIdentifierToSettingsFile(file, V1_10_5, false);

        List<String> result = lines(file);
        List<String> settingsLeft = result.stream()
            .filter(l -> l.startsWith("SETTING_"))
            .collect(Collectors.toList());
        assertEquals(irisSettings, settingsLeft, "the settings must still be in the file: " + result);
        assertEquals(Arrays.asList(MAIN, V1_10_5), result.subList(0, 2));
        assertEquals(Collections.singletonList("settings.txt"), filesInDirectory());
    }
}
