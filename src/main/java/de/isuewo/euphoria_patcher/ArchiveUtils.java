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
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

public class ArchiveUtils {
    public static void extract(Path in, Path out) throws IOException, ArchiveException {
        if (!Files.exists(out)) {
            Files.createDirectory(out);
        }

        try (ArchiveInputStream ai = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(Files.newInputStream(in, StandardOpenOption.READ)))) {
            ArchiveEntry entry;
            while ((entry = ai.getNextEntry()) != null) {
                if (!ai.canReadEntryData(entry)) {
                    continue;
                }
                Path targetFilePath = out.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectory(targetFilePath);
                } else {
                    Path parent = targetFilePath.getParent();
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    try (OutputStream o = Files.newOutputStream(targetFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        IOUtils.copy(ai, o);
                    }
                }
            }
        }
    }

    public static void archive(Path sourceDir, Path archive) throws IOException {
        List<Path> filesToArchive = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .sorted() //ensures that the archive is deterministic
                .collect(Collectors.toList());

        try (TarArchiveOutputStream o = new TarArchiveOutputStream(new BufferedOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            for (Path f : filesToArchive) {
                TarArchiveEntry entry = new TarArchiveEntry(f.toFile(), sourceDir.relativize(f).toString());

                // also ensures that the archive is deterministic
                entry.setModTime(0);
                entry.setIds(0, 0);
                entry.setNames("", "");

                o.putArchiveEntry(entry);
                if (Files.isRegularFile(f)) {
                    try (InputStream i = Files.newInputStream(f, StandardOpenOption.READ)) {
                        IOUtils.copy(i, o);
                    }
                }
                o.closeArchiveEntry();
            }
            o.finish();
        }
    }
}