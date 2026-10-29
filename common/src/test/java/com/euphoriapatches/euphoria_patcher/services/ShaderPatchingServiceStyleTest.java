package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how the Reimagined and Unbound style is written into the shaders after patching. Patching itself is the job of
 * the ShaderPatcher library and tested there.
 */
class ShaderPatchingServiceStyleTest {
    private static final String COMMON = "shaders/lib/common.glsl";
    private static final String PATCHED_REIMAGINED = "ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5";
    private static final String PATCHED_UNBOUND = "ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5";

    @TempDir
    Path shaderpacks;

    private ShaderPatchingService service;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @BeforeEach
    void setUp() {
        service = new ShaderPatchingService("EuphoriaPatches", COMMON, shaderpacks);
    }

    private Path patchedPack(String name, int style) throws IOException {
        Path pack = shaderpacks.resolve(name);
        Files.createDirectories(pack.resolve("shaders/lib"));
        Files.write(pack.resolve(COMMON), ("#define SOMETHING 1\n#define SHADER_STYLE " + style + "\n#define OTHER 4\n").getBytes(StandardCharsets.UTF_8));
        Files.write(pack.resolve("shaders/lib/other.glsl"), "other file".getBytes(StandardCharsets.UTF_8));
        return pack;
    }

    private String common(Path pack) throws IOException {
        return new String(Files.readAllBytes(pack.resolve(COMMON)), StandardCharsets.UTF_8);
    }

    private static String styleLine(String commonContent) {
        for (String line : commonContent.split("\n")) {
            if (line.contains("SHADER_STYLE")) return line.trim();
        }
        return null;
    }

    // ---- normalizeShaderStyleInCommon ----

    @Test
    void normalizingSetsTheStyleToReimagined() throws IOException {
        Path pack = patchedPack("base", 4);

        service.normalizeShaderStyleInCommon(pack);

        assertEquals("#define SHADER_STYLE 1", styleLine(common(pack)));
        assertTrue(common(pack).contains("#define OTHER 4"), "only the style is changed, not other values");
    }

    @Test
    void normalizingAReimaginedBaseChangesNothing() throws IOException {
        Path pack = patchedPack("base", 1);
        String before = common(pack);

        service.normalizeShaderStyleInCommon(pack);

        assertEquals(before, common(pack));
    }

    @Test
    void normalizingAPackWithoutCommonFileDoesNotThrow() throws IOException {
        Path pack = Files.createDirectories(shaderpacks.resolve("empty"));

        assertDoesNotThrow(() -> service.normalizeShaderStyleInCommon(pack));
    }

    // ---- applyStyleSettings ----

    @Test
    void withNoStyleWantedNothingIsChanged() throws IOException {
        Path pack = patchedPack(PATCHED_REIMAGINED, 4);
        String before = common(pack);

        service.applyStyleSettings(pack, false, false);

        assertEquals(before, common(pack));
    }

    @Test
    void onlyUnboundWritesStyle4() throws IOException {
        Path pack = patchedPack(PATCHED_UNBOUND, 1);

        service.applyStyleSettings(pack, true, false);

        assertEquals("#define SHADER_STYLE 4", styleLine(common(pack)));
    }

    @Test
    void onlyReimaginedWritesStyle1() throws IOException {
        Path pack = patchedPack(PATCHED_REIMAGINED, 4);

        service.applyStyleSettings(pack, false, true);

        assertEquals("#define SHADER_STYLE 1", styleLine(common(pack)));
    }

    @Test
    void bothStylesFromAReimaginedPackCreateTheUnboundCopy() throws IOException {
        Path reimagined = patchedPack(PATCHED_REIMAGINED, 1);

        service.applyStyleSettings(reimagined, true, true);

        Path unbound = shaderpacks.resolve(PATCHED_UNBOUND);
        assertTrue(Files.isDirectory(unbound), "the other style has to be created");
        assertEquals("#define SHADER_STYLE 1", styleLine(common(reimagined)));
        assertEquals("#define SHADER_STYLE 4", styleLine(common(unbound)));
        assertTrue(Files.exists(unbound.resolve("shaders/lib/other.glsl")), "the copy has the same content");
    }

    @Test
    void bothStylesFromAnUnboundPackCreateTheReimaginedCopy() throws IOException {
        Path unbound = patchedPack(PATCHED_UNBOUND, 1);

        service.applyStyleSettings(unbound, true, true);

        Path reimagined = shaderpacks.resolve(PATCHED_REIMAGINED);
        assertTrue(Files.isDirectory(reimagined));
        assertEquals("#define SHADER_STYLE 4", styleLine(common(unbound)));
        assertEquals("#define SHADER_STYLE 1", styleLine(common(reimagined)));
    }

    @Test
    void anExistingOtherStyleIsNeverOverwritten() throws IOException {
        Path reimagined = patchedPack(PATCHED_REIMAGINED, 1);
        Path unbound = patchedPack(PATCHED_UNBOUND, 4);
        Files.write(unbound.resolve("shaders/lib/other.glsl"), "my own edits".getBytes(StandardCharsets.UTF_8));
        String unboundCommonBefore = common(unbound);

        service.applyStyleSettings(reimagined, true, true);

        assertEquals("my own edits", new String(Files.readAllBytes(unbound.resolve("shaders/lib/other.glsl")), StandardCharsets.UTF_8));
        assertEquals(unboundCommonBefore, common(unbound));
        assertEquals("#define SHADER_STYLE 1", styleLine(common(reimagined)));
    }

    @Test
    void aPackWithoutCommonFileReportsAnErrorForTheCaller() throws IOException {
        Path pack = Files.createDirectories(shaderpacks.resolve(PATCHED_REIMAGINED));

        assertThrows(IOException.class, () -> service.applyStyleSettings(pack, false, true));
    }
}
