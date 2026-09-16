package com.guimaraes.TalentPulseAI.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.guimaraes.TalentPulseAI.model.JobDescription;

@Repository 
public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {

}
