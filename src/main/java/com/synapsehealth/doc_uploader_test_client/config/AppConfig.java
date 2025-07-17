package com.synapsehealth.doc_uploader_test_client.config;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Configuration for the document upload test client.
 * Contains all settings needed for the application to run.
 *
 * This class holds configuration values loaded from command line arguments
 * or configuration files and provides validation to ensure all required
 * values are present and valid.
 */
@Data
@Builder
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    /**
     * Valid endpoint types that can be used with the API
     */
    private static final List<String> VALID_ENDPOINT_TYPES = Arrays.asList("fhir", "rest");

    /**
     * Path to the document file to upload
     */
    private String filePath;

    /**
     * Path to the RSA private key file (PEM format)
     */
    private String privateKeyPath;

    /**
     * OAuth2 client ID for authentication
     */
    private String clientId;

    /**
     * OAuth2 client secret for authentication
     */
    private String clientSecret;

    /**
     * URL of the identity server's token endpoint
     */
    private String tokenUrl;

    /**
     * Custom API URL for document uploads (takes precedence over apiEndpointType)
     */
    private String apiUrl;

    /**
     * API endpoint type, either "fhir" or "rest"
     */
    private String apiEndpointType;

    /**
     * OAuth2 scope value for authentication
     */
    private String scope;

    /**
     * OAuth2 grant type for authentication (defaults to "client_credentials")
     */
    private String grantType;

    /**
     * Optional patient identifier for document metadata
     */
    private String patientId;

    /**
     * Enable verbose logging and output
     */
    private boolean verbose;

    /**
     * Number of retry attempts for transient failures
     */
    private int retryCount;

    /**
     * Validates that all required configuration is present and valid.
     *
     * @throws IllegalArgumentException if any required configuration is missing or invalid
     */
    public void validate() throws IllegalArgumentException {
        List<String> missingFields = new ArrayList<>();
        List<String> invalidFields = new ArrayList<>();

        // Check for required fields
        if (isEmpty(filePath)) missingFields.add("file path");
        if (isEmpty(privateKeyPath)) missingFields.add("private key path");
        if (isEmpty(clientId)) missingFields.add("client ID");
        if (isEmpty(clientSecret)) missingFields.add("client secret");
        if (isEmpty(tokenUrl)) missingFields.add("token URL");
        if (isEmpty(scope)) missingFields.add("scope");

        // At least one of apiUrl or apiEndpointType must be provided
        if (isEmpty(apiUrl) && isEmpty(apiEndpointType)) {
            missingFields.add("API URL or API endpoint type");
        }

        // Validate endpoint type if provided
        if (!isEmpty(apiEndpointType) && !isValidEndpointType(apiEndpointType)) {
            invalidFields.add("API endpoint type must be either 'fhir' or 'rest', but was: " + apiEndpointType);
        }

        // Validate retry count
        if (retryCount < 0) {
            invalidFields.add("retry count must be non-negative, but was: " + retryCount);
        }

        // Report missing fields
        if (!missingFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required configuration: " + String.join(", ", missingFields));
        }

        // Report invalid fields
        if (!invalidFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid configuration values: " + String.join(", ", invalidFields));
        }

        // Log successful validation for debugging
        if (verbose) {
            logger.debug("Configuration validation successful");
        }
    }

    /**
     * Resolves the actual API URL based on the endpoint type if not explicitly provided.
     *
     * @return The resolved API URL
     * @throws IllegalArgumentException if the API endpoint type is invalid
     */
    public String resolveApiUrl() {
        if (!isEmpty(apiUrl)) {
            return apiUrl;
        }

        if (!isValidEndpointType(apiEndpointType)) {
            throw new IllegalArgumentException(
                    "Invalid API endpoint type: " + apiEndpointType + ". Must be either 'fhir' or 'rest'.");
        }

        // Default URLs based on endpoint type
        if ("fhir".equalsIgnoreCase(apiEndpointType)) {
            return "https://integrations-api.synapsehealth.dev/api/v1/fhir/binary";
        } else {
            // REST endpoint
            return "https://integrations-api.synapsehealth.dev/api/v1/documents";
        }
    }

    /**
     * Checks if a string is null or empty.
     *
     * @param str The string to check
     * @return true if the string is null or empty, false otherwise
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if the provided endpoint type is valid.
     *
     * @param endpointType The endpoint type to check
     * @return true if valid, false otherwise
     */
    private boolean isValidEndpointType(String endpointType) {
        if (isEmpty(endpointType)) {
            return false;
        }

        return VALID_ENDPOINT_TYPES.stream()
                .anyMatch(validType -> validType.equalsIgnoreCase(endpointType));
    }
}
