package com.genealogy.store;

import com.genealogy.config.MediaStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for path traversal protection in LocalObjectStore.
 * Verifies that attempts to escape the storage root via "../" sequences
 * or absolute paths are rejected.
 */
class LocalObjectStorePathTraversalTest {

    @TempDir
    Path tempDir;

    private LocalObjectStore store;
    private Path storageRoot;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = tempDir.resolve("media-storage");
        Files.createDirectories(storageRoot);

        MediaStorageProperties props = new MediaStorageProperties();
        MediaStorageProperties.Local local = new MediaStorageProperties.Local();
        local.setRoot(storageRoot.toString());
        props.setLocal(local);
        props.setUploadSecret("test-secret-key-for-testing");

        store = new LocalObjectStore(props);
    }

    @Test
    void storeFile_withValidKey_succeeds() throws IOException {
        String validKey = "families/abc123/media/photo.jpg";
        byte[] content = "test content".getBytes();

        store.storeFile(validKey, new ByteArrayInputStream(content), content.length);

        assertTrue(store.fileExists(validKey));
        Path storedFile = store.getFilePath(validKey);
        assertTrue(Files.exists(storedFile));
        assertArrayEquals(content, Files.readAllBytes(storedFile));
    }

    @Test
    void storeFile_withParentTraversal_throwsPathTraversalException() {
        String maliciousKey = "../../../etc/passwd";
        byte[] content = "malicious content".getBytes();

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.storeFile(maliciousKey, new ByteArrayInputStream(content), content.length)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void storeFile_withEmbeddedParentTraversal_throwsPathTraversalException() {
        String maliciousKey = "families/abc/../../../etc/passwd";
        byte[] content = "malicious content".getBytes();

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.storeFile(maliciousKey, new ByteArrayInputStream(content), content.length)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void storeFile_withAbsolutePath_throwsPathTraversalException() {
        String maliciousKey = "/etc/passwd";
        byte[] content = "malicious content".getBytes();

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.storeFile(maliciousKey, new ByteArrayInputStream(content), content.length)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void storeFile_withAbsolutePathViaDots_throwsPathTraversalException() {
        String maliciousKey = "/../../../tmp/malicious.txt";
        byte[] content = "malicious content".getBytes();

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.storeFile(maliciousKey, new ByteArrayInputStream(content), content.length)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void fileExists_withParentTraversal_throwsPathTraversalException() {
        String maliciousKey = "../../../etc/passwd";

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.fileExists(maliciousKey)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void fileExists_withAbsolutePath_throwsPathTraversalException() {
        String maliciousKey = "/etc/passwd";

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.fileExists(maliciousKey)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void getFilePath_withParentTraversal_throwsPathTraversalException() {
        String maliciousKey = "../../../etc/passwd";

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.getFilePath(maliciousKey)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void getFilePath_withAbsolutePath_throwsPathTraversalException() {
        String maliciousKey = "/etc/passwd";

        PathTraversalException ex = assertThrows(
            PathTraversalException.class,
            () -> store.getFilePath(maliciousKey)
        );
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void storeFile_withNestedValidDirectory_succeeds() throws IOException {
        String validKey = "families/abc123/media/2024/01/photo.jpg";
        byte[] content = "test content".getBytes();

        store.storeFile(validKey, new ByteArrayInputStream(content), content.length);

        assertTrue(store.fileExists(validKey));
    }

    @Test
    void storeFile_withDotInFilename_succeeds() throws IOException {
        String validKey = "families/abc123/media/photo.backup.jpg";
        byte[] content = "test content".getBytes();

        store.storeFile(validKey, new ByteArrayInputStream(content), content.length);

        assertTrue(store.fileExists(validKey));
    }

    @Test
    void storeFile_withCurrentDirReference_succeeds() throws IOException {
        String validKey = "families/./abc123/media/photo.jpg";
        byte[] content = "test content".getBytes();

        store.storeFile(validKey, new ByteArrayInputStream(content), content.length);

        assertTrue(store.fileExists("families/abc123/media/photo.jpg"));
    }

    @Test
    void storeFile_withDeepParentTraversal_throwsPathTraversalException() {
        String maliciousKey = "families/abc/../../../../../tmp/evil.txt";
        byte[] content = "malicious content".getBytes();

        assertThrows(
            PathTraversalException.class,
            () -> store.storeFile(maliciousKey, new ByteArrayInputStream(content), content.length)
        );
    }
}
