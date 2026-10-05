package com.guimaraes.TalentPulseAI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResumePdfTextExtractorTests {

    @TempDir
    Path tempDirectory;

    private final ResumePdfTextExtractor extractor = new ResumePdfTextExtractor();

    @Test
    void extractsTextFromPdf() throws Exception {
        Path pdfPath = tempDirectory.resolve("resume.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Experienced Java engineer");
                content.endText();
            }
            document.save(pdfPath.toFile());
        }

        assertEquals("Experienced Java engineer", extractor.extractText(pdfPath).trim());
    }

    @Test
    void rejectsPdfWithoutExtractableText() throws Exception {
        Path pdfPath = tempDirectory.resolve("empty.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdfPath.toFile());
        }

        IOException exception = assertThrows(IOException.class, () -> extractor.extractText(pdfPath));

        assertEquals("PDF contains no extractable text", exception.getMessage());
    }
}
