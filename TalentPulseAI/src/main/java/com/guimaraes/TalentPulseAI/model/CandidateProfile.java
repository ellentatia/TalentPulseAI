package com.guimaraes.TalentPulseAI.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Integer yearsOfExperience;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> skills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> jobTitles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> education = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> locations = new ArrayList<>();

    public CandidateProfile() {}

    public CandidateProfile(Candidate candidate) {
        this.candidate = candidate;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(Integer yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills); }

    public List<String> getJobTitles() { return jobTitles; }
    public void setJobTitles(List<String> jobTitles) { this.jobTitles = jobTitles == null ? new ArrayList<>() : new ArrayList<>(jobTitles); }

    public List<String> getEducation() { return education; }
    public void setEducation(List<String> education) { this.education = education == null ? new ArrayList<>() : new ArrayList<>(education); }

    public List<String> getLocations() { return locations; }
    public void setLocations(List<String> locations) { this.locations = locations == null ? new ArrayList<>() : new ArrayList<>(locations); }
}
