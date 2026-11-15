# Stage 5 Walkthrough: ATT-7876

**Ticket**: ATT-7876 (Bug)  
**Summary**: Prevent post-crash barometric calibration from corrupting pre-crash samples, DEM columns, and average altitude  
**Parent Epic**: ATT-419 (Live Tracking & Session Recording)  
**Target Release**: `V5.0.0`  
**Requirement Mapping**: `REQ-FIL-018` (`REQ-FIL-018.1`..`REQ-FIL-018.3`)  
**Test Spec Mapping**: `TST-FIL-018` (`TST-FIL-018.1`..`TST-FIL-018.5`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Executive Summary & Implementation Highlights
- **Scope**: Bounds barometric drift correction in `WorkoutSamplesDatabaseManager` and `LiveWorkoutSession` to strictly barometric pressure device columns (and canonical `ALTITUDE` only on rows where the barometric source matches within 0.01 before mutation).
- **Epoch Bounding (Case A, `t0Ms <= 0`)**: Enforces `TIME >= floor(sessionEpochStartTimeMs / 1000)` and `TIME <= t1Ms / 1000.0`. Pre-crash samples recorded prior to process restart remain immutable.
- **Dimensional Consistency (Case B, `t0Ms > 0`)**: Adjusts `altStats.sum` via `meanCorrection * altStats.count` to match 1-Hz counting frequency rather than aggregating 20-second sampled points directly over 1-Hz sample count.
- **Characterisation Note**: `TrackerServiceCrashRecoveryCharacterisationTest` on base commit `533d88b1` is left untouched as it is exclusively owned and remediated under `ATT-7902`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-018.1` | `TST-FIL-018.1` | Unit (Robolectric) | PASSED | `[Verified]` |
| `REQ-FIL-018.2` | `TST-FIL-018.2` | Unit (Robolectric) | PASSED | `[Verified]` |
| `REQ-FIL-018.3` | `TST-FIL-018.3` | Unit (Robolectric) | PASSED | `[Verified]` |
| `REQ-FIL-018.1` | `TST-FIL-018.4` | Contract / Unit | PASSED | `[Verified]` |
| `REQ-FIL-018.1`..`.3` | `TST-FIL-018.5` | Tier-1 Suite | PASSED | `[Verified]` |

---

## 3. Automated Test Evidence (Streamlined Tier-1)

Executed under `tools/parallel_sprint_lock.py` (`flock /tmp/att_build_merge.lock`):
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.integration.TrackerServiceIntegrationTest" \
  --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSession*" \
  --tests "com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager*" \
  --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPressure*" \
  --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
  --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
  :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain --max-workers=2
```

### Observed Test Results (69/69 passed, 0 failures, 0 errors, 0 skipped):
- `TrackerServiceIntegrationTest`: 5/5 passed
- `LiveWorkoutSessionTest`: 6/6 passed
- `LiveWorkoutSessionAltitudeDriftTest`: 5/5 passed
- `WorkoutSamplesDatabaseManagerTest`: 7/7 passed
- `WorkoutSamplesDatabaseManagerEscapingTest`: 6/6 passed
- `AltitudeFromPressureDeviceTest`: 12/12 passed
- `TranslationParityTest`: 10/10 passed
- `SourceTextReadingAuditContractTest`: 4/4 passed
- `AspiceSweAuditContractTest`: 9/9 passed
- `ThemeColorCentralizationAuditTest`: 5/5 passed

### Quality Gates:
- `:app:lintDebug`: 0 errors
- `:app:compileDebugAndroidTestKotlin`: SUCCESSFUL
- Scoped Detekt: 0 issues on touched files (`WorkoutRepository.kt`, `WorkoutSamplesDatabaseManagerTest.kt`, `LiveWorkoutSessionAltitudeDriftTest.kt`)
- `python3 tools/verify_requirement_governance.py --sync-indexes`: PASS (100% parity)
