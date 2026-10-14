package dev.builtbyahnaf.memomint.domain.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link Memory} domain model.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>Field getters and setters round-trip correctly.</li>
 *   <li>Convenience predicates ({@code isActive}, {@code isDeleted}) reflect the status field.</li>
 *   <li>Equality and hashCode are based on id, not field equality.</li>
 *   <li>The original-text field behaves as a plain mutable field (enforcement of
 *       the "never overwrite" invariant is a convention documented in the Javadoc,
 *       not a compile-time restriction).</li>
 * </ul>
 *
 * <p>These tests are pure JVM tests with no Android dependencies.</p>
 */
public class MemoryTest {

    // =========================================================================
    // Construction and field round-trip
    // =========================================================================

    @Test
    public void fullConstructor_setsAllFields() {
        Memory m = new Memory(
                "id-001",
                MemoryType.NOTE,
                "My original text",
                "A title",
                1_000L,
                2_000L,
                SourceType.TYPED,
                "src-123",
                MemoryStatus.ACTIVE,
                3_000L
        );

        assertEquals("id-001", m.getId());
        assertEquals(MemoryType.NOTE, m.getType());
        assertEquals("My original text", m.getOriginalText());
        assertEquals("A title", m.getTitle());
        assertEquals(1_000L, m.getCreatedAt());
        assertEquals(2_000L, m.getUpdatedAt());
        assertEquals(SourceType.TYPED, m.getSourceType());
        assertEquals("src-123", m.getSourceId());
        assertEquals(MemoryStatus.ACTIVE, m.getStatus());
        assertEquals(Long.valueOf(3_000L), m.getLastConfirmedAt());
    }

    @Test
    public void nullableFields_canBeNull() {
        Memory m = new Memory(
                "id-002",
                MemoryType.PHYSICAL_ITEM,
                "Item text",
                /* title= */ null,
                100L, 200L,
                SourceType.VOICE,
                /* sourceId= */ null,
                MemoryStatus.ACTIVE,
                /* lastConfirmedAt= */ null
        );

        assertNull("title should be null", m.getTitle());
        assertNull("sourceId should be null", m.getSourceId());
        assertNull("lastConfirmedAt should be null", m.getLastConfirmedAt());
    }

    @Test
    public void setters_updateFields() {
        Memory m = new Memory();
        m.setId("new-id");
        m.setType(MemoryType.LOCATION);
        m.setOriginalText("A new original text");
        m.setTitle("New title");
        m.setCreatedAt(500L);
        m.setUpdatedAt(600L);
        m.setSourceType(SourceType.SHARED);
        m.setSourceId("shared-src");
        m.setStatus(MemoryStatus.ARCHIVED);
        m.setLastConfirmedAt(700L);

        assertEquals("new-id", m.getId());
        assertEquals(MemoryType.LOCATION, m.getType());
        assertEquals("A new original text", m.getOriginalText());
        assertEquals("New title", m.getTitle());
        assertEquals(500L, m.getCreatedAt());
        assertEquals(600L, m.getUpdatedAt());
        assertEquals(SourceType.SHARED, m.getSourceType());
        assertEquals("shared-src", m.getSourceId());
        assertEquals(MemoryStatus.ARCHIVED, m.getStatus());
        assertEquals(Long.valueOf(700L), m.getLastConfirmedAt());
    }

    // =========================================================================
    // Status predicates
    // =========================================================================

    @Test
    public void isActive_trueForActiveStatus() {
        Memory m = new Memory();
        m.setId("id-active");
        m.setStatus(MemoryStatus.ACTIVE);
        assertTrue(m.isActive());
        assertFalse(m.isDeleted());
    }

    @Test
    public void isDeleted_trueForDeletedStatus() {
        Memory m = new Memory();
        m.setId("id-deleted");
        m.setStatus(MemoryStatus.DELETED);
        assertTrue(m.isDeleted());
        assertFalse(m.isActive());
    }

    @Test
    public void archived_isNeitherActiveNorDeleted() {
        Memory m = new Memory();
        m.setId("id-archived");
        m.setStatus(MemoryStatus.ARCHIVED);
        assertFalse(m.isActive());
        assertFalse(m.isDeleted());
    }

    // =========================================================================
    // Equality and hashCode — identity is based on id
    // =========================================================================

    @Test
    public void equals_sameId_returnsTrue() {
        Memory a = new Memory();
        a.setId("shared-id");
        a.setStatus(MemoryStatus.ACTIVE);

        Memory b = new Memory();
        b.setId("shared-id");
        b.setStatus(MemoryStatus.ARCHIVED); // different field, same id

        assertEquals("Same id should be equal", a, b);
    }

    @Test
    public void equals_differentId_returnsFalse() {
        Memory a = new Memory();
        a.setId("id-A");

        Memory b = new Memory();
        b.setId("id-B");

        assertNotEquals("Different ids should not be equal", a, b);
    }

    @Test
    public void hashCode_sameId_sameHash() {
        Memory a = new Memory();
        a.setId("hashed-id");

        Memory b = new Memory();
        b.setId("hashed-id");

        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equals_self_returnsTrue() {
        Memory m = new Memory();
        m.setId("self");
        assertEquals(m, m);
    }

    @Test
    public void equals_null_returnsFalse() {
        Memory m = new Memory();
        m.setId("non-null");
        assertNotEquals(m, null);
    }

    @Test
    public void equals_differentClass_returnsFalse() {
        Memory m = new Memory();
        m.setId("class-check");
        //noinspection AssertBetweenInconvertibleTypes
        assertNotEquals(m, "a string");
    }

    // =========================================================================
    // Original text — the field is mutable but the invariant is documented
    // =========================================================================

    @Test
    public void setOriginalText_updatesField() {
        // This test verifies that the field updates correctly.
        // The "never overwrite with generated content" rule is enforced by convention
        // (documented in Memory.java Javadoc), not by type-system restriction.
        Memory m = new Memory();
        m.setId("orig-test");
        m.setOriginalText("First version");
        assertEquals("First version", m.getOriginalText());

        m.setOriginalText("User-edited version");
        assertEquals("User-edited version", m.getOriginalText());
    }

    // =========================================================================
    // MemoryType enum completeness
    // =========================================================================

    @Test
    public void memoryType_allValuesAreDistinct() {
        MemoryType[] types = MemoryType.values();
        // Verify there are exactly 6 types as defined in Phase 1 scope
        assertEquals("Expected 6 MemoryType values", 6, types.length);
    }

    // =========================================================================
    // MemoryStatus enum
    // =========================================================================

    @Test
    public void memoryStatus_allValuesAreDistinct() {
        MemoryStatus[] statuses = MemoryStatus.values();
        assertEquals("Expected 3 MemoryStatus values", 3, statuses.length);
    }

    // =========================================================================
    // toString — must include id and type for log readability
    // =========================================================================

    @Test
    public void toString_containsIdAndType() {
        Memory m = new Memory();
        m.setId("tostring-id");
        m.setType(MemoryType.FILE_REFERENCE);
        m.setStatus(MemoryStatus.ACTIVE);
        m.setCreatedAt(12345L);

        String str = m.toString();
        assertTrue("toString should include id", str.contains("tostring-id"));
        assertTrue("toString should include type", str.contains("FILE_REFERENCE"));
    }
}
