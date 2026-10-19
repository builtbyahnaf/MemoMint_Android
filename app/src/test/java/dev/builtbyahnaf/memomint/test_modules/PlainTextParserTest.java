package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import dev.builtbyahnaf.memomint.domain.file.parser.DocumentParseException;
import dev.builtbyahnaf.memomint.domain.file.parser.ParseResult;
import dev.builtbyahnaf.memomint.domain.file.parser.PlainTextParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Essential unit tests for {@link PlainTextParser}.
 */
public class PlainTextParserTest {

    private PlainTextParser parser;

    @Before
    public void setUp() {
        parser = new PlainTextParser();
    }

    @Test
    public void testSupportsTextAndMarkdown() {
        assertTrue(parser.supports("text/plain", "txt"));
        assertTrue(parser.supports("text/markdown", "md"));
        assertTrue(parser.supports("application/json", "json"));
        assertTrue(parser.supports(null, "csv"));
        assertFalse(parser.supports("application/pdf", "pdf"));
    }

    @Test
    public void testExtractsPlainAsciiText() throws DocumentParseException {
        String content = "Hello MemoMint! This is a simple note.";
        InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parser.parse(in, 1000);

        assertEquals(content, result.getExtractedText());
        assertFalse(result.isTruncated());
        assertFalse(result.isScannedNeedsOcr());
    }

    @Test
    public void testExtractsUtf8WithAccents() throws DocumentParseException {
        String content = "Café résumé señorita 🚀";
        InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parser.parse(in, 1000);

        assertEquals(content, result.getExtractedText());
    }

    @Test
    public void testHandlesUtf16LeBom() throws DocumentParseException {
        String content = "UTF-16LE text content";
        byte[] raw = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[raw.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(raw, 0, withBom, 2, raw.length);

        ParseResult result = parser.parse(new ByteArrayInputStream(withBom), 1000);
        assertEquals(content, result.getExtractedText());
    }

    @Test
    public void testHandlesUtf16BeBom() throws DocumentParseException {
        String content = "UTF-16BE text content";
        byte[] raw = content.getBytes(StandardCharsets.UTF_16BE);
        byte[] withBom = new byte[raw.length + 2];
        withBom[0] = (byte) 0xFE;
        withBom[1] = (byte) 0xFF;
        System.arraycopy(raw, 0, withBom, 2, raw.length);

        ParseResult result = parser.parse(new ByteArrayInputStream(withBom), 1000);
        assertEquals(content, result.getExtractedText());
    }

    @Test
    public void testTruncatesAtMaxChars() throws DocumentParseException {
        String content = "12345678901234567890";
        InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        ParseResult result = parser.parse(in, 10);

        assertEquals(10, result.getCharacterCount());
        assertEquals("1234567890", result.getExtractedText());
        assertTrue(result.isTruncated());
    }

    @Test
    public void testEmptyFileReturnsEmptyResult() throws DocumentParseException {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ParseResult result = parser.parse(in, 1000);

        assertEquals("", result.getExtractedText());
        assertEquals(0, result.getCharacterCount());
        assertFalse(result.isTruncated());
    }
}
