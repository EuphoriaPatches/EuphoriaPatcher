package mc.euphoriaPatcher;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
     * @throws IOException if there's an issue with file operations
     * @throws ArchiveException if there's an issue with archive processing
     */

    public static void extract(Path in, Path out) throws IOException, ArchiveException {
        // Create the output directory if it doesn't exist
        Files.createDirectories(out);

        // Construct the path for the .txt file (assuming it has the same name as the output directory)
        Path txtFilePath = out.resolve(out.getFileName() + ".txt");
        String txtContent = null;

        // Check if the .txt file exists and backup its content
        if (Files.exists(txtFilePath)) {
            txtContent = new String(Files.readAllBytes(txtFilePath), StandardCharsets.UTF_8);
        }

        // Start the extraction process
        try (ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(
                new BufferedInputStream(Files.newInputStream(in)))) {

            ArchiveEntry entry;
            // Iterate through each entry in the archive
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                // Skip entries that can't be read
                if (!archiveInputStream.canReadEntryData(entry)) {
                    continue;
                }

                // Resolve the target path for the current entry
                Path targetFilePath = out.resolve(entry.getName()).normalize();

                // Security check to prevent path traversal attacks
                if (targetFilePath.toString().contains("..")) {
                    throw new IOException("Potentially malicious entry detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    // Create directory if the entry is a directory
                    Files.createDirectories(targetFilePath);
                } else {
                    // Create parent directories for the file
                    Files.createDirectories(targetFilePath.getParent());

                    // Extract the file
                    try (OutputStream outputStream = Files.newOutputStream(targetFilePath)) {
                        IOUtils.copy(archiveInputStream, outputStream);
                    }
                }
            }
        }

        // After extraction, restore the .txt file if it existed before
        if (txtContent != null) {
            Files.write(txtFilePath, txtContent.getBytes(StandardCharsets.UTF_8));
            EuphoriaPatcher.log(0, "Restored .txt file: " + txtFilePath);
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
        // Use try-with-resources to automatically close the output stream
        try (TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(Files.newOutputStream(archive))) {
            // Walk through the file tree of the source directory
            try (Stream<Path> fileStream = Files.walk(sourceDir)) {
                // Sort files to ensure a platform-independent order
                fileStream.sorted(Comparator.comparing(Path::toUri)).forEach(filePath -> addFileToArchive(tarOutputStream, sourceDir, filePath));
            }
            tarOutputStream.finish();
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
            TarArchiveEntry tarEntry = new TarArchiveEntry(filePath.toFile(), fileName); // Create a TAR entry for the file or directory

            // Set deterministic flags for the archive entry
            tarEntry.setModTime(0);
            tarEntry.setIds(0, 0);
            tarEntry.setNames("", "");

            tarOutputStream.putArchiveEntry(tarEntry);

            if (Files.isRegularFile(filePath)) { // If the entry is a regular file, write its contents to the archive
                try (InputStream inputStream = Files.newInputStream(filePath)) {
                    IOUtils.copy(inputStream, tarOutputStream);
                }
            }

            tarOutputStream.closeArchiveEntry(); // Close the current entry in the archive
        } catch (IOException e) {
            EuphoriaPatcher.log(3, "Could not add files to TAR Archive: " + e.getMessage());
        }
    }
}
