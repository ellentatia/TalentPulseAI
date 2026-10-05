package com.guimaraes.TalentPulseAI.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.service.ResumePdfTextExtractor;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.core.io.FileSystemResource;

class ResumeBatchConfigTests {

    @TempDir
    Path tempDirectory;

    @Test
    void processorUsesPdfTextExtractorToPopulateCandidate() throws Exception {
        Path resumePath = tempDirectory.resolve("resume.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, null, resumePath.toString());
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        ResumePdfTextExtractor extractor = mock(ResumePdfTextExtractor.class);
        when(candidateRepository.findByResumeFilePath(resumePath.toAbsolutePath().normalize().toString()))
                .thenReturn(Optional.of(candidate));
        when(extractor.extractText(resumePath.toAbsolutePath().normalize())).thenReturn("Extracted resume text");

        ResumeBatchConfig config = new ResumeBatchConfig(candidateRepository, vectorSearchService, extractor);
        ItemProcessor<org.springframework.core.io.Resource, Candidate> processor = config.pdfResumeProcessor();

        Candidate processed = processor.process(new FileSystemResource(resumePath));

        assertEquals("Extracted resume text", processed.getRawResumeContent());
        verify(extractor).extractText(resumePath.toAbsolutePath().normalize());
    }
}
