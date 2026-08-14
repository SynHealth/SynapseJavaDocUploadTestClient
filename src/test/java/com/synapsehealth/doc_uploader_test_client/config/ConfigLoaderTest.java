package com.synapsehealth.doc_uploader_test_client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateConfigFromCliArgs() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String tokenUrl = "https://test-token.url";
        String apiUrl = "https://test-api.url";
        String apiEndpointType = "fhir";
        String scope = "api scope";
        String grantType = "client_credentials";
        String patientId = "12345";
        boolean verbose = true;
        int retryCount = 5;

        AppConfig config = ConfigLoader.createFromCliArgs(
                filePath, privateKeyPath, clientId, clientSecret,
                tokenUrl, apiUrl, apiEndpointType, scope, grantType, patientId, verbose, retryCount
        );

        assertEquals(filePath, config.getFilePath());
        assertEquals(privateKeyPath, config.getPrivateKeyPath());
        assertEquals(clientId, config.getClientId());
        assertEquals(clientSecret, config.getClientSecret());
        assertEquals(tokenUrl, config.getTokenUrl());
        assertEquals(apiUrl, config.getApiUrl());
        assertEquals(apiEndpointType, config.getApiEndpointType());
        assertEquals(scope, config.getScope());
        assertEquals(patientId, config.getPatientId());
        assertTrue(config.isVerbose());
        assertEquals(retryCount, config.getRetryCount());
    }

    @Test
    void shouldUseDefaultTokenUrlWhenNotProvided() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String tokenUrl = null; // Not provided
        String apiUrl = "https://test-api.url";
        String apiEndpointType = "rest";
        String scope = "api scope";
        String grantType = "client_credentials";
        String patientId = "12345";
        boolean verbose = false;
        int retryCount = 3;

        AppConfig config = ConfigLoader.createFromCliArgs(
                filePath, privateKeyPath, clientId, clientSecret,
                tokenUrl, apiUrl, apiEndpointType, scope, grantType, patientId, verbose, retryCount
        );

        assertEquals("https://login.microsoftonline.com/ae3a12b3-a1b6-492d-81fd-be75596c89b9/oauth2/v2.0/token",
                config.getTokenUrl());
    }

    @Test
    void shouldResolveApiUrlFromEndpointType() {
        AppConfig config = ConfigLoader.createFromCliArgs(
                "file.pdf", "key.pem", "id", "secret",
                null, null, "fhir", "scope", null,
                "client_credentials", false, 3
        );

        assertEquals("https://integrations-api-phi.synapsehealth.dev/api/v1/fhir/binary",
                config.getApiUrl());

        AppConfig config2 = ConfigLoader.createFromCliArgs(
                "file.pdf", "key.pem", "id", "secret",
                null, null, "rest", "scope",
                "client_credentials", null, false, 3
        );

        assertEquals("https://integrations-api-phi.synapsehealth.dev/api/v1/documents",
                config2.getApiUrl());
    }

    @Test
    void shouldLoadConfigFromYamlFile() throws IOException {
        String yamlContent =
                "file: test.pdf\n" +
                "privateKey: test.pem\n" +
                "clientId: yaml-client-id\n" +
                "clientSecret: yaml-client-secret\n" +
                "tokenUrl: https://yaml-token.url\n" +
                "apiUrl: https://yaml-api.url\n" +
                "scope: yaml-scope\n" +
                "patientId: yaml-patient\n" +
                "verbose: true\n" +
                "retryCount: 4";

        Path yamlFile = tempDir.resolve("test-config.yaml");
        Files.writeString(yamlFile, yamlContent);

        AppConfig config = ConfigLoader.loadFromFile(yamlFile.toString());

        assertEquals("test.pdf", config.getFilePath());
        assertEquals("test.pem", config.getPrivateKeyPath());
        assertEquals("yaml-client-id", config.getClientId());
        assertEquals("yaml-client-secret", config.getClientSecret());
        assertEquals("https://yaml-token.url", config.getTokenUrl());
        assertEquals("https://yaml-api.url", config.getApiUrl());
        assertEquals("yaml-scope", config.getScope());
        assertEquals("yaml-patient", config.getPatientId());
        assertTrue(config.isVerbose());
        assertEquals(4, config.getRetryCount());
    }

    @Test
    void shouldLoadConfigFromPropertiesFile() throws IOException {
        String propertiesContent =
                "file=test.pdf\n" +
                "privateKey=test.pem\n" +
                "clientId=props-client-id\n" +
                "clientSecret=props-client-secret\n" +
                "tokenUrl=https://props-token.url\n" +
                "apiUrl=https://props-api.url\n" +
                "scope=props-scope\n" +
                "patientId=props-patient\n" +
                "verbose=true\n" +
                "retryCount=5";

        Path propsFile = tempDir.resolve("test-config.properties");
        Files.writeString(propsFile, propertiesContent);

        AppConfig config = ConfigLoader.loadFromFile(propsFile.toString());

        assertEquals("test.pdf", config.getFilePath());
        assertEquals("test.pem", config.getPrivateKeyPath());
        assertEquals("props-client-id", config.getClientId());
        assertEquals("props-client-secret", config.getClientSecret());
        assertEquals("https://props-token.url", config.getTokenUrl());
        assertEquals("https://props-api.url", config.getApiUrl());
        assertEquals("props-scope", config.getScope());
        assertEquals("props-patient", config.getPatientId());
        assertTrue(config.isVerbose());
        assertEquals(5, config.getRetryCount());
    }

    @Test
    void shouldHandleInvalidConfigFileFormat() {
        String invalidFilePath = "/path/to/invalid.txt";

        assertThrows(FileNotFoundException.class, () ->
                ConfigLoader.loadFromFile(invalidFilePath));
    }

    @Test
    void shouldHandleNonExistentConfigFile() {
        String nonExistentFilePath = tempDir.resolve("non-existent.yaml").toString();

        assertThrows(IOException.class, () ->
                ConfigLoader.loadFromFile(nonExistentFilePath));
    }

    @Test
    void shouldHandleInvalidYamlFormat() throws IOException {
        String invalidYamlContent = "This is not valid YAML content\nclientId: test: test";
        Path invalidYamlFile = tempDir.resolve("invalid.yaml");
        Files.writeString(invalidYamlFile, invalidYamlContent);

        assertThrows(IOException.class, () ->
                ConfigLoader.loadFromFile(invalidYamlFile.toString()));
    }
}
