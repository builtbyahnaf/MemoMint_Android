package dev.builtbyahnaf.memomint.ui.memory;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.builtbyahnaf.memomint.R;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.FreshnessStatus;
import dev.builtbyahnaf.memomint.domain.trust.FreshnessEvaluator;
import dev.builtbyahnaf.memomint.domain.model.FreshnessReport;

/**
 * RecyclerView adapter responsible for presenting {@link Memory} cards in the primary memory feed.
 *
 * <h2>Purpose & Architectural Role</h2>
 * <p>Belongs to Layer A (Presentation). Binds memory domain models to {@link RecyclerView} card views,
 * presenting key visual indicators (type badge, creation timestamp, freshness/confirmation status)
 * and exposing user interaction events (edit, confirm, archive, restore, delete) via an explicit listener.</p>
 *
 * <h2>Visual Differentiation by MemoryType</h2>
 * <p>Each memory type is visually distinguished with dedicated badge backgrounds and text colors:</p>
 * <ul>
 *   <li><b>PHYSICAL_ITEM:</b> Sky blue badge (frequently associated with locations and freshness).</li>
 *   <li><b>NOTE:</b> Amber badge (general observations and thoughts).</li>
 *   <li><b>LOCATION:</b> Rose badge (spatial contexts).</li>
 *   <li><b>FILE_REFERENCE:</b> Indigo badge (attached local files).</li>
 *   <li><b>DOCUMENT_CONTEXT:</b> Teal badge (extracted document snippets).</li>
 *   <li><b>REMINDER:</b> Orange badge (time-based intentions).</li>
 * </ul>
 *
 * <h2>Algorithms & Workflow: List Updates</h2>
 * <p>Uses {@link DiffUtil} to compute minimal item dispatch events between the previous list and
 * newly loaded lists. This prevents full list redraws, keeps scroll positions stable, and provides
 * smooth native animations when memories are created, updated, or removed.</p>
 */
public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder> {

    /**
     * Listener interface delivering item-level user actions to the hosting Activity or Fragment.
     */
    public interface OnMemoryActionListener {
        /** Invoked when the user taps the Edit action on a memory card. */
        void onEdit(Memory memory);

        /** Invoked when the user confirms that the recorded facts/location are still accurate. */
        void onConfirm(Memory memory);

        /** Invoked when the user toggles the Archive/Restore status of a memory. */
        void onArchiveRestore(Memory memory);

        /** Invoked when the user requests deletion of a memory. */
        void onDelete(Memory memory);
    }

    private final List<Memory> items = new ArrayList<>();
    private final OnMemoryActionListener actionListener;

    /**
     * Constructs the adapter with a mandatory action listener.
     *
     * @param actionListener callback receiver for user interactions; must not be null
     */
    public MemoryAdapter(@NonNull OnMemoryActionListener actionListener) {
        this.actionListener = actionListener;
    }

    /**
     * Replaces the adapter's data set using {@link DiffUtil} calculation for optimal UI performance.
     *
     * <h2>Algorithm & Complexity</h2>
     * <p>Eugene Myers' difference algorithm is used via {@link DiffUtil#calculateDiff(DiffUtil.Callback)}.
     * This compares {@code oldList} against {@code targetList} in $O(N + D^2)$ time, where $N$ is
     * the sum of list lengths and $D$ is the number of edit script differences. It computes the
     * minimal set of structural updates (insertions, deletions, moves, and mutations):</p>
     * <ul>
     *   <li>{@link DiffUtil.Callback#areItemsTheSame(int, int)}: Tests stable item identity using
     *       {@link Memory#getId()}. If IDs match, the item is considered the same logical memory.</li>
     *   <li>{@link DiffUtil.Callback#areContentsTheSame(int, int)}: Tests whether visual content
     *       changed. Specifically checks {@code updatedAt}, {@code status}, {@code lastConfirmedAt},
     *       and {@code originalText}. If all 4 match, no item rebind is dispatched.</li>
     * </ul>
     *
     * @param newItems the fresh list of memories to display; passing null clears the list
     */
    public void submitList(List<Memory> newItems) {
        List<Memory> oldList = new ArrayList<>(this.items);
        List<Memory> targetList = (newItems != null) ? newItems : new ArrayList<>();

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return targetList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).getId().equals(targetList.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Memory oldItem = oldList.get(oldItemPosition);
                Memory newItem = targetList.get(newItemPosition);
                return oldItem.getUpdatedAt() == newItem.getUpdatedAt()
                        && oldItem.getStatus() == newItem.getStatus()
                        && (oldItem.getLastConfirmedAt() == null ? newItem.getLastConfirmedAt() == null : oldItem.getLastConfirmedAt().equals(newItem.getLastConfirmedAt()))
                        && oldItem.getOriginalText().equals(newItem.getOriginalText());
            }
        });

        this.items.clear();
        this.items.addAll(targetList);
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Inflates the card view layout for an individual memory card.
     *
     * @param parent   the ViewGroup into which the new View will be added
     * @param viewType the view type of the new View (unused here as cards share a single layout)
     * @return a new {@link MemoryViewHolder} holding the inflated layout
     */
    @NonNull
    @Override
    public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new MemoryViewHolder(view);
    }

    /**
     * Binds domain data from the item at {@code position} to the view holder.
     *
     * @param holder   the ViewHolder to update
     * @param position the zero-based index of the item within {@link #items}
     */
    @Override
    public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /**
     * Returns the total count of memories currently held in the adapter.
     *
     * @return item count
     */
    @Override
    public int getItemCount() {
        return items.size();
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    /**
     * ViewHolder caching UI subview references for a single memory card to eliminate
     * repeated {@code findViewById} lookups during fast scrolling.
     */
    class MemoryViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTypeBadge;
        private final TextView tvDate;
        private final TextView tvTitle;
        private final TextView tvOriginalText;
        private final TextView tvFreshnessChip;
        private final View layoutConfirmation;
        private final TextView tvConfirmationStatus;
        private final MaterialButton btnConfirm;
        private final MaterialButton btnEdit;
        private final MaterialButton btnArchiveRestore;
        private final MaterialButton btnDelete;

        MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTypeBadge = itemView.findViewById(R.id.tvTypeBadge);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvOriginalText = itemView.findViewById(R.id.tvOriginalText);
            tvFreshnessChip = itemView.findViewById(R.id.tvFreshnessChip);
            layoutConfirmation = itemView.findViewById(R.id.layoutConfirmation);
            tvConfirmationStatus = itemView.findViewById(R.id.tvConfirmationStatus);
            btnConfirm = itemView.findViewById(R.id.btnConfirm);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnArchiveRestore = itemView.findViewById(R.id.btnArchiveRestore);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        /**
         * Binds a {@link Memory} model instance to the view holder's UI elements.
         *
         * <h2>Workflow:</h2>
         * <ol>
         *   <li><b>Type Badge:</b> Applies Day/Night adaptive color tokens according to memory type.</li>
         *   <li><b>Timestamp:</b> Converts UTC epoch creation timestamp to human-friendly relative span.</li>
         *   <li><b>Title:</b> Toggles visibility based on whether an optional title was provided.</li>
         *   <li><b>Original Text:</b> Binds unedited user text (the primary truth of the memory).</li>
         *   <li><b>Freshness:</b> If PHYSICAL_ITEM or LOCATION, reveals confirmation status and re-confirm button.</li>
         *   <li><b>Action Buttons:</b> Configures archive vs. restore label and binds click listeners.</li>
         * </ol>
         *
         * @param memory the memory data to display
         */
        void bind(Memory memory) {
            Context context = itemView.getContext();

            // 1. Memory Type Badge
            applyTypeBadgeStyle(context, memory.getType());

            // 2. Creation Date formatting (relative time, e.g. "5m ago", "Yesterday")
            String dateStr = DateUtils.getRelativeTimeSpanString(
                    memory.getCreatedAt(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString();
            tvDate.setText(dateStr);

            // 3. Title (optional display)
            if (memory.getTitle() != null && !memory.getTitle().trim().isEmpty()) {
                tvTitle.setVisibility(View.VISIBLE);
                tvTitle.setText(memory.getTitle());
            } else {
                tvTitle.setVisibility(View.GONE);
            }

            // 4. Original Text (primary content)
            tvOriginalText.setText(memory.getOriginalText());

            // 5. Freshness / Confirmation Section
            bindConfirmationSection(context, memory);

            // 6. Archive vs Restore label
            if (memory.getStatus() == MemoryStatus.ARCHIVED) {
                btnArchiveRestore.setText(R.string.action_restore);
            } else {
                btnArchiveRestore.setText(R.string.action_archive);
            }

            // 7. Event listeners
            btnEdit.setOnClickListener(v -> actionListener.onEdit(memory));
            btnConfirm.setOnClickListener(v -> actionListener.onConfirm(memory));
            btnArchiveRestore.setOnClickListener(v -> actionListener.onArchiveRestore(memory));
            btnDelete.setOnClickListener(v -> actionListener.onDelete(memory));
        }

        /**
         * Applies type-specific background badges and text labels.
         * The colors automatically resolve between Light and Dark mode using DayNight resource qualifiers.
         */
        private void applyTypeBadgeStyle(Context context, MemoryType type) {
            if (type == null) type = MemoryType.NOTE;

            switch (type) {
                case PHYSICAL_ITEM:
                    tvTypeBadge.setText("PHYSICAL ITEM");
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_physical_item_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_physical_item_text));
                    break;
                case LOCATION:
                    tvTypeBadge.setText("LOCATION");
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_location_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_location_text));
                    break;
                case FILE_REFERENCE:
                    tvTypeBadge.setText("FILE REF");
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_file_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_file_text));
                    break;
                case DOCUMENT_CONTEXT:
                    tvTypeBadge.setText("DOCUMENT");
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_document_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_document_text));
                    break;
                case REMINDER:
                    tvTypeBadge.setText("REMINDER");
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_reminder_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_reminder_text));
                    break;
                case NOTE:
                default:
                    tvTypeBadge.setText("NOTE");
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_note_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_note_text));
                    break;
            }
        }

        /**
         * Renders freshness indicator for items and locations.
         *
         * <p>Phase 5 enhancement: evaluates freshness using {@link FreshnessEvaluator} (without
         * a policy — the adapter uses a 30-day default interval so it can be purely synchronous
         * in the bind pass). A coloured chip above the confirm section gives an at-a-glance
         * status to the user.</p>
         */
        private void bindConfirmationSection(Context context, Memory memory) {
            // Confirmation applies primarily to PHYSICAL_ITEM and LOCATION
            boolean isPhysical = (memory.getType() == MemoryType.PHYSICAL_ITEM
                    || memory.getType() == MemoryType.LOCATION);

            // Phase 5: Freshness chip (shown for all physical/location memories)
            if (tvFreshnessChip != null) {
                if (isPhysical) {
                    FreshnessEvaluator evaluator = new FreshnessEvaluator();
                    FreshnessReport report = evaluator.evaluate(memory, null);
                    tvFreshnessChip.setVisibility(View.VISIBLE);
                    tvFreshnessChip.setText(report.getHumanReadableStatus());
                    // Colour-code the chip by status
                    if (report.getStatus() == FreshnessStatus.CONFIRMED_FRESH) {
                        tvFreshnessChip.setBackgroundColor(
                                ContextCompat.getColor(context, R.color.badge_physical_item_bg));
                        tvFreshnessChip.setTextColor(
                                ContextCompat.getColor(context, R.color.badge_physical_item_text));
                    } else if (report.getStatus() == FreshnessStatus.AGING) {
                        tvFreshnessChip.setBackgroundColor(
                                ContextCompat.getColor(context, R.color.badge_reminder_bg));
                        tvFreshnessChip.setTextColor(
                                ContextCompat.getColor(context, R.color.badge_reminder_text));
                    } else if (report.getStatus() == FreshnessStatus.STALE) {
                        tvFreshnessChip.setBackgroundColor(
                                ContextCompat.getColor(context, R.color.badge_document_bg));
                        tvFreshnessChip.setTextColor(
                                ContextCompat.getColor(context, R.color.badge_document_text));
                    } else {
                        // UNCONFIRMED / default
                        tvFreshnessChip.setBackgroundColor(
                                ContextCompat.getColor(context, R.color.badge_note_bg));
                        tvFreshnessChip.setTextColor(
                                ContextCompat.getColor(context, R.color.badge_note_text));
                    }
                } else {
                    tvFreshnessChip.setVisibility(View.GONE);
                }
            }

            if (isPhysical) {
                layoutConfirmation.setVisibility(View.VISIBLE);
                if (memory.getLastConfirmedAt() != null) {
                    String relativeConfirmed = DateUtils.getRelativeTimeSpanString(
                            memory.getLastConfirmedAt(),
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                    ).toString();
                    tvConfirmationStatus.setText(context.getString(R.string.status_confirmed_format, relativeConfirmed));
                    tvConfirmationStatus.setTextColor(ContextCompat.getColor(context, R.color.mint_primary));
                    btnConfirm.setText("Re-confirm");
                } else {
                    tvConfirmationStatus.setText(R.string.status_unconfirmed);
                    tvConfirmationStatus.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant_light));
                    btnConfirm.setText(R.string.action_confirm);
                }
            } else {
                layoutConfirmation.setVisibility(View.GONE);
            }
        }
    }
}
