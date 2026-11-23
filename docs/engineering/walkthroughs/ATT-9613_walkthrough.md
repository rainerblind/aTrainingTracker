# Stage 5 Verification & Walkthrough — ATT-9613

- **Ticket**: `ATT-9613` (`2 SP`, `Bug`, `severity-major`, `tier-a`, Epic `ATT-281`) | **Target Release**: `V5.0.0`
- **Branch**: `feature/ATT-9613` (preserved intact for Stage 6 Cold Review; `sprint/2026-11-23_01_Früh` untouched)
- **Gate 3 Sign-Off**: `ATT-9658` (`Erledigt` / `GATE_PASSED`, all 3 Gate 3 guardrails implemented)
- **Traceability**: `REQ-EXP-048` (`REQ-EXP-048.1`–`.2`, `[Verified]`) <-> `TST-EXP-048` (`TST-EXP-048.1`–`.2`, `[Verified]`) | `Derived (SW-only)`

## 1. Implemented Changes

| Component | Change Summary | Guardrail / Invariant |
|---|---|---|
| `FileFormat.java` (`107` LoC) | Added explicit compile-time `boolean isDerivedExportFile` constructor parameter and `public boolean isDerivedExportFile()` getter (`true` for SQLite-regenerable `CSV`, `GC`, `TCX`, `GPX`, `FIT`, `STRAVA`; `false` for irreplaceable `RAW_SENSOR` and API-only `HEALTH_CONNECT`). | Preserves 100% of enum names, `dirName`, `fileEnding`, `uiNameId`, `getMimeType()`, `STANDARD_FILE_FORMATS`, and `ONLINE_COMMUNITIES` (Gate 3 #1). |
| `ExportManager.java` (`522` LoC) | Hardened `invalidateWorkoutExport(fileBaseName)` Step 2 with null-safe `basePath` guard (`String basePath = baseDir != null ? baseDir.getPath() : null; if (basePath != null && !basePath.isEmpty())`) and `if (!format.isDerivedExportFile() || format.getFileEnding().isEmpty()) continue;`, plus updated Javadoc (Gate 3 #2). | Never deletes any file under `files/raw_sensor/` (`.polar_raw.csv.gz`, `.phone_raw.csv.gz`, `.tmp`); preserves Step 1 WorkManager cancellation, Step 2 derived-cache purge, and Step 3 `ExportStatus.WAITING` DB reset + broadcast (`REQ-DAT-029.4`). |
| `ExportManagerTest.kt` (`TST-EXP-048.1`) | Added `testInvalidateWorkoutExport_deletesDerivedCachesOnlyAndPreservesRawSensorSidecars_TST_EXP_048_1`: exhaustive partition check over `FileFormat.values()`, real temp `filesDir` with all 6 derived caches + 4 `raw_sensor/*` sidecar/tmp files + `HealthConnect` sentinel, verifying derived caches deleted, sidecars preserved byte-for-byte, and WorkManager/DB/broadcast calls intact. | Temp directory cleaned up in `finally`; zero production side-effects. |
| `RawSensorFileExporterTest.kt` (`TST-EXP-048.2`) | Added `testInvalidateWorkoutExport_followedByRawSensorExport_preservesAndExportsBothSidecars_TST_EXP_048_2`: seeds Polar + Phone sidecars and derived `FIT`/`TCX` files in Robolectric `context.filesDir`, invokes `ExportManager(context).invalidateWorkoutExport(fileBaseName)`, verifies derived caches deleted & sidecars byte-identical (`assertArrayEquals`), then verifies `RawSensorFileExporter.export()` returns `"Successfully exported 2 raw sensor sidecars"`; expanded `tearDown()` to clean up all format dirs (Gate 3 #3). | Zero SQLite, UI, or `strings*.xml` edits (`Localization Parity: N/A - 0 string changes`); `ANT-Android-SDKs` untouched (`160000`). |

## 2. Verification Evidence (`TST-EXP-048.1`–`.2`)

- **Targeted Tier-1 Unit & Contract Suites (`45/45` PASS, `0` fail, `0` err, `0` skip, `0` new compiler warnings)**:
  - `ExportManagerTest`: `4/4` PASS (`testInvalidateWorkoutExport_deletesDerivedCachesOnlyAndPreservesRawSensorSidecars_TST_EXP_048_1`)
  - `RawSensorFileExporterTest`: `6/6` PASS (`testInvalidateWorkoutExport_followedByRawSensorExport_preservesAndExportsBothSidecars_TST_EXP_048_2`)
  - `ExportManagerFitTest`: `8/8` PASS | `ExportManagerGoogleDriveTest`: `2/2` PASS | `ExportManagerDropboxGatingTest`: `3/3` PASS
  - `BaseExporterDownloadsTest`: `5/5` PASS | `PolarRawTelemetryPrunerTest`: `6/6` PASS | `ArchitectureConformanceContractTest`: `11/11` PASS
- **Non-UI Verification Scope (`Rule 7.6`)**: Non-UI backend exporter bugfix (zero Compose/Activity/Fragment/Manifest/resource/Service edits); verified via JVM + Robolectric unit/contract tests (`45/45` PASS).
- **Machine Evidence Bundle (`Rule 8.4` / `ATT-9544`)**: `tools/auto_gate.py --ticket ATT-9613 --tier tier-a --base-ref sprint/2026-11-23_01_Früh --write-bundle --advance-jira --as agent1` -> **PASS** (`dependent_unit_tests`: `306/306` Android unit tests PASS across `51` JUnit XML suites; `static_safety_linters`: PASS; `deliverable_lint`: PASS; `conflict_markers`: PASS; `52` SHA-256 hashed files):
  - Summary: [`docs/engineering/test_evidence/tickets/ATT-9613/evidence_summary.json`](../test_evidence/tickets/ATT-9613/evidence_summary.json)
  - Manifest: [`docs/engineering/test_evidence/tickets/ATT-9613/manifest.json`](../test_evidence/tickets/ATT-9613/manifest.json)
- **Governance & Index Parity**: `python3 tools/verify_requirement_governance.py --sync-indexes` and `python3 tools/verify_requirement_governance.py` PASS (`exit 0`).
