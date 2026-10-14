package dev.builtbyahnaf.memomint.application;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.data.repository.MemoryRepository;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

/**
 * Application service coordinating use cases for creating, reading, updating, and deleting memories.
 *
 * <h2>Purpose & Architectural Role</h2>
 * <p>In accordance with Layer B (Application Services) of the MemoMint architecture, this service
 * decouples presentation components (activities, fragments, widgets) from persistence and data layers.
 * It enforces business invariants, input validation, timestamp assignment, UUID generation, and
 * asynchronous background execution so the Android main thread remains responsive (NFR-02, NFR-03).</p>
 *
 * <h2>Core Invariants Enforced</h2>
 * <ul>
 *   <li><b>Original-Content Preservation (FR-03):</b> The user's original text is treated as primary truth.
 *       Only explicit user edit requests are permitted to update {@code originalText}. Derived context,
 *       summaries, or background jobs must never overwrite this field.</li>
 *   <li><b>Immutable Identity:</b> Stable UUIDs are generated at creation time and never altered.</li>
 *   <li><b>Time Consistency:</b> Creation and modification timestamps use coordinated UTC epoch milliseconds.</li>
 *   <li><b>Status Separation:</b> Soft deletion ({@link MemoryStatus#DELETED}) and archiving
 *       ({@link MemoryStatus#ARCHIVED}) are isolated state transitions that do not immediately destroy
 *       the underlying row, allowing safe recovery and non-destructive workflows.</li>
 * </ul>
 *
 * <h2>Algorithm & Workflow: Memory Creation</h2>
 * <ol>
 *   <li><b>Validation:</b> Verify that {@code originalText} is non-null and contains non-whitespace text.
 *       Throw {@link IllegalArgumentException} if empty.</li>
 *   <li><b>ID Generation:</b> Generate a random type-4 UUID string.</li>
 *   <li><b>Timestamping:</b> Capture current UTC epoch milliseconds using {@link System#currentTimeMillis()}.
 *       Both {@code createdAt} and {@code updatedAt} are initialized to this timestamp.</li>
 *   <li><b>Model Construction:</b> Instantiate a {@link Memory} with initial status {@link MemoryStatus#ACTIVE}.
 *       Nullable fields (e.g. {@code title}, {@code sourceId}) are set without synthetic placeholders.</li>
 *   <li><b>Persistence:</b> Delegate to {@link MemoryRepository#save(Memory)} on a dedicated background worker
 *       thread, returning the result to the caller via {@link Callback}.</li>
 * </ol>
 *
 * <h2>Threading Model & Tradeoffs</h2>
 * <p>SQLite operations must not execute on the Android UI/main thread to avoid Application Not Responding (ANR)
 * dialogs. This service manages a dedicated single-threaded {@link ExecutorService} for serial database writes
 * and reads, guaranteeing serialized transactional execution without race conditions. Results are marshaled
 * back to the calling thread using Android's main looper {@link Handler}.</p>
 */
public class MemoryService {

    // -------------------------------------------------------------------------
    // Callback Interface
    // -------------------------------------------------------------------------

    /**
     * Asynchronous callback interface delivering operation outcomes back to the caller on the main thread.
     *
     * @param <T> the type of result produced by the operation
     */
    public interface Callback<T> {
        /**
         * Invoked on the Android main thread when the operation completes successfully.
         *
         * @param result the resulting data
         */
        void onSuccess(T result);

        /**
         * Invoked on the Android main thread if an unexpected error occurs during processing.
         *
         * @param throwable the captured exception
         */
        void onError(Throwable throwable);
    }

    // -------------------------------------------------------------------------
    // Dependencies & Concurrency
    // -------------------------------------------------------------------------

    private final MemoryRepository repository;
    private final ExecutorService backgroundExecutor;
    private final Handler mainHandler;

    /**
     * Default constructor initializing standard background executor and main thread handler.
     *
     * @param repository the data repository backing memory persistence; must not be null
     */
    public MemoryService(MemoryRepository repository) {
        this(repository, Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper()));
    }

    /**
     * Test-friendly constructor allowing injection of custom executors and handlers (e.g. for synchronous tests).
     *
     * @param repository         the memory repository implementation
     * @param backgroundExecutor executor for running database queries off the UI thread
     * @param mainHandler        handler for posting callbacks back to the main UI thread
     */
    public MemoryService(MemoryRepository repository, ExecutorService backgroundExecutor, Handler mainHandler) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        if (backgroundExecutor == null) {
            throw new IllegalArgumentException("backgroundExecutor must not be null");
        }
        if (mainHandler == null) {
            throw new IllegalArgumentException("mainHandler must not be null");
        }
        this.repository = repository;
        this.backgroundExecutor = backgroundExecutor;
        this.mainHandler = mainHandler;
    }

    // =========================================================================
    // Synchronous Domain Operations (Test & Background Worker Friendly)
    // =========================================================================

    /**
     * Synchronously creates and persists a new memory record.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Memory m = service.createMemorySync(
     *     "Spare headphones are in the bedside table drawer.",
     *     "Headphones Location",
     *     MemoryType.PHYSICAL_ITEM,
     *     SourceType.TYPED,
     *     null
     * );
     * }</pre>
     *
     * @param originalText the unedited text provided by the user; must be non-empty
     * @param title        optional short title; null if omitted
     * @param type         the classification of the memory; defaults to NOTE if null
     * @param sourceType   the entry mechanism (e.g. TYPED, VOICE); defaults to TYPED if null
     * @param sourceId     optional identifier of the originating source
     * @return the newly created and persisted {@link Memory} instance
     * @throws IllegalArgumentException if originalText is null or blank
     */
    public Memory createMemorySync(
            String originalText,
            String title,
            MemoryType type,
            SourceType sourceType,
            String sourceId
    ) {
        if (originalText == null || originalText.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory originalText must not be empty or blank");
        }

        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        MemoryType effectiveType = (type != null) ? type : MemoryType.NOTE;
        SourceType effectiveSource = (sourceType != null) ? sourceType : SourceType.TYPED;
        String trimmedTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : null;

        Memory memory = new Memory(
                id,
                effectiveType,
                originalText.trim(),
                trimmedTitle,
                now,
                now,
                effectiveSource,
                sourceId,
                MemoryStatus.ACTIVE,
                /* lastConfirmedAt= */ null
        );

        repository.save(memory);
        return memory;
    }

    /**
     * Synchronously updates an existing memory based on explicit user edits.
     *
     * <p><b>Preservation Check:</b> This method only accepts user-initiated edits. It verifies
     * that the target memory exists before applying updates and updates {@code updatedAt}.</p>
     *
     * @param id           the UUID of the existing memory to update
     * @param newText      the user-edited text; must be non-empty
     * @param newTitle     optional updated title
     * @param newType      updated memory classification
     * @return the updated Memory instance
     * @throws IllegalArgumentException if memory not found or newText is invalid
     */
    public Memory updateMemorySync(String id, String newText, String newTitle, MemoryType newType) {
        if (id == null) throw new IllegalArgumentException("Memory ID must not be null");
        if (newText == null || newText.trim().isEmpty()) {
            throw new IllegalArgumentException("Memory newText must not be empty or blank");
        }

        Memory existing = repository.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("No memory found with ID: " + id);
        }

        long now = System.currentTimeMillis();
        existing.setOriginalText(newText.trim());
        existing.setTitle((newTitle != null && !newTitle.trim().isEmpty()) ? newTitle.trim() : null);
        if (newType != null) {
            existing.setType(newType);
        }
        existing.setUpdatedAt(now);

        repository.update(existing);
        return existing;
    }

    /**
     * Synchronously records a user confirmation that the memory's recorded facts are still accurate.
     *
     * @param id the UUID of the memory to confirm
     * @return true if updated successfully
     */
    public boolean confirmMemorySync(String id) {
        if (id == null) throw new IllegalArgumentException("Memory ID must not be null");
        return repository.recordConfirmation(id, System.currentTimeMillis());
    }

    /**
     * Synchronously soft-deletes a memory by transitioning its status to {@link MemoryStatus#DELETED}.
     *
     * @param id the UUID of the memory to soft-delete
     * @return true if status was updated
     */
    public boolean softDeleteMemorySync(String id) {
        if (id == null) throw new IllegalArgumentException("Memory ID must not be null");
        return repository.softDelete(id);
    }

    /**
     * Synchronously restores a soft-deleted or archived memory back to {@link MemoryStatus#ACTIVE}.
     *
     * @param id the UUID of the memory to restore
     * @return true if status was updated
     */
    public boolean restoreMemorySync(String id) {
        if (id == null) throw new IllegalArgumentException("Memory ID must not be null");
        return repository.updateStatus(id, MemoryStatus.ACTIVE);
    }

    /**
     * Synchronously archives a memory ({@link MemoryStatus#ARCHIVED}).
     *
     * @param id the UUID of the memory to archive
     * @return true if status was updated
     */
    public boolean archiveMemorySync(String id) {
        if (id == null) throw new IllegalArgumentException("Memory ID must not be null");
        return repository.updateStatus(id, MemoryStatus.ARCHIVED);
    }

    /**
     * Synchronously permanently purges a memory and all cascade-dependent records.
     *
     * @param id the UUID of the memory to permanently erase
     * @return true if deleted
     */
    public boolean hardDeleteMemorySync(String id) {
        if (id == null) throw new IllegalArgumentException("Memory ID must not be null");
        return repository.hardDelete(id);
    }

    /**
     * Synchronously returns all active memories ordered newest first.
     *
     * @return list of active memories
     */
    public List<Memory> getActiveMemoriesSync() {
        return repository.findByStatus(MemoryStatus.ACTIVE);
    }

    /**
     * Synchronously returns all memories in the given status ordered newest first.
     *
     * @param status status filter
     * @return list of matching memories
     */
    public List<Memory> getMemoriesByStatusSync(MemoryStatus status) {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        return repository.findByStatus(status);
    }

    /**
     * Synchronously retrieves a single memory by ID.
     *
     * @param id memory UUID
     * @return memory or null
     */
    public Memory getMemoryByIdSync(String id) {
        return repository.findById(id);
    }

    // =========================================================================
    // Asynchronous Operations for Android UI
    // =========================================================================

    /**
     * Asynchronously creates a new memory off the main thread and notifies the callback on the main thread.
     */
    public void createMemory(
            String originalText,
            String title,
            MemoryType type,
            SourceType sourceType,
            String sourceId,
            Callback<Memory> callback
    ) {
        backgroundExecutor.execute(() -> {
            try {
                Memory memory = createMemorySync(originalText, title, type, sourceType, sourceId);
                postSuccess(callback, memory);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously updates a memory off the main thread.
     */
    public void updateMemory(
            String id,
            String newText,
            String newTitle,
            MemoryType newType,
            Callback<Memory> callback
    ) {
        backgroundExecutor.execute(() -> {
            try {
                Memory updated = updateMemorySync(id, newText, newTitle, newType);
                postSuccess(callback, updated);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously confirms a memory's freshness.
     */
    public void confirmMemory(String id, Callback<Boolean> callback) {
        backgroundExecutor.execute(() -> {
            try {
                boolean result = confirmMemorySync(id);
                postSuccess(callback, result);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously soft-deletes a memory.
     */
    public void softDeleteMemory(String id, Callback<Boolean> callback) {
        backgroundExecutor.execute(() -> {
            try {
                boolean result = softDeleteMemorySync(id);
                postSuccess(callback, result);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously restores a memory to ACTIVE.
     */
    public void restoreMemory(String id, Callback<Boolean> callback) {
        backgroundExecutor.execute(() -> {
            try {
                boolean result = restoreMemorySync(id);
                postSuccess(callback, result);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously archives a memory.
     */
    public void archiveMemory(String id, Callback<Boolean> callback) {
        backgroundExecutor.execute(() -> {
            try {
                boolean result = archiveMemorySync(id);
                postSuccess(callback, result);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously permanently deletes a memory and its cascade records.
     */
    public void hardDeleteMemory(String id, Callback<Boolean> callback) {
        backgroundExecutor.execute(() -> {
            try {
                boolean result = hardDeleteMemorySync(id);
                postSuccess(callback, result);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously loads active memories for display in UI lists.
     */
    public void loadActiveMemories(Callback<List<Memory>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<Memory> list = getActiveMemoriesSync();
                postSuccess(callback, list);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    /**
     * Asynchronously loads memories matching a given status.
     */
    public void loadMemoriesByStatus(MemoryStatus status, Callback<List<Memory>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<Memory> list = getMemoriesByStatusSync(status);
                postSuccess(callback, list);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private <T> void postSuccess(Callback<T> callback, T data) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(data));
        }
    }

    private <T> void postError(Callback<T> callback, Throwable t) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(t));
        }
    }
}
