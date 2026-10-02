package com.guimaraes.TalentPulseAI.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table (name = "candidates")
public class Candidate {

    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String rawResumeContent;

    @Column(length = 1024)
    private String resumeFilePath;

    @Column(length = 20)
    private String ingestionStatus;

    private LocalDateTime createdAt;

    @PrePersist 
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Candidate() {
    }

    public Candidate(String fullName, String email, String phoneNumber, String rawResumeContent, String resumeFilePath) {
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.rawResumeContent = rawResumeContent;
        this.resumeFilePath = resumeFilePath;
    }   

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRawResumeContent() { return rawResumeContent; }
    public void setRawResumeContent(String rawResumeContent) { this.rawResumeContent = rawResumeContent; }

    public String getResumeFilePath() { return resumeFilePath; }
    public void setResumeFilePath(String resumeFilePath) { this.resumeFilePath = resumeFilePath; }

    public String getIngestionStatus() { return ingestionStatus; }
    public void setIngestionStatus(String ingestionStatus) { this.ingestionStatus = ingestionStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
