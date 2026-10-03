# Architecture & Implementation Plan: Compose List Map Lite-Mode Enforcement & RenderThread Deadlock Immunity (ATT-1246 / Gate 3)

## 1. Context & Objectives
In production release `4.9.36 (260)`, Crashlytics reported a fatal native ANR (Issue `1b9b9a1021c548bb3c8e6b665f963fe1`, Session `6AAEDA02007B00020E63172E3D797304_DNE_0_v2`) characterized by a circular deadlock between the Android Main (UI) Thread and the HWUI `RenderThread`:
* **Main Thread**: Blocked in `std::__1::future<void>::get()` inside `RenderProxy::setStopped(bool)` invoked via `HardwareRenderer.setStopped(true) -> nSetStopped()`. Because `nSetStopped` is an ART `@FastNative` method, the main thread retains the ART `mutator_lock_` in Shared mode without transitioning to native state.
* **RenderThread**: Blocked in `art::ConditionVariable::WaitHoldingLocks(art::Thread*)` inside JNI `CallVoidMethodV` attempting to deliver `setFrameCompleteCallback` for a completed `DrawFrameTask`. To invoke Java code, it must acquire the ART `mutator_lock_`, which is held by the main thread.

This deadlock was triggered by the introduction of full-fidelity, hardware-accelerated `GoogleMap` instances within Compose `LazyColumn` list items (`PathPreviewMap.kt` in `WorkoutSummary` and `RouteItem`, and `WorkoutClusterComponents.kt` in `ClusterCard` and `ClusterWorkoutCard`) without Lite Mode.

This plan details the architectural design, code modifications, and verification plan to enforce Google Maps Lite Mode across all list preview maps, permanently decoupling thumbnail rendering from the native HWUI render thread and eliminating native lock contention.

---

## 2. Requirement & Test Specification Traceability
- **Parent Ticket**: `ATT-1246` (`[Bug] art::ConditionVariable::WaitHoldingLocks(art::Thread*)`)
- **Fix Version**: `V4.9.38`
- **Sub-task**: `ATT-1273` (`[Impl-Plan]`)
- **System Requirement**: `REQ-STB-010` (*Static Map Lite-Mode Enforcement in Compose Lazy Lists & RenderThread Deadlock Immunity*) in `docs/requirements.md`
- **Test Specification**: `TST-STB-010` (*Compose List Map Lite-Mode Enforcement & RenderThread Deadlock Immunity Verification*) in `docs/tests.md`
- **Stage**: `Stage 3: Implementation Plan (SWE.2 / SWE.3)`

---

## 3. Detailed Component Architecture & Modifications

### 3.1 `PathPreviewMap.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`)
1. **Import `GoogleMapOptions`**:
   - Add `import com.google.android.gms.maps.GoogleMapOptions`.
2. **Inject `googleMapOptionsFactory`**:
   - In the `GoogleMap` composable call (line 58):
     ```kotlin
     GoogleMap(
         modifier = modifier,
         cameraPositionState = cameraPositionState,
         googleMapOptionsFactory = {
             GoogleMapOptions().liteMode(true)
         },
         uiSettings = MapUiSettings(
             zoomControlsEnabled = false,
             compassEnabled = false,
             mapToolbarEnabled = false,
             myLocationButtonEnabled = false,
             scrollGesturesEnabled = false, // Static look for list rows
             zoomGesturesEnabled = false
         ),
         properties = MapProperties(mapType = MapType.TERRAIN),
         onMapLoaded = { isMapLoaded = true },
         onMapClick = { onMapClick() }
     ) { ... }
     ```
3. **Preserved Semantics**:
   - Polylines (`Polyline`) and Start, End, Apex markers (`Marker`) are fully supported in Lite Mode.
   - Auto-bounds zoom via `CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 20)` in `LaunchedEffect` executes seamlessly.
   - Tap callback (`onMapClick`) continues to forward the user to the interactive route/workout detail view.

### 3.2 `WorkoutClusterComponents.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`)
1. **Import `GoogleMapOptions`**:
   - Add `import com.google.android.gms.maps.GoogleMapOptions`.
2. **Lite Mode in `ClusterCard` (Route Cluster List Item Thumbnail - Line 273)**:
   - Configure `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
   - Disables vector pipeline and continuous render loops for the 100dp x 100dp cluster card thumbnails in `WorkoutClustersList`.
3. **Lite Mode in `ClusterWorkoutCard` (Cluster Workout Item Thumbnail - Line 485)**:
   - Configure `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
   - Disables vector pipeline for each workout thumbnail in `WorkoutClusterDetails`.
4. **Preserved Semantics**:
   - Polyline rendering of cluster centroid paths and individual workout routes.
   - Bounding box auto-centering with 40px padding.
   - Click handlers navigating to the cluster heatmap detail screen.

### 3.3 Isolation of Full-Screen Interactive Maps (Zero Side Effects)
* **[`ATrainingTrackerMap.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt)**: Retains full vector rendering (`liteMode = false`) with user panning, zooming, rotation, and live GPS tracking.
* **[`ManualClusterScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt)**: Retains full vector rendering (`liteMode = false`) for interactive spatial point selection.
* **[`LapEditBottomSheet.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt)**: Retains full vector rendering (`liteMode = false`) with gesture manipulation.

---

## 4. Invariants & Safety Verification

1. **Deadlock Immunity on Lifecycle Transitions**:
   - In Lite Mode, Google Maps renders static bitmap snapshots on background worker threads without registering native `FrameCompleteCallback` callbacks on the `RenderThread`.
   - When the activity pauses or stops, `ViewRootImpl.performDraw() -> HardwareRenderer.setStopped(true)` returns immediately without waiting on frame complete callbacks, eliminating the circular deadlock condition (`art::ConditionVariable::WaitHoldingLocks`).
2. **GPU & Native Resource Conservation**:
   - Eliminates allocation of dozens of native `GLSurfaceView` / `TextureView` surfaces and OpenGL contexts in `LazyColumn`.
   - Avoids memory pressure, texture churn, and frame dropping during list scrolling.
3. **Visual & Interaction Parity**:
   - Route path geometry, colors, custom start/end/apex icons, and spatial bounds clamping remain 100% pixel-accurate.
   - Click navigation to full interactive map views remains responsive and unimpaired.

---

## 5. Implementation Steps & Traceability Matrix

| Step | File | Action | Verification Trace |
|---|---|---|---|
| 1 | `PathPreviewMap.kt` | Supply `GoogleMapOptions().liteMode(true)` | TC-001 / `TST-STB-010` |
| 2 | `WorkoutClusterComponents.kt` | Supply `GoogleMapOptions().liteMode(true)` in `ClusterCard` & `ClusterWorkoutCard` | TC-002, TC-003 / `TST-STB-010` |
| 3 | `MapPreviewLiteModeTest.kt` | Create unit tests validating options, visual paths, and click delegation | TC-004, TC-005, TC-006 / `TST-STB-010` |
| 4 | Clean-Room Regression | Run `./gradlew testDebugUnitTest` across all modules | TC-008 / `TST-STB-010` |
| 5 | Physical Device Verification | Deploy to device `66020DLCR002FL`, scroll cluster & workout lists, background/foreground | TC-007 / `TST-STB-010` |
