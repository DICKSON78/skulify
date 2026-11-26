package org.example.school_wallet.operations;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.example.school_wallet.academic.CrudService;
import org.example.school_wallet.common.error.Errors;
import org.example.school_wallet.security.CurrentUser;
import org.example.school_wallet.security.ResourceAccessPolicy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload, list metadata through generic CRUD, and download stored documents. */
@RestController
@RequestMapping("/api/v1/school-documents")
public class SchoolDocumentController {

    private final CrudService crud;
    private final ResourceAccessPolicy policy;
    private final SchoolDocumentStorage storage;

    public SchoolDocumentController(
            CrudService crud, ResourceAccessPolicy policy, SchoolDocumentStorage storage) {
        this.crud = crud;
        this.policy = policy;
        this.storage = storage;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "category", defaultValue = "GENERAL") String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "documentDate", required = false) String documentDate) {
        policy.require("school-documents", ResourceAccessPolicy.Action.CREATE);
        if (file == null || file.isEmpty()) {
            throw Errors.badRequest("EMPTY_FILE", "Choose a file to upload");
        }
        if (title == null || title.isBlank()) {
            throw Errors.badRequest("TITLE_REQUIRED", "Document title is required");
        }

        var user = CurrentUser.require();
        UUID id = UUID.randomUUID();
        String relativePath = null;
        try {
            relativePath = storage.store(user.tenantId(), id, file);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reference", "DOC-" + id.toString().substring(0, 8).toUpperCase());
            body.put("title", title.trim());
            body.put("category", category == null || category.isBlank() ? "GENERAL" : category.trim());
            body.put("description", description);
            body.put("fileName", file.getOriginalFilename());
            body.put("filePath", relativePath);
            body.put("mimeType", file.getContentType());
            body.put("fileSize", file.getSize());
            body.put("documentDate",
                    documentDate == null || documentDate.isBlank()
                            ? LocalDate.now().toString() : documentDate.trim());
            body.put("uploadedById", user.id());
            body.put("status", "ACTIVE");
            return crud.create("school-documents", body);
        } catch (IOException ex) {
            storage.delete(relativePath);
            throw Errors.badRequest("UPLOAD_FAILED", "The document could not be stored");
        } catch (RuntimeException ex) {
            // Do not leave an orphaned file if metadata creation fails.
            storage.delete(relativePath);
            throw ex;
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        policy.require("school-documents", ResourceAccessPolicy.Action.READ);
        Map<String, Object> document = crud.get("school-documents", id);
        String fileName = String.valueOf(document.getOrDefault("fileName", "document"));
        String mimeType = String.valueOf(document.getOrDefault("mimeType", MediaType.APPLICATION_OCTET_STREAM_VALUE));
        Resource resource = new FileSystemResource(storage.resolve(String.valueOf(document.get("filePath"))));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        mimeType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName.replace('"', '_') + "\"")
                .body(resource);
    }
}
