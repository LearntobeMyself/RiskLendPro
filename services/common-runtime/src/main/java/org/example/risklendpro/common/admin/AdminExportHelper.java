package org.example.risklendpro.common.admin;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 导出的 CSV 以文件流一次性返回，不落盘、不返回二次下载地址。
 *
 * <p>旧实现把 CSV 写到服务本地 {@code upload/export} 下，返回
 * {@code /upload/export/xxx.csv}：该地址在网关没有任何路由（网关只转发
 * {@code /api/v1/{user,risk,loan,cs}} 前缀），前端跟随 downloadUrl 必然
 * 拿到 404，且文件只存在某一台容器的可写层里、也无法回收。
 * 改为直接返回流后，磁盘依赖、静态资源通道、残留文件清理三个问题一起消失。</p>
 */
@Component
public class AdminExportHelper {

    /** Excel 打开 UTF-8 CSV 需要 BOM，否则中文乱码。 */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * @param prefix 文件名前缀，如 users / risk-export，需为 ASCII
     */
    public ResponseEntity<byte[]> csv(String prefix, List<String> headers, List<List<String>> rows) {
        List<List<String>> all = new ArrayList<>(rows.size() + 1);
        all.add(headers);
        all.addAll(rows);

        StringBuilder sb = new StringBuilder();
        for (List<String> row : all) {
            sb.append(String.join(",", row.stream().map(this::escapeCsv).toList())).append("\r\n");
        }

        String filename = prefix + "-" + STAMP.format(LocalDateTime.now()) + ".csv";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // 浏览器不得嗅探内容类型，避免 CSV 被当成 HTML 执行
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(withBom(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] withBom(byte[] body) {
        byte[] payload = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, payload, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, payload, UTF8_BOM.length, body.length);
        return payload;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
