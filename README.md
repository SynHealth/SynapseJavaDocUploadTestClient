# Document Upload Test Client
## Internal Technical Documentation

**Version:** 1.0.0  
**Last Updated:** June 25, 2025  
**Maintainer:** Harry Dhillon

---

## Purpose and Scope

This document provides technical details of the Document Upload Test Client for internal developers and support staff. The client was created to help customers validate their integration with our binary document upload API before moving to production environments.


## Architecture Overview

The Document Upload Test Client is a Java-based command-line application built with Spring Boot. It follows a modular design with clear separation of concerns:

```
com.synapsehealth.doc_uploader_test_client
├── MainRunner.java            # Entry point and workflow orchestration
├── config
│   ├── AppConfig.java         # Configuration model
│   └── ConfigLoader.java      # Loads config from CLI or files
├── auth
│   └── AuthService.java       # JWT token acquisition
├── crypto
│   └── SignatureService.java  # Document signing
├── upload
│   └── UploadService.java     # HTTP upload handling
├── model
│   └── UploadResult.java      # Result model
└── utils
    └── HttpClientUtil.java    # HTTP utility functions
```

### Technical Stack

- **Java 21** - Core language
- **Spring Boot 4.1.0** - Application framework
- **Picocli 4.7.5** - Command-line parsing
- **Bouncy Castle 1.85** - Cryptographic operations
- **Jackson** - JSON/YAML handling

## Key Components

### 1. MainRunner

The application entry point that:
- Parses command line arguments using Picocli
- Loads configuration from CLI or config files
- Orchestrates the authentication, signing, and upload workflow
- Provides formatted output to the user

### 2. Authentication (AuthService)

Handles OAuth2 client credentials flow for secure API access:
- Makes HTTP request to token endpoint with client credentials
- Includes scope parameter (new requirement)
- Parses JWT from response
- Implements token caching to avoid unnecessary authentication calls
- Handles token refresh when expired

### 3. Document Signing (SignatureService)

Implements the security requirements for document validation:
- Reads the document as a byte array
- Signs the document bytes directly using SHA256withRSA algorithm
- Base64-encodes the signature for transmission

**Important Note:** You can incorrectly perform double-hashing by first computing a SHA-256 hash of the document and then passing that hash to the SHA256withRSA algorithm (which performs its own hashing). The implementation passes the raw document bytes directly to the signature algorithm, which matches the server expectation.

Key security considerations:
- Uses Java Security and Bouncy Castle for cryptographic operations
- Never transmit the private key
- Supports PKCS#1 and PKCS#8 key formats

### 4. Document Upload (UploadService)

Handles the actual document transmission to our API:
- Detects a file type and sets the appropriate content type
- Adds required security headers:
  - `Authorization: Bearer <jwt>`
  - `x-signature: <base64-signature>`
- Handles HTTP responses and error conditions
- Implements retry logic for transient failures
- Supports both FHIR and REST API response formats

**Response Format Handling:**
- FHIR endpoint returns OperationOutcome objects with document ID embedded in a nested structure
- REST endpoint returns a simpler JSON with top-level documentId
- The service now intelligently parses both formats to extract the document ID

## Workflow Sequence

1. **Configuration Loading**
   - Command line args parsed → AppConfig POJO created
   - OR config file loaded → AppConfig POJO created
   - Configuration validated for required fields

2. **Authentication Flow**
   - AuthService requests JWT token from identity server
   - Token cached for reuse (For sdk evolution, this will be replaced with a more robust token management system)
   - Token added to Authorization header

3. **Document Security Flow**
   - Document read into memory
   - Document signed directly with SHA256withRSA
   - Signature base64-encoded
   - Signature added to request header

4. **Upload Flow**
   - Document sent as raw binary data with the appropriate content type
   - Security headers added
   - Request sent to API (either FHIR or REST endpoint)
   - Response parsed based on the endpoint type
   - Result displayed to user

## Error Handling

The client implements comprehensive error handling:

- **Authentication errors** - Issues with client credentials or token endpoint
- **Key loading errors** - Problems with the RSA private key format
- **Signing errors** - Cryptographic operation failures
- **Network errors** - Connection issues, timeouts
- **API errors** - HTTP error responses from the API

All errors provide meaningful error messages and optional verbose output to assist with debugging.

## Configuration Options

The client supports both command-line arguments and configuration files (YAML or .properties format). Key configuration parameters include:

- File path
- Private key path
- OAuth2 client credentials
- OAuth2 scope
- API endpoint selection (FHIR or REST)
- Verbose logging toggle (Optional)
- Retry count (Optional)

### Endpoint Selection

The client now supports two predefined API endpoints:
1. **FHIR Endpoint**: `https://integrations-api-phi.synapsehealth.dev/api/v1/fhir/binary`
   - Use direct binary upload with appropriate content type headers
   - Parses complex FHIR OperationOutcome responses
   - Extracts document ID from nested issue.details.coding structure

2. **REST Endpoint**: `https://integrations-api-phi.synapsehealth.dev/api/v1/documents`
   - Use direct binary upload with appropriate content type headers
   - Parses simple JSON responses
   - Extracts document ID from top-level field

The endpoint can be selected either through the `--api-endpoint-type` parameter (values: "fhir" or "rest") or by directly specifying a custom URL with `--api-url`.

## Response Format Handling

The client can handle different API response formats:

### FHIR Binary Response (OperationOutcome)

```json
{
  "resourceType": "OperationOutcome",
  "id": "success",
  "issue": [
    {
      "severity": "information",
      "code": "informational",
      "details": {
        "coding": [
          {
            "system": "urn:synapse:documentId",
            "code": "synapse-document-4496cd9e-0c9c-48e6-a85e-a679af2baa11"
          }
        ]
      }
    }
  ]
}
```

### REST API Response

```json
{
  "documentId": "synapse-document-f52c4547-fdce-486d-8fb1-5edb645a7f49",
  "contentType": "application/octet-stream",
  "size": 1755429
}
```

The parsing logic first checks for the FHIR format by looking for the "resourceType" field. If present and set to "OperationOutcome", it navigates the complex structure to extract the document ID from the coding array. Otherwise, it falls back to checking for top-level documentId or id fields.

## Building and Distribution

### Building from Source

```bash
./gradlew clean build shadowJar
```

The build produces a standalone JAR at `build/libs/doc-uploader-test-client.jar`

### Release Process

1. Update version in build.gradle
2. Run full test suite
3. Build the JAR
4. Upload JAR to customer portal
5. Update documentation with the new version

## Security Considerations

1. **Credential Handling**
   - Client credentials are never logged (Unless explicitly requested in verbose mode)
   - Credentials can be loaded from environment variables

2. **Document Security**
   - Documents are signed using industry-standard RSA cryptography
   - Private keys never leave the client destination
   - Implementation matches the same signing process required by server validation

3. **Network Security**
   - All communications use HTTPS
   - Certificate validation enabled
   - Proper headers for authentication and validation

## Known Issues

None at this time.

## Support

For internal technical issues with the client, contact the development team at Synapse.

