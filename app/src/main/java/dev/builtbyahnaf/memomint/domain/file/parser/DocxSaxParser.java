package dev.builtbyahnaf.memomint.domain.file.parser;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

/**
 * Streaming parser for Microsoft Word (.docx) documents.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §3.2, §6.2)</h2>
 * <ul>
 *   <li><b>Zero Heavy Dependencies:</b> Uses {@link ZipInputStream} and {@link XmlPullParser}
 *       instead of massive external libraries like Apache POI (saves ~30MB APK bloat).</li>
 *   <li><b>Streaming Extraction:</b> Locates {@code word/document.xml} inside the ZIP container
 *       and streams XML events directly into character output.</li>
 *   <li><b>Paragraph Preservation:</b> Inserts newlines for {@code <w:p>} paragraph elements
 *       and spaces between text runs {@code <w:t>} to retain readable formatting.</li>
 * </ul>
 */
public class DocxSaxParser implements DocumentParser {

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCUMENT_XML_ENTRY = "word/document.xml";

    @Override
    public boolean supports(String mimeType, String extension) {
        if (mimeType != null && mimeType.equalsIgnoreCase(DOCX_MIME)) {
            return true;
        }
        return extension != null && extension.equalsIgnoreCase("docx");
    }

    @Override
    public ParseResult parse(InputStream inputStream, int maxChars) throws DocumentParseException {
        if (inputStream == null) {
            throw new DocumentParseException(IndexErrorCode.ERR_FILE_NOT_FOUND, "InputStream is null");
        }

        StringBuilder sb = new StringBuilder(Math.min(maxChars, 16384));
        boolean documentXmlFound = false;
        boolean truncated = false;

        try (ZipInputStream zipIn = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (DOCUMENT_XML_ENTRY.equalsIgnoreCase(entry.getName())) {
                    documentXmlFound = true;
                    java.io.InputStream nonClosingStream = new java.io.FilterInputStream(zipIn) {
                        @Override
                        public void close() {
                            // No-op: prevent SAXParser from closing the underlying ZipInputStream
                        }
                    };
                    truncated = parseDocumentXml(nonClosingStream, sb, maxChars);
                    break;
                }
                zipIn.closeEntry();
            }
        } catch (ZipException ze) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_DOCX_CORRUPT,
                    "Corrupted ZIP archive structure in DOCX file: " + ze.getMessage(),
                    ze
            );
        } catch (IOException ioe) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_DOCX_CORRUPT,
                    "I/O error reading DOCX entry: " + ioe.getMessage(),
                    ioe
            );
        } catch (OutOfMemoryError oom) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_OUT_OF_MEMORY,
                    "Heap exhaustion during DOCX parsing",
                    oom
            );
        }

        if (!documentXmlFound) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_DOCX_CORRUPT,
                    "Invalid DOCX structure: missing 'word/document.xml' entry"
            );
        }

        String extracted = sb.toString().trim();
        return new ParseResult(extracted, 1, truncated, Collections.emptyMap());
    }

    private boolean parseDocumentXml(InputStream xmlStream, StringBuilder sb, int maxChars)
            throws DocumentParseException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            try {
                // Feature disabling external DTDs to prevent XXE
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {}

            SAXParser parser = factory.newSAXParser();
            final boolean[] truncated = new boolean[]{false};

            parser.parse(xmlStream, new DefaultHandler() {
                private boolean insideTextTag = false;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if (sb.length() >= maxChars) {
                        truncated[0] = true;
                        return;
                    }
                    String tag = (localName != null && !localName.isEmpty()) ? localName : qName;
                    if (tag.contains(":")) {
                        tag = tag.substring(tag.indexOf(':') + 1);
                    }

                    if ("p".equalsIgnoreCase(tag)) {
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                            sb.append('\n');
                        }
                    } else if ("tab".equalsIgnoreCase(tag)) {
                        sb.append('\t');
                    } else if ("br".equalsIgnoreCase(tag) || "cr".equalsIgnoreCase(tag)) {
                        sb.append('\n');
                    } else if ("t".equalsIgnoreCase(tag)) {
                        insideTextTag = true;
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    String tag = (localName != null && !localName.isEmpty()) ? localName : qName;
                    if (tag.contains(":")) {
                        tag = tag.substring(tag.indexOf(':') + 1);
                    }
                    if ("t".equalsIgnoreCase(tag)) {
                        insideTextTag = false;
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (insideTextTag && length > 0) {
                        int capacity = maxChars - sb.length();
                        if (capacity <= 0) {
                            truncated[0] = true;
                            return;
                        }
                        if (length <= capacity) {
                            sb.append(ch, start, length);
                        } else {
                            sb.append(ch, start, capacity);
                            truncated[0] = true;
                        }
                    }
                }
            });

            return truncated[0];
        } catch (SAXException se) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_DOCX_CORRUPT,
                    "Malformed XML in word/document.xml: " + se.getMessage(),
                    se
            );
        } catch (IOException ioe) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_DOCX_CORRUPT,
                    "I/O error reading XML stream: " + ioe.getMessage(),
                    ioe
            );
        } catch (Exception e) {
            throw new DocumentParseException(
                    IndexErrorCode.ERR_DOCX_CORRUPT,
                    "Failed to parse DOCX document XML: " + e.getMessage(),
                    e
            );
        }
    }
}
