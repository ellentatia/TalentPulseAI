package com.guimaraes.TalentPulseAI.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.guimaraes.TalentPulseAI.model.Candidate;

@Repository 
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {
    Optional<Candidate> findByEmail(String email);
    Optional<Candidate> findByResumeFilePath(String resumeFilePath);
    boolean existsByEmailIgnoreCase(String email);

}
