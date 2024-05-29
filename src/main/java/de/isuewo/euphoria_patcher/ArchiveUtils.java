package de.isuewo.euphoria_patcher;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class ArchiveUtils {
    public static void extract(Path in, Path out) {
        try (ArchiveInputStream ai = new ArchiveStreamFactory().createArchiveInputStream(Files.newInputStream(in))) {
            ArchiveEntry entry;
            while ((entry = ai.getNextEntry()) != null) {
                if (!ai.canReadEntryData(entry)) {
                    continue;
                }
                if (!entry.isDirectory()) {
                    Path targetFilePath = out.resolve(entry.getName());
                    Files.createDirectories(targetFilePath.getParent());
                    try (OutputStream o = Files.newOutputStream(targetFilePath)) {
                        IOUtils.copy(ai, o);
                    }
                }
            }
        } catch (IOException | ArchiveException e) {
            e.printStackTrace();
        }
    }

    public static void archive(Path sourceDir, Path archive) {
        try (TarArchiveOutputStream o = new TarArchiveOutputStream(Files.newOutputStream(archive))) {
            try (Stream<Path> walk = Files.walk(sourceDir)) {
                walk.filter(Files::isRegularFile).sorted().forEach(f -> {
                    TarArchiveEntry entry = new TarArchiveEntry(f.toFile(), sourceDir.relativize(f).toString());
                    entry.setModTime(0);
                    entry.setIds(0, 0);
                    entry.setNames("", "");
                    try (InputStream i = Files.newInputStream(f)) {
                        o.putArchiveEntry(entry);
                        IOUtils.copy(i, o);
                        o.closeArchiveEntry();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                o.finish();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}