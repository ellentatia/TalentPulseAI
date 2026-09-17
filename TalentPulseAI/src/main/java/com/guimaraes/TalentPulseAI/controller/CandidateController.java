package com.guimaraes.TalentPulseAI.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.guimaraes.TalentPulseAI.dto.CandidateMatchResponse;
import com.guimaraes.TalentPulseAI.dto.JobDescriptionRequest;
import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.JobDescription;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.repository.JobDescriptionRepository;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/recruiter")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateRepository candidateRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final VectorSearchService vectorSearchService;

    // 1. Manually add candidate resume and compute vector embedding
    @PostMapping("/candidates")
    public ResponseEntity<Candidate> addCandidate(@RequestBody Candidate candidate) {
        Candidate saved = candidateRepository.save(candidate);
        
        if (saved.getRawResumeContent() != null) {
            vectorSearchService.storeCandidateResume(saved.getId().toString(), saved.getRawResumeContent());
        }
        
        return ResponseEntity.ok(saved);
    }

    // 2. Add Job Description
    @PostMapping("/jobs")
    public ResponseEntity<JobDescription> addJob(@RequestBody JobDescriptionRequest request) {
        JobDescription job = new JobDescription(request.title(), request.department(), request.requirements());
        return ResponseEntity.ok(jobDescriptionRepository.save(job));
    }

    // 3. Match candidates against a Job Description using PGvector
    @GetMapping("/jobs/{jobId}/match")
    public ResponseEntity<List<CandidateMatchResponse>> matchCandidates(@PathVariable UUID jobId,
                                                                         @RequestParam(defaultValue = "5") int topK) {
        JobDescription job = jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job Description not found"));

        List<Document> similarDocs = vectorSearchService.findTopMatchingCandidates(job.getRequirements(), topK);
        List<CandidateMatchResponse> response = new ArrayList<>();

        for (Document doc : similarDocs) {
            String candidateIdStr = (String) doc.getMetadata().get("candidateId");
            if (candidateIdStr != null) {
                UUID candidateId = UUID.fromString(candidateIdStr);
                candidateRepository.findById(candidateId).ifPresent(c -> {
                    double score = doc.getMetadata().containsKey("distance") 
                            ? 1.0 - ((Number) doc.getMetadata().get("distance")).doubleValue() 
                            : 0.85;
                    
                    response.add(new CandidateMatchResponse(
                        c.getId(),
                        c.getFullName(),
                        c.getEmail(),
                        score,
                        doc.getText()
                    ));
                });
            }
        }

        return ResponseEntity.ok(response);
    }
}