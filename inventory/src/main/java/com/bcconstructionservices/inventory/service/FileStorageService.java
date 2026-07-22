package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.exception.InvalidFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Saves uploaded image files to local disk and returns a relative URL to
 * persist in the database. Files are organized into subfolders by type
 * (e.g. "items", "receipts") rather than sitting in one flat directory.
 *
 * <p>Storage locations come from application.yaml:
 * <ul>
 *   <li>{@code app.storage.local-path} - filesystem root where files are written</li>
 *   <li>{@code app.storage.base-url}   - URL prefix returned to callers/stored in DB</li>
 * </ul>
 *
 * <p>Note on the size check: Spring's servlet multipart layer already
 * enforces {@code spring.servlet.multipart.max-file-size} and throws
 * {@link org.springframework.web.multipart.MaxUploadSizeExceededException}
 * during request parsing - before this service is ever reached - so that is
 * the real first line of defense for multipart uploads. The explicit check
 * here is defense-in-depth: it also covers callers that invoke storeFile
 * outside the multipart request path, and produces a domain-specific
 * InvalidFileException (HTTP 400) with a clear message rather than the
 * servlet-level 500/multipart error.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    @Value("${app.storage.local-path:/tmp/inventory-uploads}")
    private String localPath;

    @Value("${app.storage.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    private final DataSize maxFileSize;

    public FileStorageService(
            @Value("${app.storage.local-path:/tmp/inventory-uploads}") String localPath,
            @Value("${app.storage.base-url:http://localhost:8080/uploads}") String baseUrl,
            // Bound via Spring's built-in DataSize converter; defaults to
            // Spring Boot's own 1MB multipart default if the property is unset.
            @Value("${spring.servlet.multipart.max-file-size:1MB}") DataSize maxFileSize) {
        this.localPath = localPath;
        this.baseUrl = baseUrl;
        this.maxFileSize = maxFileSize;
    }

    /**
     * Validates and stores an uploaded file under {local-path}/{subfolder}/,
     * using a generated UUID-based filename to avoid collisions and to avoid
     * trusting the user-supplied filename.
     *
     * @param file      the uploaded file
     * @param subfolder logical subfolder (e.g. "items", "receipts")
     * @return the relative URL to store in the database, e.g.
     *         {base-url}/{subfolder}/{generatedFilename}
     * @throws InvalidFileException if the file is empty, too large, or not an
     *                              accepted image content type
     */
    public String storeFile(MultipartFile file, String subfolder) {
        validate(file);

        String generatedFilename = generateFilename(file.getOriginalFilename());

        Path targetFolder = Paths.get(localPath, subfolder).normalize().toAbsolutePath();
        Path targetFile = targetFolder.resolve(generatedFilename);

        try {
            // Ensure the subfolder (and any missing parents) exists before writing.
            Files.createDirectories(targetFolder);

            // try-with-resources guarantees the upload stream is closed even
            // if the copy fails partway through.
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // A write failure isn't a client/validation problem, so this is a
            // genuine 500 rather than an InvalidFileException.
            throw new RuntimeException(
                    "Failed to store file '" + generatedFilename + "' in subfolder '" + subfolder + "'", e);
        }

        return buildUrl(subfolder, generatedFilename);
    }

    /**
     * Resolves the on-disk path from a URL previously returned by storeFile
     * and deletes the file. Does not throw if the file is already gone -
     * logs a warning instead, so callers (e.g. an entity delete flow) aren't
     * broken by an already-missing image.
     *
     * @param imageUrl a URL previously returned by storeFile
     */
    public void deleteFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("deleteFile called with null/blank imageUrl; nothing to delete");
            return;
        }

        Path targetFile = resolvePathFromUrl(imageUrl);
        if (targetFile == null) {
            log.warn("Could not resolve a storage path from imageUrl '{}'; skipping delete", imageUrl);
            return;
        }

        try {
            boolean deleted = Files.deleteIfExists(targetFile);
            if (!deleted) {
                log.warn("File to delete was already missing: {}", targetFile);
            }
        } catch (IOException e) {
            // Deletion failing on an existing file (e.g. permissions) is worth
            // surfacing, but per the requirement we don't want a missing file
            // to break the caller. Log rather than propagate.
            log.warn("Failed to delete file '{}': {}", targetFile, e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file is empty.");
        }

        if (file.getSize() > maxFileSize.toBytes()) {
            throw new InvalidFileException(
                    "File size " + file.getSize() + " bytes exceeds the maximum allowed size of "
                            + maxFileSize.toBytes() + " bytes.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException(
                    "Unsupported file type '" + contentType
                            + "'. Only image/jpeg, image/png, and image/webp are accepted.");
        }
    }

    private String generateFilename(String originalFilename) {
        return UUID.randomUUID() + extractExtension(originalFilename);
    }

    /**
     * Returns the file extension (including the leading dot) from the original
     * filename, lowercased, or an empty string if there isn't a usable one.
     * Guards against path separators in the extension segment so a crafted
     * filename can't smuggle in directory components.
     */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot).toLowerCase();
        if (ext.contains("/") || ext.contains("\\")) {
            return "";
        }
        return ext;
    }

    private String buildUrl(String subfolder, String filename) {
        String trimmedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return trimmedBase + "/" + subfolder + "/" + filename;
    }

    /**
     * Maps a stored URL back to its on-disk Path by stripping the configured
     * base-url prefix and resolving the remainder under local-path. Returns
     * null if the URL doesn't share the configured base-url (so we don't
     * attempt to delete something outside our storage root). The final
     * normalize + containment check prevents path-traversal
     * (e.g. "../../etc/...") from escaping the storage directory.
     */
    private Path resolvePathFromUrl(String imageUrl) {
        String trimmedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        if (!imageUrl.startsWith(trimmedBase)) {
            return null;
        }

        String relative = imageUrl.substring(trimmedBase.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isBlank()) {
            return null;
        }

        Path storageRoot = Paths.get(localPath).normalize().toAbsolutePath();
        Path resolved = storageRoot.resolve(relative).normalize().toAbsolutePath();

        // Ensure the resolved path stays within the storage root.
        if (!resolved.startsWith(storageRoot)) {
            log.warn("Resolved path '{}' escapes storage root '{}'; refusing to delete", resolved, storageRoot);
            return null;
        }

        return resolved;
    }
}