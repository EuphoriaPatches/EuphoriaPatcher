package com.euphoriapatches.euphoria_patcher.features;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the regex replacements that are applied to the files of a patched shaderpack. The shaderpacks are real folders
 * in a temporary folder.
 */
class ModifyPatchedShaderpacksTest {
    private static final String REIMAGINED = "ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5";
    private static final String UNBOUND = "ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5";
    /** One backslash. Written out like this so that no escape sequence is formed in the source. */
    private static final String BS = "\\";

    @TempDir
    Path shaderpacks;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    private Path pack(String name, String relativeFile, String content) throws IOException {
        Path pack = Files.createDirectories(shaderpacks.resolve(name));
        write(pack.resolve(relativeFile), content);
        return pack;
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    // ---- replacing in one file ----

    @Test
    void replacesTextInASingleFile() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/shaders.properties", "screen=<empty> <empty>\nother=1\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/shaders.properties", null, "screen=<empty> <empty>", "screen=info19 info20");

        assertEquals("screen=info19 info20\nother=1\n", read(pack.resolve("shaders/shaders.properties")));
    }

    @Test
    void groupsOfTheRegexCanBeUsedInTheReplacement() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/f.txt", "value.info19.0=old\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/f.txt", null, "(value" + BS + ".)info19(" + BS + ".0)=.*", "$1info20$2=new");

        assertEquals("value.info20.0=new\n", read(pack.resolve("shaders/f.txt")));
    }

    @Test
    void severalPairsAreAppliedInOrder() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/f.txt", "a\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/f.txt", null, "a", "b", "b", "c");

        assertEquals("c\n", read(pack.resolve("shaders/f.txt")), "the second pair sees the result of the first");
    }

    @Test
    void everyMatchInASmallFileIsReplacedAndLineEndingsStay() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/f.txt", "x=1\r\nx=1\r\ny=2\r\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/f.txt", null, "x=1", "x=9");

        assertEquals("x=9\r\nx=9\r\ny=2\r\n", read(pack.resolve("shaders/f.txt")));
    }

    @Test
    void aFileWithoutAMatchStaysTheSame() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/f.txt", "nothing to see\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/f.txt", null, "absent", "present");

        assertEquals("nothing to see\n", read(pack.resolve("shaders/f.txt")));
    }

    @Test
    void textThatIsNotAsciiSurvivesAndTheReplacementIsWrittenAsUtf8() throws IOException {
        String japanese = String.valueOf((char) 0x3042) + (char) 0x3044;
        String accented = String.valueOf((char) 0xC1) + (char) 0xCD;
        String section = String.valueOf((char) 0xA7);
        Path pack = pack(REIMAGINED, "shaders/lang/ja_JP.lang", "option.a=" + japanese + accented + "\nvalue.info19.0=old\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/lang", ".lang", "value" + BS + ".info19" + BS + ".0=.*", "value.info19.0=" + section + "c1.0 -> 2.0");

        Path file = pack.resolve("shaders/lang/ja_JP.lang");
        assertEquals("option.a=" + japanese + accented + "\nvalue.info19.0=" + section + "c1.0 -> 2.0\n", read(file));
        // The section sign is the two bytes c2 a7 in UTF-8
        byte[] bytes = Files.readAllBytes(file);
        boolean found = false;
        for (int i = 0; i + 1 < bytes.length; i++) {
            if ((bytes[i] & 0xFF) == 0xC2 && (bytes[i + 1] & 0xFF) == 0xA7) found = true;
        }
        assertTrue(found, "the section sign has to be written as UTF-8");
    }

    @Test
    void doubledBackslashesInTheReplacementGiveOneBackslashForTheJson() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/pack.json", "{\n  \"shaderDescription\": \"old\",\n  \"version\": \"1\"\n}\n");

        // The way the update notification writes the description: a unicode escape of JSON in the file needs two backslashes here
        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/pack.json", null,
                "\"shaderDescription\":.*", "\"shaderDescription\": \"" + BS + BS + "u00A7aNEW\",");

        assertEquals("{\n  \"shaderDescription\": \"" + BS + "u00A7aNEW\",\n  \"version\": \"1\"\n}\n", read(pack.resolve("shaders/pack.json")));
    }

    // ---- folders with an extension ----

    @Test
    void onlyFilesWithTheGivenExtensionInTheFolderAreChanged() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/lang/en_US.lang", "key=old\n");
        write(pack.resolve("shaders/lang/es_ES.lang"), "key=old\n");
        write(pack.resolve("shaders/lang/readme.txt"), "key=old\n");
        write(pack.resolve("shaders/lang/sub/nested.lang"), "key=old\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/lang", ".lang", "key=old", "key=new");

        assertEquals("key=new\n", read(pack.resolve("shaders/lang/en_US.lang")));
        assertEquals("key=new\n", read(pack.resolve("shaders/lang/es_ES.lang")));
        assertEquals("key=old\n", read(pack.resolve("shaders/lang/readme.txt")));
        assertEquals("key=old\n", read(pack.resolve("shaders/lang/sub/nested.lang")), "subfolders are not searched");
    }

    // ---- which shaderpacks ----

    @Test
    void theOtherStyleIsChangedToWhenBothStylesAreInstalled() throws IOException {
        Path reimagined = pack(REIMAGINED, "shaders/f.txt", "v=old\n");
        Path unbound = pack(UNBOUND, "shaders/f.txt", "v=old\n");

        ModifyPatchedShaderpacks.modifyFiles(reimagined, true, true, "shaders/f.txt", null, "v=old", "v=new");

        assertEquals("v=new\n", read(reimagined.resolve("shaders/f.txt")));
        assertEquals("v=new\n", read(unbound.resolve("shaders/f.txt")));
    }

    @Test
    void theReimaginedStyleIsChangedToo() throws IOException {
        Path reimagined = pack(REIMAGINED, "shaders/f.txt", "v=old\n");
        Path unbound = pack(UNBOUND, "shaders/f.txt", "v=old\n");

        ModifyPatchedShaderpacks.modifyFiles(unbound, true, true, "shaders/f.txt", null, "v=old", "v=new");

        assertEquals("v=new\n", read(reimagined.resolve("shaders/f.txt")));
        assertEquals("v=new\n", read(unbound.resolve("shaders/f.txt")));
    }

    @Test
    void theOtherStyleIsLeftAloneWhenOnlyOneStyleIsWanted() throws IOException {
        Path reimagined = pack(REIMAGINED, "shaders/f.txt", "v=old\n");
        Path unbound = pack(UNBOUND, "shaders/f.txt", "v=old\n");

        ModifyPatchedShaderpacks.modifyFiles(reimagined, false, true, "shaders/f.txt", null, "v=old", "v=new");

        assertEquals("v=new\n", read(reimagined.resolve("shaders/f.txt")));
        assertEquals("v=old\n", read(unbound.resolve("shaders/f.txt")));
    }

    @Test
    void aMissingOtherStyleIsNoProblem() throws IOException {
        Path reimagined = pack(REIMAGINED, "shaders/f.txt", "v=old\n");

        assertDoesNotThrow(() -> ModifyPatchedShaderpacks.modifyFiles(reimagined, true, true, "shaders/f.txt", null, "v=old", "v=new"));

        assertEquals("v=new\n", read(reimagined.resolve("shaders/f.txt")));
    }

    @Test
    void zippedShaderpacksAreNotTouched() throws IOException {
        Path zip = shaderpacks.resolve(REIMAGINED + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("shaders/f.txt"));
            out.write("v=old".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        byte[] before = Files.readAllBytes(zip);

        ModifyPatchedShaderpacks.modifyFiles(zip, false, true, "shaders/f.txt", null, "v=old", "v=new");

        assertArrayEquals(before, Files.readAllBytes(zip));
    }

    // ---- things that go wrong ----

    @Test
    void aMissingTargetIsNoProblemAndCreatesNothing() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/other.txt", "x\n");

        assertDoesNotThrow(() -> ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/missing.txt", null, "x", "y"));

        assertFalse(Files.exists(pack.resolve("shaders/missing.txt")));
        assertEquals("x\n", read(pack.resolve("shaders/other.txt")));
    }

    @Test
    void anOddNumberOfRegexAndReplacementStringsChangesNothing() throws IOException {
        Path pack = pack(REIMAGINED, "shaders/f.txt", "x\n");

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/f.txt", null, "x", "y", "dangling");

        assertEquals("x\n", read(pack.resolve("shaders/f.txt")));
    }

    // ---- big files ----

    private static String bigFile(int firstMatchLine, int secondMatchLine) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            if (i == firstMatchLine || i == secondMatchLine) {
                text.append("target=old\n");
            } else {
                text.append("filler line number ").append(i).append(" with some padding to make the file big\n");
            }
        }
        return text.toString();
    }

    @Test
    void aBigFileIsChangedToo() throws IOException {
        String content = bigFile(2500, -1);
        assertTrue(content.length() > 100_000, "the fixture has to be bigger than the limit for line by line processing");
        Path pack = pack(REIMAGINED, "shaders/big.properties", content);

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/big.properties", null, "target=old", "target=new");

        List<String> lines = Files.readAllLines(pack.resolve("shaders/big.properties"), StandardCharsets.UTF_8);
        assertEquals(3000, lines.size());
        assertEquals("target=new", lines.get(2500));
        assertEquals("filler line number 7 with some padding to make the file big", lines.get(7));
    }

    @Test
    void inABigFileEachPatternOnlyReplacesItsFirstMatchingLine() throws IOException {
        // Unlike small files, where every match is replaced. The patterns of the mod each aim at one line.
        Path pack = pack(REIMAGINED, "shaders/big.properties", bigFile(10, 2000));

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/big.properties", null, "target=old", "target=new");

        List<String> lines = Files.readAllLines(pack.resolve("shaders/big.properties"), StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("target=new", "target=old"), Arrays.asList(lines.get(10), lines.get(2000)));
    }

    @Test
    void aBigFileKeepsNonAsciiTextToo() throws IOException {
        String text = "caf" + (char) 0xE9 + " " + (char) 0x3042;
        // The real lang files are over 100KB, so the update notice with its section sign is written by this path
        String section = String.valueOf((char) 0xA7);
        Path pack = pack(REIMAGINED, "shaders/big.lang", bigFile(5, -1).replace("filler line number 3 ", text + " "));

        ModifyPatchedShaderpacks.modifyFiles(pack, false, true, "shaders/big.lang", null, "target=old", "target=" + section + "new");

        List<String> lines = Files.readAllLines(pack.resolve("shaders/big.lang"), StandardCharsets.UTF_8);
        assertEquals("target=" + section + "new", lines.get(5));
        assertTrue(lines.get(3).startsWith(text + " with some padding"));
    }
}
