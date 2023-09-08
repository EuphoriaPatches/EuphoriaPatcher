package de.isuewo.euphoria_patcher;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {

    public static File createTempDir() {
        try {
            return Files.createTempDirectory("euphoria-patcher-").toFile();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void extract(File input, File targetDir) {
        try {
            ArchiveInputStream i = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(Files.newInputStream(input.toPath())));
            ArchiveEntry entry;
            while ((entry = i.getNextEntry()) != null) {
                if (!i.canReadEntryData(entry)) {
                    continue;
                }
                File f = targetDir.toPath().resolve(entry.getName()).toFile();
                if (entry.isDirectory()) {
                    if (!f.isDirectory() && !f.mkdirs()) {
                        System.err.println("failed to create directory " + f);
                    }
                } else {
                    File parent = f.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        System.err.println("failed to create directory " + parent);
                    }
                    try (OutputStream o = Files.newOutputStream(f.toPath())) {
                        IOUtils.copy(i, o);
                    }
                }
            }
        } catch (IOException | ArchiveException e) {
            throw new RuntimeException(e);
        }
    }

    public static void archive(File sourceDir, File archive) {
        if (!archive.getParentFile().exists() && !archive.getParentFile().mkdirs()) {
            System.err.println("Failed to create archive: " + archive);
            return;
        }

        List<File> filesToArchive = new ArrayList<>(FileUtils.listFiles(sourceDir, null, true));
        Collections.sort(filesToArchive); // ensures that the archive is deterministic

        try (TarArchiveOutputStream o = new TarArchiveOutputStream(new BufferedOutputStream(Files.newOutputStream(archive.toPath())))) {
            for (File f : filesToArchive) {
                TarArchiveEntry entry = new TarArchiveEntry(f, sourceDir.toPath().relativize(f.toPath()).toString());

                // also ensures that the archive is deterministic
                entry.setModTime(0);
                entry.setIds(0, 0);
                entry.setNames("", "");

                o.putArchiveEntry(entry);
                if (f.isFile()) {
                    try (InputStream i = Files.newInputStream(f.toPath())) {
                        IOUtils.copy(i, o);
                    }
                }
                o.closeArchiveEntry();
            }
            o.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
