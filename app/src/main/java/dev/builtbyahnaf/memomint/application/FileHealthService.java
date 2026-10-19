package dev.builtbyahnaf.memomint.application;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;

/**
 * Service responsible for auditing document access health and detecting permission revocations.
 *
 * <h2>Purpose & Architectural Invariants (Section 15.3, FR-08, Phase 4 Plan §4.2, §6.4)</h2>
 * <ul>
 *   <li><b>Truthful State Transitions:</b> Never claims a file is deleted when access is merely
 *       blocked by permission revocation or transient media unmounting.</li>
 *   <li><b>Explicit Mapping:</b>
 *     <ul>
 *       <li>{@link SecurityException} &rarr; {@link AccessState#PERMISSION_REVOKED}</li>
 *       <li>{@link FileNotFoundException} &rarr; {@link AccessState#MISSING_OR_UNAVAILABLE}</li>
 *       <li>{@link IOException} &rarr; {@link AccessState#TEMPORARILY_INACCESSIBLE}</li>
 *       <li>Successful probe &rarr; {@link AccessState#ACCESSIBLE}</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class FileHealthService {

    private static final String TAG = "FileHealthService";

    private final FileReferenceRepository fileRepo;

    public FileHealthService(FileReferenceRepository fileRepo) {
        this.fileRepo = Objects.requireNonNull(fileRepo, "fileRepo cannot be null");
    }

    /**
     * Probes the given document reference and evaluates its current accessibility health.
     *
     * @param ref            the FileReference to verify
     * @param streamProvider supplier opening a probe stream
     * @return evaluated AccessState
     */
    public AccessState checkHealth(FileReference ref, FileIndexingService.InputStreamProvider streamProvider) {
        if (ref == null) {
            return AccessState.MISSING_OR_UNAVAILABLE;
        }

        if (streamProvider == null) {
            return AccessState.TEMPORARILY_INACCESSIBLE;
        }

        try (InputStream in = streamProvider.openStream()) {
            if (in == null) {
                return AccessState.MISSING_OR_UNAVAILABLE;
            }
            // Successfully opened stream
            return AccessState.ACCESSIBLE;
        } catch (SecurityException se) {
            Log.w(TAG, "Permission revoked for " + ref.getDisplayName() + ": " + se.getMessage());
            return AccessState.PERMISSION_REVOKED;
        } catch (FileNotFoundException fnfe) {
            Log.w(TAG, "File not found for " + ref.getDisplayName() + ": " + fnfe.getMessage());
            return AccessState.MISSING_OR_UNAVAILABLE;
        } catch (IOException ioe) {
            Log.w(TAG, "Transient I/O error checking " + ref.getDisplayName() + ": " + ioe.getMessage());
            return AccessState.TEMPORARILY_INACCESSIBLE;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error checking health of " + ref.getDisplayName() + ": " + e.getMessage(), e);
            return AccessState.TEMPORARILY_INACCESSIBLE;
        }
    }

    /**
     * Checks document health and updates the database record with the new state and verification timestamp.
     *
     * @param ref            the FileReference to verify and persist
     * @param streamProvider supplier opening a probe stream
     * @return updated AccessState
     */
    public AccessState verifyAndUpdate(FileReference ref, FileIndexingService.InputStreamProvider streamProvider) {
        AccessState newState = checkHealth(ref, streamProvider);
        long now = System.currentTimeMillis();
        fileRepo.updateAccessState(ref.getId(), newState, now);
        return newState;
    }
}
