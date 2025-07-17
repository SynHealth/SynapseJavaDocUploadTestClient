package com.synapsehealth.doc_uploader_test_client.auth;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    void shouldGetTokenSuccessfully() throws Exception {
        String tokenResponse = "{\"access_token\": \"test-jwt-token\", \"expires_in\": 3600}";
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

    @Test
    void shouldThrowExceptionWhenTokenRequestFails() {
        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        Exception exception = assertThrows(Exception.class, () -> {
            authService.getToken();
        });

        assertTrue(exception.getMessage().contains("Authentication failed"));
    }

    @Test
    void shouldThrowExceptionWhenTokenResponseIsMalformed() {
        String invalidResponse = "{\"not_a_token\": \"something-else\"}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(invalidResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        Exception exception = assertThrows(Exception.class, () -> {
            authService.getToken();
        });

        assertTrue(exception.getMessage().contains("Authentication failed: Invalid token response: Missing access_token field"));
    }

    @Test
    void shouldCacheTokenAndReuseIt() throws Exception {
        String tokenResponse = "{\"access_token\": \"cached-token\", \"expires_in\": 3600}";
        ResponseEntity<String> mockResponse = new ResponseEntity<>(tokenResponse, HttpStatus.OK);

        when(mockRestTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(mockResponse);

        String firstToken = authService.getToken();

        String secondToken = authService.getToken();

        assertEquals("cached-token", firstToken);
        assertEquals("cached-token", secondToken);
    }
}
