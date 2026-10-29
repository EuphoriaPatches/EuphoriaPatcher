package com.euphoriapatches.euphoria_patcher.features.steganography;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SteganographyCodecTest {
    private static final String SETTINGS_TEXT = "# Euphoria Patches settings\nSHADOW_QUALITY=2\nCLOUD_STYLE=3\nWAVING_LEAVES=true\n"
            + "sliders: 0.25 0.5 0.75 1.0 \u00e9\u00e8\u20ac \u65e5\u672c\u8a9e";

    /** Random noise like the blue channel of a real image */
    private static byte[] noise(int length) {
        byte[] data = new byte[length];
        new Random(67).nextBytes(data);
        return data;
    }

    @Test
    void textComesBackExactlyAsItWasEmbedded() {
        byte[] channel = noise(5000);

        assertTrue(SteganographyCodec.encode(channel, SETTINGS_TEXT, true));

        assertEquals(SETTINGS_TEXT, SteganographyCodec.decode(channel));
    }

    @Test
    void longTextRoundTrips() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            text.append("SETTING_").append(i).append('=').append(i % 7).append('\n');
        }
        byte[] channel = noise(100000);

        assertTrue(SteganographyCodec.encode(channel, text.toString(), false));

        assertEquals(text.toString(), SteganographyCodec.decode(channel));
    }

    @Test
    void onlyTheLowestBitOfEachByteIsChanged() {
        byte[] original = noise(5000);
        byte[] channel = original.clone();

        SteganographyCodec.encode(channel, SETTINGS_TEXT, true);

        for (int i = 0; i < channel.length; i++) {
            assertEquals(original[i] & 0xFE, channel[i] & 0xFE, "upper 7 bits changed at index " + i);
        }
    }

    @Test
    void bytesAfterThePayloadAreLeftAlone() {
        byte[] original = noise(5000);
        byte[] channel = original.clone();
        long used = SteganographyCodec.requiredChannelBits(SETTINGS_TEXT);

        SteganographyCodec.encode(channel, SETTINGS_TEXT, true);

        for (int i = (int) used; i < channel.length; i++) {
            assertEquals(original[i], channel[i], "byte after the payload changed at index " + i);
        }
    }

    @Test
    void theEuphoriaShaderFlagIsStoredRightAfterTheHeader() {
        byte[] withFlag = noise(5000);
        byte[] withoutFlag = noise(5000);

        SteganographyCodec.encode(withFlag, SETTINGS_TEXT, true);
        SteganographyCodec.encode(withoutFlag, SETTINGS_TEXT, false);

        // Header is 12 bytes = 96 bits, the flag is the bit right after it
        assertEquals(1, withFlag[96] & 1);
        assertEquals(0, withoutFlag[96] & 1);
        assertEquals(SETTINGS_TEXT, SteganographyCodec.decode(withFlag));
        assertEquals(SETTINGS_TEXT, SteganographyCodec.decode(withoutFlag));
    }

    @Test
    void encodingFillsExactlyTheRequiredBits() {
        int required = (int) SteganographyCodec.requiredChannelBits(SETTINGS_TEXT);

        byte[] exact = noise(required);
        assertTrue(SteganographyCodec.encode(exact, SETTINGS_TEXT, true));
        assertEquals(SETTINGS_TEXT, SteganographyCodec.decode(exact));

        byte[] oneTooSmall = noise(required - 1);
        byte[] untouched = oneTooSmall.clone();
        assertFalse(SteganographyCodec.encode(oneTooSmall, SETTINGS_TEXT, true));
        assertArrayEquals(untouched, oneTooSmall, "a failed encode must not modify the data");
    }

    @Test
    void capacityMatchesWhatEncodingNeeds() {
        long required = SteganographyCodec.requiredChannelBits(SETTINGS_TEXT);
        int width = 100;
        int heightThatFits = (int) Math.ceil(required / (double) width);

        assertTrue(SteganographyCodec.hasCapacity(width, heightThatFits, SETTINGS_TEXT));
        assertFalse(SteganographyCodec.hasCapacity(width, heightThatFits - 1, SETTINGS_TEXT));
        assertFalse(SteganographyCodec.hasCapacity(8, 8, SETTINGS_TEXT));
    }

    @Test
    void capacityRejectsNonsenseInput() {
        assertFalse(SteganographyCodec.hasCapacity(1000, 1000, null));
        assertFalse(SteganographyCodec.hasCapacity(1000, 1000, ""));
        assertFalse(SteganographyCodec.hasCapacity(0, 1000, SETTINGS_TEXT));
        assertFalse(SteganographyCodec.hasCapacity(1000, -1, SETTINGS_TEXT));
    }

    @Test
    void requiredBitsGrowWithTheText() {
        assertEquals(0, SteganographyCodec.requiredChannelBits(null));
        assertTrue(SteganographyCodec.requiredChannelBits("a") > 97, "even one letter needs the header and the flag");

        StringBuilder noisy = new StringBuilder();
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            noisy.append((char) ('a' + random.nextInt(26)));
        }
        assertTrue(SteganographyCodec.requiredChannelBits(noisy.toString()) > SteganographyCodec.requiredChannelBits("short text"));
    }

    @Test
    void encodeRefusesMissingInput() {
        assertFalse(SteganographyCodec.encode(null, SETTINGS_TEXT, true));
        assertFalse(SteganographyCodec.encode(noise(5000), null, true));
        assertFalse(SteganographyCodec.encode(noise(5000), "", true));
    }

    @Test
    void decodeFindsNothingInAnImageThatWasNeverTagged() {
        assertNull(SteganographyCodec.decode(noise(5000)));
        assertNull(SteganographyCodec.decode(new byte[5000]));
        byte[] allOnes = new byte[5000];
        Arrays.fill(allOnes, (byte) 0xFF);
        assertNull(SteganographyCodec.decode(allOnes));
    }

    @Test
    void decodeHandlesTooLittleData() {
        assertNull(SteganographyCodec.decode(null));
        assertNull(SteganographyCodec.decode(new byte[0]));
        assertNull(SteganographyCodec.decode(new byte[50]));
    }

    @Test
    void decodeRejectsTruncatedData() {
        byte[] channel = noise(5000);
        SteganographyCodec.encode(channel, SETTINGS_TEXT, true);
        int required = (int) SteganographyCodec.requiredChannelBits(SETTINGS_TEXT);

        assertNull(SteganographyCodec.decode(Arrays.copyOf(channel, required - 8)));
    }

    @Test
    void decodeRejectsCorruptedPayload() {
        byte[] channel = noise(5000);
        SteganographyCodec.encode(channel, SETTINGS_TEXT, true);

        // Flip one bit inside the compressed data, the checksum no longer matches
        channel[120] ^= 1;

        assertNull(SteganographyCodec.decode(channel));
    }

    @Test
    void decodeRejectsACorruptedHeader() {
        byte[] channel = noise(5000);
        SteganographyCodec.encode(channel, SETTINGS_TEXT, true);

        channel[3] ^= 1; // inside the magic letters

        assertNull(SteganographyCodec.decode(channel));
    }
}
