package com.guimaraes.TalentPulseAI.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity 
@Table (name = "match_results")
public class MatchResult {

    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobDescription jobDescription;

    @Column(nullable = false)
    private double matchScore; // Cosine similarity score (0.0 to 1.0)

    @Column(columnDefinition = "TEXT")
    private String aiAnalysisSummary;

    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onEvaluate() {
        this.evaluatedAt = LocalDateTime.now();
    }

    public MatchResult() {}

    public MatchResult(Candidate candidate, JobDescription jobDescription, Double matchScore, String aiAnalysisSummary) {
        this.candidate = candidate;
        this.jobDescription = jobDescription;
        this.matchScore = matchScore;
        this.aiAnalysisSummary = aiAnalysisSummary;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }

    public JobDescription getJobDescription() { return jobDescription; }
    public void setJobDescription(JobDescription jobDescription) { this.jobDescription = jobDescription; }

    public Double getMatchScore() { return matchScore; }
    public void setMatchScore(Double matchScore) { this.matchScore = matchScore; }

    public String getAiAnalysisSummary() { return aiAnalysisSummary; }
    public void setAiAnalysisSummary(String aiAnalysisSummary) { this.aiAnalysisSummary = aiAnalysisSummary; }

    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
