package dev.builtbyahnaf.memomint.domain.trust;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.builtbyahnaf.memomint.data.repository.MemoryRepository;
import dev.builtbyahnaf.memomint.domain.model.ConflictDetectionResult;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;

/**
 * Pure Java domain service that detects contradictions between newly created memories and
 * existing active memories (Section 15.4, Phase 5 Plan §2.4).
 *
 * <h2>Purpose</h2>
 * <p>When a user records a new location or physical item memory, this detector analyzes active
 * records to determine if an existing memory asserts an overlapping or competing claim.
 * Detected conflicts are surfaced to the user to offer supersession or independent coexistence.</p>
 *
 * <h2>Detection Heuristic</h2>
 * <p>Uses conservative keyword extraction on normalized text. If two non-identical memories
 * share two or more meaningful keywords (length &ge; 4), a conflict candidate is identified.</p>
 */
public class ConflictDetector {

    private final MemoryRepository memoryRepository;

    /**
     * Constructs a new {@link ConflictDetector}.
     *
     * @param memoryRepository the repository to query active memories; must not be null
     * @throws IllegalArgumentException if memoryRepository is null
     */
    public ConflictDetector(MemoryRepository memoryRepository) {
        if (memoryRepository == null) {
            throw new IllegalArgumentException("memoryRepository must not be null");
        }
        this.memoryRepository = memoryRepository;
    }

    /**
     * Evaluates a newly created memory against existing active physical item and location memories.
     *
     * @param newMemory the new memory being evaluated; must not be null
     * @return a {@link ConflictDetectionResult} describing whether a contradiction was found
     * @throws IllegalArgumentException if newMemory is null
     */
    public ConflictDetectionResult detect(Memory newMemory) {
        if (newMemory == null) {
            throw new IllegalArgumentException("newMemory must not be null");
        }

        if (newMemory.getType() != MemoryType.PHYSICAL_ITEM && newMemory.getType() != MemoryType.LOCATION) {
            return ConflictDetectionResult.noConflict(newMemory.getId());
        }

        List<Memory> physical = memoryRepository.findByTypeAndStatus(MemoryType.PHYSICAL_ITEM, MemoryStatus.ACTIVE);
        List<Memory> locations = memoryRepository.findByTypeAndStatus(MemoryType.LOCATION, MemoryStatus.ACTIVE);

        List<Memory> candidates = new ArrayList<>();
        if (physical != null) {
            for (Memory m : physical) {
                if (m != null && m.getId() != null && !m.getId().equals(newMemory.getId())) {
                    candidates.add(m);
                }
            }
        }
        if (locations != null) {
            for (Memory m : locations) {
                if (m != null && m.getId() != null && !m.getId().equals(newMemory.getId())) {
                    candidates.add(m);
                }
            }
        }

        Set<String> newKeywords = extractKeywords(newMemory.getOriginalText());
        if (newKeywords.size() < 2) {
            return ConflictDetectionResult.noConflict(newMemory.getId());
        }

        String newText = newMemory.getOriginalText();

        for (Memory existingMemory : candidates) {
            String existingText = existingMemory.getOriginalText();
            if (existingText == null) {
                continue;
            }

            boolean isIdenticalText = newText != null && newText.trim().equalsIgnoreCase(existingText.trim());
            if (isIdenticalText) {
                continue;
            }

            Set<String> existingKeywords = extractKeywords(existingText);
            Set<String> intersection = new HashSet<>(newKeywords);
            intersection.retainAll(existingKeywords);

            if (intersection.size() >= 2) {
                String firstSharedWord = intersection.iterator().next();
                return ConflictDetectionResult.conflict(
                        newMemory.getId(),
                        existingMemory.getId(),
                        firstSharedWord,
                        truncate(existingText, 60),
                        truncate(newText, 60)
                );
            }
        }

        return ConflictDetectionResult.noConflict(newMemory.getId());
    }

    /**
     * Extracts normalized keyword tokens from text.
     *
     * <p>Normalizes input to lowercase, splits on non-alphanumeric characters, and keeps
     * words of length 4 or greater.</p>
     *
     * @param text the raw memory text; may be null
     * @return set of unique keyword tokens; never null
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        if (text == null || text.trim().isEmpty()) {
            return keywords;
        }

        String[] tokens = text.toLowerCase().split("[^a-zA-Z0-9]+");
        for (String token : tokens) {
            if (token != null && token.length() >= 4) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * Truncates a string to the given maximum length, appending an ellipsis if truncated.
     *
     * @param s      the string to truncate; may be null
     * @param maxLen the maximum length before truncation
     * @return truncated string with an ellipsis, or the original string
     */
    private String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
