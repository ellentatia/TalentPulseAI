package com.guimaraes.TalentPulseAI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobDescriptionRequest(
    @NotBlank @Size(max = 160) String title,
    @Size(max = 120) String department,
    @NotBlank @Size(max = 20000) String requirements
) {}
