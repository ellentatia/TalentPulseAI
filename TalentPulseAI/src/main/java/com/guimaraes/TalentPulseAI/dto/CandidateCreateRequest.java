package com.guimaraes.TalentPulseAI.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateCreateRequest(
    @NotBlank @Size(max = 160) String fullName,
    @NotBlank @Email @Size(max = 254) String email,
    @Size(max = 40) String phoneNumber,
    @NotBlank @Size(max = 1000000) String resumeContent
) {}
