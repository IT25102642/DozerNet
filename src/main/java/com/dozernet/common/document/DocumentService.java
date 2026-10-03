package com.dozernet.common.document;

import com.dozernet.common.exception.BusinessRuleException;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores and retrieves supporting documents (NIC copies, licences, ownership
 * proof, inspection photos). Files are written under the configured upload
 * directory with a randomised name; the database keeps only metadata.
 *
 * <p>Documents stay attached to the account, so a customer who later registers
 * as a private owner can reuse the NIC copy already on file rather than
 * uploading it again.</p>
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** 5 MB is ample for a scanned NIC or a site photo. */
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final DocumentRepository documentRepository;
    private final Path uploadRoot;

    public DocumentService(DocumentRepository documentRepository,
                           @Value("${dozernet.upload-dir:uploads}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Saves an uploaded file against the given account.
     *
     * @return the stored document, or {@code null} when no file was submitted
     *         (uploads are optional on most forms)
     */
    @Transactional
    public Document store(User owner, DocumentType type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        validate(file);

        String stored = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
        try {
            Files.createDirectories(uploadRoot);
            Path target = uploadRoot.resolve(stored).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BusinessRuleException("Invalid upload destination");
            }
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("Could not store {} upload for user {}", type, owner.getEmail(), ex);
            throw new BusinessRuleException("Could not save the uploaded file. Please try again.");
        }

        Document document = new Document(owner, type,
                safeOriginalName(file.getOriginalFilename()), stored,
                file.getContentType(), file.getSize());
        return documentRepository.save(document);
    }

    public List<Document> forOwner(User owner) {
        return documentRepository.findByOwnerOrderByCreatedAtDesc(owner);
    }

    /**
     * Permanently removes every document the account uploaded, both the
     * database rows and the stored files (used when an account is deleted).
     */
    @Transactional
    public void deleteAllFor(User owner) {
        List<Document> documents = documentRepository.findByOwnerOrderByCreatedAtDesc(owner);
        for (Document document : documents) {
            Path path = uploadRoot.resolve(document.getStoredFilename()).normalize();
            if (path.startsWith(uploadRoot)) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Could not remove stored file for document {}", document.getId(), ex);
                }
            }
        }
        documentRepository.deleteAll(documents);
    }

    public Document getById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", id));
    }

    /** Reads the stored bytes so a controller can stream the file back. */
    public byte[] readBytes(Document document) {
        Path path = uploadRoot.resolve(document.getStoredFilename()).normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            throw ResourceNotFoundException.of("Document file", document.getId());
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void validate(MultipartFile file) {
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessRuleException("File is too large (maximum 5 MB)");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Upload a JPG, PNG or PDF file");
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,5}") ? ext : "";
    }

    private static String safeOriginalName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String name = Paths.get(filename).getFileName().toString();
        return name.length() <= 255 ? name : name.substring(0, 255);
    }
}
