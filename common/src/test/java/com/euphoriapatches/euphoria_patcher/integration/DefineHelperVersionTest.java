package com.euphoriapatches.euphoria_patcher.integration;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the version number that is written into the shader as a define. A GLSL number has to be made of plain digits,
 * whatever language the player's system is set to.
 * Tests have been mostly written with the help of LLMs but they have been reviewed manually.
 */
class DefineHelperVersionTest {

    @BeforeAll
    static void setUpEnvironment() {
        TestEnvironment.init();
    }

    @Test
    void theVersionIsMajorThenMinorAndPatchWithTwoDigitsEach() {
        String[] parts = EuphoriaPatcher.PATCH_VERSION.replace("_", "").split("[.]");
        String expected = Integer.parseInt(parts[0])
                + String.format(Locale.ROOT, "%02d", Integer.parseInt(parts[1]))
                + String.format(Locale.ROOT, "%02d", Integer.parseInt(parts[2]));

        assertEquals(expected, DefineHelper.getFormattedEPVersion());
    }

    @Test
    void theVersionOnlyHasPlainDigitsInLanguagesWithOtherDigits() {
        Locale original = Locale.getDefault();
        try {
            // Arabic, Persian and Bengali have their own digits since newer Java versions, Hindi had them on Java 8
            for (String tag : new String[]{"ar-SA", "ar-EG", "fa-IR", "bn-BD", "hi-IN", "th-TH"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));

                String version = DefineHelper.getFormattedEPVersion();

                for (int i = 0; i < version.length(); i++) {
                    char c = version.charAt(i);
                    assertTrue(c >= '0' && c <= '9', "default locale " + tag + " gave the version " + version);
                }
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
