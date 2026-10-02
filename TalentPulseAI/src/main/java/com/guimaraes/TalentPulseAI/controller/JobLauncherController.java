package com.guimaraes.TalentPulseAI.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@RestController 
@RequestMapping ("/api/recruiter/jobs")
@RequiredArgsConstructor 
public class JobLauncherController {

    private static final String JOB_NAME = "importResumesJob";

    private final JobLauncher jobLauncher;
    private final Job importResumesJob;
    private final JobExplorer jobExplorer;

    @PostMapping("/ingest-resumes") 
    public ResponseEntity<BatchJobLaunchResponse> triggerBatchIngestion() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
            .addLong("time", System.currentTimeMillis())
            .toJobParameters();

            jobLauncher.run(importResumesJob, jobParameters);
            return ResponseEntity.accepted()
                    .body(new BatchJobLaunchResponse("Resume ingestion job started"));
        } catch (Exception e) {
            log.error("Failed to execute batch ingestion job: ", e);
            return ResponseEntity.internalServerError()
                    .body(new BatchJobLaunchResponse("Unable to start resume ingestion job"));
        }
    }

    @GetMapping("/ingest-resumes/status")
    public ResponseEntity<BatchJobStatus> getIngestionStatus() {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        JobInstance jobInstance = jobExplorer.getLastJobInstance(JOB_NAME);
        if (jobInstance == null) {
            return response.body(BatchJobStatus.idle());
        }

        JobExecution execution = jobExplorer.getLastJobExecution(jobInstance);
        if (execution == null) {
            return response.body(BatchJobStatus.idle());
        }

        return response.body(new BatchJobStatus(
            execution.getStatus().name(),
            execution.getExitStatus().getExitDescription(),
            execution.getStartTime(),
            execution.getEndTime()
        ));
    }

    public record BatchJobStatus(
        String status,
        String exitDescription,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        private static BatchJobStatus idle() {
            return new BatchJobStatus("IDLE", null, null, null);
        }
    }

    public record BatchJobLaunchResponse(String message) {}

}
