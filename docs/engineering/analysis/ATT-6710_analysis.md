# Stage 1 Analysis: ATT-6710 - Live sparkline buffers become empty when backgrounding app or recreating TrackingViewModel

**Ticket**: [ATT-6710](https://rainerblind.atlassian.net/browse/ATT-6710)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6710`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Live sparkline trend graphs (`SensorSparklineView` / `LiveSparklineBuffer`, introduced in `ATT-1294`) in the tracking cockpit reset to empty whenever the app is backgrounded via the system Back gesture/button, when navigating across drawer destinations, or when the `TrackingViewModel` is recreated. Three compounding defects cause this behavior:
1. `ATrainingTrackerApp.kt:260` root `BackHandler` calls `activity.finish()`, destroying `MainActivityWithNavigation` and clearing the UI-scoped `ViewModelStore` instead of moving the task to back.
2. `LiveSparklineBuffer` is instantiated and held exclusively within `TrackingViewModel.sparklineBuffers` (`mutableMapOf<Long, LiveSparklineBuffer>()`), lacking singleton lifecycle persistence in `BANALServiceRepository`.
3. In `TrackingViewModel.applySensorData()`, `updatedFields` starts from `cachedBaseFields` (where `sparklineSamples = emptyList()`). Whenever `allSensorData` is empty or a sensor does not emit in a given tick, the field falls back to `cachedBaseFields`, overwriting `sparklineSamples` with `emptyList()`.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Causes**:
  1. *Lifecycle Destruction*: Root `BackHandler` in `ATrainingTrackerApp.kt:247-262` executes `activity.finish()` when `currentRoute == NavRoutes.START_TRACKING` and `previousBackStackEntry == null`. The Activity lifecycle tears down, terminating `TrackingViewModel` via `onCleared()`.
  2. *Lack of Singleton Accumulation*: Unlike GPS breadcrumbs (`_currentTrack`) and elevation points (`_currentPathPoints`) that persist in `BANALServiceRepository`, sparklines only accumulate samples when `TrackingViewModel` is active and observing.
  3. *Cached State Overwrite*: In `TrackingViewModel.kt:514-598`, `cachedBaseFields` is built on initial load with `sparklineSamples = emptyList()`. `applySensorData()` only updates fields matching active `allSensorData` items; non-emitting sensors retain `cachedBaseFields`'s `emptyList()`.
* **Independent Peer Review & Verification**:
  - *Call-Site Audit*: `ATrainingTrackerApp.kt:260` is the single root exit point. Calling `activity.moveTaskToBack(true)` preserves the Activity and its ViewModelStore in the background task stack without termination.
  - *Repository Accumulation*: `BANALServiceRepository.startObservingBANALService()` runs a 1 Hz pulse loop. Centralizing `LiveSparklineBuffer` registry inside `BANALServiceRepository` allows continuous 1 Hz sample accumulation across drawer navigation and backgrounding, clearing only when `clearBreadcrumbs()` executes on workout start/reset.
  - *State Update Guard*: In `applySensorData()`, mapping every `showGraph == true` field from its repository-backed buffer guarantees non-emitting ticks never erase accumulated samples.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  - Replace `activity.finish()` with `activity.moveTaskToBack(true)` in `ATrainingTrackerApp.kt`.
  - Introduce thread-safe `LiveSparklineBuffer` registry and 1 Hz sample accumulation in `BANALServiceRepository`, clearing on `clearBreadcrumbs()`.
  - Delegate sparkline buffer retrieval and sample population in `TrackingViewModel` to `BANALServiceRepository`, eliminating the `cachedBaseFields` `emptyList()` fallback.
  - Unit/contract tests verifying task preservation on root back, buffer lifecycle persistence in `BANALServiceRepository`, and non-empty sample retention on empty/partial sensor ticks.
* **Out-of-Scope Non-Goals**: Modifying `LiveSparklineBuffer` decimation math, altering `SensorSparklineView` canvas rendering, or modifying database schemas.
* **Complexity Estimation**: 2 SP (Standard bugfix across navigation, repository, and ViewModel; no schema changes).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-TRK-059.4` (*Live Sparkline Buffer Engine & Bounded Memory*) and `REQ-UI-159` (*Single-Activity Navigation Hierarchy*).
* **Historical Origin & Commit Trace**: `ATT-1294` (Sparkline graphs), `ATT-1083` (Single-Activity Navigation), `ATT-4841` (Sensor field display elements).
* **Root Reason for Existing Formulation**: Root `BackHandler` previously called `finish()` as a straightforward default for the root screen. `sparklineBuffers` was placed in `TrackingViewModel` during initial prototyping for isolated tab rendering without considering Activity recreation or backgrounding.
* **Preservation of Core Invariants**: 100% preservation of `LiveSparklineBuffer` bounded memory ($\le 300$ samples / $\le 1$ KB), `SensorSparklineView` Canvas rendering, and drawer navigation hierarchy.

---

## 5. Architectural Strategy & High-Level Solution
* **Navigation**: In `ATrainingTrackerApp.kt:260`, invoke `activity.moveTaskToBack(true)`.
* **Repository**: In `BANALServiceRepository.kt`, manage `sparklineBuffers: ConcurrentHashMap<Long, SparklineBufferRegistration>`. Update active buffers on each 1 Hz tick in `startObservingBANALService()`, clear them in `clearBreadcrumbs()`, and expose `getOrCreateSparklineBuffer()` and `getSparklineSamples()`.
* **ViewModel**: In `TrackingViewModel.kt`, replace local `sparklineBuffers` with `banalServiceRepository` registry. In `applySensorData()`, unconditionally populate `sparklineSamples` from the repository buffer for all `showGraph == true` fields.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero memory leaks; bounded sparkline memory ($\le 1$ KB/tile); zero recomposition churn; existing back-navigation behavior preserved for child destinations and drawer states.
* **Risk Rating**: **LOW** (Targeted lifecycle and state-holding refactor; zero database or binary protocol changes).
