# ADR-005: Hybrid Preferences (`SharedPreferences` + Jetpack `DataStore`)

- **Document ID**: `DOC-ARC-ADR005`
- **Version**: 1.0.0
- **Status**: Retrospective (Pending PO Sign-Off)
- **Approver**: Product Owner (Pending Sign-Off)
- **Deciders**: Architecture Board, Product Owner (Pending Sign-Off)
- **Date**: 2026-11-17
- **Target Release**: `5.1.0` (Active Sprint: `2026-11-17_01_Früh`)
- **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
- **Ticket**: [ATT-8161](https://issue-tracker/ATT-8161)
- **Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1`, `REQ-PRO-116.2`), `REQ-SET-079`
- **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1`, `TST-PRO-116.2`), `DataStoreHotPathContractTest`

---

## 1. Context & Problem Statement

Configuration settings span two distinct access patterns: (1) synchronous reads from legacy Java core services and backup/migration utilities (`TrainingApplication`, `BackupWorker`, `MigrationEngine`, `PreferenceManager.getDefaultSharedPreferences`), and (2) reactive, multi-observer Kotlin `Flow`/`StateFlow` preferences driving Jetpack Compose UI, training zones, expert tuning, and sensor fusion (`SettingsDataStore`, `TuningPreferencesDataStore`, `MyPreferenceManager`, `AttFusedPreferences`).

## 2. Decision Drivers & Evaluated Alternatives

Key selection criteria: (1) zero main-thread XML disk I/O jank during Compose rendering (`< 16.6 ms` frame budget, `REQ-NFR-012`), (2) `O(1)` non-blocking synchronous snapshots for hot paths (`REQ-SET-079`, `DataStoreHotPathContractTest`), and (3) zero regression or preference loss for existing `SharedPreferences` keys and Java callers.

### Alternative 1: Keep Monolithic `SharedPreferences` Exclusively
- **Description**: Store all new zone, expert tuning, and UI preferences in `PreferenceManager.getDefaultSharedPreferences` and poll/listen via `OnSharedPreferenceChangeListener`.
- **Evaluation**: **Rejected**. `SharedPreferences` performs synchronous XML parsing on initialization (`getDefaultSharedPreferences` risks ANR/frame drops), lacks structured coroutine `Flow` composition, and provides no atomic transactional guarantees on async corruption.

### Alternative 2: Big-Bang Cutover Removing All `SharedPreferences` Usage
- **Description**: Migrate every `PreferenceManager.getDefaultSharedPreferences` key in `TrainingApplication`, `BackupWorker`, `MigrationEngine`, and legacy settings dialogs into `DataStore` in a single release.
- **Evaluation**: **Rejected**. `TrainingApplication` exposes dozens of synchronous static getters consumed directly by Java `TrackerService`, `BANALService`, and backup XML serializers; forcing `runBlocking` across `DataStore` from Java hotpaths risks deadlocks and migration regressions.

### Alternative 3: Hybrid DataStore-First Architecture with Eager `StateFlow` Cache & Java Helper Bridge (Selected)
- **Description**: Author all modern reactive settings, zones, and tuning flags in `androidx.datastore:datastore-preferences` (`SettingsDataStore` caching `SettingsSnapshot` in an eager `StateFlow`, `TuningPreferencesDataStore`, `MyPreferenceManager`, `AttFusedPreferences`) with `SettingsDataStoreJavaHelper` bridging synchronous Java reads, while retaining `SharedPreferences` solely for legacy `TrainingApplication` keys and incremental backup compatibility.
- **Evaluation**: **Selected**. Delivers non-blocking reactive `StateFlow` updates for Compose UI and `O(1)` snapshot reads without `runBlocking` hazards in legacy Java services.

## 3. Decision Outcome

New UI, zone, and tuning preferences MUST use Jetpack `DataStore` (`SettingsDataStore` / `TuningPreferencesDataStore` / `MyPreferenceManager`) backed by eager `StateFlow` caching (`DataStoreHotPathContractTest`), while `TrainingApplication` `SharedPreferences` keys remain stable until cleanly retired.

## 4. Architectural Consequences & Trade-offs

### Positive Consequences
- **Zero Main-Thread Blocking**: `SettingsSnapshot` and `TuningPreferencesDataStore` emit immutable `StateFlow` values without Compose frame drops.
- **Java Interop Safety**: `SettingsDataStoreJavaHelper` exposes non-blocking cached values to legacy Java callers.

### Negative / Mitigated Consequences
- **Dual Preference Stores**: Backup/restore (`BackupWorker`, `MigrationEngine`) and legacy settings must account for both `SharedPreferences` and `.preferences_pb` DataStore files; mitigated by explicit `DataStore` backup/migration tests and `DataStoreHotPathContractTest`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8161`, `ATT-8164`) | `ATT-8161`, `ATT-8164` | Retrospective ADR baseline with standardized `DOC-ARC-ADR005` control header. |
