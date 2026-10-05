package com.guimaraes.TalentPulseAI.batch;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.service.ResumePdfTextExtractor;
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
    private final VectorSearchService vectorSearchService;
    private final ResumePdfTextExtractor resumePdfTextExtractor;

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
            log.info("Processing resume for candidate ID: {}", candidate.getId());

            candidate.setRawResumeContent(resumePdfTextExtractor.extractText(resumePath));
            return candidate;
        };
    }

    @Bean
    public ItemWriter<Candidate> candidateVectorWriter() {
        return chunk -> {
            for (Candidate candidate : chunk) {
                if (candidate.getRawResumeContent() != null && !candidate.getRawResumeContent().isBlank()) {
                    vectorSearchService.storeCandidateResume(
                        candidate.getId().toString(),
                        candidate.getRawResumeContent()
                    );
                    log.info("Stored embedding for candidate ID: {}", candidate.getId());
                }

                Path processedPath = moveToProcessedFolder(candidate.getResumeFilePath());
                candidate.setResumeFilePath(processedPath.toString());
                candidate.setIngestionStatus("COMPLETED");
                candidateRepository.save(candidate);
            }
        };
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