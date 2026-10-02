package com.dozernet.common.web;

import com.dozernet.common.document.Document;
import com.dozernet.common.document.DocumentService;
import com.dozernet.common.exception.ResourceNotFoundException;
import com.dozernet.common.model.Role;
import com.dozernet.common.security.CurrentUserService;
import com.dozernet.common.user.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Streams an uploaded document back to the browser. Only the account that
 * uploaded the document, or an administrator reviewing a verification, may
 * open it - a missing permission is reported as "not found" so the endpoint
 * cannot be used to probe which document ids exist.
 */
@Controller
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUserService currentUserService;

    public DocumentController(DocumentService documentService, CurrentUserService currentUserService) {
        this.documentService = documentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> view(@PathVariable Long id) {
        User viewer = currentUserService.require();
        Document document = documentService.getById(id);

        boolean ownDocument = document.getOwner().getId().equals(viewer.getId());
        if (!ownDocument && !viewer.hasRole(Role.ADMIN)) {
            throw ResourceNotFoundException.of("Document", id);
        }

        MediaType mediaType = document.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(document.getContentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + document.getOriginalFilename() + "\"")
                .body(documentService.readBytes(document));
    }
}
