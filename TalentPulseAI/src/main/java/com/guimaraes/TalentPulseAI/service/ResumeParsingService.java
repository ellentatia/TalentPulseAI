package com.guimaraes.TalentPulseAI.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.CandidateProfile;

@Service
public class ResumeParsingService {

    private static final int MAX_SUMMARY_LENGTH = 2000;
    private static final Pattern EXPERIENCE_YEARS = Pattern.compile(
            "(?i)(?<!\\d)(\\d{1,2})\\+?\\s+years?(?:\\s+of)?\\s+(?:professional\\s+)?experience\\b");
    private static final Pattern LABELED_VALUE = Pattern.compile(
            "(?i)^\\s*(?:job\\s+title|position|role)\\s*:\\s*(.{1,120}?)\\s*$");
    private static final Pattern LOCATION_VALUE = Pattern.compile(
            "(?i)^\\s*(?:location|based\\s+in)\\s*:\\s*(.{1,120}?)\\s*$");
    private static final Pattern EDUCATION_VALUE = Pattern.compile(
            "(?i)^\\s*education\\s*:\\s*(.{1,240}?)\\s*$");
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "(?i)^\\s*(?:professional\\s+)?(?:summary|profile|education|work\\s+experience|professional\\s+experience|experience|employment\\s+history|(?:(?:technical|key|core)\\s+)?skills|certifications|projects)\\s*:?\\s*$");
    private static final Pattern SUMMARY_HEADING = Pattern.compile(
            "(?i)^\\s*(?:professional\\s+)?(?:summary|profile)\\s*:?\\s*(.*)$");
    private static final Pattern EDUCATION_HEADING = Pattern.compile(
            "(?i)^\\s*education\\s*:?\\s*(.*)$");
    private static final Pattern SKILLS_HEADING = Pattern.compile(
            "(?i)^\\s*(?:(?:technical|key|core)\\s+)?skills\\s*:?\\s*(.*)$");
    private static final Pattern BULLET_PREFIX = Pattern.compile("^\\s*(?:[-*•]|\\d+[.)])\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SkillExtractionService skillExtractionService;

    public ResumeParsingService(SkillExtractionService skillExtractionService) {
        this.skillExtractionService = skillExtractionService;
    }

    public CandidateProfile parse(String resumeText, Candidate candidate) {
        CandidateProfile profile = new CandidateProfile(candidate);
        if (resumeText == null || resumeText.isBlank()) {
            return profile;
        }

        List<String> lines = resumeText.lines().toList();
        profile.setSummary(extractSummary(lines));
        profile.setYearsOfExperience(extractYearsOfExperience(resumeText));
        profile.setSkills(skillExtractionService.extractSkills(extractSkillsSection(lines)));
        profile.setJobTitles(extractLabeledValues(lines, LABELED_VALUE));
        profile.setEducation(extractEducation(lines));
        profile.setLocations(extractLabeledValues(lines, LOCATION_VALUE));
        return profile;
    }

    private String extractSummary(List<String> lines) {
        List<String> summaryLines = new ArrayList<>();
        boolean inSummary = false;

        for (String line : lines) {
            Matcher heading = SUMMARY_HEADING.matcher(line);
            if (heading.matches()) {
                inSummary = true;
                addIfNotBlank(summaryLines, heading.group(1));
                continue;
            }
            if (inSummary && SECTION_HEADING.matcher(line).matches()) {
                break;
            }
            if (inSummary) {
                addIfNotBlank(summaryLines, line);
            }
        }

        if (summaryLines.isEmpty()) {
            return null;
        }
        String summary = String.join(" ", summaryLines);
        return summary.length() <= MAX_SUMMARY_LENGTH
                ? summary
                : summary.substring(0, MAX_SUMMARY_LENGTH).stripTrailing();
    }

    private Integer extractYearsOfExperience(String resumeText) {
        Matcher matcher = EXPERIENCE_YEARS.matcher(resumeText);
        if (!matcher.find()) {
            return null;
        }
        int years = Integer.parseInt(matcher.group(1));
        return years <= 80 ? years : null;
    }

    private List<String> extractLabeledValues(List<String> lines, Pattern pattern) {
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                addUnique(values, cleanValue(matcher.group(1)));
            }
        }
        return values;
    }

    private List<String> extractEducation(List<String> lines) {
        List<String> education = new ArrayList<>();
        boolean inEducation = false;

        for (String line : lines) {
            Matcher heading = EDUCATION_HEADING.matcher(line);
            if (heading.matches()) {
                inEducation = true;
                addUnique(education, cleanValue(heading.group(1)));
                continue;
            }
            if (inEducation && SECTION_HEADING.matcher(line).matches()) {
                break;
            }
            if (inEducation) {
                addUnique(education, cleanValue(line));
            }
        }
        return education;
    }

    private String extractSkillsSection(List<String> lines) {
        List<String> skillLines = new ArrayList<>();
        boolean inSkills = false;

        for (String line : lines) {
            Matcher heading = SKILLS_HEADING.matcher(line);
            if (heading.matches()) {
                inSkills = true;
                addIfNotBlank(skillLines, heading.group(1));
                continue;
            }
            if (inSkills && SECTION_HEADING.matcher(line).matches()) {
                break;
            }
            if (inSkills) {
                addIfNotBlank(skillLines, line);
            }
        }
        return String.join("\n", skillLines);
    }

    private String cleanValue(String value) {
        return BULLET_PREFIX.matcher(value).replaceFirst("").strip();
    }

    private void addIfNotBlank(List<String> values, String value) {
        String cleaned = cleanValue(value);
        if (!cleaned.isBlank()) {
            values.add(cleaned);
        }
    }

    private void addUnique(List<String> values, String value) {
        if (!value.isBlank() && values.stream().noneMatch(existing ->
                existing.toLowerCase(Locale.ROOT).equals(value.toLowerCase(Locale.ROOT)))) {
            values.add(value);
        }
    }
}
