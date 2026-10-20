package com.euphoriapatches.euphoria_patcher.util;

import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for cryptographic hashing operations
 */
public class HashUtils {

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[HashUtils] " + message);
    }

    /**
     * Calculates the SHA-256 hash of a string
     * @param input String to hash
     * @return Hexadecimal string representation of the hash, or null if an error occurs
     */
    public static String calculateSHA256(String input) {
        if (input == null) {
            debugLog("Cannot calculate SHA-256 of null string");
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            debugLog("Error calculating SHA-256: " + e.getMessage());
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
