package com.messlens.security;

import com.messlens.audit.AuditLog;
import com.messlens.dao.DatabaseManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Tiny helpers shared by the security test programs (no JUnit needed). */
final class TestKit {
    static int pass, fail;

    static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("  PASS  " + name); }
        else { fail++; System.out.println("  FAIL  " + name); }
    }

    static void section(String title) { System.out.println("\n== " + title); }

    /** Fresh database + fresh audit keys. Needs the working directory's config.properties to point at test files. */
    static void freshEnvironment() throws Exception {
        System.setProperty("messlens.pbkdf2.iterations", "2000");   // fast tests; production default is 600000
        com.messlens.util.Config.overrideForTests("test.db", "audit_test", "evidence_test");   // never touch real data
        deleteTree(new File("audit_test").toPath());
        deleteTree(new File("evidence_test").toPath());
        new File("test.db").delete();
        new File("test.db-wal").delete();
        new File("test.db-shm").delete();
        AuditLog.reset();
        DatabaseManager.initializeDatabase();
    }

    static void deleteTree(Path p) throws Exception {
        if (!Files.exists(p)) return;
        try (var w = Files.walk(p)) {
            w.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        }
    }

    static void summary(String suite) {
        System.out.println("\n" + suite + ": " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
