# SQL Init: Lightweight Versioned Migration Runner — Design

- **Date:** 2026-09-28
- **Status:** Approved (awaiting final spec review)
- **Scope:** `init/` module (`core`, `cdi`, `config`)

## Context

`init/` currently applies `schema-locations` and `data-locations` scripts in a single transaction via
`SqlScriptPopulator`. It has no notion of "already applied" — it relies on idempotent scripts
(`CREATE TABLE IF NOT EXISTS`) and re-runs everything on every startup.

The goal is a lightweight, Flyway-like **versioned** runner: a history table tracks which migrations have run,
each migration script runs once, in its own unit of work, and its success or failure is recorded atomically.

## Goals

1. Replace `schema-locations`/`data-locations` with a single `script-locations`.
2. Track versioned execution in a history table; atomically move a version to `running`, run its script as one
   unit of work, then record `succeeded` or `failed`.
3. Resolve scripts flexibly from `classpath:` (context classloader only) or `filesystem:` prefixes.
4. Add a `platform` property that selects the dialect-specific DDL used to create the history table.

## Non-goals (YAGNI)

- **Repeatable migrations** (`R__*.sql`) and **checksums** — versioned only.
- **String/complex versions** — integer versions only.
- **Out-of-order detection, baseline, target, callbacks, placeholders** — not implemented.
- **Cross-node lock table** — the unique `version` key is the concurrency primitive.
- **Backward compatibility** with `schema-locations`/`data-locations` — clean break (project is pre-1.0).

## Design

### 1. Config model — `SqlInitConfig`

Immutable value object produced by `SqlInitConfig.Builder`, with a `SqlInitConfig.defaults()` factory.

| MicroProfile property (`jdbcclient.init.*`) | Default | Meaning |
|---|---|---|
| `script-locations` | `classpath:db/migration` | Unified location list (replaces schema/data locations). |
| `separator` | `;` | Statement separator, feeds the parser's `DELIMITER` support. |
| `platform` | auto-detected | Overrides dialect detection; e.g. `h2`, `postgresql`, `mysql`, `mssql`, `oracle`. |
| `history-table` | `sqlinit_migration` | Name of the version-tracking table. |

`separator` is optional (defaults to `;`); `platform` is optional (auto-detect from
`DatabaseMetaData.getDatabaseProductName()` plus the JDBC URL); `history-table` is optional.

### 2. Migration model

- File naming: `V<version>__<description>.sql` (version = positive integer; `__` = double underscore).
- `Migration` is a record: `int version, String description, String script, Resource resource`.
- Pending migrations are collected from all `script-locations`, then sorted by version ascending.
- A duplicate version across files is an error.

### 3. History table + `platform` DDL

```
<history-table>(
  version        INT PRIMARY KEY,
  description    VARCHAR(200)  NOT NULL,
  script         VARCHAR(500)  NOT NULL,
  status         VARCHAR(16)   NOT NULL,  -- 'running' | 'succeeded' | 'failed'
  installed_on   TIMESTAMP     NOT NULL,
  error_message  VARCHAR(1000)
)
```

- `version` is the primary key (no surrogate key). This keeps DDL nearly portable.
- `installed_on` is set from Java (`java.time`), not a DB default, so DDL stays minimal.
- `DatabasePlatform` is an enum (`H2`, `POSTGRESQL`, `MYSQL`, `MSSQL`, `ORACLE`) whose members carry the exact
  `CREATE TABLE` text. Dialect differences are type spellings only (`VARCHAR2` on Oracle, `NVARCHAR`/`DATETIME` on
  MSSQL, `VARCHAR`/`TIMESTAMP` elsewhere). `DatabasePlatform.detect(DatabaseMetaData, jdbcUrl)` implements
  auto-detection; the `platform` property overrides it. Aliases (`pg`→`POSTGRESQL`, `mariadb`→`MYSQL`,
  `sqlserver`→`MSSQL`) are accepted.

### 4. Execution flow — `SqlMigrator.migrate()`

Single entry point replacing `SqlScriptPopulator.populate()`. For each pending migration, on one connection:

1. **Claim (auto-commit ON):** `INSERT INTO history (version, description, script, status, installed_on)
   VALUES (?, ?, ?, 'running', ?)`. The `version` primary key makes this the atomic claim; a concurrent
   second instance's insert throws a constraint violation and aborts startup.
2. **Unit of work (auto-commit OFF):** execute all statements of the script via the parser; `commit()`;
   on failure `rollback()`.
3. **Record (auto-commit ON):** `UPDATE history SET status='succeeded'` or
   `status='failed', error_message=?`.

Bookkeeping (steps 1 & 3) commits independently of the work (step 2), so a rolled-back migration still leaves a
persistent `failed` row. Existing-row policy when resolving pending migrations:

- `succeeded` → skip.
- `failed` or leftover `running` → fail startup with a clear message directing the operator to the row, rather than
  silently re-running against partial state.

### 5. Script location resolution

`ScriptLocator` interface with two implementations, both supporting literal paths and Ant patterns
(`**`, `*`, `?` via the kept `AntPathMatcher`):

- `classpath:` — resolved through `Thread.currentThread().getContextClassLoader()` only (`getResources` + pattern
  match). The old `classpath:` vs `classpath*:` distinction is dropped.
- `filesystem:` — a `Path` on disk, walked for pattern matches.
- A bare location (no prefix) defaults to `classpath:`.

`SqlScriptParser` and `AntPathMatcher` are kept unchanged.

### 6. Module / file layout

**`init/core`** (package `io.github.hantsy.jdbc.sqlinit`):

- Keep: `SqlScriptParser`, `AntPathMatcher`.
- Rework: `SqlInitConfig` → builder + defaults.
- Remove: `SqlScriptPopulator` and its single-transaction/schema-data test resources.
- Add:
  - `SqlMigrator` — public entry point, `migrate() throws SQLException`.
  - `Migration` — record.
  - `ScriptLocator` (+ `ClasspathScriptLocator`, `FilesystemScriptLocator`) — package-private.
  - `MigrationHistory` — creates the table and issues the claim/record queries — package-private.
  - `DatabasePlatform` — public enum.

**`init/cdi`**: `SqlInit` qualifier and `SqlInitExtension` unchanged in role; the extension now calls
`SqlMigrator.migrate()`.

**`init/config`**: `SqlInitConfigProducer` reworked to the four new properties.

`module-info` exports stay `io.github.hantsy.jdbc.sqlinit` (+ `.cdi`/`.config`), exporting `SqlMigrator`,
`SqlInitConfig`, `DatabasePlatform` instead of the populator.

### 7. Documentation

Update `docs/sql-init.md` to reflect: new artifacts/classes, `script-locations` + `platform` + `history-table`
properties, `classpath:`/`filesystem:` location syntax, versioned migration naming, and the per-migration
transaction/status model (replacing the "single transaction / idempotent scripts" narrative).

## Testing

**Core (H2 in-memory):**

- Resolver: classpath literal + pattern, filesystem literal + pattern, bare-location default.
- Versioned ordering; skip already-`succeeded`; duplicate-version error.
- `running` → `succeeded` transition recorded.
- `failed` persistence after a rolled-back migration (history row remains, DDL rolled back).
- Leftover `running` / `failed` row fails startup with a clear message.
- `platform` auto-detection (H2) and explicit override.
- Keep `SqlScriptParserTest` and `AntPathMatcherTest`.

**CDI:** existing `SqlInitExtensionTest` family re-pointed at `SqlMigrator`.

## Open defaults (confirmable at review)

- History table name `sqlinit_migration`.
- Default location `classpath:db/migration`.
