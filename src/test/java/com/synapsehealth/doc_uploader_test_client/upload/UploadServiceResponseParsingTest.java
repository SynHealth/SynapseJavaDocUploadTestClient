package com.synapsehealth.doc_uploader_test_client.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import com.synapsehealth.doc_uploader_test_client.model.UploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UploadServiceResponseParsingTest {

    @TempDir
    Path tempDir;

    @Mock
    private RestTemplate mockRestTemplate;

    @Mock
    private AppConfig mockConfig;

    private UploadService uploadService;
    private File testFile;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        testFile = tempDir.resolve("test_doc.pdf").toFile();
        Files.writeString(testFile.toPath(), "Test document content");

        when(mockConfig.getApiUrl()).thenReturn("https://test.api/documents");
        when(mockConfig.getRetryCount()).thenReturn(3);
        when(mockConfig.isVerbose()).thenReturn(false);

        uploadService = new UploadService(mockConfig);

        Field restTemplateField = UploadService.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(uploadService, mockRestTemplate);
    }

    @Test
    void shouldParseStandardRestResponse() throws Exception {
        String responseBody = "{\"documentId\":\"doc-123\",\"contentType\":\"application/pdf\",\"size\":12345}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertTrue(result.isSuccess());
        assertEquals("doc-123", result.getDocumentId());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void shouldParseFhirOperationOutcomeResponse() throws Exception {
        String responseBody = "{\n" +
            "  \"resourceType\": \"OperationOutcome\",\n" +
            "  \"id\": \"success\",\n" +
            "  \"issue\": [\n" +
            "    {\n" +
            "      \"severity\": \"information\",\n" +
            "      \"code\": \"informational\",\n" +
            "      \"details\": {\n" +
            "        \"coding\": [\n" +
            "          {\n" +
            "            \"system\": \"urn:synapse:documentId\",\n" +
            "            \"code\": \"synapse-document-abc123\"\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertTrue(result.isSuccess());
        assertEquals("synapse-document-abc123", result.getDocumentId());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    void shouldHandleFhirResponseWithoutCoding() throws Exception {
        String responseBody = "{\n" +
            "  \"resourceType\": \"OperationOutcome\",\n" +
            "  \"id\": \"success\",\n" +
            "  \"issue\": [\n" +
            "    {\n" +
            "      \"severity\": \"information\",\n" +
            "      \"code\": \"informational\",\n" +
            "      \"details\": {}\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertTrue(result.isSuccess());
        assertEquals("success", result.getDocumentId());
    }

    @Test
    void shouldHandleEmptyFhirIssuesArray() throws Exception {
        String responseBody = "{\n" +
            "  \"resourceType\": \"OperationOutcome\",\n" +
            "  \"id\": \"fallback-id\",\n" +
            "  \"issue\": []\n" +
            "}";

        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertTrue(result.isSuccess());
        assertEquals("fallback-id", result.getDocumentId());
    }

    @Test
    void shouldHandleMalformedJsonResponse() throws Exception {
        ResponseEntity<String> response = new ResponseEntity<>("{invalid-json", HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertTrue(result.isSuccess());
        assertNull(result.getDocumentId());
    }

    @Test
    void shouldHandleTimeoutException() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertFalse(result.isSuccess());
        assertEquals(0, result.getStatusCode());
        assertTrue(result.getErrorMessage().contains("timed out"));
    }

    @Test
    void shouldHandleServerError() {
        String errorBody = "{\"error\":\"Internal server error\",\"message\":\"Failed to process document\"}";
        HttpStatusCodeException exception = new HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                errorBody.getBytes(),
                null
        );

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(exception);

        UploadResult result = uploadService.uploadDocument(testFile, "jwt-token", "signature", null);

        assertFalse(result.isSuccess());
        assertEquals(500, result.getStatusCode());
        assertEquals("Failed to process document", result.getErrorMessage());
        assertEquals(errorBody, result.getDetailedError());
    }

    @Test
    void shouldHandleContentTypeDetection() throws Exception {
        File pdfFile = tempDir.resolve("document.pdf").toFile();
        Files.writeString(pdfFile.toPath(), "PDF content");

        String responseBody = "{\"documentId\":\"doc-123\"}";
        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(response);

        assertDoesNotThrow(() -> uploadService.uploadDocument(pdfFile, "jwt-token", "signature", null));
    }
}
