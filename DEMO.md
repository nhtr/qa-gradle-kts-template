Great—here’s a **safe, generic, SQL-injection–resistant** archiver you can drop into a Spring Boot project.
Key ideas:

* **No dynamic identifiers from callers.** Callers can only pick from a **whitelisted enum**.
* **Single round-trip CTE**: `DELETE … RETURNING` → `INSERT … SELECT` (fast & atomic).
* **Prepared statements** for all values (`id`, `deleted_by`, etc.).
* **Minimal allocations**, no entity hydration.

---

# 1) Whitelisted archive specs (what’s allowed)

```java
// package com.example.archive;
import java.util.List;

public enum ArchiveSpec {
    /**
     * Moves from app.users -> archive.users
     * Destination columns include deleted_at and deleted_by.
     */
    USERS(
        "app",      // source schema
        "users",    // source table
        "archive",  // dest schema
        "users",    // dest table
        // destination column order (stable & indexed where needed)
        List.of("id", "email", "full_name", "created_at", "updated_at", "deleted_at", "deleted_by"),
        // projection from moved rows; '?' placeholders will be bound by the archiver
        "id, email, full_name, created_at, updated_at, now(), ?" // last ? = deleted_by
    );

    private final String srcSchema;
    private final String srcTable;
    private final String dstSchema;
    private final String dstTable;
    private final List<String> dstColumns;
    private final String selectProjection;

    ArchiveSpec(String srcSchema, String srcTable,
                String dstSchema, String dstTable,
                List<String> dstColumns, String selectProjection) {
        this.srcSchema = srcSchema;
        this.srcTable = srcTable;
        this.dstSchema = dstSchema;
        this.dstTable = dstTable;
        this.dstColumns = dstColumns;
        this.selectProjection = selectProjection;
    }

    public String srcSchema() { return srcSchema; }
    public String srcTable() { return srcTable; }
    public String dstSchema() { return dstSchema; }
    public String dstTable() { return dstTable; }
    public List<String> dstColumns() { return dstColumns; }
    public String selectProjection() { return selectProjection; }
}
```

> You can add more entries (e.g., `ORDERS`, `INVOICES`) with their own mappings—**still safe** because callers can’t pass arbitrary identifiers.

---

# 2) The Archiver (reusable, lean, safe)

```java
// package com.example.archive;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class RowArchiver {

    private final JdbcTemplate jdbc;

    public RowArchiver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Quote identifiers once (defensive). Values still use '?' placeholders. */
    private static String q(String ident) {
        // Optional extra safety: reject non-simple identifiers
        if (!ident.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + ident);
        }
        return "\"" + ident + "\"";
    }

    private static String joinCols(List<String> cols) {
        StringBuilder sb = new StringBuilder(cols.size() * 10);
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(q(cols.get(i)));
        }
        return sb.toString();
    }

    /**
     * Archive+delete a single id. Atomic, single round trip.
     * @param spec      Whitelisted table mapping
     * @param id        Row id to archive
     * @param deletedBy Auditing info (username/service); can be null
     * @return rows moved (0 or 1 for PK)
     */
    @Transactional
    public int archiveById(ArchiveSpec spec, long id, String deletedBy) {
        Objects.requireNonNull(spec, "spec");

        final String sql = """
            WITH moved AS (
              DELETE FROM %s.%s
              WHERE %s = ?
              RETURNING *
            )
            INSERT INTO %s.%s (%s)
            SELECT %s FROM moved
            """.formatted(
                q(spec.srcSchema()), q(spec.srcTable()),
                q("id"),
                q(spec.dstSchema()), q(spec.dstTable()),
                joinCols(spec.dstColumns()),
                spec.selectProjection()
            );

        // Bind order: 1) id, 2) deleted_by (if projection expects it)
        return jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            int p = 1;
            ps.setLong(p++, id);
            // If your projection contains a '?', bind deleted_by; otherwise skip
            if (spec.selectProjection().contains("?")) {
                ps.setString(p++, deletedBy);
            }
            return ps;
        });
    }

    /**
     * Archive+delete many ids efficiently. Chunks to keep SQL short and GC light.
     * Uses `WHERE id IN (?, ?, ...)` and repeats the same single-statement CTE.
     *
     * @return total rows moved.
     */
    @Transactional
    public int archiveByIds(ArchiveSpec spec, List<Long> ids, String deletedBy, int chunkSize) {
        Objects.requireNonNull(spec, "spec");
        if (ids == null || ids.isEmpty()) return 0;
        if (chunkSize <= 0) chunkSize = 512; // sensible default

        int total = 0;
        for (int from = 0; from < ids.size(); from += chunkSize) {
            List<Long> chunk = ids.subList(from, Math.min(from + chunkSize, ids.size()));
            total += archiveChunk(spec, chunk, deletedBy);
        }
        return total;
    }

    private int archiveChunk(ArchiveSpec spec, List<Long> chunk, String deletedBy) {
        final String placeholders = String.join(", ", chunk.stream().map(x -> "?").toList());
        final String sql = ("""
            WITH moved AS (
              DELETE FROM %s.%s
              WHERE %s IN (%s)
              RETURNING *
            )
            INSERT INTO %s.%s (%s)
            SELECT %s FROM moved
            """).formatted(
                q(spec.srcSchema()), q(spec.srcTable()),
                q("id"), placeholders,
                q(spec.dstSchema()), q(spec.dstTable()),
                joinCols(spec.dstColumns()),
                spec.selectProjection()
            );

        return jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            int p = 1;
            for (Long id : chunk) ps.setLong(p++, id);
            if (spec.selectProjection().contains("?")) {
                ps.setString(p++, deletedBy);
            }
            return ps;
        });
    }
}
```

### Why this resists SQL injection

* Callers can only choose from `ArchiveSpec` (enum) → **no untrusted identifiers**.
* All **data** (`id`, `deleted_by`) are **bound via `?`**.
* Identifiers are validated and **quoted**.

---

# 3) Service example (clean API)

```java
// package com.example.user;
import com.example.archive.ArchiveSpec;
import com.example.archive.RowArchiver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserArchivingService {

    private final RowArchiver archiver;

    public UserArchivingService(RowArchiver archiver) {
        this.archiver = archiver;
    }

    @Transactional
    public boolean deleteUser(long id, String deletedBy) {
        // Move app.users -> archive.users and delete source row
        int moved = archiver.archiveById(ArchiveSpec.USERS, id, deletedBy);
        return moved == 1;
    }

    @Transactional
    public int deleteUsersBulk(List<Long> ids, String deletedBy) {
        return archiver.archiveByIds(ArchiveSpec.USERS, ids, deletedBy, 1000);
    }
}
```

---

# 4) Flyway migration (tables & indexes)

```sql
-- app schema (source)
CREATE SCHEMA IF NOT EXISTS app;
CREATE TABLE IF NOT EXISTS app.users (
  id          BIGSERIAL PRIMARY KEY,
  email       TEXT NOT NULL UNIQUE,
  full_name   TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- archive schema (destination)
CREATE SCHEMA IF NOT EXISTS archive;
CREATE TABLE IF NOT EXISTS archive.users (
  id          BIGINT PRIMARY KEY,
  email       TEXT NOT NULL,
  full_name   TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL,
  deleted_at  TIMESTAMPTZ NOT NULL,
  deleted_by  TEXT
);

CREATE INDEX IF NOT EXISTS idx_archive_users_deleted_at ON archive.users(deleted_at);
CREATE INDEX IF NOT EXISTS idx_archive_users_email ON archive.users(email);
```

---

## Notes & options

* **Atomicity**: Each call wraps in a transaction; the move+delete is all-or-nothing.
* **Performance**: No entity loading, minimal allocations; CTE runs entirely server-side.
* **Memory**: Large bulk deletes are chunked; adjust `chunkSize` to your workload.
* **Auditing**: Add more `?` in the enum’s `selectProjection` for e.g. `request_id`, then bind them in `RowArchiver` (extend the method signature).
* **Integrity**: If you have FKs to `app.users(id)`, archive dependents first (another `ArchiveSpec`) or migrate to logical soft-delete flags for referential consistency.

If you share your full table shapes or extra audit fields you need, I’ll add more `ArchiveSpec` entries and an overload to bind multiple audit parameters cleanly.
