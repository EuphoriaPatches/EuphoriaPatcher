package com.euphoriapatches.euphoria_patcher.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileOperationsTest {

    @TempDir
    Path tempDir;

    @Test
    void readsTheWholeFileAndKeepsLineBreaks() throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.write(file, "line one\r\nline two\nline three".getBytes(StandardCharsets.UTF_8));

        assertEquals("line one\r\nline two\nline three", FileOperations.readFileAsString(file));
    }

    @Test
    void readsUtf8() throws IOException {
        Path file = tempDir.resolve("utf8.txt");
        Files.write(file, "caf\u00e9 \u20ac".getBytes(StandardCharsets.UTF_8));

        assertEquals("caf\u00e9 \u20ac", FileOperations.readFileAsString(file));
    }

    @Test
    void emptyFileGivesEmptyString() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.write(file, new byte[0]);

        assertEquals("", FileOperations.readFileAsString(file));
    }

    @Test
    void returnsNullForMissingFileAndNullPath() {
        assertNull(FileOperations.readFileAsString(tempDir.resolve("nope.txt")));
        assertNull(FileOperations.readFileAsString(null));
    }

    @Test
    void returnsNullForInvalidUtf8InsteadOfGuessing() throws IOException {
        Path file = tempDir.resolve("broken.txt");
        Files.write(file, new byte[]{'a', (byte) 0xff, (byte) 0xfe, 'b'});

        assertNull(FileOperations.readFileAsString(file));
    }
}
