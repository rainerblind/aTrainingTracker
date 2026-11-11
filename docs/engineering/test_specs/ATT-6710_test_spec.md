# Stage 2: Requirement & Test Specification - ATT-6710: Live sparkline buffers become empty when backgrounding app or recreating TrackingViewModel

**Ticket**: [ATT-6710](https://rainerblind.atlassian.net/browse/ATT-6710) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6710`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.6`..`REQ-TRK-059.8`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.6`..`TST-TRK-059.8`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-TRK-059`)

### 1.1 Functional Scope & Technical Invariants
* **Singleton Repository Sparkline Buffer Persistence (`REQ-TRK-059.6`)**: In `BANALServiceRepository.kt`, the system SHALL maintain active `LiveSparklineBuffer` instances across the singleton application lifecycle. Incoming numeric telemetry samples during `startObservingBANALService()` SHALL feed active buffers at 1 Hz. Buffers SHALL persist across `TrackingViewModel` recreation and drawer navigation, resetting only when `clearBreadcrumbs()` executes on workout start/reset.
* **Non-Emitting Tick Sample Retention (`REQ-TRK-059.7`)**: In `TrackingViewModel.applySensorData()`, every field configured with `showGraph == true` SHALL retain its latest buffer snapshot from `BANALServiceRepository`. When `allSensorData` is empty or a sensor does not emit in a given tick, the field SHALL NOT revert to `emptyList()` from `cachedBaseFields`.
* **Root Back Navigation Task Preservation (`REQ-TRK-059.8`)**: In `ATrainingTrackerApp.kt` Layer 3 root `BackHandler`, when `currentRoute == NavRoutes.START_TRACKING` and `previousBackStackEntry == null`, the app SHALL call `activity.moveTaskToBack(true)` instead of `activity.finish()`, preserving `MainActivityWithNavigation` and its `ViewModelStore` without lifecycle teardown.
* **Localization Impact**: N/A (zero new user-facing strings; 100% existing 9-locale parity preserved).

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Root Back Navigation Backgrounding)**: *Given* live sparkline graphs accumulating samples in the Cockpit, *When* the user presses the system Back gesture/button at root `NavRoutes.START_TRACKING`, *Then* `moveTaskToBack(true)` is invoked, `MainActivityWithNavigation` is backgrounded without `onDestroy()`, and returning to foreground retains full sparkline history.
* **AC-2 (Drawer Navigation & ViewModel Recreation)**: *Given* active sparkline buffers with accumulated telemetry samples, *When* navigating across drawer destinations (Workouts, Routes, Sensors) or recreating `TrackingViewModel`, *Then* `BANALServiceRepository` preserves all recorded samples and immediately re-populates the Cockpit tiles upon return.
* **AC-3 (Non-Emitting Tick Retention)**: *Given* `cachedBaseFields` in `TrackingViewModel`, *When* `applySensorData()` processes an empty emission or partial sensor update where a graph-enabled sensor is absent, *Then* the field retains its accumulated sparkline samples and never reverts to `emptyList()`.

### 1.3 System Invariants
Zero memory leaks; bounded sparkline memory ($\le 1\text{ KB}$ per tile / $\le 300$ samples); existing back-stack navigation on child destinations preserved; 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-059`, extending `REQ-TRK-046`, `REQ-UI-436`, and `REQ-UI-159`. Refined by `ATT-6710` for singleton repository buffer persistence in `BANALServiceRepository.kt`, sample retention in `TrackingViewModel.kt`, and `moveTaskToBack(true)` in `ATrainingTrackerApp.kt`.
2. *Historical Origin & Commit Trace*: `ATT-4841` (DB v16 `SHOW_VALUE`/`SHOW_BAR`), `ATT-4927` (DB v18), `ATT-1294` (`LiveSparklineBuffer` in `TrackingViewModel`), `ATT-6710` (buffer wiping on root `finish()` and `cachedBaseFields` fallback).
3. *Root Reason for Existing Formulation*: Root `BackHandler` previously called `finish()` as the standard root screen exit default. Sparkline buffers were originally placed inside `TrackingViewModel` for initial UI prototyping without considering Activity recreation or backgrounding.
4. *Preservation of Core Invariants*: Bounded memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile), 1 Hz decimation math, `SensorSparklineView` canvas rendering, drawer navigation hierarchy, and back-stack behavior on child destinations remain 100% preserved.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-059`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-059.6` (Singleton Buffer Persistence & Lifecycle Test)**: In `BANALServiceRepositorySparklineTest.kt`, verify `BANALServiceRepository` registers and feeds 1 Hz telemetry samples into `LiveSparklineBuffer`, buffers survive multiple simulated ViewModel connections/disconnections, and `clearBreadcrumbs()` resets buffers to empty.
* **`TST-TRK-059.7` (TrackingViewModel Non-Emitting Tick Sample Retention)**: In `TrackingViewModelSparklineTest.kt`, verify `applySensorData()` preserves latest sparkline samples for `showGraph == true` fields when `allSensorData` is empty or lacks updates for that sensor, asserting fields never revert to `emptyList()`.
* **`TST-TRK-059.8` (Root Back Navigation moveTaskToBack Contract Test)**: In `ATrainingTrackerAppNavigationTest.kt`, verify root `BackHandler` on `NavRoutes.START_TRACKING` invokes `moveTaskToBack(true)` instead of `finish()` when `previousBackStackEntry` is null.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.6` | `TST-TRK-059.6` | `BANALServiceRepositorySparklineTest` | Singleton repository buffer persistence, 1 Hz telemetry accumulation, and reset on `clearBreadcrumbs()` | Specified |
| `REQ-TRK-059.7` | `TST-TRK-059.7` | `TrackingViewModelSparklineTest` | Preservation of accumulated samples on empty or partial sensor emissions | Specified |
| `REQ-TRK-059.8` | `TST-TRK-059.8` | `ATrainingTrackerAppNavigationTest` | Root back navigation invokes `moveTaskToBack(true)` preserving Activity task | Specified |
| `REQ-UI-106` | `TST-TRK-059.2` | `TranslationParityTest` | 9-locale parity verification (EN, DE, ES, FR, IT, JA, NL, PL, PT) | Specified |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Regression Suite | `--tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` | Specified |
