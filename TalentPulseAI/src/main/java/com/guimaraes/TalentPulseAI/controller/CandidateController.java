package com.guimaraes.TalentPulseAI.controller;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import com.guimaraes.TalentPulseAI.dto.CandidateCreateRequest;
import com.guimaraes.TalentPulseAI.dto.CandidateIngestionStatusResponse;
import com.guimaraes.TalentPulseAI.dto.CandidateMatchResponse;
import com.guimaraes.TalentPulseAI.dto.CandidateResponse;
import com.guimaraes.TalentPulseAI.dto.JobDescriptionRequest;
import com.guimaraes.TalentPulseAI.dto.JobDescriptionResponse;
import com.guimaraes.TalentPulseAI.dto.ResumeUploadResponse;
import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.CandidateProfile;
import com.guimaraes.TalentPulseAI.model.JobDescription;
import com.guimaraes.TalentPulseAI.repository.CandidateProfileRepository;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.repository.JobDescriptionRepository;
import com.guimaraes.TalentPulseAI.service.ResumeParsingService;
import com.guimaraes.TalentPulseAI.service.ResumeUploadService;
import com.guimaraes.TalentPulseAI.service.MatchExplanationService;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class CandidateController {

    private static final int MAX_TOP_K = 20;
    private static final int MAX_SNIPPET_LENGTH = 240;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\w)\\+?\\d[\\d\\s().-]{7,}\\d(?!\\w)");

    private final CandidateRepository candidateRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final MatchExplanationService matchExplanationService;
    private final VectorSearchService vectorSearchService;
    private final ResumeParsingService resumeParsingService;
    private final ResumeUploadService resumeUploadService;

    @PostMapping(value = "/candidates/resumes", consumes = "multipart/form-data")
    public ResponseEntity<ResumeUploadResponse> uploadResume(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("file") MultipartFile file) {
        Candidate candidate = resumeUploadService.upload(fullName, email, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ResumeUploadResponse(candidate.getId(), candidate.getIngestionStatus()));
    }

    @PostMapping("/candidates")
    public ResponseEntity<CandidateResponse> addCandidate(@Valid @RequestBody CandidateCreateRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (candidateRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A candidate with this email already exists");
        }

        Candidate candidate = new Candidate(
                request.fullName().trim(),
                normalizedEmail,
                request.phoneNumber() == null ? null : request.phoneNumber().trim(),
                request.resumeContent().trim(),
                null);
        candidate.setIngestionStatus("PENDING");

        Candidate saved;
        try {
            saved = candidateRepository.save(candidate);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A candidate with this email already exists");
        }

        CandidateProfile profile;
        try {
            profile = resumeParsingService.parse(saved.getRawResumeContent(), saved);
        } catch (RuntimeException exception) {
            saved.setIngestionStatus("FAILED");
            saved.setIngestionError("PROFILE_EXTRACTION_FAILED");
            candidateRepository.save(saved);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Unable to extract candidate profile");
        }

        try {
            candidateProfileRepository.save(profile);
        } catch (RuntimeException exception) {
            saved.setIngestionStatus("FAILED");
            saved.setIngestionError("PROFILE_PERSISTENCE_FAILED");
            candidateRepository.save(saved);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save candidate profile");
        }

        try {
            vectorSearchService.storeCandidateProfile(saved.getId().toString(), profile);
        } catch (RuntimeException exception) {
            saved.setIngestionStatus("FAILED");
            saved.setIngestionError("VECTOR_INDEXING_FAILED");
            candidateRepository.save(saved);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Unable to index candidate profile");
        }
        saved.setIngestionStatus("COMPLETED");
        saved.setIngestionError(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCandidateResponse(candidateRepository.save(saved)));
    }

    @GetMapping("/candidates")
    public List<CandidateResponse> listCandidates() {
        return candidateRepository.findAll().stream()
                .map(this::toCandidateResponse)
                .toList();
    }

    @GetMapping("/candidates/{candidateId}")
    public CandidateResponse getCandidate(@PathVariable UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        return toCandidateResponse(candidate);
    }

    @GetMapping("/candidates/{candidateId}/ingestion-status")
    public CandidateIngestionStatusResponse getCandidateIngestionStatus(@PathVariable UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        return new CandidateIngestionStatusResponse(
                candidate.getId(),
                candidate.getIngestionStatus(),
                candidate.getIngestionError());
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobDescriptionResponse> addJob(@Valid @RequestBody JobDescriptionRequest request) {
        JobDescription job = new JobDescription(
                request.title().trim(),
                request.department() == null || request.department().isBlank() ? null : request.department().trim(),
                request.requirements().trim());
        JobDescription saved = jobDescriptionRepository.save(job);
        return ResponseEntity.status(HttpStatus.CREATED).body(toJobDescriptionResponse(saved));
    }

    @GetMapping("/jobs/{jobId}/match")
    public ResponseEntity<List<CandidateMatchResponse>> matchCandidates(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "5") int topK) {
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "topK must be between 1 and " + MAX_TOP_K);
        }

        JobDescription job = jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job description not found"));

        List<Document> similarDocs = vectorSearchService.findTopMatchingCandidates(job.getRequirements(), topK);
        List<CandidateMatchResponse> response = similarDocs.stream()
                .map(document -> toCandidateMatchResponse(document, job.getRequirements()))
                .toList();
        return ResponseEntity.ok(response);
    }

    private CandidateMatchResponse toCandidateMatchResponse(Document document, String jobRequirements) {
        Object candidateIdMetadata = document.getMetadata().get("candidateId");
        if (candidateIdMetadata == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Match result is missing candidate ID");
        }
        UUID candidateId;
        try {
            candidateId = UUID.fromString(candidateIdMetadata.toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Match result contains invalid candidate ID");
        }

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Matched candidate record was not found"));
        Object distanceMetadata = document.getMetadata().get("distance");
        if (!(distanceMetadata instanceof Number distance)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Match result is missing similarity distance");
        }

        CandidateProfile profile = candidateProfileRepository.findByCandidate_Id(candidateId).orElse(null);
        MatchExplanationService.Explanation explanation =
                matchExplanationService.explain(jobRequirements, profile);
        List<String> evidenceSnippets = explanation.evidenceSnippets().stream()
                .map(this::safeSnippet)
                .filter(snippet -> !snippet.isBlank())
                .toList();

        return new CandidateMatchResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getEmail(),
                1.0 - distance.doubleValue(),
                evidenceSnippets.isEmpty() ? "" : evidenceSnippets.getFirst(),
                explanation.matchedSkills(),
                explanation.overlappingExperience(),
                explanation.roleFitSummary(),
                evidenceSnippets);
    }

    private CandidateResponse toCandidateResponse(Candidate candidate) {
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFullName(),
                candidate.getEmail(),
                candidate.getPhoneNumber(),
                candidate.getIngestionStatus(),
                candidate.getCreatedAt());
    }

    private JobDescriptionResponse toJobDescriptionResponse(JobDescription job) {
        return new JobDescriptionResponse(
                job.getId(),
                job.getTitle(),
                job.getDepartment(),
                job.getRequirements(),
                job.getCreatedAt());
    }

    private String safeSnippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String sanitized = EMAIL_PATTERN.matcher(text).replaceAll("[email redacted]");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[phone redacted]");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.length() > MAX_SNIPPET_LENGTH) {
            return sanitized.substring(0, MAX_SNIPPET_LENGTH).trim() + "...";
        }
        return sanitized;
    }
}
