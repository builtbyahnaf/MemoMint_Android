package dev.builtbyahnaf.memomint.application;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants.IndexJobs;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.data.repository.FileSearchRepository;
import dev.builtbyahnaf.memomint.data.repository.IndexJobRepository;
import dev.builtbyahnaf.memomint.domain.file.ContentFingerprinter;
import dev.builtbyahnaf.memomint.domain.file.ExclusionPolicy;
import dev.builtbyahnaf.memomint.domain.file.parser.DocumentParseException;
import dev.builtbyahnaf.memomint.domain.file.parser.DocumentParser;
import dev.builtbyahnaf.memomint.domain.file.parser.DocumentParserFactory;
import dev.builtbyahnaf.memomint.domain.file.parser.ParseResult;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;
import dev.builtbyahnaf.memomint.domain.model.IndexJob;
import dev.builtbyahnaf.memomint.domain.model.IndexingStatus;

/**
 * Orchestrates background document indexing, incremental change detection,
 * text extraction, and full-text index updates.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §2.2, §6.4)</h2>
 * <ul>
 *   <li><b>5-Stage Deterministic Pipeline:</b> Enforces pre-flight checks, incremental delta detection,
 *       streaming parsing, FTS persistence, and transactional status recording.</li>
 *   <li><b>Incremental Change Avoidance:</b> Skips re-parsing if size, timestamp, and SHA-256 fingerprint
 *       are identical to previous indexing runs.</li>
 *   <li><b>Zero Unhandled Crashes:</b> Encapsulates all parser exceptions into structured error codes
 *       without halting queue execution (NFR-08).</li>
 * </ul>
 */
public class FileIndexingService {

    private static final String TAG = "FileIndexingService";
    private static final int DEFAULT_MAX_CHARS = 500_000; // 500k characters ceiling

    @FunctionalInterface
    public interface InputStreamProvider {
        InputStream openStream() throws IOException;
    }

    private final FileReferenceRepository fileRepo;
    private final IndexJobRepository jobRepo;
    private final FileSearchRepository searchRepo;
    private final DocumentParserFactory parserFactory;
    private final ExclusionPolicy exclusionPolicy;
    private final ContentFingerprinter fingerprinter;
    private final Executor backgroundExecutor;

    public FileIndexingService(
            FileReferenceRepository fileRepo,
            IndexJobRepository jobRepo,
            FileSearchRepository searchRepo,
            DocumentParserFactory parserFactory,
            ExclusionPolicy exclusionPolicy,
            ContentFingerprinter fingerprinter,
            Executor backgroundExecutor
    ) {
        this.fileRepo = Objects.requireNonNull(fileRepo, "fileRepo cannot be null");
        this.jobRepo = Objects.requireNonNull(jobRepo, "jobRepo cannot be null");
        this.searchRepo = Objects.requireNonNull(searchRepo, "searchRepo cannot be null");
        this.parserFactory = (parserFactory != null) ? parserFactory : DocumentParserFactory.getInstance();
        this.exclusionPolicy = (exclusionPolicy != null) ? exclusionPolicy : new ExclusionPolicy();
        this.fingerprinter = (fingerprinter != null) ? fingerprinter : new ContentFingerprinter();
        this.backgroundExecutor = (backgroundExecutor != null) ? backgroundExecutor : Executors.newSingleThreadExecutor();
    }

    /**
     * Enqueues a document for background indexing.
     */
    public IndexJob enqueueFile(FileReference ref, InputStreamProvider streamProvider) {
        String jobId = UUID.randomUUID().toString();
        IndexJob job = new IndexJob(
                jobId,
                ref.getId(),
                IndexJobs.Status.PENDING,
                0,
                null,
                System.currentTimeMillis(),
                null
        );
        jobRepo.enqueue(job);

        backgroundExecutor.execute(() -> processJob(job, ref, streamProvider));
        return job;
    }

    /**
     * Synchronously executes the 5-stage indexing pipeline for a document.
     * Useful for direct invocations or deterministic unit tests.
     */
    public IndexJob indexFileSync(FileReference ref, InputStreamProvider streamProvider) {
        String jobId = UUID.randomUUID().toString();
        IndexJob job = new IndexJob(
                jobId,
                ref.getId(),
                IndexJobs.Status.PENDING,
                0,
                null,
                System.currentTimeMillis(),
                null
        );
        jobRepo.enqueue(job);
        return processJob(job, ref, streamProvider);
    }

    private IndexJob processJob(IndexJob job, FileReference ref, InputStreamProvider streamProvider) {
        long startTime = System.currentTimeMillis();
        jobRepo.updateStatus(job.getId(), IndexJobs.Status.RUNNING, 10, null);

        // -----------------------------------------------------------------
        // Stage 1: Pre-flight & Health Check
        // -----------------------------------------------------------------
        long sizeBytes = (ref.getSizeBytes() != null) ? ref.getSizeBytes() : 0L;
        String exclusionReason = exclusionPolicy.getExclusionReason(ref.getDisplayName(), sizeBytes);

        if (exclusionReason != null) {
            String errorCode = IndexErrorCode.ERR_FILE_TOO_LARGE.equals(exclusionReason)
                    ? IndexErrorCode.ERR_FILE_TOO_LARGE
                    : "EXCLUDED";
            jobRepo.updateStatus(job.getId(), IndexJobs.Status.FAILED, 100, errorCode);
            fileRepo.updateIndexingStatus(ref.getId(), IndexingStatus.EXCLUDED, null, null);
            return jobRepo.findById(job.getId());
        }

        // -----------------------------------------------------------------
        // Stage 2: Incremental Change Detection
        // -----------------------------------------------------------------
        jobRepo.updateStatus(job.getId(), IndexJobs.Status.RUNNING, 30, null);
        String currentFingerprint = null;
        try (InputStream in = streamProvider.openStream()) {
            if (in != null) {
                currentFingerprint = fingerprinter.computeFingerprint(in);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed computing fingerprint for " + ref.getDisplayName() + ": " + e.getMessage());
        }

        if (currentFingerprint != null
                && currentFingerprint.equals(ref.getContentFingerprint())
                && ref.getLastIndexedAt() != null) {
            // Unchanged; skip re-indexing
            jobRepo.updateStatus(job.getId(), IndexJobs.Status.DONE, 100, null);
            fileRepo.updateAccessState(ref.getId(), AccessState.ACCESSIBLE, System.currentTimeMillis());
            return jobRepo.findById(job.getId());
        }

        // -----------------------------------------------------------------
        // Stage 3: Streaming Document Parsing
        // -----------------------------------------------------------------
        jobRepo.updateStatus(job.getId(), IndexJobs.Status.RUNNING, 60, null);
        String extension = getExtension(ref.getDisplayName());
        DocumentParser parser = parserFactory.getParser(ref.getMimeType(), extension);

        ParseResult parseResult = null;
        try (InputStream in = streamProvider.openStream()) {
            if (in == null) {
                throw new DocumentParseException(IndexErrorCode.ERR_FILE_NOT_FOUND, "Unable to open document stream");
            }
            parseResult = parser.parse(in, DEFAULT_MAX_CHARS);
        } catch (DocumentParseException dpe) {
            Log.e(TAG, "Parse failure for " + ref.getDisplayName() + ": " + dpe.getMessage());
            jobRepo.updateStatus(job.getId(), IndexJobs.Status.FAILED, 100, dpe.getErrorCode());
            fileRepo.updateIndexingStatus(ref.getId(), IndexingStatus.FAILED, null, currentFingerprint);
            return jobRepo.findById(job.getId());
        } catch (IOException ioe) {
            Log.e(TAG, "I/O error during indexing: " + ioe.getMessage());
            jobRepo.updateStatus(job.getId(), IndexJobs.Status.FAILED, 100, IndexErrorCode.ERR_IO_TIMEOUT);
            fileRepo.updateIndexingStatus(ref.getId(), IndexingStatus.FAILED, null, currentFingerprint);
            return jobRepo.findById(job.getId());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error indexing document: " + e.getMessage(), e);
            jobRepo.updateStatus(job.getId(), IndexJobs.Status.FAILED, 100, IndexErrorCode.ERR_PARSE_FAILED);
            fileRepo.updateIndexingStatus(ref.getId(), IndexingStatus.FAILED, null, currentFingerprint);
            return jobRepo.findById(job.getId());
        }

        // -----------------------------------------------------------------
        // Stage 4: Text Content & FTS Indexing
        // -----------------------------------------------------------------
        jobRepo.updateStatus(job.getId(), IndexJobs.Status.RUNNING, 85, null);
        boolean isScanned = parseResult.isScannedNeedsOcr();
        String textToIndex = parseResult.getExtractedText();

        if (!isScanned && !textToIndex.isEmpty()) {
            searchRepo.indexDocumentContent(ref.getId(), ref.getDisplayName(), textToIndex);
        }

        // -----------------------------------------------------------------
        // Stage 5: Status Commit
        // -----------------------------------------------------------------
        IndexingStatus finalStatus = isScanned ? IndexingStatus.DONE_WITHOUT_TEXT : IndexingStatus.DONE;
        long now = System.currentTimeMillis();
        fileRepo.updateIndexingStatus(ref.getId(), finalStatus, now, currentFingerprint);
        fileRepo.updateAccessState(ref.getId(), AccessState.ACCESSIBLE, now);

        String errorCode = isScanned ? IndexErrorCode.ERR_PDF_SCANNED : null;
        jobRepo.updateStatus(job.getId(), IndexJobs.Status.DONE, 100, errorCode);

        return jobRepo.findById(job.getId());
    }

    private static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1)
                ? fileName.substring(dot + 1).toLowerCase()
                : "";
    }
}
