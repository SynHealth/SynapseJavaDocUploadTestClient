package com.synapsehealth.doc_uploader_test_client.utils;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Utility class for HTTP client operations.
 */
public class HttpClientUtil {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    /**
     * Creates a RestTemplate with retry capabilities
     *
     * @param maxRetries The maximum number of retries for transient failures
     * @return A configured RestTemplate instance
     */
    public static RestTemplate createRestTemplate(int maxRetries) {
        return new RestTemplateBuilder()
                .requestFactory(createClientHttpRequestFactory())
                .connectTimeout(Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT_MS))
                .readTimeout(Duration.ofMillis(DEFAULT_READ_TIMEOUT_MS))
                .build();
    }

    /**
     * Creates a client HTTP request factory with appropriate timeouts
     */
    private static Supplier<ClientHttpRequestFactory> createClientHttpRequestFactory() {
        return () -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
            return factory;
        };
    }
}
