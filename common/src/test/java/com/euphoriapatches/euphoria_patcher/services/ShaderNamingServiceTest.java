package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderPropertyReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ShaderNamingServiceTest {
    private static final String COMMON = "shaders/lib/common.glsl";

    @TempDir
    Path shaderpacks;

    private ShaderNamingService service;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    void setUp() {
        ShaderPropertyReader.clearCache();
        service = new ShaderNamingService("Complementary", "EuphoriaPatches", "_r5.9.3", "_1.10.5",
                COMMON, "shaders/lib/misc/myFile.glsl", shaderpacks, null);
    }

    private Path zipFile(String name) throws IOException {
        Path zip = shaderpacks.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders/readme.txt"));
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    private Path folderWithCommon(String name, String commonContent) throws IOException {
        Path folder = shaderpacks.resolve(name);
        Files.createDirectories(folder.resolve("shaders/lib"));
        Files.write(folder.resolve(COMMON), commonContent.getBytes(StandardCharsets.UTF_8));
        return folder;
    }

    @Test
    void aCorrectlyNamedZipStaysWhereItIs() throws IOException {
        Path zip = zipFile("ComplementaryReimagined_r5.9.3.zip");

        Path result = service.renameToCorrectShaderName(zip);

        assertEquals(zip, result);
        assertTrue(Files.exists(zip));
    }

    @Test
    void aCorrectlyNamedFolderStaysWhereItIs() throws IOException {
        Path folder = folderWithCommon("ComplementaryUnbound_r5.9.3", "#define SHADER_STYLE 4");

        assertEquals(folder, service.renameToCorrectShaderName(folder));
        assertTrue(Files.isDirectory(folder));
    }

    @Test
    void renamesAZipToTheStyleFromItsName() throws IOException {
        Path zip = zipFile("Complementary Reimagined v5.9.3 (1).zip");

        Path result = service.renameToCorrectShaderName(zip);

        assertEquals(shaderpacks.resolve("ComplementaryReimagined_r5.9.3.zip"), result);
        assertTrue(Files.exists(result));
        assertFalse(Files.exists(zip), "the old name must be gone");
    }

    @Test
    void renamesAFolderWithoutAddingAZipEnding() throws IOException {
        Path folder = folderWithCommon("Complementary Unbound beta", "#define SHADER_STYLE 4");

        Path result = service.renameToCorrectShaderName(folder);

        assertEquals(shaderpacks.resolve("ComplementaryUnbound_r5.9.3"), result);
        assertTrue(Files.isDirectory(result));
        assertTrue(Files.exists(result.resolve(COMMON)), "the content has to move with the folder");
        assertFalse(Files.exists(folder));
    }

    @Test
    void unboundInTheNameWinsWhenBothStylesAreMentioned() throws IOException {
        Path folder = folderWithCommon("Reimagined to Unbound copy", "#define SHADER_STYLE 1");

        Path result = service.renameToCorrectShaderName(folder);

        assertEquals(shaderpacks.resolve("ComplementaryUnbound_r5.9.3"), result);
    }

    @Test
    void readsTheStyleFromCommonGlslWhenTheNameDoesNotSayIt() throws IOException {
        Path unbound = folderWithCommon("my-renamed-pack-a", "#define SHADER_STYLE 4\n");
        Path reimagined = folderWithCommon("my-renamed-pack-b", "#define SHADER_STYLE 1\n");

        assertEquals(shaderpacks.resolve("ComplementaryUnbound_r5.9.3"), service.renameToCorrectShaderName(unbound));
        assertEquals(shaderpacks.resolve("ComplementaryReimagined_r5.9.3"), service.renameToCorrectShaderName(reimagined));
    }

    @Test
    void fallsBackToReimaginedWhenNothingTellsTheStyle() throws IOException {
        Path folder = shaderpacks.resolve("mystery-pack");
        Files.createDirectories(folder);

        assertEquals(shaderpacks.resolve("ComplementaryReimagined_r5.9.3"), service.renameToCorrectShaderName(folder));
    }

    @Test
    void doesNotOverwriteAnExistingShaderWithTheCorrectName() throws IOException {
        Path existing = zipFile("ComplementaryReimagined_r5.9.3.zip");
        long existingSize = Files.size(existing);
        Path other = shaderpacks.resolve("Complementary Reimagined copy.zip");
        Files.write(other, "different content that is not the same size".getBytes(StandardCharsets.UTF_8));

        Path result = service.renameToCorrectShaderName(other);

        assertEquals(other, result, "the path is returned unchanged");
        assertTrue(Files.exists(other), "the file with the other name stays");
        assertEquals(existingSize, Files.size(existing), "the existing shader must not be touched");
    }
}
