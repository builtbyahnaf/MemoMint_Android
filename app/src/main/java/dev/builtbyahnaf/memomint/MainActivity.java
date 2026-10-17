package dev.builtbyahnaf.memomint;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import java.util.List;

import dev.builtbyahnaf.memomint.application.MemoryService;
import dev.builtbyahnaf.memomint.application.SearchService;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.SearchRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;
import dev.builtbyahnaf.memomint.domain.retrieval.SearchResult;
import dev.builtbyahnaf.memomint.ui.memory.MemoryAdapter;
import dev.builtbyahnaf.memomint.ui.search.SearchAdapter;

/**
 * Primary user interface for Project MemoMint (Phase 1 & Phase 2).
 *
 * <h2>Purpose & Architectural Scope</h2>
 * <p>Serves as the main activity satisfying Phase 1 and Phase 2 requirements:
 * offline memory capture, editing, lifecycle management, and natural language retrieval
 * with transparent explanations.</p>
 */
public class MainActivity extends AppCompatActivity implements MemoryAdapter.OnMemoryActionListener {

    private MemoryService memoryService;
    private MemoryAdapter memoryAdapter;
    private SearchService searchService;
    private SearchAdapter searchAdapter;

    private RecyclerView recyclerView;
    private View layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private ProgressBar progressBar;
    private ChipGroup chipGroupFilter;
    private ExtendedFloatingActionButton fabAddMemory;
    private TextInputEditText etSearchQuery;

    private MemoryStatus currentFilterStatus = MemoryStatus.ACTIVE;
    private boolean isSearchActive = false;
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize Services & Persistence Layer
        MemoMintDatabase database = MemoMintDatabase.getInstance(this);
        MemoryRepositoryImpl repository = new MemoryRepositoryImpl(database);
        memoryService = new MemoryService(repository);
        SearchRepositoryImpl searchRepository = new SearchRepositoryImpl(database);
        searchService = new SearchService(searchRepository);

        // Bind Views
        recyclerView = findViewById(R.id.recyclerViewMemories);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        progressBar = findViewById(R.id.progressBar);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        fabAddMemory = findViewById(R.id.fabAddMemory);
        etSearchQuery = findViewById(R.id.etSearchQuery);

        // Configure edge-to-edge insets:
        // 1. Top status bar insets are applied to the AppBar header so the background extends behind the status bar.
        // 2. Bottom navigation bar insets are applied to the RecyclerView bottom padding and FAB margin.
        View headerContainer = findViewById(R.id.headerContainer);
        int initialHeaderPaddingTop = headerContainer.getPaddingTop();
        int initialFabMarginBottom = ((ViewGroup.MarginLayoutParams) fabAddMemory.getLayoutParams()).bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Extend AppBar behind the status bar
            headerContainer.setPadding(
                    headerContainer.getPaddingLeft(),
                    initialHeaderPaddingTop + systemBars.top,
                    headerContainer.getPaddingRight(),
                    headerContainer.getPaddingBottom()
            );

            // Elevate FAB above system navigation bar
            ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fabAddMemory.getLayoutParams();
            fabParams.bottomMargin = initialFabMarginBottom + systemBars.bottom;
            fabAddMemory.setLayoutParams(fabParams);

            // Ensure RecyclerView items don't hide behind system navigation bar
            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    systemBars.bottom + 96
            );

            return insets;
        });

        // Configure Adapters
        memoryAdapter = new MemoryAdapter(this);
        searchAdapter = new SearchAdapter(new SearchAdapter.OnSearchResultClickListener() {
            @Override
            public void onEdit(Memory memory) {
                showMemoryEditorDialog(memory);
            }

            @Override
            public void onConfirm(Memory memory) {
                MainActivity.this.onConfirm(memory);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(memoryAdapter);

        // Configure Search Bar TextWatcher
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = (s != null) ? s.toString().trim() : "";
                currentSearchQuery = query;
                if (!query.isEmpty()) {
                    isSearchActive = true;
                    recyclerView.setAdapter(searchAdapter);
                    chipGroupFilter.setVisibility(View.GONE);
                    performSearch(query);
                } else {
                    isSearchActive = false;
                    recyclerView.setAdapter(memoryAdapter);
                    chipGroupFilter.setVisibility(View.VISIBLE);
                    loadMemories();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Configure Filter Chips:
        // Toggles currentFilterStatus between ACTIVE and ARCHIVED.
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipArchived)) {
                currentFilterStatus = MemoryStatus.ARCHIVED;
            } else {
                currentFilterStatus = MemoryStatus.ACTIVE;
            }
            loadMemories();
        });

        // Configure FAB for Memory Creation:
        fabAddMemory.setOnClickListener(v -> showMemoryEditorDialog(/* memoryToEdit= */ null));

        // Initial asynchronous query to populate the memory feed upon activity launch.
        loadMemories();
    }

    /**
     * Queries memories matching {@link #currentFilterStatus} asynchronously off the main thread.
     *
     * <h2>Algorithm & Workflow</h2>
     * <ol>
     *   <li>Display {@link ProgressBar} and initiate asynchronous load via {@link MemoryService}.</li>
     *   <li>On callback success (executed on Android main looper):
     *     <ul>
     *       <li>Hide {@link ProgressBar}.</li>
     *       <li>Pass the loaded list to {@link MemoryAdapter#submitList(List)}, which computes
     *           minimal visual diffs using {@link androidx.recyclerview.widget.DiffUtil}.</li>
     *       <li>If the resulting list is empty, toggle visibility to display {@code layoutEmptyState}
     *           and hide the {@link RecyclerView}.</li>
     *       <li>If items exist, reveal {@link RecyclerView} and conceal empty placeholder.</li>
     *     </ul>
     *   </li>
     *   <li>On error: Hide {@link ProgressBar} and present a user-facing error toast.</li>
     * </ol>
     */
    private void loadMemories() {
        if (isSearchActive) {
            performSearch(currentSearchQuery);
            return;
        }
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(R.string.empty_memories_title);
            tvEmptySubtitle.setText(R.string.empty_memories_subtitle);
        }
        progressBar.setVisibility(View.VISIBLE);
        memoryService.loadMemoriesByStatus(currentFilterStatus, new MemoryService.Callback<List<Memory>>() {
            @Override
            public void onSuccess(List<Memory> memories) {
                progressBar.setVisibility(View.GONE);
                memoryAdapter.submitList(memories);
                if (memories == null || memories.isEmpty()) {
                    layoutEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    layoutEmptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Error loading memories: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Executes an asynchronous search query through {@link SearchService} and binds the
     * resulting ranked and explained matches to {@link SearchAdapter}.
     *
     * @param query raw search text
     */
    private void performSearch(String query) {
        progressBar.setVisibility(View.VISIBLE);
        searchService.search(query, new SearchService.Callback<List<SearchResult>>() {
            @Override
            public void onSuccess(List<SearchResult> results) {
                if (!isSearchActive || !query.equals(currentSearchQuery)) {
                    return; // Discard stale asynchronous results
                }
                progressBar.setVisibility(View.GONE);
                searchAdapter.submitList(results);
                if (results == null || results.isEmpty()) {
                    if (tvEmptyTitle != null) {
                        tvEmptyTitle.setText(R.string.search_no_results);
                        tvEmptySubtitle.setText(R.string.search_no_results_subtitle);
                    }
                    layoutEmptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    layoutEmptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Search error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =========================================================================
    // Memory Creation and Editing Dialogs
    // =========================================================================

    /**
     * Displays a Material dialog for capturing a new memory or editing an existing one.
     *
     * <h2>Purpose & Workflow</h2>
     * <p>Provides a low-friction entry point for users to capture unstructured text without
     * mandatory categorization, while supporting optional titles and type overrides.</p>
     *
     * <h2>Dialog Validation Lifecycle & Edge Case Handling</h2>
     * <p>By default, Android's {@link AlertDialog} auto-dismisses upon clicking any positive
     * or negative button regardless of input validity. To prevent accidental dialog dismissal
     * when form validation fails (e.g. empty original text):</p>
     * <ul>
     *   <li>Pass {@code null} as the positive button listener in {@link MaterialAlertDialogBuilder}.</li>
     *   <li>Attach a custom {@link View.OnClickListener} inside {@link AlertDialog#setOnShowListener}.</li>
     *   <li>Validate {@code etOriginalText}: if blank, set an error on {@code etOriginalText} and
     *       return immediately without dismissing the dialog or making database calls.</li>
     *   <li>Only call {@link DialogInterface#dismiss()} after {@link MemoryService} reports
     *       successful persistence.</li>
     * </ul>
     *
     * @param memoryToEdit the existing Memory to edit, or null to record a new one
     */
    private void showMemoryEditorDialog(Memory memoryToEdit) {
        boolean isEdit = (memoryToEdit != null);
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_memory_editor, null);

        Spinner spinnerType = dialogView.findViewById(R.id.spinnerMemoryType);
        TextInputEditText etTitle = dialogView.findViewById(R.id.etTitle);
        TextInputEditText etOriginalText = dialogView.findViewById(R.id.etOriginalText);

        // Populate MemoryType Spinner with user-friendly formatted labels (e.g. PHYSICAL_ITEM -> PHYSICAL ITEM)
        MemoryType[] types = MemoryType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].name().replace('_', ' ');
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, typeNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(spinnerAdapter);

        // If editing, pre-fill form fields with current values from the database record
        if (isEdit) {
            etTitle.setText(memoryToEdit.getTitle());
            etOriginalText.setText(memoryToEdit.getOriginalText());
            for (int i = 0; i < types.length; i++) {
                if (types[i] == memoryToEdit.getType()) {
                    spinnerType.setSelection(i);
                    break;
                }
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? R.string.dialog_edit_title : R.string.dialog_create_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, null) // Custom listener attached in setOnShowListener below
                .setNegativeButton(R.string.action_cancel, (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String originalText = etOriginalText.getText() != null ? etOriginalText.getText().toString().trim() : "";
                String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
                int selectedTypePos = spinnerType.getSelectedItemPosition();
                MemoryType selectedType = (selectedTypePos >= 0 && selectedTypePos < types.length) ? types[selectedTypePos] : MemoryType.NOTE;

                // Validation: original text must not be empty (FR-01, FR-03)
                if (originalText.isEmpty()) {
                    etOriginalText.setError(getString(R.string.error_empty_text));
                    return;
                }

                if (isEdit) {
                    // Update existing memory record; updates updatedAt while preserving createdAt and provenance
                    memoryService.updateMemory(
                            memoryToEdit.getId(),
                            originalText,
                            title,
                            selectedType,
                            new MemoryService.Callback<Memory>() {
                                @Override
                                public void onSuccess(Memory result) {
                                    Toast.makeText(MainActivity.this, R.string.msg_memory_saved, Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    loadMemories();
                                }

                                @Override
                                public void onError(Throwable t) {
                                    Toast.makeText(MainActivity.this, "Save failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                } else {
                    // Create brand new memory record with status ACTIVE and generated UUID
                    memoryService.createMemory(
                            originalText,
                            title,
                            selectedType,
                            SourceType.TYPED,
                            /* sourceId= */ null,
                            new MemoryService.Callback<Memory>() {
                                @Override
                                public void onSuccess(Memory result) {
                                    Toast.makeText(MainActivity.this, R.string.msg_memory_saved, Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    loadMemories();
                                }

                                @Override
                                public void onError(Throwable t) {
                                    Toast.makeText(MainActivity.this, "Save failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                    );
                }
            });
        });

        dialog.show();
    }

    // =========================================================================
    // MemoryAdapter Callbacks (Item Actions)
    // =========================================================================

    /**
     * Handles the user tapping "Edit" on a memory card.
     * Launches the pre-populated editor dialog for the selected memory.
     *
     * @param memory the memory entity to modify
     */
    @Override
    public void onEdit(Memory memory) {
        showMemoryEditorDialog(memory);
    }

    /**
     * Handles user confirmation of physical item location or factual freshness (FR-09, Section 15.2).
     *
     * <p><b>Trust Model Principle:</b> The application cannot physically sense physical belongings
     * without sensors. Confirmation must always come from explicit user assertion, updating
     * {@code last_confirmed_at} with the current UTC epoch timestamp.</p>
     *
     * @param memory the memory entity whose freshness is confirmed
     */
    @Override
    public void onConfirm(Memory memory) {
        memoryService.confirmMemory(memory.getId(), new MemoryService.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Toast.makeText(MainActivity.this, R.string.msg_memory_confirmed, Toast.LENGTH_SHORT).show();
                loadMemories();
            }

            @Override
            public void onError(Throwable t) {
                Toast.makeText(MainActivity.this, "Confirmation failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Toggles between Active and Archived states for the given memory.
     *
     * <p><b>Lifecycle Invariant:</b> Archiving removes memories from the active feed without
     * deleting their data or cascade dependencies. Users can seamlessly restore them anytime.</p>
     *
     * @param memory the memory entity to archive or restore
     */
    @Override
    public void onArchiveRestore(Memory memory) {
        if (memory.getStatus() == MemoryStatus.ARCHIVED) {
            memoryService.restoreMemory(memory.getId(), new MemoryService.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    Toast.makeText(MainActivity.this, R.string.msg_memory_restored, Toast.LENGTH_SHORT).show();
                    loadMemories();
                }

                @Override
                public void onError(Throwable t) {
                    Toast.makeText(MainActivity.this, "Failed to restore: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            memoryService.archiveMemory(memory.getId(), new MemoryService.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    Toast.makeText(MainActivity.this, R.string.msg_memory_archived, Toast.LENGTH_SHORT).show();
                    loadMemories();
                }

                @Override
                public void onError(Throwable t) {
                    Toast.makeText(MainActivity.this, "Failed to archive: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Initiates permanent memory deletion with explicit user confirmation (FR-14).
     *
     * <p><b>Cascade Cleanup Warning:</b> Hard-deleting a memory row automatically triggers
     * SQLite's {@code ON DELETE CASCADE} constraints, permanently erasing derived context records,
     * reminders, and confirmation policies. A confirmation dialog prevents accidental data loss.</p>
     *
     * @param memory the memory entity to be permanently deleted
     */
    @Override
    public void onDelete(Memory memory) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(R.string.dialog_delete_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    memoryService.hardDeleteMemory(memory.getId(), new MemoryService.Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            Toast.makeText(MainActivity.this, R.string.msg_memory_deleted, Toast.LENGTH_SHORT).show();
                            loadMemories();
                        }

                        @Override
                        public void onError(Throwable t) {
                            Toast.makeText(MainActivity.this, "Delete failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}