package com.euphoriapatches.euphoria_patcher.features.shader_settings;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the conversion of old settings into the current ones, with the real rules file that is shipped in the jar.
 */
class ShaderSettingsConverterTest {

    private static final class Rule {
        final String from;
        final String to;

        Rule(String from, String to) {
            this.from = from;
            this.to = to;
        }

        boolean isWildcard() {
            return from.contains("=*");
        }

        String settingNameOfFrom() {
            return from.substring(0, from.indexOf('='));
        }
    }

    /** Reads the rules file the same way a person reads it: one "old -> new" rule per line */
    private static List<Rule> readRulesFile() throws IOException {
        List<Rule> rules = new ArrayList<>();
        try (InputStream stream = ShaderSettingsConverterTest.class.getClassLoader().getResourceAsStream("settingsConversions.txt")) {
            assertNotNull(stream, "settingsConversions.txt has to be part of the jar");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("->", 2);
                assertEquals(2, parts.length, "rule without an arrow: " + line);
                rules.add(new Rule(parts[0].trim(), parts[1].trim()));
            }
        }
        return rules;
    }

    // ---- the real rules file ----

    @Test
    void theRulesFileHasRules() throws IOException {
        assertFalse(readRulesFile().isEmpty());
    }

    @Test
    void everyRuleInTheFileConvertsItsOwnOldSetting() throws IOException {
        for (Rule rule : readRulesFile()) {
            if (rule.isWildcard()) {
                String newName = rule.to.substring(0, rule.to.indexOf('='));
                assertEquals(newName + "=37", ShaderSettingsConverter.convertLine(rule.settingNameOfFrom() + "=37"),
                        "wildcard rule: " + rule.from + " -> " + rule.to);
            } else {
                assertEquals(rule.to, ShaderSettingsConverter.convertLine(rule.from), "rule: " + rule.from + " -> " + rule.to);
            }
        }
    }

    @Test
    void noTwoRulesCanApplyToTheSameLine() throws IOException {
        // The rules are tried in no particular order, so a line that two rules match would convert differently from run to run
        List<Rule> rules = readRulesFile();
        Set<String> exactSources = new HashSet<>();
        for (Rule rule : rules) {
            assertTrue(exactSources.add(rule.from.toLowerCase(Locale.ROOT)), "two rules start from " + rule.from);
        }
        for (Rule wildcard : rules) {
            if (!wildcard.isWildcard()) continue;
            for (Rule other : rules) {
                if (other == wildcard) continue;
                assertNotEquals(wildcard.settingNameOfFrom().toLowerCase(Locale.ROOT),
                        other.from.substring(0, other.from.indexOf('=')).toLowerCase(Locale.ROOT),
                        "the wildcard rule " + wildcard.from + " overlaps with " + other.from);
            }
        }
    }

    // ---- converting lines ----

    @Test
    void convertsAKnownOldValue() {
        assertEquals("BLOOM_ENABLED=-1", ShaderSettingsConverter.convertLine("BLOOM=false"));
        assertEquals("MCBL_MAIN_DEFINE=1", ShaderSettingsConverter.convertLine("MULTICOLORED_BLOCKLIGHT=true"));
    }

    @Test
    void wildcardRulesKeepTheValue() {
        assertEquals("ACT_ORE_INTENSITY=0.75", ShaderSettingsConverter.convertLine("ACL_ORE_INTENSITY=0.75"));
        assertEquals("GENERATED_NORMAL_RES=256", ShaderSettingsConverter.convertLine("NORMAL_RES=256"));
        assertEquals("GENERATED_NORMAL_RES=", ShaderSettingsConverter.convertLine("NORMAL_RES="));
    }

    @Test
    void aSettingWithAnotherValueThanTheRuleIsLeftAlone() {
        assertEquals("BLOOM=true", ShaderSettingsConverter.convertLine("BLOOM=true"));
        assertEquals("BLOOM=false2", ShaderSettingsConverter.convertLine("BLOOM=false2"));
        assertEquals("AURORA_COLOR_PRESET=3", ShaderSettingsConverter.convertLine("AURORA_COLOR_PRESET=3"));
    }

    @Test
    void ruleMatchingIgnoresTheCapitalisation() {
        assertEquals("BLOOM_ENABLED=-1", ShaderSettingsConverter.convertLine("bloom=FALSE"));
    }

    @Test
    void settingNamesAreMatchedAsAWhole() {
        // A longer name that merely starts like a rule must not be touched
        assertEquals("MY_BLOOM=false", ShaderSettingsConverter.convertLine("MY_BLOOM=false"));
        assertEquals("BLOOM_STRENGTH=false", ShaderSettingsConverter.convertLine("BLOOM_STRENGTH=false"));
    }

    @Test
    void surroundingSpacesDoNotPreventAMatch() {
        assertEquals("BLOOM_ENABLED=-1", ShaderSettingsConverter.convertLine("  BLOOM=false  "));
    }

    @Test
    void commentsAndEmptyLinesAreReturnedExactlyAsTheyAre() {
        assertEquals("# BLOOM=false", ShaderSettingsConverter.convertLine("# BLOOM=false"));
        assertEquals("// BLOOM=false", ShaderSettingsConverter.convertLine("// BLOOM=false"));
        assertEquals("", ShaderSettingsConverter.convertLine(""));
        assertEquals("   ", ShaderSettingsConverter.convertLine("   "));
    }

    @Test
    void unrelatedSettingsAreReturnedExactlyAsTheyAre() {
        assertEquals("SHADOW_QUALITY=2", ShaderSettingsConverter.convertLine("SHADOW_QUALITY=2"));
        assertEquals("  SHADOW_QUALITY=2  ", ShaderSettingsConverter.convertLine("  SHADOW_QUALITY=2  "));
    }

    @Test
    void convertsAWholeFileKeepingOrderAndLength() {
        List<String> before = Arrays.asList(
                "# Euphoria Patches settings",
                "SHADOW_QUALITY=2",
                "BLOOM=false",
                "",
                "ACL_ORE_INTENSITY=1.5",
                "UNKNOWN_SETTING=7");

        List<String> after = ShaderSettingsConverter.convertLines(before);

        assertEquals(Arrays.asList(
                "# Euphoria Patches settings",
                "SHADOW_QUALITY=2",
                "BLOOM_ENABLED=-1",
                "",
                "ACT_ORE_INTENSITY=1.5",
                "UNKNOWN_SETTING=7"), after);
        assertEquals("BLOOM=false", before.get(2), "the input list must not be modified");
    }

    @Test
    void convertingAnEmptyFileGivesAnEmptyResult() {
        assertTrue(ShaderSettingsConverter.convertLines(new ArrayList<String>()).isEmpty());
    }
}
