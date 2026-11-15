# Stage 2: Requirement & Test Specification - ATT-7919: Aftermath Retry Sync Banner for Asleep Polar H10

**Ticket**: [ATT-7919](https://rainerblind.atlassian.net/browse/ATT-7919)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7919`  
**Requirement Mapping**: `REQ-SET-106` | **Test Spec ID**: `TST-SET-108`  
**Risk Classification**: `MEDIUM` (`Visual/UI` + DB Schema Migration v30 -> v31)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-SET-106)

### 1.1 Problem Statement & Rationale
A Polar H10 powers down BLE 45s after losing skin contact (e.g. swimmers unclipping straps before reaching lockers). When stop-time backfill times out in `TrackerService`, the workout must finalize without blocking, while persisting pending-sync metadata in `WorkoutSummaries.db`. An Aftermath retry banner prompts the athlete to wake the strap, executes deferred backfill, updates summaries, triggers re-export, and deletes the strap exercise slot strictly after SQLite commit.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration, On-Device Manual

### 1.3 Functional & Architectural Requirements
1. `TrackerService.endWorkout()` SHALL persist `polarDeviceId`, `polarExerciseId`, `polarExpectedStartMs`, `polarExpectedEndMs`, and `polarSyncPending = 1` into `WorkoutSummaries.db` upon stop-time backfill failure/timeout (`REQ-SET-106.1`).
2. `TrackOnMapScreen` SHALL render a Material 3 `PolarH10RetrySyncBanner` when `polarSyncPending == 1` displaying wake guidance, "Sync Now", and "Dismiss" with 100% 9-language localization parity (`REQ-SET-106.2`).
3. Tapping "Sync Now" SHALL invoke `PolarH10OfflineController.syncPendingWorkout()`, fetch stored RR intervals bounded by a 30s timeout, patch `WorkoutSamples.db` and `WorkoutRr.db`, recompute HRV summaries, delete strap exercise via `removeExercise()` strictly after SQLite commit, clear `polarSyncPending = 0`, and trigger `ExportManager.exportWorkout()` (`REQ-SET-106.3`).
4. Tapping "Dismiss" SHALL update `WorkoutSummaries.db` setting `polarSyncPending = 0` and hide the banner permanently without deleting sensor exercises (`REQ-SET-106.4`).
5. `WorkoutSummariesDatabaseManager` upgrade from version 30 to 31 SHALL add pending sync columns with exact parity between `onCreate()` and `onUpgrade()` (`REQ-SET-106.5`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Stop-Time Fallback)**: *Given* an asleep Polar H10 on workout stop, *When* 30s backfill times out, *Then* workout finishes without hanging and writes `polarSyncPending = 1` to `WorkoutSummaries.db`.
* **AC-2 (Aftermath Banner Display)**: *Given* a workout with `polarSyncPending == 1`, *When* opened in Aftermath, *Then* M3 retry sync banner is visible with wake guidance.
* **AC-3 (Deferred Sync & Re-Export)**: *Given* the athlete wakes the strap and taps "Sync Now", *When* backfill completes, *Then* samples/RR are patched, summaries recomputed, strap exercise deleted, banner removed, and exports regenerated.
* **AC-4 (Banner Dismissal)**: *Given* an athlete taps "Dismiss", *When* confirmed, *Then* `polarSyncPending` is cleared to 0 without deleting strap memory.

### 1.5 System Invariants
- Zero workout finalization delays when strap is asleep.
- Existing live GPS, cadence, power, and HR telemetry are never overwritten.
- `removeExercise()` on the strap executes strictly after SQLite commits succeed.

---

## 2. Test Specification (TST-SET-108)

* **TST-SET-108.1 (Stop-Time Fallback Persistence)**: Test asserting `TrackerService` records `polarSyncPending = 1` when backfill times out.
* **TST-SET-108.2 (DB v31 Migration & Parity)**: `WorkoutSummariesDatabaseManagerMigrationV31Test` asserting v30->v31 migration parity.
* **TST-SET-108.3 (M3 Banner & 9-Locale Parity)**: Compose rendering test and `TranslationParityTest` across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero emojis).
* **TST-SET-108.4 (Deferred Sync Integration)**: Test asserting "Sync Now" patches DBs, updates summaries, clears pending flag, executes `removeExercise()` post-commit, and triggers re-export.
* **TST-SET-108.5 (Dismiss Action)**: Test asserting "Dismiss" sets `polarSyncPending = 0` without deleting strap exercise.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-108.1]` | Unit | `TrackerService.endWorkout` stop-time fallback | `REQ-SET-106.1` | Specified |
| `[TST-SET-108.2]` | Integration | `WorkoutSummariesDatabaseManager` v31 migration | `REQ-SET-106.5` | Specified |
| `[TST-SET-108.3]` | UI / Locale | `PolarH10RetrySyncBanner` & `TranslationParityTest` | `REQ-SET-106.2` | Specified |
| `[TST-SET-108.4]` | Integration | `PolarH10OfflineController.syncPendingWorkout` | `REQ-SET-106.3` | Specified |
| `[TST-SET-108.5]` | Unit | `WorkoutRepository.dismissPolarSyncPending` | `REQ-SET-106.4` | Specified |
