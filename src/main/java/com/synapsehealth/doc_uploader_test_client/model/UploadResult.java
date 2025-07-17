package com.synapsehealth.doc_uploader_test_client.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing the result of a document upload operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResult {
    private boolean success;
    private int statusCode;
    private String statusMessage;
    private String documentId;
    private String errorMessage;
    private String detailedError;
}
