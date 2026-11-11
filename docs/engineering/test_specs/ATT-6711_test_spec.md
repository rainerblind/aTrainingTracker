# Stage 2: Requirement & Test Specification - ATT-6711: Health Connect Navigation Drawer, Android 14+ Manifest & Record Mapping Hardening

**Ticket**: [ATT-6711](https://rainerblind.atlassian.net/browse/ATT-6711)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-SET-100` (`REQ-SET-100.2`), `REQ-SET-101` (`REQ-SET-101.2`, `REQ-SET-101.3`), `REQ-SET-103` (`REQ-SET-103.1`, `REQ-SET-103.2`)  
**Test Spec ID**: `TST-SET-100` (`TST-SET-100.2`, `TST-SET-100.3`), `TST-SET-101` (`TST-SET-101.2`, `TST-SET-101.3`, `TST-SET-101.4`, `TST-SET-101.5`), `TST-SET-103` (`TST-SET-103.3`, `TST-SET-103.4`, `TST-SET-103.5`)  
**Branch**: `feature/ATT-6711` | **Author**: AI Agent 1 (Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-SET-100, REQ-SET-101, REQ-SET-103)

### 1.1 Problem Statement & Rationale
`ATT-3052` delivered initial Health Connect support but left critical defects: `SettingsBottomSheetType.HEALTH_CONNECT` is unreachable due to missing `drawer_health_connect` in `ids.xml`, `AppNavigationDrawer.kt`, and `NavRoutes.toBottomSheetType()`; `WorkoutHeader.onSyncHealthConnect` is uncalled; `EditWorkoutScreen` triggers sync immediately on toggle while `HealthConnectManager.syncWorkout()` aborts if auto-sync is off; `WRITE_ELEVATION_GAINED` and cadence permissions are missing from `AndroidManifest.xml`, auto-denying permissions; Android 14+ mandatory `VIEW_PERMISSION_USAGE` activity alias is missing; `connect-client` is on alpha11; `ExerciseRoute` causes `SecurityException` when route write permission is denied; cadence series are omitted; calories are parsed from UI strings; telemetry writes risk Binder 1 MB overflows; and sample queries bypass repositories.

### 1.2 Functional & Architectural Requirements
1. **Navigation Drawer & Manual Sync Flow (`REQ-SET-103.1`, `REQ-SET-103.2`)**:
   - `res/values/ids.xml` SHALL define `drawer_health_connect`.
   - `AppNavigationDrawer.kt` SHALL include `drawer_health_connect` under `prefsOnlineCommunities` with vector icon and Last Sync timestamp (`Design Guidelines §3.4`).
   - `NavRoutes.toBottomSheetType()` SHALL map `R.id.drawer_health_connect` to `SettingsBottomSheetType.HEALTH_CONNECT`.
   - `WorkoutHeader.onSyncHealthConnect` SHALL be wired in `WorkoutDetailTabbedContent` and `WorkoutSummary`, invoking `HealthConnectManager.syncWorkout(..., force = true)`.
   - `HealthConnectManager.syncWorkout()` SHALL accept `force: Boolean = false`, allowing manual sync even when `syncToHealthConnect()` is disabled.
   - `EditWorkoutScreen.kt` SHALL NOT trigger sync on checkbox toggle; sync SHALL execute strictly upon clicking Save when checked.
2. **Android 14+ Manifest & Permission Hardening (`REQ-SET-100.2`)**:
   - `AndroidManifest.xml` SHALL declare `WRITE_ELEVATION_GAINED`, `WRITE_CYCLING_PEDALING_CADENCE`, and `WRITE_STEPS_CADENCE`.
   - `HealthConnectManager.REQUIRED_PERMISSIONS` SHALL include these permissions.
   - `AndroidManifest.xml` SHALL declare an `<activity-alias>` for `android.intent.action.VIEW_PERMISSION_USAGE` targeting `MainActivityWithNavigation` protected by `android.permission.START_VIEW_PERMISSION_USAGE` with category `android.intent.category.HEALTH_PERMISSIONS`.
   - `app/build.gradle` SHALL upgrade `androidx.health.connect:connect-client` to `1.1.0`.
3. **Record Mapping Hardening, Typed Calories & IPC Chunking (`REQ-SET-101.2`, `REQ-SET-101.3`)**:
   - `HealthConnectManager.syncWorkout()` SHALL gate `ExerciseRoute` attachment on `"android.permission.health.WRITE_EXERCISE_ROUTE" in granted`.
   - `HealthConnectRecordMapper` SHALL map cadence samples to `CyclingPedalingCadenceRecord` (cycling) or `StepsCadenceRecord` (running).
   - Calories SHALL be derived directly from numerical `WorkoutData.calories` (`Coding Standards §1.3`).
   - Telemetry series records SHALL be chunked to $\le 2,000$ samples per record/chunk.
   - Sample queries SHALL route through `WorkoutSamplesRepository` (`ATT-6426`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Drawer Navigation & Dialog)**: *Given* an athlete in the app, *When* opening the drawer and clicking Health Connect, *Then* `HealthConnectSettingsDialog` opens showing current sync status and Last Sync timestamp.
* **AC 2 (Manual Sync & Save Deferral)**: *Given* a finished workout, *When* clicking manual sync in `WorkoutHeader` or saving with sync enabled in `EditWorkoutScreen`, *Then* sync executes with `force = true` regardless of global auto-sync setting.
* **AC 3 (Manifest & Android 14+ Alias)**: *Given* an Android 14+ device, *When* inspecting Health Connect privacy permissions, *Then* `VIEW_PERMISSION_USAGE` activity alias is resolved and all declared write permissions can be granted without auto-denial.
* **AC 4 (Route Gate, Cadence & Chunking)**: *Given* a workout with route permission denied or >2,000 samples, *When* synced, *Then* session exports without route without `SecurityException`, cadence series are written, and series records are partitioned into chunks $\le 2,000$ samples.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-100` (`REQ-SET-100.2`), `REQ-SET-101` (`REQ-SET-101.2`, `101.3`), and `REQ-SET-103` (`REQ-SET-103.1`, `103.2`), targeting `AndroidManifest.xml`, `HealthConnectManager.kt`, `HealthConnectRecordMapper.kt`, `ids.xml`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `EditWorkoutScreen.kt`, and `app/build.gradle`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3052` (commit `3ef15b88`), amended in Sprint `2026-46.2` (`ATT-6712`, `ATT-6711`).
* **Root Reason for Existing Formulation**: Scoped initially to basic workout export; `ATT-3052` omitted drawer navigation wiring, elevation and cadence permissions in manifest, Android 14+ aliases, route permission check, series sample chunking, and typed calorie retrieval due to missing `WorkoutData.calories`.
* **Preservation of Core Invariants**: Local workout tracking, SQLite persistence, deterministic clientRecordId keying, and existing cloud export pipelines remain 100% untouched.

---

## 3. Test Specification (TST-SET-100, TST-SET-101, TST-SET-103)

### Test Case 1: Manifest & Permission Verification (`TST-SET-100.2`)
* **Scope**: `HealthConnectAvailabilityTest.kt` / `HealthConnectManagerTest.kt`. Verify declaration of `WRITE_ELEVATION_GAINED`, `WRITE_CYCLING_PEDALING_CADENCE`, `WRITE_STEPS_CADENCE`, and Android 14+ `VIEW_PERMISSION_USAGE` alias with `START_VIEW_PERMISSION_USAGE`.

### Test Case 2: Route Permission Gating & Cadence Series Mapping (`TST-SET-101.2`)
* **Scope**: `HealthConnectRecordMapperTest.kt` / `HealthConnectManagerTest.kt`. Verify `ExerciseRoute` is omitted when `WRITE_EXERCISE_ROUTE` is denied; verify cycling cadence maps to `CyclingPedalingCadenceRecord` and running cadence to `StepsCadenceRecord`.

### Test Case 3: Numerical Calorie Extraction & IPC Series Chunking (`TST-SET-101.3`, `TST-SET-101.4`)
* **Scope**: `HealthConnectRecordMapperTest.kt`. Verify numerical calorie mapping from `WorkoutData.calories`, and verify sample lists > 2,000 are partitioned into chunks of $\le 2,000$ points.

### Test Case 4: Repository Layering Architecture (`TST-SET-101.5`)
* **Scope**: `HealthConnectRecordMapperTest.kt`. Verify record extraction routes through `WorkoutSamplesRepository`.

### Test Case 5: Navigation Drawer Wiring & Route Mapping (`TST-SET-103.4`)
* **Scope**: `NavRoutesTest.kt` / `AppNavigationDrawerTest.kt`. Verify `drawer_health_connect` in `ids.xml`, configuration in `AppNavigationDrawer`, and `NavRoutes.toBottomSheetType()` resolving to `SettingsBottomSheetType.HEALTH_CONNECT`.

### Test Case 6: Manual Sync & Edit Workout Dialog Lifecycle (`TST-SET-103.5`)
* **Scope**: `HealthConnectManagerTest.kt` / `EditWorkoutScreenTest.kt`. Verify `force = true` manual sync in `HealthConnectManager`, `WorkoutHeader.onSyncHealthConnect` wiring, and `EditWorkoutScreen` deferred sync on save.

### Test Case 7: 9-Locale Translation Parity Verification (`TST-SET-103.3`)
* **Scope**: `TranslationParityTest.kt`. Verify 100% parity across all 9 locales for all added/modified strings.

### Test Case 8: Tier 1 Targeted Regression Suite (`TST-SET-100.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-100.2]` | Unit / Manifest | Manifest permissions & Android 14+ alias | `REQ-SET-100.2` | Specified |
| `[TST-SET-101.2]` | Unit / Mapper | Route permission guard & cadence series | `REQ-SET-101.2` | Specified |
| `[TST-SET-101.3]` | Unit / Mapper | Typed calorie extraction from `WorkoutData` | `REQ-SET-101.2` | Specified |
| `[TST-SET-101.4]` | Unit / Mapper | IPC sample chunking ($\le 2,000$ samples) | `REQ-SET-101.3` | Specified |
| `[TST-SET-101.5]` | Unit / Arch | `WorkoutSamplesRepository` abstraction | `REQ-SET-101.2` | Specified |
| `[TST-SET-103.4]` | UI / Nav | Drawer item, icon, timestamp & route mapping | `REQ-SET-103.1` | Specified |
| `[TST-SET-103.5]` | UI / Flow | `force = true` manual sync & save deferral | `REQ-SET-103.2` | Specified |
| `[TST-SET-103.3]` | Localization | 9-locale translation parity | `REQ-SET-103.1` | Specified |
| `[TST-SET-100.3]` | Regression | Tier 1 targeted unit test suite | `REQ-PRO-001` | Specified |
