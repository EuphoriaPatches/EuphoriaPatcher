package de.isuewo.euphoria_patcher;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class ArchiveUtils {
    public static void extract(Path in, Path out) {
        try (ArchiveInputStream ai = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(Files.newInputStream(in)))) {
            ArchiveEntry entry;
            while ((entry = ai.getNextEntry()) != null) {
                if (!ai.canReadEntryData(entry)) {
                    continue;
                }
                Path targetFilePath = out.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetFilePath);
                } else {
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
                walk.sorted(Comparator.comparing(Path::toUri)) // platform-independent order
                        .forEach(f -> {
                            String fileName = sourceDir.relativize(f).toString().replace(File.separatorChar, '/'); // fixes weird issues with Lunar client
                            TarArchiveEntry entry = new TarArchiveEntry(f.toFile(), fileName);
                            // flags for deterministic archives
                            entry.setModTime(0);
                            entry.setIds(0, 0);
                            entry.setNames("", "");
                            try {
                                o.putArchiveEntry(entry);
                                if (Files.isRegularFile(f)) {
                                    try (InputStream i = Files.newInputStream(f)) {
                                        IOUtils.copy(i, o);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                o.closeArchiveEntry();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            }
            o.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}