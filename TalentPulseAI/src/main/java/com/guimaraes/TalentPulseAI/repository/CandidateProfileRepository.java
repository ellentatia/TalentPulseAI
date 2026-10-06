package com.guimaraes.TalentPulseAI.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.guimaraes.TalentPulseAI.model.CandidateProfile;

@Repository
public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {
    Optional<CandidateProfile> findByCandidate_Id(UUID candidateId);
}
