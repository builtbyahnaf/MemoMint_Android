package dev.builtbyahnaf.memomint;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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

import java.util.List;

import dev.builtbyahnaf.memomint.application.MemoryService;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;
import dev.builtbyahnaf.memomint.ui.memory.MemoryAdapter;

/**
 * Primary user interface for Project MemoMint (Phase 1).
 *
 * <h2>Purpose & Architectural Scope</h2>
 * <p>Serves as the main activity satisfying the Phase 1 completion criterion:
 * <i>"users can create and manage ordinary memories entirely offline."</i></p>
 * <p>Acts strictly as Layer A (Presentation): binds UI views to {@link MemoryService},
 * handles user dialog inputs, validates UI form fields, and renders asynchronous database
 * updates without blocking the main looper.</p>
 *
 * <h2>Key Capabilities</h2>
 * <ul>
 *   <li><b>Offline Capture:</b> Users tap the FAB to open the creation dialog, select a
 *       {@link MemoryType}, enter text, and save immediately to the local SQLite database.</li>
 *   <li><b>Preservation & Editing:</b> Selecting "Edit" opens a pre-populated dialog allowing
 *       the user to adjust their original text or title, updating {@code updatedAt} while
 *       strictly maintaining data provenance and creation history.</li>
 *   <li><b>Freshness Confirmation:</b> Physical-item memories feature a "Confirm Location"
 *       action that updates the {@code last_confirmed_at} timestamp upon user confirmation.</li>
 *   <li><b>Lifecycle Toggles:</b> Users can switch between Active and Archived memories,
 *       archive items to declutter their feed, and safely delete records.</li>
 * </ul>
 *
 * <h2>Threading & Tradeoffs</h2>
 * <p>All database I/O is executed asynchronously by {@link MemoryService} on a background
 * worker thread. UI state updates (showing empty placeholders, updating progress bars,
 * dispatching list diffs) are posted safely back to the Android main thread.</p>
 */
public class MainActivity extends AppCompatActivity implements MemoryAdapter.OnMemoryActionListener {

    private MemoryService memoryService;
    private MemoryAdapter memoryAdapter;

    private RecyclerView recyclerView;
    private View layoutEmptyState;
    private ProgressBar progressBar;
    private ChipGroup chipGroupFilter;
    private ExtendedFloatingActionButton fabAddMemory;

    private MemoryStatus currentFilterStatus = MemoryStatus.ACTIVE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Apply system bar insets for edge-to-edge layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Services & Persistence Layer
        MemoMintDatabase database = MemoMintDatabase.getInstance(this);
        MemoryRepositoryImpl repository = new MemoryRepositoryImpl(database);
        memoryService = new MemoryService(repository);

        // Bind Views
        recyclerView = findViewById(R.id.recyclerViewMemories);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        progressBar = findViewById(R.id.progressBar);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        fabAddMemory = findViewById(R.id.fabAddMemory);

        // Configure RecyclerView
        memoryAdapter = new MemoryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(memoryAdapter);

        // Configure Filter Chips
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipArchived)) {
                currentFilterStatus = MemoryStatus.ARCHIVED;
            } else {
                currentFilterStatus = MemoryStatus.ACTIVE;
            }
            loadMemories();
        });

        // Configure FAB for Memory Creation
        fabAddMemory.setOnClickListener(v -> showMemoryEditorDialog(/* memoryToEdit= */ null));

        // Initial Load
        loadMemories();
    }

    /**
     * Loads memories for the currently selected filter status asynchronously.
     */
    private void loadMemories() {
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

    // =========================================================================
    // Memory Creation and Editing Dialogs
    // =========================================================================

    /**
     * Displays a Material dialog for capturing a new memory or editing an existing one.
     *
     * <p><b>Algorithm:</b></p>
     * <ol>
     *   <li>Inflate {@code dialog_memory_editor.xml}.</li>
     *   <li>Populate the type spinner with all {@link MemoryType} values.</li>
     *   <li>If editing, pre-fill title, original text, and select the corresponding type.</li>
     *   <li>Upon clicking Save, validate that text is non-empty.</li>
     *   <li>Dispatch to {@link MemoryService#createMemory} or {@link MemoryService#updateMemory}.</li>
     * </ol>
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

        // Populate MemoryType Spinner
        MemoryType[] types = MemoryType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].name().replace('_', ' ');
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, typeNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(spinnerAdapter);

        // Pre-fill existing data if editing
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
                .setPositiveButton(R.string.action_save, null) // Override onClick below to control dismissal
                .setNegativeButton(R.string.action_cancel, (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String originalText = etOriginalText.getText() != null ? etOriginalText.getText().toString().trim() : "";
                String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
                int selectedTypePos = spinnerType.getSelectedItemPosition();
                MemoryType selectedType = (selectedTypePos >= 0 && selectedTypePos < types.length) ? types[selectedTypePos] : MemoryType.NOTE;

                // Validation
                if (originalText.isEmpty()) {
                    etOriginalText.setError(getString(R.string.error_empty_text));
                    return;
                }

                if (isEdit) {
                    // Update existing
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
                    // Create new
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

    @Override
    public void onEdit(Memory memory) {
        showMemoryEditorDialog(memory);
    }

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