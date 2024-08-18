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

    /**
     * Extracts the contents of an archive to the specified output directory.
     *
     * @param in  Path to the input archive file.
     * @param out Path to the output directory where files should be extracted.
     */
    public static void extract(Path in, Path out) {
        // Use try-with-resources to automatically close resources
        try (ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(
                new BufferedInputStream(Files.newInputStream(in)))) {

            ArchiveEntry entry;
            // Process each entry in the archive
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                if (!archiveInputStream.canReadEntryData(entry)) {
                    continue;  // Skip entries that cannot be read
                }

                // Resolve the target path for this entry
                Path targetFilePath = out.resolve(entry.getName());

                if (entry.isDirectory()) {
                    // Create directories as needed
                    Files.createDirectories(targetFilePath);
                } else {
                    // Ensure parent directories exist before writing file
                    Files.createDirectories(targetFilePath.getParent());
                    try (OutputStream outputStream = Files.newOutputStream(targetFilePath)) {
                        // Copy the file content from the archive to the target file
                        IOUtils.copy(archiveInputStream, outputStream);
                    }
                }
            }
        } catch (IOException | ArchiveException e) {
            // Handle exceptions by printing the stack trace (consider more sophisticated error handling)
            e.printStackTrace();
        }
    }

    /**
     * Creates a TAR archive from the contents of a specified directory.
     *
     * @param sourceDir Path to the directory to be archived.
     * @param archive   Path to the output TAR archive file.
     */
    public static void archive(Path sourceDir, Path archive) {
        // Use try-with-resources to automatically close the output stream
        try (TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(Files.newOutputStream(archive))) {

            // Walk through the file tree of the source directory
            try (Stream<Path> fileStream = Files.walk(sourceDir)) {
                // Sort files to ensure a platform-independent order
                fileStream.sorted(Comparator.comparing(Path::toUri))
                        .forEach(filePath -> addFileToArchive(tarOutputStream, sourceDir, filePath));
            }

            // Finalize the archive by writing any necessary end-of-file information
            tarOutputStream.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a file to the TAR archive, ensuring that directory structure is preserved and files are correctly added.
     *
     * @param tarOutputStream The TAR output stream.
     * @param sourceDir       The base directory from which files are being archived.
     * @param filePath        The path of the file to add to the archive.
     */
    private static void addFileToArchive(TarArchiveOutputStream tarOutputStream, Path sourceDir, Path filePath) {
        try {
            // Compute the relative file name and normalize it for the TAR format
            String fileName = sourceDir.relativize(filePath).toString().replace(File.separatorChar, '/'); // fixes weird issues with Lunar client

            // Create a TAR entry for the file or directory
            TarArchiveEntry tarEntry = new TarArchiveEntry(filePath.toFile(), fileName);

            // Set deterministic flags for the archive entry
            tarEntry.setModTime(0);   // Set modification time to zero for deterministic archives
            tarEntry.setIds(0, 0);    // Set user and group IDs to zero
            tarEntry.setNames("", ""); // Clear user and group names

            // Add the entry to the archive
            tarOutputStream.putArchiveEntry(tarEntry);

            // If the entry is a regular file, write its contents to the archive
            if (Files.isRegularFile(filePath)) {
                try (InputStream inputStream = Files.newInputStream(filePath)) {
                    IOUtils.copy(inputStream, tarOutputStream);
                }
            }

            // Close the current entry in the archive
            tarOutputStream.closeArchiveEntry();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
