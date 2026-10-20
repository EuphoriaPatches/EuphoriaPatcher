package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.PatchInfo;
import com.euphoriapatches.euphoria_patcher.services.ShaderDetector.ShaderInfo;
import com.euphoriapatches.euphoria_patcher.io.ArchiveOperations;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.euphoria_patcher.util.UserInstallErrorMessages;
import com.euphoriapatches.euphoria_patcher.util.shader.ShaderVersionComparator;
import com.euphoriapatches.shaderpatcher.PatchResult;
import com.euphoriapatches.shaderpatcher.ShaderPatcher;
import com.euphoriapatches.shaderpatcher.exception.BaseHashMismatchException;
import com.euphoriapatches.shaderpatcher.exception.BaseSizeMismatchException;
import com.euphoriapatches.shaderpatcher.exception.InvalidPackException;
import com.euphoriapatches.shaderpatcher.exception.OutputExistsException;
import com.euphoriapatches.shaderpatcher.exception.PackIoException;
import com.euphoriapatches.shaderpatcher.exception.PatchNotFoundException;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Handles shader patching operations.
 * The verification and patching itself is done by the ShaderPatcher library, which reports every problem as an
 * exception. This class is the single place where those exceptions are turned into messages for the user.
 */
public class ShaderPatchingService {
    private final String patchName;
    private final String commonLocation;
    private final Path shaderpacks;

    public ShaderPatchingService(String patchName, String commonLocation, Path shaderpacks) {
        this.patchName = patchName;
        this.commonLocation = commonLocation;
        this.shaderpacks = shaderpacks;
    }

    /**
     * Process and patch shaders
     */
    public boolean processAndPatchShaders(ShaderInfo info, Path temp) {
        try {
            if (info.baseFile == null) {
                return false;
            }

            String originalFileName = info.baseFile.getFileName().toString();
            // Name under which the error handlers know the archive of the base shader
            String archiveName = originalFileName.replace(".zip", "") + ".tar";

            // Work on a private copy: the shader the user has, is never modified. The copy keeps the name of the base
            // shader because the library derives the name of the patched shader from it.
            Path baseCopy = temp.resolve(originalFileName);
            try {
                ShaderPatcher.extract(info.baseFile, baseCopy);
            } catch (ShaderPatcherException e) {
                return handleFailure(e, archiveName, originalFileName);
            }
            normalizeShaderStyleInCommon(baseCopy);

            ShaderPatcher patcher = PatchLibrary.get();

            // A base shader that is named like a dev/test version is never patched, even if it matches the release
            if (ShaderVersionComparator.isTestOrDevVersion(archiveName)) {
                debugLog("Test/fix version detected: " + originalFileName);
                try {
                    patcher.verifyBase(baseCopy);
                    UserInstallErrorMessages.handleDevVersion(archiveName, originalFileName);
                } catch (BaseHashMismatchException e) {
                    UserInstallErrorMessages.handleDevVersion(archiveName, originalFileName);
                } catch (ShaderPatcherException e) {
                    return handleFailure(e, archiveName, originalFileName);
                }
                return false;
            }

            PatchResult result = patcher.apply(baseCopy, shaderpacks);
            Path patchedFile = result.getOutputDir();
            debugLog("Patched shader written to: " + patchedFile);

            debugLog("Applying style settings...");
            applyStyleSettings(patchedFile, info.styleUnbound, info.styleReimagined);
            log(1, patchName + " was successfully installed. Enjoy! -SpacEagle17");
            return true;
        } catch (ShaderPatcherException e) {
            return handleFailure(e, null, info.baseFile != null ? info.baseFile.getFileName().toString() : null);
        } catch (IOException e) {
            debugLog("Error applying patch file: " + e.getMessage());
            log(3, "Error applying patch file: " + e.getMessage());
            log(3, EuphoriaLogger.getStackTrace(e));
            return false;
        } finally {
            ArchiveOperations.deleteTempDirectory(temp);
        }
    }

    /**
     * Turns a failure reported by the library into the matching message for the user. Always returns false.
     */
    private boolean handleFailure(ShaderPatcherException e, String archiveName, String originalFileName) {
        debugLog("Patching failed: " + e);
        if (archiveName == null && originalFileName != null) {
            archiveName = originalFileName.replace(".zip", "") + ".tar";
        }

        if (e instanceof BaseSizeMismatchException || e instanceof InvalidPackException) {
            // An unreadable archive is handled like a wrong one: the user needs to supply the correct shader
            UserInstallErrorMessages.handleSizeMismatch(archiveName, originalFileName, getVersionComparator());
        } else if (e instanceof BaseHashMismatchException) {
            if (ShaderVersionComparator.isTestOrDevVersion(archiveName)) {
                UserInstallErrorMessages.handleDevVersion(archiveName, originalFileName);
            } else {
                UserInstallErrorMessages.handleHashMismatch(archiveName, originalFileName);
            }
        } else if (e instanceof PatchNotFoundException) {
            // Patch resource not found - this is a critical error
            log(3, 12, "========================");
            log(3, 12, "CRITICAL BUILD ERROR");
            log(3, 12, "========================");
            log(3, 12, "Patch file not found in mod resources!");
            log(3, 12, "Expected: " + PatchInfo.PATCH_RESOURCE);
            log(3, 12, "");
            log(3, 12, "This indicates the mod was built incorrectly.");
            log(3, 12, "The patch file should be in the JAR resources.");
            log(3, 12, "");
            log(3, 12, "Please report this issue to the mod developer");
            log(3, 12, "at: https://github.com/EuphoriaPatches/EuphoriaPatcher/issues");
            log(3, 12, "========================");
        } else if (e instanceof OutputExistsException) {
            log(2, "The patched shader already exists: " + e.getPath());
        } else if (e instanceof PackIoException) {
            log(3, "Error while patching: " + e.getMessage());
            log(3, EuphoriaLogger.getStackTrace(e));
        } else {
            // Damaged patch file and anything else
            log(3, "Error applying patch file: " + e.getMessage());
            log(3, EuphoriaLogger.getStackTrace(e));
        }
        return false;
    }

    private static ShaderVersionComparator getVersionComparator() {
        EuphoriaPatcher instance = EuphoriaPatcher.getInstance();
        return instance != null ? instance.getVersionComparator() : null;
    }

    /**
     * Update common file to normalize shader style
     */
    public void normalizeShaderStyleInCommon(Path baseExtracted) {
        try {
            Path commons = baseExtracted.resolve(commonLocation);
            String config = FileUtils.readFileToString(commons.toFile(), "UTF-8").replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
            FileUtils.writeStringToFile(commons.toFile(), config, "UTF-8");
        } catch (IOException e) {
            log(3, "Error normalizing shader style in common file: " + e.getMessage());
        }
    }

    /**
     * Apply style settings
     */
    public void applyStyleSettings(Path patchedFile, boolean styleUnbound, boolean styleReimagined) throws IOException {
        if (!styleUnbound && !styleReimagined) return;

        File commons = new File(patchedFile.toFile(), commonLocation);
        String commonContent = FileUtils.readFileToString(commons, "UTF-8");

        // Create both style configs
        String reimaginedConfig = commonContent.replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 1");
        String unboundConfig = commonContent.replaceFirst("SHADER_STYLE [14]", "SHADER_STYLE 4");

        if (!styleReimagined) {
            // Only Unbound style
            FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
            return;
        }

        if (!styleUnbound) {
            // Only Reimagined style
            FileUtils.writeStringToFile(commons, reimaginedConfig, "UTF-8");
            return;
        }

        // Handle both styles
        boolean isReimagined = patchedFile.getFileName().toString().contains("Reimagined");
        String otherStyle = isReimagined ? "Unbound" : "Reimagined";
        String currentStyle = isReimagined ? "Reimagined" : "Unbound";

        File otherStyleFile = new File(patchedFile.getParent().toFile(),
                patchedFile.getFileName().toString().replace(currentStyle, otherStyle));

        // Only create the other style if it doesn't already exist
        // This prevents overwriting an existing installation when patching the missing style
        if (!otherStyleFile.exists()) {
            debugLog("Creating missing " + otherStyle + " style from newly patched " + currentStyle);
            FileUtils.copyDirectory(patchedFile.toFile(), otherStyleFile);

            // Apply correct config to the newly created other style
            if (isReimagined) {
                // Current is Reimagined, created Unbound
                FileUtils.writeStringToFile(new File(otherStyleFile, commonLocation), unboundConfig, "UTF-8");
            } else {
                // Current is Unbound, created Reimagined
                FileUtils.writeStringToFile(new File(otherStyleFile, commonLocation), reimaginedConfig, "UTF-8");
            }
        } else {
            debugLog("Other style (" + otherStyle + ") already exists, not overwriting");
        }

        // Apply correct config to the current style
        if (isReimagined) {
            FileUtils.writeStringToFile(commons, reimaginedConfig, "UTF-8");
        } else {
            FileUtils.writeStringToFile(commons, unboundConfig, "UTF-8");
        }
    }

    private void log(int level, String message) {
        EuphoriaPatcher.log(level, message);
    }

    @SuppressWarnings("SameParameterValue")
    private void log(int level, int fadeTimer, String message) {
        EuphoriaPatcher.log(level, fadeTimer, message);
    }

    private void debugLog(String message) {
        EuphoriaLogger.debugLog("[ShaderPatchingService] " + message);
    }
}
