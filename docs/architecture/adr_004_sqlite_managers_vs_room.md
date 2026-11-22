# ADR-004: SQLite Database Managers vs Room ORM

- **Document ID**: `DOC-ARC-ADR004`
- **Version**: 1.0.0
- **Status**: Approved
- **Approver**: Rainer Blind (PO)
- **Deciders**: Architecture Board, Product Owner (Pending Sign-Off)
- **Date**: 2026-11-17
- **Target Release**: `5.1.0` (Active Sprint: `2026-11-17_01_Früh`)
- **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
- **Ticket**: [ATT-8161](https://issue-tracker/ATT-8161)
- **Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1`, `REQ-PRO-116.2`), `REQ-ARC-009`, `REQ-NFR-010`, `REQ-NFR-011`
- **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1`, `TST-PRO-116.2`), `ArchitectureConformanceContractTest`, `PersistenceSqliteIntegrationTest`

---

## 1. Context & Problem Statement

`aTrainingTracker` persists multi-domain structured data across dedicated SQLite databases (`WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, `DevicesDatabaseManager`, `WorkoutClusterDatabaseManager`, `HrvReadinessDatabaseManager`, `WorkoutRrDatabaseManager`, `KnownLocationsDatabaseManager`). Notably, `WorkoutSamplesDatabaseManager` creates a **dynamic per-workout SQLite table** (`createNewTable(workoutName, sensorTypes)`) whose columns correspond to the exact `SensorType` bus active for that workout.

## 2. Decision Drivers & Evaluated Alternatives

Key selection criteria: (1) support dynamic per-workout telemetry table schemas without schema-migration failures, (2) `< 30 ms` 1-Hz write latency (`REQ-NFR-010`), (3) non-destructive multi-version `onUpgrade` compatibility for 15-year historical workout databases, and (4) clean decoupling of Compose UI from raw `Cursor` / SQLite APIs (`REQ-ARC-009`, `REQ-PRO-050`).

### Alternative 1: Full Migration to AndroidX Room ORM
- **Description**: Replace all `SQLiteOpenHelper` managers with compile-time Room `@Database`, `@Entity`, and `@Dao` definitions.
- **Evaluation**: **Rejected**. Room enforces static compile-time `@Entity` table schemas and validates exact schema hashes on open, which is fundamentally incompatible with `WorkoutSamplesDatabaseManager`'s dynamic per-workout tables (`makeColumns` / `createNewTable`). Rewriting legacy `onUpgrade` paths (`PRAGMA table_info` defensive column checks) into Room `Migration` classes also risks breaking historical backups and upgrades.

### Alternative 2: Direct SQLite Manager / `Cursor` Access from Compose UI
- **Description**: Retain `SQLiteOpenHelper` managers and allow Compose ViewModels/screens to query `*DatabaseManager` singletons and `Cursor`s directly.
- **Evaluation**: **Rejected**. Direct UI-to-SQLite coupling leaks `Cursor` resources, blocks `Dispatchers.Main`, and violates clean architecture layering (`ArchitectureConformanceContractTest` downward debt ratchet).

### Alternative 3: SQLite WAL `SQLiteOpenHelper` Managers Encapsulated Behind Kotlin Repositories (Selected)
- **Description**: Keep the proven `SQLiteOpenHelper` database managers in `app/src/main/java/.../database/` with WAL mode, idempotent `PRAGMA table_info` `onUpgrade` parity, and serialized dispatchers (`mDbExecutor` for live tracking, `Dispatchers.IO` for repositories, `dbDispatcher` for `KnownLocationsRepository`), while encapsulating all UI/domain access behind clean Kotlin repositories (`WorkoutRepository`, `DeviceDataRepository`, `KnownLocationsRepository`).
- **Evaluation**: **Selected**. Preserves dynamic per-workout sample tables and multi-decade database backward compatibility while giving Jetpack Compose UI reactive `Flow`/`StateFlow` streams and enforcing zero new UI-to-DB references (`ArchitectureConformanceContractTest`).

## 3. Decision Outcome

All persistence remains on `SQLiteOpenHelper` WAL managers (`IF-SWE-06`) wrapped by Kotlin repositories (`IF-SWE-02`), with `ArchitectureConformanceContractTest` ratcheting down legacy UI callers (`BASELINE_ALLOWED_UI_DB_CALLERS`).

## 4. Architectural Consequences & Trade-offs

### Positive Consequences
- **Dynamic Per-Workout Tables Preserved**: `WorkoutSamplesDatabaseManager` stores only active `SensorType` columns per workout, keeping storage growth near `~360 KB/h` (`REQ-NFR-011`).
- **Non-Destructive Upgrades**: Defensive `ALTER TABLE ADD COLUMN` checks preserve 100% of historical user workouts.
- **Layer Isolation Enforced**: `ArchitectureConformanceContractTest` blocks net-new UI files from referencing `*DatabaseManager` or `*DbHelper`.

### Negative / Mitigated Consequences
- **Manual SQL & Cursor Boilerplate**: Lacks compile-time SQL verification provided by Room; mitigated by `TryWithResourcesCursorAuditTest`, `PersistenceSqliteIntegrationTest`, and repository unit tests.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8161`, `ATT-8164`) | `ATT-8161`, `ATT-8164` | Retrospective ADR baseline with standardized `DOC-ARC-ADR004` control header. |
