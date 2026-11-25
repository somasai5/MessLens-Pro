package com.messlens.exception;

/**
 * Base user-defined checked exception for MessLens application.
 * Satisfies SRM CSE 307 Syllabus Unit 2: Exception Handling & User Defined Exceptions.
 */
public class MessLensException extends Exception {
    public MessLensException(String message) {
        super(message);
    }

    public MessLensException(String message, Throwable cause) {
        super(message, cause);
    }
}

