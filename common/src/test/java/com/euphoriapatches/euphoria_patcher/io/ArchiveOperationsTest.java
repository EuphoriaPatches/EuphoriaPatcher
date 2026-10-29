package com.euphoriapatches.euphoria_patcher.io;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveOperationsTest {

    @TempDir
    Path tempDir;

    // ---- deleteRecursively ----

    @Test
    void deletesAFolderWithEverythingInIt() throws IOException {
        Path root = tempDir.resolve("pack");
        Files.createDirectories(root.resolve("shaders/lib/deeper"));
        write(root.resolve("shaders/lib/deeper/a.glsl"), "a");
        write(root.resolve("shaders/b.glsl"), "b");
        write(root.resolve("top.txt"), "top");

        ArchiveOperations.deleteRecursively(root);

        assertFalse(Files.exists(root));
        assertFalse(Files.exists(root.resolve("shaders/lib/deeper/a.glsl")));
    }

    @Test
    void deletesASingleFile() throws IOException {
        Path file = tempDir.resolve("single.txt");
        write(file, "x");

        ArchiveOperations.deleteRecursively(file);

        assertFalse(Files.exists(file));
    }

    @Test
    void deletesAnEmptyFolder() throws IOException {
        Path empty = Files.createDirectory(tempDir.resolve("empty"));

        ArchiveOperations.deleteRecursively(empty);

        assertFalse(Files.exists(empty));
    }

    @Test
    void throwsForAPathThatDoesNotExist() {
        assertThrows(NoSuchFileException.class, () -> ArchiveOperations.deleteRecursively(tempDir.resolve("missing")));
    }

    @Test
    void deletingALinkKeepsWhatItPointsTo() throws Exception {
        Path target = realFolderWithFile("target");
        Path link = tempDir.resolve("link");
        Assumptions.assumeTrue(createDirectoryLink(link, target), "cannot create a symlink or junction here");

        ArchiveOperations.deleteRecursively(link);

        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS), "the link itself must be gone");
        assertTrue(Files.exists(target.resolve("keep/precious.txt")), "the target and its content must be untouched");
    }

    @Test
    void deletingAFolderWithALinkInsideKeepsWhatTheLinkPointsTo() throws Exception {
        Path target = realFolderWithFile("target");
        Path holder = tempDir.resolve("holder");
        Files.createDirectories(holder.resolve("inner"));
        write(holder.resolve("inner/own.txt"), "own");
        Assumptions.assumeTrue(createDirectoryLink(holder.resolve("link"), target), "cannot create a symlink or junction here");

        ArchiveOperations.deleteRecursively(holder);

        assertFalse(Files.exists(holder), "the folder and its own files must be gone");
        assertTrue(Files.exists(target.resolve("keep/precious.txt")), "the target and its content must be untouched");
    }

    // ---- reading from zip files ----

    @Test
    void findsAndReadsAFileAtTheExactPath() throws IOException {
        Path zip = zip("exact.zip", "shaders/pack.json", "{\"version\": \"1\"}");

        assertTrue(ArchiveOperations.fileExistsInZip(zip, "shaders/pack.json"));
        // The file is read line by line, every line (also the last one) is returned with a "\n" after it
        assertEquals("{\"version\": \"1\"}\n", ArchiveOperations.readFileFromZip(zip, "shaders/pack.json"));
    }

    @Test
    void textInsideTheZipIsReadAsUtf8WhateverTheDefaultEncodingIs() throws IOException {
        String text = "caf\u00e9 \u20ac \u65e5\u672c";
        Path zip = zip("utf8.zip", "shaders/pack.json", text);

        assertEquals(text + "\n", ArchiveOperations.readFileFromZip(zip, "shaders/pack.json"));
    }

    @Test
    void findsTheFileInsideASingleTopLevelFolder() throws IOException {
        Path zip = zip("nested.zip", "TopFolder/shaders/pack.json", "nested");

        assertTrue(ArchiveOperations.fileExistsInZip(zip, "shaders/pack.json"));
        assertEquals("nested\n", ArchiveOperations.readFileFromZip(zip, "shaders/pack.json"));
    }

    @Test
    void doesNotLookMoreThanOneFolderDeep() throws IOException {
        Path zip = zip("deep.zip", "A/B/shaders/pack.json", "too deep");

        assertFalse(ArchiveOperations.fileExistsInZip(zip, "shaders/pack.json"));
        assertNull(ArchiveOperations.readFileFromZip(zip, "shaders/pack.json"));
    }

    @Test
    void acceptsBackslashesAndALeadingSlashInTheRequestedPath() throws IOException {
        Path zip = zip("slashes.zip", "shaders/pack.json", "x");

        assertTrue(ArchiveOperations.fileExistsInZip(zip, "\\shaders\\pack.json"));
    }

    @Test
    void aFolderEntryDoesNotCountAsAFile() throws IOException {
        Path zip = tempDir.resolve("folder-entry.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders/pack.json/"));
            out.closeEntry();
        }

        assertFalse(ArchiveOperations.fileExistsInZip(zip, "shaders/pack.json"));
        assertNull(ArchiveOperations.readFileFromZip(zip, "shaders/pack.json"));
    }

    @Test
    void reportsMissingEntriesAndUnusableArchives() throws IOException {
        Path zip = zip("other.zip", "shaders/other.txt", "x");
        Path notAZip = tempDir.resolve("not-a-zip.zip");
        write(notAZip, "this is not a zip");
        Path wrongExtension = tempDir.resolve("data.txt");
        write(wrongExtension, "x");

        assertFalse(ArchiveOperations.fileExistsInZip(zip, "shaders/pack.json"));
        assertNull(ArchiveOperations.readFileFromZip(zip, "shaders/pack.json"));
        assertFalse(ArchiveOperations.fileExistsInZip(notAZip, "shaders/pack.json"));
        assertNull(ArchiveOperations.readFileFromZip(notAZip, "shaders/pack.json"));
        assertFalse(ArchiveOperations.fileExistsInZip(wrongExtension, "shaders/pack.json"));
        assertFalse(ArchiveOperations.fileExistsInZip(tempDir.resolve("missing.zip"), "shaders/pack.json"));
        assertNull(ArchiveOperations.readFileFromZip(tempDir.resolve("missing.zip"), "shaders/pack.json"));
    }

    // ---- helpers ----

    private Path realFolderWithFile(String name) throws IOException {
        Path folder = tempDir.resolve(name);
        Files.createDirectories(folder.resolve("keep"));
        write(folder.resolve("keep/precious.txt"), "precious");
        return folder;
    }

    private static void write(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path zip(String name, String entryName, String content) throws IOException {
        Path zip = tempDir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    /**
     * Creates a directory junction on Windows (no admin rights needed) or a symbolic link elsewhere.
     * @return false if the link could not be created, so the calling test can be skipped
     */
    private static boolean createDirectoryLink(Path link, Path target) throws Exception {
        try {
            if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                        .redirectErrorStream(true).start();
                process.getInputStream().close();
                if (process.waitFor() != 0) {
                    return false;
                }
            } else {
                Files.createSymbolicLink(link, target);
            }
            return Files.exists(link);
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }
}
