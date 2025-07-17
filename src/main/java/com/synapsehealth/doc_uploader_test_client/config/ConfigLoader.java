package com.synapsehealth.doc_uploader_test_client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;

/**
 * Utility class for loading configuration from files or command line arguments.
 *
 * This class provides methods to create AppConfig objects either from command line
 * arguments or from configuration files (YAML or Properties). It includes robust
 * error handling and validation to ensure the resulting configuration is valid.
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Default token URL used when none is provided
     */
    private static final String DEFAULT_TOKEN_URL = "https://app-dmepos-identity-uat.azurewebsites.net/connect/token";

    /**
     * Default retry count used when none is provided or the provided value is invalid
     */
    private static final int DEFAULT_RETRY_COUNT = 3;

    /**
     * Set of required configuration fields that must be present for a valid configuration
     */
    private static final Set<String> REQUIRED_FIELDS = new HashSet<>();
    static {
        REQUIRED_FIELDS.add("filePath");
        REQUIRED_FIELDS.add("privateKeyPath");
        REQUIRED_FIELDS.add("clientId");
        REQUIRED_FIELDS.add("clientSecret");
        REQUIRED_FIELDS.add("scope");
    }

    /**
     * Creates a config object from CLI arguments.
     *
     * @param filePath Path to the document file to upload
     * @param privateKeyPath Path to the private key PEM file
     * @param clientId OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @param tokenUrl Identity server token endpoint (defaults to UAT if null)
     * @param apiUrl Custom API URL (if provided, apiEndpointType is ignored)
     * @param apiEndpointType API endpoint type ("fhir" or "rest")
     * @param scope OAuth2 scope value
     * @param grantType OAuth2 grant type (defaults to "client_credentials")
     * @param patientId Optional patient ID metadata
     * @param verbose Enable verbose logging
     * @param retryCount Number of retry attempts (must be non-negative)
     * @return An AppConfig instance with the provided configuration
     * @throws IllegalArgumentException if required fields are missing or values are invalid
     */
    public static AppConfig createFromCliArgs(
            String filePath,
            String privateKeyPath,
            String clientId,
            String clientSecret,
            String tokenUrl,
            String apiUrl,
            String apiEndpointType,
            String scope,
            String grantType,
            String patientId,
            boolean verbose,
            int retryCount) {

        // Default token URL if not provided
        if (tokenUrl == null || tokenUrl.isEmpty()) {
            tokenUrl = DEFAULT_TOKEN_URL;
            logger.debug("Using default token URL: {}", DEFAULT_TOKEN_URL);
        }

        // Sanitize retry count to ensure it's non-negative
        if (retryCount < 0) {
            logger.warn("Negative retry count ({}) provided, using default: {}", retryCount, DEFAULT_RETRY_COUNT);
            retryCount = DEFAULT_RETRY_COUNT;
        }

        // Use default grant type if not provided
        if (grantType == null || grantType.isEmpty()) {
            grantType = "client_credentials";
            logger.debug("Using default grant type: client_credentials");
        }

        AppConfig config = AppConfig.builder()
                .filePath(filePath)
                .privateKeyPath(privateKeyPath)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tokenUrl(tokenUrl)
                .apiUrl(apiUrl)
                .apiEndpointType(apiEndpointType)
                .scope(scope)
                .grantType(grantType)
                .patientId(patientId)
                .verbose(verbose)
                .retryCount(retryCount)
                .build();

        // If API URL is not explicitly set but endpoint type is, resolve the URL
        if ((apiUrl == null || apiUrl.isEmpty()) && apiEndpointType != null && !apiEndpointType.isEmpty()) {
            config.setApiUrl(config.resolveApiUrl());
        }

        return config;
    }

    /**
     * Loads configuration from a file (YAML or properties).
     *
     * @param configFilePath Path to the configuration file
     * @return An AppConfig instance with the configuration loaded from the file
     * @throws IOException if the file cannot be read or has an invalid format
     * @throws IllegalArgumentException if the configuration file path is invalid
     * @throws FileNotFoundException if the configuration file does not exist
     */
    public static AppConfig loadFromFile(String configFilePath) throws IOException {
        logger.info("Loading configuration from: {}", configFilePath);

        if (configFilePath == null || configFilePath.isEmpty()) {
            throw new IllegalArgumentException("Config file path cannot be null or empty");
        }

        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: " + configFilePath);
        }

        if (configFilePath.toLowerCase().endsWith(".yaml") ||
            configFilePath.toLowerCase().endsWith(".yml")) {
            return loadFromYaml(configFilePath);
        } else if (configFilePath.toLowerCase().endsWith(".properties")) {
            return loadFromProperties(configFilePath);
        } else {
            throw new IllegalArgumentException("Unsupported config file format. Use .yaml, .yml, or .properties");
        }
    }

    /**
     * Loads configuration from a YAML file.
     *
     * @param yamlPath Path to the YAML configuration file
     * @return An AppConfig instance with the configuration loaded from the YAML file
     * @throws IOException if the YAML file cannot be read or parsed
     * @throws FileNotFoundException if the YAML file does not exist
     */
    private static AppConfig loadFromYaml(String yamlPath) throws IOException {
        File file = new File(yamlPath);
        if (!file.exists()) {
            throw new FileNotFoundException("Config file not found: " + yamlPath);
        }

        try {
            // Check if file is empty
            if (file.length() == 0) {
                throw new IOException("Failed to load configuration: Configuration file is empty");
            }

            Map<String, Object> yamlMap = yamlMapper.readValue(file, Map.class);

            // Check for required fields
            validateRequiredFields(yamlMap);

            // Default token URL if not provided
            String tokenUrl = getString(yamlMap, "tokenUrl");
            if (tokenUrl == null || tokenUrl.isEmpty()) {
                tokenUrl = DEFAULT_TOKEN_URL;
                logger.debug("Using default token URL: {}", DEFAULT_TOKEN_URL);
            }

            String apiEndpointType = getString(yamlMap, "apiEndpointType");
            // Fix the property name from uploadUrl to apiUrl to match the YAML key
            String apiUrl = getString(yamlMap, "apiUrl");
            if (apiUrl == null) {
                // Try fallback to uploadUrl for backward compatibility
                apiUrl = getString(yamlMap, "uploadUrl");
                if (apiUrl != null) {
                    logger.warn("'uploadUrl' is deprecated, please use 'apiUrl' instead");
                }
            }

            // Handle retry count, ensuring it's non-negative
            int retryCount = getInteger(yamlMap, "retryCount", DEFAULT_RETRY_COUNT);
            if (retryCount < 0) {
                logger.warn("Negative retry count ({}) in config file, using default: {}", retryCount, DEFAULT_RETRY_COUNT);
                retryCount = DEFAULT_RETRY_COUNT;
            }

            AppConfig config = AppConfig.builder()
                    .filePath(getString(yamlMap, "file"))
                    .privateKeyPath(getString(yamlMap, "privateKey"))
                    .clientId(getString(yamlMap, "clientId"))
                    .clientSecret(getString(yamlMap, "clientSecret"))
                    .tokenUrl(tokenUrl)
                    .apiUrl(apiUrl)
                    .apiEndpointType(apiEndpointType)
                    .scope(getString(yamlMap, "scope"))
                    .grantType(getString(yamlMap, "grantType", "client_credentials"))
                    .patientId(getString(yamlMap, "patientId"))
                    .verbose(getBoolean(yamlMap, "verbose", false))
                    .retryCount(retryCount)
                    .build();

            // If API URL is not explicitly set but endpoint type is, resolve the URL
            if ((apiUrl == null || apiUrl.isEmpty()) && apiEndpointType != null && !apiEndpointType.isEmpty()) {
                config.setApiUrl(config.resolveApiUrl());
            }

            return config;
        } catch (Exception e) {
            logger.error("Error parsing YAML file: {}", yamlPath, e);
            throw new IOException("Failed to parse YAML configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Loads configuration from a Properties file.
     *
     * @param propertiesPath Path to the properties configuration file
     * @return An AppConfig instance with the configuration loaded from the properties file
     * @throws IOException if the properties file cannot be read or parsed
     */
    private static AppConfig loadFromProperties(String propertiesPath) throws IOException {
        Properties properties = new Properties();

        try (FileInputStream fis = new FileInputStream(propertiesPath)) {
            properties.load(fis);
        } catch (Exception e) {
            logger.error("Error loading properties file: {}", propertiesPath, e);
            throw new IOException("Failed to load properties file: " + e.getMessage(), e);
        }

        try {
            // Check for required fields
            validateRequiredFields(properties);

            // Default token URL if not provided
            String tokenUrl = properties.getProperty("tokenUrl");
            if (tokenUrl == null || tokenUrl.isEmpty()) {
                tokenUrl = DEFAULT_TOKEN_URL;
                logger.debug("Using default token URL: {}", DEFAULT_TOKEN_URL);
            }

            String apiEndpointType = properties.getProperty("apiEndpointType");
            // Fix the property name from uploadUrl to apiUrl to match properties key
            String apiUrl = properties.getProperty("apiUrl");
            if (apiUrl == null) {
                // Try fallback to uploadUrl for backward compatibility
                apiUrl = properties.getProperty("uploadUrl");
                if (apiUrl != null) {
                    logger.warn("'uploadUrl' is deprecated, please use 'apiUrl' instead");
                }
            }

            // Handle potential NumberFormatException for retryCount
            int retryCount = DEFAULT_RETRY_COUNT;
            try {
                String retryCountStr = properties.getProperty("retryCount");
                if (retryCountStr != null && !retryCountStr.isEmpty()) {
                    retryCount = Integer.parseInt(retryCountStr);
                    if (retryCount < 0) {
                        logger.warn("Negative retry count ({}) in properties, using default: {}", retryCount, DEFAULT_RETRY_COUNT);
                        retryCount = DEFAULT_RETRY_COUNT;
                    }
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid retryCount value in properties, using default: {}", DEFAULT_RETRY_COUNT);
            }

            AppConfig config = AppConfig.builder()
                    .filePath(properties.getProperty("file"))
                    .privateKeyPath(properties.getProperty("privateKey"))
                    .clientId(properties.getProperty("clientId"))
                    .clientSecret(properties.getProperty("clientSecret"))
                    .tokenUrl(tokenUrl)
                    .apiUrl(apiUrl)
                    .apiEndpointType(apiEndpointType)
                    .scope(properties.getProperty("scope"))
                    .grantType(properties.getProperty("grantType", "client_credentials"))
                    .patientId(properties.getProperty("patientId"))
                    .verbose(Boolean.parseBoolean(properties.getProperty("verbose", "false")))
                    .retryCount(retryCount)
                    .build();

            // If API URL is not explicitly set but an endpoint type is, resolve the URL
            if ((apiUrl == null || apiUrl.isEmpty()) && apiEndpointType != null && !apiEndpointType.isEmpty()) {
                config.setApiUrl(config.resolveApiUrl());
            }

            return config;
        } catch (Exception e) {
            logger.error("Error creating configuration from properties file: {}", propertiesPath, e);
            throw new IOException("Failed to create configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that all required fields are present in the YAML configuration.
     *
     * @param yamlMap Map containing configuration values from YAML
     * @throws IllegalArgumentException if any required field is missing
     */
    private static void validateRequiredFields(Map<String, Object> yamlMap) {
        StringBuilder missingFields = new StringBuilder();

        // Check for file and privateKey (different from the actual field names)
        if (!yamlMap.containsKey("file") || yamlMap.get("file") == null) {
            missingFields.append("file, ");
        }

        if (!yamlMap.containsKey("privateKey") || yamlMap.get("privateKey") == null) {
            missingFields.append("privateKey, ");
        }

        // Check for clientId, clientSecret, and scope
        if (!yamlMap.containsKey("clientId") || yamlMap.get("clientId") == null) {
            missingFields.append("clientId, ");
        }

        if (!yamlMap.containsKey("clientSecret") || yamlMap.get("clientSecret") == null) {
            missingFields.append("clientSecret, ");
        }

        if (!yamlMap.containsKey("scope") || yamlMap.get("scope") == null) {
            missingFields.append("scope, ");
        }

        // Check that either apiUrl or apiEndpointType is present
        boolean hasApiUrl = yamlMap.containsKey("apiUrl") && yamlMap.get("apiUrl") != null;
        boolean hasUploadUrl = yamlMap.containsKey("uploadUrl") && yamlMap.get("uploadUrl") != null;
        boolean hasApiEndpointType = yamlMap.containsKey("apiEndpointType") && yamlMap.get("apiEndpointType") != null;

        if (!hasApiUrl && !hasUploadUrl && !hasApiEndpointType) {
            missingFields.append("apiUrl or apiEndpointType, ");
        }

        // If there are missing fields, throw an exception
        if (missingFields.length() > 0) {
            // Remove trailing comma and space
            String missing = missingFields.substring(0, missingFields.length() - 2);
            throw new IllegalArgumentException("Missing required fields: " + missing);
        }
    }

    /**
     * Validates that all required fields are present in the Properties configuration.
     *
     * @param properties Properties containing configuration values
     * @throws IllegalArgumentException if any required field is missing
     */
    private static void validateRequiredFields(Properties properties) {
        StringBuilder missingFields = new StringBuilder();

        // Check for file and privateKey
        if (properties.getProperty("file") == null) {
            missingFields.append("file, ");
        }

        if (properties.getProperty("privateKey") == null) {
            missingFields.append("privateKey, ");
        }

        // Check for clientId, clientSecret, and scope
        if (properties.getProperty("clientId") == null) {
            missingFields.append("clientId, ");
        }

        if (properties.getProperty("clientSecret") == null) {
            missingFields.append("clientSecret, ");
        }

        if (properties.getProperty("scope") == null) {
            missingFields.append("scope, ");
        }

        // Check that either apiUrl or apiEndpointType is present
        boolean hasApiUrl = properties.getProperty("apiUrl") != null;
        boolean hasUploadUrl = properties.getProperty("uploadUrl") != null;
        boolean hasApiEndpointType = properties.getProperty("apiEndpointType") != null;

        if (!hasApiUrl && !hasUploadUrl && !hasApiEndpointType) {
            missingFields.append("apiUrl or apiEndpointType, ");
        }

        // If there are missing fields, throw an exception
        if (missingFields.length() > 0) {
            // Remove trailing comma and space
            String missing = missingFields.substring(0, missingFields.length() - 2);
            throw new IllegalArgumentException("Missing required fields: " + missing);
        }
    }

    /**
     * Helper method to safely get a string value from a map.
     *
     * @param map The map containing configuration values
     * @param key The key to look up
     * @return The string value, or null if not found
     */
    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Helper method to safely get a string value from a map with default.
     *
     * @param map The map containing configuration values
     * @param key The key to look up
     * @param defaultValue The default value to return if the key is not found
     * @return The string value, or the default value if not found
     */
    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        String value = getString(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Helper method to safely get a boolean value from a map with default.
     *
     * @param map The map containing configuration values
     * @param key The key to look up
     * @param defaultValue The default value to return if the key is not found or is not a valid boolean
     * @return The boolean value, or the default value if not found or invalid
     */
    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        try {
            return Boolean.parseBoolean(value.toString());
        } catch (Exception e) {
            logger.warn("Invalid boolean value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Helper method to safely get an integer value from a map with default.
     *
     * @param map The map containing configuration values
     * @param key The key to look up
     * @param defaultValue The default value to return if the key is not found or is not a valid integer
     * @return The integer value, or the default value if not found or invalid
     */
    private static int getInteger(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
