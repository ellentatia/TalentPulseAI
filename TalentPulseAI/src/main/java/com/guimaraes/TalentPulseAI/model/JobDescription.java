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
@Table(name = "job_descriptions")
public class JobDescription {

    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String department;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String requirements;

    private LocalDateTime createdAt;

    @PrePersist 
    private void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public JobDescription() {}

    public JobDescription(String title, String department, String requirements) {
        this.title = title;
        this.department = department;
        this.requirements = requirements; 
        }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
