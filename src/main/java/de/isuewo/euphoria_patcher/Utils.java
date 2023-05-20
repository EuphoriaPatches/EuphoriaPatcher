package de.isuewo.euphoria_patcher;

import org.apache.commons.compress.archivers.ArchiveEntry;
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
import java.util.UUID;

public class Utils {

    public static File createTempDir() {
        return new File(FileUtils.getTempDirectoryPath() + File.separator + UUID.randomUUID());
    }

    public static void extract(File zipFile, File targetDir) {
        try (ArchiveInputStream i = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(Files.newInputStream(zipFile.toPath())))) {
            ArchiveEntry entry;
            while ((entry = i.getNextEntry()) != null) {
                if (!i.canReadEntryData(entry)) {
                    continue;
                }
                File f = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    if (!f.isDirectory() && !f.mkdirs()) {
                        System.err.println("Failed to create directory " + f);
                    }
                } else {
                    File parent = f.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        System.err.println("Failed to create directory " + parent);
                    }
                    try (OutputStream o = Files.newOutputStream(f.toPath())) {
                        IOUtils.copy(i, o);
                    } catch (IOException e) {
                        System.err.println("Failed to extract entry " + entry + " to " + f + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to extract archive " + zipFile + ": " + e.getMessage());
        }
    }

    public static void archive(File sourceDir, File archive) {
        List<File> filesToArchive = new ArrayList<>(FileUtils.listFiles(sourceDir, null, true));

        Collections.sort(filesToArchive); // ensures that the archive is deterministic

        if (!archive.getParentFile().exists() && !archive.getParentFile().mkdirs()) {
            System.err.println("Failed to create archive: " + archive);
            return;
        }

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
                    } catch (IOException e) {
                        System.err.println("Failed to archive entry " + entry + " from " + f + ": " + e.getMessage());
                    }
                }
                o.closeArchiveEntry();
            }
            o.finish();
        } catch (Exception e) {
            System.err.println("Failed to create archive: " + e.getMessage());
        }
    }
}
