# Stage 1 Analysis: ATT-3581 - Calibrate Follow-Me High-Speed Zoom with Logarithmic Scaling and Remove Camera Sliders from Advanced Settings

**Ticket**: [ATT-3581](https://rainerblind.atlassian.net/browse/ATT-3581)  
**Sub-task**: [ATT-3715](https://rainerblind.atlassian.net/browse/ATT-3715) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-3581`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

During active workout tracking and navigation in Cockpit (`SensorGridScreen.kt` / `ATrainingTrackerMap.kt`), the Follow-Me camera tracking mode dynamically adjusts zoom according to the athlete's speed (`calculateFollowMeTargetZoom` in `MapBehaviors.kt`).

However, the existing zoom calculation and configuration architecture suffers from two critical flaws:

1. **Exponential Viewport Blowout on Descents**:
   - In `MapBehaviors.kt:191–205`, `calculateFollowMeTargetZoom` applies a strictly linear formula across all speeds down to `minZoom = 12.0f`:
     ```kotlin
     val speedKmh = (speedMps * 3.6f).coerceAtLeast(0.0f)
     val zoomSlope = (baseZoom - cruisingZoom) / 20.0f // 0.1 zoom levels per km/h
     return (baseZoom - zoomSlope * speedKmh).coerceIn(minZoom, maxZoom)
     ```
   - Google Maps camera zoom levels are **logarithmic (base 2)**: reducing the zoom level by $1.0$ doubles the visible ground distance on screen ($2^{\Delta \text{zoom}}$).
   - In the low-speed range from $0\text{ km/h}$ to $20\text{ km/h}$, the linear formula drops from `baseZoom = 20.0f` to `cruisingZoom = 18.0f` ($\Delta \text{zoom} = 2.0$, a $4\times$ viewport increase), which corresponds to ~70–100 m ahead and feels completely natural during climbing and relaxed cruising.
   - However, during high-speed flat cycling and descents ($50\text{–}60\text{ km/h}$), continuing the linear slope drops the zoom level to **15.0 (at 50 km/h)** and **14.0 (at 60 km/h)**. Even though speed has only tripled compared to $20\text{ km/h}$, the $4$-level drop from $18.0$ to $14.0$ zooms out by **$2^4 = 16\times$** (~2.2 km ahead). At this scale, roads become hairline threads, upcoming hairpins and sharp turns vanish into the map background, and navigation cues lose their immediate situational context.

2. **Excessive UI Complexity in Advanced Settings**:
   - In `NavigationSection.kt:141–269`, the app exposes 7 manual sliders and toggles for Follow-Me camera tuning: `Initial Zoom`, `Speed-Dependent Zoom Enabled`, `Cruising Speed Zoom`, `Camera Tilt Angle`, `Forward Lookahead Bottom Padding`, `Speed Smoothing Alpha`, and `Bearing Smoothing Alpha`.
   - These sliders were originally introduced during early Compose migration as exploratory developer tools. Exposing them in user-facing Advanced Settings burdens athletes with technical parameters they cannot realistically tune, while custom values saved in DataStore override the mathematically calibrated camera behavior.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 The Mathematics of Google Maps Ground Resolution
The ground resolution $R$ (meters per pixel or display point) of the Google Maps Mercator projection at latitude $\phi$ and zoom level $z$ is:
$$R(z, \phi) = \frac{156543.03392 \times \cos(\phi)}{2^z}$$

Thus, visible distance $D$ on screen for a fixed viewport dimension is proportional to $2^{-z}$:
$$D(z) \propto 2^{-z}$$

To maintain a **constant lookahead time horizon** $T_{\text{lookahead}}$ (the visible distance ahead growing proportionally to velocity $v$), the visible distance $D$ must satisfy:
$$D(v) \propto v \implies 2^{-z(v)} \propto v \implies -z(v) \propto \log_2(v) \implies z(v) = z_0 - k \cdot \log_2(v)$$

Applying a linear zoom function $z(v) = z_0 - c \cdot v$ causes visible distance to grow **exponentially** with speed:
$$D(v) \propto 2^{c \cdot v}$$
At $60\text{ km/h}$, $D(v)$ explodes by $2^{0.1 \times 60} = 2^6 = 64\times$ relative to standstill, rather than $3\times$ as intended!

### 2.2 Desired Piecewise Zoom Profile
To preserve the proven, beloved low-speed feel while correcting the high-speed descent blowout:
1. **Low-Speed Region ($0 \le \text{speedKmh} \le 20.0\text{ km/h}$)**:
   - Retain linear interpolation between `baseZoom = 20.0f` (at $0\text{ km/h}$) and `cruisingZoom = 18.0f` (at $20\text{ km/h}$).
   - $10\text{ km/h} \to 19.0\text{f}$.
2. **High-Speed Region ($\text{speedKmh} > 20.0\text{ km/h}$)**:
   - Scale logarithmically with $\log_2(\text{speedKmh} / 20.0)$:
     $$\text{rawZoom} = \text{cruisingZoom} - \left(\frac{\text{baseZoom} - \text{cruisingZoom}}{2.0}\right) \cdot \frac{\ln(\text{speedKmh} / 20.0)}{\ln(2.0)}$$
   - With defaults (`baseZoom = 20.0f`, `cruisingZoom = 18.0f`):
     - $20\text{ km/h} \to 18.0\text{f}$ (continuous transition, $C^0$)
     - $30\text{ km/h} \to 17.42\text{f}$ ($1.5\times$ cruising distance)
     - $40\text{ km/h} \to 17.00\text{f}$ ($2.0\times$ cruising distance)
     - $50\text{ km/h} \to 16.68\text{f}$ ($2.5\times$ cruising distance)
     - $60\text{ km/h} \to 16.42\text{f}$ ($3.0\times$ cruising distance)
     - $\ge 80\text{ km/h} \to 16.00\text{f}$ (clamped at `minZoom = 16.0f`)
3. **Minimum Zoom Clamp (`minZoom`)**:
   - Update default `minZoom` from `12.0f` to `16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f`), ensuring the rider is never zoomed out past street-level navigation clarity even on $90\text{+ km/h}$ alpine descents.

---

## 3. Architecture & Target Scope

### 3.1 Component Boundaries
```
+--------------------------------------------------------------------------+
|                        Presentation / UI Layer                           |
|                                                                          |
|  +--------------------------+         +-------------------------------+  |
|  | ATrainingTrackerMap      | ------> | followMeController            |  |
|  |  (Cockpit / Replay)      |         |  - Uses canonical defaults    |  |
|  +--------------------------+         |  - Evaluates target zoom      |  |
|                                       +-------------------------------+  |
|                                                       |                  |
|  +--------------------------+                         v                  |
|  | NavigationSection        |         +-------------------------------+  |
|  |  - Route Selection Rad.  |         | calculateFollowMeTargetZoom   |  |
|  |  - Cue Dismiss Duration  |         |  - Linear (<= 20 km/h)        |  |
|  |  - Turn Countdown Dist.  |         |  - Logarithmic (> 20 km/h)    |  |
|  |  - Unique Route Snapping |         |  - Clamped at minZoom = 16.0f |  |
|  |  (Camera sliders REMOVED)|         +-------------------------------+  |
|  +--------------------------+                                            |
+--------------------------------------------------------------------------+
```

### 3.2 Target Code Modifications
1. `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`:
   - Add `const val DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f` to `TuningPreferencesDefaults`.
2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`:
   - Update `calculateFollowMeTargetZoom` default `minZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM`.
   - Implement piecewise linear-to-logarithmic formula.
3. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`:
   - Remove the `tuning_map_camera_section_title` divider/header and all 7 Follow-Me camera slider items and their callback parameters.
4. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`:
   - Remove local Follow-Me state variables and parameters passed into `NavigationSection`.
5. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt`:
   - Call `followMeController(...)` using canonical defaults from `TuningPreferencesDefaults`.

---

## 4. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

### 1. Original Requirement ID & Target
* Amends `REQ-MAP-042` (*Speed-Dependent Map Zoom Calibration*, Sprint 2026-41.6, `ATT-2946`) and `REQ-MAP-049` (*Follow-Me Camera Speed Zoom Smoothing...*, Sprint 2026-42.0, `ATT-3227`).
* Formalized as `REQ-MAP-051` (*Calibrated Piecewise Logarithmic Follow-Me High-Speed Zoom and Autonomous Camera Control*).

### 2. Historical Origin & Commit Trace
* `REQ-MAP-042` (Sprint 2026-41.6, commit `3c59a421`): Introduced the initial linear speed-dependent zoom formula and camera sliders in the Navigation tuning section.
* `REQ-MAP-048` (Sprint 2026-41.9, commit `d97e201b`): Replaced buggy map padding with `calculateFollowMeCameraTarget` along-bearing focal center projection and tilt pitch compensation.
* `REQ-MAP-049` (Sprint 2026-42.0, commit `c891ba32`): Introduced speed and bearing low-pass exponential smoothing filters and a $0.10f$ zoom hysteresis deadband.

### 3. Root Reason for Existing Formulation
* When `REQ-MAP-042` was authored, linear interpolation was chosen as an intuitive first approximation. However, the author did not account for the base-2 logarithmic nature of Google Maps tiles ($2^{-\text{zoom}}$), leading to the $16\times$ viewport blowout on descents.
* The 7 camera sliders were exposed in Advanced Settings as developer exploratory controls during early tuning. In production, athletes should not be burdened with tweaking obscure camera parameters, especially when manual tweaks override the physically calibrated curve.

### 4. Preservation of Core Invariants
* **Low-Speed Clarity**: The exact linear zoom curve between $0\text{ km/h}$ ($20.0f$) and $20\text{ km/h}$ ($18.0f$) is 100% preserved.
* **Camera Stabilization**: Exponential speed smoothing (`0.10f`), bearing smoothing (`0.15f`), and the $0.10f$ hysteresis deadband from `REQ-MAP-049` remain active.
* **Forward Lookahead & Tilt**: Forward lookahead projection (`30%`) and tilt angle (`70°`) from `REQ-MAP-048` remain active.
* **Remaining Navigation Settings**: Route Selection Radius, Navigation Cue Auto-Dismiss Duration, Turn Cue Countdown Distance, and Auto-Follow Unique Route remain configurable in Advanced Settings.
* **Clean-Room Test Pass Rate**: 100% test pass rate across all 2,964+ unit tests.

---

## 5. Scope Bounding (In-Scope vs. Out-of-Scope)

### In-Scope:
* Implementing piecewise linear ($\le 20\text{ km/h}$) to logarithmic ($> 20\text{ km/h}$) zoom curve in `calculateFollowMeTargetZoom`.
* Raising default `minZoom` to `16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM`).
* Removing the 7 Follow-Me camera tuning controls from `NavigationSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt`.
* Wiring `ATrainingTrackerMap.kt` to use `TuningPreferencesDefaults` for Follow-Me camera parameters directly.
* Updating unit and contract tests in `FollowMeCameraZoomTest.kt`, `FollowMeSmoothingTest.kt`, `AdvancedTuningVisualContractTest.kt`, `MapCameraTuningPreferencesTest.kt`.

### Out-of-Scope:
* Modifying non-Follow-Me camera modes (`FIT_ALL`, `EXPLICIT_BOUNDS`, `FREE_PAN`).
* Altering turn-by-turn guidance prompts, audio chimes, or HUD overlays.
* Modifying Route Selection Radius or Cue Dismiss Duration sliders.
* Removing DataStore keys from `TuningPreferencesDataStore.kt` (keys remain present so existing saved files or schema tests don't fail).

---

## 6. Verification & Test Strategy

1. **Unit Tests (`FollowMeCameraZoomTest.kt`)**:
   - Assert linear interpolation at $0\text{ km/h}$ ($20.0f$), $10\text{ km/h}$ ($19.0f$), $20\text{ km/h}$ ($18.0f$).
   - Assert logarithmic values at $30\text{ km/h}$ ($17.42f$), $40\text{ km/h}$ ($17.00f$), $50\text{ km/h}$ ($16.68f$), $60\text{ km/h}$ ($16.42f$).
   - Assert clamping at $16.0f$ for speeds $\ge 80\text{ km/h}$ (e.g. $90\text{ km/h}$, $120\text{ km/h}$).
   - Assert `speedZoomEnabled == false` locks to `baseZoom`.
2. **Contract Tests (`AdvancedTuningVisualContractTest.kt`, `FollowMePaddingContractTest.kt`)**:
   - Assert absence of Follow-Me camera slider items in `NavigationSection.kt`.
   - Assert `ATrainingTrackerMap.kt` compiles cleanly with default `followMeController` parameters.
3. **Full Clean-Room Suite**:
   - Run `./gradlew testDebugUnitTest` ensuring 100% pass rate.
