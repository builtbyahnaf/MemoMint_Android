package dev.builtbyahnaf.memomint.ui.trust;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog that surfaces when the conflict-detection engine identifies a potential location
 * contradiction between a new memory and an existing active memory for the same physical item.
 *
 * <h2>Purpose (Phase 5, Section 15.4, Plan §2.4)</h2>
 * <p>When the user saves a new PHYSICAL_ITEM or LOCATION memory and the {@code ConflictDetector}
 * finds that an existing active memory references the same item at a different location, this
 * dialog is presented. The user chooses one of two non-destructive resolutions:</p>
 * <ol>
 *   <li><b>Update &amp; Archive History</b> — the new memory SUPERSEDES the old one. The old
 *       memory is retained for history but de-prioritised in the default feed.</li>
 *   <li><b>Keep Both</b> — both memories remain independently active, linked only by a
 *       {@code RELATED_TO} edge if desired.</li>
 * </ol>
 *
 * <h2>Non-destructive invariant</h2>
 * <p>Regardless of the user's choice, the existing (conflicting) memory row is <em>never
 * deleted</em>. The dialog never auto-confirms; supersession only happens if the user
 * explicitly taps "Update &amp; Archive History" (NFR-01, NFR-03).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ConflictResolutionDialog dialog = ConflictResolutionDialog.newInstance(
 *     newMemoryId, conflictingMemoryId, "Spare Keys",
 *     "Kitchen Drawer", "Hallway Table");
 * dialog.setListener(myListener);
 * dialog.show(getSupportFragmentManager(), "conflict");
 * }</pre>
 */
public class ConflictResolutionDialog extends DialogFragment {

    private static final String ARG_NEW_MEMORY_ID        = "new_memory_id";
    private static final String ARG_CONFLICTING_MEMORY_ID = "conflicting_memory_id";
    private static final String ARG_ITEM_LABEL           = "item_label";
    private static final String ARG_OLD_LOCATION         = "old_location";
    private static final String ARG_NEW_LOCATION         = "new_location";

    /**
     * Callback interface for the hosting Activity or Fragment to receive the resolution decision.
     */
    public interface Listener {
        /**
         * Called when the user chooses to supersede the old memory with the new one.
         *
         * <p>The host should call {@code SupersessionManager.supersede(newMemoryId, conflictingMemoryId)}
         * to create the SUPERSEDES relation.</p>
         *
         * @param newMemoryId         UUID of the active (newer) memory
         * @param conflictingMemoryId UUID of the memory to be superseded
         */
        void onSupersede(String newMemoryId, String conflictingMemoryId);

        /**
         * Called when the user chooses to keep both memories active independently.
         *
         * @param newMemoryId         UUID of the new memory
         * @param conflictingMemoryId UUID of the existing memory
         */
        void onKeepBoth(String newMemoryId, String conflictingMemoryId);
    }

    private Listener listener;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code ConflictResolutionDialog} with full conflict context.
     *
     * @param newMemoryId         UUID of the newly saved memory (the "active" version)
     * @param conflictingMemoryId UUID of the existing conflicting memory
     * @param itemLabel           normalised item label shared by both memories (e.g. "Spare Keys")
     * @param oldLocation         short summary of the old assertion
     * @param newLocation         short summary of the new assertion
     * @return configured dialog instance ready to show
     */
    public static ConflictResolutionDialog newInstance(
            String newMemoryId,
            String conflictingMemoryId,
            String itemLabel,
            String oldLocation,
            String newLocation) {
        ConflictResolutionDialog dialog = new ConflictResolutionDialog();
        Bundle args = new Bundle();
        args.putString(ARG_NEW_MEMORY_ID, newMemoryId);
        args.putString(ARG_CONFLICTING_MEMORY_ID, conflictingMemoryId);
        args.putString(ARG_ITEM_LABEL, itemLabel != null ? itemLabel : "this item");
        args.putString(ARG_OLD_LOCATION, oldLocation != null ? oldLocation : "previous location");
        args.putString(ARG_NEW_LOCATION, newLocation != null ? newLocation : "new location");
        dialog.setArguments(args);
        return dialog;
    }

    // -------------------------------------------------------------------------
    // Listener injection
    // -------------------------------------------------------------------------

    /**
     * Registers the callback listener to receive the user's resolution choice.
     *
     * @param listener the callback receiver; must not be null
     */
    public void setListener(@NonNull Listener listener) {
        this.listener = listener;
    }

    // -------------------------------------------------------------------------
    // Dialog creation
    // -------------------------------------------------------------------------

    /**
     * Builds and returns the Material alert dialog with two resolution options.
     *
     * @param savedInstanceState not used
     * @return the constructed conflict resolution dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String newMemoryId         = args.getString(ARG_NEW_MEMORY_ID, "");
        String conflictingMemoryId = args.getString(ARG_CONFLICTING_MEMORY_ID, "");
        String itemLabel           = args.getString(ARG_ITEM_LABEL, "this item");
        String oldLocation         = args.getString(ARG_OLD_LOCATION, "previous location");
        String newLocation         = args.getString(ARG_NEW_LOCATION, "new location");

        String message = "You previously noted that "
                + itemLabel + " was in \"" + oldLocation + "\".\n\n"
                + "Are you updating its location to \"" + newLocation + "\"?";

        Context ctx = requireContext();
        return new MaterialAlertDialogBuilder(ctx)
                .setTitle("Location Update Detected")
                .setMessage(message)
                // Positive: supersede (recommended)
                .setPositiveButton("Update & Archive History", (dialog, which) -> {
                    if (listener != null) listener.onSupersede(newMemoryId, conflictingMemoryId);
                })
                // Negative: keep both independently
                .setNegativeButton("Keep Both", (dialog, which) -> {
                    if (listener != null) listener.onKeepBoth(newMemoryId, conflictingMemoryId);
                })
                .create();
    }
}
