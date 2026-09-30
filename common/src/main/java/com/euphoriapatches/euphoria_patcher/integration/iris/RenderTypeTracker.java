package com.euphoriapatches.euphoria_patcher.integration.iris;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class RenderTypeTracker {
    // WeakHashMap automatically drops the record instance when GC cleans it up
    private static final Map<Object, String> PREPARED_NAMES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean hasEntries = false;

    public static void put(Object preparedRenderType, String name) {
        if (preparedRenderType != null) {
            PREPARED_NAMES.put(preparedRenderType, name);
            hasEntries = true;
        }
    }

    public static String getName(Object preparedRenderType) {
        return preparedRenderType == null || !hasEntries ? null : PREPARED_NAMES.get(preparedRenderType);
    }
}
