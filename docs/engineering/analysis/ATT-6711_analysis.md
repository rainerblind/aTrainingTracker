# Stage 1 Analysis: ATT-6711 - Navigation Drawer Wiring, Android 14+ Manifest & Record Mapping Hardening for Health Connect

**Ticket**: [ATT-6711](https://rainerblind.atlassian.net/browse/ATT-6711)  
**Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6711` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint `2026-46.1` joint review audit of `ATT-3052` identified three critical blockers in Health Connect integration:
1. **Unreachable Settings Dialog & Unwired Manual Sync**: `SettingsBottomSheetType.HEALTH_CONNECT` exists in `NavRoutes.kt` and `ATrainingTrackerApp.kt:703`, but `drawer_health_connect` was omitted from `res/values/ids.xml`, `AppNavigationDrawer.kt`, and `NavRoutes.toBottomSheetType()`, leaving users without any UI navigation entry point. `WorkoutHeader.onSyncHealthConnect` has zero callers, and `EditWorkoutDialog` executes sync immediately upon checkbox toggle while `HealthConnectManager.syncWorkout()` aborts if global auto-sync is false.
2. **Android 14+ Manifest & Permission Blockers**: `HealthConnectManager.REQUIRED_PERMISSIONS` requests `ElevationGainedRecord` (`WRITE_ELEVATION_GAINED`), which is missing from `AndroidManifest.xml`, causing Health Connect to auto-deny permissions and perpetually reset `syncEnabled` to false. `AndroidManifest.xml` lacks the mandatory Android 14+ `<activity-alias>` for `android.intent.action.VIEW_PERMISSION_USAGE` (`HEALTH_PERMISSIONS`), and `app/build.gradle` uses `1.1.0-alpha11` instead of `1.1.0`.
3. **Route Permission Guard, Missing Cadence, Calorie String Scraping & IPC Chunking**: `syncWorkout()` attaches `ExerciseRoute` without verifying `"android.permission.health.WRITE_EXERCISE_ROUTE" in granted`, throwing `SecurityException`. `CyclingPedalingCadenceRecord` and `StepsCadenceRecord` are missing. Calories are extracted via UI formatted strings (`Coding Standards §1.3` violation, crashing on non-English locales), continuous 1-Hz samples are written unchunked (risking Binder 1 MB `TransactionTooLargeException`), and `HealthConnectRecordMapper` directly accesses `WorkoutSamplesDatabaseManager` instead of a repository (`ATT-6426`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Navigation Drawer & Routes**: `res/values/ids.xml` lacks `drawer_health_connect`; `AppNavigationDrawer.kt` lacks a drawer item under `prefsOnlineCommunities`; `NavRoutes.toBottomSheetType()` returns null for `R.id.drawer_health_connect`.
* **Manual Sync Lifecycle**: `WorkoutDetailTabbedContent.kt` does not pass `onSyncHealthConnect` to `WorkoutHeader`. `EditWorkoutScreen.kt:337-344` invokes `syncWorkoutAsync` directly in `onCheckedChange` rather than deferring to `onSave`. `HealthConnectManager.syncWorkout()` lacks a `force = true` override for manual syncs.
* **Manifest & SDK Status**: `AndroidManifest.xml` lacks `WRITE_ELEVATION_GAINED`, `WRITE_CYCLING_PEDALING_CADENCE`, and `WRITE_STEPS_CADENCE`, plus `<activity-alias>` with `permission="android.permission.START_VIEW_PERMISSION_USAGE"` for Android 14+ privacy review.
* **Independent Hypothesis Verification (4-Augen-Prinzip)**: Codebase forensics confirm `WorkoutData` lacked a direct `calories: Int?` field, forcing UI string parsing. Adding `calories` to `WorkoutData` and reading from `WorkoutSummaries.CALORIES` provides a clean numeric primitive. Binder IPC buffer limits (1 MB shared across process) require series sample chunking ($\le 2,000$ points/record). Wrapping `WorkoutSamplesDatabaseManager` in `WorkoutSamplesRepository` restores strict architectural layering (`ATT-6426`).

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  * Add `drawer_health_connect` to `ids.xml`, `AppNavigationDrawer.kt` (with vector icon and formatted Last Sync timestamp per `Design Guidelines §3.4`), and `NavRoutes.toBottomSheetType()`.
  * Wire `WorkoutHeader.onSyncHealthConnect` with manual sync (`force = true`), and defer `EditWorkoutDialog` sync to `onSave`.
  * Declare `WRITE_ELEVATION_GAINED`, cadence permissions, and Android 14+ `VIEW_PERMISSION_USAGE` alias in `AndroidManifest.xml`.
  * Upgrade `androidx.health.connect:connect-client` to `1.1.0`.
  * Guard `ExerciseRoute` on `WRITE_EXERCISE_ROUTE`, map cycling/running cadence series, extract numeric calories via `WorkoutData.calories`, chunk series to $\le 2,000$ points, and introduce `WorkoutSamplesRepository`.
* **Out-of-Scope Non-Goals**:
  * Changing `WorkoutSamples.db` SQLite schema.
  * Adding Android emulator tests (JVM unit/Robolectric tests only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID**: `REQ-SET-100`, `REQ-SET-101`, `REQ-SET-103` in `docs/requirements/req_settings_profiles.md`.
* **Historical Origin**: Sprint `2026-46.1` (`ATT-3052`, commit `3ef15b88`).
* **Root Reason for Existing Formulation**: ATT-3052 created initial Health Connect scaffolding; elevation and cadence permissions were omitted; UI string scraping was used because `WorkoutData` omitted calories.
* **Preservation of Core Invariants**: Standalone tracking loop unaffected; deterministic `clientRecordId` keying; 100% 9-language translation parity.

---

## 5. Architectural Strategy & High-Level Solution
1. **Navigation & UI Wiring**: Add `drawer_health_connect` to drawer configuration and route mapping. Pass `onSyncHealthConnect` in `WorkoutDetailTabbedContent.kt`. Defer `EditWorkoutDialog` sync to save with `force = true`.
2. **Manifest & Permissions**: Declare missing permissions in `AndroidManifest.xml` and add Android 14+ `<activity-alias>` with `android.permission.START_VIEW_PERMISSION_USAGE`. Add cadence permissions to `REQUIRED_PERMISSIONS`.
3. **Record Mapping & Repository**: Introduce `WorkoutSamplesRepository` for sample queries. Guard route creation on route permission. Chunk series samples to $\le 2,000$ records. Add `calories: Int?` to `WorkoutData` and use numeric primitive in `HealthConnectRecordMapper.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero tracking interruptions; no IPC Binder transaction overflows; backward compatibility of all workout exports.
* **Risk Rating**: **MEDIUM**. Touches manifest, IPC serialization, and multi-provider UI; mitigated by comprehensive Robolectric and unit tests under sprint lock.
