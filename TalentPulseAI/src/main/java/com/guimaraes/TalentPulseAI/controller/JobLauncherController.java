package com.guimaraes.TalentPulseAI.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@RestController 
@RequestMapping ("/api/recruiter/jobs")
@RequiredArgsConstructor 
public class JobLauncherController {

    private final JobLauncher jobLauncher;
    private final Job importResumesJob;

    @PostMapping("/ingest-resumes") 
    public ResponseEntity<String> triggerBatchIngestion() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();

            jobLauncher.run(importResumesJob, jobParameters);
            return ResponseEntity.ok("Batch resume processing job started sucessfully");            
        } catch (Exception e) {
            log.error("Failed to execute batch ingestion job: ", e);
            return ResponseEntity.internalServerError().body("Error launching job: " + e.getMessage());
        }
    }

}
