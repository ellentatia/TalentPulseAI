package com.guimaraes.TalentPulseAI.dto;

import java.util.List;
import java.util.UUID;

public record CandidateMatchResponse(
    UUID candidateId,
    String fullName,
    String email,
    double similarityScore,
    String snippet,
    List<String> matchedSkills,
    String overlappingExperience,
    String roleFitSummary,
    List<String> evidenceSnippets
) {}
