package com.synapsehealth.doc_uploader_test_client.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import com.synapsehealth.doc_uploader_test_client.utils.HttpClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.Base64;

/**
 * Service responsible for obtaining and managing JWT tokens.
 *
 * This service handles authentication with the identity server, including token
 * request, caching, and automatic refresh. It provides robust error handling
 * for network issues, invalid credentials, and malformed responses.
 */
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    /**
     * Minimum token expiry buffer in seconds to prevent using a token that's about to expire
     */
    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 30;

    /**
     * Default token expiration time in seconds (1 hour) if not specified by the server
     */
    private static final int DEFAULT_TOKEN_EXPIRY_SECONDS = 3600;

    private final AppConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private String cachedToken;
    private long tokenExpiryTime;

    /**
     * Creates a new instance of the AuthService.
     *
     * @param config The application configuration containing authentication settings
     */
    public AuthService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = HttpClientUtil.createRestTemplate(config.getRetryCount());
    }

    /**
     * Gets a valid JWT token, either from cache or by requesting a new one.
     * Handles token caching and automatic refresh when expired.
     *
     * @return A valid JWT token
     * @throws Exception if authentication fails
     */
    public String getToken() throws Exception {
        if (isTokenValid()) {
            logger.debug("Using cached token");
            return cachedToken;
        }

        logger.info("Requesting new token from identity server");
        return requestNewToken();
    }

    /**
     * Checks if the cached token is still valid.
     *
     * @return true if the token is valid and not about to expire, false otherwise
     */
    private boolean isTokenValid() {
        if (cachedToken == null || cachedToken.isEmpty()) {
            return false;
        }

        // Add a buffer to avoid edge cases with token expiration
        return Instant.now().getEpochSecond() < (tokenExpiryTime - TOKEN_EXPIRY_BUFFER_SECONDS);
    }

    /**
     * Requests a new token from the identity server.
     *
     * @return A fresh JWT token
     * @throws Exception with detailed error message if the token request fails
     */
    private String requestNewToken() throws Exception {
        try {
            // Prepare request headers and body
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", config.getGrantType());
            requestBody.add("client_id", config.getClientId());
            requestBody.add("client_secret", config.getClientSecret());
            requestBody.add("scope", config.getScope());

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            if (config.isVerbose()) {
                logger.info("Token request URL: {}", config.getTokenUrl());
                logger.info("Token request body: {}", requestBody);
            }

            // Make the request to the identity server
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getTokenUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseTokenResponse(response.getBody());
            } else {
                throw new Exception("Authentication failed: Empty or invalid response from identity server");
            }
        } catch (ResourceAccessException e) {
            // Handle network-related exceptions with helpful messages
            if (e.getCause() instanceof ConnectException) {
                throw new Exception("Failed to connect to authentication server. Please check your network connection and token URL.", e);
            }
            throw new Exception("Authentication failed: Network error while connecting to identity server", e);
        } catch (HttpClientErrorException e) {
            // Handle HTTP error responses
            String errorDetails = extractErrorDetails(e);
            throw new Exception("Authentication failed: " + errorDetails, e);
        } catch (Exception e) {
            // Catch-all for other unexpected errors
            throw new Exception("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the token response from the identity server.
     *
     * @param responseBody The JSON response body from the token request
     * @return The parsed access token
     * @throws Exception if the response cannot be parsed or is missing required fields
     */
    private String parseTokenResponse(String responseBody) throws Exception {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Validate that access_token exists and is not empty
            if (!root.has("access_token")) {
                throw new Exception("Invalid token response: Missing access_token field");
            }

            String accessToken = root.get("access_token").asText();
            if (accessToken == null || accessToken.trim().isEmpty()) {
                throw new Exception("Empty access token received from identity server");
            }

            // Parse expiration time. Default to 1 hour if not provided
            int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt() : DEFAULT_TOKEN_EXPIRY_SECONDS;
            tokenExpiryTime = Instant.now().getEpochSecond() + expiresIn;

            if (config.isVerbose()) {
                logger.info("Received token. Expires in {} seconds", expiresIn);
            }

            // Update the cache
            cachedToken = accessToken;
            return accessToken;
        } catch (IOException e) {
            throw new Exception("Failed to parse authentication response: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts detailed error information from an HTTP error response.
     *
     * @param e The HTTP client exception
     * @return A meaningful error message
     */
    private String extractErrorDetails(HttpClientErrorException e) {
        try {
            // Try to parse JSON error response
            String responseBody = e.getResponseBodyAsString();
            if (responseBody != null && !responseBody.isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(responseBody);
                if (errorNode.has("error_description")) {
                    return errorNode.get("error_description").asText();
                } else if (errorNode.has("error")) {
                    return errorNode.get("error").asText();
                }
            }
        } catch (Exception ignored) {
            // If we can't parse the response, fall back to the status text
        }

        // Fall back to HTTP status if JSON parsing fails
        return switch (e.getStatusCode().value()) {
            case 400 -> "Bad Request - Invalid client credentials or grant type";
            case 401 -> "Unauthorized - Client credentials rejected";
            case 403 -> "Forbidden - Client lacks permission for requested scope";
            case 429 -> "Too Many Requests - Rate limit exceeded, try again later";
            default -> e.getStatusText();
        };
    }

    /**
     * Forces the service to refresh the token on the next request.
     * Useful for testing or when a token is known to be invalid.
     */
    public void invalidateToken() {
        this.cachedToken = null;
        this.tokenExpiryTime = 0;
    }
}
