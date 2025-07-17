package com.synapsehealth.doc_uploader_test_client.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import com.synapsehealth.doc_uploader_test_client.model.UploadResult;
import com.synapsehealth.doc_uploader_test_client.utils.HttpClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Objects;

/**
 * Service responsible for uploading documents to the API.
 */
public class UploadService {
    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);

    private final AppConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public UploadService(AppConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = HttpClientUtil.createRestTemplate(config.getRetryCount());
    }

    /**
     * Uploads a document to the API with authentication and signature.
     *
     * @param file The document file to upload
     * @param jwtToken The JWT token for authorization
     * @param signature The base64-encoded signature of the document
     * @param patientId Optional patient ID metadata
     * @return The upload result
     */
    public UploadResult uploadDocument(File file, String jwtToken, String signature, String patientId) {
        logger.info("Preparing to upload document: {}", file.getName());

        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<?> requestEntity;

            String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(".pdf")) {
                headers.setContentType(MediaType.APPLICATION_PDF);
            } else {
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }

            // For Binary endpoint, we send the file content directly as the request body
            FileSystemResource fileResource = new FileSystemResource(file);
            requestEntity = new HttpEntity<>(fileResource, headers);

            // If patientId is provided with FHIR endpoint, we may need to adjust the URL
            // Some FHIR implementations handle metadata through URL parameters
            if (patientId != null && !patientId.isEmpty()) {
                logger.info("Patient ID provided for FHIR endpoint: {}", patientId);
                // Could be handled through URL parameter if needed
            }

            // Common headers for both approaches
            headers.set("Authorization", "Bearer " + jwtToken);
            headers.set("x-signature", signature);

            if (config.isVerbose()) {
                logger.info("Upload URL: {}", config.getApiUrl());
                logger.info("Content-Type: {}", headers.getContentType());
                logger.info("Added headers: Authorization (Bearer token) and Signature");
            }

            // Send the request
            logger.info("Sending upload request...");
            ResponseEntity<String> response = restTemplate.exchange(
                    config.getApiUrl(),
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // Process successful response
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Upload successful: {}", response.getStatusCode());

                // Extract document ID from response if available
                String documentId = null;
                if (response.getBody() != null && !response.getBody().isEmpty()) {
                    try {
                        // Log the raw response for debugging when in verbose mode
                        if (config.isVerbose()) {
                            logger.info("Raw API response: {}", response.getBody());
                        }

                        JsonNode responseNode = objectMapper.readTree(response.getBody());

                        // First check if this is a FHIR OperationOutcome response
                        if (responseNode.has("resourceType") && "OperationOutcome".equals(responseNode.get("resourceType").asText())) {
                            logger.info("Detected FHIR OperationOutcome response");

                            // Check for document ID in issue[0].details.coding[0].code path
                            if (responseNode.has("issue") && responseNode.get("issue").isArray() && responseNode.get("issue").size() > 0) {
                                JsonNode issueNode = responseNode.get("issue").get(0);
                                if (issueNode.has("details") && issueNode.get("details").has("coding") &&
                                        issueNode.get("details").get("coding").isArray() &&
                                        issueNode.get("details").get("coding").size() > 0) {

                                    JsonNode codingNode = issueNode.get("details").get("coding").get(0);
                                    if (codingNode.has("system") &&
                                            "urn:synapse:documentId".equals(codingNode.get("system").asText()) &&
                                            codingNode.has("code")) {

                                        documentId = codingNode.get("code").asText();
                                        logger.info("Extracted document ID from FHIR OperationOutcome: {}", documentId);
                                    }
                                }
                            }

                            // Fallback to operation outcome ID if document ID not found in coding
                            if (documentId == null && responseNode.has("id")) {
                                documentId = responseNode.get("id").asText();
                                logger.info("Using OperationOutcome ID as document ID: {}", documentId);
                            }
                        }
                        // Check for standard REST API response format
                        else if (responseNode.has("documentId")) {
                            documentId = responseNode.get("documentId").asText();
                            logger.info("Extracted document ID from standard response: {}", documentId);
                        } else if (responseNode.has("id")) {
                            documentId = responseNode.get("id").asText();
                            logger.info("Using ID field as document ID: {}", documentId);
                        }
                    } catch (Exception e) {
                        logger.warn("Could not parse document ID from response", e);
                    }
                }

                return UploadResult.builder()
                        .success(true)
                        .statusCode(response.getStatusCode().value())
                        .statusMessage(response.getStatusCode().toString())
                        .documentId(documentId)
                        .build();
            } else {
                // This should not normally be reached due to Spring's exception handling
                return UploadResult.builder()
                        .success(false)
                        .statusCode(response.getStatusCode().value())
                        .statusMessage(response.getStatusCode().toString())
                        .errorMessage("Upload failed with status: " + response.getStatusCode())
                        .detailedError(response.getBody())
                        .build();
            }

        } catch (HttpStatusCodeException e) {
            // Handle HTTP error responses
            logger.error("Upload failed with status code: {}", e.getStatusCode());

            return UploadResult.builder()
                    .success(false)
                    .statusCode(e.getStatusCode().value())
                    .statusMessage(e.getStatusCode().toString())
                    .errorMessage(getErrorMessage(e))
                    .detailedError(e.getResponseBodyAsString())
                    .build();

        } catch (Exception e) {
            // Handle other exceptions (network, etc.)
            logger.error("Upload failed with exception", e);

            return UploadResult.builder()
                    .success(false)
                    .statusCode(0)
                    .statusMessage("Request Failed")
                    .errorMessage("Upload failed: " + e.getMessage())
                    .detailedError(e.toString())
                    .build();
        }
    }

    /**
     * Extracts a meaningful error message from HTTP exceptions
     */
    private String getErrorMessage(HttpStatusCodeException e) {
        try {
            e.getResponseBodyAsString();
            if (!e.getResponseBodyAsString().isEmpty()) {
                JsonNode errorNode = objectMapper.readTree(e.getResponseBodyAsString());
                if (errorNode.has("message")) {
                    return errorNode.get("message").asText();
                } else if (errorNode.has("error")) {
                    return errorNode.get("error").asText();
                }
            }
        } catch (Exception ignored) {
            // If we can't parse the error response, fall back to status text
        }

        return switch (e.getStatusCode().value()) {
            case 400 -> "Bad Request - Check your document format and parameters";
            case 401 -> "Unauthorized - Authentication token is invalid or expired";
            case 403 -> "Forbidden - You don't have permission to upload documents";
            case 404 -> "Not Found - The upload endpoint doesn't exist";
            case 413 -> "Request Entity Too Large - The document exceeds size limits";
            case 415 -> "Unsupported Media Type - Document format not supported";
            case 429 -> "Too Many Requests - Rate limit exceeded, try again later";
            case 500, 502, 503, 504 -> "Server Error - The API is experiencing issues";
            default -> e.getStatusText();
        };
    }
}
