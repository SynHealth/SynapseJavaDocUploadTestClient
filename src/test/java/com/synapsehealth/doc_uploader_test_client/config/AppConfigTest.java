package com.synapsehealth.doc_uploader_test_client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void shouldValidateCompleteConfig() {
        AppConfig validConfig = AppConfig.builder()
                .filePath("/path/to/file.pdf")
                .privateKeyPath("/path/to/key.pem")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .tokenUrl("https://test-token.url")
                .scope("client_credentials")
                .apiUrl("https://test-api.url")
                .build();

        assertDoesNotThrow(validConfig::validate);
    }

    @Test
    void shouldRejectIncompleteConfig() {
        AppConfig incompleteConfig = AppConfig.builder()
                .filePath("/path/to/file.pdf")
                .privateKeyPath("/path/to/key.pem")
                // Missing clientId
                .clientSecret("test-client-secret")
                .tokenUrl("https://test-token.url")
                .apiUrl("https://test-api.url")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                incompleteConfig::validate
        );

        assertTrue(exception.getMessage().contains("client ID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fhir", "rest", "FHIR", "REST"})
    void shouldAcceptValidEndpointTypes(String endpointType) {
        AppConfig config = AppConfig.builder()
                .filePath("/path/to/file.pdf")
                .privateKeyPath("/path/to/key.pem")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .tokenUrl("https://test-token.url")
                .scope("client_credentials")
                .apiUrl("https://test-api.url")
                .apiEndpointType(endpointType)
                .build();

        assertDoesNotThrow(config::validate);
    }

    @Test
    void shouldRejectInvalidEndpointType() {
        AppConfig config = AppConfig.builder()
                .filePath("/path/to/file.pdf")
                .privateKeyPath("/path/to/key.pem")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .tokenUrl("https://test-token.url")
                .apiUrl("https://test-api.url")
                .scope("client_credentials")
                .apiEndpointType("invalid-type")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
        );

        assertTrue(exception.getMessage().contains("API endpoint type"));
    }

    @Test
    void shouldResolveApiUrlBasedOnEndpointTypeFhir() {
        AppConfig config = AppConfig.builder()
                .apiEndpointType("fhir")
                .build();

        String resolvedUrl = config.resolveApiUrl();

        assertEquals("https://integrations-api-phi.synapsehealth.dev/api/v1/fhir/binary", resolvedUrl);
    }

    @Test
    void shouldResolveApiUrlBasedOnEndpointTypeRest() {
        AppConfig config = AppConfig.builder()
                .apiEndpointType("rest")
                .build();

        String resolvedUrl = config.resolveApiUrl();

        assertEquals("https://integrations-api-phi.synapsehealth.dev/api/v1/documents", resolvedUrl);
    }

    @Test
    void shouldPreferExplicitApiUrlOverEndpointType() {
        AppConfig config = AppConfig.builder()
                .apiUrl("https://explicit-api.url")
                .apiEndpointType("rest")
                .build();

        String resolvedUrl = config.resolveApiUrl();

        assertEquals("https://explicit-api.url", resolvedUrl);
    }
}
