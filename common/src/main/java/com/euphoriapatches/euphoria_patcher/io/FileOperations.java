package com.euphoriapatches.euphoria_patcher.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileOperations {

    /**
     * Read a file's content as a string
     * @param filePath Path to the file
     * @return The file content as a String, or null if the file does not exist, cannot be read or is not valid UTF-8
     */
    public static String readFileAsString(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return null;
        }

        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(filePath))).toString();
        } catch (IOException e) {
            return null;
        }
    }
}
