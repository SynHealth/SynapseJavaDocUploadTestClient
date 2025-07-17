package com.synapsehealth.doc_uploader_test_client.upload;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import com.synapsehealth.doc_uploader_test_client.model.UploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private RestTemplate mockRestTemplate;

    @TempDir
    Path tempDir;

    private UploadService uploadService;
    private AppConfig config;
    private File testFile;
    private String jwtToken;
    private String signature;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        Path testFilePath = tempDir.resolve("test-document.pdf");
        Files.writeString(testFilePath, "Test document content");
        testFile = testFilePath.toFile();

        config = AppConfig.builder()
                .apiUrl("https://test-api.url")
                .verbose(false)
                .retryCount(1)
                .build();

        uploadService = new UploadService(config);

        Field restTemplateField = UploadService.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(uploadService, mockRestTemplate);

        jwtToken = "sample-jwt-token";
        signature = "sample-base64-signature";
    }

    @Test
    void shouldUploadDocumentSuccessfully() {
        String successResponse = "{\"documentId\": \"doc-123\", \"status\": \"received\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(successResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        UploadResult result = uploadService.uploadDocument(testFile, jwtToken, signature, "patient-123");

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertEquals("doc-123", result.getDocumentId());
    }

    @Test
    void shouldHandleUnauthorizedError() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized",
                "{\"error\": \"invalid_token\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        UploadResult result = uploadService.uploadDocument(testFile, jwtToken, signature, null);

        assertFalse(result.isSuccess());
        assertEquals(401, result.getStatusCode());
        assertTrue(result.getStatusMessage().toLowerCase().contains("unauthorized"));
    }

    @Test
    void shouldHandleBadRequestError() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request",
                "{\"message\": \"Invalid document format\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        UploadResult result = uploadService.uploadDocument(testFile, jwtToken, signature, null);

        assertFalse(result.isSuccess());
        assertEquals(400, result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("Invalid document format"));
    }

    @Test
    void shouldHandleServerError() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        UploadResult result = uploadService.uploadDocument(testFile, jwtToken, signature, null);

        assertFalse(result.isSuccess());
        assertEquals(500, result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("Server Error"));
    }

    @Test
    void shouldHandleNetworkError() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RuntimeException("Network error"));

        UploadResult result = uploadService.uploadDocument(testFile, jwtToken, signature, null);

        assertFalse(result.isSuccess());
        assertEquals(0, result.getStatusCode());
        assertEquals("Request Failed", result.getStatusMessage());
        assertTrue(result.getErrorMessage().contains("Network error"));
    }
}
