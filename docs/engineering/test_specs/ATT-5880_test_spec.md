# Stage 2: Requirement & Test Specification - ATT-5880: Map Camera Centering & Window Insets

**Ticket**: [ATT-5880](https://atrainingtracker.atlassian.net/browse/ATT-5880)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Karten & Routen (Maps & Routes)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5880`  
**Requirement Mapping**: `REQ-MAP-059` (*Initial Map Camera Centering, Fallback Cascade & Navigation Bar Insets*)  
**Test Spec ID**: `TST-MAP-061` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-MAP-059)

### 1.1 Problem Statement & Rationale
Opening `Karten -> Karte` or Cockpit tracking map defaults to world zoom or `(0.0, 0.0)` Null Island in the Gulf of Guinea when GPS fix is pending. Furthermore, `GoogleMap` in `MapScreenWithTrack` uses zero bottom padding, clipping the Google attribution logo behind edge-to-edge system navigation bars, and lacks top-left navigation context.

### 1.2 Functional & Architectural Requirements
1. **Defensive Fallback Cascade (`REQ-MAP-059.1`)**: `MapInitialLocationResolver` SHALL evaluate coordinates via: (1) Live GPS fix / `LocationManager.getLastKnownLocation` with permission guards, (2) Home Base known location, (3) Most visited known location ($\operatorname{argmax}(\text{hitCount})$), (4) Recent workout start, (5) Default fallback.
2. **Initial Camera Centering & Deferred Fix Alignment (`REQ-MAP-059.2`)**: `ATrainingTrackerMap` and `TrackingMapContainer` SHALL initialize `CameraPositionState` to resolved coordinates at zoom 14.0f. `MapBoundsController` SHALL include `currentLocation` in `LaunchedEffect` keys to align the camera upon receiving the first live fix.
3. **Navigation Bar Inset Forwarding (`REQ-MAP-059.3`)**: `ATrainingTrackerMap` SHALL accept and forward `contentPadding: PaddingValues` to `GoogleMap`. In `MapScreenWithTrack`, it SHALL pass `WindowInsets.navigationBars.asPaddingValues()` to lift the Google logo above the system bar.
4. **Top-Start Navigation Button (`REQ-MAP-059.4`)**: In `MapScreenWithTrack`, a 44dp circular Surface button (`testTag("map_navigation_button")`) SHALL be anchored at `Alignment.TopStart.statusBarsPadding()`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Initial Camera Centering)**:
  * *Given* `Karten -> Karte` or Cockpit map opened with GPS fix pending,
  * *When* camera initializes,
  * *Then* position centers on fallback location at zoom 14.0f (never `(0.0, 0.0)` Null Island).
* **Criterion 2 (Deferred Fix Camera Update)**:
  * *Given* `Karten -> Karte` open with no active track or route,
  * *When* first live GPS fix arrives,
  * *Then* `MapBoundsController` animates camera to athlete's current location.
* **Criterion 3 (Navigation Bar Insets)**:
  * *Given* `MapScreenWithTrack` rendered in edge-to-edge mode,
  * *When* viewed with a 3-button or gesture navigation bar,
  * *Then* Google attribution logo is positioned cleanly above the navigation bar via bottom insets.
* **Criterion 4 (Top-Start Navigation Button)**:
  * *Given* `MapScreenWithTrack` rendered on `Karten -> Karte`,
  * *When* displayed,
  * *Then* a 44dp circular Surface navigation button is anchored at the top-left with status bar padding.

### 1.4 System Invariants
* Rule 33 single spatial authority in `followMeController` preserved with zero dynamic lookahead padding oscillation loops.
* Zero reflection violations in `TrackingMapIsolationContractTest`.

---

## 2. Test Specification (TST-MAP-061)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-061.1]` | Unit | `MapInitialLocationResolverTest`: Cascade evaluation & permission guards | `REQ-MAP-059.1` | Specified |
| `[TST-MAP-061.2]` | Unit | `ATrainingTrackerMapTest`: Initial camera position at zoom 14f | `REQ-MAP-059.2` | Specified |
| `[TST-MAP-061.3]` | Unit | `MapBehaviorsTest`: Deferred live fix camera update on empty bounds | `REQ-MAP-059.2` | Specified |
| `[TST-MAP-061.4]` | Contract | `MapScreenWithTrackTest`: Bottom insets and top-start button contract | `REQ-MAP-059.3`, `REQ-MAP-059.4` | Specified |
| `[TST-MAP-061.5]` | Contract | `TrackingMapIsolationContractTest`: Rule 33 isolation & zero reflection | `REQ-MAP-059.3` | Specified |
| `[TST-MAP-061.6]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"` | `REQ-MAP-059.1`..`4` | Specified |
