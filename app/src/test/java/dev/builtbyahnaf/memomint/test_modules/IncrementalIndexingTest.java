package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dev.builtbyahnaf.memomint.domain.file.ContentFingerprinter;
import dev.builtbyahnaf.memomint.domain.file.ExclusionPolicy;
import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Essential unit tests for incremental indexing fingerprinting and exclusion policies.
 */
public class IncrementalIndexingTest {

    private ContentFingerprinter fingerprinter;
    private ExclusionPolicy exclusionPolicy;

    @Before
    public void setUp() {
        fingerprinter = new ContentFingerprinter();
        exclusionPolicy = new ExclusionPolicy();
    }

    @Test
    public void testComputesIdenticalFingerprintForSameContent() throws IOException {
        String content = "MemoMint offline indexing content test";
        String hash1 = fingerprinter.computeFingerprint(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        String hash2 = fingerprinter.computeFingerprint(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    public void testComputesDifferentFingerprintForChangedContent() throws IOException {
        String content1 = "Original text version 1";
        String content2 = "Modified text version 2";

        String hash1 = fingerprinter.computeFingerprint(new ByteArrayInputStream(content1.getBytes(StandardCharsets.UTF_8)));
        String hash2 = fingerprinter.computeFingerprint(new ByteArrayInputStream(content2.getBytes(StandardCharsets.UTF_8)));

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void testExcludesOversizedFiles() {
        long limit = ExclusionPolicy.DEFAULT_MAX_FILE_SIZE_BYTES;
        long oversized = limit + 1;

        assertTrue(exclusionPolicy.isExcluded("huge_document.pdf", oversized));
        assertEquals(IndexErrorCode.ERR_FILE_TOO_LARGE, exclusionPolicy.getExclusionReason("huge_document.pdf", oversized));

        assertFalse(exclusionPolicy.isExcluded("normal_document.pdf", 1024 * 1024));
    }

    @Test
    public void testExcludesBinaryExtensions() {
        assertTrue(exclusionPolicy.isExcluded("firmware.bin", 5000));
        assertTrue(exclusionPolicy.isExcluded("installer.exe", 5000));
        assertTrue(exclusionPolicy.isExcluded("video.mp4", 5000));
        assertTrue(exclusionPolicy.isExcluded("archive.zip", 5000));

        assertFalse(exclusionPolicy.isExcluded("notes.txt", 5000));
        assertFalse(exclusionPolicy.isExcluded("research.docx", 5000));
        assertFalse(exclusionPolicy.isExcluded("manual.pdf", 5000));
    }

    @Test
    public void testExcludesHiddenFiles() {
        assertTrue(exclusionPolicy.isExcluded(".secret_config.txt", 100));
        assertTrue(exclusionPolicy.isExcluded(".hidden", 100));
        assertEquals("HIDDEN_FILE", exclusionPolicy.getExclusionReason(".secret_notes.md", 100));
    }
}
