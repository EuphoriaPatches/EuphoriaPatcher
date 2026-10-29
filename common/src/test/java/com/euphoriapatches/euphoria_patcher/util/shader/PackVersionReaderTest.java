package com.euphoriapatches.euphoria_patcher.util.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackVersionReaderTest {

    @TempDir
    Path tempDir;

    // ---- parseVersion ----

    @Test
    void parsesTheVersionFromJson() {
        assertEquals("1.10.5", PackVersionReader.parseVersion("{\"version\": \"1.10.5\", \"name\": \"x\"}"));
        assertEquals("2.0.1-dev", PackVersionReader.parseVersion("{\n  \"name\": \"x\",\n  \"version\"  :   \"2.0.1-dev\"\n}"));
    }

    @Test
    void usesTheFirstVersionKey() {
        assertEquals("1.0", PackVersionReader.parseVersion("{\"version\":\"1.0\",\"other\":{\"version\":\"2.0\"}}"));
    }

    @Test
    void parseVersionReturnsNullWhenThereIsNoVersion() {
        assertNull(PackVersionReader.parseVersion("{\"name\": \"x\"}"));
        assertNull(PackVersionReader.parseVersion(""));
        assertNull(PackVersionReader.parseVersion(null));
    }

    // ---- readVersion ----

    @Test
    void readsTheVersionOfAFolder() throws IOException {
        Path pack = folderWithPackJson("folder", "{\"version\": \"1.10.5\"}");

        assertEquals("1.10.5", PackVersionReader.readVersion(pack));
    }

    @Test
    void readsTheVersionOfAZip() throws IOException {
        Path zip = zip("plain.zip", "shaders/pack.json", "{\"version\": \"1.9.9\"}");

        assertEquals("1.9.9", PackVersionReader.readVersion(zip));
    }

    @Test
    void readsTheVersionOfAZipWithOneTopLevelFolder() throws IOException {
        Path zip = zip("nested.zip", "TopFolder/shaders/pack.json", "{\"version\": \"4.4.4\"}");

        assertEquals("4.4.4", PackVersionReader.readVersion(zip));
    }

    @Test
    void returnsNullWhenThereIsNothingToRead() throws IOException {
        Path folderWithoutJson = Files.createDirectories(tempDir.resolve("no-json"));
        Path folderWithoutVersion = folderWithPackJson("no-version", "{\"name\": \"x\"}");
        Path zipWithoutJson = zip("no-json.zip", "shaders/other.txt", "x");
        Path notAZip = tempDir.resolve("fake.zip");
        Files.write(notAZip, "not really a zip".getBytes(StandardCharsets.UTF_8));
        Path wrongExtension = tempDir.resolve("data.txt");
        Files.write(wrongExtension, "{\"version\": \"9.9.9\"}".getBytes(StandardCharsets.UTF_8));

        assertNull(PackVersionReader.readVersion(folderWithoutJson));
        assertNull(PackVersionReader.readVersion(folderWithoutVersion));
        assertNull(PackVersionReader.readVersion(zipWithoutJson));
        assertNull(PackVersionReader.readVersion(notAZip));
        assertNull(PackVersionReader.readVersion(wrongExtension));
        assertNull(PackVersionReader.readVersion(tempDir.resolve("does-not-exist")));
        assertNull(PackVersionReader.readVersion(null));
    }

    @Test
    void returnsNullForAPackJsonThatIsNotValidUtf8() throws IOException {
        Path pack = tempDir.resolve("broken");
        Files.createDirectories(pack.resolve("shaders"));
        Files.write(pack.resolve("shaders/pack.json"), new byte[]{'{', '"', 'x', '"', ':', '"', (byte) 0xff, '"', ',', '"', 'v', 'e', 'r', 's', 'i', 'o', 'n', '"', ':', '"', '3', '"', '}'});

        assertNull(PackVersionReader.readVersion(pack));
    }

    @Test
    void remembersTheVersionOfAPathOnceItWasRead() throws IOException {
        Path pack = folderWithPackJson("cached", "{\"version\": \"1.0.0\"}");
        assertEquals("1.0.0", PackVersionReader.readVersion(pack));

        Files.write(pack.resolve("shaders/pack.json"), "{\"version\": \"2.0.0\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals("1.0.0", PackVersionReader.readVersion(pack), "the second read has to come from the cache");
    }

    @Test
    void remembersThatAPathHasNoVersion() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("cached-empty"));
        assertNull(PackVersionReader.readVersion(pack));

        Files.createDirectories(pack.resolve("shaders"));
        Files.write(pack.resolve("shaders/pack.json"), "{\"version\": \"1.0.0\"}".getBytes(StandardCharsets.UTF_8));

        assertNull(PackVersionReader.readVersion(pack), "'no version' is cached too");
    }

    // ---- helpers ----

    private Path folderWithPackJson(String name, String json) throws IOException {
        Path pack = tempDir.resolve(name);
        Files.createDirectories(pack.resolve("shaders"));
        Files.write(pack.resolve("shaders/pack.json"), json.getBytes(StandardCharsets.UTF_8));
        return pack;
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
}
