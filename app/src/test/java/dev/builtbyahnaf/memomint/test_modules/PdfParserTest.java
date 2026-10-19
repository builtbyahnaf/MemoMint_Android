package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import dev.builtbyahnaf.memomint.domain.file.parser.DocumentParseException;
import dev.builtbyahnaf.memomint.domain.file.parser.ParseResult;
import dev.builtbyahnaf.memomint.domain.file.parser.PdfStreamTextParser;
import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Essential unit tests for {@link PdfStreamTextParser}.
 */
public class PdfParserTest {

    private PdfStreamTextParser parser;

    @Before
    public void setUp() {
        parser = new PdfStreamTextParser();
    }

    @Test
    public void testSupportsPdf() {
        assertTrue(parser.supports("application/pdf", "pdf"));
        assertTrue(parser.supports(null, "pdf"));
    }

    @Test
    public void testExtractsTextFromPdfStream() throws DocumentParseException {
        String mockPdf = "%PDF-1.4\n"
                + "1 0 obj\n"
                + "<< /Type /Catalog /Pages 2 0 R >>\n"
                + "endobj\n"
                + "2 0 obj\n"
                + "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n"
                + "endobj\n"
                + "3 0 obj\n"
                + "<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\n"
                + "endobj\n"
                + "4 0 obj\n"
                + "<< /Length 45 >>\n"
                + "stream\n"
                + "BT\n"
                + "/F1 12 Tf\n"
                + "(Dishwasher user manual and warranty details) Tj\n"
                + "ET\n"
                + "endstream\n"
                + "endobj\n"
                + "trailer\n"
                + "<< /Root 1 0 R >>\n"
                + "%%EOF\n";

        ParseResult result = parser.parse(new ByteArrayInputStream(mockPdf.getBytes(StandardCharsets.ISO_8859_1)), 2000);

        assertNotNull(result);
        assertTrue(result.getExtractedText().contains("Dishwasher user manual and warranty details"));
        assertFalse(result.isScannedNeedsOcr());
    }

    @Test
    public void testDetectsEncryptedPdf() {
        String encryptedPdf = "%PDF-1.4\n"
                + "1 0 obj\n"
                + "<< /Type /Catalog /Pages 2 0 R >>\n"
                + "endobj\n"
                + "trailer\n"
                + "<< /Root 1 0 R /Encrypt 5 0 R >>\n"
                + "%%EOF\n";

        try {
            parser.parse(new ByteArrayInputStream(encryptedPdf.getBytes(StandardCharsets.ISO_8859_1)), 1000);
            fail("Expected DocumentParseException for encrypted PDF");
        } catch (DocumentParseException e) {
            assertEquals(IndexErrorCode.ERR_PDF_ENCRYPTED, e.getErrorCode());
        }
    }

    @Test
    public void testDetectsScannedImageOnlyPdf() throws DocumentParseException {
        // PDF with a Page object but 0 text streams (only an image XObject)
        String scannedPdf = "%PDF-1.4\n"
                + "1 0 obj\n"
                + "<< /Type /Catalog /Pages 2 0 R >>\n"
                + "endobj\n"
                + "2 0 obj\n"
                + "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n"
                + "endobj\n"
                + "3 0 obj\n"
                + "<< /Type /Page /Parent 2 0 R >>\n"
                + "endobj\n"
                + "trailer\n"
                + "<< /Root 1 0 R >>\n"
                + "%%EOF\n";

        ParseResult result = parser.parse(new ByteArrayInputStream(scannedPdf.getBytes(StandardCharsets.ISO_8859_1)), 1000);

        assertNotNull(result);
        assertTrue(result.isScannedNeedsOcr());
        assertEquals("", result.getExtractedText());
    }
}
