package com.euphoriapatches.euphoria_patcher.util.shader;

import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import com.euphoriapatches.euphoria_patcher.integration.ShaderLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the changes that are made to the popular settings profile for loaders that cannot run every feature. The
 * methods are private and depend on the game for the decision when to use them, so they are called directly, with a
 * profile line the way shaders.properties of the shader has it. If a Complementary update changes that line so that
 * the replacement stops matching, the real file has to be checked.
 */
class BootShaderModificatorTest {
    private static final String PROPERTIES = "shaders/shaders.properties";

    private static final String POPULAR = "    profile2.POPULAR     = DETAIL_QUALITY=2 COLORED_LIGHTING=192 WORLD_SPACE_REFLECTIONS=-1 "
            + "AURORA_COLOR_PRESET=-1  GLOWING_WART  SITUATIONAL_ORES !REDSTONE_IPBR END_CRYSTAL_VORTEX=3 DRAGON_DEATH_EFFECT=1 "
            + "!WB_CHROMATIC DARKER_DEPTH_OCEANS=12 END_PORTAL_BEAM EMISSIVE_SOUL_SAND !SELECT_OUTLINE_AUTO_HIDE BEACON_BEAM_EMISSION=3.0";

    private static final String OTHER_PROFILE = "    profile2.SPACEAGLE17 = DETAIL_QUALITY=3 COLORED_LIGHTING=192 WORLD_SPACE_REFLECTIONS=1 "
            + "END_CRYSTAL_VORTEX=3 DRAGON_DEATH_EFFECT=1 END_PORTAL_BEAM EMISSIVE_SOUL_SAND";

    private static final String DEFINES = "        #define END_CRYSTAL_VORTEX 0\n        #define DRAGON_DEATH_EFFECT 0\n        #undef END_PORTAL_BEAM";

    @TempDir
    Path shaderpacks;

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    private Path pack(String name, String popularLine) throws IOException {
        Path pack = Files.createDirectories(shaderpacks.resolve(name));
        String content = "    profile2.DEFAULT     = DETAIL_QUALITY=2 COLORED_LIGHTING=0   END_CRYSTAL_VORTEX=3\n"
                + popularLine + "\n" + OTHER_PROFILE + "\n" + DEFINES + "\n";
        Files.createDirectories(pack.resolve(PROPERTIES).getParent());
        Files.write(pack.resolve(PROPERTIES), content.getBytes(StandardCharsets.UTF_8));
        return pack;
    }

    private static List<String> lines(Path pack) throws IOException {
        return Files.readAllLines(pack.resolve(PROPERTIES), StandardCharsets.UTF_8);
    }

    private static String line(Path pack, String start) throws IOException {
        for (String line : lines(pack)) {
            if (line.trim().startsWith(start)) return line;
        }
        throw new AssertionError("no line starting with " + start);
    }

    private static List<String> tokens(String line) {
        return new ArrayList<>(Arrays.asList(line.trim().split("\\s+")));
    }

    private static void run(String method, Path pack, boolean unbound, boolean reimagined) throws Exception {
        Method target = BootShaderModificator.class.getDeclaredMethod(method, Path.class, boolean.class, boolean.class);
        target.setAccessible(true);
        try {
            target.invoke(null, pack, unbound, reimagined);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    /** The tokens of the popular profile with one token replaced by another */
    private static List<String> popularWith(String oldToken, String newToken) {
        List<String> tokens = tokens(POPULAR);
        int index = tokens.indexOf(oldToken);
        assertTrue(index >= 0, oldToken + " has to be in the fixture");
        tokens.set(index, newToken);
        return tokens;
    }

    private void assertOnlyPopularChanged(Path pack, List<String> expectedPopularTokens) throws IOException {
        assertEquals(expectedPopularTokens, tokens(line(pack, "profile2.POPULAR")));
        assertEquals(OTHER_PROFILE, line(pack, "profile2.SPACEAGLE17"), "the other profiles have to stay");
        assertTrue(lines(pack).contains(DEFINES.split("\n")[0]), "the defines have to stay");
        assertTrue(line(pack, "profile2.DEFAULT").contains("COLORED_LIGHTING=0"));
    }

    // ---- the four changes of the popular profile ----

    @Test
    void coloredLightingIsSwitchedOffInThePopularProfile() throws Exception {
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeColoredLighting", pack, false, true);

        assertOnlyPopularChanged(pack, popularWith("COLORED_LIGHTING=192", "COLORED_LIGHTING=0"));
    }

    @Test
    void theEndCrystalVortexIsSwitchedOffInThePopularProfile() throws Exception {
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeEndCrystalVortex", pack, false, true);

        assertOnlyPopularChanged(pack, popularWith("END_CRYSTAL_VORTEX=3", "END_CRYSTAL_VORTEX=0"));
    }

    @Test
    void theDragonDeathEffectIsSwitchedOffInThePopularProfile() throws Exception {
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeDragonDeathEffect", pack, false, true);

        assertOnlyPopularChanged(pack, popularWith("DRAGON_DEATH_EFFECT=1", "DRAGON_DEATH_EFFECT=0"));
    }

    @Test
    void theEndPortalBeamIsDisabledInThePopularProfile() throws Exception {
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeEndPortalBeam", pack, false, true);

        assertOnlyPopularChanged(pack, popularWith("END_PORTAL_BEAM", "!END_PORTAL_BEAM"));
    }

    @Test
    void doingAChangeTwiceChangesNothingTheSecondTime() throws Exception {
        for (String method : new String[]{"removeColoredLighting", "removeEndCrystalVortex", "removeDragonDeathEffect", "removeEndPortalBeam"}) {
            Path pack = pack("Twice-" + method, POPULAR);
            run(method, pack, false, true);
            List<String> once = lines(pack);

            run(method, pack, false, true);

            assertEquals(once, lines(pack), method);
        }
    }

    @Test
    void allFourChangesTogetherDoNotGetInEachOthersWay() throws Exception {
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeColoredLighting", pack, false, true);
        run("removeEndCrystalVortex", pack, false, true);
        run("removeDragonDeathEffect", pack, false, true);
        run("removeEndPortalBeam", pack, false, true);

        List<String> expected = tokens(POPULAR);
        expected.set(expected.indexOf("COLORED_LIGHTING=192"), "COLORED_LIGHTING=0");
        expected.set(expected.indexOf("END_CRYSTAL_VORTEX=3"), "END_CRYSTAL_VORTEX=0");
        expected.set(expected.indexOf("DRAGON_DEATH_EFFECT=1"), "DRAGON_DEATH_EFFECT=0");
        expected.set(expected.indexOf("END_PORTAL_BEAM"), "!END_PORTAL_BEAM");
        assertOnlyPopularChanged(pack, expected);
    }

    // ---- not matching more than it should ----

    @Test
    void longerSettingNamesAndValuesAreNotMistakenForTheOnesToChange() throws Exception {
        String popular = "    profile2.POPULAR = A=1 COLORED_LIGHTING=1920 B=2 END_CRYSTAL_VORTEX=30 C=3 DRAGON_DEATH_EFFECT=10 D=4 "
                + "END_PORTAL_BEAM_EXTRA E=5 !END_PORTAL_BEAM F=6";
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", popular);

        run("removeColoredLighting", pack, false, true);
        run("removeEndCrystalVortex", pack, false, true);
        run("removeDragonDeathEffect", pack, false, true);
        run("removeEndPortalBeam", pack, false, true);

        assertEquals(popular, line(pack, "profile2.POPULAR"));
    }

    @Test
    void aShaderWithoutAPopularProfileIsNotChanged() throws Exception {
        Path pack = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", "    profile2.SOMETHING_ELSE = COLORED_LIGHTING=192 END_PORTAL_BEAM x=1");
        List<String> before = lines(pack);

        run("removeColoredLighting", pack, false, true);
        run("removeEndPortalBeam", pack, false, true);

        assertEquals(before, lines(pack));
    }

    // ---- both styles ----

    @Test
    void bothStylesAreChangedWhenBothAreInstalled() throws Exception {
        Path reimagined = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);
        Path unbound = pack("ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeColoredLighting", reimagined, true, true);

        assertTrue(line(reimagined, "profile2.POPULAR").contains("COLORED_LIGHTING=0"));
        assertTrue(line(unbound, "profile2.POPULAR").contains("COLORED_LIGHTING=0"));
    }

    @Test
    void theOtherStyleIsLeftAloneWhenItIsNotInstalledByThisRun() throws Exception {
        Path reimagined = pack("ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);
        Path unbound = pack("ComplementaryUnbound_r5.9.3 + EuphoriaPatches_1.10.5", POPULAR);

        run("removeColoredLighting", reimagined, false, true);

        assertTrue(line(unbound, "profile2.POPULAR").contains("COLORED_LIGHTING=192"));
    }

    // ---- when the macOS workarounds are needed ----

    @Test
    void macOSNeedsTheWorkaroundsForEveryLoaderExceptNewIris() {
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.OPTIFINE, 0));
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.IRIS, 10808), "Iris before 1.12.0");
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.IRIS, 11199));
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.IRIS, 0), "an Iris version that could not be read");
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.UNKNOWN, 0));
    }

    @Test
    void irisFrom1_12_0OnNeedsNoWorkaroundsOnMacOS() {
        assertFalse(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.IRIS, 11200));
        assertFalse(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.IRIS, 11201));
        assertFalse(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.IRIS, 20000));
    }

    @Test
    void anotherLoaderWithAHighVersionNumberIsNotLetOff() {
        // Oculus and the others count their own versions, 1.12.0 of one of them says nothing about Iris
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.OCULUS, 11200));
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("Mac OS X", ShaderLoader.NEOCULUS, 20000));
    }

    @Test
    void otherSystemsNeverNeedTheMacOSWorkarounds() {
        assertFalse(BootShaderModificator.needsMacOSWorkarounds("Windows 11", ShaderLoader.IRIS, 10808));
        assertFalse(BootShaderModificator.needsMacOSWorkarounds("Linux", ShaderLoader.OPTIFINE, 0));
        assertFalse(BootShaderModificator.needsMacOSWorkarounds(null, ShaderLoader.IRIS, 0));
    }

    @Test
    void macOSIsRecognisedWhateverTheCapitalisation() {
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("macOS", ShaderLoader.IRIS, 10808));
        assertTrue(BootShaderModificator.needsMacOSWorkarounds("MAC OS X", ShaderLoader.IRIS, 10808));
    }
}
