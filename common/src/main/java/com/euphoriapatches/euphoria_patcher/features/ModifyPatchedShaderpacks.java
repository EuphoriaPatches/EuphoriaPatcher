package com.euphoriapatches.euphoria_patcher.features;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ModifyPatchedShaderpacks {

    private static final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ModifyPatchedShaderpacks] " + message);
    }

    /**
     * Modifies files in shader packs based on specified path and file extension
     *
     * @param patchedFile The shader pack file or directory
     * @param styleUnbound Whether to include Unbound style
     * @param styleReimagined Whether to include Reimagined style
     * @param targetPath Path relative to shader pack that should be modified (file or directory)
     * @param fileExtension File extension filter for directory paths (e.g. ".lang"), null for single files
     * @param regexAndReplacements Pairs of regex patterns and their replacements
     * @throws IOException If an I/O error occurs
     */
    public static void modifyFiles(Path patchedFile, boolean styleUnbound, boolean styleReimagined,
                                   String targetPath, String fileExtension, String... regexAndReplacements) throws IOException {
        debugLog("Starting to modify files in '" + patchedFile.getFileName() + "', target path: " + targetPath);
        if (regexAndReplacements.length % 2 != 0) {
            EuphoriaPatcher.log(2, 0, "Regex and replacement pairs must be provided");
            return;
        }

        processShaderPacks(patchedFile, styleUnbound, styleReimagined, shaderPack -> {
            try {
                // Only process directories - skip ZIP files to avoid corruption issues
                if (Files.isRegularFile(shaderPack) && shaderPack.toString().endsWith(".zip")) {
                    debugLog("Skipping ZIP file modification (not supported): " + shaderPack.getFileName());
                    return;
                }
                processDirectoryShaderpack(shaderPack, targetPath, fileExtension, regexAndReplacements);
            } catch (IOException e) {
                EuphoriaPatcher.log(2, 0, "Error processing files in " + shaderPack.getFileName() + ": " + e.getMessage());
            }
        });

        debugLog("Finished modifying files for target path: " + targetPath);
    }

    private static void processDirectoryShaderpack(Path shaderPack, String targetPath, String fileExtension,
                                               String... regexAndReplacements) throws IOException {
        Path resolvedPath = shaderPack.resolve(targetPath);
        debugLog("Processing in shader pack directory: " + shaderPack.getFileName());

        if (fileExtension != null && Files.isDirectory(resolvedPath)) {
            // Process directory with file extension filter
            debugLog("Processing directory: " + resolvedPath + " with extension filter: " + fileExtension);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(resolvedPath, "*" + fileExtension)) {
                for (Path file : files) {
                    modifyFile(file, regexAndReplacements);
                }
            }
        } else if (Files.exists(resolvedPath)) {
            // Process single file
            debugLog("Processing single file: " + resolvedPath);
            try {
                modifyFile(resolvedPath, regexAndReplacements);
            } catch (IOException e) {
                debugLog("Error processing file: " + e.getMessage());
            }
        } else {
            debugLog("Target path not found: " + resolvedPath);
        }
    }

    private static void modifyFile(Path filePath, String... regexAndReplacements) throws IOException {
        debugLog("Modifying file: " + filePath.getFileName());

        // For larger files (>100KB), use line-by-line processing
        // Make sure that a regex only matches 1 line. Because bigger files only the 1st one is matched
        if (Files.size(filePath) > 100_000) {
            debugLog("File size is larger than 100KB, using line-by-line modification for: " + filePath.getFileName());
            try {
                lineByLineModify(filePath, regexAndReplacements);
            } catch (IOException e) {
                debugLog("Error during line-by-line modification: " + e.getMessage());
            }
        } else {
            // Use existing approach for smaller files
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            String modifiedContent = applyReplacements(content, regexAndReplacements);
            Files.write(filePath, modifiedContent.getBytes(StandardCharsets.UTF_8));
        }

        debugLog("Successfully modified file: " + filePath.getFileName());
    }

    private static void lineByLineModify(Path filePath, String... regexAndReplacements) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        boolean modified = false;

        // Prepare all regex patterns (with caching)
        List<Pattern> patterns = new ArrayList<>();
        boolean[] patternMatched = new boolean[regexAndReplacements.length / 2]; // Track which patterns were matched
        int totalPatterns = regexAndReplacements.length / 2;
        int matchedPatterns = 0;

        for (int i = 0; i < regexAndReplacements.length; i += 2) {
            String regex = regexAndReplacements[i];
            patterns.add(patternCache.computeIfAbsent(regex, Pattern::compile));
        }

        // Process each line
        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum);

            // Apply replacements for this line
            for (int i = 0; i < patterns.size(); i++) {
                // Skip patterns we've already matched
                if (patternMatched[i]) continue;

                Pattern pattern = patterns.get(i);
                String replacement = regexAndReplacements[i * 2 + 1];

                if (pattern.matcher(line).find()) {
                    // Only replace if pattern matches this line
                    String newLine = line.replaceAll(pattern.pattern(), replacement);
                    if (!newLine.equals(line)) {
                        lines.set(lineNum, newLine);
                        modified = true;
                        patternMatched[i] = true;
                        matchedPatterns++;
                        debugLog("Match found and replaced on line " + (lineNum + 1) + " for pattern: " + pattern.pattern());
                    }
                }
            }

            // Exit early if we've matched all patterns
            if (matchedPatterns == totalPatterns) {
                debugLog("All " + totalPatterns + " patterns matched - exiting file scan early at line " + (lineNum + 1));
                break;
            }
        }

        // Only write the file if changes were made
        if (modified) {
            Files.write(filePath, lines);
        }
    }

    private static void processShaderPacks(Path patchedFile, boolean styleUnbound, boolean styleReimagined, Consumer<Path> processor) {
        debugLog("Processing shader packs with styleUnbound=" + styleUnbound + ", styleReimagined=" + styleReimagined);
        List<Path> shaderPacks = getShaderPacks(patchedFile, styleUnbound, styleReimagined);
        debugLog("Found " + shaderPacks.size() + " shader packs to process");
        for (Path shaderPack : shaderPacks) {
            if (Files.exists(shaderPack)) {
                processor.accept(shaderPack);
            } else {
                debugLog("Shader pack not found: " + shaderPack);
            }
        }
    }

    private static String applyReplacements(String content, String... regexAndReplacements) {
        debugLog("Applying " + (regexAndReplacements.length / 2) + " regex replacements");
        String modifiedContent = content;
        for (int i = 0; i < regexAndReplacements.length; i += 2) {
            String regex = regexAndReplacements[i];
            String replacement = regexAndReplacements[i + 1];
            debugLog("Applying regex: '" + regex + "' -> '" + replacement + "'");
            modifiedContent = modifiedContent.replaceAll(regex, replacement);
        }
        return modifiedContent;
    }

    private static List<Path> getShaderPacks(Path patchedFile, boolean styleUnbound, boolean styleReimagined) {
        debugLog("Getting shader packs from: " + patchedFile.getFileName());
        List<Path> shaderPacks = new ArrayList<>();
        shaderPacks.add(patchedFile);

        if (styleUnbound && styleReimagined) {
            Path otherStylePath = patchedFile.getFileName().toString().contains("Reimagined")
                    ? patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Reimagined", "Unbound"))
                    : patchedFile.resolveSibling(patchedFile.getFileName().toString().replace("Unbound", "Reimagined"));

            debugLog("Looking for other style at: " + otherStylePath.getFileName());
            if (Files.exists(otherStylePath)) {
                debugLog("Found other style shader pack: " + otherStylePath.getFileName());
                shaderPacks.add(otherStylePath);
            } else {
                debugLog("Other style shader pack not found: " + otherStylePath.getFileName());
            }
        }
        return shaderPacks;
    }
}
