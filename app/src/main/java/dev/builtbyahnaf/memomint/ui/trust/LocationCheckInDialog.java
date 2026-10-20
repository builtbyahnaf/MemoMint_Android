package dev.builtbyahnaf.memomint.ui.trust;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog prompting the user to confirm that a physical item is still in its recorded location,
 * or to indicate that the item has moved or cannot be found.
 *
 * <h2>Purpose (Phase 5, Section 15.5, FR-09)</h2>
 * <p>When a check-in is due for a PHYSICAL_ITEM or LOCATION memory, this dialog is shown.
 * The user has three choices:</p>
 * <ol>
 *   <li><b>Still Here</b> — updates {@code last_confirmed_at} and advances the next check-in.</li>
 *   <li><b>Moved / Updated</b> — opens the memory editor so the user can record a new location,
 *       which will trigger the supersession workflow.</li>
 *   <li><b>Not Sure</b> — marks the memory STALE without creating any new assertion.</li>
 * </ol>
 *
 * <h2>Trust invariant</h2>
 * <p>This dialog NEVER automatically updates {@code last_confirmed_at}. All confirmation
 * updates happen only via an explicit {@link Listener#onConfirmStillHere(String)} callback
 * triggered by the user tapping "Still Here" (Section 15, NFR-01).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * LocationCheckInDialog dialog = LocationCheckInDialog.newInstance(
 *     memoryId, "Earbuds", "Desk drawer");
 * dialog.setListener(myListener);
 * dialog.show(getSupportFragmentManager(), "check_in");
 * }</pre>
 */
public class LocationCheckInDialog extends DialogFragment {

    /** Fragment argument key for the memory's stable UUID. */
    private static final String ARG_MEMORY_ID = "memory_id";
    /** Fragment argument key for the item label to display. */
    private static final String ARG_ITEM_LABEL = "item_label";
    /** Fragment argument key for the recorded location to display. */
    private static final String ARG_LOCATION = "location";

    /**
     * Callback interface for the hosting Activity or Fragment to receive the user's response.
     */
    public interface Listener {
        /**
         * Called when the user confirms the item is still in the recorded location.
         *
         * @param memoryId the UUID of the confirmed memory
         */
        void onConfirmStillHere(String memoryId);

        /**
         * Called when the user indicates the item has been moved.
         * The host should open the editor to record a new location.
         *
         * @param memoryId the UUID of the memory to relocate
         */
        void onItemMoved(String memoryId);

        /**
         * Called when the user is unsure or cannot find the item.
         * The host should mark the memory STALE.
         *
         * @param memoryId the UUID of the memory to mark as unverifiable
         */
        void onNotSure(String memoryId);
    }

    private Listener listener;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code LocationCheckInDialog} with item and location details.
     *
     * @param memoryId  the UUID of the memory being checked in
     * @param itemLabel the name of the physical item (e.g. "Earbuds")
     * @param location  the recorded location (e.g. "desk drawer")
     * @return a configured dialog instance ready to show
     */
    public static LocationCheckInDialog newInstance(
            String memoryId, String itemLabel, String location) {
        LocationCheckInDialog dialog = new LocationCheckInDialog();
        Bundle args = new Bundle();
        args.putString(ARG_MEMORY_ID, memoryId);
        args.putString(ARG_ITEM_LABEL, itemLabel != null ? itemLabel : "this item");
        args.putString(ARG_LOCATION, location != null ? location : "its recorded location");
        dialog.setArguments(args);
        return dialog;
    }

    // -------------------------------------------------------------------------
    // Listener injection
    // -------------------------------------------------------------------------

    /**
     * Registers the callback listener that receives the user's check-in response.
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
     * Builds and returns the Material alert dialog with three action buttons.
     *
     * @param savedInstanceState not used
     * @return the constructed dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String memoryId   = args.getString(ARG_MEMORY_ID, "");
        String itemLabel  = args.getString(ARG_ITEM_LABEL, "this item");
        String location   = args.getString(ARG_LOCATION, "its recorded location");

        String message = "Is your " + itemLabel + " still in " + location + "?";

        Context ctx = requireContext();
        return new MaterialAlertDialogBuilder(ctx)
                .setTitle("Location Check-In")
                .setMessage(message)
                // Positive: confirm in place
                .setPositiveButton("Still Here", (dialog, which) -> {
                    if (listener != null) listener.onConfirmStillHere(memoryId);
                })
                // Neutral: item was moved — open editor
                .setNeutralButton("Moved", (dialog, which) -> {
                    if (listener != null) listener.onItemMoved(memoryId);
                })
                // Negative: user is unsure
                .setNegativeButton("Not Sure", (dialog, which) -> {
                    if (listener != null) listener.onNotSure(memoryId);
                })
                .create();
    }
}
