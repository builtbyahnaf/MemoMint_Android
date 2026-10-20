package dev.builtbyahnaf.memomint;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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

import dev.builtbyahnaf.memomint.application.ConfirmationScheduler;
import dev.builtbyahnaf.memomint.application.MemoryService;
import dev.builtbyahnaf.memomint.application.SearchService;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepositoryImpl;
import dev.builtbyahnaf.memomint.ui.trust.LocationCheckInDialog;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.SearchRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.retrieval.SearchResult;
import dev.builtbyahnaf.memomint.ui.memory.MemoryAdapter;
import dev.builtbyahnaf.memomint.ui.memory.MemoryEditorActivity;
import dev.builtbyahnaf.memomint.ui.search.SearchAdapter;

/**
 * Primary user interface for Project MemoMint.
 *
 * <h2>Purpose & Architectural Scope</h2>
 * <p>Serves as the main dashboard for offline memory retrieval, lifecycle management,
 * and entry point to the full-screen {@link MemoryEditorActivity}.</p>
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

    private final ActivityResultLauncher<Intent> editorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadMemories();
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Log.w("MainActivity", "Notification permission not granted.");
                }
            });

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
        searchService.setMemoryRelationRepository(new MemoryRelationRepositoryImpl(database));

        // Schedule periodic check-in audit alarm (Phase 5)
        ConfirmationScheduler.scheduleRecurringAudit(this);

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
                if (memory != null) {
                    openMemoryEditor(memory.getId());
                }
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

        // Configure FAB for Memory Creation (Launches Full-Sized Activity):
        fabAddMemory.setOnClickListener(v -> openMemoryEditor(/* memoryId= */ null));

        // Prompt for notification permission on Android 13+ if not already granted
        checkNotificationPermission();

        // Initial asynchronous query to populate the memory feed upon activity launch.
        loadMemories();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMemories();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    /**
     * Launches the full-screen {@link MemoryEditorActivity} for creating or editing memories.
     *
     * @param memoryId target memory UUID if editing; null if creating a new memory
     */
    private void openMemoryEditor(String memoryId) {
        Intent intent = new Intent(this, MemoryEditorActivity.class);
        if (memoryId != null) {
            intent.putExtra(MemoryEditorActivity.EXTRA_MEMORY_ID, memoryId);
        }
        editorLauncher.launch(intent);
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
    // MemoryAdapter Callbacks (Item Actions)
    // =========================================================================

    /**
     * Handles the user tapping "Edit" on a memory card.
     * Launches the full-screen editor activity for the selected memory.
     *
     * @param memory the memory entity to modify
     */
    @Override
    public void onEdit(Memory memory) {
        if (memory != null) {
            openMemoryEditor(memory.getId());
        }
    }

    /**
     * Handles user confirmation of physical item location or factual freshness (FR-09, Section 15.2).
     *
     * <p>For physical items and locations, displays the {@link LocationCheckInDialog} allowing
     * the user to confirm in place, mark as moved, or defer. For notes, directly updates
     * {@code last_confirmed_at} with the current UTC epoch timestamp.</p>
     *
     * @param memory the memory entity whose freshness is confirmed
     */
    @Override
    public void onConfirm(Memory memory) {
        if (memory.getType() == MemoryType.PHYSICAL_ITEM || memory.getType() == MemoryType.LOCATION) {
            String itemLabel = (memory.getTitle() != null && !memory.getTitle().trim().isEmpty())
                    ? memory.getTitle().trim()
                    : "this item";
            LocationCheckInDialog dialog = LocationCheckInDialog.newInstance(
                    memory.getId(),
                    itemLabel,
                    memory.getOriginalText()
            );
            dialog.setListener(new LocationCheckInDialog.Listener() {
                @Override
                public void onConfirmStillHere(String memoryId) {
                    performConfirmMemory(memoryId);
                }

                @Override
                public void onItemMoved(String memoryId) {
                    openMemoryEditor(memoryId);
                }

                @Override
                public void onNotSure(String memoryId) {
                    Toast.makeText(MainActivity.this, "Check-in skipped for now", Toast.LENGTH_SHORT).show();
                }
            });
            dialog.show(getSupportFragmentManager(), "location_check_in");
        } else {
            performConfirmMemory(memory.getId());
        }
    }

    private void performConfirmMemory(String memoryId) {
        memoryService.confirmMemory(memoryId, new MemoryService.Callback<Boolean>() {
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