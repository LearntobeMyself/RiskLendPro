package org.example.risklendpro.service.supplement;

import jakarta.annotation.PostConstruct;
import org.example.risklendpro.config.SupplementMaterialProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final SupplementMaterialProperties properties;
    private Path uploadBase;

    public FileStorageService(SupplementMaterialProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initUploadBase() throws IOException {
        Path base = Paths.get(properties.getUploadDir());
        if (!base.isAbsolute()) {
            base = Paths.get(System.getProperty("user.dir")).resolve(base);
        }
        uploadBase = base.normalize().toAbsolutePath();
        Files.createDirectories(uploadBase);
        log.info("Supplement upload dir: {}", uploadBase);
    }

    public StoredFile store(String applyId, MultipartFile file) throws IOException {
        validateFile(file);
        String ext = extractExtension(file.getOriginalFilename());
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = uploadBase.resolve(applyId);
        Files.createDirectories(dir);
        Path target = dir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(target.toString(), file.getOriginalFilename(), file.getSize(), file.getContentType());
    }

    public void deleteIfExists(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolvePath(storedPath));
        } catch (IOException ignored) {
        }
    }

    public Path resolvePath(String storedPath) {
        Path path = Paths.get(storedPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (storedPath.replace('\\', '/').startsWith("upload/risk-supplement")) {
            return uploadBase.getParent().resolve(path).normalize();
        }
        return uploadBase.resolve(path).normalize();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new RuntimeException("文件大小不能超过 " + properties.getMaxFileSizeMb() + "MB");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!properties.allowedExtensionList().contains(ext)) {
            throw new RuntimeException("仅支持上传: " + properties.getAllowedExtensions());
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new RuntimeException("文件名缺少扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String storedPath, String originalName, long size, String mimeType) {
    }
}
