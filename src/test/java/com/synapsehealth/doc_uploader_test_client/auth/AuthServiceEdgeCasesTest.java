package com.synapsehealth.doc_uploader_test_client.auth;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceEdgeCasesTest {

    @Mock
    private RestTemplate mockRestTemplate;

    private AuthService authService;
    private AppConfig config;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        config = AppConfig.builder()
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .tokenUrl("https://test-token.url")
                .scope("test-scope")
                .build();

        authService = new AuthService(config);

        Field restTemplateField = AuthService.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(authService, mockRestTemplate);
    }

    @Test
    void shouldHandleEmptyAccessToken() throws Exception {
        String tokenResponse = "{\"access_token\": \"\", \"expires_in\": 3600}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        Exception exception = assertThrows(Exception.class, () -> {
            authService.getToken();
        });

        assertTrue(exception.getMessage().contains("Empty access token"));
    }

    @Test
    void shouldHandleNullAccessToken() throws Exception {
        String tokenResponse = "{\"access_token\": \" \", \"expires_in\": 3600}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        Exception exception = assertThrows(Exception.class, () -> {
            authService.getToken();
        });

        assertTrue(exception.getMessage().contains("Authentication failed: Empty access token"));
    }

    @Test
    void shouldHandleNetworkFailure() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new ResourceAccessException("Network Error", new ConnectException("Connection refused")));

        Exception exception = assertThrows(Exception.class, () -> {
            authService.getToken();
        });

        assertTrue(exception.getMessage().contains("Failed to connect to authentication server"));
    }

    @Test
    void shouldHandleMalformedJsonResponse() {
        ResponseEntity<String> mockResponse = new ResponseEntity<>("{invalid-json", HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        Exception exception = assertThrows(Exception.class, () -> {
            authService.getToken();
        });

        assertTrue(exception.getMessage().contains("Failed to parse authentication response"));
    }

    @Test
    void shouldReuseExistingValidToken() throws Exception {
        String tokenResponse = "{\"access_token\": \"test-jwt-token\", \"expires_in\": 3600}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        String token1 = authService.getToken();
        String token2 = authService.getToken();

        assertEquals(token1, token2);
        verify(mockRestTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void shouldHandleExpiryTimeZero() throws Exception {
        String tokenResponse = "{\"access_token\": \"test-jwt-token\", \"expires_in\": 0}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        String token = authService.getToken();

        assertEquals("test-jwt-token", token);
    }
}
