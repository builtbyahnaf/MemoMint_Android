package dev.builtbyahnaf.memomint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;

import dev.builtbyahnaf.memomint.application.FileHealthRecoveryService;
import dev.builtbyahnaf.memomint.application.FileHealthService;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.IndexingStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit and integration tests for {@link FileHealthRecoveryService}.
 *
 * <p>Verifies reconnection flows, state transitions from {@link AccessState#PERMISSION_REVOKED}
 * or {@link AccessState#MISSING_OR_UNAVAILABLE} back to {@link AccessState#ACCESSIBLE},
 * and marking a document as missing while preserving its record (Phase 5, Section 15.3, FR-08).</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FileHealthRecoveryTest {

    private MemoMintDatabase db;
    private FileReferenceRepositoryImpl fileRepo;
    private FileHealthService healthService;
    private FileHealthRecoveryService recoveryService;

    @Before
    public void setUp() {
        db = new MemoMintDatabase(RuntimeEnvironment.getApplication(), null);
        fileRepo = new FileReferenceRepositoryImpl(db);
        healthService = new FileHealthService(fileRepo);
        recoveryService = new FileHealthRecoveryService(fileRepo, healthService);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private FileReference insertSampleFile(AccessState initialState) {
        String id = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        FileReference ref = new FileReference(
                id,
                "content://media/external/file/101",
                "notes_final.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                1024L,
                now,
                null,
                "fingerprint_abc",
                initialState,
                now,
                IndexingStatus.PENDING,
                false
        );
        fileRepo.save(ref);
        return ref;
    }

    @Test
    public void prepareReconnection_returnsFileReference_whenFound() {
        FileReference ref = insertSampleFile(AccessState.PERMISSION_REVOKED);
        FileReference loaded = recoveryService.prepareReconnection(ref.getId());

        assertNotNull(loaded);
        assertEquals(ref.getId(), loaded.getId());
        assertEquals("notes_final.docx", loaded.getDisplayName());
    }

    @Test
    public void prepareReconnection_returnsNull_whenNotFoundOrNullId() {
        assertNull(recoveryService.prepareReconnection("non-existent-id"));
        assertNull(recoveryService.prepareReconnection(null));
    }

    @Test
    public void finalizeReconnection_updatesUriAndAccessStateToAccessible() {
        FileReference ref = insertSampleFile(AccessState.PERMISSION_REVOKED);
        long now = System.currentTimeMillis();
        String newUri = "content://media/external/file/202";

        boolean success = recoveryService.finalizeReconnection(ref.getId(), newUri, now);
        assertTrue(success);

        FileReference updated = fileRepo.findById(ref.getId());
        assertNotNull(updated);
        assertEquals(AccessState.ACCESSIBLE, updated.getAccessState());
        assertEquals(newUri, updated.getUriOrPath());
        assertEquals(now, (long) updated.getLastVerifiedAt());
    }

    @Test
    public void markKeptWithoutFile_transitionsStateToMissingOrUnavailable() {
        FileReference ref = insertSampleFile(AccessState.ACCESSIBLE);
        long now = System.currentTimeMillis();

        boolean success = recoveryService.markKeptWithoutFile(ref.getId(), now);
        assertTrue(success);

        FileReference updated = fileRepo.findById(ref.getId());
        assertNotNull(updated);
        assertEquals(AccessState.MISSING_OR_UNAVAILABLE, updated.getAccessState());
    }

    @Test
    public void finalizeReconnection_returnsFalse_forNullInputs() {
        assertFalse(recoveryService.finalizeReconnection(null, "content://uri", System.currentTimeMillis()));
        assertFalse(recoveryService.finalizeReconnection("some-id", null, System.currentTimeMillis()));
    }
}
