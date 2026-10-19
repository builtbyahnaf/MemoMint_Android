package dev.builtbyahnaf.memomint.domain.file.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable container encapsulating text and structural metadata extracted from a document.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.2)</h2>
 * <ul>
 *   <li><b>Memory Safety:</b> Informs callers whether text content was truncated to prevent heap exhaustion.</li>
 *   <li><b>Honest State Representation:</b> Distinguishes between documents with valid 0-length text (empty file)
 *       and scanned documents lacking an embedded text stream ({@link #isScannedNeedsOcr()}).</li>
 *   <li><b>Zero Fabrication:</b> Contains only real characters and structural properties extracted from the stream.</li>
 * </ul>
 */
public final class ParseResult {

    private final String extractedText;
    private final int characterCount;
    private final int pageCount;
    private final boolean truncated;
    private final boolean scannedNeedsOcr;
    private final Map<String, String> documentMetadata;

    public ParseResult(
            String extractedText,
            int pageCount,
            boolean truncated,
            boolean scannedNeedsOcr,
            Map<String, String> documentMetadata
    ) {
        this.extractedText = (extractedText != null) ? extractedText : "";
        this.characterCount = this.extractedText.length();
        this.pageCount = Math.max(1, pageCount);
        this.truncated = truncated;
        this.scannedNeedsOcr = scannedNeedsOcr;
        this.documentMetadata = (documentMetadata != null)
                ? Collections.unmodifiableMap(new HashMap<>(documentMetadata))
                : Collections.emptyMap();
    }

    public ParseResult(
            String extractedText,
            int pageCount,
            boolean truncated,
            Map<String, String> documentMetadata
    ) {
        this(extractedText, pageCount, truncated, false, documentMetadata);
    }

    public static ParseResult empty() {
        return new ParseResult("", 1, false, false, Collections.emptyMap());
    }

    public static ParseResult scannedNeedsOcr(int pageCount, Map<String, String> metadata) {
        return new ParseResult("", pageCount, false, true, metadata);
    }

    public String getExtractedText() {
        return extractedText;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public boolean isScannedNeedsOcr() {
        return scannedNeedsOcr;
    }

    public Map<String, String> getDocumentMetadata() {
        return documentMetadata;
    }

    @Override
    public String toString() {
        return "ParseResult{"
                + "chars=" + characterCount
                + ", pages=" + pageCount
                + ", truncated=" + truncated
                + ", scannedNeedsOcr=" + scannedNeedsOcr
                + '}';
    }
}
