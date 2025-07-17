package com.synapsehealth.doc_uploader_test_client.crypto;

import com.synapsehealth.doc_uploader_test_client.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SignatureServiceEdgeCasesTest {

    @TempDir
    Path tempDir;

    @Mock
    private AppConfig mockConfig;

    private File privateKeyFile;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private SignatureService signatureService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();

        privateKeyFile = createPrivateKeyFile(privateKey);

        when(mockConfig.getPrivateKeyPath()).thenReturn(privateKeyFile.getAbsolutePath());
        when(mockConfig.isVerbose()).thenReturn(false);

        signatureService = new SignatureService(mockConfig);
    }

    @Test
    void shouldSignDocumentCorrectly() throws Exception {
        byte[] documentBytes = "Test document content".getBytes(StandardCharsets.UTF_8);

        String signature = signatureService.signDocument(documentBytes);

        assertNotNull(signature);
        assertTrue(verifySignature(documentBytes, signature, publicKey));
    }

    @Test
    void shouldHandleEmptyDocument() throws Exception {
        byte[] emptyBytes = new byte[0];

        String signature = signatureService.signDocument(emptyBytes);

        assertNotNull(signature);
        assertTrue(verifySignature(emptyBytes, signature, publicKey));
    }

    @Test
    void shouldHandleLargeDocument() throws Exception {
        byte[] largeDoc = new byte[1024 * 1024];
        new SecureRandom().nextBytes(largeDoc);

        String signature = signatureService.signDocument(largeDoc);

        assertNotNull(signature);
        assertTrue(verifySignature(largeDoc, signature, publicKey));
    }

    @Test
    void shouldThrowExceptionWithInvalidPrivateKeyFile() throws Exception {
        when(mockConfig.getPrivateKeyPath()).thenReturn("/path/does/not/exist.pem");

        SignatureService badService = new SignatureService(mockConfig);

        byte[] documentBytes = "Test document".getBytes(StandardCharsets.UTF_8);
        Exception exception = assertThrows(Exception.class, () -> {
            badService.signDocument(documentBytes);
        });

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void shouldThrowExceptionWithCorruptPrivateKeyFile() throws Exception {
        File invalidKeyFile = tempDir.resolve("invalid_key.pem").toFile();
        Files.writeString(invalidKeyFile.toPath(), "This is not a valid PEM key");
        when(mockConfig.getPrivateKeyPath()).thenReturn(invalidKeyFile.getAbsolutePath());

        SignatureService badService = new SignatureService(mockConfig);

        byte[] documentBytes = "Test document".getBytes(StandardCharsets.UTF_8);
        Exception exception = assertThrows(Exception.class, () -> {
            badService.signDocument(documentBytes);
        });

        assertTrue(exception.getMessage().contains("Failed to sign document") ||
                   exception.getMessage().contains("Error loading private key"));
    }

    @Test
    void shouldProduceConsistentSignaturesForSameInput() throws Exception {
        byte[] documentBytes = "Identical content".getBytes(StandardCharsets.UTF_8);

        String signature1 = signatureService.signDocument(documentBytes);
        String signature2 = signatureService.signDocument(documentBytes);

        assertEquals(signature1, signature2);
    }

    @Test
    void shouldNotDoubleHashDocument() throws Exception {
        byte[] documentBytes = "Test double hashing prevention".getBytes(StandardCharsets.UTF_8);

        Signature directSignature = Signature.getInstance("SHA256withRSA");
        directSignature.initSign(privateKey);
        directSignature.update(documentBytes);
        String expectedSignature = Base64.getEncoder().encodeToString(directSignature.sign());

        String actualSignature = signatureService.signDocument(documentBytes);

        assertEquals(expectedSignature, actualSignature);
    }

    private File createPrivateKeyFile(PrivateKey privateKey) throws IOException {
        File keyFile = tempDir.resolve("private_key.pem").toFile();

        try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            byte[] pkcs8Key = privateKey.getEncoded();
            String base64Key = Base64.getEncoder().encodeToString(pkcs8Key);

            String pemKey = "-----BEGIN PRIVATE KEY-----\n" +
                            base64Key + "\n" +
                            "-----END PRIVATE KEY-----\n";

            fos.write(pemKey.getBytes(StandardCharsets.UTF_8));
        }

        return keyFile;
    }

    private boolean verifySignature(byte[] data, String base64Signature, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(Base64.getDecoder().decode(base64Signature));
    }
}
