package com.synapsehealth.doc_uploader_test_client.crypto;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SignatureServiceTest {

    @TempDir
    Path tempDir;

    private SignatureService signatureService;
    private AppConfig config;
    private String privateKeyPath;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        privateKeyPath = tempDir.resolve("private_key.pem").toString();
        String privateKeyContent =
                "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(privateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----";
        Files.writeString(Path.of(privateKeyPath), privateKeyContent);

        config = AppConfig.builder()
                .privateKeyPath(privateKeyPath)
                .build();

        signatureService = new SignatureService(config);
    }

    @Test
    void shouldSignDocumentSuccessfully() throws Exception {
        byte[] documentBytes = "Test document content".getBytes(StandardCharsets.UTF_8);

        String signature = signatureService.signDocument(documentBytes);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());

        try {
            byte[] decodedSignature = Base64.getDecoder().decode(signature);
            assertNotNull(decodedSignature);
        } catch (IllegalArgumentException e) {
            fail("Signature is not a valid Base64 string: " + signature);
        }
    }

    @Test
    void shouldGenerateConsistentSignaturesForSameDocument() throws Exception {
        byte[] documentBytes = "Same content for both signatures".getBytes(StandardCharsets.UTF_8);

        String signature1 = signatureService.signDocument(documentBytes);
        String signature2 = signatureService.signDocument(documentBytes);

        assertEquals(signature1, signature2);
    }

    @Test
    void shouldGenerateDifferentSignaturesForDifferentDocuments() throws Exception {
        byte[] documentBytes1 = "First document content".getBytes(StandardCharsets.UTF_8);
        byte[] documentBytes2 = "Second document content".getBytes(StandardCharsets.UTF_8);

        String signature1 = signatureService.signDocument(documentBytes1);
        String signature2 = signatureService.signDocument(documentBytes2);

        assertNotEquals(signature1, signature2);
    }

    @Test
    void shouldThrowExceptionWhenPrivateKeyFileNotFound() {
        AppConfig invalidConfig = AppConfig.builder()
                .privateKeyPath("/path/to/nonexistent/key.pem")
                .build();

        SignatureService invalidService = new SignatureService(invalidConfig);
        byte[] documentBytes = "Test content".getBytes(StandardCharsets.UTF_8);

        Exception exception = assertThrows(Exception.class, () -> {
            invalidService.signDocument(documentBytes);
        });

        assertTrue(exception.getMessage().contains("Private key file does not exist"));
    }
}
