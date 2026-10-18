package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.context.ContextExtractor;
import dev.builtbyahnaf.memomint.domain.context.DateTimeExtractor;
import dev.builtbyahnaf.memomint.domain.context.ExtractionResult;
import dev.builtbyahnaf.memomint.domain.context.ItemExtractor;
import dev.builtbyahnaf.memomint.domain.context.KeywordExtractor;
import dev.builtbyahnaf.memomint.domain.context.LocationExtractor;
import dev.builtbyahnaf.memomint.domain.context.QuantityExtractor;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests validating the Phase 3 Context Extraction Pipeline and its 5 sub-extractors.
 *
 * <h2>Verifications Covered</h2>
 * <ul>
 *   <li>Physical item entity extraction with variant resolution and canonical normalization.</li>
 *   <li>Type isolation (NOTE memories do not produce physical item entities).</li>
 *   <li>Regex spatial preposition extraction and normalization.</li>
 *   <li>Digit and word-number quantity extraction with container counts.</li>
 *   <li>Relative and calendar date temporal extraction.</li>
 *   <li>Keyword extraction with stopword filtering and synonym expansion.</li>
 *   <li>Sequential orchestration across all 5 sub-extractors.</li>
 *   <li><b>FR-03 Invariant:</b> Original memory text is strictly preserved without mutation.</li>
 *   <li><b>NFR-08 Invariant:</b> Robust fault tolerance when passed null or invalid data.</li>
 * </ul>
 */
public class ContextExtractorTest {

    private ItemExtractor itemExtractor;
    private LocationExtractor locationExtractor;
    private QuantityExtractor quantityExtractor;
    private DateTimeExtractor dateTimeExtractor;
    private KeywordExtractor keywordExtractor;
    private ContextExtractor contextExtractor;

    @Before
    public void setUp() {
        itemExtractor = new ItemExtractor();
        locationExtractor = new LocationExtractor();
        quantityExtractor = new QuantityExtractor();
        dateTimeExtractor = new DateTimeExtractor();
        keywordExtractor = new KeywordExtractor();
        contextExtractor = ContextExtractor.create();
    }

    private Memory createMemory(String id, String text, MemoryType type) {
        long now = System.currentTimeMillis();
        return new Memory(
                id,
                type,
                text,
                "Title",
                now,
                now,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                now
        );
    }

    @Test
    public void testItemExtractor_extractsEarphones() {
        Memory memory = createMemory("M1", "My wireless earphones are on the table.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = itemExtractor.extract(memory);

        assertNotNull(results);
        assertFalse("Expected extracted items", results.isEmpty());

        boolean foundEarphone = false;
        for (ExtractionResult r : results) {
            if ("earphone".equals(r.getCanonicalLabel()) && r.getType() == ExtractionResult.Type.ITEM_NAME) {
                foundEarphone = true;
                assertEquals("earphones", r.getContent().toLowerCase());
            }
        }
        assertTrue("Expected canonical item 'earphone'", foundEarphone);
    }

    @Test
    public void testItemExtractor_extractsUsbCable() {
        Memory memory = createMemory("M2", "My spare usb-c cable is inside the bag.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = itemExtractor.extract(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundCable = false;
        for (ExtractionResult r : results) {
            if ("cable".equals(r.getCanonicalLabel()) && r.getType() == ExtractionResult.Type.ITEM_NAME) {
                foundCable = true;
            }
        }
        assertTrue("Expected canonical item 'cable'", foundCable);
    }

    @Test
    public void testItemExtractor_returnsEmpty_forNoteType() {
        Memory memory = createMemory("M3", "Remember to buy earphones and a new laptop.", MemoryType.NOTE);
        List<ExtractionResult> results = itemExtractor.extract(memory);

        assertNotNull(results);
        assertTrue("Non-PHYSICAL_ITEM memories must not extract physical items", results.isEmpty());
    }

    @Test
    public void testLocationExtractor_extractsDrawer() {
        Memory memory = createMemory("M4", "Spare house keys are in the top drawer of my desk.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = locationExtractor.extract(memory);

        assertNotNull(results);
        assertFalse("Expected extracted locations", results.isEmpty());

        boolean foundDrawer = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.LOCATION && r.getContent().contains("drawer")) {
                foundDrawer = true;
            }
        }
        assertTrue("Expected location containing 'drawer'", foundDrawer);
    }

    @Test
    public void testLocationExtractor_extractsBedside() {
        Memory memory = createMemory("M5", "Audio earbuds are on the bedside table.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = locationExtractor.extract(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundBedside = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.LOCATION && r.getContent().contains("bedside")) {
                foundBedside = true;
            }
        }
        assertTrue("Expected location containing 'bedside'", foundBedside);
    }

    @Test
    public void testLocationExtractor_extractsInMyOffice() {
        Memory memory = createMemory("M6", "My leather backpack is in my office.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = locationExtractor.extract(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundOffice = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.LOCATION && r.getContent().contains("office")) {
                foundOffice = true;
            }
        }
        assertTrue("Expected location containing 'office'", foundOffice);
    }

    @Test
    public void testQuantityExtractor_extractsDigitQuantity() {
        Memory memory = createMemory("M7", "I purchased 3 boxes of pens for the home office.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = quantityExtractor.extract(memory);

        assertNotNull(results);
        assertFalse("Expected extracted quantities", results.isEmpty());

        boolean foundQuantity = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.QUANTITY && "3 boxes of pens".equals(r.getContent())) {
                foundQuantity = true;
            }
        }
        assertTrue("Expected quantity '3 boxes of pens'", foundQuantity);
    }

    @Test
    public void testQuantityExtractor_extractsWordQuantity_three() {
        Memory memory = createMemory("M8", "I left three pens on the desk.", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> results = quantityExtractor.extract(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundQuantity = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.QUANTITY && "3 pens".equals(r.getContent())) {
                foundQuantity = true;
            }
        }
        assertTrue("Expected word-number converted to '3 pens'", foundQuantity);
    }

    @Test
    public void testDateTimeExtractor_extractsTomorrow() {
        Memory memory = createMemory("M9", "Doctor appointment scheduled for tomorrow.", MemoryType.REMINDER);
        List<ExtractionResult> results = dateTimeExtractor.extract(memory);

        assertNotNull(results);
        assertFalse("Expected temporal extractions", results.isEmpty());

        boolean foundTomorrow = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.TEMPORAL_MENTION && "tomorrow".equalsIgnoreCase(r.getContent())) {
                foundTomorrow = true;
            }
        }
        assertTrue("Expected temporal match for 'tomorrow'", foundTomorrow);
    }

    @Test
    public void testDateTimeExtractor_extractsNextMonday() {
        Memory memory = createMemory("M10", "Flight departs next Monday morning.", MemoryType.NOTE);
        List<ExtractionResult> results = dateTimeExtractor.extract(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundNextMonday = false;
        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.TEMPORAL_MENTION && "next monday".equalsIgnoreCase(r.getContent())) {
                foundNextMonday = true;
            }
        }
        assertTrue("Expected temporal match for 'next Monday'", foundNextMonday);
    }

    @Test
    public void testKeywordExtractor_expandsSynonyms() {
        Memory memory = createMemory("M11", "Backup cables in drawer.", MemoryType.NOTE);
        List<ExtractionResult> results = keywordExtractor.extract(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean hasBackup = false;
        boolean hasSpare = false;

        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.KEYWORD) {
                if ("backup".equalsIgnoreCase(r.getContent())) hasBackup = true;
                if ("spare".equalsIgnoreCase(r.getContent())) hasSpare = true;
            }
        }

        assertTrue("Expected original keyword 'backup'", hasBackup);
        assertTrue("Expected synonym expansion 'spare'", hasSpare);
    }

    @Test
    public void testContextExtractor_orchestratesAll() {
        Memory memory = createMemory(
                "M12",
                "Stored 3 boxes of pens in the drawer for tomorrow.",
                MemoryType.PHYSICAL_ITEM
        );
        List<ExtractionResult> results = contextExtractor.extractAll(memory);

        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean hasItem = false;
        boolean hasLocation = false;
        boolean hasQuantity = false;
        boolean hasTemporal = false;
        boolean hasKeyword = false;

        for (ExtractionResult r : results) {
            if (r.getType() == ExtractionResult.Type.ITEM_NAME) hasItem = true;
            if (r.getType() == ExtractionResult.Type.LOCATION) hasLocation = true;
            if (r.getType() == ExtractionResult.Type.QUANTITY) hasQuantity = true;
            if (r.getType() == ExtractionResult.Type.TEMPORAL_MENTION) hasTemporal = true;
            if (r.getType() == ExtractionResult.Type.KEYWORD) hasKeyword = true;
        }

        assertTrue("Expected item extraction", hasItem);
        assertTrue("Expected location extraction", hasLocation);
        assertTrue("Expected quantity extraction", hasQuantity);
        assertTrue("Expected temporal extraction", hasTemporal);
        assertTrue("Expected keyword extraction", hasKeyword);
    }

    @Test
    public void testFR03_originalTextNotModified() {
        String originalVerbatim = "Verbatim text containing earphones, 3 pens, in the drawer tomorrow.";
        Memory memory = createMemory("M13", originalVerbatim, MemoryType.PHYSICAL_ITEM);

        contextExtractor.extractAll(memory);

        assertEquals("FR-03 Invariant: memory.originalText must never be modified by extraction pipeline",
                originalVerbatim, memory.getOriginalText());
    }

    @Test
    public void testNFR08_extractionFailureDoesNotThrow() {
        // Null memory
        List<ExtractionResult> r1 = contextExtractor.extractAll(null);
        assertNotNull(r1);
        assertTrue(r1.isEmpty());

        // Memory with null text
        Memory memoryNullText = createMemory("M14", null, MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> r2 = contextExtractor.extractAll(memoryNullText);
        assertNotNull(r2);
        assertTrue(r2.isEmpty());

        // Memory with blank text
        Memory memoryBlankText = createMemory("M15", "    ", MemoryType.PHYSICAL_ITEM);
        List<ExtractionResult> r3 = contextExtractor.extractAll(memoryBlankText);
        assertNotNull(r3);
        assertTrue(r3.isEmpty());
    }
}
