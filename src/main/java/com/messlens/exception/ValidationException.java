package com.messlens.exception;

/**
 * Thrown when form input or reviewer remarks fail validation constraints.
 * Satisfies SRM CSE 307 Syllabus Unit 2: Exception Handling & User Defined Exceptions.
 */
public class ValidationException extends MessLensException {
    public ValidationException(String message) {
        super(message);
    }
}

