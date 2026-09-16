package com.guimaraes.TalentPulseAI.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.guimaraes.TalentPulseAI.model.MatchResult;

@Repository 
public interface MatchResultRepository extends JpaRepository<MatchResult, UUID> {
List<MatchResult>findByCandidateId(UUID candidateId);
List<MatchResult> findByJobDescriptionIdOrderByMatchScoreDesc(UUID jobId);}
