package dev.builtbyahnaf.memomint.ui.search;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
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
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.retrieval.SearchResult;

/**
 * RecyclerView adapter rendering ranked, explainable search results.
 *
 * <h2>Purpose & Architectural Invariants (Section 12.5 & Phase 2 Plan §3.6)</h2>
 * <p>Belongs to Layer A (Presentation). Binds {@link SearchResult} domain objects to cards,
 * presenting:</p>
 * <ul>
 *   <li><b>Type Badge:</b> Visual differentiation based on {@link MemoryType}.</li>
 *   <li><b>Score Badge:</b> Normalized percentage match score ($[0.0, 1.0]$).</li>
 *   <li><b>Term Highlighting:</b> Bold spans highlighting matched search keywords.</li>
 *   <li><b>Transparent Explanations:</b> Explanatory provenance bullet points detailing exact matches,
 *       synonym bridges, and typo corrections.</li>
 *   <li><b>Freshness Separation:</b> Independent confirmation status badges.</li>
 * </ul>
 */
public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.SearchResultViewHolder> {

    public interface OnSearchResultClickListener {
        void onEdit(Memory memory);
        void onConfirm(Memory memory);
    }

    private final List<SearchResult> items = new ArrayList<>();
    private final OnSearchResultClickListener clickListener;

    public SearchAdapter(@NonNull OnSearchResultClickListener clickListener) {
        this.clickListener = clickListener;
    }

    /**
     * Updates the displayed search results using {@link DiffUtil}.
     */
    public void submitList(List<SearchResult> newItems) {
        List<SearchResult> oldList = new ArrayList<>(this.items);
        List<SearchResult> targetList = (newItems != null) ? newItems : new ArrayList<>();

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
                return oldList.get(oldItemPosition).getMemory().getId()
                        .equals(targetList.get(newItemPosition).getMemory().getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SearchResult oldRes = oldList.get(oldItemPosition);
                SearchResult newRes = targetList.get(newItemPosition);
                return Double.compare(oldRes.getFinalScore(), newRes.getFinalScore()) == 0
                        && oldRes.getExplanations().equals(newRes.getExplanations())
                        && oldRes.getMemory().getUpdatedAt() == newRes.getMemory().getUpdatedAt();
            }
        });

        this.items.clear();
        this.items.addAll(targetList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new SearchResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        holder.bind(items.get(position), clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SearchResultViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTypeBadge;
        private final TextView tvScoreBadge;
        private final TextView tvDate;
        private final TextView tvTitle;
        private final TextView tvOriginalText;
        private final View layoutExplanations;
        private final TextView tvExplanationText;
        private final View layoutConfirmation;
        private final TextView tvConfirmationStatus;
        private final MaterialButton btnConfirm;
        private final MaterialButton btnEdit;

        public SearchResultViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTypeBadge         = itemView.findViewById(R.id.tvTypeBadge);
            tvScoreBadge        = itemView.findViewById(R.id.tvScoreBadge);
            tvDate              = itemView.findViewById(R.id.tvDate);
            tvTitle             = itemView.findViewById(R.id.tvTitle);
            tvOriginalText      = itemView.findViewById(R.id.tvOriginalText);
            layoutExplanations  = itemView.findViewById(R.id.layoutExplanations);
            tvExplanationText   = itemView.findViewById(R.id.tvExplanationText);
            layoutConfirmation  = itemView.findViewById(R.id.layoutConfirmation);
            tvConfirmationStatus = itemView.findViewById(R.id.tvConfirmationStatus);
            btnConfirm          = itemView.findViewById(R.id.btnConfirm);
            btnEdit             = itemView.findViewById(R.id.btnEdit);
        }

        public void bind(SearchResult result, OnSearchResultClickListener listener) {
            Context context = itemView.getContext();
            Memory memory = result.getMemory();

            // 1. Type Badge
            bindTypeBadge(context, memory.getType());

            // 2. Score Badge
            int percentage = (int) Math.round(result.getFinalScore() * 100);
            tvScoreBadge.setText(percentage + "% Match");

            // 3. Creation Date
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            tvDate.setText(sdf.format(new Date(memory.getCreatedAt())));

            // 4. Title (highlighted)
            if (memory.getTitle() != null && !memory.getTitle().trim().isEmpty()) {
                tvTitle.setVisibility(View.VISIBLE);
                tvTitle.setText(highlightTerms(memory.getTitle(), result.getMatchedTerms()));
            } else {
                tvTitle.setVisibility(View.GONE);
            }

            // 5. Original Text (highlighted)
            tvOriginalText.setText(highlightTerms(memory.getOriginalText(), result.getMatchedTerms()));

            // 6. Explanations (Provenance Section)
            List<String> explanations = result.getExplanations();
            if (!explanations.isEmpty()) {
                layoutExplanations.setVisibility(View.VISIBLE);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < explanations.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("• ").append(explanations.get(i));
                }
                tvExplanationText.setText(sb.toString());
            } else {
                layoutExplanations.setVisibility(View.GONE);
            }

            // 7. Confirmation / Freshness Section
            bindConfirmationSection(context, memory, result.getFreshnessStatus(), listener);

            // 8. Actions
            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(memory);
            });
        }

        private void bindTypeBadge(Context context, MemoryType type) {
            tvTypeBadge.setText(type.name().replace('_', ' '));
            switch (type) {
                case PHYSICAL_ITEM:
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_physical_item_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_physical_item_text));
                    break;
                case LOCATION:
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_location_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_location_text));
                    break;
                case FILE_REFERENCE:
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_file_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_file_text));
                    break;
                case DOCUMENT_CONTEXT:
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_document_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_document_text));
                    break;
                case REMINDER:
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_reminder_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_reminder_text));
                    break;
                case NOTE:
                default:
                    tvTypeBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.badge_note_bg));
                    tvTypeBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_note_text));
                    break;
            }
        }

        private void bindConfirmationSection(
                Context context,
                Memory memory,
                SearchResult.FreshnessStatus freshness,
                OnSearchResultClickListener listener
        ) {
            boolean isPhysical = (memory.getType() == MemoryType.PHYSICAL_ITEM || memory.getType() == MemoryType.LOCATION);
            if (!isPhysical) {
                layoutConfirmation.setVisibility(View.GONE);
                return;
            }

            layoutConfirmation.setVisibility(View.VISIBLE);
            if (freshness == SearchResult.FreshnessStatus.CONFIRMED_RECENT && memory.getLastConfirmedAt() != null) {
                String relative = DateUtils.getRelativeTimeSpanString(
                        memory.getLastConfirmedAt(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString();
                tvConfirmationStatus.setText(context.getString(R.string.status_confirmed_format, relative));
                tvConfirmationStatus.setTextColor(ContextCompat.getColor(context, R.color.mint_primary));
                btnConfirm.setText("Re-confirm");
            } else if (freshness == SearchResult.FreshnessStatus.CONFIRMED_STALE && memory.getLastConfirmedAt() != null) {
                String relative = DateUtils.getRelativeTimeSpanString(
                        memory.getLastConfirmedAt(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString();
                tvConfirmationStatus.setText("⚠ Stale (" + relative + ")");
                tvConfirmationStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_error));
                btnConfirm.setText("Re-confirm");
            } else {
                tvConfirmationStatus.setText(R.string.status_unconfirmed);
                tvConfirmationStatus.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant_light));
                btnConfirm.setText(R.string.action_confirm);
            }

            btnConfirm.setOnClickListener(v -> {
                if (listener != null) listener.onConfirm(memory);
            });
        }

        private CharSequence highlightTerms(String text, List<String> terms) {
            if (text == null || terms == null || terms.isEmpty()) {
                return text;
            }

            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            String lowerText = text.toLowerCase(Locale.ROOT);

            for (String term : terms) {
                if (term == null || term.trim().isEmpty()) continue;
                String lowerTerm = term.toLowerCase(Locale.ROOT).trim();
                int index = 0;
                while ((index = lowerText.indexOf(lowerTerm, index)) != -1) {
                    int end = index + lowerTerm.length();
                    builder.setSpan(new StyleSpan(Typeface.BOLD), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    index = end;
                }
            }

            return builder;
        }
    }
}
