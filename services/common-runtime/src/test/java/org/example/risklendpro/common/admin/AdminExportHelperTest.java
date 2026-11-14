package org.example.risklendpro.common.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出契约的回归测试：曾经因为返回 /upload/... 静态地址而产生下载 404，
 * 这里把"必须是一次性文件流、必须是可被 Excel 正确解析的 CSV"钉住。
 */
class AdminExportHelperTest {

    private final AdminExportHelper helper = new AdminExportHelper();

    @Test
    void 返回带BOM的UTF8文件流() {
        ResponseEntity<byte[]> response = helper.csv("users",
                List.of("id", "name"),
                List.of(List.of("1", "张三")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());

        byte[] body = response.getBody();
        assertNotNull(body);
        // EF BB BF + "id,name\r\n1,张三\r\n"
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                new byte[]{body[0], body[1], body[2]});
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertEquals("id,name\r\n1,张三\r\n", text);
    }

    @Test
    void 响应头为附件下载且声明不嗅探() {
        ResponseEntity<byte[]> response = helper.csv("risk-export", List.of("a"), List.of());

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment"), "必须是下载而不是内联：" + disposition);
        assertTrue(disposition.contains("risk-export-"), "文件名应带业务前缀：" + disposition);
        assertTrue(disposition.endsWith(".csv\"") || disposition.contains(".csv"),
                "文件名应以 .csv 结尾：" + disposition);
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    void 不再返回任何静态下载地址() {
        // 回归点：旧实现返回 /upload/export/xxx.csv，网关无该路由，访问即 404
        String text = new String(helper.csv("users", List.of("id"), List.of())
                .getBody(), StandardCharsets.UTF_8);
        assertTrue(!text.contains("/upload/"), "不应再出现 /upload/ 路径：" + text);
        assertTrue(!text.contains("downloadUrl"), "不应再出现 downloadUrl 字段");
    }

    @Test
    void 逗号引号换行按RFC4180转义() {
        ResponseEntity<byte[]> response = helper.csv("t",
                List.of("a", "b", "c"),
                List.of(java.util.Arrays.asList("x,y", "他说\"好\"", "第一行\n第二行")));

        byte[] body = response.getBody();
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"x,y\""), "含逗号必须加引号：" + text);
        assertTrue(text.contains("\"他说\"\"好\"\"\""), "引号必须成对转义：" + text);
        assertTrue(text.contains("\"第一行\n第二行\""), "含换行必须整体加引号：" + text);
    }

    @Test
    void 空值输出空串而不是null字面量() {
        ResponseEntity<byte[]> response = helper.csv("t",
                List.of("a", "b"),
                List.of(java.util.Arrays.asList("1", null)));

        byte[] body = response.getBody();
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertEquals("a,b\r\n1,\r\n", text);
        assertTrue(!text.contains("null"));
    }

    @Test
    void 无数据时仍输出表头() {
        ResponseEntity<byte[]> response = helper.csv("t", List.of("a", "b"), List.of());

        byte[] body = response.getBody();
        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertEquals("a,b\r\n", text);
    }
}
