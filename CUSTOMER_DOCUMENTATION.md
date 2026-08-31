# Document Upload Test Client
## Customer Documentation

**Version:** 1.0.0  
**Last Updated:** June 25, 2025  
**Contact:** support@synapsehealth.com

---

## Overview

The Document Upload Test Client is a secure, command-line tool designed to help you validate your integration with our document upload API before moving to production. This client handles authentication, document security, and file uploads in the exact same manner as your production integration will need to.

## Benefits

- **Validate Your Integration:** Test your setup with our API before going live
- **Security Compliance:** Ensure your documents meet our security standards
- **Troubleshooting:** Get detailed feedback if uploads aren't working correctly
- **Multi-Format Support:** Works with both our FHIR Binary and REST API endpoints
- **Reference Implementation:** Demonstrates the exact signing and authentication approach you'll need in your production code

## Requirements

- **Java Runtime Environment (JRE) 21+** or **OpenJDK 21+**
- Your OAuth2 client credentials (client ID and secret)
- Your RSA private key (in PEM format)
- The document(s) you want to test uploading

## Quick Start

1. **Set up Java:**
   - Install Java 21 or later from [Oracle](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://adoptium.net/)
   - Verify installation by running `java -version` in your terminal (it must report 21 or higher)

2. **Download the JAR:**
   - Download the test client JAR file from our [GitHub releases page](https://github.com/SynHealth/SynapseJavaDocUploadTestClient/releases)

3. **Run the client:**
   ```bash
   java -jar doc-uploader-test-client.jar \
     --file document.pdf \
     --private-key private_key.pem \
     --client-id YOUR_CLIENT_ID \
     --client-secret YOUR_CLIENT_SECRET \
     --scope api://fulfill-api-phi/.default \
     --api-endpoint-type fhir \
   ```

## Configuration Options

| Option                | Description                                   | Required | Default                  |
|-----------------------|-----------------------------------------------|----------|--------------------------|
| `--file`              | Path to the document file to upload           | Yes      | -                        |
| `--private-key`       | Path to your RSA private key (PEM format)     | Yes      | -                        |
| `--client-id`         | Your OAuth2 client ID                         | Yes      | -                        |
| `--client-secret`     | Your OAuth2 client secret                     | Yes      | -                        |
| `--scope`             | OAuth2 scope value                            | Yes      | -                        |
| `--token-url`         | Identity server token endpoint                | No       | UAT endpoint*            |
| `--api-endpoint-type` | API endpoint type (`fhir` or `rest`)          | No**     | -                        |
| `--api-url`           | Custom document upload API endpoint           | No**     | Depends on endpoint type |
| `--verbose`           | Enable detailed logging                       | No       | false                    |
| `--config`            | Path to config file (alternative to CLI args) | No       | -                        |
| `--retry-count`       | Number of retries for network failures        | No       | 3                        |
| `--help`              | Show help message                             | No       | -                        |

\* Default token URL: `https://login.microsoftonline.com/ae3a12b3-a1b6-492d-81fd-be75596c89b9/oauth2/v2.0/token`  
\** You must specify either `--api-endpoint-type` or `--api-url`

### API Endpoint Options

You have two options for specifying the document upload destination:

1. **Using `--api-endpoint-type`**:
   - `fhir` - Uses the FHIR Binary endpoint: `https://integrations-api-phi.synapsehealth.dev/api/v1/fhir/binary`
   - `rest` - Uses the REST endpoint: `https://integrations-api-phi.synapsehealth.dev/api/v1/documents`

2. **Using `--api-url`**: Specify a custom API URL directly

### Response Format Differences

Depending on which endpoint type you use, you'll get different response formats:

- **FHIR Endpoint** returns an OperationOutcome response:
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

- **REST Endpoint** returns a simpler JSON response:
  ```json
  {
    "documentId": "synapse-document-f52c4547-fdce-486d-8fb1-5edb645a7f49",
    "contentType": "application/octet-stream",
    "size": 1755429
  }
  ```

The client handles both formats and will display the document ID correctly in either case.

## Using a Configuration File

Instead of passing all options via command line, you can use a YAML configuration file:

```yaml
# config.yaml
file: path/to/your/document.pdf
privateKey: path/to/your/private_key.pem
clientId: YOUR_CLIENT_ID
clientSecret: YOUR_CLIENT_SECRET
scope: api://fulfill-api-phi/.default
apiEndpointType: fhir  # or "rest"
verbose: true # Optional, set to true for detailed logs
retryCount: 3 # Optional, number of retries for network failures
```

Then run:
```bash
java -jar doc-uploader-test-client.jar --config config.yaml
```

## Understanding the Results

### Successful Upload

```
✅ Document uploaded successfully!
   - Status: 200 OK
   - Document ID: synapse-document-4496cd9e-0c9c-48e6-a85e-a679af2baa11
```

The Document ID can be used to track the document in your systems or when contacting support.

### Failed Upload

```
❌ Upload failed
   - Status: 401 Unauthorized
   - Reason: Invalid signature or expired token
```

Common issues and troubleshooting:

| Status               | Likely Cause                   | Solution                               |
|----------------------|--------------------------------|----------------------------------------|
| 400 Bad Request      | Document format issue          | Ensure document type is supported      |
| 401 Unauthorized     | Invalid credentials            | Check client ID, secret, and scope     |
| 403 Forbidden        | Permissions issue              | Contact your account manager           |
| 413 Entity Too Large | Document is too large          | Try a smaller document                 |
| 500 Server Error     | Signature validation failure   | Ensure your signing process is correct |
| 5xx Other Server Error| API service issue             | Contact support                        |

For more detailed error information, run with the `--verbose` flag.

## Security Information

This client performs the following security measures, identical to what your production implementation should do:

1. **Authentication:** Obtains a JWT token via OAuth2 client credentials flow
2. **Document Signing:** Signs the document with your RSA private key using SHA256withRSA algorithm
3. **Secure Transmission:** Sends the document over HTTPS with proper headers:
   - `Authorization: Bearer <jwt-token>`
   - `x-signature: <base64-encoded-signature>`

Your private key is used locally and never transmitted to our servers.

## Correct Document Signing

For your own implementation, ensure you're following the same signing approach as this client:

1. Read the document bytes
2. Sign the document using SHA256withRSA algorithm (not signing a pre-computed hash)
3. Base64-encode the signature
4. Send as the `x-signature` header

## Need Help?

If you encounter any issues with the test client, please:

1. Run with `--verbose` flag to get detailed logs
2. Contact us with the output from your verbose test run

The client redacts your client secret from verbose output. Even so, review the
logs before sending them and never share your client secret or private key.

---

## FAQs

**Q: Where can I get my client ID and secret?**  
A: These are provided to you by Synapse Health. Contact your account manager if you don't have access.

**Q: What scope value should I use?**  
A: Your account manager will provide you with the correct scope value to use. Authentication uses Microsoft Entra ID, which requires the scope in `.default` form — a single value such as `api://fulfill-api-phi/.default`, not a space-separated list of individual permissions. A scope in any other form is rejected with an `AADSTS1002012` error.

**Q: Should I use the FHIR or REST endpoint?**  
A: Use the FHIR endpoint if you are integrating with a FHIR-compatible system, otherwise use the REST endpoint. If you're unsure which to use, contact your account manager.

**Q: How do I generate an RSA key pair?**  
A: You can generate a key pair using OpenSSL:
```bash
# Generate private key
openssl genrsa -out private_key.pem 2048
# Extract public key
openssl rsa -in private_key.pem -pubout -out public_key.pem
```

**Q: Do I need to share my private key with Synapse Health?**  
A: No. You only need to provide your public key to us through the Developer Portal. The private key should be kept secure on your systems.

**Q: Why am I getting signature validation errors?**  
A: The most common cause is an incorrect signing process. Ensure you're using SHA256withRSA and signing the **RAW DOCUMENT (NOT THE PRE-COMPUTED HASH)**. This test client implements the exact signing process required.

**Q: How can I test if my private key is working correctly?**  
A: Using this test client is the best way to validate your private key. If it works with this client but not your own implementation, the issue is likely in your signing process.

**Q: What document types are supported?**  
A: Currently, we support PDF and common image formats (JPEG, PNG, TIFF). For other document types, contact your account manager.
