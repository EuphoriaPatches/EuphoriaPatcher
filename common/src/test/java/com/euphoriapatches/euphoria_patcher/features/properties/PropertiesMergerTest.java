package com.euphoriapatches.euphoria_patcher.features.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesMergerTest {

    @TempDir
    Path tempDir;

    private Path propertiesDir() throws IOException {
        return Files.createDirectories(tempDir.resolve("shaders/properties"));
    }

    private Path target() {
        return tempDir.resolve("shaders/block.properties");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** The file names in the order they were merged, taken from the "# Source:" headers */
    private static List<String> mergedSources(String merged) {
        List<String> sources = new ArrayList<>();
        for (String line : merged.split("\n")) {
            if (line.startsWith("# Source: ")) {
                sources.add(line.substring("# Source: ".length()));
            }
        }
        return sources;
    }

    // ---- order ----

    @Test
    void mergesInTheOrderOfPropertiesOrder() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("renderlayers/z.properties"), "z=1");
        write(dir.resolve("1.7.10/a.properties"), "a=1");
        write(dir.resolve("1.8+/b.properties"), "b=1");
        write(dir.resolve("1.13+/block.5000-10000.properties"), "pattern=1");
        write(dir.resolve("tags/t.properties"), "t=1");
        write(dir.resolve("instructions/i.properties"), "i=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        assertEquals(Arrays.asList(
                "instructions/i.properties",
                "tags/t.properties",
                "1.13+/block.5000-10000.properties",
                "1.8+/b.properties",
                "1.7.10/a.properties",
                "renderlayers/z.properties"), mergedSources(read(target())));
    }

    @Test
    void insideAFolderTheNamedPatternComesFirstThenTheRestAlphabeticallyThenSubfolders() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("1.13+/zzz.properties"), "z=1");
        write(dir.resolve("1.13+/aaa.properties"), "a=1");
        write(dir.resolve("1.13+/sub/c.properties"), "c=1");
        write(dir.resolve("1.13+/block.5000-2.properties"), "second=1");
        write(dir.resolve("1.13+/block.5000-10000.properties"), "first=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        assertEquals(Arrays.asList(
                "1.13+/block.5000-10000.properties",
                "1.13+/block.5000-2.properties",
                "1.13+/aaa.properties",
                "1.13+/zzz.properties",
                "1.13+/sub/c.properties"), mergedSources(read(target())));
    }

    @Test
    void foldersWithoutOrderedChildrenAreMergedFilesFirstThenSubfoldersAlphabetically() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("1.8+/b.properties"), "b=1");
        write(dir.resolve("1.8+/a.properties"), "a=1");
        write(dir.resolve("1.8+/two/y.properties"), "y=1");
        write(dir.resolve("1.8+/one/x.properties"), "x=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        assertEquals(Arrays.asList(
                "1.8+/a.properties",
                "1.8+/b.properties",
                "1.8+/one/x.properties",
                "1.8+/two/y.properties"), mergedSources(read(target())));
    }

    @Test
    void foldersThatAreNotInTheMergeOrderAreLeftOut() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("tags/t.properties"), "t=1");
        write(dir.resolve("unknown/u.properties"), "u=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        String merged = read(target());
        assertEquals(Arrays.asList("tags/t.properties"), mergedSources(merged));
        assertFalse(merged.contains("u=1"));
    }

    @Test
    void onlyPropertiesFilesAreMerged() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("tags/t.properties"), "t=1");
        write(dir.resolve("tags/notes.txt"), "not a properties file");
        write(dir.resolve("tags/backup.properties.bak"), "content-of-the-backup");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        String merged = read(target());
        assertEquals(Arrays.asList("tags/t.properties"), mergedSources(merged));
        assertFalse(merged.contains("not a properties file"));
        assertFalse(merged.contains("content-of-the-backup"));
    }

    // ---- output ----

    @Test
    void theContentOfEveryFileEndsUpInTheTargetUnderItsSourceHeader() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("instructions/i.properties"), "block.1=minecraft:stone\n");
        write(dir.resolve("tags/t.properties"), "block.2=minecraft:dirt");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        String merged = read(target());
        int first = merged.indexOf("# Source: instructions/i.properties");
        int second = merged.indexOf("# Source: tags/t.properties");
        assertTrue(first >= 0 && second > first);
        assertTrue(merged.indexOf("block.1=minecraft:stone") > first);
        assertTrue(merged.indexOf("block.2=minecraft:dirt") > second);
    }

    @Test
    void aFileWithoutNewlineAtTheEndDoesNotRunIntoTheNextHeader() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("instructions/i.properties"), "last.line.without.newline=1");
        write(dir.resolve("tags/t.properties"), "next=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        String merged = read(target());
        // Every section header starts with an empty line, so a plain "...=1\n" would be there even without the added newline
        assertTrue(merged.contains("last.line.without.newline=1\n\n# ===="), "the line must end before the next section starts");
        assertTrue(merged.endsWith("next=1\n"), "the last file gets its newline too");
    }

    @Test
    void theTargetStartsWithTheDoNotEditHeader() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("tags/t.properties"), "t=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        String merged = read(target());
        assertTrue(merged.startsWith("####"));
        assertTrue(merged.contains("Do not edit this file directly"));
    }

    @Test
    void sourcePathsInTheHeadersUseForwardSlashes() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("1.8+/sub/deep.properties"), "d=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        assertEquals(Arrays.asList("1.8+/sub/deep.properties"), mergedSources(read(target())));
    }

    @Test
    void anExistingTargetIsReplacedNotAppendedTo() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("tags/t.properties"), "fresh=1");
        write(target(), "stale.content=1\n");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        String merged = read(target());
        assertFalse(merged.contains("stale.content"));
        assertTrue(merged.contains("fresh=1"));
    }

    @Test
    void mergingTwiceGivesTheSameResult() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("tags/t.properties"), "t=1");
        write(dir.resolve("1.8+/b.properties"), "b=1");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));
        String first = read(target());
        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        assertEquals(first, read(target()));
    }

    @Test
    void theTargetFolderIsCreatedWhenMissing() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("tags/t.properties"), "t=1");
        Path deepTarget = tempDir.resolve("not/yet/there/block.properties");

        assertTrue(PropertiesMerger.mergeProperties(dir, deepTarget));

        assertTrue(Files.exists(deepTarget));
    }

    @Test
    void unicodeContentSurvives() throws IOException {
        Path dir = propertiesDir();
        String comment = "# caf\u00e9 \u20ac \u65e5\u672c\u8a9e";
        write(dir.resolve("tags/t.properties"), comment + "\nkey=1\n");

        assertTrue(PropertiesMerger.mergeProperties(dir, target()));

        assertTrue(read(target()).contains(comment));
    }

    // ---- nothing to merge ----

    @Test
    void failsWhenTheFolderDoesNotExist() {
        assertFalse(PropertiesMerger.mergeProperties(tempDir.resolve("missing"), target()));
        assertFalse(Files.exists(target()));
    }

    @Test
    void failsWhenThePathIsAFileNotAFolder() throws IOException {
        Path file = tempDir.resolve("file.txt");
        write(file, "x");

        assertFalse(PropertiesMerger.mergeProperties(file, target()));
    }

    @Test
    void failsAndKeepsTheOldTargetWhenThereIsNothingToMerge() throws IOException {
        Path dir = propertiesDir();
        write(dir.resolve("unknown/u.properties"), "u=1");
        write(target(), "keep me\n");

        assertFalse(PropertiesMerger.mergeProperties(dir, target()));

        assertEquals("keep me\n", read(target()));
    }
}
