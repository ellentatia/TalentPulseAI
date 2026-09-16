package com.guimaraes.TalentPulseAI.dto;

import java.util.UUID;

public record CandidateMatchResponse(
    UUID candidateId,
    String fullName,
    String email,
    double matchScore,
    String snippet
) {}
