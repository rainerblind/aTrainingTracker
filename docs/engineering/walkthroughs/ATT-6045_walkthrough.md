# Walkthrough — ATT-6045: Thread-/DST-Safe `java.time` GPX/TCX Parsing & `BaseExporter` Index-0 Fix

- **Jira Ticket**: `ATT-6045` (`2 SP`, Bug, Medium/P2, Epic `ATT-6033`, Related: `ATT-6036`)
- **Target Release**: `V5.0.0`
- **Living Specs**: `REQ-MIG-055` (`REQ-MIG-055.1`..`REQ-MIG-055.2`) <-> `TST-MIG-055` (`TST-MIG-055.1`..`TST-MIG-055.3`) — `[Verified]`
- **Code Commit (Gate 4 Certified)**: `529a9ee8` (`ATT-9420`) on `feature/ATT-6045` (`Rule 7.5` doc-only Stage 5: `0` changes to `app/` post-`529a9ee8`)
- **Localization**: `Localization Parity: N/A - no new or modified UI strings`

## 1. Root Cause & Implemented Changes
1. **Thread- & DST-Safe ISO-8601 UTC Parsing (`WorkoutTimestampParser.kt` [`71` LoC], `GpxWorkoutParser.kt` [`306 -> 296` LoC], `TcxWorkoutParser.kt` [`250 -> 240` LoC] — `REQ-MIG-055.1`)**:
   - Replaced shared unsynchronized `SimpleDateFormat` singletons (`Gpx:45`, `Tcx:42`) and per-sample/per-lap `SimpleDateFormat` allocations (`Gpx:170,213`; `Tcx:101,126`) with immutable, thread-safe `java.time` helpers in `WorkoutTimestampParser`.
   - `formatIsoToUtcDbString` parses offset-aware ISO strings (`Z`, `+HH:MM`, `-HH:MM`, `.SSS`) via `OffsetDateTime.parse` -> `.withOffsetSameInstant(ZoneOffset.UTC)` and bare offset-less ISO via `LocalDateTime.parse`, formatting deterministically to `"yyyy-MM-dd HH:mm:ss"` (UTC-Wall-Clock) outside DST gaps without `TimeZone.getDefault()` `+1h` spring-forward corruption, and returning raw text on malformed strings.
   - `computeDurationSeconds` computes GPX lap duration via `java.time.Duration` in UTC without `SimpleDateFormat` allocation.
2. **`BaseExporter` Index-0 & Dead-Code Cleanup (`BaseExporter.java` [`357` LoC], `LegacyImportEngine.kt` [`1,453 -> 1,452` LoC] — `REQ-MIG-055.2`)**:
   - Changed `myGetStringFromCursor` (`BaseExporter.java:304`) and `myGetBooleanFromCursor` (`BaseExporter.java:322`) from `index <= 0` to `index < 0` (matching `dataValid`), preserving `"" -> null` and `> 0` boolean semantics.
   - Removed unused dead field `LegacyImportEngine.tcxTimeFormat` (`:84`) while retaining `ExportStatusDatabaseManager.getDatabase()` for `DatabaseConnectionManager.getActiveDatabases()`.

## 2. Verified Test & Governance Evidence (`89/89 PASS` across 20 Suites @ Gate 4 `529a9ee8`)
*Scope: Non-UI migration/exporter bugfix (APK deployment skipped per `stage5-verification` Section 2). Stage 4 & Gate 4 (`ATT-9420` @ `529a9ee8`) verified `89/89` targeted unit/contract tests (`--rerun`) + `4/4` Python safety linters PASS (`app/` bit-for-bit identical since `529a9ee8`).*

| Test Suite(s) | Result | Key `TST-MIG-055` Coverage |
| :--- | :---: | :--- |
| `WorkoutTimestampParserTest` | `3/3` PASS | `formatIsoToUtcDbString_handlesOffsetsFractionalBareIsoAndDstGaps`, `computeDurationSeconds_handlesValidBoundsAndMalformedInputs`, `formatIsoToUtcDbString_concurrentContentionRemainsDeterministic` (8 threads x 500 iters) |
| `GpxWorkoutParserTest`, `TcxWorkoutParserTest` | `3/3`, `4/4` PASS | `testConcurrentMultiThreadGpxParsing_isDeterministic_TST_MIG_055_1`, `testParseDstBoundariesOffsetsAndFractionalSeconds_TST_MIG_055_2`, `testConcurrentMultiThreadTcxParsing_isDeterministic_TST_MIG_055_1`, `testParseDstBoundariesOffsetsAndMalformedFallback_TST_MIG_055_2` (`Europe/Berlin` DST spring-forward/fall-back, `Z`, `+02:00`, `-05:00`, `.SSS`, bare ISO, fallback) |
| `BaseExporterTest`, `BaseExporterDownloadsTest` | `5/5`, `5/5` PASS | `testCursorHelpers_allowColumnIndexZeroAndRejectNegative_TST_MIG_055_3` (index `0` vs `-1`, `"" -> null`, `1 -> true`, `0 -> false`) |
| `LegacyImportEngine*` (11 suites), `WorkoutImportAtomicityTest`, `WorkoutClusterApexMigrationTest`, `ArchitectureConformanceContractTest`, `RepositoryEngineCleanlinessContractTest` | `69/69` PASS | Import/migration regression (`54 + 2 + 1 = 57/57`) + architecture/cleanliness contracts (`11/11`, `1/1`) |
| Governance & Safety Linters (`b3ea47f6` base) | EXIT `0` | `check_secrets_hygiene`, `lint_cursor_safety`, `lint_lifecycle_safety`, `lint_safe_enum`, `--sync-indexes`, `--base-ref b3ea47f6 --check-verified-evidence --test-results-dir /tmp/empty_check_6045` |
