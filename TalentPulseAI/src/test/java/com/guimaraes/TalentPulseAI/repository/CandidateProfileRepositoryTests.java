package com.guimaraes.TalentPulseAI.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.CandidateProfile;

@SpringBootTest
@Transactional
class CandidateProfileRepositoryTests {

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Test
    void storesProfileSeparatelyAndFindsItByCandidateId() {
        Candidate candidate = candidateRepository.saveAndFlush(
                new Candidate("Synthetic Candidate", "profile-test@example.com", null, "Private source text", null));
        CandidateProfile profile = new CandidateProfile(candidate);
        profile.setSummary("Experienced patient care professional");
        profile.setYearsOfExperience(4);
        profile.setSkills(List.of("patient care", "scheduling"));
        profile.setJobTitles(List.of("Medical Assistant"));
        profile.setEducation(List.of("Medical Assistant Diploma"));
        profile.setLocations(List.of("Boston"));

        candidateProfileRepository.saveAndFlush(profile);

        CandidateProfile storedProfile = candidateProfileRepository.findByCandidate_Id(candidate.getId())
                .orElseThrow();
        assertEquals(candidate.getId(), storedProfile.getCandidate().getId());
        assertEquals("Experienced patient care professional", storedProfile.getSummary());
        assertEquals(4, storedProfile.getYearsOfExperience());
        assertEquals(List.of("patient care", "scheduling"), storedProfile.getSkills());
        assertEquals(List.of("Medical Assistant"), storedProfile.getJobTitles());
        assertEquals(List.of("Medical Assistant Diploma"), storedProfile.getEducation());
        assertEquals(List.of("Boston"), storedProfile.getLocations());
    }

    @Test
    void candidateCanExistWithoutProfileUntilParsingIsImplemented() {
        Candidate candidate = candidateRepository.saveAndFlush(
                new Candidate("Unparsed Candidate", "unparsed-test@example.com", null, "Private source text", null));

        assertTrue(candidateProfileRepository.findByCandidate_Id(candidate.getId()).isEmpty());
    }
}
