package com.euphoriapatches.euphoria_patcher.services;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;
import com.euphoriapatches.shaderpatcher.PatchSource;
import com.euphoriapatches.shaderpatcher.ShaderPatcher;
import com.euphoriapatches.shaderpatcher.exception.ShaderPatcherException;

/**
 * Holder for the ShaderPatcher library instance that applies the patch bundled in this mod.
 */
public final class PatchLibrary {
    /** Name of the patch resource inside the mod jar */
    public static final String PATCH_RESOURCE = EuphoriaPatcher.PATCH_NAME + EuphoriaPatcher.PATCH_VERSION + ".patch";

    private static volatile ShaderPatcher instance;

    private PatchLibrary() {
    }

    /**
     * @return the patcher for the bundled patch, created on first use
     * @throws ShaderPatcherException if the patch resource is missing or unusable, which means the mod was built wrongly
     */
    public static ShaderPatcher get() throws ShaderPatcherException {
        ShaderPatcher patcher = instance;
        if (patcher == null) {
            synchronized (PatchLibrary.class) {
                patcher = instance;
                if (patcher == null) {
                    patcher = ShaderPatcher.builder(PatchSource.classpath(EuphoriaPatcher.class, PATCH_RESOURCE))
                            .expectPatchName(EuphoriaPatcher.PATCH_NAME)
                            .expectPatchVersion(EuphoriaPatcher.PATCH_VERSION)
                            .debugLog(message -> EuphoriaLogger.debugLog("[ShaderPatcher] " + message))
                            .build();
                    instance = patcher;
                }
            }
        }
        return patcher;
    }
}
