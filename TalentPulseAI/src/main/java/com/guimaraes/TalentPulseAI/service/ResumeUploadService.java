package com.guimaraes.TalentPulseAI.service;

import com.guimaraes.TalentPulseAI.model.Candidate;
import com.guimaraes.TalentPulseAI.repository.CandidateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ResumeUploadService {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CandidateRepository candidateRepository;
    private final Path resumeDirectory;

    public ResumeUploadService(
            CandidateRepository candidateRepository,
            @Value("${talentpulse.resume-storage-dir:uploads/resumes}") String resumeStorageDirectory) {
        this.candidateRepository = candidateRepository;
        this.resumeDirectory = Path.of(resumeStorageDirectory).toAbsolutePath().normalize();
    }

    public Candidate upload(String fullName, String email, MultipartFile file) {
        String validatedName = validateName(fullName);
        String normalizedEmail = validateEmail(email);
        validatePdf(file);

        if (candidateRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A candidate with this email already exists");
        }

        UUID uploadId = UUID.randomUUID();
        Path storedPath = resumeDirectory.resolve(uploadId + ".pdf");
        Path temporaryPath = resumeDirectory.resolve(uploadId + ".upload");

        try {
            Files.createDirectories(resumeDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporaryPath);
            }
            if (Files.size(temporaryPath) > MAX_FILE_SIZE_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF exceeds the 10 MB limit");
            }
            moveIntoPlace(temporaryPath, storedPath);

            Candidate candidate = new Candidate(
                    validatedName,
                    normalizedEmail,
                    null,
                    null,
                    storedPath.toString());
            candidate.setIngestionStatus("PENDING");
            try {
                return candidateRepository.save(candidate);
            } catch (DataIntegrityViolationException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A candidate with this email already exists");
            }
        } catch (ResponseStatusException exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(storedPath);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(storedPath);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store PDF resume");
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(storedPath);
            throw exception;
        }
    }

    private String validateName(String fullName) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Candidate name is required (maximum 160 characters)");
        }
        return fullName.trim();
    }

    private String validateEmail(String email) {
        if (email == null || email.trim().length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid candidate email is required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid candidate email is required");
        }
        return normalizedEmail;
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a PDF resume");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF exceeds the 10 MB limit");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF resumes are accepted");
        }

        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(1024);
            if (!containsPdfSignature(header)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File content is not a PDF");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded PDF");
        }
    }

    private boolean containsPdfSignature(byte[] header) {
        for (int index = 0; index <= header.length - PDF_SIGNATURE.length; index++) {
            boolean matches = true;
            for (int signatureIndex = 0; signatureIndex < PDF_SIGNATURE.length; signatureIndex++) {
                if (header[index + signatureIndex] != PDF_SIGNATURE[signatureIndex]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}