# Stage 2: Requirement & Test Specification - ATT-4019: Reduce redundant period rebuilds and triple tracking-finished handling

**Ticket**: [ATT-4019](https://rainerblind.atlassian.net/browse/ATT-4019)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Improve Periods/Stats*)  
**Target Release**: `2026-43.5` | **Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-PER-016` (*Period Rebuild Flow Projection, Single-Owner Tracking Finalization Orchestration & Shared Mapper Instance*)  
**Test Spec ID**: `TST-PER-022` | **Branch**: `feature/ATT-4019`  
**Author**: AI Agent 1 (Flash Implementer) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-PER-016)

### 1.1 Problem Statement & Rationale
`PeriodsRepository` triggers full O(N) period rebuilds on every `allWorkouts` emission (including export status updates, renames, and 50-item streaming chunks). Dual broadcasts from `TrackerService` combined with dual receivers in `PeriodsRepository` and an explicit call from `WorkoutRepository` cause 3 sequential database transactions and a 4th reactive rebuild per finished workout. `WorkoutDataMapper` is re-instantiated ad-hoc 3+ times.

### 1.2 Functional & Architectural Requirements
* `REQ-PER-016.1`: `PeriodsRepository` SHALL project and deduplicate `workoutRepo.allWorkouts` via `distinctUntilChanged` on period-relevant fields (ID, `startTimeS`, `activeTimeSec`, spatial bounds, sport) and debounce progressive stream bursts, preventing non-period updates from rebuilding periods.
* `REQ-PER-016.2`: `WorkoutRepository` SHALL be the sole authoritative orchestrator invoking `PeriodsRepository.onWorkoutFinished(workout)`. Redundant direct `BroadcastReceiver` registrations in `PeriodsRepository` SHALL be removed, and `onWorkoutFinished` SHALL enforce idempotent deduplication.
* `REQ-PER-016.3`: `PeriodsRepository` SHALL consolidate ad-hoc `WorkoutDataMapper` instantiations into a single shared/reusable instance.
* `REQ-PER-016.4`: Immediate period appearance (`REQ-PER-014`), DST aggregation boundaries (`REQ-PER-015`), and O(1) sync (`REQ-MIG-026`) MUST be strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* an athlete updating notes or export status on a workout, *When* `allWorkouts` emits, *Then* `PeriodsRepository`'s projected flow drops the emission without calling `loadFromDatabase()`.
* *Given* a workout completed in `TrackerService`, *When* `TRACKING_FINISHED_INTENT` is dispatched, *Then* `WorkoutRepository` calls `PeriodsRepository.onWorkoutFinished` exactly once, and zero duplicate receiver executions occur.
* *Given* period migration or queries running, *When* mapping cursor rows, *Then* the shared `WorkoutDataMapper` is reused.

### 1.4 System Invariants
Zero SQLite schema migrations; 100% preservation of period metrics and polyline previews; thread safety under `rebuildMutex`; 100% clean-room test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-014` (`PeriodsRepository.kt`, `WorkoutRepository.kt`). Interacts with `REQ-STB-018.1`.
* **Historical Origin & Commit Trace**: Sprint 2026-41.7 (`ATT-3056`, commit `f65f8fdd`) added dual broadcast receivers in `PeriodsRepository`. Later, Sprint 2026-43.3 (`ATT-4002`, commit `1c8e1a68`) delegated downstream notifications to `WorkoutRepository`.
* **Root Reason for Existing Formulation**: Dual listening guaranteed first-workout appearance on clean installs. Downstream architectural delegation in ATT-4002 created triple `onWorkoutFinished` invocations plus a reactive reload.
* **Preservation of Core Invariants**: First-workout immediate display (`REQ-PER-014`), O(1) sync (`REQ-MIG-026`), and period calculation accuracy remain 100% preserved.

---

## 3. Test Specification (TST-PER-022)

### Test Case 1: Flow Projection & Deduplication (`TST-PER-022.1`, Unit)
* **Target**: `app/src/test/.../PeriodsRepositorySyncTest.kt`
* **Preconditions / Action**: Emit `WorkoutData` with modified `notes`, `title`, or `stravaStatus` into `allWorkouts`.
* **Expected Result**: Projected flow suppresses trigger; `loadFromDatabase` is not invoked. Rapid chunk bursts are debounced.

### Test Case 2: Single-Owner Tracking Finalization & Idempotency (`TST-PER-022.2`, Unit)
* **Target**: `app/src/test/.../PeriodsRepositorySyncTest.kt`
* **Preconditions / Action**: Simulate `TRACKING_FINISHED_INTENT` handling; verify direct broadcast receivers are absent in `PeriodsRepository` and `WorkoutRepository` is the sole caller. Call `onWorkoutFinished` with duplicate IDs consecutively.
* **Expected Result**: `onWorkoutFinished` processes exactly once; duplicate consecutive calls are safely dropped.

### Test Case 3: Shared Mapper Instance Reuse (`TST-PER-022.3`, Unit)
* **Target**: `app/src/test/.../PeriodsRepositorySyncTest.kt`
* **Preconditions / Action**: Inspect mapper instance in `PeriodsRepository` across migration and single-row lookups.
* **Expected Result**: Exactly 1 shared `WorkoutDataMapper` instance is reused.

### Test Case 4: Localization Parity & Full Clean-Room Suite (`TST-PER-022.4`, Regression)
* **Command**: `./gradlew testDebugUnitTest` + `TranslationParityTest`
* **Expected Result**: Zero missing strings across 9 locales (0 new UI strings); 100% pass rate.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-022.1` | Unit | `PeriodsRepository.init` Flow collection | `REQ-PER-016.1` | Specified |
| `TST-PER-022.2` | Unit | `WorkoutRepository` / `PeriodsRepository.onWorkoutFinished` | `REQ-PER-016.2`, `REQ-PER-014` | Specified |
| `TST-PER-022.3` | Unit | `PeriodsRepository.mapper` | `REQ-PER-016.3` | Specified |
| `TST-PER-022.4` | Regr | `testDebugUnitTest` / `TranslationParityTest` | `REQ-PER-016.4`, `REQ-PRO-001` | Specified |

---

## 5. Gate 2 Sign-Off Criteria
* Complete bidirectional traceability between `REQ-PER-016` and `TST-PER-022.1-4`.
* Chesterton's Fence audit verified with all 4 mandatory fields.
* Zero production code modified; ready for Gate 2 review and Stage 3 planning.
