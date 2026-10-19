package dev.builtbyahnaf.memomint.domain.file.parser;

import java.io.InputStream;

/**
 * Common contract for streaming document text extractors.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.2)</h2>
 * <ul>
 *   <li><b>Streaming I/O:</b> Parsers must operate incrementally over an {@link InputStream} rather than
 *       reading full documents into heap memory at once.</li>
 *   <li><b>Bounded Execution:</b> Must respect {@code maxChars} upper bound to prevent heap exhaustion (NFR-08).</li>
 *   <li><b>Explicit Error Propagation:</b> Catastrophic failures must throw {@link DocumentParseException}
 *       with an appropriate error code from {@link dev.builtbyahnaf.memomint.domain.model.IndexErrorCode}.</li>
 * </ul>
 */
public interface DocumentParser {

    /**
     * Checks whether this parser is capable of processing the given MIME type or file extension.
     *
     * @param mimeType  MIME type string (e.g. "application/pdf", "text/plain"), may be null
     * @param extension file extension without dot (e.g. "pdf", "docx", "txt"), may be null
     * @return true if this parser can handle the document
     */
    boolean supports(String mimeType, String extension);

    /**
     * Parses text content from the provided input stream.
     *
     * @param inputStream open stream to the document (caller is responsible for closing, or parser closes after reading)
     * @param maxChars    maximum number of characters to extract before truncation
     * @return extraction result containing text, metadata, page count, and status
     * @throws DocumentParseException on fatal, non-recoverable parse failure
     */
    ParseResult parse(InputStream inputStream, int maxChars) throws DocumentParseException;
}
