package mc.euphoria_patches.euphoria_patcher.util;

import mc.euphoria_patches.euphoria_patcher.EuphoriaPatcher;
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

    private static void debugLog(String message) {
        EuphoriaLogger.debugLog("[ArchiveUtils] " + message);
    }

    /**
     * Extracts the contents of an archive to the specified output directory.
     *
     * @param in  Path to the input archive file.
     * @param out Path to the output directory where files should be extracted.
     * @throws IOException if there's an issue with file operations
     * @throws ArchiveException if there's an issue with archive processing
     */
    public static void extract(Path in, Path out) throws IOException, ArchiveException {
        debugLog("Starting extraction from " + in + " to " + out);
        
        // Create the output directory if it doesn't exist
        Files.createDirectories(out);
        debugLog("Created output directory: " + out);

        // Start the extraction process
        try (ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(
                new BufferedInputStream(Files.newInputStream(in)))) {
            debugLog("Archive input stream created");
            
            ArchiveEntry entry;
            int extractedCount = 0;
            int skippedCount = 0;
            
            // Iterate through each entry in the archive
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                // Skip entries that can't be read
                if (!archiveInputStream.canReadEntryData(entry)) {
                    debugLog("Skipping unreadable entry: " + entry.getName());
                    continue;
                }
                
                try {
                    // Resolve the target path for the current entry
                    Path targetFilePath = out.resolve(entry.getName()).normalize();                    
                    debugLog("Processing entry: " + entry.getName() + " -> " + targetFilePath);

                    if (entry.isDirectory()) {
                        // Create directory if the entry is a directory
                        Files.createDirectories(targetFilePath);
                        debugLog("Created directory: " + targetFilePath);
                    } else {
                        // Create parent directories for the file
                        Files.createDirectories(targetFilePath.getParent());

                        // Extract the file
                        try (OutputStream outputStream = Files.newOutputStream(targetFilePath)) {
                            IOUtils.copy(archiveInputStream, outputStream);
                            extractedCount++;
                            debugLog("Extracted file: " + targetFilePath);
                        }
                    }
                } catch (java.nio.file.InvalidPathException e) {
                    // Just log and skip files with invalid paths
                    debugLog("Skipping entry with invalid path: " + entry.getName() + " - " + e.getMessage());
                    skippedCount++;
                }
            }
            
            String completionMessage = "Extraction completed. " + extractedCount + " files extracted";
            if (skippedCount > 0) {
                completionMessage += ", " + skippedCount + " entries skipped";
            }
            debugLog(completionMessage);
        } catch (Exception e) {
            debugLog("Error during extraction: " + e.getMessage());
            EuphoriaPatcher.log(3, "Archive extraction failed: " + e.getMessage());
            if (e.getCause() != null) {
                debugLog("Caused by: " + e.getCause().getMessage());
            }
            e.printStackTrace();
        }
    }

    /**
     * Creates a TAR archive from the contents of a specified directory.
     *
     * @param sourceDir Path to the directory to be archived.
     * @param archive   Path to the output TAR archive file.
     * @throws IOException if there's an issue with file operations
     */
    public static void archive(Path sourceDir, Path archive) throws IOException {
        debugLog("Starting archive creation from " + sourceDir + " to " + archive);
        
        // Use try-with-resources to automatically close the output stream
        try (TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(Files.newOutputStream(archive))) {
            debugLog("TAR archive output stream created");
            
            // Walk through the file tree of the source directory
            try (Stream<Path> fileStream = Files.walk(sourceDir)) {
                debugLog("Walking file tree in source directory");
                
                // Sort files to ensure a platform-independent order
                fileStream.sorted(Comparator.comparing(Path::toUri)).forEach(filePath -> addFileToArchive(tarOutputStream, sourceDir, filePath));
            }
            tarOutputStream.finish();
            debugLog("Archive creation completed: " + archive);
        } catch (Exception e) {
            debugLog("Error creating archive: " + e.getMessage());
            throw e;
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
            String fileName = sourceDir.relativize(filePath).toString().replace(File.separatorChar, '/'); // fixes weird issues with Lunar client
            debugLog("Adding to archive: " + fileName);
            
            TarArchiveEntry tarEntry = new TarArchiveEntry(filePath.toFile(), fileName); // Create a TAR entry for the file or directory

            // Set deterministic metadata
            tarEntry.setModTime(0); // Fixed timestamp
            tarEntry.setSize(Files.isRegularFile(filePath) ? Files.size(filePath) : 0); // Explicit file size
            tarEntry.setIds(0, 0); // Fixed user/group IDs
            tarEntry.setNames("", ""); // Fixed user/group names
            tarEntry.setMode(0100644); // Standard file permissions for files

            // Exceptions if error
            tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR);
            tarOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR);

            tarOutputStream.putArchiveEntry(tarEntry);

            if (Files.isRegularFile(filePath)) { // If the entry is a regular file, write its contents to the archive
                try (InputStream inputStream = Files.newInputStream(filePath)) {
                    IOUtils.copy(inputStream, tarOutputStream);
                    debugLog("Added file content: " + fileName);
                }
            }

            tarOutputStream.closeArchiveEntry(); // Close the current entry in the archive
            debugLog("Entry added to archive: " + fileName);
        } catch (IOException e) {
            debugLog("Error adding file to archive: " + filePath + " - " + e.getMessage());
            EuphoriaPatcher.log(3, "Could not add files to TAR Archive: " + e.getMessage());
        }
    }
}
