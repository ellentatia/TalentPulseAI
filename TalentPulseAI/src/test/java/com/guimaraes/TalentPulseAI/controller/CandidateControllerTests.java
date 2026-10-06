package com.guimaraes.TalentPulseAI.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.guimaraes.TalentPulseAI.config.SecurityConfig;
import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.JobDescription;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.repository.JobDescriptionRepository;
import com.guimaraes.TalentPulseAI.service.ResumeUploadService;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CandidateController.class)
@Import(SecurityConfig.class)
class CandidateControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandidateRepository candidateRepository;

    @MockitoBean
    private JobDescriptionRepository jobDescriptionRepository;

    @MockitoBean
    private VectorSearchService vectorSearchService;

    @MockitoBean
    private ResumeUploadService resumeUploadService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void recruiterCanListCandidatesWithoutReceivingRawResumeOrStoragePath() throws Exception {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = candidate(candidateId);
        when(candidateRepository.findAll()).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/recruiter/candidates").with(user("recruiter").roles("RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidateId").value(candidateId.toString()))
                .andExpect(jsonPath("$[0].fullName").value("Alex Morgan"))
                .andExpect(jsonPath("$[0].rawResumeContent").doesNotExist())
                .andExpect(jsonPath("$[0].resumeFilePath").doesNotExist())
                .andExpect(jsonPath("$[0].ingestionError").doesNotExist());
    }

    @Test
    void recruiterEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/recruiter/candidates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recruiterEndpointsRejectUsersWithoutRecruiterRole() throws Exception {
        mockMvc.perform(get("/api/recruiter/candidates").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jobCreationRejectsBlankRequiredFields() throws Exception {
        mockMvc.perform(post("/api/recruiter/jobs")
                        .with(user("recruiter").roles("RECRUITER"))
                        .contentType("application/json")
                        .content("""
                                {"title":" ","department":"Clinical","requirements":""}
                                """))
                .andExpect(status().isBadRequest());

        verify(jobDescriptionRepository, never()).save(any(JobDescription.class));
    }

    @Test
    void matchingRejectsTopKOutsideSupportedRange() throws Exception {
        mockMvc.perform(get("/api/recruiter/jobs/{jobId}/match", UUID.randomUUID())
                        .queryParam("topK", "21")
                        .with(user("recruiter").roles("RECRUITER")))
                .andExpect(status().isBadRequest());

        verify(jobDescriptionRepository, never()).findById(any());
    }

    @Test
    void matchingReturnsNotFoundForUnknownJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(jobDescriptionRepository.findById(jobId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/recruiter/jobs/{jobId}/match", jobId)
                        .with(user("recruiter").roles("RECRUITER")))
                .andExpect(status().isNotFound());

        verify(vectorSearchService, never()).findTopMatchingCandidates(any(), any(Integer.class));
    }

    @Test
    void matchResultRedactsContactDetailsAndReturnsOnlyBoundedSnippet() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Candidate candidate = candidate(candidateId);
        JobDescription job = new JobDescription("Medical Assistant", "Clinical", "Patient care experience");
        when(jobDescriptionRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(vectorSearchService.findTopMatchingCandidates(job.getRequirements(), 5))
                .thenReturn(List.of(new Document(
                        candidateId.toString(),
                        "Experienced in patient care. alex@example.com Call 202-555-0100. " + "x".repeat(400),
                        java.util.Map.of("candidateId", candidateId.toString(), "distance", 0.2))));
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        mockMvc.perform(get("/api/recruiter/jobs/{jobId}/match", jobId)
                        .with(user("recruiter").roles("RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchScore").value(0.8))
                .andExpect(jsonPath("$[0].snippet").value(org.hamcrest.Matchers.containsString("[email redacted]")))
                .andExpect(jsonPath("$[0].snippet").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("alex@example.com"))))
                .andExpect(jsonPath("$[0].snippet").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("202-555-0100"))))
                .andExpect(jsonPath("$[0].snippet").value(org.hamcrest.Matchers.containsString("...")));
    }

    @Test
    void candidateStatusReturnsOnlySafeStatusFields() throws Exception {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = candidate(candidateId);
        candidate.setIngestionStatus("FAILED");
        candidate.setIngestionError("PDF_TEXT_EXTRACTION_FAILED");
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        mockMvc.perform(get("/api/recruiter/candidates/{candidateId}/ingestion-status", candidateId)
                        .with(user("recruiter").roles("RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("PDF_TEXT_EXTRACTION_FAILED"))
                .andExpect(jsonPath("$.resumeFilePath").doesNotExist())
                .andExpect(jsonPath("$.rawResumeContent").doesNotExist());
    }

    private Candidate candidate(UUID id) {
        Candidate candidate = new Candidate(
                "Alex Morgan",
                "alex@example.com",
                "202-555-0100",
                "Confidential resume text",
                "C:\\private\\uploads\\resume.pdf");
        candidate.setId(id);
        candidate.setIngestionStatus("COMPLETED");
        return candidate;
    }
}
