package com.guimaraes.TalentPulseAI.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.guimaraes.TalentPulseAI.model.CandidateProfile;

@Service 
public class VectorSearchService {

    private final VectorStore vectorStore;

    public VectorSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void storeCandidateProfile(String candidateId, CandidateProfile profile) {
        String profileText = toSearchableProfileText(profile);
        Document document = new Document(
                candidateId,
                profileText,
                Map.of("candidateId", candidateId, "documentType", "candidate-profile"));
        vectorStore.add(List.of(document));
    }

    private String toSearchableProfileText(CandidateProfile profile) {
        List<String> sections = new ArrayList<>();
        addSection(sections, "Summary", profile.getSummary());
        if (profile.getYearsOfExperience() != null) {
            sections.add("Years of experience: " + profile.getYearsOfExperience());
        }
        addSection(sections, "Skills", profile.getSkills());
        addSection(sections, "Job titles", profile.getJobTitles());
        addSection(sections, "Education", profile.getEducation());
        addSection(sections, "Locations", profile.getLocations());
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile has no searchable fields");
        }
        return String.join("\n", sections);
    }

    private void addSection(List<String> sections, String label, String value) {
        if (value != null && !value.isBlank()) {
            sections.add(label + ": " + value.strip());
        }
    }

    private void addSection(List<String> sections, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            sections.add(label + ": " + String.join(", ", values));
        }
    }

    // Search indexed candidate profiles for a given job description
    public List<Document> findTopMatchingCandidates(String jobRequirements, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(jobRequirements)
                .topK(topK)
                .similarityThreshold(0.70)
                .build();

        return vectorStore.similaritySearch(request);
    }

}
