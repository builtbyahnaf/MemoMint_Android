package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;

/**
 * Repository interface for {@link ConfirmationPolicy} CRUD operations.
 *
 * <h2>Design contract</h2>
 * <p>Each physical-item memory may have at most one {@link ConfirmationPolicy} row (one-to-one
 * relationship using {@code memory_id} as the primary key). This interface abstracts the
 * persistence operations so that domain and application services are decoupled from the
 * SQLite implementation (NFR-02 local-first, NFR-03 data integrity).</p>
 *
 * <h2>Upsert semantics</h2>
 * <p>{@link #saveOrUpdate(ConfirmationPolicy)} uses INSERT OR REPLACE semantics so callers
 * do not need to distinguish between creating a new policy and updating an existing one.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Implementations must be safe to call from a background thread.</p>
 *
 * @see ConfirmationPolicy
 */
public interface ConfirmationPolicyRepository {

    // -------------------------------------------------------------------------
    // Create / Update (Upsert)
    // -------------------------------------------------------------------------

    /**
     * Inserts or replaces the confirmation policy for a memory.
     *
     * <p>Uses INSERT OR REPLACE semantics keyed on {@code memory_id}. If a row already exists
     * for the given memory, all columns are overwritten with the values from {@code policy}.</p>
     *
     * @param policy the policy to persist; must not be null; {@code policy.getMemoryId()} must
     *               refer to an existing row in the {@code memories} table
     * @throws IllegalArgumentException if {@code policy} or its memoryId is null
     */
    void saveOrUpdate(ConfirmationPolicy policy);

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the confirmation policy for the given memory, or {@code null} if none exists.
     *
     * @param memoryId the stable UUID of the memory whose policy should be retrieved
     * @return the {@link ConfirmationPolicy}, or {@code null} if the memory has no policy
     */
    ConfirmationPolicy findByMemoryId(String memoryId);

    /**
     * Returns all enabled confirmation policies whose {@code next_check_at} timestamp is
     * less than or equal to the given reference time.
     *
     * <p>Equivalent to:
     * {@code SELECT * FROM confirmation_policies WHERE enabled=1 AND next_check_at <= nowMillis}.</p>
     *
     * <p>Used by the background check-in auditor ({@code CheckInReceiver}) to discover which
     * memories need a confirmation prompt sent to the user.</p>
     *
     * @param nowMillis the reference time in UTC epoch milliseconds (typically
     *                  {@code System.currentTimeMillis()})
     * @return list of due policies; never null; may be empty
     */
    List<ConfirmationPolicy> findDueForCheckIn(long nowMillis);

    // -------------------------------------------------------------------------
    // Update (targeted)
    // -------------------------------------------------------------------------

    /**
     * Updates the scheduling fields of an existing confirmation policy after a check-in prompt
     * has been issued or a confirmation has been recorded.
     *
     * <p>Only {@code next_check_at} and {@code last_prompted_at} are modified; other fields
     * (e.g. {@code interval_type}, {@code enabled}) remain unchanged.</p>
     *
     * @param memoryId        the stable UUID of the memory whose policy should be updated
     * @param nextCheckAtMs   the new scheduled time for the next check-in prompt, in UTC epoch ms
     * @param lastPromptedAt  the timestamp when the prompt was most recently issued; may be null
     * @return {@code true} if a row was updated; {@code false} if no matching memoryId was found
     */
    boolean updateNextCheckAt(String memoryId, long nextCheckAtMs, Long lastPromptedAt);

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Removes the confirmation policy for the given memory.
     *
     * <p>Note: in practice the {@code ON DELETE CASCADE} foreign key on the
     * {@code confirmation_policies} table automatically removes the policy when the parent
     * memory is hard-deleted. This method provides explicit programmatic control for cases
     * where only the policy (not the memory itself) should be removed.</p>
     *
     * @param memoryId the stable UUID of the memory whose policy should be removed
     * @return {@code true} if a row was deleted; {@code false} if no matching memoryId was found
     */
    boolean delete(String memoryId);
}
