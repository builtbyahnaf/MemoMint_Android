package dev.builtbyahnaf.memomint.domain.file.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

/**
 * Lightweight, zero-dependency streaming PDF text extractor.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §3.1, §6.2)</h2>
 * <ul>
 *   <li><b>Pure Java Implementation:</b> Extracts textual content from page streams and deflated
 *       stream objects without external native or heavy JAR dependencies (PDFBox/iText).</li>
 *   <li><b>Encryption Awareness:</b> Detects {@code /Encrypt} dictionaries in PDF headers/trailers
 *       and throws {@link DocumentParseException} with {@link IndexErrorCode#ERR_PDF_ENCRYPTED}.</li>
 *   <li><b>Scanned Document Detection:</b> Accurately identifies image-only PDFs with 0 extractable
 *       characters and flags {@link ParseResult#isScannedNeedsOcr()} without fabricating content (FR-08).</li>
 *   <li><b>Operator Parsing:</b> Parses standard PDF text drawing operators: {@code Tj}, {@code TJ},
 *       {@code '}, {@code "}, with literal escape and hex string decoding.</li>
 * </ul>
 */
public class PdfStreamTextParser implements DocumentParser {

    private static final String PDF_MIME = "application/pdf";
    private static final Pattern ENCRYPT_PATTERN = Pattern.compile("/Encrypt\\b");
    private static final Pattern PAGE_COUNT_PATTERN = Pattern.compile("/Type\\s*/Page\\b");
    private static final Pattern STREAM_PATTERN = Pattern.compile("stream[\\r\\n]+([\\s\\S]*?)[\\r\\n]+endstream");

    @Override
    public boolean supports(String mimeType, String extension) {
        if (mimeType != null && mimeType.equalsIgnoreCase(PDF_MIME)) {
            return true;
        }
        return extension != null && extension.equalsIgnoreCase("pdf");
    }

    @Override
    public ParseResult parse(InputStream inputStream, int maxChars) throws DocumentParseException {
        if (inputStream == null) {
            throw new DocumentParseException(IndexErrorCode.ERR_FILE_NOT_FOUND, "InputStream is null");
        }

        byte[] rawBytes;
        try {
            rawBytes = readStreamToByteArray(inputStream);
        } catch (IOException e) {
            throw new DocumentParseException(IndexErrorCode.ERR_PARSE_FAILED, "Failed to read PDF stream: " + e.getMessage(), e);
        } catch (OutOfMemoryError oom) {
            throw new DocumentParseException(IndexErrorCode.ERR_OUT_OF_MEMORY, "OOM while buffering PDF stream", oom);
        }

        if (rawBytes.length == 0) {
            throw new DocumentParseException(IndexErrorCode.ERR_EMPTY_FILE, "PDF file is 0 bytes");
        }

        // Check PDF header signature
        String headerAscii = new String(rawBytes, 0, Math.min(rawBytes.length, 1024), StandardCharsets.ISO_8859_1);
        if (!headerAscii.contains("%PDF-")) {
            throw new DocumentParseException(IndexErrorCode.ERR_PARSE_FAILED, "Not a valid PDF file (missing %PDF- header)");
        }

        // 1. Check for Encryption
        // Scan trailer and object dictionaries for /Encrypt
        String asciiStructure = new String(rawBytes, StandardCharsets.ISO_8859_1);
        if (ENCRYPT_PATTERN.matcher(asciiStructure).find()) {
            throw new DocumentParseException(IndexErrorCode.ERR_PDF_ENCRYPTED, "PDF document is encrypted or password-protected");
        }

        // 2. Estimate page count
        int pageCount = 0;
        Matcher pageMatcher = PAGE_COUNT_PATTERN.matcher(asciiStructure);
        while (pageMatcher.find()) {
            pageCount++;
        }
        if (pageCount == 0) {
            pageCount = 1;
        }

        // 3. Extract text from content streams
        StringBuilder sb = new StringBuilder(Math.min(maxChars, 16384));
        boolean truncated = false;

        try {
            truncated = extractStreams(rawBytes, sb, maxChars);
        } catch (OutOfMemoryError oom) {
            throw new DocumentParseException(IndexErrorCode.ERR_OUT_OF_MEMORY, "OOM while decompressing PDF text streams", oom);
        }

        String extracted = sb.toString().trim();

        // 4. Scanned PDF detection
        // If 0 printable letters/digits extracted across >= 1 page, flag as scanned
        boolean hasPrintableChars = false;
        for (int i = 0; i < extracted.length(); i++) {
            if (Character.isLetterOrDigit(extracted.charAt(i))) {
                hasPrintableChars = true;
                break;
            }
        }

        Map<String, String> metadata = new HashMap<>();
        metadata.put("pageCount", String.valueOf(pageCount));

        if (!hasPrintableChars) {
            return ParseResult.scannedNeedsOcr(pageCount, metadata);
        }

        return new ParseResult(extracted, pageCount, truncated, false, metadata);
    }

    private boolean extractStreams(byte[] rawBytes, StringBuilder sb, int maxChars) {
        int index = 0;
        byte[] streamMarker = "stream".getBytes(StandardCharsets.ISO_8859_1);
        byte[] endstreamMarker = "endstream".getBytes(StandardCharsets.ISO_8859_1);

        while (index < rawBytes.length) {
            int streamStart = indexOfBytes(rawBytes, streamMarker, index);
            if (streamStart == -1) break;

            // Move past "stream" and following \r?\n
            int dataStart = streamStart + streamMarker.length;
            if (dataStart < rawBytes.length && rawBytes[dataStart] == '\r') dataStart++;
            if (dataStart < rawBytes.length && rawBytes[dataStart] == '\n') dataStart++;

            int streamEnd = indexOfBytes(rawBytes, endstreamMarker, dataStart);
            if (streamEnd == -1) break;

            // Trim trailing \r or \n before endstream
            int dataEnd = streamEnd;
            if (dataEnd > dataStart && rawBytes[dataEnd - 1] == '\n') dataEnd--;
            if (dataEnd > dataStart && rawBytes[dataEnd - 1] == '\r') dataEnd--;

            int streamLength = dataEnd - dataStart;
            if (streamLength > 0) {
                // Check if preceding dictionary contains /FlateDecode
                int dictStart = Math.max(0, streamStart - 400);
                String dictContext = new String(rawBytes, dictStart, streamStart - dictStart, StandardCharsets.ISO_8859_1);
                boolean isFlate = dictContext.contains("/FlateDecode");

                byte[] decompressed = null;
                if (isFlate) {
                    decompressed = inflateBytes(rawBytes, dataStart, streamLength);
                } else {
                    decompressed = new byte[streamLength];
                    System.arraycopy(rawBytes, dataStart, decompressed, 0, streamLength);
                }

                if (decompressed != null && decompressed.length > 0) {
                    boolean reachedLimit = parseTextFromContentStream(decompressed, sb, maxChars);
                    if (reachedLimit) {
                        return true;
                    }
                }
            }

            index = streamEnd + endstreamMarker.length;
        }

        return false;
    }

    private byte[] inflateBytes(byte[] source, int offset, int length) {
        Inflater inflater = new Inflater(false);
        try {
            inflater.setInput(source, offset, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(length * 4, 65536));
            byte[] buf = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                out.write(buf, 0, count);
                if (out.size() > 2 * 1024 * 1024) { // 2MB max per stream
                    break;
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            // Deflate decompression failed or stream wasn't flate; try raw
            return null;
        } finally {
            inflater.end();
        }
    }

    private boolean parseTextFromContentStream(byte[] contentBytes, StringBuilder sb, int maxChars) {
        String content = new String(contentBytes, StandardCharsets.ISO_8859_1);
        int len = content.length();
        int i = 0;

        while (i < len) {
            if (sb.length() >= maxChars) {
                return true;
            }

            char c = content.charAt(i);

            // 1. Literal string inside parentheses: (...) Tj or part of [...] TJ
            if (c == '(') {
                int start = i + 1;
                int depth = 1;
                StringBuilder literal = new StringBuilder();
                i++;
                while (i < len && depth > 0) {
                    char ch = content.charAt(i);
                    if (ch == '\\' && i + 1 < len) {
                        char nextCh = content.charAt(i + 1);
                        switch (nextCh) {
                            case 'n': literal.append('\n'); i += 2; continue;
                            case 'r': literal.append('\r'); i += 2; continue;
                            case 't': literal.append('\t'); i += 2; continue;
                            case 'b': literal.append('\b'); i += 2; continue;
                            case 'f': literal.append('\f'); i += 2; continue;
                            case '(': literal.append('('); i += 2; continue;
                            case ')': literal.append(')'); i += 2; continue;
                            case '\\': literal.append('\\'); i += 2; continue;
                            default:
                                if (nextCh >= '0' && nextCh <= '7') {
                                    // Octal escape
                                    int octalVal = nextCh - '0';
                                    i += 2;
                                    if (i < len && content.charAt(i) >= '0' && content.charAt(i) <= '7') {
                                        octalVal = octalVal * 8 + (content.charAt(i) - '0');
                                        i++;
                                    }
                                    if (i < len && content.charAt(i) >= '0' && content.charAt(i) <= '7') {
                                        octalVal = octalVal * 8 + (content.charAt(i) - '0');
                                        i++;
                                    }
                                    literal.append((char) octalVal);
                                    continue;
                                }
                                literal.append(nextCh);
                                i += 2;
                                continue;
                        }
                    } else if (ch == '(') {
                        depth++;
                        literal.append(ch);
                    } else if (ch == ')') {
                        depth--;
                        if (depth > 0) {
                            literal.append(ch);
                        }
                    } else {
                        literal.append(ch);
                    }
                    i++;
                }

                // Check following operator
                int nextOpIndex = skipWhitespace(content, i);
                if (nextOpIndex < len) {
                    if (content.startsWith("Tj", nextOpIndex)
                            || content.charAt(nextOpIndex) == '\''
                            || content.charAt(nextOpIndex) == '"') {
                        appendText(sb, literal.toString(), maxChars);
                        if (content.charAt(nextOpIndex) == '\'' || content.charAt(nextOpIndex) == '"') {
                            sb.append('\n');
                        }
                    } else {
                        // Might be part of an array TJ
                        appendText(sb, literal.toString(), maxChars);
                    }
                }
                continue;
            }

            // 2. Hex string: <48656C6C6F>
            if (c == '<' && i + 1 < len && content.charAt(i + 1) != '<') {
                int start = i + 1;
                int end = content.indexOf('>', start);
                if (end != -1) {
                    String hex = content.substring(start, end).replaceAll("\\s+", "");
                    String decoded = decodeHexAscii(hex);
                    if (!decoded.isEmpty()) {
                        appendText(sb, decoded, maxChars);
                    }
                    i = end + 1;
                    continue;
                }
            }

            // 3. Spacing / Newline operators: T* or TD
            if (c == 'T' && i + 1 < len) {
                char next = content.charAt(i + 1);
                if (next == '*' || next == 'D') {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n' && sb.charAt(sb.length() - 1) != ' ') {
                        sb.append(' ');
                    }
                    i += 2;
                    continue;
                }
            }

            i++;
        }

        return sb.length() >= maxChars;
    }

    private void appendText(StringBuilder sb, String text, int maxChars) {
        if (text == null || text.isEmpty()) return;
        int capacity = maxChars - sb.length();
        if (capacity <= 0) return;

        if (text.length() <= capacity) {
            sb.append(text);
        } else {
            sb.append(text, 0, capacity);
        }
    }

    private static int skipWhitespace(String str, int index) {
        while (index < str.length() && Character.isWhitespace(str.charAt(index))) {
            index++;
        }
        return index;
    }

    private static String decodeHexAscii(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length() - 1; i += 2) {
            try {
                int val = Integer.parseInt(hex.substring(i, i + 2), 16);
                if (val >= 32 && val <= 126) {
                    sb.append((char) val);
                } else if (val == '\n' || val == '\r' || val == '\t') {
                    sb.append(' ');
                }
            } catch (NumberFormatException ignored) {}
        }
        return sb.toString();
    }

    private static int indexOfBytes(byte[] source, byte[] target, int fromIndex) {
        if (fromIndex >= source.length || target.length == 0) return -1;
        for (int i = fromIndex; i <= source.length - target.length; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static byte[] readStreamToByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(32768);
        byte[] temp = new byte[8192];
        int read;
        while ((read = in.read(temp)) != -1) {
            buffer.write(temp, 0, read);
        }
        return buffer.toByteArray();
    }
}
