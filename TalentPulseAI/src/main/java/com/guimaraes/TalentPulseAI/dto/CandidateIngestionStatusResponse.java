package com.guimaraes.TalentPulseAI.dto;

import java.util.UUID;

public record CandidateIngestionStatusResponse(
    UUID candidateId,
    String status,
    String errorCode
) {}
