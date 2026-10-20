package dev.builtbyahnaf.memomint.application;

import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;

/**
 * Service managing user-guided recovery and reconnection of broken or revoked file references.
 *
 * <h2>Purpose &amp; Invariants (Section 15.3, FR-08, Phase 4 Plan §4.2, Phase 5 Plan §2.5)</h2>
 * <ul>
 *   <li><b>Truthful State Recovery:</b> Handles transitions from {@link AccessState#PERMISSION_REVOKED}
 *       or {@link AccessState#MISSING_OR_UNAVAILABLE} back to {@link AccessState#ACCESSIBLE} when the user
 *       reselects or re-grants access via the Storage Access Framework (SAF).</li>
 *   <li><b>Decoupled Document Retention:</b> If a file is permanently lost or moved outside reach,
 *       allows retaining the memory text context while marking the reference as
 *       {@link AccessState#MISSING_OR_UNAVAILABLE} without deleting the parent memory.</li>
 * </ul>
 */
public class FileHealthRecoveryService {

    private final FileReferenceRepository fileRepo;
    private final FileHealthService fileHealthService;

    /**
     * Constructs a new {@link FileHealthRecoveryService}.
     *
     * @param fileRepo          repository managing {@link FileReference} records; must not be null
     * @param fileHealthService service evaluating document access health; must not be null
     * @throws IllegalArgumentException if either parameter is null
     */
    public FileHealthRecoveryService(FileReferenceRepository fileRepo, FileHealthService fileHealthService) {
        if (fileRepo == null) {
            throw new IllegalArgumentException("fileRepo must not be null");
        }
        if (fileHealthService == null) {
            throw new IllegalArgumentException("fileHealthService must not be null");
        }
        this.fileRepo = fileRepo;
        this.fileHealthService = fileHealthService;
    }

    /**
     * Prepares for document reconnection by retrieving the existing file reference record.
     *
     * @param fileId stable UUID of the file reference to reconnect
     * @return matching {@link FileReference}, or null if fileId is null or not found
     */
    public FileReference prepareReconnection(String fileId) {
        if (fileId == null) {
            return null;
        }
        return fileRepo.findById(fileId);
    }

    /**
     * Finalizes reconnection of a file reference with a new content URI and resets its health state to ACCESSIBLE.
     *
     * @param fileId       stable UUID of the file reference to update; must not be null
     * @param newUriString newly selected content URI or filesystem path; must not be null
     * @param nowMillis    UTC epoch timestamp in milliseconds of reconnection
     * @return true if update succeeded, false if an error occurred or parameters were invalid
     */
    public boolean finalizeReconnection(String fileId, String newUriString, long nowMillis) {
        if (fileId == null || newUriString == null) {
            return false;
        }

        try {
            fileRepo.updateAccessState(fileId, AccessState.ACCESSIBLE, nowMillis);

            FileReference ref = fileRepo.findById(fileId);
            if (ref != null) {
                ref.setUriOrPath(newUriString);
                ref.setAccessState(AccessState.ACCESSIBLE);
                ref.setLastVerifiedAt(nowMillis);
                fileRepo.save(ref);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Marks a file reference as missing while preserving its text context and memory association.
     *
     * @param fileId    stable UUID of the file reference; must not be null
     * @param nowMillis UTC epoch timestamp in milliseconds
     * @return true if status was updated successfully, false otherwise
     */
    public boolean markKeptWithoutFile(String fileId, long nowMillis) {
        if (fileId == null) {
            return false;
        }

        try {
            fileRepo.updateAccessState(fileId, AccessState.MISSING_OR_UNAVAILABLE, nowMillis);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
