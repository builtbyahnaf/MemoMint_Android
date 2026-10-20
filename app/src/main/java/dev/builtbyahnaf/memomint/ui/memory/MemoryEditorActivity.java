package dev.builtbyahnaf.memomint.ui.memory;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepository;
import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.ConflictDetectionResult;
import dev.builtbyahnaf.memomint.domain.trust.ConflictDetector;
import dev.builtbyahnaf.memomint.domain.trust.SupersessionManager;
import dev.builtbyahnaf.memomint.ui.trust.ConflictResolutionDialog;
import dev.builtbyahnaf.memomint.R;
import dev.builtbyahnaf.memomint.application.MemoryService;
import dev.builtbyahnaf.memomint.application.ReminderScheduler;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.ReminderRepository;
import dev.builtbyahnaf.memomint.data.repository.ReminderRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.context.ParsedReminder;
import dev.builtbyahnaf.memomint.domain.context.ReminderParser;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.IndexingStatus;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.Reminder;
import dev.builtbyahnaf.memomint.domain.model.ReminderStatus;
import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

/**
 * Full-sized Activity for capturing new memories and modifying existing memories.
 *
 * <h2>Architectural &amp; Functional Scope</h2>
 * <ul>
 *   <li><b>Full-Screen Experience:</b> Replaces the previous modal dialog with a dedicated,
 *       edge-to-edge full-sized screen.</li>
 *   <li><b>File &amp; File Location Support:</b> Enables attaching files via SAF or typing custom
 *       file paths/URIs, recording metadata and health state.</li>
 *   <li><b>Notifications &amp; Recurrence:</b> Allows setting exact notifications with daily,
 *       weekly, or monthly repeated reminders.</li>
 *   <li><b>Token-Efficient Intelligence:</b> Employs deterministic on-device NLP parsing
 *       ({@link ReminderParser}) to extract reminders and defined times from "what do you want to remember".</li>
 * </ul>
 */
public class MemoryEditorActivity extends AppCompatActivity {

    private static final String TAG = "MemoryEditorActivity";
    public static final String EXTRA_MEMORY_ID = "extra_memory_id";

    // Services & Repositories
    private MemoryService memoryService;
    private ReminderRepository reminderRepository;
    private FileReferenceRepository fileReferenceRepository;
    private ReminderScheduler reminderScheduler;

    // Views
    private MaterialToolbar toolbar;
    private AutoCompleteTextView actvMemoryType;
    private TextInputEditText etTitle;
    private TextInputEditText etOriginalText;

    // Smart Reminder Recommendation
    private MaterialCardView cardSmartSuggestion;
    private TextView tvSmartSuggestionText;
    private MaterialButton btnApplySmartReminder;

    // Reminder Controls
    private MaterialSwitch switchEnableReminder;
    private View layoutReminderFields;
    private TextInputEditText etReminderTitle;
    private MaterialButton btnSelectDate;
    private MaterialButton btnSelectTime;
    private AutoCompleteTextView actvRepeatInterval;

    // File Controls
    private MaterialButton btnBrowseFile;
    private MaterialButton btnClearFile;
    private TextInputEditText etFileLocation;
    private View layoutFileDetails;
    private TextView tvFileName;
    private TextView tvFileInfo;
    private MaterialButton btnSaveMemory;

    // State Variables
    private String editingMemoryId = null;
    private Memory existingMemory = null;
    private Reminder existingReminder = null;
    private FileReference attachedFileRef = null;

    private long selectedReminderTimestamp;
    private RepeatInterval selectedRepeatInterval = RepeatInterval.NONE;
    private ParsedReminder lastParsedReminder = null;
    private final Handler nlpHandler = new Handler(Looper.getMainLooper());
    private Runnable nlpRunnable;

    // SAF File Picker Launcher
    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFileSelected);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_memory_editor);

        // Initialize dependencies
        MemoMintDatabase db = MemoMintDatabase.getInstance(this);
        memoryService = new MemoryService(new MemoryRepositoryImpl(db));
        reminderRepository = new ReminderRepositoryImpl(db);
        fileReferenceRepository = new FileReferenceRepositoryImpl(db);
        reminderScheduler = new ReminderScheduler(this, reminderRepository);

        // Check if editing existing memory
        editingMemoryId = getIntent().getStringExtra(EXTRA_MEMORY_ID);

        // Initialize default reminder timestamp: Tomorrow at 9:00 AM
        Calendar defaultCal = Calendar.getInstance();
        defaultCal.add(Calendar.DAY_OF_YEAR, 1);
        defaultCal.set(Calendar.HOUR_OF_DAY, 9);
        defaultCal.set(Calendar.MINUTE, 0);
        defaultCal.set(Calendar.SECOND, 0);
        defaultCal.set(Calendar.MILLISECOND, 0);
        selectedReminderTimestamp = defaultCal.getTimeInMillis();

        initViews();
        configureEdgeToEdge();
        setupToolbar();
        setupDropdowns();
        setupListeners();

        if (editingMemoryId != null) {
            loadExistingMemory(editingMemoryId);
        } else {
            updateDateTimeButtons();
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscard();
            }
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        actvMemoryType = findViewById(R.id.actvMemoryType);
        etTitle = findViewById(R.id.etTitle);
        etOriginalText = findViewById(R.id.etOriginalText);

        cardSmartSuggestion = findViewById(R.id.cardSmartSuggestion);
        tvSmartSuggestionText = findViewById(R.id.tvSmartSuggestionText);
        btnApplySmartReminder = findViewById(R.id.btnApplySmartReminder);

        switchEnableReminder = findViewById(R.id.switchEnableReminder);
        layoutReminderFields = findViewById(R.id.layoutReminderFields);
        etReminderTitle = findViewById(R.id.etReminderTitle);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSelectTime = findViewById(R.id.btnSelectTime);
        actvRepeatInterval = findViewById(R.id.actvRepeatInterval);

        btnBrowseFile = findViewById(R.id.btnBrowseFile);
        btnClearFile = findViewById(R.id.btnClearFile);
        etFileLocation = findViewById(R.id.etFileLocation);
        layoutFileDetails = findViewById(R.id.layoutFileDetails);
        tvFileName = findViewById(R.id.tvFileName);
        tvFileInfo = findViewById(R.id.tvFileInfo);
        btnSaveMemory = findViewById(R.id.btnSaveMemory);
    }

    private void configureEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editorRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
    }

    private void setupToolbar() {
        toolbar.setTitle(editingMemoryId != null ? R.string.title_activity_memory_editor_edit : R.string.title_activity_memory_editor);
        toolbar.setNavigationOnClickListener(v -> confirmDiscard());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveMemory();
                return true;
            }
            return false;
        });
    }

    private void setupDropdowns() {
        // Memory Type Dropdown
        MemoryType[] types = MemoryType.values();
        String[] typeNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = types[i].name().replace('_', ' ');
        }
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, typeNames);
        actvMemoryType.setAdapter(typeAdapter);
        actvMemoryType.setText(types[0].name().replace('_', ' '), false);

        // Repeat Interval Dropdown
        String[] repeatLabels = new String[]{
                getString(R.string.repeat_none),
                getString(R.string.repeat_daily),
                getString(R.string.repeat_weekly),
                getString(R.string.repeat_monthly)
        };
        ArrayAdapter<String> repeatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, repeatLabels);
        actvRepeatInterval.setAdapter(repeatAdapter);
        actvRepeatInterval.setText(repeatLabels[0], false);
        actvRepeatInterval.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 1: selectedRepeatInterval = RepeatInterval.DAILY; break;
                case 2: selectedRepeatInterval = RepeatInterval.WEEKLY; break;
                case 3: selectedRepeatInterval = RepeatInterval.MONTHLY; break;
                default: selectedRepeatInterval = RepeatInterval.NONE; break;
            }
        });
    }

    private void setupListeners() {
        // Intelligent text parsing on "What do you want to remember"
        etOriginalText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (nlpRunnable != null) {
                    nlpHandler.removeCallbacks(nlpRunnable);
                }
                nlpRunnable = () -> runIntelligentReminderParsing(s != null ? s.toString() : "");
                nlpHandler.postDelayed(nlpRunnable, 350);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Smart Suggestion Apply Button
        btnApplySmartReminder.setOnClickListener(v -> applySmartReminder());

        // Reminder Switch Toggle
        switchEnableReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutReminderFields.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked && (etReminderTitle.getText() == null || etReminderTitle.getText().toString().trim().isEmpty())) {
                if (lastParsedReminder != null && !lastParsedReminder.getReminderTitle().isEmpty()) {
                    etReminderTitle.setText(lastParsedReminder.getReminderTitle());
                } else if (etTitle.getText() != null && !etTitle.getText().toString().trim().isEmpty()) {
                    etReminderTitle.setText(etTitle.getText().toString().trim());
                }
            }
        });

        // Date Picker
        btnSelectDate.setOnClickListener(v -> showDatePicker());

        // Time Picker
        btnSelectTime.setOnClickListener(v -> showTimePicker());

        // File Selection Buttons
        btnBrowseFile.setOnClickListener(v -> openDocumentLauncher.launch(new String[]{"*/*"}));
        btnClearFile.setOnClickListener(v -> clearAttachedFile());

        // Save Button
        btnSaveMemory.setOnClickListener(v -> saveMemory());
    }

    /**
     * Executes deterministic, token-efficient intelligence parsing against user input.
     *
     * @param text raw input from "what do you want to remember"
     */
    private void runIntelligentReminderParsing(String text) {
        if (text == null || text.trim().isEmpty()) {
            cardSmartSuggestion.setVisibility(View.GONE);
            lastParsedReminder = null;
            return;
        }

        ParsedReminder parsed = ReminderParser.parse(text);
        if (parsed.hasReminderIntent()) {
            lastParsedReminder = parsed;
            tvSmartSuggestionText.setText(getString(R.string.smart_reminder_applied, parsed.getExplanation()));
            cardSmartSuggestion.setVisibility(View.VISIBLE);
        } else {
            cardSmartSuggestion.setVisibility(View.GONE);
            lastParsedReminder = null;
        }
    }

    private void applySmartReminder() {
        if (lastParsedReminder == null) return;

        switchEnableReminder.setChecked(true);
        selectedReminderTimestamp = lastParsedReminder.getTargetTimestamp();
        selectedRepeatInterval = RepeatInterval.fromString(lastParsedReminder.getRepeatInterval());

        if (!lastParsedReminder.getReminderTitle().isEmpty()) {
            etReminderTitle.setText(lastParsedReminder.getReminderTitle());
        }

        updateRepeatDropdown(selectedRepeatInterval);
        updateDateTimeButtons();
        cardSmartSuggestion.setVisibility(View.GONE);
        Toast.makeText(this, "Reminder set for " + lastParsedReminder.getExplanation(), Toast.LENGTH_SHORT).show();
    }

    private void updateDateTimeButtons() {
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());

        Date date = new Date(selectedReminderTimestamp);
        btnSelectDate.setText(dateFmt.format(date));
        btnSelectTime.setText(timeFmt.format(date));
    }

    private void updateRepeatDropdown(RepeatInterval interval) {
        String label = getString(R.string.repeat_none);
        switch (interval) {
            case DAILY: label = getString(R.string.repeat_daily); break;
            case WEEKLY: label = getString(R.string.repeat_weekly); break;
            case MONTHLY: label = getString(R.string.repeat_monthly); break;
            case NONE: default: label = getString(R.string.repeat_none); break;
        }
        actvRepeatInterval.setText(label, false);
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedReminderTimestamp);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar updated = Calendar.getInstance();
                    updated.setTimeInMillis(selectedReminderTimestamp);
                    updated.set(Calendar.YEAR, year);
                    updated.set(Calendar.MONTH, month);
                    updated.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    selectedReminderTimestamp = updated.getTimeInMillis();
                    updateDateTimeButtons();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedReminderTimestamp);

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Calendar updated = Calendar.getInstance();
                    updated.setTimeInMillis(selectedReminderTimestamp);
                    updated.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    updated.set(Calendar.MINUTE, minute);
                    updated.set(Calendar.SECOND, 0);
                    updated.set(Calendar.MILLISECOND, 0);
                    selectedReminderTimestamp = updated.getTimeInMillis();
                    updateDateTimeButtons();
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false
        );
        dialog.show();
    }

    private void onFileSelected(Uri uri) {
        if (uri == null) return;

        try {
            // Take persistable permission if granted via SAF
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException ignored) {
                // Not all content providers support persisted grants; proceed with normal URI
            }

            String displayName = null;
            Long sizeBytes = null;
            String mimeType = getContentResolver().getType(uri);

            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx);
                    }
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx);
                    }
                }
            }

            if (displayName == null) {
                displayName = uri.getLastPathSegment();
            }

            etFileLocation.setText(uri.toString());

            attachedFileRef = new FileReference();
            attachedFileRef.setId(UUID.randomUUID().toString());
            attachedFileRef.setUriOrPath(uri.toString());
            attachedFileRef.setDisplayName(displayName != null ? displayName : "Document");
            attachedFileRef.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
            attachedFileRef.setSizeBytes(sizeBytes);
            attachedFileRef.setAccessState(AccessState.ACCESSIBLE);
            attachedFileRef.setIndexingStatus(IndexingStatus.PENDING);
            attachedFileRef.setLastVerifiedAt(System.currentTimeMillis());

            displayFileDetails(attachedFileRef.getDisplayName(), attachedFileRef.getSizeBytes(), attachedFileRef.getMimeType());

            // If memory type is generic NOTE, suggest changing to FILE_REFERENCE
            if (actvMemoryType.getText().toString().equals(MemoryType.NOTE.name())) {
                actvMemoryType.setText(MemoryType.FILE_REFERENCE.name().replace('_', ' '), false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving chosen file URI: " + e.getMessage(), e);
            Toast.makeText(this, "Could not open selected file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void displayFileDetails(String name, Long sizeBytes, String mimeType) {
        layoutFileDetails.setVisibility(View.VISIBLE);
        btnClearFile.setVisibility(View.VISIBLE);
        tvFileName.setText(getString(R.string.file_selected_prefix, name));

        String sizeStr = (sizeBytes != null) ? formatFileSize(sizeBytes) : "Unknown size";
        tvFileInfo.setText(sizeStr + " • " + (mimeType != null ? mimeType : "File"));
    }

    private void clearAttachedFile() {
        attachedFileRef = null;
        etFileLocation.setText("");
        layoutFileDetails.setVisibility(View.GONE);
        btnClearFile.setVisibility(View.GONE);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Loads existing memory entity and its associated reminder or file reference for editing.
     */
    private void loadExistingMemory(String memoryId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            MemoMintDatabase db = MemoMintDatabase.getInstance(this);
            Memory memory = new MemoryRepositoryImpl(db).findById(memoryId);
            Reminder reminder = reminderRepository.findByMemoryId(memoryId);

            FileReference fileRef = null;
            if (memory != null && memory.getSourceId() != null) {
                fileRef = fileReferenceRepository.findById(memory.getSourceId());
            }

            final Memory mem = memory;
            final Reminder rem = reminder;
            final FileReference fr = fileRef;

            runOnUiThread(() -> {
                if (mem == null) {
                    Toast.makeText(this, "Memory not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                existingMemory = mem;
                existingReminder = rem;
                attachedFileRef = fr;

                etTitle.setText(mem.getTitle());
                etOriginalText.setText(mem.getOriginalText());
                actvMemoryType.setText(mem.getType().name().replace('_', ' '), false);

                // Populate Reminder if present
                if (rem != null && rem.isActive()) {
                    switchEnableReminder.setChecked(true);
                    etReminderTitle.setText(rem.getTitle());
                    selectedReminderTimestamp = rem.getScheduledAt();
                    selectedRepeatInterval = RepeatInterval.fromString(rem.getRepeatInterval());
                    updateRepeatDropdown(selectedRepeatInterval);
                    updateDateTimeButtons();
                } else {
                    switchEnableReminder.setChecked(false);
                    updateDateTimeButtons();
                }

                // Populate File Reference if present
                if (fr != null) {
                    etFileLocation.setText(fr.getUriOrPath());
                    displayFileDetails(fr.getDisplayName(), fr.getSizeBytes(), fr.getMimeType());
                }
            });
        });
    }

    /**
     * Saves the memory, associates files and reminders, and schedules notifications.
     */
    private void saveMemory() {
        String originalText = (etOriginalText.getText() != null) ? etOriginalText.getText().toString().trim() : "";
        String title = (etTitle.getText() != null) ? etTitle.getText().toString().trim() : "";

        if (originalText.isEmpty()) {
            etOriginalText.setError(getString(R.string.error_empty_text));
            return;
        }

        // Determine MemoryType
        String typeStr = actvMemoryType.getText().toString().replace(' ', '_');
        MemoryType selectedType = MemoryType.NOTE;
        try {
            selectedType = MemoryType.valueOf(typeStr);
        } catch (IllegalArgumentException ignored) {}

        // Handle File Reference or Custom Location input
        String customLocation = (etFileLocation.getText() != null) ? etFileLocation.getText().toString().trim() : "";
        if (!customLocation.isEmpty()) {
            if (attachedFileRef == null) {
                attachedFileRef = new FileReference();
                attachedFileRef.setId(UUID.randomUUID().toString());
                attachedFileRef.setUriOrPath(customLocation);
                attachedFileRef.setDisplayName(new File(customLocation).getName());
                attachedFileRef.setAccessState(AccessState.NOT_YET_VERIFIED);
                attachedFileRef.setIndexingStatus(IndexingStatus.PENDING);
                attachedFileRef.setLastVerifiedAt(System.currentTimeMillis());
            } else {
                attachedFileRef.setUriOrPath(customLocation);
            }
        }

        final MemoryType finalType = selectedType;
        final boolean isEdit = (existingMemory != null);

        // Run persistence asynchronously on background executor
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String sourceId = null;

                // 1. Persist File Reference if attached
                if (attachedFileRef != null) {
                    fileReferenceRepository.save(attachedFileRef);
                    sourceId = attachedFileRef.getId();
                }

                Memory memoryToSave;
                if (isEdit) {
                    memoryToSave = existingMemory;
                    memoryToSave.setTitle(title);
                    memoryToSave.setOriginalText(originalText);
                    memoryToSave.setType(finalType);
                    memoryToSave.setUpdatedAt(System.currentTimeMillis());
                    if (sourceId != null) {
                        memoryToSave.setSourceId(sourceId);
                    }
                    new MemoryRepositoryImpl(MemoMintDatabase.getInstance(this)).save(memoryToSave);
                } else {
                    SourceType srcType = SourceType.TYPED;
                    memoryToSave = new Memory(
                            UUID.randomUUID().toString(),
                            finalType,
                            originalText,
                            title,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            srcType,
                            sourceId,
                            MemoryStatus.ACTIVE,
                            null
                    );
                    new MemoryRepositoryImpl(MemoMintDatabase.getInstance(this)).save(memoryToSave);
                }

                // 2. Handle Reminders & Notifications
                boolean reminderEnabled = switchEnableReminder.isChecked();
                if (reminderEnabled) {
                    String reminderTitle = (etReminderTitle.getText() != null && !etReminderTitle.getText().toString().trim().isEmpty())
                            ? etReminderTitle.getText().toString().trim()
                            : (!title.isEmpty() ? title : originalText);

                    Reminder reminder = (existingReminder != null) ? existingReminder : new Reminder();
                    if (reminder.getId() == null) {
                        reminder.setId(UUID.randomUUID().toString());
                        reminder.setCreatedAt(System.currentTimeMillis());
                    }
                    reminder.setMemoryId(memoryToSave.getId());
                    reminder.setTitle(reminderTitle);
                    reminder.setScheduledAt(selectedReminderTimestamp);
                    reminder.setTimeZoneId(TimeZone.getDefault().getID());
                    reminder.setStatus(ReminderStatus.ACTIVE);
                    reminder.setRepeatInterval(selectedRepeatInterval.name());

                    reminderRepository.save(reminder);
                    reminderScheduler.schedule(reminder);
                    Log.i(TAG, "Scheduled reminder id=" + reminder.getId() + " at " + selectedReminderTimestamp);
                } else {
                    // Reminder disabled: cancel any active alarm
                    if (existingReminder != null) {
                        reminderScheduler.cancel(existingReminder.getId());
                        reminderRepository.deleteByMemoryId(memoryToSave.getId());
                    }
                }

                // 3. Phase 5: Initialize ConfirmationPolicy for Physical Items & Locations
                if (finalType == MemoryType.PHYSICAL_ITEM || finalType == MemoryType.LOCATION) {
                    ConfirmationPolicyRepository policyRepo =
                            new ConfirmationPolicyRepositoryImpl(MemoMintDatabase.getInstance(this));
                    if (policyRepo.findByMemoryId(memoryToSave.getId()) == null) {
                        long nextCheck = System.currentTimeMillis() + (7L * 86_400_000L); // 7 days
                        policyRepo.saveOrUpdate(new ConfirmationPolicy(
                                memoryToSave.getId(),
                                true,
                                "WEEKLY",
                                7,
                                null,
                                nextCheck
                        ));
                    }
                }

                // 4. Phase 5: Conflict Detection for New Physical Item Records
                if (!isEdit && (finalType == MemoryType.PHYSICAL_ITEM || finalType == MemoryType.LOCATION)) {
                    ConflictDetector detector = new ConflictDetector(
                            new MemoryRepositoryImpl(MemoMintDatabase.getInstance(this)));
                    ConflictDetectionResult conflict = detector.detect(memoryToSave);

                    if (conflict.isConflictFound() && conflict.getConflictingMemoryId() != null) {
                        runOnUiThread(() -> {
                            ConflictResolutionDialog dialog = ConflictResolutionDialog.newInstance(
                                    memoryToSave.getId(),
                                    conflict.getConflictingMemoryId(),
                                    conflict.getSharedEntityLabel(),
                                    conflict.getExistingLocationSummary(),
                                    conflict.getNewLocationSummary()
                            );
                            dialog.setListener(new ConflictResolutionDialog.Listener() {
                                @Override
                                public void onSupersede(String newMemoryId, String conflictingMemoryId) {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        MemoMintDatabase dbInstance = MemoMintDatabase.getInstance(MemoryEditorActivity.this);
                                        SupersessionManager sm = new SupersessionManager(
                                                new MemoryRelationRepositoryImpl(dbInstance));
                                        sm.supersede(newMemoryId, conflictingMemoryId, System.currentTimeMillis());
                                    });
                                    Toast.makeText(MemoryEditorActivity.this, R.string.msg_memory_saved, Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                }

                                @Override
                                public void onKeepBoth(String newMemoryId, String conflictingMemoryId) {
                                    Toast.makeText(MemoryEditorActivity.this, R.string.msg_memory_saved, Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                }
                            });
                            dialog.show(getSupportFragmentManager(), "conflict_dialog");
                        });
                        return;
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.msg_memory_saved, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed saving memory: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmDiscard() {
        String text = (etOriginalText.getText() != null) ? etOriginalText.getText().toString().trim() : "";
        if (!text.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.discard_dialog_title)
                    .setMessage(R.string.discard_dialog_message)
                    .setPositiveButton(R.string.action_discard, (dialog, which) -> finish())
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            finish();
        }
    }
}
