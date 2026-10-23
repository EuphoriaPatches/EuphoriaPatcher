package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.PatchInfo;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.shaderpatcher.PatchSource;
import com.euphoriapatches.shaderpatcher.ShaderPatcher;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;

/**
 * Holder for the ShaderPatcher library instance that applies the patch bundled in this mod.
 */
public final class PatchLibrary {
    private static ShaderPatcher instance;
    private static ShaderPatcherException failure;

    private PatchLibrary() {
    }

    /**
     * @return the patcher for the bundled patch, created on first use
     * @throws ShaderPatcherException if the patch resource is missing or unusable, which means the mod was built wrongly
     */
    public static synchronized ShaderPatcher get() throws ShaderPatcherException {
        if (failure != null) throw failure;
        if (instance == null) {
            try {
                instance = ShaderPatcher.builder(PatchSource.classpath(EuphoriaPatcher.class, PatchInfo.PATCH_RESOURCE))
                        .expectPatchName(PatchInfo.SPEC.getPatchName())
                        .expectPatchVersion(PatchInfo.SPEC.getPatchVersion())
                        .debugLog(message -> EuphoriaLogger.debugLog("[ShaderPatcher] " + message))
                        .build();
            } catch (ShaderPatcherException e) {
                failure = e;
                throw e;
            }
        }
        return instance;
    }

    /**
     * @return why the bundled patch can not be used, or null if it can. The result never changes while the game runs
     */
    public static ShaderPatcherException getFailure() {
        try {
            get();
            return null;
        } catch (ShaderPatcherException e) {
            return e;
        }
    }
}
