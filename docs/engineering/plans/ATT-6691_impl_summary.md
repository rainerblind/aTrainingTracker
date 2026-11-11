h2. Stage 4 Software Construction: ATT-6691
* **Database Registry & Connection Quiescence Parity (REQ-MAP-066.2)**:
  - Exposed `val database: SQLiteDatabase` on `ClimbsDatabaseManager`.
  - Registered `ClimbsDbHelper.DATABASE_NAME` (v1) in `DatabaseSchemaRegistry`.
  - Registered `ClimbsDatabaseManager.database` in `DatabaseConnectionManager.getActiveDatabases(context)`.
* **Backup Restore Self-Healing Hook (REQ-MAP-066.3)**:
  - In `MigrationEngine.performFullRestore()`, detects absence of `Climbs.db` in restored archive and resets `climbs_workout_backfill_v1` in restored SharedPreferences.
* **Historical Workout Backfill Pipeline (REQ-MAP-066.1)**:
  - In `WorkoutRepository`, declared `PREF_CLIMBS_WORKOUT_BACKFILL_V1 = "climbs_workout_backfill_v1"`, implemented `backfillHistoricalClimbs()`, `triggerHistoricalClimbBackfillIfPending()`, and wired into `init {}`.
* **Unit Testing & Verification (TST-MAP-068.1..4)**:
  - `ClimbsDatabaseRegistryTest`: 3/3 tests passed.
  - `MigrationEngineClimbsRestoreTest`: 2/2 tests passed.
  - `WorkoutHistoricalClimbBackfillTest`: 2/2 tests passed.
  - Full targeted and regression test suite passed 100% under `parallel_sprint_lock.py`.
