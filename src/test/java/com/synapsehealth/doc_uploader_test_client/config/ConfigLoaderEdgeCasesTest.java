package com.synapsehealth.doc_uploader_test_client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigLoaderEdgeCasesTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldHandleMissingRequiredFieldsInYaml() throws Exception {
        File configFile = tempDir.resolve("incomplete-config.yaml").toFile();
        String incompleteYaml = "clientId: test-client-id\n" +
                               "# Missing clientSecret\n" +
                               "scope: test-scope\n";
        Files.writeString(configFile.toPath(), incompleteYaml);

        Exception exception = assertThrows(Exception.class, () -> {
            ConfigLoader.loadFromFile(configFile.getAbsolutePath());
        });

        assertTrue(exception.getMessage().contains("clientSecret"));
    }

    @Test
    void shouldHandleEmptyConfigFile() throws Exception {
        File configFile = tempDir.resolve("empty-config.yaml").toFile();
        Files.writeString(configFile.toPath(), "");

        Exception exception = assertThrows(IOException.class, () -> {
            ConfigLoader.loadFromFile(configFile.getAbsolutePath());
        });

        assertTrue(exception.getMessage().contains("Failed to load configuration"));
    }

    @Test
    void shouldHandleNonExistentConfigFile() {
        String nonExistentPath = "/path/to/nonexistent/config.yaml";

        Exception exception = assertThrows(IOException.class, () -> {
            ConfigLoader.loadFromFile(nonExistentPath);
        });

        assertTrue(exception.getMessage().contains("not found") ||
                   exception.getMessage().contains("does not exist"));
    }

    @Test
    void shouldHandleInvalidYamlFormat() throws Exception {
        File configFile = tempDir.resolve("invalid-yaml.yaml").toFile();
        String invalidYaml = "clientId: test-client-id\n" +
                            "clientSecret: 'unclosed string\n" +
                            "scope: test-scope";
        Files.writeString(configFile.toPath(), invalidYaml);

        Exception exception = assertThrows(IOException.class, () -> {
            ConfigLoader.loadFromFile(configFile.getAbsolutePath());
        });

        assertTrue(exception.getMessage().contains("Failed to parse"));
    }

    @Test
    void shouldHandlePropertiesFileFormat() throws Exception {
        File configFile = tempDir.resolve("config.properties").toFile();
        Properties props = new Properties();
        props.setProperty("file", "/path/to/doc.pdf");
        props.setProperty("privateKey", "/path/to/private_key.pem");
        props.setProperty("clientId", "test-client-id");
        props.setProperty("clientSecret", "test-client-secret");
        props.setProperty("scope", "test-scope");
        props.setProperty("apiEndpointType", "fhir");
        props.setProperty("verbose", "true");

        try (var out = Files.newOutputStream(configFile.toPath())) {
            props.store(out, "Test config");
        }

        AppConfig config = ConfigLoader.loadFromFile(configFile.getAbsolutePath());

        assertEquals("/path/to/doc.pdf", config.getFilePath());
        assertEquals("/path/to/private_key.pem", config.getPrivateKeyPath());
        assertEquals("test-client-id", config.getClientId());
        assertEquals("test-client-secret", config.getClientSecret());
        assertEquals("test-scope", config.getScope());
        assertEquals("fhir", config.getApiEndpointType());
        assertTrue(config.isVerbose());
    }

    @Test
    void shouldHandleCommandLineFlagsWithEndpointType() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private_key.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String scope = "test-scope";
        String apiEndpointType = "rest";

        AppConfig config = ConfigLoader.createFromCliArgs(
                filePath, privateKeyPath, clientId, clientSecret,
                null, null, apiEndpointType, scope, "client_credentials",
                null, true, 5
        );

        assertEquals("rest", config.getApiEndpointType());
        assertTrue(config.getApiUrl().contains("documents"));
        assertFalse(config.getApiUrl().contains("fhir"));
    }

    @Test
    void shouldHandleCommandLineArgsWithCustomApiUrl() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private_key.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String scope = "test-scope";
        String customApiUrl = "https://custom.api.example.com/upload";

        AppConfig config = ConfigLoader.createFromCliArgs(
                filePath, privateKeyPath, clientId, clientSecret,
                null, customApiUrl, null, scope, "client_credentials",
                null, true, 5
        );

        assertEquals(customApiUrl, config.getApiUrl());
        assertNull(config.getApiEndpointType());
    }

    @Test
    void shouldThrowExceptionWhenBothApiUrlAndTypeMissing() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private_key.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String scope = "test-scope";

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            AppConfig config = ConfigLoader.createFromCliArgs(
                    filePath, privateKeyPath, clientId, clientSecret,
                    null, null, null, scope, "client_credentials",
                    null, true, 5
            );
            config.validate(); // This should throw the exception
        });

        assertTrue(exception.getMessage().contains("API endpoint") ||
                   exception.getMessage().contains("apiUrl"));
    }

    @Test
    void shouldHandleInvalidEndpointType() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private_key.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String scope = "test-scope";
        String invalidEndpointType = "invalid-type";


        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            AppConfig config = ConfigLoader.createFromCliArgs(
                    filePath, privateKeyPath, clientId, clientSecret,
                    null, null, invalidEndpointType, scope, "client_credentials",
                    null, false, 3
            );
            config.validate();
        });

        assertTrue(exception.getMessage().contains("Invalid API endpoint type"));
    }

    @Test
    void shouldHandleNegativeRetryCount() {
        String filePath = "/path/to/file.pdf";
        String privateKeyPath = "/path/to/private_key.pem";
        String clientId = "test-client-id";
        String clientSecret = "test-client-secret";
        String scope = "test-scope";
        int negativeRetryCount = -3;

        AppConfig config = ConfigLoader.createFromCliArgs(
                filePath, privateKeyPath, clientId, clientSecret,
                null, "https://api.example.com", null, scope, "client_credentials",
                null, false, negativeRetryCount
        );

        assertTrue(config.getRetryCount() >= 0);
    }
}
