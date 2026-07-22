package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.exception.InvalidFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FileStorageService, exercised against a real temp directory
 * (@TempDir) with no Spring context - the service is constructed directly.
 *
 * <p>A few expectations here are pinned to the service's ACTUAL behavior
 * (verified by reading the implementation), where the prompt asked to confirm
 * real behavior rather than assume:
 * <ul>
 *   <li>Content-type validation lowercases the input before checking, so
 *       "IMAGE/JPEG" IS accepted (case-insensitive) - see
 *       shouldAcceptContentTypeCaseInsensitively.</li>
 *   <li>Path-separator safety comes from the fact that storeFile NEVER uses
 *       the client filename for the target path - it always writes
 *       {localPath}/{subfolder}/{UUID+ext}. So a crafted "../../evil.jpg"
 *       name simply results in a normal UUID-named file inside the subfolder;
 *       nothing escapes. The test verifies that outcome rather than asserting
 *       on the internal extension-extraction guard.</li>
 *   <li>Empty-file and null-file both throw InvalidFileException with the same
 *       "Uploaded file is empty." message, so those tests assert on the
 *       exception type, not the message.</li>
 * </ul>
 */
class FileStorageServiceTest {

    private static final String BASE_URL = "https://cdn.example.com/uploads";

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        // 1KB max so the size-limit test can exceed it with a small payload.
        service = new FileStorageService(tempDir.toString(), BASE_URL, DataSize.ofKilobytes(1));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private MockMultipartFile imageFile(String originalName, String contentType, byte[] content) {
        return new MockMultipartFile("image", originalName, contentType, content);
    }

    private MockMultipartFile jpeg(String originalName) {
        return imageFile(originalName, "image/jpeg", "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));
    }

    /** Extracts the {subfolder}/{filename} tail from a returned URL, relative to baseUrl. */
    private Path onDiskPathFor(String returnedUrl) {
        String relative = returnedUrl.substring(BASE_URL.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return tempDir.resolve(relative);
    }

    // ===============================================================
    // storeFile
    // ===============================================================

    @Nested
    class StoreFileTests {

        @Test
        void shouldStoreJpegAndReturnUrlAndWriteFileToDisk() {
            MockMultipartFile file = jpeg("cement.jpg");

            String url = service.storeFile(file, "items");

            assertThat(url).startsWith(BASE_URL);
            assertThat(url).contains("/items/");
            assertThat(url).endsWith(".jpg");

            Path stored = onDiskPathFor(url);
            assertThat(Files.exists(stored)).isTrue();
            assertThat(stored.getParent()).isEqualTo(tempDir.resolve("items"));
        }

        @Test
        void shouldStorePngSuccessfully() {
            MockMultipartFile file = imageFile("cement.png", "image/png",
                    "fake-png-bytes".getBytes(StandardCharsets.UTF_8));

            String url = service.storeFile(file, "items");

            assertThat(url).contains("/items/").endsWith(".png");
            assertThat(Files.exists(onDiskPathFor(url))).isTrue();
        }

        @Test
        void shouldStoreWebpSuccessfully() {
            MockMultipartFile file = imageFile("cement.webp", "image/webp",
                    "fake-webp-bytes".getBytes(StandardCharsets.UTF_8));

            String url = service.storeFile(file, "receipts");

            assertThat(url).contains("/receipts/").endsWith(".webp");
            assertThat(Files.exists(onDiskPathFor(url))).isTrue();
        }

        @Test
        void shouldRejectUnsupportedContentType() {
            MockMultipartFile textFile = imageFile("notes.txt", "text/plain",
                    "hello".getBytes(StandardCharsets.UTF_8));

            assertThatExceptionOfType(InvalidFileException.class)
                    .isThrownBy(() -> service.storeFile(textFile, "items"));

            MockMultipartFile pdfFile = imageFile("scan.pdf", "application/pdf",
                    "%PDF-fake".getBytes(StandardCharsets.UTF_8));

            assertThatExceptionOfType(InvalidFileException.class)
                    .isThrownBy(() -> service.storeFile(pdfFile, "items"));
        }

        @Test
        void shouldRejectEmptyFile() {
            MockMultipartFile empty = imageFile("empty.jpg", "image/jpeg", new byte[0]);

            assertThatExceptionOfType(InvalidFileException.class)
                    .isThrownBy(() -> service.storeFile(empty, "items"));
        }

        @Test
        void shouldRejectNullFile() {
            assertThatExceptionOfType(InvalidFileException.class)
                    .isThrownBy(() -> service.storeFile(null, "items"));
        }

        @Test
        void shouldRejectFileExceedingMaxSize() {
            // maxFileSize is 1KB; 2KB of content over a valid image content type
            // must trip the size check specifically (non-empty, valid type).
            byte[] tooBig = new byte[2048];
            MockMultipartFile oversized = imageFile("big.jpg", "image/jpeg", tooBig);

            assertThatExceptionOfType(InvalidFileException.class)
                    .isThrownBy(() -> service.storeFile(oversized, "items"));
        }

        @Test
        void shouldGenerateDistinctFilenamesForSameOriginalName() {
            MockMultipartFile first = jpeg("cement.jpg");
            MockMultipartFile second = jpeg("cement.jpg");

            String urlA = service.storeFile(first, "items");
            String urlB = service.storeFile(second, "items");

            assertThat(urlA).isNotEqualTo(urlB);

            Path storedA = onDiskPathFor(urlA);
            Path storedB = onDiskPathFor(urlB);
            assertThat(storedA).isNotEqualTo(storedB);
            // Both files coexist on disk - no collision/overwrite.
            assertThat(Files.exists(storedA)).isTrue();
            assertThat(Files.exists(storedB)).isTrue();
        }

        @Test
        void shouldStoreFileWithNoExtensionWhenOriginalHasNone() {
            MockMultipartFile noExt = imageFile("cementphoto", "image/jpeg",
                    "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));

            String url = service.storeFile(noExt, "items");

            // Generated name is just the UUID, no trailing extension/dot.
            String filename = url.substring(url.lastIndexOf('/') + 1);
            assertThat(filename).doesNotContain(".");
            assertThat(Files.exists(onDiskPathFor(url))).isTrue();
        }

        @Test
        void shouldNotWriteOutsideSubfolderForCraftedFilenameWithPathSeparators() throws IOException {
            // The client filename is never used to build the target path - the
            // file always lands at {localPath}/{subfolder}/{UUID+ext}. So a
            // traversal-style name can't escape the subfolder.
            MockMultipartFile crafted = imageFile("../../evil.jpg", "image/jpeg",
                    "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));

            String url = service.storeFile(crafted, "items");

            Path stored = onDiskPathFor(url);
            // The stored file sits directly inside {tempDir}/items, not above it.
            assertThat(stored.getParent()).isEqualTo(tempDir.resolve("items"));
            assertThat(stored.normalize().startsWith(tempDir)).isTrue();
            assertThat(Files.exists(stored)).isTrue();

            // Nothing named "evil.jpg" was written anywhere in the temp tree.
            try (var walk = Files.walk(tempDir)) {
                assertThat(walk.noneMatch(p -> p.getFileName().toString().equals("evil.jpg"))).isTrue();
            }
        }

        @Test
        void shouldCreateSubfolderWhenItDoesNotExistYet() {
            Path freshSubfolder = tempDir.resolve("brand-new-subfolder");
            assertThat(Files.exists(freshSubfolder)).isFalse();

            MockMultipartFile file = jpeg("cement.jpg");
            String url = service.storeFile(file, "brand-new-subfolder");

            assertThat(Files.isDirectory(freshSubfolder)).isTrue();
            assertThat(Files.exists(onDiskPathFor(url))).isTrue();
        }

        @Test
        void shouldAcceptContentTypeCaseInsensitively() {
            // The implementation lowercases the content type before matching,
            // so an upper-case "IMAGE/JPEG" is accepted.
            MockMultipartFile upperType = imageFile("cement.jpg", "IMAGE/JPEG",
                    "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));

            String url = service.storeFile(upperType, "items");

            assertThat(Files.exists(onDiskPathFor(url))).isTrue();
        }
    }

    // ===============================================================
    // deleteFile
    // ===============================================================

    @Nested
    class DeleteFileTests {

        @Test
        void shouldDeletePreviouslyStoredFileFromDisk() {
            String url = service.storeFile(jpeg("cement.jpg"), "items");
            Path stored = onDiskPathFor(url);
            assertThat(Files.exists(stored)).isTrue();

            service.deleteFile(url);

            assertThat(Files.exists(stored)).isFalse();
        }

        @Test
        void shouldNotThrowWhenImageUrlIsNull() {
            assertThatCode(() -> service.deleteFile(null)).doesNotThrowAnyException();
        }

        @Test
        void shouldNotThrowWhenImageUrlIsBlank() {
            assertThatCode(() -> service.deleteFile("   ")).doesNotThrowAnyException();
        }

        @Test
        void shouldNotDeleteAnythingWhenUrlDoesNotMatchBaseUrl() throws IOException {
            // Store a real file, then attempt to delete via a URL with a totally
            // different base - nothing should happen and the real file stays.
            String url = service.storeFile(jpeg("cement.jpg"), "items");
            Path stored = onDiskPathFor(url);
            assertThat(Files.exists(stored)).isTrue();

            assertThatCode(() -> service.deleteFile("https://other-cdn.net/items/whatever.jpg"))
                    .doesNotThrowAnyException();

            assertThat(Files.exists(stored)).isTrue();
        }

        @Test
        void shouldNotThrowWhenFileAlreadyMissing() {
            // A well-formed URL under baseUrl, but no such file was ever written.
            String urlForMissingFile = BASE_URL + "/items/00000000-0000-0000-0000-000000000000.jpg";

            assertThatCode(() -> service.deleteFile(urlForMissingFile)).doesNotThrowAnyException();
        }

        @Test
        void shouldNotDeleteFileOutsideStorageRootOnPathTraversalAttempt() throws IOException {
            // Canary file OUTSIDE the storage root, which any successful traversal
            // would put at risk. The specific target isn't important — we're
            // just proving escape attempts are refused, not that a specific
            // file gets reached.
            Path canary = Files.createTempFile("canary-", ".txt");
            Files.writeString(canary, "do not delete me");
            assertThat(Files.exists(canary)).isTrue();

            try {
                // Relative "../" segments only — no absolute path embedded —
                // so URL parsing doesn't choke on Windows drive letters before
                // the escape check runs. The escape-detection logic is what
                // we're actually testing.
                String traversalUrl = BASE_URL + "/../../../../../../../../etc/passwd";

                assertThatExceptionOfType(IllegalArgumentException.class)
                        .isThrownBy(() -> service.deleteFile(traversalUrl))
                        .withMessageContaining("Storage path escapes root");

                // The canary outside the storage root is untouched regardless.
                assertThat(Files.exists(canary)).isTrue();
            } finally {
                Files.deleteIfExists(canary);
            }
        }

    }
}