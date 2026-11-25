package com.messlens.exception;

/**
 * Thrown on invalid login credentials or unauthorized role access.
 * Satisfies SRM CSE 307 Syllabus Unit 2: Exception Handling & User Defined Exceptions.
 */
public class AuthenticationException extends MessLensException {
    public AuthenticationException(String message) {
        super(message);
    }
}

