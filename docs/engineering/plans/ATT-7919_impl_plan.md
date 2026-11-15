# Stage 3: Implementation Plan - ATT-7919: Aftermath Retry Sync Banner for Asleep Polar H10

**Ticket**: [ATT-7919](https://rainerblind.atlassian.net/browse/ATT-7919) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7919`  
**Requirement Mapping**: `REQ-SET-106` | **Test Mapping**: `TST-SET-108`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  
**Risk Classification**: `MEDIUM` (`Visual/UI` + DB Schema Migration v30 -> v31)  

---

## 1. Problem Description & Background
When an athlete unclips a Polar H10 chest strap in the shower before stopping tracking at the locker, the H10 enters sleep mode 45s after losing skin contact. The stop-time backfill (ATT-7918) times out, leaving the recorded exercise inside sensor memory while the workout saves without HR/RR. This plan implements persistent pending sync metadata in `WorkoutSummaries.db`, a Material 3 retry banner in Aftermath (`TrackOnMapScreen`), deferred backfill via `PolarH10OfflineController.syncPendingWorkout()`, and post-commit strap cleanup + re-export.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-106` (`.1` pending metadata persistence, `.2` M3 retry banner & 9 locales, `.3` deferred sync & re-export, `.4` dismiss action, `.5` DB v31 migration parity)
* **Test Mapping**: `TST-SET-108` (`.1` timeout fallback persistence, `.2` DB v31 migration parity, `.3` M3 banner & 9-locale parity, `.4` deferred sync integration, `.5` dismiss action)

---

## 3. System Invariants & Preserved Behavior
1. **Non-Blocking Invariant**: Stop-time timeout never hangs `TrackerService.endWorkout()`; workout finalization always completes cleanly.
2. **Transactional Slot Protection**: `removeExercise()` on the strap is executed strictly after SQLite commits backfilled HR/RR samples.
3. **Existing Stream Integrity**: Live GPS, speed, power, and existing HR/RR samples are never overwritten or deleted.
4. **Dismiss Safety**: Dismissing the banner clears `polarSyncPending` without deleting sensor flash memory.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Database & Repository**:
  * Bump `WorkoutSummariesDbHelper.DB_VERSION` 30 -> 31; add `polarDeviceId`, `polarExerciseId`, `polarExpectedStartMs`, `polarExpectedEndMs`, `polarSyncPending`.
  * Update `WorkoutData.kt`, `WorkoutDataMapper.kt`, and `WorkoutRepository.kt` with pending sync state and dismiss method.
* **PolarH10OfflineController**:
  * Record pending sync metadata in `WorkoutSummaries.db` on stop-time connection failure/timeout.
  * Implement `syncPendingWorkout(context, workoutId)`: connects to strap, verifies window/exercise ID, executes ATT-7918 backfill, clears pending flag, refreshes UI, and triggers `exportManager.exportWorkout()`.
* **UI Consistency (`docs/design_guidelines.md` §5.4 & §5.10)**:
  * Component: `PolarH10RetrySyncBanner.kt` using `ElevatedCard` with `secondaryContainer` tint, prominent wake instructions, primary `Button` ("Sync Now"), and secondary `OutlinedButton` ("Dismiss").
  * 9-language localization in `strings_devices.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero decorative inline emojis.
  * Visual verification via `@Preview` and `emulator-5554` screenshot plan.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Database Schema Migration v30 -> v31
* Files: `WorkoutSummariesDatabaseManager.java`, `DatabaseMigrationIndexTest.kt`, `WorkoutRpeFeelTest.kt`, `WorkoutSummariesDatabaseManagerMigrationV31Test.kt`.
* Changes: Bump DB_VERSION to 31; add columns with `addColumnIfNotExists` and matching `onCreate`; add `setPolarSyncPending` / `clearPolarSyncPending`; update test version assertions.
### Step 2: PolarH10OfflineController Stop-Time Fallback & Deferred Backfill
* Files: `PolarH10OfflineController.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`.
* Changes: Persist pending metadata on stop-time timeout; implement `syncPendingWorkout()`, window match, post-commit `removeExercise()`, UI refresh, and re-export.
### Step 3: 9-Language Localization (Zero Emojis)
* Files: `app/src/main/res/values*/strings_devices.xml` (all 9 locales).
* Changes: Add `polar_h10_retry_sync_title`, `_desc`, `_action`, `_dismiss`, `_syncing`, and `_failed`.
### Step 4: Material 3 PolarH10RetrySyncBanner & Aftermath Wiring
* Files: `PolarH10RetrySyncBanner.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`.
* Changes: Implement M3 banner card with wake instructions, "Sync Now" loading state, and "Dismiss"; mount at top of overview content when `polarSyncPending == true`; add `@Preview`.
### Step 5: Unit & Integration Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerMigrationV31Test" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineControllerTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted tests, `TranslationParityTest`, Compose `@Preview`, followed by clean-room full test suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-7919` ensures zero impact on `sprint/2026-46.7`.
