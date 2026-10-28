package com.euphoriapatches.euphoria_patcher.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.euphoriapatches.shaderpatcher.generate.GenerateRequest;
import com.euphoriapatches.shaderpatcher.generate.GeneratedPatch;
import com.euphoriapatches.shaderpatcher.generate.PatchGenerator;
import com.euphoriapatches.euphoria_patcher.PatchInfo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Patch generator for development purposes, run with {@code ./gradlew :tools:generatePatch}.
 * It lives in the tools project because it is not part of the mod and works without Minecraft.
 * Usage:
 * 1. Ensure you have the base shader and the Euphoria-Patches folder in your shaderpacks folder
 *    (the base shader is downloaded from Modrinth if it is missing)
 * 2. Run the Gradle task
 * 3. The .patch files will be generated in common/src/main/resources and EuphoriaPatchFiles
 * Name and version of the patch come from {@link PatchInfo}, the same file the mod uses.
 * The versions inside the Euphoria-Patches folder are rewritten to match it, see {@link #syncVersions}.
 */
public class GeneratePatch {
    // ANSI escape codes for colored output
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";

    // Constants - use PatchInfo directly to avoid EuphoriaPatcher initialization
    private static final String BRAND_NAME = PatchInfo.BRAND_NAME;
    private static final String PATCH_NAME = PatchInfo.PATCH_NAME;
    private static final String VERSION = PatchInfo.VERSION;
    private static final String PATCH_VERSION = PatchInfo.PATCH_VERSION;

    // Modrinth API constants
    private static final String MODRINTH_PROJECT_ID = "HVnmMxH1";
    private static final String MODRINTH_API_BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "EuphoriaPatcher-DevPatchGenerator/1.0";

    // Get the IntelliJ project base directory
    private static final Path INTELLIJ_BASE_DIR = Paths.get("").toAbsolutePath();

    // Derive paths from the base directory
    private static final Path RESOURCES_DIR = INTELLIJ_BASE_DIR.resolve("common/src/main/resources");
    private static final Path PATCH_FILES_DIR = INTELLIJ_BASE_DIR.resolve("EuphoriaPatchFiles");
    private static final Path SHADERPACKS_DIR = INTELLIJ_BASE_DIR.resolve("shaderpacks");

    public static void main(String[] args) {
        System.out.println(BLUE + "=== Euphoria Patches - Dev Patch Generator ===" + RESET);
        System.out.println("IntelliJ Base Directory: " + BLUE + INTELLIJ_BASE_DIR + RESET);
        System.out.println();

        // Ensure shaderpacks folder exists
        try {
            ensureDirectoryExists(SHADERPACKS_DIR);
        } catch (IOException e) {
            System.err.println(RED + "ERROR: Could not create shaderpacks folder!" + RESET);
        }

        System.out.println("Shaderpacks folder: " + BLUE + SHADERPACKS_DIR + RESET);
        System.out.println();

        // Clean up old shader zip files
        try {
            cleanupOldShaderZips();
        } catch (IOException e) {
            System.err.println(YELLOW + "Warning: Could not clean up old shader zips: " + e.getMessage() + RESET);
        }

        try {
            // Find base and patched shaders
            ShaderPair shaderPair = findShaders();

            // If base shader is missing, try to download it
            if (shaderPair == null || shaderPair.baseShader == null) {
                System.out.println(YELLOW + "Base shader not found locally. Attempting to download from Modrinth..." + RESET);
                Path downloadedShader = downloadBaseShaderFromModrinth();
                if (downloadedShader != null) {
                    shaderPair = findShaders(); // Re-scan after download
                }
            }

            // Final check after potential download
            if (shaderPair == null || shaderPair.baseShader == null || shaderPair.patchedShader == null) {
                System.err.println(RED + "ERROR: Could not find both base and patched shaders!" + RESET);
                throw new RuntimeException("Missing required shader files");
            }

            System.out.println(GREEN + "Found base shader: " + BLUE + shaderPair.baseShader.getFileName() + RESET);
            System.out.println(GREEN + "Found patched shader: " + BLUE + shaderPair.patchedShader.getFileName() + RESET);
            System.out.println();

            // Generate patch files
            generatePatchFiles(shaderPair);

            System.out.println();
            System.out.println(GREEN + "=== Patch generation completed successfully! ===" + RESET);

        } catch (Exception e) {
            System.err.println(RED + "ERROR: " + e.getMessage() + RESET);
            e.printStackTrace();
            System.exit(1); // makes the Gradle task fail
        }
    }

    /**
     * Cleans up old shader zip files that don't match the current VERSION
     */
    private static void cleanupOldShaderZips() throws IOException {
        ensureDirectoryExists(SHADERPACKS_DIR);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SHADERPACKS_DIR)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();

                // Only consider zip files
                if (!name.endsWith(".zip")) {
                    continue;
                }

                if (name.startsWith(BRAND_NAME)) {
                    // If it doesn't contain the current VERSION, delete it
                    if (!name.contains(VERSION)) {
                        System.out.println(YELLOW + "Deleting outdated shader zip: " + BLUE + name + RESET);
                        try {
                            Files.delete(path);
                            System.out.println(GREEN + "✓ Deleted: " + name + RESET);
                        } catch (IOException e) {
                            System.err.println(RED + "Failed to delete " + name + ": " + e.getMessage() + RESET);
                        }
                    }
                }
            }
        }
    }

    /**
     * Downloads the base shader from Modrinth if not found locally
     */
    private static Path downloadBaseShaderFromModrinth() {
        try {
            System.out.println(YELLOW + "Fetching latest version from Modrinth API..." + RESET);

            // Fetch project versions from Modrinth API
            String apiUrl = MODRINTH_API_BASE + "/project/" + MODRINTH_PROJECT_ID + "/version";
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                System.err.println(RED + "ERROR: Modrinth API returned status " + conn.getResponseCode() + RESET);
                return null;
            }

            // Parse JSON response
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();

            // Get the latest version (first in the array)
            if (versions.isEmpty()) {
                System.err.println(RED + "ERROR: No versions found on Modrinth!" + RESET);
                return null;
            }

            JsonObject latestVersion = versions.get(0).getAsJsonObject();
            String versionNumber = latestVersion.get("version_number").getAsString();
            System.out.println(GREEN + "Found latest version: " + versionNumber + RESET);

            // Get the first file (there's only one)
            JsonArray files = latestVersion.getAsJsonArray("files");
            JsonObject file = files.get(0).getAsJsonObject();

            String downloadUrl = file.get("url").getAsString();
            String fileName = file.get("filename").getAsString();

            System.out.println(YELLOW + "Downloading: " + BLUE + fileName + RESET);
            System.out.println("From: " + downloadUrl);

            // Download the file
            Path outputPath = SHADERPACKS_DIR.resolve(fileName);
            HttpURLConnection downloadConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            downloadConn.setRequestProperty("User-Agent", USER_AGENT);

            long fileSize = downloadConn.getContentLengthLong();
            System.out.println("File size: " + BLUE + (fileSize / 1024 / 1024) + " MB" + RESET);

            try (InputStream in = downloadConn.getInputStream();
                 FileOutputStream out = new FileOutputStream(outputPath.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;
                int lastPercentage = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    int percentage = (int) ((totalBytesRead * 100) / fileSize);
                    if (percentage > lastPercentage && percentage % 10 == 0) {
                        System.out.println("  Progress: " + percentage + "%");
                        lastPercentage = percentage;
                    }
                }
            }

            System.out.println(GREEN + "✓ Successfully downloaded: " + fileName + RESET);
            return outputPath;

        } catch (Exception e) {
            System.err.println(RED + "ERROR: Failed to download from Modrinth: " + e.getMessage() + RESET);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Finds the base and patched shader files
     */
    private static ShaderPair findShaders() {
        // Check if directory exists and is not empty
        if (!Files.exists(SHADERPACKS_DIR) || !Files.isDirectory(SHADERPACKS_DIR)) {
            System.err.println(RED + "ERROR: shaderpacks directory not found at: " + SHADERPACKS_DIR + RESET);
            return null;
        }

        Path baseShader = null;
        Path patchedShader = null;

        // Look for base shader (e.g., ComplementaryReimagined_r5.3.zip)
        // and patched shader (e.g., ComplementaryReimagined_r5.3 + EuphoriaPatches_1.7.8 OR Euphoria-Patches directory)
        try {
            for (Path path : Files.newDirectoryStream(SHADERPACKS_DIR)) {
                String name = path.getFileName().toString();

                // Check if it's a base shader
                if (name.startsWith(BRAND_NAME) &&
                    name.contains(VERSION) &&
                    !name.contains(PATCH_NAME)) {
                    baseShader = path;
                }

                // Check if it's the standard patched shader format
                if (name.contains(BRAND_NAME) &&
                    name.contains(" + " + PATCH_NAME + PATCH_VERSION)) {
                    patchedShader = path;
                }

                // Check if it's the Euphoria-Patches directory
                if (name.equals("Euphoria-Patches") && Files.isDirectory(path)) {
                    Path gitDir = path.resolve(".git");
                    if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                        // The versions inside get updated to PatchInfo in generatePatchFiles
                        patchedShader = path;
                    } else {
                        System.err.println(YELLOW + "Warning: Euphoria-Patches directory found but missing .git folder" + RESET);
                    }
                }

                if (baseShader != null && patchedShader != null) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println(RED + "ERROR: Failed to read shaderpacks directory: " + e.getMessage() + RESET);
            return null;
        }

        // Return the ShaderPair even if some are null - let the caller handle it
        if (baseShader == null && patchedShader == null) {
            // Both missing - likely first time running, no need to log
            return new ShaderPair(baseShader, patchedShader);
        }

        if (patchedShader == null) {
            System.err.println(YELLOW + "Patched shader not found in " + SHADERPACKS_DIR + RESET);
            System.err.println("Need either:");
            System.err.println("  - " + BRAND_NAME + "Reimagined" + VERSION + " + " + PATCH_NAME + PATCH_VERSION);
            System.err.println("  - Euphoria-Patches directory with .git folder");
        }

        return new ShaderPair(baseShader, patchedShader);
    }

    /**
     * Generates the .patch files
     */
    private static void generatePatchFiles(ShaderPair shaderPair) throws Exception {
        // The Euphoria-Patches folder is the working copy, so it is brought to the versions of PatchInfo
        if (Files.isDirectory(shaderPair.patchedShader)) {
            syncVersions(shaderPair.patchedShader);
            ensureVersionDefine(shaderPair.patchedShader);
        }

        // Ensure output directories exist
        ensureDirectoryExists(RESOURCES_DIR);
        ensureDirectoryExists(PATCH_FILES_DIR);

        String patchFileName = PATCH_NAME + PATCH_VERSION + ".patch";

        // Delete outdated patch files in resources dir
        try {
            for (Path path : Files.newDirectoryStream(RESOURCES_DIR)) {
                String fileName = path.getFileName().toString();
                if (fileName.endsWith(".patch")
                        && fileName.contains(PATCH_NAME)
                        && !fileName.contains(PATCH_VERSION)) {
                    System.out.println(YELLOW + "Deleting outdated patch file: " + BLUE + path + RESET);
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println(RED + "Failed to delete: " + path + " (" + e.getMessage() + ")" + RESET);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(RED + "Error checking for outdated patch files: " + e.getMessage() + RESET);
        }

        System.out.println(YELLOW + "Generating patch file: " + patchFileName + RESET);
        System.out.println(YELLOW + "This may take a moment..." + RESET);

        // The library archives both shaders, creates the patch, checks that it reproduces the patched shader,
        // and stores the fingerprint of the base shader inside the patch file
        GeneratedPatch generated = PatchGenerator.generate(GenerateRequest.builder(PatchInfo.SPEC)
                .base(shaderPair.baseShader)
                .edited(shaderPair.patchedShader)
                .editedExclude(GeneratePatch::isDevOnlyFile)
                .addOutputDir(RESOURCES_DIR)
                .addOutputDir(PATCH_FILES_DIR)
                .debugLog(message -> System.out.println("  ShaderPatcher: " + message))
                .build());

        System.out.println("Base archive:    " + BLUE + generated.getMetadata().getBaseFingerprint() + RESET);
        System.out.println("Patched archive: " + BLUE + generated.getMetadata().getPatchedFingerprint() + RESET);
        for (Path file : generated.getPatchFiles()) {
            System.out.println(GREEN + "✓ Created: " + BLUE + file + RESET);
        }
    }

    /**
     * Sets the versions in the Euphoria-Patches folder to the ones of {@link PatchInfo}.
     */
    private static void syncVersions(Path folder) throws IOException {
        String patchVersion = PATCH_VERSION.replace("_", "");
        String baseVersion = VERSION.replace("_", "");

        // Names can contain non-breaking spaces (U+00A0), the color code is "§d" in lang files and written out in pack.json
        String space = "[ \\u00A0]+";
        Pattern patchPattern = Pattern.compile("(?<p>\"version\"\\s*:\\s*\"|Euphoria" + space + "Patches" + space
                + "(?:\\u00A7d|\\\\u00A7d)?)\\d+(?:\\.\\d+)*");
        Pattern basePattern = Pattern.compile("(?<p>Complementary(?:" + space + "Shaders)?" + space + ")r\\d+(?:\\.\\d+)*");

        try (DirectoryStream<Path> madeFor = Files.newDirectoryStream(folder, "Made for Complementary Shaders *.txt")) {
            for (Path file : madeFor) {
                Files.move(file, folder.resolve("Made for Complementary Shaders " + baseVersion + ".txt"));
            }
        }

        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                String relative = folder.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith(".git") || relative.startsWith(".vscode")
                        || !relative.matches(".+\\.(json|lang|glsl|txt|md|properties)")) {
                    continue;
                }
                String text = Files.readString(file);
                String updated = patchPattern.matcher(text).replaceAll("${p}" + Matcher.quoteReplacement(patchVersion));
                updated = basePattern.matcher(updated).replaceAll("${p}" + Matcher.quoteReplacement(baseVersion));
                if (!updated.equals(text)) {
                    Files.writeString(file, updated);
                    System.out.println("  Updated " + relative);
                }
            }
        }
    }

    /**
     * Adds the {@code AAB_FOR_EUPHORIA_PATCHES_VERSION_} define of {@link PatchInfo#PATCH_VERSION} to
     * settingsFileDefines.glsl if it is not in there yet.
     */
    private static void ensureVersionDefine(Path folder) throws IOException {
        Path defines = folder.resolve("shaders/lib/shaderSettings/settingsFileDefines.glsl");
        if (!Files.isRegularFile(defines)) {
            System.out.println(YELLOW + "settingsFileDefines.glsl not found, skipping version define" + RESET);
            return;
        }
        String define = "AAB_FOR_EUPHORIA_PATCHES_VERSION_" + PATCH_VERSION.replace("_", "").replace('.', '_');

        if (Files.readString(defines).contains(define)) {
            return;
        }

        String lines = "//#define " + define + "\n#ifdef " + define + "\n#endif\n";
        Files.writeString(defines, lines, StandardOpenOption.APPEND);
        System.out.println("  Added " + define + " to settingsFileDefines.glsl");
    }

    /**
     * Files of the Euphoria-Patches repository that must not end up in the patch: git/editor folders at the top level,
     * zips and the helper scripts and templates for developers at any depth
     */
    private static boolean isDevOnlyFile(String name) {
        if (name.equals(".github") || name.equals(".vscode")) { // ".git" is always left out by the library
            return true;
        }
        String fileName = name.substring(name.lastIndexOf('/') + 1);
        return fileName.equals("propertiesFragmenter.py") || fileName.equals("settingsRanges.py")
                || fileName.equals("TextToGLSLText.py") || fileName.endsWith(".eptext") || fileName.endsWith(".zip");
    }

    /**
     * Ensures a directory exists, creating it if necessary
     */
    private static void ensureDirectoryExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            System.out.println(YELLOW + "Creating directory: " + BLUE + dir + RESET);
            Files.createDirectories(dir);
        }
    }

    /**
     * Helper class to hold base and patched shader paths
     */
    private static class ShaderPair {
        final Path baseShader;
        final Path patchedShader;

        ShaderPair(Path baseShader, Path patchedShader) {
            this.baseShader = baseShader;
            this.patchedShader = patchedShader;
        }
    }
}
