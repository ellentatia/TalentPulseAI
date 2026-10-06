package com.guimaraes.TalentPulseAI.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class SkillExtractionServiceTests {

    private final SkillExtractionService service = new SkillExtractionService();

    @Test
    void canonicalizesKnownAliasesAndRetainsUnlistedSkillPhrases() {
        assertEquals(
                List.of("ehr", "java", "pythonista", "python", "sql", "unlisted specialty"),
                service.extractSkills(
                        "EHR, electronic health records; Java, Pythonista, Python and SQL | Unlisted Specialty."));
    }

    @Test
    void preservesMeaningfulPunctuationInUnlistedSkillNames() {
        assertEquals(
                List.of("c++", "c#", ".net"),
                service.extractSkills("C++, C#, .NET"));
    }

    @Test
    void removesDuplicateAliasesAfterCanonicalization() {
        assertEquals(
                List.of("ehr"),
                service.extractSkills("EHR, electronic health records, Electronic Health Record."));
    }

    @Test
    void returnsNoSkillsForEmptyOrNullResumeText() {
        assertEquals(List.of(), service.extractSkills(""));
        assertEquals(List.of(), service.extractSkills(null));
    }
}
