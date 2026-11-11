# Stage 5 Walkthrough: ATT-6711 - Health Connect Navigation Drawer Wiring, Android 14+ Manifest & Record Mapping Hardening

**Ticket**: [ATT-6711](https://rainerblind.atlassian.net/browse/ATT-6711)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-SET-100` (`REQ-SET-100.2`), `REQ-SET-101` (`REQ-SET-101.2`, `REQ-SET-101.3`), `REQ-SET-103` (`REQ-SET-103.1`, `REQ-SET-103.2`)  
**Test Spec ID**: `TST-SET-100` (`TST-SET-100.2`, `TST-SET-100.3`), `TST-SET-101` (`TST-SET-101.2`, `TST-SET-101.3`, `TST-SET-101.4`, `TST-SET-101.5`), `TST-SET-103` (`TST-SET-103.3`, `TST-SET-103.4`, `TST-SET-103.5`), `REQ-PRO-001`  
**Branch**: `feature/ATT-6711` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
Sprint `2026-46.1` follow-up of `ATT-3052` resolved three critical integration blockers in Android Health Connect:
1. **Navigation Drawer & Dialog Integration**: `res/values/ids.xml` now declares `drawer_health_connect`, mounted as a first-class entry in `AppNavigationDrawer.kt` under `prefsOnlineCommunities` with custom monochrome vector drawable `ic_health_connect.xml` and theme-aware tinting. `NavRoutes.toBottomSheetType()` resolves `drawer_health_connect` to `SettingsBottomSheetType.HEALTH_CONNECT`, opening `HealthConnectSettingsDialog.kt` which now formats and renders the Last Sync timestamp per `Design Guidelines §3.4`.
2. **Manual Sync & Save Deferral**: Added `force: Boolean = false` to `HealthConnectManager.syncWorkout()`. Wired `WorkoutHeader.onSyncHealthConnect` with `force = true` in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`, allowing manual sync even when auto-sync is disabled. Deferred sync in `EditWorkoutScreen.kt` strictly to Save button click when checked.
3. **Android 14+ Manifest & Permission Hardening**: Declared `WRITE_ELEVATION_GAINED` and `WRITE_STEPS` in `AndroidManifest.xml` alongside `HealthConnectManager.REQUIRED_PERMISSIONS`, resolving auto-denial on Android 14+. Declared mandatory `<activity-alias>` for `android.intent.action.VIEW_PERMISSION_USAGE` with category `HEALTH_PERMISSIONS` and permission `android.permission.START_VIEW_PERMISSION_USAGE`. Upgraded `androidx.health.connect:connect-client` to stable `1.1.0`.
4. **Record Mapping, Route Gating, Chunking & Typed Calories**: Created `WorkoutSamplesRepository` on `Dispatchers.IO` encapsulating `WorkoutSamplesDatabaseManager` queries returning typed `WorkoutTelemetrySample` models (`Rule 81 / ATT-6426`). Guarded `ExerciseRoute` strictly on `hasRoutePermission` without throwing `SecurityException`. Added cadence series mapping (`CyclingPedalingCadenceRecord` and `StepsCadenceRecord`). Extracted typed numeric calories via `WorkoutData.calories` mapped from `WorkoutSummaries.CALORIES`. Chunked all series records to $\le 2,000$ points/record using `Metadata.activelyRecorded(...)` per stable 1.1.0 API.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-100.2` | `TST-SET-100.2` | Unit / Manifest (`HealthConnectAvailabilityTest`, `HealthConnectManagerTest`) | **PASSED** | `Verified` |
| `REQ-SET-101.2` | `TST-SET-101.2` | Unit (`HealthConnectRecordMapperTest`) | **PASSED** | `Verified` |
| `REQ-SET-101.3` | `TST-SET-101.4` | Unit / IPC (`HealthConnectRecordMapperTest`) | **PASSED** | `Verified` |
| `REQ-SET-101.2` | `TST-SET-101.5` | Architecture (`WorkoutSamplesRepository`) | **PASSED** | `Verified` |
| `REQ-SET-103.1` | `TST-SET-103.4` | Navigation (`AppNavigationDrawerContractTest`, `NavRoutesTest`) | **PASSED** | `Verified` |
| `REQ-SET-103.2` | `TST-SET-103.5` | UI / Flow (`HealthConnectSyncTest`, `EditWorkoutScreen`) | **PASSED** | `Verified` |
| `REQ-SET-103.1` | `TST-SET-103.3` | Localization (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SET-100.3` | Tier 1 Targeted Regression (137 tests under lock) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Tests (Tier 1 Regression Scope)
```text
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.settings.healthconnect.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" \
  --tests "com.atrainingtracker.trainingtracker.localization.*"

BUILD SUCCESSFUL
137 tests completed, 0 failed, 0 skipped
```

### Living Documentation & Governance Check
```text
python3 tools/verify_requirement_governance.py --sync-indexes
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Verification & Clean-Room Audit
* **Invariants**: Core tracking loop operates independently of Health Connect status; deterministic clientRecordId keying; no IPC Binder buffer overflows (>2,000 samples chunked); zero leaky DatabaseManager references (`WorkoutSamplesRepository` encapsulation); 100% 9-language translation parity.
