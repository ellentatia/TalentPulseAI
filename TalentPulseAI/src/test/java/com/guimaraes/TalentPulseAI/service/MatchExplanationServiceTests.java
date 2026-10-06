package com.guimaraes.TalentPulseAI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.guimaraes.TalentPulseAI.model.CandidateProfile;

class MatchExplanationServiceTests {

    private final MatchExplanationService service =
            new MatchExplanationService(new SkillExtractionService());

    @Test
    void explainsExplicitSkillAliasesAndExperienceWithoutCallingItCandidateQuality() {
        CandidateProfile profile = new CandidateProfile();
        profile.setSkills(List.of("ehr", "phlebotomy", "unlisted specialty"));
        profile.setYearsOfExperience(7);
        profile.setSummary("Clinical data professional with EHR experience.");
        profile.setJobTitles(List.of("Clinical Data Manager"));

        MatchExplanationService.Explanation explanation = service.explain(
                "Experience with electronic health records and blood draws; 5 years of experience required.",
                profile);

        assertEquals(List.of("ehr", "phlebotomy"), explanation.matchedSkills());
        assertEquals("Profile states 7 years; job description states 5 years.",
                explanation.overlappingExperience());
        assertEquals(
                "The profile lists 2 skills also explicitly named in the job description.",
                explanation.roleFitSummary());
        assertEquals(
                List.of("Skills: ehr", "Skills: phlebotomy", "Clinical data professional with EHR experience."),
                explanation.evidenceSnippets());
    }

    @Test
    void doesNotClaimSkillOverlapForSubstringsOrInferExperienceThresholds() {
        CandidateProfile profile = new CandidateProfile();
        profile.setSkills(List.of("java"));
        profile.setYearsOfExperience(7);

        MatchExplanationService.Explanation explanation =
                service.explain("JavaScript development experience preferred.", profile);

        assertEquals(List.of(), explanation.matchedSkills());
        assertNull(explanation.overlappingExperience());
        assertEquals(
                "No listed profile skill was explicitly named in the job description. "
                        + "Semantic retrieval can surface related language; review the evidence.",
                explanation.roleFitSummary());
        assertEquals(List.of("Skills: java"), explanation.evidenceSnippets());
    }

    @Test
    void handlesMissingProfileWithoutInventingEvidence() {
        MatchExplanationService.Explanation explanation = service.explain("Patient care", null);

        assertEquals(List.of(), explanation.matchedSkills());
        assertNull(explanation.overlappingExperience());
        assertEquals(
                "No structured profile is available. Review the indexed evidence before drawing conclusions.",
                explanation.roleFitSummary());
        assertEquals(List.of(), explanation.evidenceSnippets());
    }
}
