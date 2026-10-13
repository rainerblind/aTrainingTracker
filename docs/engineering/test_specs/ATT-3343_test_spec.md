# Stage 2: Requirement & Test Specification - ATT-3343: Ensure tracking map fills all remaining vertical space above elevation profile

**Ticket**: [ATT-3343](https://atrainingtracker.atlassian.net/browse/ATT-3343)  
**Sub-task**: [ATT-3439](https://atrainingtracker.atlassian.net/browse/ATT-3439) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*)  
**Test Spec ID**: `TST-UI-319`  
**Branch**: `feature/ATT-3343`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107), and complements `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Scoping During Tracking*, Sprint 2026-41.10 / Sprint 2026-42.1, ATT-3199 & ATT-3317).
   - Formulated as `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*).
   - Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`): Introduced `Modifier.weight(1f, fill = false)` on the sensor grid Column to prevent sensor rows from crowding the elevation profile when the map is disabled.
   - Sprint 2026-41.9 (`ATT-3173`, commit `ad0c2020`): Excised `Modifier.weight(1f)` from the elevation profile `Surface` to eliminate half-screen stretching when the map is hidden, establishing canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
3. **Root Reason for Existing Formulation**:
   - In `ATT-3107`, `weight(1f, fill = false)` was placed on the sensor grid Column so it wraps content for 1–2 rows and scrolls for many rows without pushing bottom cockpit views off screen.
   - However, when the map is visible (`state.mapState.showMap == true`), the map is also assigned `Modifier.weight(1f)`.
   - In Jetpack Compose `Column`, weighted children divide the available remaining space proportionally according to their weights. Because both the sensor grid and the map declare weight `1f`, total weight is `2f` (50% quota each).
   - Because the sensor grid specifies `fill = false`, it only consumes its intrinsic content height (e.g. 1 row = ~80dp). Crucially, Compose does *not* reallocate the unused portion of the sensor grid's 50% quota to the other weighted child (the map).
   - The map remains hard-capped at its 50% allocation. The unweighted elevation profile renders directly below the map, leaving the remaining unused 50% space (~250–350dp) completely empty as dead white space at the bottom of the screen.
4. **Preservation of Core Invariants**:
   - **Invariant 1 (Map-Disabled Invariant)**: When `state.mapState.showMap == false`, the sensor grid retains `Modifier.weight(1f, fill = false)` so it wraps content for 1–2 rows and scrolls smoothly for 4–6 rows, and the elevation profile renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) directly below it (`REQ-UI-346`).
   - **Invariant 2 (Adaptive Height Invariant)**: The elevation profile retains its canonical adaptive height computed via `calculateElevationProfileHeight(range)` with non-weighted wrapping `Surface` (`padding(top = 8.dp)`).
   - **Invariant 3 (Sensor Grid Containment Invariant)**: When the map is visible, the sensor grid wraps content naturally when few rows exist, and is bounded to a maximum proportional constraint (`heightIn(max = availableHeight * 0.5f)`) with internal scrolling when many rows exist, preventing it from starving the map.
   - **Invariant 4 (Full Flexible Expansion Invariant)**: When the map is visible, the map Box is the sole flexible weight consumer (`Modifier.weight(1f, fill = true)`), expanding to consume 100% of all vertical space remaining between the sensor grid and the elevation profile (or bottom edge).
   - **Invariant 5 (System Stability)**: Recomposition isolation (`TrackingMapContainer`), ambient navigation overlays, live climb sheets, and 100% full-suite unit test pass rate MUST be strictly preserved.

---

## 1. Requirement Specification (REQ-UI-358)

### 1.1 Problem Statement & Rationale
When a tracking tab is configured with both a sensor grid, a map, and optionally an elevation profile (e.g. "Karte" tab), the Google Map should expand dynamically to fill all available vertical space between the sensor tiles and the bottom cockpit boundary. Currently, symmetric weight allocation (`1f` vs `1f`) in `SensorGridScreen.kt` causes the map to cap at 50% of the screen height when 1–2 sensor rows are displayed, leaving up to 40% of the screen completely empty as blank dead white space.

Establishing asymmetric weight allocation—where the sensor grid wraps content up to a bounded 50% limit and the map acts as the sole flexible weight consumer—guarantees that 100% of remaining vertical space is dedicated to the map without blank gaps.

### 1.2 Functional & Architectural Requirements
The system SHALL fulfill the following functional and architectural clauses:

1. **Available Viewport Height Measurement (`SensorGridScreen.kt`)**:
   - The main tracking cockpit container in `SensorGridScreen.kt` SHALL be hosted within `BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()))`.
   - The available height `val availableHeight = maxHeight` SHALL be passed or referenced to constrain child elements proportionally.

2. **Asymmetric Weight Allocation for Sensor Grid & Map**:
   - **When `state.mapState.showMap == true`**:
     - The sensor grid `Column` SHALL NOT declare `Modifier.weight(...)`.
     - The sensor grid `Column` SHALL declare:
       ```kotlin
       Modifier
           .fillMaxWidth()
           .heightIn(max = availableHeight * 0.5f)
           .verticalScroll(rememberScrollState())
       ```
     - When few sensor rows exist (e.g. 1–2 rows), the sensor grid SHALL measure at its exact intrinsic content height.
     - When many sensor rows exist (e.g. 4–6 rows), the sensor grid SHALL be bounded to at most 50% of `availableHeight` and scroll vertically internally.
     - The map `Box` SHALL declare `Modifier.fillMaxWidth().weight(1f, fill = true)`.
     - Because the map is the sole weighted child in the Column, it SHALL expand flexibly to occupy 100% of all vertical space remaining after measuring the sensor grid and the elevation profile.
   - **When `state.mapState.showMap == false`**:
     - The sensor grid `Column` SHALL declare `Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f, fill = false)` preserving the exact canonical behavior of `REQ-UI-346`.

3. **Elevation Profile Anchoring & Adaptive Height Preservation**:
   - The elevation profile `Surface` SHALL remain non-weighted with `Modifier.fillMaxWidth().padding(top = 8.dp)`.
   - The elevation profile SHALL continue to calculate its height adaptively between $70\text{dp}$ and $200\text{dp}$ via `calculateElevationProfileHeight(range)` per `REQ-UI-346`.
   - When the elevation profile is toggled off or contains fewer than 2 points, the map SHALL seamlessly expand down to the bottom screen boundary.

4. **Preservation of System Invariants**:
   - Zero regression to `TrackingMapContainer` recomposition barrier isolation (`REQ-UI-326`).
   - Ambient navigation overlays, live climb sheets, and configuration mode controls remain fully functional.
   - 100% clean-room unit test pass rate across the full test suite.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: 1-Row Sensor Grid with Map and Elevation Profile**:
  * *Given* a tracking view configured with 1 row of sensor fields, a map, and an elevation profile
  * *When* viewing the tracking screen
  * *Then* the map expands to fill all remaining vertical space between the sensor row and the elevation profile, with zero dead white space below the elevation profile.
* **Criterion 2: Multi-Row Sensor Grid with Map**:
  * *Given* a tracking view configured with 5 rows of sensor fields and a map
  * *When* viewing the screen
  * *Then* the sensor grid is capped at 50% of the available height and scrolls smoothly without pushing the map or elevation profile off screen.
* **Criterion 3: Map-Disabled Fallback**:
  * *Given* a tracking view with the map toggled off (`showMap == false`) and elevation profile enabled
  * *When* viewing the tracking screen
  * *Then* the sensor grid wraps content without stretching and the elevation profile renders at its canonical adaptive height directly below it.
* **Criterion 4: Elevation Profile Toggle Dynamism**:
  * *Given* an active tracking view with map and elevation profile
  * *When* the athlete toggles the elevation profile off in configuration mode
  * *Then* the map dynamically expands to fill the entire vertical space below the sensor grid down to the screen edge.

---

## 2. Test Case Specification (TST-UI-319)

### TST-UI-319.1: Compose Container & AST Layout Structure Tests (`SensorGridScreenLayoutTest.kt`)
* **Objective**: Verify that `SensorGridScreen.kt` structures the layout with `BoxWithConstraints` and assigns asymmetric weights conditionally.
* **Test Procedures**:
  1. Inspect `SensorGridScreen.kt` source AST.
  2. Verify that `BoxWithConstraints` wraps the cockpit layout.
  3. Verify that when `state.mapState.showMap` is true, the sensor grid applies `heightIn(max = ... * 0.5f)` and does not declare `weight(1f)`.
  4. Verify that when `state.mapState.showMap` is false, the sensor grid declares `weight(1f, fill = false)`.
  5. Verify that the map Box declares `Modifier.fillMaxWidth().weight(1f, fill = true)`.
* **Expected Result**: Structural contract holds strictly across all modes.

### TST-UI-319.2: Geometry & Layout Constraint Unit Verification (`SensorGridScreenLayoutTest.kt`)
* **Objective**: Verify mathematical viewport allocation under simulated screen constraints.
* **Test Procedures**:
  1. Simulate viewport $H = 800\text{dp}$.
  2. Given 1 row of sensors ($80\text{dp}$) and elevation profile ($128\text{dp}$):
     - Assert sensor grid height is $80\text{dp}$.
     - Assert elevation profile height is $128\text{dp}$.
     - Assert map height is $800 - 80 - 128 = 592\text{dp}$.
     - Assert total consumed height is $800\text{dp}$ ($0\text{dp}$ dead space).
  3. Given 6 rows of sensors ($480\text{dp}$) with map ($showMap = true$):
     - Assert sensor grid height is clamped to $400\text{dp}$ ($50\%$ of $800\text{dp}$).
     - Assert map receives remaining space.
  4. Given map disabled ($showMap = false$):
     - Assert sensor grid receives `weight(1f, fill = false)`.
* **Expected Result**: Mathematical layout constraints eliminate dead space completely.

### TST-UI-319.3: 9-Language Localization Audit (`TranslationParityTest.kt`)
* **Objective**: Confirm that zero localization discrepancies or missing string entries exist.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### TST-UI-319.4: Clean-Room Full Suite Regression Execution
* **Objective**: Validate that no existing tracking, map, or telemetry components are broken.
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% tests pass with 0 failures, 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Task | Test ID | Target Component |
| :--- | :--- | :--- | :--- |
| `REQ-UI-358.1` (Viewport Height Measurement) | AST & Layout Structure Test | `TST-UI-319.1` | `SensorGridScreen.kt` |
| `REQ-UI-358.2` (Asymmetric Weight Allocation) | Geometry & Weight Contract Test | `TST-UI-319.2` | `SensorGridScreen.kt` |
| `REQ-UI-358.3` (Elevation Profile Anchoring) | Adaptive Height Preservation Test | `TST-UI-319.2` | `SensorGridScreen.kt` |
| `REQ-UI-358.4` (System Invariants & Localization) | Clean-Room Suite & Translation Parity | `TST-UI-319.3`, `TST-UI-319.4` | Full Codebase |
