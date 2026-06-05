package org.example.risklendpro.controller;

import org.example.risklendpro.entity.RiskSupplementMaterial;
import org.example.risklendpro.service.supplement.FileStorageService;
import org.example.risklendpro.service.supplement.SupplementMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/admin/supplement")
public class AdminSupplementController {

    @Autowired
    private SupplementMaterialService supplementMaterialService;

    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping("/file/{materialId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long materialId,
            @RequestParam(required = false, defaultValue = "false") boolean inline) throws Exception {
        RiskSupplementMaterial material = supplementMaterialService.getById(materialId);
        if (material == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = fileStorageService.resolvePath(material.getStoredPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        String contentType = material.getMimeType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String disposition = inline ? "inline" : "attachment";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + material.getOriginalName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}
