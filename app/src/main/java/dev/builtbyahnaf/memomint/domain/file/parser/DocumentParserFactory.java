package dev.builtbyahnaf.memomint.domain.file.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Factory and registry resolving appropriate {@link DocumentParser} implementations
 * based on MIME type and file extension.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §3, §6.2)</h2>
 * <ul>
 *   <li><b>Order of Evaluation:</b> Evaluates specialized parsers (PDF, DOCX) first,
 *       falling back to {@link PlainTextParser} for text-based or unknown formats.</li>
 *   <li><b>Extensibility:</b> Allows registering additional custom parsers without modifying
 *       calling service layers.</li>
 * </ul>
 */
public class DocumentParserFactory {

    private static volatile DocumentParserFactory sInstance;

    private final List<DocumentParser> parsers;
    private final PlainTextParser defaultParser;

    public static DocumentParserFactory getInstance() {
        if (sInstance == null) {
            synchronized (DocumentParserFactory.class) {
                if (sInstance == null) {
                    sInstance = new DocumentParserFactory();
                }
            }
        }
        return sInstance;
    }

    public DocumentParserFactory() {
        this.parsers = new ArrayList<>();
        this.defaultParser = new PlainTextParser();

        // Register specialized parsers first
        this.parsers.add(new PdfStreamTextParser());
        this.parsers.add(new DocxSaxParser());
        this.parsers.add(this.defaultParser);
    }

    public DocumentParserFactory(List<DocumentParser> customParsers, PlainTextParser fallbackParser) {
        this.parsers = new ArrayList<>(customParsers != null ? customParsers : Collections.emptyList());
        this.defaultParser = (fallbackParser != null) ? fallbackParser : new PlainTextParser();
        if (!this.parsers.contains(this.defaultParser)) {
            this.parsers.add(this.defaultParser);
        }
    }

    /**
     * Resolves the best matching parser for the given MIME type and file extension.
     *
     * @param mimeType  document MIME type (e.g. "application/pdf")
     * @param extension document file extension without leading dot (e.g. "docx")
     * @return matching DocumentParser, or default PlainTextParser if no specific match
     */
    public DocumentParser getParser(String mimeType, String extension) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(mimeType, extension)) {
                return parser;
            }
        }
        return defaultParser;
    }
}
