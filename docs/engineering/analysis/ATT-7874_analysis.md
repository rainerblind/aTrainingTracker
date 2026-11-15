# Stage 1 Analysis: ATT-7874 - Fix OutOfMemoryError from leaked TuningPreferencesDataStore scopes and offscreen Cockpit maps

**Ticket**: [ATT-7874](https://rainerblind.atlassian.net/browse/ATT-7874)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-7874`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
During background tracking, the app suffered fatal 512 MB Java heap `OutOfMemoryError` crashes (native JNI `SIGABRT` in `SharedLabelerJni.nativeMeasureIconLayers` or OOM in `PolylineOptions`/`MapApplier.onClear`), preceded by GC stalls, 402 live threads, 1.25 GB RSS, and background `EXCESSIVE CPU USAGE` process kills.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Forensic investigation confirmed two memory and thread leak vectors:
1. **Unrestricted Scope & Context Allocation in `TuningPreferencesDataStore`**: The public constructor allocates a permanent, unmanaged `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and an unmanaged `CoroutineScope(Dispatchers.IO).launch` in `init`. 19 call sites directly instantiate `TuningPreferencesDataStore(context)`—passing Activity `LocalContext.current` via Compose `remember { ... }`—leaking Activity contexts and creating duplicate eager `StateFlow` worker threads.
2. **Offscreen Map Composition & Ungated Background Telemetry**: `TrackingTabsScreen` sets `beyondViewportPageCount = trackingViews.size + 1` in `HorizontalPager`, keeping all tabs and Google Maps composed. Concurrently, `TrackingViewModel.observeMapState()` collects 1-Hz `currentTrack`/`userSpeed`/`userBearing` in `viewModelScope` without lifecycle gating (`repeatOnLifecycle(STARTED)`), forcing continuous offscreen Polyline/marker allocations while the screen is off.
* **Independent Peer Review & Verification**:
  * Confirmed all 19 production call sites bypass `TuningPreferencesDataStore.getInstance(context)`.
  * Verified `SensorGridScreen` does not pass `isTabActive` to `TrackingMapContainer`, and `TrackingTabGridContent` collects `uiState` via `collectAsState()` without lifecycle awareness.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Restrict `TuningPreferencesDataStore` instantiation to `getInstance(context)` and force `context.applicationContext`.
  * Migrate all 19 production call sites to the application-scoped singleton.
  * Bound `HorizontalPager.beyondViewportPageCount` and defer `GoogleMap` composition on inactive tabs.
  * Gate `TrackingViewModel` map state / Compose collection on `Lifecycle.State.STARTED`.
* **Out-of-Scope Non-Goals**:
  * No refactoring of Map DSL or Google Maps SDK rendering pipeline.
  * No alteration of `TrackerService` 1-Hz background database recording.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Historical Origin**: `ATT-2945` (`commit f3ff8577`) set `beyondViewportPageCount = trackingViews.size + 1` to prevent tab swiping latency.
* **Preservation of Core Invariants**: Swiping responsiveness for sensor grids is preserved while decoupling heavy `GoogleMap` composition and gating 1-Hz map allocations when offscreen or screen-off. Net-new requirement `REQ-TRK-079`. No existing requirements modified.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new requirement `REQ-TRK-079` (`REQ-TRK-079.1`..`REQ-TRK-079.3`).
* **Affected Architecture Elements**: `TuningPreferencesDataStore.kt` (constructor visibility, app context, init scope), 19 UI/ViewModel call sites, `TrackingTabsScreen.kt`, `TrackingTabGridContent.kt`, `SensorGridScreen.kt`, `TrackingViewModel.kt`.
* **Verification Impact & Test Surface**: `TuningPreferencesDataStoreTest.kt`, `TrackingViewModelTest.kt`, new `TuningPreferencesDataStoreLifecycleTest.kt`.
* **Risk Assessment & Invariants**: Medium risk. Invariants: Background tracking in `TrackerService` 100% unaffected; active cockpit rendering intact; zero DataStore key/schema changes.

---

## 6. Architectural Strategy & High-Level Solution
1. In `TuningPreferencesDataStore`: Make constructor `@VisibleForTesting internal`, force `context.applicationContext`, and launch init purge inside `appScope`. Migrate all 19 call sites to `getInstance(context)`.
2. In `TrackingTabsScreen`: Bound `beyondViewportPageCount = 0` (or 1) and skip `TrackingMapContainer` rendering when `!isTabActive`.
3. In `TrackingTabGridContent` & `TrackingViewModel`: Use `collectAsStateWithLifecycle(STARTED)` and pause live map telemetry emissions when not in foreground.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in background GPS recording; settings persist immediately; zero leaks of Activity context.
* **Risk Rating**: **MEDIUM** (Cockpit tab composition and lifecycle state flow).
