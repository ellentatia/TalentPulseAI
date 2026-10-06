package com.guimaraes.TalentPulseAI.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.guimaraes.TalentPulseAI.model.CandidateProfile;

@Service
public class MatchExplanationService {

    private static final int MAX_EVIDENCE_SNIPPETS = 3;
    private static final int MAX_EVIDENCE_LENGTH = 240;
    private static final Pattern EXPERIENCE_YEARS = Pattern.compile(
            "(?i)(?<!\\d)(\\d{1,2})\\+?\\s+years?(?:\\s+of)?\\s+(?:professional\\s+)?experience\\b");

    private final SkillExtractionService skillExtractionService;

    public MatchExplanationService(SkillExtractionService skillExtractionService) {
        this.skillExtractionService = skillExtractionService;
    }

    public Explanation explain(String jobRequirements, CandidateProfile profile) {
        if (profile == null) {
            return new Explanation(
                    List.of(),
                    null,
                    "No structured profile is available. Review the indexed evidence before drawing conclusions.",
                    List.of());
        }

        List<String> matchedSkills = profile.getSkills().stream()
                .filter(skill -> skillExtractionService.mentionsSkill(jobRequirements, skill))
                .distinct()
                .toList();

        String roleFitSummary = matchedSkills.isEmpty()
                ? "No listed profile skill was explicitly named in the job description. Semantic retrieval can surface related language; review the evidence."
                : "The profile lists " + matchedSkills.size()
                        + " skill" + (matchedSkills.size() == 1 ? "" : "s")
                        + " also explicitly named in the job description.";

        return new Explanation(
                matchedSkills,
                describeExperienceOverlap(jobRequirements, profile.getYearsOfExperience()),
                roleFitSummary,
                createEvidenceSnippets(profile, matchedSkills));
    }

    private String describeExperienceOverlap(String jobRequirements, Integer profileYears) {
        if (profileYears == null) {
            return null;
        }

        Matcher matcher = EXPERIENCE_YEARS.matcher(jobRequirements);
        if (!matcher.find()) {
            return null;
        }
        return "Profile states " + profileYears + " years; job description states "
                + matcher.group(1) + " years.";
    }

    private List<String> createEvidenceSnippets(CandidateProfile profile, List<String> matchedSkills) {
        List<String> evidence = new ArrayList<>();
        for (String skill : matchedSkills) {
            addEvidence(evidence, "Skills: " + skill);
        }
        addEvidence(evidence, profile.getSummary());
        profile.getJobTitles().forEach(title -> addEvidence(evidence, "Job title: " + title));
        if (evidence.isEmpty()) {
            profile.getSkills().forEach(skill -> addEvidence(evidence, "Skills: " + skill));
        }
        return List.copyOf(evidence);
    }

    private void addEvidence(List<String> evidence, String value) {
        if (value == null || value.isBlank() || evidence.size() >= MAX_EVIDENCE_SNIPPETS) {
            return;
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        String bounded = normalized.length() > MAX_EVIDENCE_LENGTH
                ? normalized.substring(0, MAX_EVIDENCE_LENGTH).stripTrailing() + "..."
                : normalized;
        if (!evidence.contains(bounded)) {
            evidence.add(bounded);
        }
    }

    public record Explanation(
            List<String> matchedSkills,
            String overlappingExperience,
            String roleFitSummary,
            List<String> evidenceSnippets) {}
}
