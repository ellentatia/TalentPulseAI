package com.guimaraes.TalentPulseAI.service;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeUploadServiceTests {

    @TempDir
    Path tempDirectory;

    @Test
    void storesValidPdfAndQueuesCandidateWithSubmittedIdentity() throws Exception {
        CandidateRepository repository = mock(CandidateRepository.class);
        when(repository.existsByEmailIgnoreCase("alex@example.com")).thenReturn(false);
        when(repository.save(any(Candidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ResumeUploadService service = new ResumeUploadService(repository, tempDirectory.toString());
        MockMultipartFile pdf = pdf("resume.pdf", "%PDF-1.7\nresume".getBytes(StandardCharsets.US_ASCII));

        Candidate candidate = service.upload(" Alex Morgan ", "Alex@Example.com", pdf);

        assertEquals("Alex Morgan", candidate.getFullName());
        assertEquals("alex@example.com", candidate.getEmail());
        assertEquals("PENDING", candidate.getIngestionStatus());
        assertTrue(Files.exists(Path.of(candidate.getResumeFilePath())));
        assertFalse(candidate.getResumeFilePath().contains("resume.pdf"));
    }

    @Test
    void rejectsFileWithPdfExtensionButInvalidContent() throws Exception {
        CandidateRepository repository = mock(CandidateRepository.class);
        ResumeUploadService service = new ResumeUploadService(repository, tempDirectory.toString());
        MockMultipartFile disguisedFile = pdf("resume.pdf", "not a pdf".getBytes(StandardCharsets.US_ASCII));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload("Alex Morgan", "alex@example.com", disguisedFile));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatusCode());
        verify(repository, never()).save(any(Candidate.class));
        assertNoFilesWereStored();
    }

    @Test
    void rejectsDuplicateCandidateEmailBeforeStoringPdf() throws Exception {
        CandidateRepository repository = mock(CandidateRepository.class);
        when(repository.existsByEmailIgnoreCase("alex@example.com")).thenReturn(true);
        ResumeUploadService service = new ResumeUploadService(repository, tempDirectory.toString());
        MockMultipartFile pdf = pdf("resume.pdf", "%PDF-1.7\nresume".getBytes(StandardCharsets.US_ASCII));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.upload("Alex Morgan", "alex@example.com", pdf));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).save(any(Candidate.class));
        assertNoFilesWereStored();
    }

    private MockMultipartFile pdf(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }

    private void assertNoFilesWereStored() throws Exception {
        try (var files = Files.list(tempDirectory)) {
            assertEquals(0, files.count());
        }
    }
}