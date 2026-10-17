package dev.builtbyahnaf.memomint;

import org.junit.Test;

import static org.junit.Assert.*;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

/**
 * Host-side unit tests verifying domain entity integrity, invariants, and equality semantics.
 *
 * <h2>Purpose & Scope (Section 25.7)</h2>
 * <p>These tests execute on the local JVM without requiring an Android emulator or device runtime.
 * They guarantee that core domain entities (such as {@link Memory}) adhere to system invariants:
 * <ul>
 *   <li><b>Entity Identity Equality:</b> Two instances sharing the same stable UUID are treated as
 *       the same entity, regardless of whether transient fields or modification timestamps differ.</li>
 *   <li><b>State Predicates:</b> Helper methods like {@link Memory#isActive()} and {@link Memory#isDeleted()}
 *       correctly reflect lifecycle status transitions.</li>
 * </ul>
 *
 * <h2>Tradeoffs</h2>
 * <p>Testing domain logic on the local JVM is fast (milliseconds) and reliable for CI/CD pipelines,
 * whereas database and UI interactions are verified via repository and instrumented integration tests.</p>
 */
public class ExampleUnitTest {

    /**
     * Verifies that two distinct Java object instances representing the same underlying memory UUID
     * evaluate as equal under {@link Memory#equals(Object)} and yield identical hash codes.
     *
     * <p><b>Invariant:</b> Entity identity is dictated strictly by {@code id}. Even if user edits
     * change {@code originalText} or {@code updatedAt}, entity identity remains stable.</p>
     */
    @Test
    public void memoryEquality_basedOnId_evaluatesCorrectly() {
        String sharedId = "test-uuid-12345";
        long now = System.currentTimeMillis();

        Memory memoryA = new Memory(
                sharedId,
                MemoryType.PHYSICAL_ITEM,
                "Passport is in the blue backpack",
                "Passport",
                now,
                now,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                null
        );

        Memory memoryB = new Memory(
                sharedId,
                MemoryType.PHYSICAL_ITEM,
                "Passport is in the travel drawer", // updated text in memoryB
                "Passport (Updated)",
                now,
                now + 5000,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                now + 5000
        );

        assertEquals("Memories with identical IDs must be equal", memoryA, memoryB);
        assertEquals("Memories with identical IDs must produce matching hashCodes",
                memoryA.hashCode(), memoryB.hashCode());
    }

    /**
     * Verifies that lifecycle helper predicates correctly report active and deleted states.
     */
    @Test
    public void memoryStatus_predicatesReflectLifecycleState() {
        Memory memory = new Memory();
        memory.setId("test-uuid-active");
        memory.setStatus(MemoryStatus.ACTIVE);

        assertTrue("Memory with ACTIVE status must return true for isActive()", memory.isActive());
        assertFalse("Memory with ACTIVE status must return false for isDeleted()", memory.isDeleted());

        memory.setStatus(MemoryStatus.DELETED);
        assertFalse("Memory with DELETED status must return false for isActive()", memory.isActive());
        assertTrue("Memory with DELETED status must return true for isDeleted()", memory.isDeleted());

        memory.setStatus(MemoryStatus.ARCHIVED);
        assertFalse("Memory with ARCHIVED status must return false for isActive()", memory.isActive());
        assertFalse("Memory with ARCHIVED status must return false for isDeleted()", memory.isDeleted());
    }
}