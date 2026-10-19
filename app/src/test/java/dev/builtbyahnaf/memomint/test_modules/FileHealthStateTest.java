package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.builtbyahnaf.memomint.application.FileHealthService;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.IndexingStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Essential unit tests for {@link FileHealthService} state transitions and permission auditing.
 */
public class FileHealthStateTest {

    private FileHealthService healthService;
    private FakeFileReferenceRepository fakeRepo;
    private FileReference sampleRef;

    @Before
    public void setUp() {
        fakeRepo = new FakeFileReferenceRepository();
        healthService = new FileHealthService(fakeRepo);

        sampleRef = new FileReference(
                "file-123",
                "content://com.android.providers.media.documents/document/123",
                "invoice.pdf",
                "application/pdf",
                1024L,
                System.currentTimeMillis(),
                null,
                null,
                AccessState.NOT_YET_VERIFIED,
                null,
                IndexingStatus.PENDING,
                false
        );
        fakeRepo.save(sampleRef);
    }

    @Test
    public void testSuccessfulProbeTransitionsToAccessible() {
        AccessState state = healthService.verifyAndUpdate(
                sampleRef,
                () -> new ByteArrayInputStream("valid content".getBytes())
        );

        assertEquals(AccessState.ACCESSIBLE, state);
        assertEquals(AccessState.ACCESSIBLE, fakeRepo.findById("file-123").getAccessState());
        assertNotNull(fakeRepo.findById("file-123").getLastVerifiedAt());
    }

    @Test
    public void testSecurityExceptionMapsToPermissionRevokedWithoutDataLoss() {
        AccessState state = healthService.verifyAndUpdate(
                sampleRef,
                () -> { throw new SecurityException("Permission Denial: no persisted grant"); }
        );

        assertEquals(AccessState.PERMISSION_REVOKED, state);
        assertEquals(AccessState.PERMISSION_REVOKED, fakeRepo.findById("file-123").getAccessState());
        // Verify file reference record still exists
        assertNotNull(fakeRepo.findById("file-123"));
    }

    @Test
    public void testFileNotFoundExceptionMapsToMissingOrUnavailable() {
        AccessState state = healthService.verifyAndUpdate(
                sampleRef,
                () -> { throw new FileNotFoundException("File not found on storage"); }
        );

        assertEquals(AccessState.MISSING_OR_UNAVAILABLE, state);
        assertEquals(AccessState.MISSING_OR_UNAVAILABLE, fakeRepo.findById("file-123").getAccessState());
    }

    @Test
    public void testTransientIoExceptionMapsToTemporarilyInaccessible() {
        AccessState state = healthService.verifyAndUpdate(
                sampleRef,
                () -> { throw new IOException("Media unmounted or socket timeout"); }
        );

        assertEquals(AccessState.TEMPORARILY_INACCESSIBLE, state);
        assertEquals(AccessState.TEMPORARILY_INACCESSIBLE, fakeRepo.findById("file-123").getAccessState());
    }

    // -------------------------------------------------------------------------
    // In-memory fake repository for test isolation
    // -------------------------------------------------------------------------
    private static class FakeFileReferenceRepository implements FileReferenceRepository {
        private final Map<String, FileReference> store = new HashMap<>();

        @Override
        public void save(FileReference fileReference) {
            store.put(fileReference.getId(), fileReference);
        }

        @Override
        public FileReference findById(String id) {
            return store.get(id);
        }

        @Override
        public FileReference findByUri(String uriOrPath) {
            for (FileReference r : store.values()) {
                if (r.getUriOrPath().equals(uriOrPath)) return r;
            }
            return null;
        }

        @Override
        public List<FileReference> findAll(int limit) {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<FileReference> findByAccessState(AccessState state) {
            List<FileReference> res = new ArrayList<>();
            for (FileReference r : store.values()) {
                if (r.getAccessState() == state) res.add(r);
            }
            return res;
        }

        @Override
        public void updateAccessState(String id, AccessState state, long verifiedAt) {
            FileReference existing = store.get(id);
            if (existing != null) {
                existing.setAccessState(state);
                existing.setLastVerifiedAt(verifiedAt);
            }
        }

        @Override
        public void updateIndexingStatus(String id, IndexingStatus status, Long indexedAt, String fingerprint) {
            FileReference existing = store.get(id);
            if (existing != null) {
                existing.setIndexingStatus(status);
                existing.setLastIndexedAt(indexedAt);
                existing.setContentFingerprint(fingerprint);
            }
        }

        @Override
        public void delete(String id) {
            store.remove(id);
        }
    }
}
