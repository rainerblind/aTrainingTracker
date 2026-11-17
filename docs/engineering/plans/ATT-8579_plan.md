# Stage 3: Implementation Plan - ATT-8579: Canonical SensorType Storage and Display Units

**Ticket**: `ATT-8579` (`2 SP`, Epic `ATT-431`) | **Branch**: `feature/ATT-8579` | **Date**: 2026-11-17  
**Requirement**: `REQ-TRK-083` (`REQ-TRK-083.1`..`REQ-TRK-083.3`) | **Test Spec**: `TST-TRK-083` (`TST-TRK-083.1`..`TST-TRK-083.3`)  
**Author**: AI Agent 1 (`--as agent1`) | **UI Consistency**: `N/A - no UI or strings modified`

---

## 1. Architecture & Layering Design (SWE.2)
- **Domain / Sensor Bus (`banalservice.sensor`)**: `SensorType.java` remains the single Java enum authority for telemetry identity (`Parcelable` via `ordinal()`, SQLite columns via `name()`, UI display resource via `@Deprecated unitId`, formatting via `MyFormatter`). Adding `private final String internalUnit` + `public String getInternalUnit()` + per-constant Javadoc exposes canonical internal storage units at the enum boundary without changing any runtime calculation, formatter, or SQLite schema.
- **SWE.2 Documentation (`docs/architecture/`)**: Dedicated `docs/architecture/sensor_units.md` holds the 67-row data dictionary, cross-linked from a new `### 5.4` subsection in `docs/architecture.md` (preserving Sections 2, 4.9, 5.1, and 6 so `AspiceSweAuditContractTest` & `ArchitectureConformanceContractTest` remain unaffected).
- **Concurrency & Threading**: Purely immutable `final String` enum metadata and JVM unit tests; zero runtime I/O, threading, or `dbDispatcher` / `Dispatchers.IO` changes.

## 2. Atomic Stage 4 Implementation Sequence & Traceability
| Step | Target File(s) | Action & Scope | REQ / TST Mapping |
| :--- | :--- | :--- | :--- |
| **Step 1** | `docs/architecture/sensor_units.md`, `docs/architecture.md` | Create 67-row canonical unit dictionary (enum `name()`, `SensorCategory`, `SensorSubCategory`, internal unit, `SensorValueType`, range/sentinels, `MyFormatter` & `MyHelper.getUnitsId()` display units, FIT/TCX/GPX/CSV/GC export units, plus `W_PRIME_BAL`/`MyHelper`/`ErtsPostWorkoutRecomputer` discrepancy notes); link via `docs/architecture.md` `### 5.4`. | `REQ-TRK-083.1`, `TST-TRK-083.2` |
| **Step 2** | `app/.../banalservice/sensor/SensorType.java` | Add `private final String internalUnit`, `public String getInternalUnit()`, constructor support, and per-constant Javadoc on all 67 enum constants (documenting internal unit, sentinels, display/export conversions). Keep enum order, names, `CREATOR`, and `getUnitId()` unchanged. | `REQ-TRK-083.2`, `TST-TRK-083.1` |
| **Step 3** | `app/src/test/.../sensor/SensorTypeUnitsContractTest.kt` | Add JVM contract test verifying: (a) non-null/non-blank `getInternalUnit()` and preceding `/** ... */` Javadoc on all 67 `SensorType.values()`; (b) bidirectional set & metadata parity (`SensorCategory`, `SensorValueType`, `internalUnit`) with `docs/architecture/sensor_units.md`; (c) `docs/architecture.md` cross-link and sentinel/exporter coverage. | `REQ-TRK-083.3`, `TST-TRK-083.1`, `TST-TRK-083.2` |
| **Step 4** | Gradle & Governance | Run targeted `./gradlew testDebugUnitTest` (under `parallel_sprint_lock.py`) covering `SensorTypeUnitsContractTest`, `SensorTypeFormatterTest`, `SensorTypeMmpTest`, `AspiceSweAuditContractTest`, and `ArchitectureConformanceContractTest`, plus `tools/verify_requirement_governance.py`. | `TST-TRK-083.3` |

## 3. System Invariants & Constraints
1. **Enum & Persistence Invariants**: All 67 `SensorType` `ordinal()` positions (`Parcelable`) and `name()` identifiers (`WorkoutSamplesDatabaseManager.makeColumns`) MUST remain unchanged; zero SQLite schema migrations.
2. **Runtime & API Stability**: `@Deprecated getUnitId()`, `MyHelper.getUnitsId()`, all `MyFormatter` classes, `ErtsPostWorkoutRecomputer` (`ATT-8577`), `FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, `CSVFileWriter.java`, and `GCFileWriter.java` MUST NOT change runtime behavior.
3. **Zero UI / Submodule Changes**: `N/A - no UI strings`; `ANT-Android-SDKs` MUST remain at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

## 4. Verification & Rollback Plan
- **Verification**: Execute `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeFormatterTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeMmpTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` and `python3 tools/verify_requirement_governance.py`.
- **Rollback**: Additive metadata and docs only; revert cleanly with `git checkout` on the 4 target files (no database downgrade or preference migration needed).

## 5. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-TRK-083`, complementing `REQ-TRK-070` (*2-Level Sensor Hierarchy*) and `REQ-PRO-043` (*SWE.2 Architecture Conformance*).
- **Historical Origin & Commit Trace**: `SensorType.unitId` predates unit-aware formatters (`SpeedFormatter`, `DistanceFormatter`, `AltitudeFormatter`, `PaceFormatter`) and `MyHelper.getUnitsId()`; `e9db6552` (`ATT-6726`, `REQ-TRK-070`) added `SensorSubCategory` without adding `internalUnit`.
- **Root Reason for Existing Formulation**: `unitId` was retained as `@Deprecated` so legacy callers (`ProxySensor.getSensorData()`, `MyHelper.getUnitsId()`) do not break while UI units vary by Metric/Imperial preference.
- **Preservation of Core Invariants**: Adding `internalUnit` alongside `unitId` preserves `getUnitId()`, `ordinal()`, `name()`, and all existing runtime outputs.
