package com.guimaraes.TalentPulseAI.service;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class ResumePdfTextExtractor {

    public String extractText(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                throw new IOException("PDF contains no extractable text");
            }
            return text;
        }
    }
}
