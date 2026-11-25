package com.messlens.security;

/** Outcome of one policy evaluation. */
public final class Decision {
    public final boolean permit;
    public final String ruleId;
    public final String reason;

    Decision(boolean permit, String ruleId, String reason) {
        this.permit = permit;
        this.ruleId = ruleId;
        this.reason = reason;
    }

    @Override public String toString() { return (permit ? "PERMIT " : "DENY ") + ruleId + " (" + reason + ")"; }
}
