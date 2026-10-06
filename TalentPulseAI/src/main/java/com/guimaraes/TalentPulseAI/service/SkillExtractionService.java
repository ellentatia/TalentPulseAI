package com.guimaraes.TalentPulseAI.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class SkillExtractionService {

    private static final Pattern SKILL_SEPARATOR = Pattern.compile("(?i)\\s*(?:[,;|\\r\\n•]|\\s+and\\s+|\\s*&\\s*)\\s*");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s\\-*•]+|[\\s.,;:]+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<String, String> SKILL_ALIASES = Map.ofEntries(
            Map.entry("ehr", "ehr"),
            Map.entry("electronic health record", "ehr"),
            Map.entry("electronic health records", "ehr"),
            Map.entry("emr", "emr"),
            Map.entry("electronic medical record", "emr"),
            Map.entry("electronic medical records", "emr"),
            Map.entry("ekg", "ekg"),
            Map.entry("ecg", "ekg"),
            Map.entry("electrocardiography", "ekg"),
            Map.entry("appointment scheduling", "appointment scheduling"),
            Map.entry("scheduling appointments", "appointment scheduling"),
            Map.entry("vital signs", "vital signs"),
            Map.entry("taking vital signs", "vital signs"),
            Map.entry("patient care", "patient care"),
            Map.entry("patient assistance", "patient care"),
            Map.entry("phlebotomy", "phlebotomy"),
            Map.entry("blood draw", "phlebotomy"),
            Map.entry("blood draws", "phlebotomy"),
            Map.entry("specimen collection", "specimen collection"),
            Map.entry("collecting specimens", "specimen collection"),
            Map.entry("microsoft excel", "excel"),
            Map.entry("excel", "excel"),
            Map.entry("microsoft office", "microsoft office"),
            Map.entry("hipaa", "hipaa"),
            Map.entry("hipaa compliance", "hipaa"),
            Map.entry("java", "java"),
            Map.entry("python", "python"),
            Map.entry("sql", "sql"),
            Map.entry("angular", "angular"),
            Map.entry("spring boot", "spring boot"),
            Map.entry("project management", "project management"),
            Map.entry("problem solving", "problem solving"),
            Map.entry("communication", "communication"),
            Map.entry("customer service", "customer service"),
            Map.entry("data entry", "data entry"),
            Map.entry("first aid", "first aid"),
            Map.entry("infection control", "infection control"),
            Map.entry("laboratory testing", "laboratory testing"),
            Map.entry("medical records", "medical records"),
            Map.entry("medication administration", "medication administration"),
            Map.entry("teamwork", "teamwork"),
            Map.entry("triage", "triage"),
            Map.entry("wound care", "wound care"),
            Map.entry("billing", "billing"),
            Map.entry("clinical documentation", "clinical documentation"));

    public List<String> extractSkills(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return List.of();
        }

        List<String> skills = new ArrayList<>();
        for (String phrase : SKILL_SEPARATOR.split(resumeText)) {
            String normalizedPhrase = normalize(phrase);
            if (!normalizedPhrase.isEmpty()) {
                String skill = SKILL_ALIASES.getOrDefault(normalizedPhrase, normalizedPhrase);
                if (!skills.contains(skill)) {
                    skills.add(skill);
                }
            }
        }
        return List.copyOf(skills);
    }

    public boolean mentionsSkill(String text, String skill) {
        if (text == null || text.isBlank() || skill == null || skill.isBlank()) {
            return false;
        }

        String canonicalSkill = canonicalize(skill);
        return SKILL_ALIASES.entrySet().stream()
                .filter(alias -> alias.getValue().equals(canonicalSkill))
                .map(Map.Entry::getKey)
                .anyMatch(alias -> containsWholePhrase(text, alias))
                || containsWholePhrase(text, canonicalSkill);
    }

    private String normalize(String phrase) {
        String normalized = WHITESPACE.matcher(phrase.toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
        return EDGE_PUNCTUATION.matcher(normalized).replaceAll("").strip();
    }

    private String canonicalize(String skill) {
        String normalizedSkill = normalize(skill);
        return SKILL_ALIASES.getOrDefault(normalizedSkill, normalizedSkill);
    }

    private boolean containsWholePhrase(String text, String phrase) {
        String expression = "(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text)
                .find();
    }
}
