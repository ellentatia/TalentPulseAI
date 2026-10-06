package com.guimaraes.TalentPulseAI.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.CandidateProfile;
import com.guimaraes.TalentPulseAI.repository.CandidateProfileRepository;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.service.ResumePdfTextExtractor;
import com.guimaraes.TalentPulseAI.service.ResumeParsingService;
import com.guimaraes.TalentPulseAI.service.SkillExtractionService;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.core.io.FileSystemResource;

class ResumeBatchConfigTests {

    @TempDir
    Path tempDirectory;

    @Test
    void processorUsesPdfTextExtractorToPopulateCandidate() throws Exception {
        Path resumePath = tempDirectory.resolve("resume.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, null, resumePath.toString());
        candidate.setId(UUID.randomUUID());
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        CandidateProfileRepository profileRepository = mock(CandidateProfileRepository.class);
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        ResumePdfTextExtractor extractor = mock(ResumePdfTextExtractor.class);
        when(profileRepository.findByCandidate_Id(candidate.getId())).thenReturn(Optional.empty());
        when(profileRepository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.findByResumeFilePath(resumePath.toAbsolutePath().normalize().toString()))
                .thenReturn(Optional.of(candidate));
        when(extractor.extractText(resumePath.toAbsolutePath().normalize())).thenReturn("""
                Summary: Patient care professional with 4 years of experience.
                Job Title: Medical Assistant
                Skills: EHR and patient care
                """);

        ResumeBatchConfig config = createConfig(candidateRepository, profileRepository, vectorSearchService, extractor);
        ItemProcessor<org.springframework.core.io.Resource, Candidate> processor = config.pdfResumeProcessor();

        Candidate processed = processor.process(new FileSystemResource(resumePath));

        assertEquals("Summary: Patient care professional with 4 years of experience.\n"
                + "Job Title: Medical Assistant\nSkills: EHR and patient care\n", processed.getRawResumeContent());
        assertEquals("PARSED", processed.getIngestionStatus());
        var profileCaptor = forClass(CandidateProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertEquals(4, profileCaptor.getValue().getYearsOfExperience());
        assertEquals(List.of("ehr", "patient care"), profileCaptor.getValue().getSkills());
        assertEquals(List.of("Medical Assistant"), profileCaptor.getValue().getJobTitles());
        verify(extractor).extractText(resumePath.toAbsolutePath().normalize());
    }

    @Test
    void processorSkipsCompletedCandidateWithoutReindexing() throws Exception {
        Path resumePath = tempDirectory.resolve("completed.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, null, resumePath.toString());
        candidate.setIngestionStatus("COMPLETED");
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        ResumePdfTextExtractor extractor = mock(ResumePdfTextExtractor.class);
        when(candidateRepository.findByResumeFilePath(resumePath.toAbsolutePath().normalize().toString()))
                .thenReturn(Optional.of(candidate));
        ResumeBatchConfig config = createConfig(
                candidateRepository, mock(CandidateProfileRepository.class), mock(VectorSearchService.class), extractor);

        Candidate processed = config.pdfResumeProcessor().process(new FileSystemResource(resumePath));

        assertNull(processed);
        verify(extractor, never()).extractText(any(Path.class));
    }

    @Test
    void processorPersistsFailureStateWhenPdfTextCannotBeExtracted() throws Exception {
        Path resumePath = tempDirectory.resolve("invalid.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, null, resumePath.toString());
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        ResumePdfTextExtractor extractor = mock(ResumePdfTextExtractor.class);
        when(candidateRepository.findByResumeFilePath(resumePath.toAbsolutePath().normalize().toString()))
                .thenReturn(Optional.of(candidate));
        when(extractor.extractText(resumePath.toAbsolutePath().normalize()))
                .thenThrow(new IOException("invalid PDF"));
        ResumeBatchConfig config = createConfig(
                candidateRepository, mock(CandidateProfileRepository.class), mock(VectorSearchService.class), extractor);

        Candidate processed = config.pdfResumeProcessor().process(new FileSystemResource(resumePath));

        assertNull(processed);
        assertEquals("FAILED", candidate.getIngestionStatus());
        assertEquals("PDF_TEXT_EXTRACTION_FAILED", candidate.getIngestionError());
        verify(candidateRepository).save(candidate);
    }

    @Test
    void processorMarksCandidateFailedWhenProfileCannotBePersisted() throws Exception {
        Path resumePath = tempDirectory.resolve("profile-failure.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, null, resumePath.toString());
        candidate.setId(UUID.randomUUID());
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        CandidateProfileRepository profileRepository = mock(CandidateProfileRepository.class);
        ResumePdfTextExtractor extractor = mock(ResumePdfTextExtractor.class);
        when(candidateRepository.findByResumeFilePath(resumePath.toAbsolutePath().normalize().toString()))
                .thenReturn(Optional.of(candidate));
        when(extractor.extractText(resumePath.toAbsolutePath().normalize())).thenReturn("Patient care");
        when(profileRepository.findByCandidate_Id(candidate.getId())).thenReturn(Optional.empty());
        when(profileRepository.save(any(CandidateProfile.class)))
                .thenThrow(new IllegalStateException("database unavailable"));
        ResumeBatchConfig config = createConfig(
                candidateRepository, profileRepository, mock(VectorSearchService.class), extractor);

        Candidate processed = config.pdfResumeProcessor().process(new FileSystemResource(resumePath));

        assertNull(processed);
        assertEquals("FAILED", candidate.getIngestionStatus());
        assertEquals("PROFILE_PERSISTENCE_FAILED", candidate.getIngestionError());
        verify(candidateRepository).save(candidate);
    }

    @Test
    void writerMarksIndexingFailureAndLeavesResumeAvailableForRetry() throws Exception {
        Path resumeDirectory = tempDirectory.resolve("resumes");
        java.nio.file.Files.createDirectories(resumeDirectory);
        Path resumePath = resumeDirectory.resolve("resume.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, "Resume text", resumePath.toString());
        candidate.setId(UUID.randomUUID());
        candidate.setIngestionStatus("PARSED");
        CandidateProfile profile = searchableProfile(candidate);
        CandidateProfileRepository profileRepository = mock(CandidateProfileRepository.class);
        when(profileRepository.findByCandidate_Id(candidate.getId())).thenReturn(Optional.of(profile));
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        doThrow(new IllegalStateException("vector store unavailable"))
                .when(vectorSearchService).storeCandidateProfile(candidate.getId().toString(), profile);
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        ResumeBatchConfig config = createConfig(
                candidateRepository, profileRepository,
                vectorSearchService, mock(ResumePdfTextExtractor.class));
        ItemWriter<Candidate> writer = config.candidateVectorWriter();
        Chunk<Candidate> chunk = new Chunk<>();
        chunk.add(candidate);

        writer.write(chunk);

        assertEquals("FAILED", candidate.getIngestionStatus());
        assertEquals("VECTOR_INDEXING_FAILED", candidate.getIngestionError());
        assertEquals(resumePath.toString(), candidate.getResumeFilePath());
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(resumePath));
        verify(candidateRepository).save(candidate);
    }

    @Test
    void writerMovesSuccessfullyIndexedResumeAndMarksCandidateCompleted() throws Exception {
        Path resumeDirectory = tempDirectory.resolve("uploads").resolve("resumes");
        java.nio.file.Files.createDirectories(resumeDirectory);
        Path resumePath = resumeDirectory.resolve("resume.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, "Resume text", resumePath.toString());
        candidate.setId(UUID.randomUUID());
        candidate.setIngestionStatus("PARSED");
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        CandidateProfile profile = searchableProfile(candidate);
        CandidateProfileRepository profileRepository = mock(CandidateProfileRepository.class);
        when(profileRepository.findByCandidate_Id(candidate.getId())).thenReturn(Optional.of(profile));
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        ResumeBatchConfig config = createConfig(
                candidateRepository, profileRepository,
                vectorSearchService, mock(ResumePdfTextExtractor.class));
        Chunk<Candidate> chunk = new Chunk<>();
        chunk.add(candidate);

        config.candidateVectorWriter().write(chunk);

        Path processedPath = tempDirectory.resolve("uploads").resolve("processed").resolve("resume.pdf");
        assertEquals("COMPLETED", candidate.getIngestionStatus());
        assertEquals(processedPath.toString(), candidate.getResumeFilePath());
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(processedPath));
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(resumePath));
        verify(vectorSearchService).storeCandidateProfile(candidate.getId().toString(), profile);
        verify(candidateRepository).save(candidate);
    }

    @Test
    void writerRestoresResumeIfCandidateCompletionCannotBeSaved() throws Exception {
        Path resumeDirectory = tempDirectory.resolve("uploads").resolve("resumes");
        java.nio.file.Files.createDirectories(resumeDirectory);
        Path resumePath = resumeDirectory.resolve("resume.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, "Resume text", resumePath.toString());
        candidate.setId(UUID.randomUUID());
        candidate.setIngestionStatus("PARSED");
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        doThrow(new IllegalStateException("database unavailable")).when(candidateRepository).save(candidate);
        CandidateProfile profile = searchableProfile(candidate);
        CandidateProfileRepository profileRepository = mock(CandidateProfileRepository.class);
        when(profileRepository.findByCandidate_Id(candidate.getId())).thenReturn(Optional.of(profile));
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        ResumeBatchConfig config = createConfig(
                candidateRepository, profileRepository,
                vectorSearchService, mock(ResumePdfTextExtractor.class));
        Chunk<Candidate> chunk = new Chunk<>();
        chunk.add(candidate);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> config.candidateVectorWriter().write(chunk));

        assertEquals(resumePath.toString(), candidate.getResumeFilePath());
        assertEquals("PARSED", candidate.getIngestionStatus());
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(resumePath));
        org.junit.jupiter.api.Assertions.assertFalse(
                java.nio.file.Files.exists(tempDirectory.resolve("uploads").resolve("processed").resolve("resume.pdf")));
        verify(vectorSearchService).storeCandidateProfile(candidate.getId().toString(), profile);
    }

    @Test
    void writerMarksCandidateFailedWhenProfileIsMissing() throws Exception {
        Path resumeDirectory = tempDirectory.resolve("resumes");
        java.nio.file.Files.createDirectories(resumeDirectory);
        Path resumePath = resumeDirectory.resolve("missing-profile.pdf");
        java.nio.file.Files.createFile(resumePath);
        Candidate candidate = new Candidate("Alex Morgan", "alex@example.com", null, "Resume text", resumePath.toString());
        candidate.setId(UUID.randomUUID());
        candidate.setIngestionStatus("PARSED");
        CandidateRepository candidateRepository = mock(CandidateRepository.class);
        CandidateProfileRepository profileRepository = mock(CandidateProfileRepository.class);
        when(profileRepository.findByCandidate_Id(candidate.getId())).thenReturn(Optional.empty());
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        ResumeBatchConfig config = createConfig(
                candidateRepository, profileRepository,
                vectorSearchService, mock(ResumePdfTextExtractor.class));
        Chunk<Candidate> chunk = new Chunk<>();
        chunk.add(candidate);

        config.candidateVectorWriter().write(chunk);

        assertEquals("FAILED", candidate.getIngestionStatus());
        assertEquals("CANDIDATE_PROFILE_NOT_FOUND", candidate.getIngestionError());
        assertEquals(resumePath.toString(), candidate.getResumeFilePath());
        verify(vectorSearchService, never()).storeCandidateProfile(any(), any());
        verify(candidateRepository).save(candidate);
    }

    private CandidateProfile searchableProfile(Candidate candidate) {
        CandidateProfile profile = new CandidateProfile(candidate);
        profile.setSummary("Patient care professional");
        profile.setSkills(List.of("patient care"));
        return profile;
    }

    private ResumeBatchConfig createConfig(
            CandidateRepository candidateRepository,
            CandidateProfileRepository profileRepository,
            VectorSearchService vectorSearchService,
            ResumePdfTextExtractor extractor) {
        return new ResumeBatchConfig(
                candidateRepository,
                profileRepository,
                vectorSearchService,
                extractor,
                new ResumeParsingService(new SkillExtractionService()));
    }
}
