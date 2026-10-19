package dev.builtbyahnaf.memomint.domain.file.parser;

/**
 * Checked exception thrown during document text parsing failures.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.2)</h2>
 * <p>Enforces explicit error classification by associating each parse exception with a
 * standardized {@link dev.builtbyahnaf.memomint.domain.model.IndexErrorCode}. This enables
 * higher-level indexing services to distinguish between recoverable errors (e.g., scanned PDF)
 * and hard exclusions (e.g., encrypted file).</p>
 */
public class DocumentParseException extends Exception {

    private final String errorCode;

    public DocumentParseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentParseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * @return one of the standardized error codes from {@link dev.builtbyahnaf.memomint.domain.model.IndexErrorCode}
     */
    public String getErrorCode() {
        return errorCode;
    }
}
