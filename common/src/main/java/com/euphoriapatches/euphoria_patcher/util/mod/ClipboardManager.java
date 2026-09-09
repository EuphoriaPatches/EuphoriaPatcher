package com.euphoriapatches.euphoria_patcher.util.mod;

import com.euphoriapatches.euphoria_patcher.EuphoriaPatcher;
import com.euphoriapatches.euphoria_patcher.logging.EuphoriaLogger;

public final class ClipboardManager {

    private ClipboardManager() {}

    private static final long TIMEOUT_MS = 7000L;

    private static volatile String pendingUrl = null;
    private static volatile long firstAttempt = 0L;

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ClipboardManager] " + message);
    }

    public static void schedule(String url) {
        if (url == null) {
            return;
        }
        pendingUrl = url;
        firstAttempt = 0L;
        debugLog("Queued clipboard copy: " + url);
    }

    public static void checkPending() {
        debugLog("Checking for pending clipboard copy");
        String url = pendingUrl;
        if (url == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (firstAttempt == 0L) {
            firstAttempt = now;
        }

        if (ModLoaderSpecifics.setClipboardStatic(url)) {
            pendingUrl = null;
            debugLog("Copied error URL to clipboard: " + url);
            EuphoriaPatcher.log(3, 8, "The download link has been copied to your clipboard. Paste it in your browser.");
        } else if (now - firstAttempt >= TIMEOUT_MS) {
            pendingUrl = null;
            debugLog("Gave up copying error URL to clipboard after " + TIMEOUT_MS + "ms");
            EuphoriaPatcher.log(3, 8, "Failed to copy error URL to clipboard. " +
                    "Please copy it manually from " + EuphoriaLogger.ERROR_LOG_FILE_NAME + " in your shaderpacks folder.");
        }
    }
}
