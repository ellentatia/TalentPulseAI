package com.guimaraes.TalentPulseAI.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CandidateResponse(
    UUID candidateId,
    String fullName,
    String email,
    String phoneNumber,
    String ingestionStatus,
    LocalDateTime createdAt
) {}
