package com.messlens.exception;

/** Thrown by the Policy Enforcement Point when the Policy Decision Point answers DENY. */
public class AccessDeniedException extends MessLensException {
    private final String ruleId;

    public AccessDeniedException(String message, String ruleId) {
        super(message);
        this.ruleId = ruleId;
    }

    /** Identifier of the policy rule that produced the denial (for example D-SOD). */
    public String getRuleId() { return ruleId; }
}
