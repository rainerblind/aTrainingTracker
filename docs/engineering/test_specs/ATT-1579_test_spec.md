# Stage 2: Requirement & Test Specification - ATT-1579: CameraUpdateFactory Initialization Resilience in ImportBackupTabsScreen

**Ticket**: [ATT-1579](https://atrainingtracker.atlassian.net/browse/ATT-1579)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-MAP-022` (*Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview*)  
**Test Mapping**: `TST-MAP-024` (`TST-MAP-024.1`, `TST-MAP-024.2`, `TST-MAP-024.3`, `TST-MAP-024.4`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Formal Requirement Specification

### REQ-MAP-022: Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview
The system SHALL guard all camera animation and update calls against premature execution before the Google Maps native renderer and layout view are fully initialized, preventing fatal `NullPointerException`s and `IllegalStateException`s during workout recovery and import (ATT-1579):

1. **Map Lifecycle Synchronization (`ClusterNamingDialog` in `ImportBackupTabsScreen.kt`)**:
   - In `ClusterNamingDialog`, the system SHALL track map readiness via:
     ```kotlin
     var isMapLoaded by remember(state) { mutableStateOf(false) }
     ```
     keyed to `state` ensuring the readiness state resets cleanly per queued recovery item.
   - The `GoogleMap` composable SHALL register:
     ```kotlin
     onMapLoaded = { isMapLoaded = true }
     ```

2. **Guarded Camera Update Execution & Explicit Fallback**:
   - The camera animation effect SHALL bind to explicit keys:
     ```kotlin
     LaunchedEffect(bounds, isMapLoaded)
     ```
   - The system SHALL only invoke camera bounds animation when `isMapLoaded == true && bounds != null`.
   - The camera update execution SHALL be enclosed in a defensive `try-catch` block catching `Exception` (`NullPointerException`, `IllegalStateException`).
   - **Explicit Fallback**: If `CameraUpdateFactory.newLatLngBounds` throws an exception, the system SHALL catch the exception without crashing and fall back to static camera centering:
     ```kotlin
     cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)
     ```

3. **Initial Baseline Framing**:
   - `rememberCameraPositionState` SHALL initialize with:
     ```kotlin
     position = CameraPosition.fromLatLngZoom(bounds?.center ?: LatLng(0.0, 0.0), 12f)
     ```
     guaranteeing non-crashing baseline framing prior to map load.

4. **Invariants**:
   - Track polyline geometry, marker positioning, cluster scoring engine, and queue sequencing MUST NOT be broken.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-MAP-022`), fulfilling Epic `ATT-235` (*No crashs*) and complementing `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views*).
2. **Historical Origin & Commit Trace**:
   - Commit `5c5d353dc808a1a06866b73773ed735b74ddcf54` (Ticket `ATT-294`): Initial implementation of legacy recovery cluster naming dialog with route preview map.
   - Commit `e99cf4c34ebc9bc3c52382171b4c7cbd1af78f33` (Tickets `ATT-304`, `ATT-305`, `ATT-306`): Added `bounds?.let` null safety, but retained eager animation directly in `LaunchedEffect(bounds)` without map lifecycle synchronization.
3. **Root Reason for Existing Formulation**:
   - The developer intended to frame the imported polyline track bounds as soon as the dialog opened. However, because Google Play Services Maps initializes asynchronously, calling `CameraUpdateFactory` before `onMapLoaded` fires threw fatal NPEs in production (Crashlytics `83f3e051082969efdb92a73faaf6ca2d`).
4. **Preservation of Core Invariants**:
   - Track bounds calculation, 50dp camera padding, polyline decoding, start/end/apex markers, and cluster candidate selection flow remain 100% intact.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Cold Start & Deferred Execution)**:
  - *Given* an athlete navigating to the Import/Backup preview tab immediately upon cold start when `CameraUpdateFactory` is not yet initialized by Google Play Services,
  - *When* `ClusterNamingDialog` is composed,
  - *Then* the application SHALL NOT throw `NullPointerException`, and camera animation SHALL be deferred until `isMapLoaded` becomes `true`.

* **Criterion 2 (Camera Framing Integrity)**:
  - *Given* an active `ClusterNamingDialog` where the map completes loading,
  - *When* `isMapLoaded` transitions to `true` and `bounds` is non-null,
  - *Then* the camera SHALL smoothly animate to frame the track bounds with 50dp padding.

* **Criterion 3 (Defensive Exception Fallback)**:
  - *Given* a transient timing anomaly where `CameraUpdateFactory.newLatLngBounds` throws an `IllegalStateException` or `NullPointerException`,
  - *When* the exception is caught,
  - *Then* the dialog SHALL NOT crash, and camera position SHALL fall back to `CameraPosition.fromLatLngZoom(bounds.center, 12f)`.

* **Criterion 4 (Queue Transition State Reset)**:
  - *Given* multiple items in the recovery queue,
  - *When* advancing from item $N$ to $N+1$,
  - *Then* `isMapLoaded` SHALL reset to `false` and wait for the subsequent map surface to load.

---

## 4. Test Case Specification

### TST-MAP-024.1: Map Readiness & Lifecycle Synchronization Unit Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Test Objective**: Verify camera update trigger conditions:
  - When `isMapLoaded == false`, camera animation must NOT be triggered.
  - When `isMapLoaded == true && bounds != null`, camera animation is triggered.
  - When switching queue item `state`, `isMapLoaded` resets.

### TST-MAP-024.2: Defensive Exception Handling & Fallback Unit Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Test Objective**: Verify exception tolerance and deterministic fallback:
  - Simulate `NullPointerException` (uninitialized `CameraUpdateFactory`) -> verify exception is caught and camera falls back to `CameraPosition.fromLatLngZoom(bounds.center, 12f)` without crashing.
  - Simulate `IllegalStateException` (zero layout dimensions) -> verify exception is caught and camera falls back to static centering without crashing.

### TST-MAP-024.3: Bounds Envelope Calculation & Null Safety Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Test Objective**: Verify polyline envelope calculation:
  - Empty polyline -> `bounds` is `null` without throwing exceptions.
  - Valid multi-point polyline -> `bounds` is non-null with valid southwest and northeast coordinates and accurate center point.

### TST-MAP-024.4: Clean-Room Full Suite Regression Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Pass Criteria**: 100% pass rate across all 960+ unit tests with 0 failures and 0 regressions.
