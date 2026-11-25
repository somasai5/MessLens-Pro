package com.messlens.security;

import java.time.Instant;

/**
 * Resource attributes (NIST SP 800-162): what the request is about.
 * Only attributes that exist are set; a rule that needs a missing attribute simply does not match.
 */
public final class Resource {
    public final String type;
    public Integer id;
    public Integer hostelId;
    /** The account that created the resource (for a photo: the kitchen staff who uploaded it). */
    public Integer ownerId;
    public String status;
    /** When the mess head decided (for the warden's time window). */
    public Instant decidedAt;
    public boolean countersigned;
    /** For staff accounts: the role of the account being created / managed. */
    public String targetRole;

    private Resource(String type) { this.type = type; }

    public static Resource of(String type) { return new Resource(type); }
    public static Resource hostel(Integer hostelId) { return new Resource("hostel").hostelId(hostelId); }

    public Resource id(Integer v) { this.id = v; return this; }
    public Resource hostelId(Integer v) { this.hostelId = v; return this; }
    public Resource owner(Integer v) { this.ownerId = v; return this; }
    public Resource status(String v) { this.status = v; return this; }
    public Resource decidedAt(Instant v) { this.decidedAt = v; return this; }
    public Resource countersigned(boolean v) { this.countersigned = v; return this; }
    public Resource targetRole(String v) { this.targetRole = v; return this; }
}
