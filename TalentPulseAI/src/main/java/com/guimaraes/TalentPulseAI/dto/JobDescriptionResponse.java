package com.guimaraes.TalentPulseAI.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobDescriptionResponse(
    UUID id,
    String title,
    String department,
    String requirements,
    LocalDateTime createdAt
) {}
