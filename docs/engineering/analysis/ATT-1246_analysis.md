# Engineering Analysis - ATT-1246

**Ticket**: `ATT-1246`: `[Bug] art::ConditionVariable::WaitHoldingLocks(art::Thread*)`  
**Parent / Epic**: `ATT-235`: `No crashs`  
**Sub-task**: `ATT-1271`: `[Analysis] art::ConditionVariable::WaitHoldingLocks(art::Thread*)`  
**Component**: UI Map Layer / Compose Graphics Pipeline (`PathPreviewMap.kt`, `WorkoutClusterComponents.kt`, `maps-compose`)  
**Sprint**: `2026-39.2`  
**Target Release**: `V4.9.38`  
**Requirement Mapping**: `REQ-STB-010` (`docs/requirements.md`)  
**Test Specification**: `TST-STB-010` (`docs/tests.md`)  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2)`

---

## 1. Problem Statement & User Impact

### 1.1 Production ANR Report
Firebase Crashlytics reported a fatal native Application Not Responding (ANR) session on production release `4.9.36 (260)` (Issue `1b9b9a1021c548bb3c8e6b665f963fe1`, Session `6AAEDA02007B00020E63172E3D797304_DNE_0_v2`, Date: `Sat Sep 19 2026 20:52:57 GMT+0200`):

```text
main (native):tid=1 systid=3683
#00 pc 0x4c25c libc.so (syscall + 28)
#01 pc 0x51068 libc.so (__futex_wait_ex(void volatile*, bool, int, bool, timespec const*) + 144)
#02 pc 0xb126c libc.so (pthread_cond_wait + 80)
#03 pc 0x564e0 libc++.so (std::__1::condition_variable::wait(std::__1::unique_lock<std::__1::mutex>&) + 20)
#04 pc 0x58efc libc++.so (std::__1::__assoc_sub_state::copy() + 84)
#05 pc 0x591cc libc++.so (std::__1::future<void>::get() + 24)
#06 pc 0x476694 libhwui.so (android::uirenderer::renderthread::RenderProxy::setStopped(bool) + 224)
       at android.graphics.HardwareRenderer.nSetStopped(Native method)
       at android.graphics.HardwareRenderer.setStopped(HardwareRenderer.java:498)
       at android.view.ViewRootImpl.performDraw(ViewRootImpl.java:4279)
       at android.view.ViewRootImpl.performTraversals(ViewRootImpl.java:3374)
       at android.view.ViewRootImpl.doTraversal(ViewRootImpl.java:2179)
       at android.view.ViewRootImpl$TraversalRunnable.run(ViewRootImpl.java:8787)
       at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1037)
       at android.view.Choreographer.doCallbacks(Choreographer.java:845)
       at android.view.Choreographer.doFrame(Choreographer.java:780)
       at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1022)
       at android.os.Handler.handleCallback(Handler.java:938)
       at android.os.Handler.dispatchMessage(Handler.java:99)
       at android.os.Looper.loopOnce(Looper.java:201)
       at android.os.Looper.loop(Looper.java:288)
       at android.app.ActivityThread.main(ActivityThread.java:7870)

RenderThread (native):tid=2 systid=3835
#00 pc 0x4c25c libc.so (syscall + 28)
#01 pc 0x28bba8 libart.so (art::ConditionVariable::WaitHoldingLocks(art::Thread*) + 148)
#02 pc 0x474a84 libart.so (art::JNI<false>::CallVoidMethodV(_JNIEnv*, _jobject*, _jmethodID*, std::__va_list) + 496)
#03 pc 0x43135c libhwui.so (_JNIEnv::CallVoidMethod(_jobject*, _jmethodID*, ...) + 120)
#04 pc 0x2097b8 libhwui.so (std::__1::__function::__func<android::android_view_ThreadedRenderer_setFrameCompleteCallback(_JNIEnv*, _jobject*, long, _jobject*)::$_5, std::__1::allocator<android::android_view_ThreadedRenderer_setFrameCompleteCallback(_JNIEnv*, _jobject*, long, _jobject*)::$_5>, void ()>::operator()() + 100)
#05 pc 0x38802c libhwui.so (android::uirenderer::renderthread::DrawFrameTask::run() + 1144)
#06 pc 0x3b3ff4 libhwui.so (android::uirenderer::WorkQueue::process() + 156)
#07 pc 0x3b3d5c libhwui.so (android::uirenderer::renderthread::RenderThread::threadLoop() + 84)
#08 pc 0x12094 libutils.so (android::Thread::_threadLoop(void*) + 260)
#09 pc 0x11964 libutils.so (thread_data_t::trampoline(thread_data_t const*) + 404)
#10 pc 0xb1ff0 libc.so (__pthread_start(void*) + 264)
#11 pc 0x51ad8 libc.so (__start_thread + 64)
```

### 1.2 User Impact
When users scroll through lists containing embedded route or workout map previews (such as the Route Clusters list, Workout Summaries list, or Routes list) and subsequently navigate back, switch tabs, or background the application, the entire UI thread freezes permanently. The OS watchdog detects the frozen main thread after 5 seconds and abruptly terminates the application process with a fatal ANR dialogue.

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 The Two-Thread Circular Deadlock Anatomy
The ANR trace captures a classic, unrecoverable circular dependency deadlock between the Android **Main (UI) Thread** and the native **RenderThread**:

1. **Main Thread Lock Holding & Synchronous Wait**:
   - The main thread is executing `ViewRootImpl.performDraw()`.
   - As an activity stops or pauses (e.g. during navigation away or backgrounding), `ViewRootImpl` invokes `HardwareRenderer.setStopped(true)`.
   - `HardwareRenderer.setStopped()` invokes native method `nSetStopped(mNativeProxy, stopped)`.
   - In AOSP framework graphics, `nSetStopped` is declared with the ART `@FastNative` optimization annotation (`dalvik.annotation.optimization.FastNative`). Because of `@FastNative`, the calling thread **does not transition to `kNative`**; it remains in the `kRunnable` state, holding the ART `mutator_lock_` in Shared mode.
   - Inside `RenderProxy::setStopped(bool)`, the native renderer posts a task to `RenderThread` and calls `std::__1::future<void>::get()`, putting the main thread to sleep on `pthread_cond_wait`.
   - **Crucial Invariant**: The main thread is waiting on `RenderThread` while retaining its managed thread lock state.

2. **RenderThread JNI State Transition Block**:
   - Concurrently, `RenderThread` is executing `DrawFrameTask::run()` to process an active frame draw.
   - At the completion of the frame, `RenderThread` executes the registered `FrameCompleteCallback` via JNI: `android_view_ThreadedRenderer_setFrameCompleteCallback` -> `_JNIEnv::CallVoidMethod()`.
   - In ART's JNI implementation (`runtime/jni_internal.cc`), `CallVoidMethodV` instantiates `ScopedObjectAccess soa(env)` to transition the thread from native state (`kNative`) to managed state (`kRunnable`) so it can safely call into the Java callback.
   - Transitioning to `kRunnable` requires acquiring ART's `Locks::mutator_lock_` (`SharedLock`).
   - Because `nSetStopped` on the main thread is blocking inside `@FastNative` without releasing its thread lock state, and/or ART GC/thread state transitions require synchronization, `RenderThread` cannot acquire the mutator lock.
   - Consequently, `RenderThread` blocks on `art::ConditionVariable::WaitHoldingLocks(art::Thread*)`.

3. **The Deadlock Condition**:
   - **Main Thread** is blocked in `std::future::get()`, waiting for `RenderThread` to complete `RenderProxy::setStopped`.
   - **RenderThread** is blocked in `art::ConditionVariable::WaitHoldingLocks`, waiting for `mutator_lock_` to execute the JNI callback.
   - Neither thread can ever advance. After 5 seconds, the OS watchdog fires `SIGQUIT`, generating the ANR report.

### 2.2 Triggering Application Root Cause: Heavyweight Maps in Compose Lazy Lists
Why was `RenderThread` actively running `DrawFrameTask` and firing frame complete callbacks while `setStopped(true)` was executing?

1. **Proliferation of Full-Fidelity `GoogleMap` Instances in `LazyColumn`**:
   - In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`:
     - Line 273: `ClusterCard` (Route Cluster list item) creates a full `GoogleMap(modifier = Modifier.fillMaxSize(), ...)` for each 100dp x 100dp thumbnail.
     - Line 485: `ClusterWorkoutCard` (Cluster Workout list item) creates another full `GoogleMap` for each item.
   - In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`:
     - Line 58: `PathPreviewMap` creates a full `GoogleMap` for each row in `WorkoutSummary.kt` (Workout list) and `RouteItem.kt` (Routes list).
   - In all these locations, map gestures and controls were explicitly disabled (`scrollGesturesEnabled = false`, `zoomGesturesEnabled = false`, `zoomControlsEnabled = false`), indicating their sole purpose was a **static preview**.
2. **Absence of Lite Mode (`liteMode = false`)**:
   - None of these composables specified `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
   - Consequently, Google Maps SDK allocated a full-fidelity interactive vector rendering engine (`GLSurfaceView`/`TextureView`, native C++ rendering pipeline, offscreen render targets, and continuous HWUI frame synchronization loops) for **every single item in the list**.
3. **Graphics Resource Exhaustion & Frame Callback Flooding**:
   - As the user scrolls a list of clusters or workouts, dozens of full vector maps are instantiated and destroyed.
   - When the user navigates away or stops the activity, `ViewRootImpl` attempts to stop the renderer while the GPU and `RenderThread` are flooded with lingering frame draw tasks from all the un-recycled map views.
   - This massive concurrency window directly triggers the circular deadlock between `setStopped` and `setFrameCompleteCallback`.

---

## 3. Call Site & Component Audit

### 3.1 Inventory of `GoogleMap` Usages Across the Codebase

| File | Context | Gestures | Role | Recommended Mode |
|---|---|---|---|---|
| [`PathPreviewMap.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt#L58) | List rows in `WorkoutSummary.kt` & `RouteItem.kt` | All disabled | Static thumbnail preview | **`liteMode = true`** |
| [`WorkoutClusterComponents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt#L273) | `ClusterCard` in `WorkoutClustersList` | All disabled | 100dp thumbnail preview | **`liteMode = true`** |
| [`WorkoutClusterComponents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt#L485) | `ClusterWorkoutCard` in `WorkoutClusterDetails` | All disabled | 100dp thumbnail preview | **`liteMode = true`** |
| [`ATrainingTrackerMap.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt#L162) | Live workout recording & aftermath full view | Enabled | Interactive full-screen map | Full Mode (`liteMode = false`) |
| [`ManualClusterScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt#L214) | Interactive cluster creation point picker | Enabled | Interactive full-screen map | Full Mode (`liteMode = false`) |
| [`LapEditBottomSheet.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt#L563) | Lap boundary inspection & editing | Enabled | Interactive bottom sheet | Full Mode (`liteMode = false`) |
| [`PeriodSummaryCard.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt#L573) | Period multi-workout overview with `TileOverlay` heatmap | Gestures disabled | Heatmap layer required | Full Mode (Lite Mode does not support `TileOverlay`) |
| [`ImportBackupTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt#L610) | Single dialog preview during migration | Gestures disabled | Standalone dialog | Can be Lite Mode |

---

## 4. Architectural Remediation Strategy

### 4.1 Enforcing Google Maps Lite Mode for List Previews
To permanently eliminate the conditions causing the `HardwareRenderer.setStopped` / `setFrameCompleteCallback` deadlock:

1. **Configure `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`**:
   - In [`PathPreviewMap.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt):
     ```kotlin
     GoogleMap(
         modifier = modifier,
         cameraPositionState = cameraPositionState,
         googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
         uiSettings = MapUiSettings(
             zoomControlsEnabled = false,
             compassEnabled = false,
             mapToolbarEnabled = false,
             myLocationButtonEnabled = false,
             scrollGesturesEnabled = false,
             zoomGesturesEnabled = false
         ),
         properties = MapProperties(mapType = MapType.TERRAIN),
         onMapLoaded = { isMapLoaded = true },
         onMapClick = { onMapClick() }
     ) { ... }
     ```
   - In [`WorkoutClusterComponents.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt) (`ClusterCard` line 273 & `ClusterWorkoutCard` line 485):
     ```kotlin
     GoogleMap(
         modifier = Modifier.fillMaxSize(),
         cameraPositionState = cameraPositionState,
         googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
         properties = MapProperties(mapType = MapType.TERRAIN),
         onMapLoaded = { isMapLoaded = true },
         uiSettings = MapUiSettings(...),
         onMapClick = { onClick() }
     ) { ... }
     ```

### 4.2 Technical & Architectural Invariants of Lite Mode
* **Elimination of Native Render Proxy Overhead**: In Lite Mode, Google Maps renders a static bitmap image on a background thread. It completely dispenses with native `GLSurfaceView` / `TextureView` allocations, continuous HWUI render thread loops, and frame complete callback hooks.
* **Elimination of Deadlock Vulnerability**: Because no `FrameCompleteCallback` is registered with HWUI, `RenderThread` never calls `_JNIEnv::CallVoidMethod` for frame completions. When the activity stops and `nSetStopped` runs on the main thread, the circular lock dependency cannot occur.
* **Preservation of Visual Assets**:
  - Polyline paths (`Polyline`) are 100% supported in Lite Mode.
  - Custom Start, End, and Apex markers (`Marker`) are 100% supported in Lite Mode.
  - Spatial auto-centering via `CameraUpdateFactory.newLatLngBounds(bounds, padding)` is 100% supported in Lite Mode.
  - On-click navigation to full-screen interactive views (`onMapClick`) is preserved.
* **Massive Performance & Battery Gains**:
  - Scrolling through dozens of route clusters or historical workouts in `LazyColumn` becomes completely fluid, dropping GPU memory consumption by hundreds of megabytes.

---

## 5. Requirement Governance & Traceability

### 5.1 Requirement Registration (`REQ-STB-010`)
* **ID**: `REQ-STB-010`
* **Title**: Static Map Lite-Mode Enforcement in Compose Lazy Lists & RenderThread Deadlock Immunity
* **Target Version**: `V4.9.38`
* **Test Case**: `TST-STB-010`

### 5.2 Verification Plan Preview (`TST-STB-010`)
1. **Automated Unit Verification**:
   - Verify `PathPreviewMap` and `WorkoutClusterComponents` configure `GoogleMapOptions().liteMode(true)`.
   - Verify all markers (start, end, apex) and polyline paths render without exception in Lite Mode.
   - Verify click listeners continue to trigger navigation callbacks.
2. **Full-Suite Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.
3. **Physical Device Stress Verification**:
   - Deploy debug build to physical device `66020DLCR002FL`.
   - Rapidly scroll through route cluster list and workout list containing embedded preview maps.
   - Background and foreground the app repeatedly during scroll; verify 0 ANRs, 0 native deadlocks, and clean Logcat telemetry.
