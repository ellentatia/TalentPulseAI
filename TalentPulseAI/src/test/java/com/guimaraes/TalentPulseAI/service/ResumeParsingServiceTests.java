package com.guimaraes.TalentPulseAI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.CandidateProfile;

class ResumeParsingServiceTests {

    private final ResumeParsingService service = new ResumeParsingService(new SkillExtractionService());

    @Test
    void extractsOnlyExplicitFieldsAndBoundedSkillsFromSyntheticResume() {
        String resume = """
                Alex Morgan
                Summary:
                Medical assistant with 5 years of experience supporting outpatient care.
                Skilled in patient care, vital signs, EHR, and appointment scheduling.
                Experience:
                Job Title: Medical Assistant
                Location: Boston
                Education:
                Medical Assistant Diploma
                Northside College
                Skills:
                Patient care, vital signs, EHR, appointment scheduling
                """;

        CandidateProfile profile = service.parse(resume, new Candidate());

        assertEquals(
                "Medical assistant with 5 years of experience supporting outpatient care. "
                        + "Skilled in patient care, vital signs, EHR, and appointment scheduling.",
                profile.getSummary());
        assertEquals(5, profile.getYearsOfExperience());
        assertEquals(List.of("patient care", "vital signs", "ehr", "appointment scheduling"), profile.getSkills());
        assertEquals(List.of("Medical Assistant"), profile.getJobTitles());
        assertEquals(List.of("Medical Assistant Diploma", "Northside College"), profile.getEducation());
        assertEquals(List.of("Boston"), profile.getLocations());
    }

    @Test
    void leavesUnsupportedFieldsUnknownInsteadOfGuessing() {
        CandidateProfile profile = service.parse(
                "Jordan Lee\nJava, Python and patient care experience without a Skills section.", new Candidate());

        assertNull(profile.getSummary());
        assertNull(profile.getYearsOfExperience());
        assertEquals(List.of(), profile.getSkills());
        assertEquals(List.of(), profile.getJobTitles());
        assertEquals(List.of(), profile.getEducation());
        assertEquals(List.of(), profile.getLocations());
    }

    @Test
    void extractsSkillsOnlyFromAnExplicitSkillsSection() {
        CandidateProfile profile = service.parse(
                "Summary: Experienced with Java and patient care.", new Candidate());

        assertEquals(List.of(), profile.getSkills());
    }

    @Test
    void retainsUnlistedSkillsFromAnExplicitSkillsSection() {
        CandidateProfile profile = service.parse(
                "Skills:\nRare specialty tool\nPatient care", new Candidate());

        assertEquals(List.of("rare specialty tool", "patient care"), profile.getSkills());
    }

    @Test
    void doesNotInferYearsFromDatesOrAcceptUnreasonableDurations() {
        assertNull(service.parse(
                "Experience: 2012 - 2024\n", new Candidate()).getYearsOfExperience());
        assertNull(service.parse(
                "100 years of experience", new Candidate()).getYearsOfExperience());
        assertEquals(8, service.parse(
                "8+ years professional experience", new Candidate()).getYearsOfExperience());
    }
}
