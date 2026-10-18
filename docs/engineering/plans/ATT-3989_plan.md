# Stage 3: Implementation Plan - ATT-3989: [High] Full restore deletes current data before validating the backup (Revised)

**Ticket**: [ATT-3989](https://atrainingtracker.atlassian.net/browse/ATT-3989)  
**Sub-task**: [ATT-4158](https://atrainingtracker.atlassian.net/browse/ATT-4158) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-MIG-041` (*Pre-Restore Backup Validation, Schema Version Bounds, Connection Teardown, and Atomic Rollback Snapshot*)  
**Test Mapping**: `TST-MIG-038` (`TST-MIG-038.1` – `TST-MIG-038.6`)  
**Branch**: `feature/ATT-3989`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In `MigrationEngine.performFullRestore()`, the application executed a destructive wipe of local app state (`databasesDir.deleteRecursively()`, `sharedPrefsDir.deleteRecursively()`, `datastoreDir.deleteRecursively()`) immediately upon unzipping an archive.
If the archive lacks a `databases/` directory, contains corrupted database files, or contains databases created by a newer app version (`PRAGMA user_version > DB_VERSION`), the operation fails *after* local data has already been deleted, resulting in total irrecoverable data loss or an unrecoverable `SQLiteDowngradeException` crash loop.

### Auditor Feedback Incorporation (Revision 2)
In response to Independent Auditor feedback on Gate 3, this revised plan incorporates:
1. **Atomic Directory Inode Swap on Same Mount Point**: Rather than deleting and copying directories, staging and swap operations utilize filesystem inode renames (`Files.move` with `StandardCopyOption.ATOMIC_MOVE` or `File.renameTo`) on the same filesystem (`dataDir`), avoiding slow or interrupted copy phases.
2. **Transactional State Journaling (`RestoreJournalManager.kt`)**: Persists an operation journal (`restore_journal.json`) tracking state phases (`VALIDATING`, `STAGED`, `SWAPPED`, `COMMITTED`). In the event of process termination, crash, or reboot, `TrainingApplication.java:onCreate()` inspects the journal and automatically rolls back any uncommitted state.
3. **Decoupled Schema Bounds Registry (`DatabaseSchemaRegistry.kt`)**: Isolates database version definitions and compatibility verification into an extensible registry object adhering to the Open-Closed Principle.
4. **Chaos Testing & Fault Injection**: Authoring tests in `FullRestoreValidationSecurityTest.kt` simulating I/O exceptions midway through the swap to prove the rollback and recovery mechanisms.
5. **Rollback Snapshot Verification**: Explicit validation that rollback snapshots are intact before altering active production paths.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-041`
  * `REQ-MIG-041.1`: Pre-Restore Archive Structure Validation (`MigrationEngine.kt`)
  * `REQ-MIG-041.2`: SQLite Integrity & Schema Version Compatibility Bounds (`DatabaseSchemaRegistry.kt`)
  * `REQ-MIG-041.3`: Database Connection Checkpointing and Teardown (`MigrationEngine.kt`, `BackupManager.kt`)
  * `REQ-MIG-041.4`: Transactional Atomic Swap & Rollback Snapshot (`MigrationEngine.kt`, `RestoreJournalManager.kt`)
  * `REQ-MIG-041.5`: Lifecycle Cleanup & Startup Journal Recovery (`RestoreJournalManager.kt`, `TrainingApplication.java`)
* **Test Mapping**: `TST-MIG-038` (`TST-MIG-038.1` – `TST-MIG-038.6`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Data Loss on Invalid or Failed Restore**: Existing user databases, shared preferences, and datastore files MUST remain 100% untouched if a restore fails, is rejected, or is abruptly terminated.
2. **Process Death / Reboot Resilience**: If Android kills the app during restore, the startup recovery hook in `TrainingApplication` detects the incomplete journal and reverts to the pre-restore snapshot.
3. **Downgrade Crash Loop Immunity**: Backups with schema versions higher than the currently running app code must be rejected before any files are swapped.
4. **Clean-Room Regression**: 100% test pass rate across the complete unit test suite.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `DatabaseSchemaRegistry.kt`
Create `com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry`:
```kotlin
object DatabaseSchemaRegistry {
    private val SUPPORTED_DB_VERSIONS: Map<String, Int> = mapOf(
        "WorkoutSummaries.db" to WorkoutSummariesDatabaseManager.DB_VERSION, // 25
        "SportType.db" to SportTypeDatabaseManager.DB_VERSION,             // 2
        "Equipment.db" to EquipmentDbHelper.DB_VERSION,                     // 2
        "Routes.db" to RoutesDatabaseManager.DB_VERSION,                   // 10
        "KnownLocations.db" to KnownLocationsDatabaseManager.DB_VERSION,   // 6
        "TrackingViews.db" to TrackingViewsDatabaseManager.DB_VERSION,     // 14
        "Devices.db" to DevicesDatabaseManager.DB_VERSION,                 // 7
        "Pebble.db" to PebbleDatabaseManager.DB_VERSION,                   // 3
        "AthleteMetrics.db" to AthleteMetricsDatabaseManager.DB_VERSION,   // 1
        "ActiveDevices.db" to ActiveDevicesDbHelper.DB_VERSION,             // 1
        "Segments.db" to SegmentsDatabaseManager.DB_VERSION,               // 2
        "StravaUpload.db" to 2,
        "ExportStatus.db" to 1
    )

    fun getMaxSupportedVersion(dbName: String): Int? = SUPPORTED_DB_VERSIONS[dbName]

    fun isVersionSupported(dbName: String, userVersion: Int): Boolean {
        val maxSupported = SUPPORTED_DB_VERSIONS[dbName] ?: return true // Unknown DBs not restricted
        return userVersion <= maxSupported
    }
}
```

### Component 2: `RestoreJournalManager.kt`
Create `com.atrainingtracker.trainingtracker.migration.RestoreJournalManager`:
Encapsulates journal file lifecycle on internal storage (`dataDir/restore_journal.json`):
```kotlin
enum class RestorePhase {
    IDLE,
    STAGED,     // Rollback snapshots created in dataDir/rollback_*
    SWAPPING,   // Inodes are being swapped
    COMMITTED   // Restore succeeded, ready to purge rollback
}

object RestoreJournalManager {
    fun recordPhase(context: Context, phase: RestorePhase) { ... }
    fun getActivePhase(context: Context): RestorePhase { ... }
    fun clearJournal(context: Context) { ... }
    
    /**
     * Called during TrainingApplication.onCreate() to heal interrupted restore operations.
     */
    @JvmStatic
    fun recoverInterruptedRestoreIfNecessary(context: Context) {
        val phase = getActivePhase(context)
        if (phase == RestorePhase.STAGED || phase == RestorePhase.SWAPPING) {
            Log.w("RestoreJournalManager", "Detected interrupted restore in phase $phase; executing automatic rollback.")
            MigrationEngine.executeEmergencyRollback(context)
        }
        clearJournal(context)
        SecureZipExtractor.purgeStaleStagingDirs(context)
    }
}
```

### Component 3: Pre-Validation in `MigrationEngine.kt`
```kotlin
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

fun validateBackupArchive(tempDir: File): ValidationResult {
    val dbDir = File(tempDir, "databases")
    if (!dbDir.exists() || !dbDir.isDirectory) {
        return ValidationResult.Invalid("Archive missing databases directory")
    }
    val dbFiles = dbDir.listFiles { f -> f.extension == "db" } ?: emptyArray()
    if (dbFiles.isEmpty()) {
        return ValidationResult.Invalid("No SQLite databases found in backup archive")
    }
    if (!File(dbDir, "WorkoutSummaries.db").exists()) {
        return ValidationResult.Invalid("Core WorkoutSummaries.db missing from backup archive")
    }

    for (dbFile in dbFiles) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            // 1. Integrity check
            db.rawQuery("PRAGMA quick_check;", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    return ValidationResult.Invalid("Integrity check failed for ${dbFile.name}")
                }
            }
            // 2. Schema version check
            val version = db.version
            if (!DatabaseSchemaRegistry.isVersionSupported(dbFile.name, version)) {
                val max = DatabaseSchemaRegistry.getMaxSupportedVersion(dbFile.name)
                return ValidationResult.Invalid("Database ${dbFile.name} schema version $version exceeds supported $max")
            }
        } catch (e: Exception) {
            return ValidationResult.Invalid("Failed to inspect ${dbFile.name}: ${e.message}")
        } finally {
            try { db?.close() } catch (_: Throwable) {}
        }
    }
    return ValidationResult.Valid
}
```

### Component 4: Atomic Inode Swap & Rollback in `MigrationEngine.kt`
To guarantee atomicity and avoid full file copies on the same filesystem:
1. Stage rollback directory in `dataDir` (`dataDir/rollback_databases`, `dataDir/rollback_shared_prefs`, `filesDir/rollback_datastore`).
2. Move validated incoming directories into `dataDir/staging_databases`, etc.
3. Record journal `RestorePhase.STAGED`.
4. Swap existing to rollback (`databases` -> `rollback_databases`).
5. Swap staging to active (`staging_databases` -> `databases`).
6. Record journal `RestorePhase.COMMITTED`.
7. Purge rollback directories and clear journal.
8. If any failure occurs:
   - Revert active from `rollback_*`.
   - Clear journal.
   - Return `false`.

### Component 5: Startup Hook in `TrainingApplication.java`
Wire `RestoreJournalManager.recoverInterruptedRestoreIfNecessary(this)` in `TrainingApplication.onCreate()`.

### Component 6: Test Suite in `FullRestoreValidationSecurityTest.kt`
- Test missing `databases/` rejection (existing data unchanged).
- Test corrupt SQLite file rejection (existing data unchanged).
- Test forward `user_version` rejection (existing data unchanged).
- Test mid-restore I/O chaos fault injection and verify complete rollback.
- Test successful restore with clean restart.

---

## 5. Atomic Implementation Steps (SWE.3)

* **Step 1**: Implement `DatabaseSchemaRegistry.kt` mapping all 13 application databases to their current schema versions.
* **Step 2**: Implement `RestoreJournalManager.kt` managing state transitions and startup recovery.
* **Step 3**: Implement pre-validation, connection checkpointing, atomic swap, and rollback in `MigrationEngine.kt`.
* **Step 4**: Wire `RestoreJournalManager.recoverInterruptedRestoreIfNecessary(this)` into `TrainingApplication.java:onCreate()`.
* **Step 5**: Implement `FullRestoreValidationSecurityTest.kt` covering `TST-MIG-038.1` - `TST-MIG-038.6`.
* **Step 6**: Run targeted unit tests: `./gradlew testDebugUnitTest --tests "*FullRestoreValidationSecurityTest*"`.
* **Step 7**: Run full clean-room suite: `./gradlew testDebugUnitTest`.
