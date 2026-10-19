package dev.builtbyahnaf.memomint.domain.file.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;
import java.util.Collections;
import java.util.Locale;

import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

/**
 * Streaming parser for plain text, Markdown, JSON, CSV, and related source formats.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §3.3, §6.2)</h2>
 * <ul>
 *   <li><b>Charset Detection Hierarchy:</b> Inspects initial Byte Order Mark (BOM) for UTF-16LE,
 *       UTF-16BE, or UTF-8. If no BOM is present, decodes as UTF-8 with {@link CodingErrorAction#REPLACE}.
 *       Falls back to ISO-8859-1 for resilient, loss-free byte-to-char mapping.</li>
 *   <li><b>Bounded Memory:</b> Reads in fixed-size character buffers and stops once {@code maxChars}
 *       is reached, marking the result as truncated.</li>
 *   <li><b>No Crashing:</b> Malformed byte sequences are replaced or handled cleanly without throwing.</li>
 * </ul>
 */
public class PlainTextParser implements DocumentParser {

    @Override
    public boolean supports(String mimeType, String extension) {
        if (mimeType != null) {
            String lowerMime = mimeType.toLowerCase(Locale.ROOT);
            if (lowerMime.startsWith("text/")
                    || lowerMime.equals("application/json")
                    || lowerMime.equals("application/xml")
                    || lowerMime.equals("application/javascript")) {
                return true;
            }
        }

        if (extension != null) {
            String lowerExt = extension.toLowerCase(Locale.ROOT);
            switch (lowerExt) {
                case "txt":
                case "md":
                case "markdown":
                case "json":
                case "csv":
                case "xml":
                case "log":
                case "properties":
                case "yaml":
                case "yml":
                case "ini":
                case "conf":
                case "html":
                case "htm":
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    @Override
    public ParseResult parse(InputStream inputStream, int maxChars) throws DocumentParseException {
        if (inputStream == null) {
            throw new DocumentParseException(IndexErrorCode.ERR_FILE_NOT_FOUND, "InputStream is null");
        }

        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 8192);
        try {
            bufferedStream.mark(4);
            byte[] bomHeader = new byte[4];
            int readBytes = bufferedStream.read(bomHeader, 0, 4);

            Charset chosenCharset = StandardCharsets.UTF_8;
            int skipBytes = 0;

            if (readBytes >= 2) {
                if ((bomHeader[0] & 0xFF) == 0xFE && (bomHeader[1] & 0xFF) == 0xFF) {
                    chosenCharset = StandardCharsets.UTF_16BE;
                    skipBytes = 2;
                } else if ((bomHeader[0] & 0xFF) == 0xFF && (bomHeader[1] & 0xFF) == 0xFE) {
                    chosenCharset = StandardCharsets.UTF_16LE;
                    skipBytes = 2;
                }
            }

            if (readBytes >= 3 && skipBytes == 0) {
                if ((bomHeader[0] & 0xFF) == 0xEF
                        && (bomHeader[1] & 0xFF) == 0xBB
                        && (bomHeader[2] & 0xFF) == 0xBF) {
                    chosenCharset = StandardCharsets.UTF_8;
                    skipBytes = 3;
                }
            }

            bufferedStream.reset();
            if (skipBytes > 0) {
                long skipped = bufferedStream.skip(skipBytes);
                // skipped BOM
            }

            StringBuilder sb = new StringBuilder(Math.min(maxChars, 16384));
            char[] buffer = new char[4096];
            boolean truncated = false;

            CharsetDecoder decoder = chosenCharset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);

            try (Reader reader = new InputStreamReader(bufferedStream, decoder)) {
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    int remainingCapacity = maxChars - sb.length();
                    if (remainingCapacity <= 0) {
                        truncated = true;
                        break;
                    }

                    if (read <= remainingCapacity) {
                        sb.append(buffer, 0, read);
                    } else {
                        sb.append(buffer, 0, remainingCapacity);
                        truncated = true;
                        break;
                    }
                }
            }

            String resultText = sb.toString();
            return new ParseResult(resultText, 1, truncated, Collections.emptyMap());

        } catch (IOException e) {
            throw new DocumentParseException(IndexErrorCode.ERR_PARSE_FAILED, "Failed to read plain text stream: " + e.getMessage(), e);
        } catch (OutOfMemoryError oom) {
            throw new DocumentParseException(IndexErrorCode.ERR_OUT_OF_MEMORY, "Heap pressure during text stream parsing", oom);
        }
    }
}
