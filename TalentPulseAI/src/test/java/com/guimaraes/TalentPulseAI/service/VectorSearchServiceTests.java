package com.guimaraes.TalentPulseAI.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import com.guimaraes.TalentPulseAI.model.CandidateProfile;

class VectorSearchServiceTests {

    @Test
    void indexesProfileFieldsWithoutCandidateIdentityOrRawResumeContent() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorSearchService service = new VectorSearchService(vectorStore);
        CandidateProfile profile = new CandidateProfile();
        profile.setSummary("Patient care professional");
        profile.setYearsOfExperience(4);
        profile.setSkills(List.of("patient care", "unlisted specialty"));
        profile.setJobTitles(List.of("Medical Assistant"));
        profile.setEducation(List.of("Medical Assistant Diploma"));
        profile.setLocations(List.of("Boston"));

        service.storeCandidateProfile("candidate-123", profile);

        verify(vectorStore).add(argThat(documents ->
                documents.size() == 1
                        && "candidate-123".equals(documents.getFirst().getId())
                        && "candidate-123".equals(documents.getFirst().getMetadata().get("candidateId"))
                        && "candidate-profile".equals(documents.getFirst().getMetadata().get("documentType"))
                        && documents.getFirst().getText().contains("Skills: patient care, unlisted specialty")
                        && documents.getFirst().getText().contains("Years of experience: 4")
                        && !documents.getFirst().getText().contains("candidate@example.com")
                        && !documents.getFirst().getText().contains("raw resume")));
    }

    @Test
    void profileUpdatesReuseCandidateDocumentIdAndReplaceIndexedContent() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorSearchService service = new VectorSearchService(vectorStore);
        String candidateId = UUID.randomUUID().toString();
        CandidateProfile firstProfile = new CandidateProfile();
        firstProfile.setSkills(List.of("patient care"));
        CandidateProfile updatedProfile = new CandidateProfile();
        updatedProfile.setSkills(List.of("patient care", "phlebotomy"));

        service.storeCandidateProfile(candidateId, firstProfile);
        service.storeCandidateProfile(candidateId, updatedProfile);

        verify(vectorStore).add(argThat(documents ->
                documents.size() == 1
                        && candidateId.equals(documents.getFirst().getId())
                        && documents.getFirst().getText().equals("Skills: patient care")));
        verify(vectorStore).add(argThat(documents ->
                documents.size() == 1
                        && candidateId.equals(documents.getFirst().getId())
                        && documents.getFirst().getText().equals("Skills: patient care, phlebotomy")));
        verify(vectorStore, times(2)).add(argThat(documents -> documents.size() == 1));
    }

    @Test
    void doesNotIndexAnEmptyProfile() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorSearchService service = new VectorSearchService(vectorStore);

        assertThrows(IllegalArgumentException.class,
                () -> service.storeCandidateProfile("candidate-123", new CandidateProfile()));

        verify(vectorStore, never()).add(anyList());
    }
}
