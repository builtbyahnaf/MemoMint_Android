package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.builtbyahnaf.memomint.domain.file.parser.DocxSaxParser;
import dev.builtbyahnaf.memomint.domain.file.parser.DocumentParseException;
import dev.builtbyahnaf.memomint.domain.file.parser.ParseResult;
import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Essential unit tests for {@link DocxSaxParser}.
 */
public class DocxParserTest {

    private DocxSaxParser parser;

    @Before
    public void setUp() {
        parser = new DocxSaxParser();
    }

    @Test
    public void testSupportsDocx() {
        assertTrue(parser.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"));
        assertTrue(parser.supports(null, "docx"));
    }

    @Test
    public void testParsesStandardDocxFixture() throws Exception {
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n"
                + "  <w:body>\n"
                + "    <w:p><w:r><w:t>Guitar price comparison: Fender Stratocaster $799.</w:t></w:r></w:p>\n"
                + "    <w:p><w:r><w:t>Gibson Les Paul $1299.</w:t></w:r></w:p>\n"
                + "  </w:body>\n"
                + "</w:document>";

        byte[] docxBytes = createMockDocx(xmlContent);
        ParseResult result = parser.parse(new ByteArrayInputStream(docxBytes), 5000);

        assertNotNull(result);
        String text = result.getExtractedText();
        assertTrue(text.contains("Guitar price comparison: Fender Stratocaster $799."));
        assertTrue(text.contains("Gibson Les Paul $1299."));
    }

    @Test
    public void testThrowsOnCorruptZipArchive() {
        byte[] invalidBytes = "This is not a zip archive".getBytes(StandardCharsets.UTF_8);
        try {
            parser.parse(new ByteArrayInputStream(invalidBytes), 1000);
            fail("Expected DocumentParseException for corrupt archive");
        } catch (DocumentParseException e) {
            assertEquals(IndexErrorCode.ERR_DOCX_CORRUPT, e.getErrorCode());
        }
    }

    @Test
    public void testThrowsOnMissingDocumentXml() throws Exception {
        // Create zip with unrelated entry
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("some_other_file.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        try {
            parser.parse(new ByteArrayInputStream(baos.toByteArray()), 1000);
            fail("Expected DocumentParseException for missing document.xml");
        } catch (DocumentParseException e) {
            assertEquals(IndexErrorCode.ERR_DOCX_CORRUPT, e.getErrorCode());
        }
    }

    private static byte[] createMockDocx(String documentXml) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("word/document.xml");
            zos.putNextEntry(entry);
            zos.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
