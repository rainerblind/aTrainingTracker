# Stage 3: Implementation Plan - ATT-1579: CameraUpdateFactory Initialization Resilience in ImportBackupTabsScreen

**Ticket**: [ATT-1579](https://atrainingtracker.atlassian.net/browse/ATT-1579)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-MAP-022` (*Google Maps CameraUpdateFactory Initialization Resilience and Lifecycle Guarding in Import Route Preview*)  
**Test Mapping**: `TST-MAP-024`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Architectural Strategy & Design Overview

To eliminate the production crash (`NullPointerException: CameraUpdateFactory is not initialized`) and protect against zero-dimension layout exceptions (`IllegalStateException: Map size can't be 0`), `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` will be refactored to align with the proven, crash-resilient map architecture established in `PeriodSummaryCard.kt` and `PathPreviewMap.kt`:

1. **Lifecycle Readiness Synchronization**:
   - Introduce `var isMapLoaded by remember(state) { mutableStateOf(false) }`.
   - Keying to `state` ensures that when the user confirms or skips an item in a multi-workout recovery queue, the map readiness state deterministically resets to `false` until the next track's map surface signals completion.
   - Register `onMapLoaded = { isMapLoaded = true }` on the `GoogleMap` composable.

2. **Guarded Camera Animation with Explicit Fallback**:
   - Rebind the animation effect to `LaunchedEffect(bounds, isMapLoaded)`.
   - Enforce precondition `if (isMapLoaded && bounds != null)`.
   - Wrap the animation call in a `try-catch` block:
     ```kotlin
     try {
         cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 50))
     } catch (e: Exception) {
         Log.w("ImportBackupTabsScreen", "CameraUpdateFactory animation failed, falling back to static bounds center: ${e.message}")
         cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)
     }
     ```
   - If Google Maps throws during early rendering passes, the static center fallback guarantees that the track remains centered in the viewport without terminating the process.

3. **Baseline Camera Positioning**:
   - Retain `rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(bounds?.center ?: LatLng(0.0, 0.0), 12f) }` ensuring safe initial framing before the map layout pass completes.

---

## 2. Atomic Implementation Phases

### Phase 1: Harden `ClusterNamingDialog` in `ImportBackupTabsScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* **Changes**:
  1. Add `import android.util.Log` if needed.
  2. Declare `var isMapLoaded by remember(state) { mutableStateOf(false) }` above `cameraPositionState`.
  3. Replace unconditional `LaunchedEffect(bounds)` with:
     ```kotlin
     LaunchedEffect(bounds, isMapLoaded) {
         if (isMapLoaded && bounds != null) {
             try {
                 cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 50))
             } catch (e: Exception) {
                 Log.w("ImportBackupTabsScreen", "CameraUpdateFactory animation failed, falling back to static bounds center: ${e.message}")
                 cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)
             }
         }
     }
     ```
  4. Pass `onMapLoaded = { isMapLoaded = true }` into the `GoogleMap` composable call at line 618.

### Phase 2: Author Unit Tests (`ImportBackupMapResilienceTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt`
* **Test Cases**:
  1. `testBoundsBuilderFromDecodedPoints`: Verifies envelope construction with valid points vs empty points.
  2. `testCameraUpdatePreconditions`: Verifies that camera update is only eligible when `isMapLoaded` is true and `bounds` is non-null.
  3. `testCameraUpdateExceptionHandlingAndFallback`: Simulates `CameraUpdateFactory` failure and verifies fallback camera position is centered on bounds center.
  4. `testStateKeyingResetsMapLoaded`: Verifies state isolation across queued recovery items.

### Phase 3: Targeted Verification & Clean-Room Regression
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "*ImportBackupMapResilienceTest*"
  ```
* Run full clean-room unit test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Preserved Invariants & Safety Verification

1. **Track Envelope & Visual Fidelity**:
   - The 50dp padding, decoded points polyline, Start/End/Apex markers, and dark/light map properties remain 100% identical.
2. **Queue Processing**:
   - `remember(state)` semantics guarantee that queued user interactions (`queueCount > 1`) transition seamlessly without stale map states or missed zoom animations.
3. **Threading & Concurrency**:
   - Compose state operations run exclusively on the main/UI thread within standard lifecycle coroutine scopes.
