# Stage 1 Analysis: ATT-5880 - Initial Map Camera Centering & Window Insets for Global & Cockpit Maps

**Ticket**: [ATT-5880](https://atrainingtracker.atlassian.net/browse/ATT-5880)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Karten & Routen (Maps & Routes)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5880`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Unpositioned Initial Camera (Hemisphere & Null Island)**:
  * Opening `Karten -> Karte` (`MapScreenWithTrack.kt`, `round4_09_karte.png`) defaults to the Western Hemisphere at world zoom (~2).
  * Opening the cockpit `Karte` tracking tab (`TrackingMapContainer`, `round4_11_start_karte.png`) defaults to `(0.0, 0.0)` Null Island in the Gulf of Guinea off West Africa when no live GPS fix is yet acquired.
* **Clipped Google Attribution Logo**: In `MapScreenWithTrack.kt`, `GoogleMap` receives zero bottom padding (`contentPadding = PaddingValues(top = 0.dp)`), causing the bottom-left Google logo to be clipped behind the Android system navigation bar (`round4_09_karte.png`).
* **Missing Top-Left Navigation Context**: On `Karten -> Karte`, only the layers FAB is anchored at top-right; no drawer menu or back navigation button is provided on top-left.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  1. *Unpositioned Camera*: `rememberCameraPositionState()` defaults to `LatLng(0.0, 0.0)` at zoom 0. When `initialBounds` is null and `currentLocation` is null, `MapBoundsController` finds no coordinates and skips updating; in Cockpit tracking, `followMeController` executes only when `currentLocation != null`.
  2. *Missing Navigation Bar Insets*: `ATrainingTrackerMap` hardcodes `topPadding = 0.dp` and lacks a bottom inset parameter to protect against edge-to-edge system navigation bars.
  3. *Missing Top Navigation Context*: `MapScreenWithTrack.kt` lacks a top-start Surface navigation button (`onMenuClick` / `onNavigateBack`).
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `ATrainingTrackerMap.kt:206-222`: Forwarding a configurable `contentPadding: PaddingValues` with bottom navigation bar insets lifts the Google logo without violating Rule 33 (fixed system insets, zero dynamic lookahead padding oscillation loops).
  * Inspected `MapBehaviors.kt:64-197`: Adding `currentLocation` to `MapBoundsController`'s `LaunchedEffect` keys enables deferred camera alignment once a live fix arrives.
  * Verified fallback cascade (`Live GPS fix` $\rightarrow$ `LocationManager.getLastKnownLocation` $\rightarrow$ `Home Base Known Location` $\rightarrow$ `Most Visited Known Location` $\rightarrow$ `Most Recent Workout Start Location`): Safely provides valid local coordinates without Null Island.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Implement `MapInitialLocationResolver` with defensive fallback cascade and permission safety.
  * Update `ATrainingTrackerMap` to initialize `CameraPositionState` with `resolvedInitialLocation` (zoom 14f) and forward `contentPadding` to `GoogleMap`.
  * Pass `WindowInsets.navigationBars.asPaddingValues()` to `ATrainingTrackerMap` in `MapScreenWithTrack.kt`.
  * Add top-start 44dp circular Surface navigation button (`Menu` / `Back`) to `MapScreenWithTrack.kt`.
  * Add `currentLocation` to `MapBoundsController` LaunchedEffect keys.
* **Out-of-Scope**: Altering Follow-Me dynamic speed zoom curves (`REQ-MAP-050`), modifying polyline rendering (`REQ-MAP-051`), or altering database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new refinement `REQ-MAP-059` (*Initial Map Camera Centering, Fallback Cascade & Navigation Bar Insets*), refining `REQ-MAP-006`, `REQ-MAP-046`, and `REQ-UI-326`.
* **Historical Origin & Commit Trace**: Sprint 2026-41.6 (`ATT-3102`, `d7b00bf8` / `c6f65b71`): Rule 33 established single spatial authority in `followMeController`, zeroing dynamic content padding. Applying static window insets (`WindowInsets.navigationBars`) fully respects Rule 33.
* **Root Reason for Existing Formulation**: `rememberCameraPositionState()` default was used without fallback coordinate resolution.
* **Preservation of Core Invariants**: Rule 33 analytical lookahead preserved; `@Immutable TrackingMapState` contract tests (`TrackingMapIsolationContractTest`) preserved; zero regression to 1-Hz sensor performance.

---

## 5. Architectural Strategy & High-Level Solution
* **Fallback Resolver Utility**: Create `MapInitialLocationResolver.resolveInitialLocation(context: Context): LatLng?` querying system `LocationManager`, `KnownLocationsRepository`, and `WorkoutSummariesDatabaseManager`.
* **Camera Initialization**: In `ATrainingTrackerMap`, set `position = CameraPosition.fromLatLngZoom(resolvedInitialLocation, 14f)` upon creation.
* **Insets & Navigation**: Add `contentPadding: PaddingValues = PaddingValues()` to `ATrainingTrackerMap` and pass `WindowInsets.navigationBars.asPaddingValues()` in `MapScreenWithTrack.kt`; add top-start floating circular navigation button.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% clean-room test pass rate; zero reflection violations in `TrackingMapIsolationContractTest`; Rule 33 single spatial authority preserved.
* **Risk Rating**: **LOW** — Additive parameters, defensive try-catch guards in location resolver, and static window insets without layout oscillation loops.
