package org.example.risklendpro.risk.supplement;

import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.supplement.dto.ImageVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Demo 图片核验接口的入口约束：限流、扩展名、大小，任一不满足都不应触发实际核验。
 */
@ExtendWith(MockitoExtension.class)
class ImageVerificationDemoControllerTest {

    @Mock
    private ImageVerificationService imageVerificationService;

    private SupplementMaterialProperties properties;
    private DemoRequestRateLimiter rateLimiter;
    private ImageVerificationDemoController controller;

    @BeforeEach
    void setUp() {
        properties = new SupplementMaterialProperties();
        properties.setAllowedExtensions("jpg,jpeg,png,pdf");
        properties.setMaxFileSizeMb(1);
        rateLimiter = new DemoRequestRateLimiter(2);
        controller = new ImageVerificationDemoController(imageVerificationService, rateLimiter, properties);
    }

    private MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void rejectsNonImageExtension() {
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1, 2, 3});

        CommonResponse<ImageVerificationResult> response = controller.verify(pdf, requestFrom("10.0.0.1"));

        assertEquals(415, response.getCode());
        verify(imageVerificationService, never()).verifyAll(any(java.nio.file.Path.class), anyString());
    }

    @Test
    void rejectsOversizedFile() {
        byte[] big = new byte[1024 * 1024 + 1];
        MockMultipartFile image = new MockMultipartFile("file", "a.png", "image/png", big);

        CommonResponse<ImageVerificationResult> response = controller.verify(image, requestFrom("10.0.0.1"));

        assertEquals(413, response.getCode());
    }

    @Test
    void rejectsMissingFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        CommonResponse<ImageVerificationResult> response = controller.verify(empty, requestFrom("10.0.0.1"));

        assertEquals(400, response.getCode());
    }

    @Test
    void blocksCallerBeyondQuota() {
        MockMultipartFile image = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        controller.verify(image, requestFrom("10.0.0.1"));
        controller.verify(image, requestFrom("10.0.0.1"));

        CommonResponse<ImageVerificationResult> third = controller.verify(image, requestFrom("10.0.0.1"));

        assertEquals(429, third.getCode());
    }

    @Test
    void verifiesAllowedImage() {
        MockMultipartFile image = new MockMultipartFile("file", "a.PNG", "image/png", new byte[]{1, 2, 3});
        ImageVerificationResult expected = ImageVerificationResult.builder().status("PASS").build();
        when(imageVerificationService.verifyAll(any(org.springframework.web.multipart.MultipartFile.class), anyString()))
                .thenReturn(expected);

        CommonResponse<ImageVerificationResult> response = controller.verify(image, requestFrom("10.0.0.1"));

        assertTrue(response.isSuccess());
        assertEquals(expected, response.getData());
    }

    @Test
    void usesForwardedForWhenPresent() {
        MockMultipartFile image = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        MockHttpServletRequest request = requestFrom("10.0.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.9");

        controller.verify(image, request);
        controller.verify(image, request);
        // 同一 XFF 首地址的第 3 次应被限流，证明限流键取的是转发地址而非 remoteAddr
        assertEquals(429, controller.verify(image, request).getCode());
    }
}
