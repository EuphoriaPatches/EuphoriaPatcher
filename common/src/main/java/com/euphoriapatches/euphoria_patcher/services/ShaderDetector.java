package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.io.FileOperations;
import com.euphoriapatches.euphoria_patcher.io.JsonUtilReader;
import com.euphoriapatches.euphoria_patcher.util.UserPersistentData;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderPropertyReader;
import com.euphoriapatches.euphoria_patcher.util.VersionComparator;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles detection of installed shaders and their properties
 */
public class ShaderDetector {
    private static final int[] INITIAL_EUPHORIA_CHECK_DELAYS_MS = {0, 50, 150, 300};
    private static final int REQUIRED_CONSECUTIVE_EUPHORIA_CHECKS = 2;

    // Static cache to store whether shaders are Euphoria Patches shaders (persists across instances)
    private static final Map<Path, Boolean> euphoriaShaderCache = new ConcurrentHashMap<>();
    // Static cache to store shader versions read from pack.json (persists across instances)
    // Uses Optional to distinguish between "not checked" (not in map), "no version" (Optional.empty()), and "version found" (Optional.of(version))
    private static final Map<Path, Optional<String>> versionCache = new ConcurrentHashMap<>();
    private final String brandName;
    private final String patchName;
    private final String version;
    private final String patchVersion;
    private final String commonLocation;
    private final String shaderMyFileLocation;
    private final Path shaderpacks;
    private final ShaderValidator shaderValidator;

    private int totalFilesToScan = 0;

    private final String buildDateStr;
    private final Integer currentBuildDate;

    private static final Pattern numberedDevPattern = Pattern.compile("Comp.*EuphoriaPatches_(\\d+\\.\\d+\\.\\d+)-dev\\d+\\.zip");
    private static final Pattern earlyDevPattern = Pattern.compile("EuphoriaPatches_earlyDev_(\\d{4}-\\d{2}-\\d{2})\\.zip");
    private static boolean hasAnyDevVersion = false;

    public ShaderDetector(String brandName, String patchName, String version, String patchVersion,
                         String commonLocation, String shaderMyFileLocation, Path shaderpacks) {
        this.brandName = brandName;
        this.patchName = patchName;
        this.version = version;
        this.patchVersion = patchVersion;
        this.commonLocation = commonLocation;
        this.shaderMyFileLocation = shaderMyFileLocation;
        this.shaderpacks = shaderpacks;
        // Circular dependency will be resolved by setter
        this.shaderValidator = new ShaderValidator();

        this.buildDateStr = JsonUtilReader.getString("buildDate");
        this.currentBuildDate = parseBuildDate(buildDateStr);
    }

    /**
     * Detect installed shaders
     */
    public ShaderInfo detectInstalledShaders(ShaderNamingService namingService) {
        ShaderInfo info = new ShaderInfo();
        try {
            // Check if patched shaders already exist
            checkForExistingPatchedShaders(info);
            if (info.isAlreadyInstalled) {
                // Check if we need to patch an additional style
                boolean successfulDataRead = checkForMissingStyle(info);
                if (!info.isAlreadyInstalled) {
                    debugLog("Found missing style to patch: " + (info.styleReimagined ? "Unbound" : "Reimagined"));
                    // Continue with normal patching flow for the missing style
                    return info;
                }
                if (successfulDataRead) {
                    if (info.styleReimagined && info.styleUnbound) debugLog("Both styles already installed, skipping detection");
                    else debugLog("User does not have the other style base shader installed, skipping detection");
                } else {
                    debugLog("Could not read data.json, skipping detection");
                }
                return info;
            }

            // Nothing can be patched without the bundled patch, so skip the scan. EuphoriaPatcher reports it to the user
            if (PatchLibrary.getFailure() != null) {
                debugLog("Bundled patch is unusable, skipping shader detection");
                return info;
            }

            // Find all potential shader paths (both files and directories) by name
            List<Path> potentialShaderPaths = new ArrayList<>();

            // Add files ending with .zip
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks,
                    path -> isBrandNameShader(path, true))) {
                stream.forEach(potentialShaderPaths::add);
            }

            // Add directories
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks,
                    path -> isBrandNameShader(path, false))) {
                stream.forEach(potentialShaderPaths::add);
            }

            // Process all found paths by name
            for (Path path : potentialShaderPaths) {
                processShaderPath(path, info, namingService);
                if (info.styleReimagined && info.styleUnbound) break;
            }

            // If no valid shader found by name, try using byte size verification
            if (info.baseFile == null) {
                log(2, 0, "No shaders with expected name pattern found, checking via byte size...");
                log(2, 0, "If you have a lot of shaders installed, this may take a while. Please be patient.");
                log(2, 0, "Please wait... \n");
                Path shaderByByteSize = findShaderByByteSize(namingService);
                if (shaderByByteSize != null) {
                    log(0, "Found valid shader by byte size: " + shaderByByteSize.getFileName());
                    // Determine shader style from path or assume default
                    String name = shaderByByteSize.getFileName().toString();
                    info.styleReimagined = name.contains("Reimagined") || !name.contains("Unbound");
                    info.styleUnbound = name.contains("Unbound");
                    info.baseFile = shaderByByteSize;
                    checkIfAlreadyInstalled(shaderByByteSize, info, namingService);
                }
            }
        } catch (IOException e) {
            log(3, "Error reading shaderpacks directory: " + e.getMessage());
        }
        return info;
    }

    /**
     * Checks for existing patched shader directories that may exist even if the base shader is gone
     */
    public void checkForExistingPatchedShaders(ShaderInfo info) {
        try {
            // First check for newer dev versions
            if (checkForNewerDevVersion(info)) {
                return;
            }

            // Then check using the standard naming pattern
            DirectoryStream.Filter<Path> patchedFilter = path ->
                path.getFileName().toString().contains(brandName) &&
                path.getFileName().toString().contains(" + " + patchName + patchVersion) &&
                (Files.isDirectory(path) ||
                (Files.isRegularFile(path) && path.toString().endsWith(".zip")));

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, patchedFilter)) {
                for (Path path : stream) {
                    checkIfAlreadyInstalled(path, info, null);
                    if (info.isAlreadyInstalled) {
                        return;
                    }
                }
            }

            debugLog("No existing patched shaders found by standard naming pattern, checking for Euphoria Patches files...");

            // If not found by name, check all directories for the myFile.glsl with version signature
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, Files::isDirectory)) {

                if (info.isAlreadyInstalled) {
                    return;
                }

                for (Path directory : stream) {
                    Path myFilePath = directory.resolve(shaderMyFileLocation);

                    debugLog("Checking directory: " + directory.getFileName() + " for myFile.glsl");

                    // Skip if the myFile.glsl doesn't exist
                    if (!Files.exists(myFilePath)) {
                        continue;
                    }

                    debugLog("Found myFile.glsl in directory: " + directory.getFileName());

                    // Read first line of the file
                    String firstLine;
                    try (BufferedReader reader = Files.newBufferedReader(myFilePath)) {
                        firstLine = reader.readLine();
                    }

                    // Check if it's a Euphoria Patches file with the matching version
                    if (firstLine != null && firstLine.startsWith("// Euphoria Patches")) {
                        String fileVersion = firstLine.replace("// Euphoria Patches ", "").trim();
                        String expectedVersion = patchVersion.replace("_", "");

                        debugLog("Found potential correct Euphoria Patches version in: " + directory.getFileName());
                        debugLog("File version: " + fileVersion + ", Expected: " + expectedVersion);

                        int comparison = VersionComparator.compareVersionStrings(fileVersion, expectedVersion);

                        if (comparison >= 0) {
                            String dirName = directory.getFileName().toString();
                            if (dirName.equals(("Euphoria-Patches")) || dirName.matches("dev\\d+") || dirName.contains("earlyDev")) {
                                debugLog("Skipping dev Euphoria-Patches versions");
                                continue;
                            }
                            if (comparison == 0) debugLog("Exact version match found");
                            else debugLog("File version is newer than expected version");
                            debugLog("Version match found - this is a correct Euphoria Patches installation");

                            info.isAlreadyInstalled = true;
                            info.installedDir = directory;

                            // Try to determine style from directory name or common.glsl
                            if (dirName.contains("Reimagined")) {
                                info.styleReimagined = true;
                            } else if (dirName.contains("Unbound")) {
                                info.styleUnbound = true;
                            } else {
                                // If not clear from directory name, check common.glsl
                                String detectedStyle = ShaderPropertyReader.detectStyleFromCommonFile(directory, commonLocation);
                                info.styleReimagined = "Reimagined".equals(detectedStyle);
                                info.styleUnbound = "Unbound".equals(detectedStyle);
                            }

                            log(0, patchName + patchVersion + " is already installed as the renamed folder: " + directory.getFileName());
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log(3, "Error checking for existing patched shaders: " + e.getMessage());
        }
    }

    /**
     * Check if a path matches the brand name shader pattern
     */
    public boolean isBrandNameShader(Path path, boolean isFile) {
        String name = path.getFileName().toString();

        debugLog("is " + name + " a brand name shader?");

        // Early exit for file type mismatch
        if (isFile) {
            if (!name.endsWith(".zip")) return false;
        } else {
            if (!Files.isDirectory(path)) return false;
        }

        // Basic conditions - check cheapest first
        if (!name.startsWith(brandName)) return false;
        if (name.contains(patchName)) return false;
        if (!name.contains(version)) return false;
        if (name.contains(" + ")) return false;

        // Exclude development or pre-release versions
        if (name.contains("_dev")) return false;
        if (name.contains("_pre")) return false;

        debugLog("Yes! " + name + " matches brand name shader pattern");
        return true;
    }

    /**
     * Find shader by byte size verification
     */
    public Path findShaderByByteSize(ShaderNamingService namingService) {
        try {
            // Reset counter at the start of a new scan
            resetFilesScannedCounter();

            // Collect files and directories, filtering out well-known popular shader names to save time
            List<Path> zipFiles = new ArrayList<>();
            List<Path> dirs = new ArrayList<>();
            int skippedCount = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path p : stream) {
                    boolean isZip = Files.isRegularFile(p) && p.toString().endsWith(".zip");
                    boolean isDir = Files.isDirectory(p);

                    if (!isZip && !isDir) continue;

                    if (isPopularShaderName(p)) {
                        skippedCount++;
                        continue;
                    }

                    if (isZip) {
                        zipFiles.add(p);
                    } else {
                        dirs.add(p);
                    }
                }
            }

            int zipFileCount = zipFiles.size();
            int dirCount = dirs.size();
            totalFilesToScan = zipFileCount + dirCount;
            debugLog("Total files to scan: " + totalFilesToScan + " (" + zipFileCount + " ZIP files, " + dirCount + " directories) - skipped " + skippedCount + " popular shaders");

            // Combine all paths for parallel processing
            List<Path> allPaths = new ArrayList<>(zipFiles);
            allPaths.addAll(dirs);

            // Use parallel validation with progress callback
            Path validShader = shaderValidator.validateByByteSizeParallel(allPaths,
                (scanned, total) -> log(2, 0, "Please wait... Scanned " + scanned + " of " + total + " files so far")
            );

            if (validShader != null) {
                // Found a valid shader by byte size, rename it to the correct format
                return namingService.renameToCorrectShaderName(validShader);
            }
        } catch (IOException e) {
            log(3, "Error searching for shaders by byte size: " + e.getMessage());
        }
        return null;
    }

    /**
     * Process shader path to determine style and check installation
     */
    public void processShaderPath(Path path, ShaderInfo info, ShaderNamingService namingService) {
        String name = path.getFileName().toString();

        // Check shader style from filename first
        boolean styleFromName = false;

        if (name.contains("Reimagined")) {
            info.styleReimagined = true;
            styleFromName = true;
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        } else if (name.contains("Unbound")) {
            info.styleUnbound = true;
            styleFromName = true;
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        }

        // If style isn't clear from the filename, check common.glsl
        if (!styleFromName) {
            String detectedStyle = ShaderPropertyReader.detectStyleFromCommonFile(path, commonLocation);
            if ("Reimagined".equals(detectedStyle)) {
                info.styleReimagined = true;
                if (info.baseFile == null) {
                    info.baseFile = path;
                }
            } else if ("Unbound".equals(detectedStyle)) {
                info.styleUnbound = true;
                if (info.baseFile == null) {
                    info.baseFile = path;
                }
            }
            log(0, "Shader style not in filename, detected " + detectedStyle + " from common.glsl");
        }

        checkIfAlreadyInstalled(path, info, namingService);
    }

    /**
     * Check if the patch is already installed
     */
    public void checkIfAlreadyInstalled(Path path, ShaderInfo info, ShaderNamingService namingService) {
        Path potentialInstallPath;
        boolean isDirectPatchedDir = path.getFileName().toString().contains(" + " + patchName + patchVersion);

        if (isDirectPatchedDir) {
            // This is already a patched shader directory
            potentialInstallPath = path;

            // Try to reconstruct the base file name
            String name = path.getFileName().toString();
            String baseName = name.substring(0, name.indexOf(" + " + patchName + patchVersion));
            Path potentialBaseZip = shaderpacks.resolve(baseName + ".zip");

            // Set shader styles based on directory name
            info.styleReimagined = name.contains("Reimagined");
            info.styleUnbound = name.contains("Unbound");

            if (Files.exists(potentialBaseZip)) {
                info.baseFile = potentialBaseZip;
            }
        } else {
            // This is a base shader file
            if (namingService != null) {
                potentialInstallPath = getPatchedShaderPath(path);
            } else {
                // Fallback if namingService is not available
                potentialInstallPath = null;
            }
            if (info.baseFile == null) {
                info.baseFile = path;
            }
        }

        // Skip check in certain situations
        if (info.isAlreadyInstalled || potentialInstallPath == null) {
            return;
        }

        // If the patched directory exists, check if it contains EuphoriaPatches files
        if (Files.exists(potentialInstallPath)) {
            verifyEuphoriaInstallation(potentialInstallPath, info);
        }
    }

    /**
     * Check if directory has Euphoria file
     */
    public boolean hasEuphoriaFile(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().contains("EuphoriaPatches"));
        }
    }

    /**
     * Verify Euphoria installation
     */
    public void verifyEuphoriaInstallation(Path potentialInstallPath, ShaderInfo info) {
        if (info.isAlreadyInstalled || potentialInstallPath == null) {
            return;
        }

        try {
            boolean containsEuphoria = hasEuphoriaFile(potentialInstallPath);

            if (containsEuphoria) {
                info.isAlreadyInstalled = true;
                info.installedDir = potentialInstallPath;
                log(0, patchName + patchVersion + " is already installed.");
            } else {
                log(0, "Found incomplete installation. Cleaning up " + potentialInstallPath.getFileName());
                ArchiveOperations.deleteRecursively(potentialInstallPath);
                info.isAlreadyInstalled = false;
            }

        } catch (IOException e) {
            log(3, "Error checking installation status. Cleaning up: " + e.getMessage());
            try {
                ArchiveOperations.deleteRecursively(potentialInstallPath);
            } catch (IOException ex) {
                log(3, "Error deleting directory: " + ex.getMessage());
            }
            info.isAlreadyInstalled = false;
        }
    }

    /**
     * Whether a shaderpack with exactly this name exists in the shaderpacks folder
     * @param name the exact name
     */
    public boolean hasShaderpackNamed(String name) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String baseName = fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                        ? fileName.substring(0, fileName.length() - ".zip".length())
                        : fileName;
                if (baseName.equals(name)) {
                    debugLog("Found shaderpack named \"" + name + "\": " + fileName);
                    return true;
                }
            }
        } catch (IOException e) {
            debugLog("Error checking for shaderpack \"" + name + "\": " + e.getMessage());
        }
        return false;
    }

    /**
     * Check if the user has any dev versions installed
     * @return true if no dev versions are installed
     */
    public boolean noDevVersionsInstalled() {
        // Return cached value if already checked
        if (hasAnyDevVersion) {
            return false;
        }

        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }

                    String fileName = path.getFileName().toString();

                    // Check both patterns
                    if (numberedDevPattern.matcher(fileName).matches() ||
                        earlyDevPattern.matcher(fileName).matches()) {
                        hasAnyDevVersion = true;
                        debugLog("Found dev version: " + fileName);
                        return false;
                    }
                }
            }
        } catch (IOException e) {
            debugLog("Error checking for dev versions: " + e.getMessage());
        }

        return true;
    }

    /**
     * Check if a given path represents a newer dev version
     * Supports two formats:
     * 1. Comp.*EuphoriaPatches_(version)-dev(number).zip
     * 2. EuphoriaPatches_earlyDev_(yyyy-mm-dd).zip
     * @param path The path to check
     * @param info Optional ShaderInfo to update if a newer dev version is found (can be null)
     * @return true if this is a newer dev version
     */
    public boolean isNewerDevVersion(Path path, ShaderInfo info) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        String fileName = path.getFileName().toString();

        String currentVersion = patchVersion.replace("_", "");

        // Check numbered dev version
        if (checkNumberedDevVersion(fileName, currentVersion)) {
            hasAnyDevVersion = true;
            if (info != null) {
                info.isAlreadyInstalled = true;
                info.installedDir = path;
                String devVersion = fileName.replaceAll(".*EuphoriaPatches_(\\d+\\.\\d+\\.\\d+)-dev\\d+\\.zip", "$1");
                log(0, "Your dev version: " + devVersion + " vs current public release version: " + currentVersion);
                log(0, "Your dev version is more recent than the last public release. Enjoy!");
                log(0, "Thanks for the support! <3");
            }
            return true;
        }

        // Check early dev version
        if (checkEarlyDevVersion(fileName, currentBuildDate)) {
            hasAnyDevVersion = true;
            if (info != null) {
                info.isAlreadyInstalled = true;
                info.installedDir = path;
                String devDateStr = fileName.replaceAll(".*EuphoriaPatches_earlyDev_(\\d{4}-\\d{2}-\\d{2})\\.zip", "$1");
                log(0, "Your earlyDev version date: " + devDateStr + " vs current build date: " + buildDateStr);
                log(0, "Your earlyDev version is more recent than the last public release. Enjoy!");
                log(0, "Thanks for the support! <3");
            }
            return true;
        }

        return false;
    }

    /**
     * Convenience method to check if a path is a newer dev version without updating ShaderInfo
     * @param path The path to check
     * @return true if this is a newer dev version
     */
    public boolean isNewerDevVersion(Path path) {
        return isNewerDevVersion(path, null);
    }

    /**
     * Check for newer dev versions of pre-patched shaders
     * Supports two formats:
     * 1. Comp.*EuphoriaPatches_(version)-dev(number).zip
     * 2. EuphoriaPatches_earlyDev_(yyyy-mm-dd).zip
     */
    private boolean checkForNewerDevVersion(ShaderInfo info) {
        try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }

                    // Use the consolidated isNewerDevVersion method
                    if (isNewerDevVersion(path, info)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            debugLog("Error checking for dev versions: " + e.getMessage());
        }
        return false;
    }

    public Integer parseBuildDate(String buildDateStr) {
        if (buildDateStr != null && !buildDateStr.equals("year-month-day")) {
            try {
                Integer date = Integer.parseInt(buildDateStr.replace("-", ""));
                debugLog("Current build date: " + buildDateStr + " (" + date + ")");
                return date;
            } catch (NumberFormatException e) {
                debugLog("Could not parse build date: " + buildDateStr);
            }
        }
        return null;
    }

    /**
     * Check if filename matches numbered dev version pattern and is newer
     * @return true if this is a newer numbered dev version
     */
    private boolean checkNumberedDevVersion(String fileName, String currentVersion) {
        Matcher matcher = ShaderDetector.numberedDevPattern.matcher(fileName);
        if (matcher.matches()) {
            String devVersion = matcher.group(1);
            debugLog("Found numbered dev version file: " + fileName + " with version: " + devVersion);

            int comparison = VersionComparator.compareVersionStrings(devVersion, currentVersion);

            if (comparison > 0) {
                debugLog("Dev version " + devVersion + " is newer than current patch version " + currentVersion);
                return true;
            } else {
                debugLog("Dev version " + devVersion + " is older or equal to current patch version " + currentVersion);
            }
        }
        return false;
    }

    /**
     * Check if filename matches early dev version pattern and is newer
     * @return true if this is a newer early dev version
     */
    private boolean checkEarlyDevVersion(String fileName, Integer currentBuildDate) {
        Matcher matcher = ShaderDetector.earlyDevPattern.matcher(fileName);
        if (matcher.matches() && currentBuildDate != null) {
            String devDateStr = matcher.group(1);
            debugLog("Found earlyDev file: " + fileName + " with date: " + devDateStr);

            try {
                int devDate = Integer.parseInt(devDateStr.replace("-", ""));
                debugLog("Comparing dates: earlyDev=" + devDate + " vs current=" + currentBuildDate);

                if (devDate > currentBuildDate) {
                    debugLog("EarlyDev date " + devDateStr + " is newer than current build date");
                    return true;
                } else {
                    debugLog("EarlyDev date " + devDateStr + " is older or equal to current build date");
                }
            } catch (NumberFormatException e) {
                debugLog("Could not parse earlyDev date: " + devDateStr);
            }
        }
        return false;
    }

    // Helper methods
    private void resetFilesScannedCounter() {
        totalFilesToScan = 0;
        debugLog("Reset files scanned counter");
    }

    @SuppressWarnings("SpellCheckingInspection")
    private boolean isPopularShaderName(Path path) {
        try {
            String nameLower = path.getFileName().toString().toLowerCase(Locale.ROOT);

            List<String> popularPatterns = Arrays.asList(
                    ".*bsl_v\\d+\\..*",
                    ".*sildur's.*",
                    ".*spooklementary.*",
                    ".*pixelcraftshaders_.*",
                    "ep_earlyDev_\\d+.*",
                    "outdated complementary.*_r\\d+.*ep.*",
                    "comp\\d+.*ep_\\d+.*",
                    ".*photon_v\\d+.*",
                    ".*hysteria-shaders.*",
                    "rethinking-voxels_r\\d+.*",
                    "solas shader v\\d+.*",
                    "superdupervanilla.*",
                    "insanity-shader.*",
                    ".*(bliss_v\\d+|bliss-shader).*",
                    ".*\\b(continuum)\\b.*",
                    ".*(chocapic|chocapic13).*",
                    ".*astra.*lex.*",
                    "euphoriapatches_earlydev_.*"
            );

            for (String regex : popularPatterns) {
                if (nameLower.matches(regex)) {
                    debugLog("Skipping popular shader name during byte-size scan: " + path.getFileName() + " (matches " + regex + ")");
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void log(int level, String message) {
        com.euphoriapatches.euphoria_patcher.EuphoriaPatcher.log(level, message);
    }

    @SuppressWarnings("SameParameterValue")
    private void log(int level, int fadeTimer, String message) {
        com.euphoriapatches.euphoria_patcher.EuphoriaPatcher.log(level, fadeTimer, message);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderDetector] " + message);
    }

    /**
     * Performs a single uncached check to determine whether a shader path is Euphoria Patches.
     */
    private boolean performSingleEuphoriaShaderCheck(Path shaderPath) {
        try {
            if (Files.isDirectory(shaderPath)) {
                Path myFilePath = shaderPath.resolve(shaderMyFileLocation);
                boolean exists = Files.exists(myFilePath);
                debugLog("Checked directory " + shaderPath.getFileName() + " for myFile: " + exists);
                return exists;
            }

            if (Files.isRegularFile(shaderPath) && shaderPath.toString().endsWith(".zip")) {
                boolean exists = ArchiveOperations.fileExistsInZip(shaderPath, shaderMyFileLocation);
                debugLog("Checked zip " + shaderPath.getFileName() + " for myFile: " + exists);
                return exists;
            }
        } catch (Exception e) {
            debugLog("Error checking if shader is Euphoria Patches: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check if a shader path is a Euphoria Patches shader by looking for the myFile.glsl
     * Uses static cache to avoid redundant checks across instances
     * @param shaderPath Path to shader (directory or zip file)
     * @return true if this is a Euphoria Patches shader
     */
    public boolean isEuphoriaPatchesShader(Path shaderPath) {
        if (shaderPath == null) {
            debugLog("isEuphoriaPatchesShader called with null path, returning false");
            return false;
        }

        // Check cache first
        Boolean cached = euphoriaShaderCache.get(shaderPath);
        if (cached != null) {
            debugLog("isEuphoriaPatchesShader cache hit for " + shaderPath.getFileName() + ": " + cached);
            return cached;
        }

        // Not in cache: require a stable result (2 consecutive identical checks) before caching.
        int consecutiveMatches = 0;
        Boolean previousResult = null;
        Boolean lastObservedResult = null;

        for (int i = 0; i < INITIAL_EUPHORIA_CHECK_DELAYS_MS.length; i++) {
            int delayMs = INITIAL_EUPHORIA_CHECK_DELAYS_MS[i];
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    debugLog("Interrupted during initial Euphoria shader verification checks");
                    break;
                }
            }

            boolean currentResult = performSingleEuphoriaShaderCheck(shaderPath);
            lastObservedResult = currentResult;

            if (previousResult != null && previousResult == currentResult) {
                consecutiveMatches++;
            } else {
                consecutiveMatches = 1;
            }

            previousResult = currentResult;
            debugLog("Initial Euphoria shader check #" + (i + 1) + " result: " + currentResult + " (streak=" + consecutiveMatches + ")");

            if (consecutiveMatches >= REQUIRED_CONSECUTIVE_EUPHORIA_CHECKS) {
                euphoriaShaderCache.put(shaderPath, currentResult);
                debugLog("Committed Euphoria shader cache for " + shaderPath.getFileName() + ": " + currentResult);
                return currentResult;
            }
        }

        if (lastObservedResult == null) {
            debugLog("No valid observation obtained for " + shaderPath.getFileName() + ", returning false");
            return false;
        }
        // Unstable result: return latest observation, but don't cache it.
        boolean fallbackResult = lastObservedResult;
        debugLog("Unstable initial Euphoria shader checks for " + shaderPath.getFileName() + ", returning uncached result: " + fallbackResult);
        return fallbackResult;
    }

    /**
     * If the shader is a Euphoria Patches shader, read the version from pack.json
     * @param shaderPath Path to shader (directory or zip file)
     * @return Version string from pack.json, or null if not a Euphoria Patches shader or version not found
     */
    public String getEuphoriaPatchesVersionFromShader(Path shaderPath) {
        if (isEuphoriaPatchesShader(shaderPath)) {
            return readVersionFromPackJson(shaderPath);
        }
        return null;
    }

    /**
     * Reads the version from pack.json in a shader directory or zip file
     * Uses static cache to avoid redundant reads across instances
     * @param shaderPath Path to shader (directory or zip file)
     * @return Version string from pack.json, or null if not found
     */
    public String readVersionFromPackJson(Path shaderPath) {
        if (shaderPath == null) {
            debugLog("readVersionFromPackJson called with null path, returning null");
            return null;
        }

        // Check cache first
        Optional<String> cached = versionCache.get(shaderPath);
        if (cached != null) {
            debugLog("Version cache hit for " + shaderPath.getFileName() + ": " + cached.orElse("null"));
            return cached.orElse(null);
        }

        // Not in cache, perform the read
        String version = null;
        Path packJsonPath;

        try {
            if (Files.isDirectory(shaderPath)) {
                // For directories, read directly from pack.json
                packJsonPath = shaderPath.resolve("shaders/pack.json");
                if (Files.exists(packJsonPath)) {
                    version = readVersionFromFile(packJsonPath);
                    debugLog("Read version from directory " + shaderPath.getFileName() + ": " + version);
                } else {
                    debugLog("pack.json not found at " + packJsonPath);
                }
            } else if (Files.isRegularFile(shaderPath) && shaderPath.toString().endsWith(".zip")) {
                // For zip files, read from archive
                String packJsonContent = ArchiveOperations.readFileFromZip(shaderPath, "shaders/pack.json");
                if (packJsonContent != null) {
                    version = parseVersionFromJson(packJsonContent);
                    debugLog("Read version from zip " + shaderPath.getFileName() + ": " + version);
                } else {
                    debugLog("pack.json not found in zip " + shaderPath.getFileName());
                }
            }
        } catch (Exception e) {
            debugLog("Error reading version from pack.json: " + e.getMessage());
        }

        // Cache the result (including null)
        versionCache.put(shaderPath, Optional.ofNullable(version));
        return version;
    }

    /**
     * Reads version string from a pack.json file
     */
    private String readVersionFromFile(Path packJsonPath) {
        String jsonContent = FileOperations.readFileAsString(packJsonPath);
        return parseVersionFromJson(jsonContent);
    }

    /**
     * Parses version string from pack.json content
     */
    private String parseVersionFromJson(String jsonContent) {
        Pattern versionPattern = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = versionPattern.matcher(jsonContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Check if one style is installed but the other is missing, and if the missing style's base shader exists
     * If found, updates info to indicate we should patch the missing style
     */
    private boolean checkForMissingStyle(ShaderInfo info) {
        // If both styles are already installed, nothing to do
        if (info.styleReimagined && info.styleUnbound) {
            debugLog("Both styles already installed");
            return false;
        }

        // Load current installation state from data.json
        if (!UserPersistentData.dataFileExists()) {
            debugLog("No data file exists, cannot check for missing styles");
            return false;
        }

        UserPersistentData.PersistentShaderData data = UserPersistentData.load();

        if (data.styleReimagined == null || data.styleUnbound == null) {
            debugLog("Could not load data.json, cannot check for missing styles");
            return false;
        }

        // If both are already marked as installed in data, skip
        if (data.styleReimagined && data.styleUnbound) {
            debugLog("Both styles marked as installed in data.json");
            info.styleReimagined = true;
            info.styleUnbound = true;
            return true;
        }

        // Determine which style is missing
        String missingStyle;
        if (data.styleReimagined) {
            missingStyle = "Unbound";
            debugLog("Reimagined is installed, checking for Unbound");
        } else if (data.styleUnbound) {
            missingStyle = "Reimagined";
            debugLog("Unbound is installed, checking for Reimagined");
        } else {
            // Neither is installed according to data, proceed with normal detection
            // Literally HOW. There is no way we can get here unless the world is broken.
            debugLog("No styles marked as installed in data.json");
            return false;
        }

        // Look for the missing style's base shader using exact filename matching
        Path missingStyleShader = findBaseShaderByStyle(missingStyle);
        if (missingStyleShader != null) {
            debugLog("Found missing style shader: " + missingStyleShader.getFileName());
            // Set up info to patch the missing style
            info.baseFile = missingStyleShader;
            info.isAlreadyInstalled = false; // Reset so patching continues

            // Set the style flags based on what we found
            if ("Reimagined".equals(missingStyle)) {
                info.styleReimagined = true;
                info.styleUnbound = data.styleUnbound; // Keep existing state
            } else {
                info.styleUnbound = true;
                info.styleReimagined = data.styleReimagined; // Keep existing state
            }
        } else {
            debugLog("Missing style " + missingStyle + " shader not found");
        }
        return true;
    }

    /**
     * Find a base shader by style using exact filename matching
     * @param style "Reimagined" or "Unbound"
     * @return Path to the shader file/directory, or null if not found
     */
    private Path findBaseShaderByStyle(String style) {
        String expectedName = brandName + style + version;
        debugLog("Looking for base shader: " + expectedName);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();

                // Check for exact match with .zip
                if (fileName.equals(expectedName + ".zip") && Files.isRegularFile(path)) {
                    debugLog("Found exact match (zip): " + fileName);
                    return path;
                }

                // Check for exact match as directory
                if (fileName.equals(expectedName) && Files.isDirectory(path)) {
                    debugLog("Found exact match (directory): " + fileName);
                    return path;
                }
            }
        } catch (IOException e) {
            debugLog("Error searching for base shader by style: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gets the path for a patched shader based on the base shader file
     *
     * @param baseFile Path to the base shader file or directory
     * @return Path to the patched shader, or null if baseFile is null
     */
    public Path getPatchedShaderPath(Path baseFile) {
        if (baseFile == null) {
            log(3, "Cannot create patched shader path - base file is null");
            return null;
        }

        try {
            // The library names the patched shader, so the name can never differ from what patching creates
            return baseFile.resolveSibling(PatchLibrary.get().getOutputName(baseFile));
        } catch (Exception e) {
            log(3, "Error creating patched shader path: " + e.getMessage());
            return null;
        }
    }

    public Path findInstalledShaderPath(Path baseFile, Path installedDir) {
        if (installedDir != null) {
            debugLog("Using already detected installed directory: " + installedDir);
            return installedDir;
        }

        if (baseFile != null) {
            return getPatchedShaderPath(baseFile);
        }

        return findPatchedShaderDirectory();
    }

    /**
     * Find the patched shader directory directly
     * Used when baseFile is null, but we need to find the installed shader
     */
    public Path findPatchedShaderDirectory() {
        try {
            DirectoryStream.Filter<Path> filter = path ->
                (Files.isDirectory(path) ||
                (Files.isRegularFile(path) && path.toString().endsWith(".zip"))) &&
                path.getFileName().toString().contains(brandName) &&
                path.getFileName().toString().contains(" + " + patchName + patchVersion);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacks, filter)) {
                for (Path path : stream) {
                    debugLog("Found patched shader directory: " + path.getFileName());
                    return path;
                }
            }
        } catch (IOException e) {
            debugLog("Error finding patched shader directory: " + e.getMessage());
        }
        return null;
    }

    /**
     * Helper class to store shader information
     */
    public static class ShaderInfo {
        public Path baseFile = null;
        public Path installedDir = null;
        public boolean styleReimagined = false;
        public boolean styleUnbound = false;
        public boolean isAlreadyInstalled = false;
    }
}
