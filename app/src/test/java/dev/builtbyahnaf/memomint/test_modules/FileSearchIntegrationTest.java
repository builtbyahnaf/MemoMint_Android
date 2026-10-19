package dev.builtbyahnaf.memomint.test_modules;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.FileSearchRepository;
import dev.builtbyahnaf.memomint.data.repository.FileSearchRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.FileSearchResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for document full-text indexing and retrieval via {@code files_fts}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FileSearchIntegrationTest {

    private MemoMintDatabase dbHelper;
    private FileSearchRepository searchRepository;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new MemoMintDatabase(context, null); // in-memory database
        searchRepository = new FileSearchRepositoryImpl(dbHelper);
    }

    @After
    public void tearDown() {
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    @Test
    public void testSearchFindsMatchingDocumentInFts() {
        searchRepository.indexDocumentContent(
                "doc-guitar",
                "notes_final_3.docx",
                "Electric vs Acoustic guitars comparison: Fender Stratocaster has brighter tone than Gibson Les Paul."
        );
        searchRepository.indexDocumentContent(
                "doc-dishwasher",
                "dishwasher_manual.pdf",
                "Bosch Dishwasher Model 500 warranty terms and cleaning filters."
        );

        List<FileSearchResult> results = searchRepository.searchFiles("guitar", 10);

        assertNotNull(results);
        assertEquals(1, results.size());
        FileSearchResult hit = results.get(0);
        assertEquals("doc-guitar", hit.getFileId());
        assertEquals("notes_final_3.docx", hit.getTitle());
        assertTrue(hit.getSnippet().toLowerCase().contains("guitar"));
        assertTrue(hit.getScore() > 0.0);
    }

    @Test
    public void testSearchCaseInsensitive() {
        searchRepository.indexDocumentContent(
                "doc-fender",
                "guitars.txt",
                "Fender Stratocaster serial number 12345."
        );

        // Lowercase search for uppercase word in content
        List<FileSearchResult> results = searchRepository.searchFiles("stratocaster", 10);

        assertFalse(results.isEmpty());
        assertEquals("doc-fender", results.get(0).getFileId());
    }

    @Test
    public void testRemoveDocumentDeletesFromFts() {
        searchRepository.indexDocumentContent(
                "doc-temp",
                "temp.txt",
                "Temporary document content about secret keys."
        );

        List<FileSearchResult> before = searchRepository.searchFiles("secret", 10);
        assertEquals(1, before.size());

        searchRepository.removeDocumentContent("doc-temp");

        List<FileSearchResult> after = searchRepository.searchFiles("secret", 10);
        assertTrue(after.isEmpty());
    }
}
