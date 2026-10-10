# Stage 2: Requirement & Test Specification: ATT-3102

## Metadata
* **Ticket ID**: ATT-3102
* **Title**: Map camera continuously alternates between center and bottom lookahead position during tracking
* **Parent Epic**: ATT-2564 (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-MAP-046`
* **Test Specification ID**: `TST-MAP-048`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-MAP-046)

### 1.1 Summary
The system SHALL unify camera positioning, forward lookahead offset calculation, and viewport padding under a single spatial authority in `followMeController` (`MapBehaviors.kt`), eliminating competing Google Maps layout recentering loops and camera oscillation jitter during active workout tracking (ATT-3102, amending `REQ-MAP-044` and `REQ-MAP-042` under Rule 33):

1. **Unified Viewport Anchor Authority (`MapBehaviors.kt`)**:
   - The map camera controller (`followMeController`) SHALL be the single spatial authority governing camera target, bearing smoothing, speed-dependent zoom, and 3D pitch tilt in `MapZoomFocus.FOLLOW_ME` mode.
   - In `followMeController`, forward lookahead SHALL be computed analytically by projecting the camera focal target coordinate ahead of the athlete along `filteredBearing` based on `tuningConfig.mapFollowMeLookaheadPaddingPercent`, viewport height, and map zoom level.
   - Specifically:
     $$\text{offsetFraction} = \left(\frac{\text{lookaheadPaddingPercent}}{100\text{f}}\right) \times 0.5\text{f}$$
     $$\text{metersPerDp} = \frac{156543.03392 \times \cos(\text{latRad})}{2^{\text{targetZoom}}}$$
     $$\text{offsetMeters} = \text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}$$
     $$\mathbf{x}_{\text{target}} = \text{SphericalUtil.computeOffset}(\text{currentLocation}, \text{offsetMeters}, \text{filteredBearing})$$
2. **Decoupling of GoogleMap Content Padding (`ATrainingTrackerMap.kt`)**:
   - In `ATrainingTrackerMap.kt`, during `MapZoomFocus.FOLLOW_ME` mode, `GoogleMap` SHALL receive `contentPadding = PaddingValues(0.dp)`, completely eliminating competing layout recentering loops between Google Maps native padding and `cameraPositionState.animate(...)`.
   - `BoxWithConstraints` SHALL pass `maxHeight.value` as `viewportHeightDp` and `tuningConfig.mapFollowMeLookaheadPaddingPercent` as `lookaheadPaddingPercent` to `followMeController`.
3. **Smooth Camera Animation & Zero-Jitter Invariant**:
   - When a new GPS coordinate arrives, `followMeController` SHALL animate the camera to `CameraPosition.builder().target(target).bearing(filteredBearing).zoom(targetZoom).tilt(effectiveTilt).build()`.
   - The camera SHALL stably anchor the athlete marker at the lower third (~35% from bottom / ~65% from top) without oscillating or jumping to the physical screen center (50% Y).
4. **Preservation of Non-Follow-Me Modes & System Invariants**:
   - In non-Follow-Me modes (`EXPLICIT_BOUNDS`, `TRACK_AND_MARKERS`, `LOCAL_SEGMENTS`, `FIT_PRIMARY`), `followMeController` SHALL remain inactive and `contentPadding` SHALL remain `0.dp`.
   - Speed zoom calibration ($0\text{ km/h} \to 20.0$, $20\text{ km/h} \to 18.0$), bearing smoothing ($\alpha = 0.15$), pitch angle ($0^\circ - 70^\circ$), DataStore preference persistence, and 100% full clean-room unit test pass rate MUST be strictly preserved.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: ATT-2946 (commit `d8177e06`) and ATT-3054 (commit `8cdc39f0`) introduced and modified `contentPadding` on `GoogleMap` to shift the camera viewport center.
* **Root Reason for Existing Formulation**: ATT-3054 changed bottom padding to top padding on `GoogleMap` under the assumption that Google Maps internal viewport shifting would seamlessly adjust the camera. However, `CameraPosition.builder().target(currentLocation)` in `followMeController` always centers the camera target at the unpadded physical screen center (50% Y), while Google Maps native padding shifts it downward (65% Y). This caused a continuous 1 Hz oscillation between center and lower third on every GPS update.
* **Preservation of Intent**: The athlete remains positioned in the lower third with the road ahead maximized, but does so without conflicting layout adjustments or camera jitter, satisfying Rule 33.

---

## 2. Test Specification (TST-MAP-048)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-MAP-048.1** | Mathematical Unit Tests | `FollowMeCameraTargetTest.kt` | Verify `calculateFollowMeCameraTarget` projects target coordinate ahead of athlete along `bearing`.<br>• Verify zero padding or zero height returns `currentLocation` unchanged.<br>• Verify scaling inversely with zoom level ($2^Z$) and cosine of latitude.<br>• Verify cardinal directions (0°, 90°, 180°, 270°) shift target correctly. |
| **TST-MAP-048.2** | Structural Contract Tests | `FollowMeUnifiedAnchorContractTest.kt` | Verify `ATrainingTrackerMap.kt` passes `PaddingValues(0.dp)` as `contentPadding` to `GoogleMap` in `FOLLOW_ME` mode.<br>• Verify `ATrainingTrackerMap.kt` passes `lookaheadPaddingPercent` and `viewportHeightDp` to `followMeController`.<br>• Verify `followMeController` in `MapBehaviors.kt` invokes `calculateFollowMeCameraTarget` to calculate camera position target. |
| **TST-MAP-048.3** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Active Tracking GPS Update Lookahead
* **Given** an active workout tracking session in `FOLLOW_ME` mode,
* **When** GPS telemetry updates are received at 1 Hz,
* **Then** `followMeController` animates the camera target to $\mathbf{x}_{\text{target}}$ ahead of the athlete along `filteredBearing`,
* **And** the athlete marker remains stably positioned at the lower third without oscillating to the viewport center.

### Scenario 2: GoogleMap Decoupled Padding
* **Given** `GoogleMap` in `FOLLOW_ME` mode in `ATrainingTrackerMap.kt`,
* **When** inspecting `contentPadding`,
* **Then** `contentPadding` evaluates to `PaddingValues(0.dp)`, preventing native padding recalculation loops.

### Scenario 3: Lookahead Slider Responsiveness
* **Given** `tuningConfig.mapFollowMeLookaheadPaddingPercent` configured in Settings,
* **When** the athlete adjusts the slider,
* **Then** `followMeController` dynamically scales the forward lookahead ground offset accordingly.

### Scenario 4: Non-Follow-Me Modes Untouched
* **Given** non-Follow-Me modes (`EXPLICIT_BOUNDS`, `TRACK_AND_MARKERS`),
* **When** rendered,
* **Then** `contentPadding` remains `0.dp` and `MapBoundsController` governs camera framing.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-048.1` | Unit Test | `calculateFollowMeCameraTarget` | `REQ-MAP-046` (Clause 1) | Specified |
| `TST-MAP-048.2` | Contract | `ATrainingTrackerMap.kt` / `MapBehaviors.kt` | `REQ-MAP-046` (Clauses 1, 2) | Specified |
| `TST-MAP-048.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
