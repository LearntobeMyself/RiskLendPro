package org.example.risklendpro.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AdminExportHelper {

    @Value("${risk.export.dir:upload/export}")
    private String exportDir;

    public String writeCsv(String prefix, List<String> headers, List<List<String>> rows) {
        try {
            Path dir = Paths.get(exportDir);
            Files.createDirectories(dir);
            String filename = prefix + "-" + System.currentTimeMillis() + ".csv";
            Path file = dir.resolve(filename);
            StringBuilder sb = new StringBuilder();
            sb.append(String.join(",", headers)).append('\n');
            for (List<String> row : rows) {
                sb.append(String.join(",", row.stream().map(this::escapeCsv).toList())).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            return "/upload/export/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> downloadMeta(String relativePath) {
        return Map.of(
                "downloadUrl", relativePath,
                "reportId", "RPT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
