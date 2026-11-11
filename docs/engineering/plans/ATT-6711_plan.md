# Stage 3: Implementation Plan - ATT-6711: Health Connect Navigation Drawer, Android 14+ Manifest & Record Mapping Hardening

**Ticket**: [ATT-6711](https://rainerblind.atlassian.net/browse/ATT-6711)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-SET-100` (`REQ-SET-100.2`), `REQ-SET-101` (`REQ-SET-101.2`, `REQ-SET-101.3`), `REQ-SET-103` (`REQ-SET-103.1`, `REQ-SET-103.2`)  
**Test Mapping**: `TST-SET-100` (`TST-SET-100.2`, `TST-SET-100.3`), `TST-SET-101` (`TST-SET-101.2`, `TST-SET-101.3`, `TST-SET-101.4`, `TST-SET-101.5`), `TST-SET-103` (`TST-SET-103.3`, `TST-SET-103.4`, `TST-SET-103.5`), `REQ-PRO-001`  
**Branch**: `feature/ATT-6711` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint `2026-46.1` follow-up identified three critical blockers: `SettingsBottomSheetType.HEALTH_CONNECT` is unreachable due to missing `drawer_health_connect` in `ids.xml`, `AppNavigationDrawer.kt`, and `NavRoutes.toBottomSheetType()`; `WorkoutHeader.onSyncHealthConnect` is unwired; `EditWorkoutScreen` triggers sync immediately on checkbox toggle while `HealthConnectManager.syncWorkout()` aborts if auto-sync is off; `WRITE_ELEVATION_GAINED` and cadence permissions are missing from `AndroidManifest.xml`, auto-denying permissions; Android 14+ mandatory `VIEW_PERMISSION_USAGE` activity alias is missing; `connect-client` is on alpha11; `ExerciseRoute` causes `SecurityException` when route write permission is denied; cadence series are omitted; calories are parsed from UI strings; telemetry writes risk Binder 1 MB overflows; and sample queries bypass repositories.

---

## 2. Traceability & Requirements Mapping
* `REQ-SET-100.2` (Manifest permissions, Android 14+ alias & connect-client 1.1.0) $\rightarrow$ `TST-SET-100.2` (`HealthConnectManagerTest`)
* `REQ-SET-101.2` (Route permission guard, cadence series & typed calories) $\rightarrow$ `TST-SET-101.2`, `TST-SET-101.3` (`HealthConnectRecordMapperTest`)
* `REQ-SET-101.3` (IPC sample chunking $\le 2,000$ points) $\rightarrow$ `TST-SET-101.4` (`HealthConnectRecordMapperTest`)
* `REQ-SET-101.2` (`WorkoutSamplesRepository` encapsulation) $\rightarrow$ `TST-SET-101.5` (`WorkoutSamplesRepositoryTest`)
* `REQ-SET-103.1` (Drawer item, icon, Last Sync & route mapping) $\rightarrow$ `TST-SET-103.4` (`NavRoutesTest`, `AppNavigationDrawerTest`)
* `REQ-SET-103.2` (`force = true` manual sync & save deferral) $\rightarrow$ `TST-SET-103.5` (`HealthConnectManagerTest`, `EditWorkoutScreenTest`)
* `REQ-SET-103.1` (9-locale translation parity) $\rightarrow$ `TST-SET-103.3` (`TranslationParityTest`)
* `REQ-PRO-001` (Tier 1 targeted regression suite) $\rightarrow$ `TST-SET-100.3` (Parallel lock Gradle verification)

---

## 3. System Invariants & Preserved Behavior
1. **Tracking Loop Isolation**: Local tracking, GPS logging, sensor streaming, and SQLite persistence operate independently of Health Connect availability or permission status.
2. **Idempotence & Keying Invariant**: Health Connect records retain deterministic `clientRecordId` keying; repeated syncs update existing records rather than producing duplicates.
3. **Repository Cleanliness (Rule 81 / ATT-6426)**: No direct `WorkoutSamplesDatabaseManager` access from cloud mappers; routed strictly through `WorkoutSamplesRepository`.

---

## 4. Proposed Architectural Changes

### UI Consistency (`design_guidelines.md` §3.1, §3.4, §5)
* **Reference Component**: `AppNavigationDrawer.kt`, `CloudProviderSettingsContent.kt`, `WorkoutHeader.kt`.
* **Reused Components**: `DrawerItemConfig`, `Icon`, `Text`, `Checkbox`, `AppBottomSheetContent`.
* **Theme Tokens**: `MaterialTheme.colorScheme.primary`, `onSurface`, `onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Navigation Drawer Wiring & Route Resolution (`ids.xml`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `ic_health_connect.xml`)
* Files: `res/values/ids.xml`, `app/src/main/res/drawable/ic_health_connect.xml`, `AppNavigationDrawer.kt`, `NavRoutes.kt`.
* Action: Add `drawer_health_connect` to `ids.xml`. Add vector drawable `ic_health_connect.xml`. Insert `DrawerItemConfig(R.id.drawer_health_connect, ...)` in `AppNavigationDrawer.kt` under `prefsOnlineCommunities`. Map `R.id.drawer_health_connect -> SettingsBottomSheetType.HEALTH_CONNECT` in `NavRoutes.toBottomSheetType()`. In `HealthConnectSettingsDialog.kt`, render formatted Last Sync timestamp per `Design Guidelines §3.4`.

### Step 2: Manual Sync & Save Deferral (`WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `EditWorkoutScreen.kt`, `HealthConnectManager.kt`)
* Files: `HealthConnectManager.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `EditWorkoutScreen.kt`.
* Action: Add `force: Boolean = false` to `HealthConnectManager.syncWorkout()` and `syncWorkoutAsync()`. Wire `WorkoutHeader.onSyncHealthConnect` in `WorkoutDetailTabbedContent.kt` and `WorkoutSummary.kt`. In `EditWorkoutScreen.kt`, stop invoking sync on checkbox toggle; trigger `syncWorkoutAsync(..., force = true)` exclusively upon clicking Save when checkbox is checked.

### Step 3: Android 14+ Manifest & Permission Hardening (`AndroidManifest.xml`, `HealthConnectManager.kt`, `app/build.gradle`)
* Files: `AndroidManifest.xml`, `HealthConnectManager.kt`, `app/build.gradle`.
* Action: Declare `WRITE_ELEVATION_GAINED`, `WRITE_CYCLING_PEDALING_CADENCE`, and `WRITE_STEPS_CADENCE` in `AndroidManifest.xml` and add to `HealthConnectManager.REQUIRED_PERMISSIONS`. Declare `<activity-alias>` for `android.intent.action.VIEW_PERMISSION_USAGE` with `START_VIEW_PERMISSION_USAGE` permission and `HEALTH_PERMISSIONS` category. Upgrade `androidx.health.connect:connect-client` to `1.1.0`.

### Step 4: Typed Calorie Extraction & IPC Series Chunking (`WorkoutData.kt`, `WorkoutDataMapper.kt`, `HealthConnectRecordMapper.kt`)
* Files: `WorkoutData.kt`, `WorkoutDataMapper.kt`, `HealthConnectRecordMapper.kt`.
* Action: Add `val calories: Int? = null` to `WorkoutData`. Populate from `WorkoutSummaries.CALORIES` in `WorkoutDataMapper.kt`. In `HealthConnectRecordMapper.kt`, replace string scraping with typed `workoutData.calories`. Chunk all time-series samples (`hrSamples`, `powerSamples`, `speedSamples`, cadence) to $\le 2,000$ points per record chunk with indexed `clientRecordId`.

### Step 5: Route Permission Guard, Cadence Series & `WorkoutSamplesRepository` (`WorkoutSamplesRepository.kt`, `HealthConnectRecordMapper.kt`)
* Files: `WorkoutSamplesRepository.kt`, `HealthConnectRecordMapper.kt`, `HealthConnectManager.kt`.
* Action: Create `WorkoutSamplesRepository` wrapping `WorkoutSamplesDatabaseManager` on `Dispatchers.IO`. In `HealthConnectManager.syncWorkout()`, pass `hasRoutePermission` to `mapWorkoutToRecords()`. Map `SensorType.CADENCE` to `CyclingPedalingCadenceRecord` (cycling) and `StepsCadenceRecord` (running).

### Step 6: Targeted Unit & Regression Test Verification
* Files: `HealthConnectRecordMapperTest.kt`, `HealthConnectManagerTest.kt`, `NavRoutesTest.kt`, `AppNavigationDrawerTest.kt`, `EditWorkoutScreenTest.kt`, `TranslationParityTest.kt`.
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test suite pass under `/tmp/att_build_merge.lock`, zero AAPT2 compilation errors, and 100% 9-language translation parity.
* **Rollback**: Branch `feature/ATT-6711` is fully isolated from `sprint/2026-46.2` until Stage 5 squash merge.
