package org.example.school_wallet.operations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Stores uploaded documents on disk and resolves paths safely within the root. */
@Service
public class SchoolDocumentStorage {

    private final Path root;

    public SchoolDocumentStorage(
            @Value("${app.documents.dir:./uploads/documents}") String directory) {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
    }

    /** Persists the file and returns a tenant-scoped relative path for the database. */
    public String store(UUID tenantId, UUID documentId, MultipartFile file) throws IOException {
        Files.createDirectories(root.resolve(String.valueOf(tenantId)));
        String relative = tenantId + "/" + documentId + "-" + safeName(file.getOriginalFilename());
        Path target = resolve(relative);
        Files.copy(file.getInputStream(), target);
        return relative;
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Document has no stored file");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid document path");
        }
        return resolved;
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // A missing file must not block the metadata record from being removed.
        }
    }

    private String safeName(String original) {
        String name = original == null ? "document" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank()) {
            name = "document";
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
