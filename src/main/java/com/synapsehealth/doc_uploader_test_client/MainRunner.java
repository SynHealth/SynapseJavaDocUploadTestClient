package com.synapsehealth.doc_uploader_test_client;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import com.synapsehealth.doc_uploader_test_client.config.ConfigLoader;
import com.synapsehealth.doc_uploader_test_client.auth.AuthService;
import com.synapsehealth.doc_uploader_test_client.crypto.SignatureService;
import com.synapsehealth.doc_uploader_test_client.model.UploadResult;
import com.synapsehealth.doc_uploader_test_client.upload.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Main entry point for the document upload test client.
 * This class handles command line parsing and orchestrates the overall flow.
 */
@Command(
    name = "doc-uploader-test-client",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Test client for secure document upload API")
public class MainRunner implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(MainRunner.class);

    @Option(names = {"--file"}, description = "Path to the document to upload", required = false)
    private String filePath;

    @Option(names = {"--private-key"}, description = "Path to private RSA key file (PEM format)", required = false)
    private String privateKeyPath;

    @Option(names = {"--client-id"}, description = "OAuth2 client ID", required = false)
    private String clientId;

    @Option(names = {"--client-secret"}, description = "OAuth2 client secret", required = false)
    private String clientSecret;

    @Option(names = {"--token-url"}, description = "Identity server token endpoint (defaults to UAT)", required = false)
    private String tokenUrl;

    @Option(names = {"--api-url"}, description = "Document upload endpoint (specify this or --api-endpoint-type)", required = false)
    private String apiUrl;

    @Option(names = {"--api-endpoint-type"}, description = "API endpoint type (fhir or rest)", required = false)
    private String apiEndpointType;

    @Option(names = {"--scope"}, description = "OAuth2 scope value", required = false)
    private String scope;

    @Option(names = {"--grant-type"}, description = "OAuth2 grant type (default is client_credentials)", required = false)
    private String grantType = "client_credentials";

    @Option(names = {"--patient-id"}, description = "Optional metadata to include", required = false)
    private String patientId;

    @Option(names = {"--config"}, description = "Path to config file (YAML or .properties)", required = false)
    private String configFile;

    @Option(names = {"--verbose"}, description = "Enable verbose logging", required = false)
    private boolean verbose = false;

    @Option(names = {"--retry-count"}, description = "Number of retries for transient failures", required = false)
    private int retryCount = 3;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MainRunner()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try {
            // Step 1: Load configuration
            AppConfig config;
            if (configFile != null && !configFile.isBlank()) {
                // Using config file - no CLI arguments required
                logger.info("Loading configuration from file: {}", configFile);
                config = ConfigLoader.loadFromFile(configFile);
            } else {
                // Using command line arguments - validate required arguments
                validateRequiredCliArgs();

                logger.info("Using command line arguments for configuration");
                config = ConfigLoader.createFromCliArgs(
                    filePath, privateKeyPath, clientId, clientSecret,
                    tokenUrl, apiUrl, apiEndpointType, scope, grantType,
                    patientId, verbose, retryCount
                );
            }

            // Validate required configuration
            config.validate();

            // Step 2: Create services
            AuthService authService = new AuthService(config);
            SignatureService signatureService = new SignatureService(config);
            UploadService uploadService = new UploadService(config);

            // Step 3: Authenticate to get JWT token
            logger.info("Authenticating with identity server...");
            String jwtToken = authService.getToken();
            logger.info("Authentication successful");

            // Step 4: Read the file and generate signature
            File documentFile = new File(config.getFilePath());
            byte[] documentBytes = Files.readAllBytes(Path.of(config.getFilePath()));
            logger.info("Generating signature for document: {}", documentFile.getName());
            String signature = signatureService.signDocument(documentBytes);

            // Step 5: Upload document with signature and token
            logger.info("Uploading document to API endpoint: {}", config.getApiUrl());
            logger.info("Using API endpoint type: {}",
                config.getApiEndpointType() != null ? config.getApiEndpointType() : "custom URL");

            UploadResult result = uploadService.uploadDocument(documentFile, jwtToken, signature, config.getPatientId());

            // Step 6: Print result
            if (result.isSuccess()) {
                System.out.println("\n✅ Document uploaded successfully!");
                System.out.println("   - Status: " + result.getStatusCode() + " " + result.getStatusMessage());
                if (result.getDocumentId() != null) {
                    System.out.println("   - Document ID: " + result.getDocumentId());
                }
            } else {
                System.out.println("\n❌ Upload failed");
                System.out.println("   - Status: " + result.getStatusCode() + " " + result.getStatusMessage());
                System.out.println("   - Reason: " + result.getErrorMessage());
                if (config.isVerbose() && result.getDetailedError() != null) {
                    System.out.println("\nDetailed Error:");
                    System.out.println(result.getDetailedError());
                }
                return 1;
            }

            return 0;
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            } else {
                System.err.println("Run with --verbose for more details");
            }
            return 1;
        }
    }

    /**
     * Validates that required command line arguments are provided when not using a config file.
     * @throws IllegalArgumentException if required arguments are missing
     */
    private void validateRequiredCliArgs() {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Missing required option: --file");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Missing required option: --client-id");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Missing required option: --client-secret");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("Missing required option: --scope");
        }
        if ((apiUrl == null || apiUrl.isBlank()) &&
            (apiEndpointType == null || apiEndpointType.isBlank())) {
            throw new IllegalArgumentException(
                "Must provide either --api-url or --api-endpoint-type");
        }
    }
}
