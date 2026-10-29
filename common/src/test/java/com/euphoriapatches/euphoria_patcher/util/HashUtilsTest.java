package com.euphoriapatches.euphoria_patcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HashUtilsTest {

    @Test
    void matchesKnownSha256Values() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashUtils.calculateSHA256("abc"));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", HashUtils.calculateSHA256(""));
    }

    @Test
    void hashesTheUtf8Bytes() {
        // SHA-256 of the two UTF-8 bytes of the e with an accent (c3 a9), written as an escape so the source encoding can't matter
        assertEquals("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c", HashUtils.calculateSHA256("\u00e9"));
        assertNotEquals(HashUtils.calculateSHA256("e"), HashUtils.calculateSHA256("\u00e9"));
    }

    @Test
    void returnsNullForNull() {
        assertNull(HashUtils.calculateSHA256(null));
    }
}
