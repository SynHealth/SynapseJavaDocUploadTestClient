package com.synapsehealth.doc_uploader_test_client.crypto;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Service responsible for hashing documents and creating digital signatures.
 */
public class SignatureService {
    private static final Logger logger = LoggerFactory.getLogger(SignatureService.class);
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final AppConfig config;

    static {
        // Register Bouncy Castle provider if not already registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public SignatureService(AppConfig config) {
        this.config = config;
    }

    /**
     * Creates a digital signature for a document.
     *
     * @param documentBytes The document content as a byte array
     * @return Base64-encoded signature string
     * @throws Exception If signing fails
     */
    public String signDocument(byte[] documentBytes) throws Exception {
        try {
            if (config.isVerbose()) {
                // Still calculate and log the hash for debugging purposes
                byte[] documentHash = hashDocument(documentBytes);
                logger.info("Document hash (SHA-256): {}", bytesToHex(documentHash));
            }

            // Step 1: Load the private key
            PrivateKey privateKey = loadPrivateKey();

            // Step 2: Sign the document directly with SHA256withRSA
            // This will hash the document internally as part of the signing process
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(documentBytes); // Feed raw document bytes, not the hash
            byte[] signatureBytes = signature.sign();

            // Step 3: Encode the signature as Base64
            String base64Signature = Base64.getEncoder().encodeToString(signatureBytes);

            if (config.isVerbose()) {
                logger.info("Generated signature: {}", base64Signature);
            }

            return base64Signature;
        } catch (Exception e) {
            logger.error("Error signing document", e);
            throw new Exception("Failed to sign document: " + e.getMessage(), e);
        }
    }

    /**
     * Hashes a document using SHA-256
     */
    private byte[] hashDocument(byte[] documentBytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        return digest.digest(documentBytes);
    }

    /**
     * Loads a private key from a PEM file
     */
    private PrivateKey loadPrivateKey() throws Exception {
        logger.info("Loading private key from: {}", config.getPrivateKeyPath());

        Path keyPath = Path.of(config.getPrivateKeyPath());
        if (!Files.exists(keyPath)) {
            throw new Exception("Private key file does not exist: " + config.getPrivateKeyPath());
        }

        try (FileReader keyReader = new FileReader(config.getPrivateKeyPath());
             PEMParser pemParser = new PEMParser(keyReader)) {

            Object pemObject = pemParser.readObject();

            // Handle different PEM formats
            if (pemObject instanceof PEMKeyPair keyPair) {
                // Traditional format
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                return converter.getPrivateKey(keyPair.getPrivateKeyInfo());
            } else {
                // Try to read as PKCS#8
                byte[] keyBytes = Files.readAllBytes(keyPath);
                String keyContent = new String(keyBytes)
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");

                byte[] decoded = Base64.getDecoder().decode(keyContent);
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePrivate(keySpec);
            }
        } catch (Exception e) {
            logger.error("Failed to load private key", e);
            throw new Exception("Error loading private key: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a byte array to a hexadecimal string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
