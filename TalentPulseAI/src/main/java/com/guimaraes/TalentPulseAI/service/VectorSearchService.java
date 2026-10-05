package com.guimaraes.TalentPulseAI.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service 
public class VectorSearchService {

    private final VectorStore vectorStore;

    public VectorSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Save candidate resume text into pgvector table
    public void storeCandidateResume(String candidateId, String resumeText) {
        Document document = new Document(
            candidateId,
            resumeText,
            Map.of("candidateId", candidateId)
        );
        vectorStore.add(List.of(document));
    }

    // Search top matching candidate resumes for a given job description
    public List<Document> findTopMatchingCandidates(String jobRequirements, int topK) {
    SearchRequest request = SearchRequest.builder()
        .query(jobRequirements)
        .topK(topK)
        .similarityThreshold(0.70)
        .build();

    return vectorStore.similaritySearch(request);
    }

}
