package com.guimaraes.TalentPulseAI.batch;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import com.guimaraes.TalentPulseAI.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ResumeBatchConfig {

    private final CandidateRepository candidateRepository;
    private final VectorSearchService vectorSearchService;

    @Bean
    public MultiResourceItemReader<Resource> pdfResourceReader() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("file:./uploads/resumes/*.pdf");

        MultiResourceItemReader<Resource> reader =
            new MultiResourceItemReader<>(new SingleResourcePassThroughReader());
        reader.setResources(resources);
        return reader;
    }

    @Bean
    public ItemProcessor<Resource, Candidate> pdfResumeProcessor() {
        return resource -> {
            File file = resource.getFile();
            String fileName = file.getName();
            log.info("Processing PDF resume: {}", fileName);

            String rawText;
            try (PDDocument document = Loader.loadPDF(file)) {
                PDFTextStripper stripper = new PDFTextStripper();
                rawText = stripper.getText(document);
            }

            String candidateName = fileName.replace(".pdf", "").replace("_", " ");
            String email = candidateName.toLowerCase().replaceAll("\\s+", ".") + "@example.com";

            return new Candidate(
                candidateName,
                email,
                "Unspecified",
                rawText,
                file.getAbsolutePath()
            );
        };
    }

    @Bean
    public ItemWriter<Candidate> candidateVectorWriter() {
        return chunk -> {
            for (Candidate candidate : chunk) {
                Candidate saved = candidateRepository.save(candidate);
                log.info("Saved candidate to DB with ID: {}", saved.getId());

                if (saved.getRawResumeContent() != null && !saved.getRawResumeContent().isBlank()) {
                    vectorSearchService.storeCandidateResume(
                        saved.getId().toString(),
                        saved.getRawResumeContent()
                    );
                    log.info("Stored embedding in pgvector for candidate ID: {}", saved.getId());
                }
            }
        };
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