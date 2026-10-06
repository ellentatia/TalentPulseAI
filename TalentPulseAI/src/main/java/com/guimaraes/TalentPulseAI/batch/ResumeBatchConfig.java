package com.guimaraes.TalentPulseAI.batch;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.model.CandidateProfile;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.repository.CandidateProfileRepository;
import com.guimaraes.TalentPulseAI.service.ResumePdfTextExtractor;
import com.guimaraes.TalentPulseAI.service.ResumeParsingService;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ResumeBatchConfig {

    private final CandidateRepository candidateRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final VectorSearchService vectorSearchService;
    private final ResumePdfTextExtractor resumePdfTextExtractor;
    private final ResumeParsingService resumeParsingService;

    @Value("${talentpulse.resume-storage-dir:uploads/resumes}")
    private String resumeStorageDirectory;

    @Bean
    @StepScope 
    public MultiResourceItemReader<Resource> pdfResourceReader() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String resourceLocation = Path.of(resumeStorageDirectory).toAbsolutePath().normalize().toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }
        String resourcePattern = resourceLocation + "*.pdf";
        Resource[] resources = resolver.getResources(resourcePattern);

        MultiResourceItemReader<Resource> reader =
            new MultiResourceItemReader<>(new SingleResourcePassThroughReader());
        reader.setResources(resources);
        return reader;
    }

    @Bean
    public ItemProcessor<Resource, Candidate> pdfResumeProcessor() {
        return resource -> {
            File file = resource.getFile();
            Path resumePath = file.toPath().toAbsolutePath().normalize();
            Candidate candidate = candidateRepository.findByResumeFilePath(resumePath.toString())
                    .orElseThrow(() -> new IllegalStateException("No candidate record exists for staged resume"));
            if ("COMPLETED".equals(candidate.getIngestionStatus())) {
                log.info("Skipping completed resume ingestion for candidate ID: {}", candidate.getId());
                return null;
            }
            log.info("Processing resume for candidate ID: {}", candidate.getId());

            try {
                candidate.setRawResumeContent(resumePdfTextExtractor.extractText(resumePath));
            } catch (IOException exception) {
                markFailed(candidate, "PDF_TEXT_EXTRACTION_FAILED");
                return null;
            }

            CandidateProfile parsedProfile;
            try {
                parsedProfile = resumeParsingService.parse(candidate.getRawResumeContent(), candidate);
            } catch (RuntimeException exception) {
                markFailed(candidate, "PROFILE_EXTRACTION_FAILED");
                return null;
            }

            try {
                CandidateProfile profile = candidateProfileRepository.findByCandidate_Id(candidate.getId())
                        .orElseGet(() -> new CandidateProfile(candidate));
                profile.setSummary(parsedProfile.getSummary());
                profile.setYearsOfExperience(parsedProfile.getYearsOfExperience());
                profile.setSkills(parsedProfile.getSkills());
                profile.setJobTitles(parsedProfile.getJobTitles());
                profile.setEducation(parsedProfile.getEducation());
                profile.setLocations(parsedProfile.getLocations());
                candidateProfileRepository.save(profile);
            } catch (RuntimeException exception) {
                markFailed(candidate, "PROFILE_PERSISTENCE_FAILED");
                return null;
            }
            candidate.setIngestionStatus("PARSED");
            candidate.setIngestionError(null);
            return candidate;
        };
    }

    @Bean
    public ItemWriter<Candidate> candidateVectorWriter() {
        return chunk -> {
            for (Candidate candidate : chunk) {
                if (candidate.getRawResumeContent() == null || candidate.getRawResumeContent().isBlank()) {
                    markFailed(candidate, "PDF_TEXT_EXTRACTION_FAILED");
                    continue;
                }

                try {
                    vectorSearchService.storeCandidateResume(
                        candidate.getId().toString(),
                        candidate.getRawResumeContent()
                    );
                    log.info("Stored embedding for candidate ID: {}", candidate.getId());
                } catch (RuntimeException exception) {
                    markFailed(candidate, "VECTOR_INDEXING_FAILED");
                    continue;
                }

                Path sourcePath = Path.of(candidate.getResumeFilePath());
                Path processedPath;
                try {
                    processedPath = moveToProcessedFolder(candidate.getResumeFilePath());
                    candidate.setResumeFilePath(processedPath.toString());
                    candidate.setIngestionStatus("COMPLETED");
                    candidate.setIngestionError(null);
                } catch (IOException exception) {
                    markFailed(candidate, "FILE_FINALIZATION_FAILED");
                    continue;
                }

                try {
                    candidateRepository.save(candidate);
                } catch (RuntimeException exception) {
                    candidate.setResumeFilePath(sourcePath.toString());
                    candidate.setIngestionStatus("PARSED");
                    candidate.setIngestionError("CANDIDATE_PERSISTENCE_FAILED");
                    try {
                        Files.move(processedPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException rollbackException) {
                        exception.addSuppressed(rollbackException);
                    }
                    throw exception;
                }
            }
        };
    }

    private void markFailed(Candidate candidate, String errorCode) {
        candidate.setIngestionStatus("FAILED");
        candidate.setIngestionError(errorCode);
        candidateRepository.save(candidate);
        log.error("Resume ingestion failed for candidate ID {}: {}", candidate.getId(), errorCode);
    }

    private Path moveToProcessedFolder(String resumeFilePath) throws IOException {
        Path source = Path.of(resumeFilePath);
        Path processedDirectory = source.getParent().getParent().resolve("processed");
        Files.createDirectories(processedDirectory);
        Path destination = processedDirectory.resolve(source.getFileName());

        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Moved processed resume: {}", destination.getFileName());
        return destination;
    }

    @Bean
    public Step processResumesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) throws IOException {
        return new StepBuilder("processResumesStep", jobRepository)
                .<Resource, Candidate>chunk(5, transactionManager)
                .reader(pdfResourceReader())
                .processor(pdfResumeProcessor())
                .writer(candidateVectorWriter())
                .build();
    }

    @Bean
    public Job importResumesJob(JobRepository jobRepository, Step processResumesStep) {
        return new JobBuilder("importResumesJob", jobRepository)
                .start(processResumesStep)
                .build();
    }

    private static class SingleResourcePassThroughReader implements ResourceAwareItemReaderItemStream<Resource> {
        private Resource resource;
        private boolean read = false;

        @Override
        public void setResource(Resource resource) {
            this.resource = resource;
            this.read = false;
        }

        @Override
        public Resource read() {
            if (!read && resource != null && resource.exists()) {
                read = true;
                return resource;
            }
            return null;
        }

        @Override public void open(ExecutionContext executionContext) {}
        @Override public void update(ExecutionContext executionContext) {}
        @Override public void close() {}
    }
}