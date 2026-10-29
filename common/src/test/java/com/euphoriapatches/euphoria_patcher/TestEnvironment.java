package com.euphoriapatches.euphoria_patcher;

import com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sets up the little bit of game-side state that the code under test touches when it logs or stores its data, so the
 * tests can run without Minecraft. Call {@link #init()} from a test that ends up using {@code EuphoriaPatcher}.
 * <p>
 * The config folder is a private temporary folder that is removed when the test JVM exits, so the tests never write
 * into a real config folder.
 * Tests have been mostly written with the help of LLMs but they have been reviewed manually.
 */
public final class TestEnvironment {
    private static Path configDirectory;
    private static volatile Path redirectedShaderpacks;

    private TestEnvironment() {
    }

    /** Makes the live shaderpacks folder another one than the default, like Dawn does. Undo it with {@link #clearShaderpacksRedirect()}. */
    public static void redirectShaderpacksTo(Path folder) {
        redirectedShaderpacks = folder;
    }

    public static void clearShaderpacksRedirect() {
        redirectedShaderpacks = null;
    }

    public static synchronized void init() {
        if (configDirectory == null) {
            try {
                configDirectory = Files.createTempDirectory("euphoria-patcher-test-config-");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            final Path toDelete = configDirectory;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(toDelete.toFile())));
        }
        ModLoaderSpecifics.setInstance(new StubModLoaderSpecifics(configDirectory));
        // The real loaders do this when the game starts, it also remembers the default shaderpacks folder
        ModLoaderSpecifics.shaderpacks();
    }

    /** The folder that the code under test sees as the config folder of the game. */
    public static synchronized Path configDirectory() {
        init();
        return configDirectory;
    }

    private static final class StubModLoaderSpecifics extends ModLoaderSpecifics {
        private final Path configDir;

        StubModLoaderSpecifics(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public String getInstanceName() {
            return FABRIC;
        }

        @Override
        public Path getShaderpacksPath() {
            // Also remembers the default folder, which a redirect does not change
            Path folder = resolveShaderpacks(configDir.resolve("shaderpacks"));
            return redirectedShaderpacks != null ? redirectedShaderpacks : folder;
        }

        @Override
        public Path getConfigDirectory() {
            return configDir;
        }

        @Override
        public boolean serverCheck() {
            return false;
        }

        @Override
        public String getCurrentDimension() {
            return null;
        }

        @Override
        public boolean isCurrentDimensionInMappings() {
            return false;
        }

        @Override
        public boolean setClipboard(String str) {
            return false;
        }

        @Override
        public boolean isTimeAdvancing() {
            return false;
        }

        @Override
        public Object getLevel() {
            return null;
        }

        @Override
        public String getCurrentBiomeName() {
            return null;
        }

        @Override
        public boolean isCurrentBiomeModded() {
            return false;
        }
    }
}
