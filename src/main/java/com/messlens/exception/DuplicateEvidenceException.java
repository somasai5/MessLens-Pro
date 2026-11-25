package com.messlens.exception;

/**
 * Thrown when an uploaded evidence image has a duplicate SHA-256 hash.
 * Satisfies SRM CSE 307 Syllabus Unit 2: Exception Handling & User Defined Exceptions.
 */
public class DuplicateEvidenceException extends MessLensException {
    private final String imageHash;

    public DuplicateEvidenceException(String message, String imageHash) {
        super(message);
        this.imageHash = imageHash;
    }

    public String getImageHash() {
        return imageHash;
    }
}

