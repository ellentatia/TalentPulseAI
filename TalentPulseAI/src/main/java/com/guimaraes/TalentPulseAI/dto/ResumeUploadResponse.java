package com.guimaraes.TalentPulseAI.dto;

import java.util.UUID;

public record ResumeUploadResponse(UUID candidateId, String status) {}