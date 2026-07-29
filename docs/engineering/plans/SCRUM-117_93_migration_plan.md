# Implementation Plan: Data Migration & Automated Backups (ATT-117, ATT-93)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-MIG-001** | State Bundling (Databases + Prefs). | `BackupManager` | `TST-MIG-001` |
| **REQ-MIG-002** | Manual & Dropbox destination support. | `BackupManager`, `DropboxIntegration` | `TST-MIG-001` |
| **REQ-MIG-003** | Automated regular backups to Dropbox. | `BackupWorkManager` | `TST-MIG-003` |
| **REQ-MIG-004** | Full Migration Mode (Wipe & Restore). | `MigrationEngine` | `TST-MIG-001` |
| **REQ-MIG-005** | Incremental Import with Deduplication. | `ImportEngine` | `TST-MIG-002` |
| **REQ-MIG-006** | State Lockdown (No tracking during migration). | `MigrationUI` | `TST-MIG-001` |

## 2. Technical Architecture

### 2.1 The Backup Archive
- Format: `.zip` (internal extension `.attbackup`).
- Payload:
    - `/databases/*` (All SQLite files, including shm/wal).
    - `/shared_prefs/*` (All preference XMLs).
    - `/files/datastore/*` (Jetpack DataStore preferences).

### 2.2 Component: `BackupManager` (Kotlin)
- Responsible for zipping internal storage.
- Logic to safely close all `SQLiteOpenHelper` instances before zipping to ensure file consistency.
- Logic to export to a temporary file for sharing.

### 2.3 Component: `MigrationEngine` (Kotlin)
- Handles the "Full Restore" flow.
- Process:
    1. Unzip to temporary location.
    2. Close all active DB connections.
    3. Wipe current `/databases` and `/shared_prefs`.
    4. Move files from temporary to permanent internal storage.
    5. Call `ProcessPhoenix` (or similar) or use `AlarmManager` to force app restart.

### 2.4 Component: `ImportEngine` (Kotlin)
- Handles "Incremental Merge".
- Logic:
    1. Iterate through `WorkoutSummaries` in the backup.
    2. Check if `FILE_BASE_NAME` exists in the local database.
    3. If missing:
        - Copy summary row.
        - Copy corresponding sample table from the backup's `WorkoutSamples.db` to the local one.
        - Merge associated laps/extrema.

### 2.5 Component: `BackupWorkManager` (Kotlin)
- Periodic `Worker` (e.g., every 3 days).
- Checks if "Automated Dropbox Backups" is enabled.
- If true, generates a backup and uploads to `/Backups/aTrainingTracker_auto_v1.attbackup`.

## 3. Implementation Phases

1. **Phase 1 (Manual Flow)**: Implementation of `BackupManager`, `MigrationEngine` and the manual UI.
2. **Phase 2 (Cloud Flow)**: Dropbox integration and automated background backups.
3. **Phase 3 (Merging Logic)**: `ImportEngine` for incremental data joining.

## 4. Verification Plan

- **TST-MIG-001**: Full backup on Device A, move file, Full Restore on Device B. Verify settings and workouts.
- **TST-MIG-002**: Import same backup twice. Verify second import results in "0 new workouts imported".
- **TST-MIG-003**: Simulate background work and check Dropbox contents.
