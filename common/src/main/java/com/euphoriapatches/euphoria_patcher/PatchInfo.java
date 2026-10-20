package com.euphoriapatches.euphoria_patcher;

import com.euphoriapatches.shaderpatcher.PatchSpec;

/**
 * The strings are compile-time constants (some loaders need that for their mod annotations),
 * {@link #SPEC} is built from them.
 */
public class PatchInfo {
    public static final String BRAND_NAME = "Complementary";
    public static final String PATCH_NAME = "EuphoriaPatches";
    public static final String VERSION = "_r5.9.3";
    public static final String PATCH_VERSION = "_1.10.5";

    public static final PatchSpec SPEC = PatchSpec.builder()
            .patchName(PATCH_NAME).patchVersion(PATCH_VERSION)
            .baseName(BRAND_NAME).baseVersion(VERSION)
            .build();

    /** Name of the patch file in the mod's resources: EuphoriaPatches_1.10.5.patch */
    public static final String PATCH_RESOURCE = SPEC.getPatchFileName();
}
