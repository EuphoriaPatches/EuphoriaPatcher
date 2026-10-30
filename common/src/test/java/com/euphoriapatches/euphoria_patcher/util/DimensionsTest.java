package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how dimension.properties of a shaderpack is read and how dimension ids end up as names for the defines. No
 * shaderpack is active in the tests, so the names come from the vanilla dimensions or the cleaned up id.
 */
class DimensionsTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    private static Map<String, String> parse(String content) throws IOException {
        return Dimensions.parseDimensionPropertiesFromReader(new BufferedReader(new StringReader(content)));
    }

    // ---- reading dimension.properties ----

    @Test
    void mapsEveryIdToTheCategoryOfItsKey() throws IOException {
        Map<String, String> map = parse(
                "dimension.world0=minecraft:overworld mymod:sky\n" +
                "dimension.world-1=minecraft:the_nether\n" +
                "dimension.world1=minecraft:the_end\n");

        assertEquals("overworld", map.get("minecraft:overworld"));
        assertEquals("overworld", map.get("mymod:sky"));
        assertEquals("nether", map.get("minecraft:the_nether"));
        assertEquals("end", map.get("minecraft:the_end"));
        assertEquals(4, map.size());
    }

    @Test
    void spacesAroundTheEqualsSignAndExtraSpacesBetweenIdsAreFine() throws IOException {
        Map<String, String> map = parse("dimension.world-1   =   a:one    b:two  \n");

        assertEquals("nether", map.get("a:one"));
        assertEquals("nether", map.get("b:two"));
        assertEquals(2, map.size());
    }

    @Test
    void idsCanContinueOnTheNextLine() throws IOException {
        Map<String, String> map = parse("dimension.world1=minecraft:the_end \\\n    mymod:void \\\n    mymod:islands\n");

        assertEquals("end", map.get("minecraft:the_end"));
        assertEquals("end", map.get("mymod:void"));
        assertEquals("end", map.get("mymod:islands"));
        assertEquals(3, map.size());
    }

    @Test
    void theWildcardIsNotAnId() throws IOException {
        Map<String, String> map = parse("dimension.world0=minecraft:overworld *\n");

        assertEquals(1, map.size());
        assertNull(map.get("*"));
    }

    @Test
    void commentsOtherDefinesAndUnknownKeysAreIgnored() throws IOException {
        Map<String, String> map = parse(
                "# dimension.world0=commented:out\n" +
                "#define SOMETHING 1\n" +
                "dimension.world2=other:folder\n" +
                "unrelated=value\n" +
                "dimension.world0=real:one\n");

        assertEquals(1, map.size());
        assertEquals("overworld", map.get("real:one"));
    }

    @Test
    void ifBlocksAreMergedInsteadOfTheLaterOneOverwritingTheEarlierOne() throws IOException {
        Map<String, String> map = parse(
                "#ifdef OPTION_A\n" +
                "dimension.world-1=first:nether\n" +
                "#else\n" +
                "dimension.world-1=second:nether\n" +
                "#endif\n" +
                "dimension.world1=minecraft:the_end\n");

        assertEquals("nether", map.get("first:nether"));
        assertEquals("nether", map.get("second:nether"));
        assertEquals("end", map.get("minecraft:the_end"));
    }

    @Test
    void everyKindOfPreprocessorLineSplitsTheFile() throws IOException {
        Map<String, String> map = parse(
                "#if A\n" +
                "dimension.world0=a:one\n" +
                "#elif B\n" +
                "dimension.world0=b:one\n" +
                "#ifndef C\n" +
                "dimension.world0=c:one\n" +
                "  #endif\n");

        assertEquals(3, map.size());
    }

    @Test
    void anEmptyFileGivesAnEmptyMap() throws IOException {
        assertTrue(parse("").isEmpty());
    }

    // ---- reading it from a shaderpack ----

    @Test
    void readsTheFileOfAFolderShaderpack() throws IOException {
        Path pack = tempDir.resolve("folder-pack");
        Files.createDirectories(pack.resolve("shaders"));
        Files.write(pack.resolve(Dimensions.SHADER_DIMENSION_PROPERTIES_LOCATION), "dimension.world-1=mod:hell\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("nether", Dimensions.parseDimensionProperties(pack).get("mod:hell"));
    }

    @Test
    void readsTheFileOfAZippedShaderpack() throws IOException {
        Path zip = tempDir.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(Dimensions.SHADER_DIMENSION_PROPERTIES_LOCATION));
            out.write("dimension.world1=mod:sky\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        assertEquals("end", Dimensions.parseDimensionProperties(zip).get("mod:sky"));
    }

    @Test
    void aShaderpackWithoutTheFileOrWithoutAnythingGivesNoMappings() throws IOException {
        Path folder = Files.createDirectories(tempDir.resolve("no-file"));
        Path zip = tempDir.resolve("no-entry.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders/other.txt"));
            out.closeEntry();
        }
        Path brokenZip = tempDir.resolve("broken.zip");
        Files.write(brokenZip, "not a zip".getBytes(StandardCharsets.UTF_8));

        assertTrue(Dimensions.parseDimensionProperties(folder).isEmpty());
        assertTrue(Dimensions.parseDimensionProperties(zip).isEmpty());
        assertTrue(Dimensions.parseDimensionProperties(brokenZip).isEmpty());
        assertTrue(Dimensions.parseDimensionProperties(null).isEmpty());
    }

    // ---- names for the defines without an active shaderpack ----

    @Test
    void vanillaDimensionsHaveTheirCategoryNames() {
        assertEquals("overworld", Dimensions.getCurrentDimension("minecraft:overworld"));
        assertEquals("nether", Dimensions.getCurrentDimension("minecraft:the_nether"));
        assertEquals("end", Dimensions.getCurrentDimension("minecraft:the_end"));
    }

    @Test
    void anUnknownDimensionIdIsCleanedUpForUseInADefine() {
        assertEquals("mymod_deep_cave_2", Dimensions.getCurrentDimension("mymod:deep.cave-2"));
        assertEquals("Mod_Name_X9", Dimensions.getCurrentDimension("Mod:Name/X9"));
    }

    @Test
    void withoutADimensionIdItIsTheOverworld() {
        assertEquals("overworld", Dimensions.getCurrentDimension(null));
    }

    @Test
    void onlyTheVanillaDimensionsAreKnownWithoutAShaderpack() {
        assertTrue(Dimensions.isCurrentDimensionInMappings("minecraft:overworld"));
        assertTrue(Dimensions.isCurrentDimensionInMappings("minecraft:the_nether"));
        assertTrue(Dimensions.isCurrentDimensionInMappings("minecraft:the_end"));
        assertFalse(Dimensions.isCurrentDimensionInMappings("mymod:unknown_dimension"));
        assertFalse(Dimensions.isCurrentDimensionInMappings(null));
    }
}
