package dev.builtbyahnaf.memomint.ui.debug;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.R;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.FileSearchRepository;
import dev.builtbyahnaf.memomint.data.repository.FileSearchRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.IndexJobRepository;
import dev.builtbyahnaf.memomint.data.repository.IndexJobRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.FileSearchResult;
import dev.builtbyahnaf.memomint.domain.model.IndexJob;

/**
 * Diagnostics and debugging activity for inspecting local document indexing status,
 * SAF permissions, and full-text search results.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §5.1)</h2>
 * <ul>
 *   <li><b>Live Queue Telemetry:</b> Displays pending, running, and recent index jobs.</li>
 *   <li><b>FTS Sandbox:</b> Allows developer to test raw queries directly against {@code files_fts}.</li>
 *   <li><b>Health Audit:</b> Displays tracked file references and their verified health states.</li>
 * </ul>
 */
public class FileIndexingDebugActivity extends AppCompatActivity {

    private MemoMintDatabase dbHelper;
    private FileReferenceRepository fileRepo;
    private IndexJobRepository jobRepo;
    private FileSearchRepository searchRepo;

    private TextView tvJobQueueStatus;
    private TextView tvTrackedFiles;
    private TextView tvSearchResults;
    private TextInputEditText etSearchQuery;
    private MaterialButton btnRefreshJobs;
    private MaterialButton btnRunHealthCheck;
    private MaterialButton btnExecuteSearch;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_indexing_debug);

        dbHelper = MemoMintDatabase.getInstance(this);
        fileRepo = new FileReferenceRepositoryImpl(dbHelper);
        jobRepo = new IndexJobRepositoryImpl(dbHelper);
        searchRepo = new FileSearchRepositoryImpl(dbHelper);

        initViews();
        setupListeners();
        loadDiagnostics();
    }

    private void initViews() {
        tvJobQueueStatus   = findViewById(R.id.tvJobQueueStatus);
        tvTrackedFiles     = findViewById(R.id.tvTrackedFiles);
        tvSearchResults    = findViewById(R.id.tvSearchResults);
        etSearchQuery      = findViewById(R.id.etSearchQuery);
        btnRefreshJobs     = findViewById(R.id.btnRefreshJobs);
        btnRunHealthCheck  = findViewById(R.id.btnRunHealthCheck);
        btnExecuteSearch   = findViewById(R.id.btnExecuteSearch);
    }

    private void setupListeners() {
        btnRefreshJobs.setOnClickListener(v -> loadDiagnostics());

        btnRunHealthCheck.setOnClickListener(v -> {
            Toast.makeText(this, "Auditing file health...", Toast.LENGTH_SHORT).show();
            loadDiagnostics();
        });

        btnExecuteSearch.setOnClickListener(v -> {
            String query = (etSearchQuery.getText() != null) ? etSearchQuery.getText().toString().trim() : "";
            if (query.isEmpty()) {
                tvSearchResults.setText("Please enter a query.");
                return;
            }
            executeSearch(query);
        });
    }

    private void loadDiagnostics() {
        executor.execute(() -> {
            List<IndexJob> recentJobs = jobRepo.findRecentJobs(10);
            List<FileReference> files = fileRepo.findAll(20);

            mainHandler.post(() -> {
                // Render jobs
                if (recentJobs.isEmpty()) {
                    tvJobQueueStatus.setText("No index jobs recorded.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (IndexJob j : recentJobs) {
                        sb.append("• [").append(j.getStatus()).append("] ")
                                .append("progress: ").append(j.getProgress()).append("% ")
                                .append("src: ").append(truncate(j.getSourceId(), 8));
                        if (j.getErrorCode() != null) {
                            sb.append(" err: ").append(j.getErrorCode());
                        }
                        sb.append("\n");
                    }
                    tvJobQueueStatus.setText(sb.toString().trim());
                }

                // Render files
                if (files.isEmpty()) {
                    tvTrackedFiles.setText("No tracked files.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (FileReference f : files) {
                        sb.append("• ").append(f.getDisplayName())
                                .append(" (").append(f.getAccessState()).append(", ")
                                .append(f.getIndexingStatus()).append(")\n");
                    }
                    tvTrackedFiles.setText(sb.toString().trim());
                }
            });
        });
    }

    private void executeSearch(String query) {
        executor.execute(() -> {
            List<FileSearchResult> results = searchRepo.searchFiles(query, 10);
            mainHandler.post(() -> {
                if (results.isEmpty()) {
                    tvSearchResults.setText("No document matches found in files_fts.");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (FileSearchResult r : results) {
                        sb.append("📄 ").append(r.getTitle())
                                .append(" (Score: ").append(String.format("%.2f", r.getScore())).append(")\n")
                                .append("   Snippet: ").append(r.getSnippet()).append("\n")
                                .append("   Terms: ").append(r.getMatchedTerms()).append("\n\n");
                    }
                    tvSearchResults.setText(sb.toString().trim());
                }
            });
        });
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
