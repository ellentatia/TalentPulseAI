package com.guimaraes.TalentPulseAI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

class VectorSearchServiceTests {

    @Test
    void usesCandidateIdAsStableVectorDocumentId() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorSearchService service = new VectorSearchService(vectorStore);

        service.storeCandidateResume("candidate-123", "Resume text");

        verify(vectorStore).add(argThat(documents ->
                documents.size() == 1
                        && "candidate-123".equals(documents.getFirst().getId())
                        && "candidate-123".equals(documents.getFirst().getMetadata().get("candidateId"))));
    }
}
