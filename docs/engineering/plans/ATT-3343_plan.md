# Stage 3: Implementation Plan - ATT-3343: Ensure tracking map fills all remaining vertical space above elevation profile

**Ticket**: [ATT-3343](https://rainerblind.atlassian.net/browse/ATT-3343)  
**Sub-task**: [ATT-3440](https://rainerblind.atlassian.net/browse/ATT-3440) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Weight Distribution, and Zero Dead Space Anchoring*)  
**Test Mapping**: `TST-UI-319` (*Dynamic Map Viewport Allocation, Asymmetric Weight Distribution, and Elevation Profile Anchoring*)  
**Branch**: `feature/ATT-3343`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Weight Distribution, and Zero Dead Space Anchoring*), targeting `ui/tracking/tracking/SensorGridScreen.kt` and `ui/tracking/tracking/SensorGridScreenLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-326` (ATT-2944) isolated map state recomposition into `TrackingMapContainer`.
   - `REQ-UI-346` (ATT-3173) introduced `ElevationProfile` into the tracking cockpit below the map.
   - `REQ-UI-355` (ATT-3199 / ATT-3317) added adaptive elevation profile scaling and rolling lookahead configuration.
   - In Sprint 2026-40.16 (ATT-2944), `Modifier.weight(1f, fill = false)` was placed on the sensor grid `Column`, and `Modifier.weight(1f)` was placed on the map `Box` within `SensorGridScreen.kt`.
3. **Root Reason for Existing Formulation**:
   - The developer originally intended `Modifier.weight(1f, fill = false)` on the sensor grid to prevent multiple rows of sensor fields from pushing the map and elevation profile completely off screen, while allowing the grid to shrink when fewer rows were configured.
   - However, in Jetpack Compose `Column`, when multiple children have `weight(1f)`, Compose calculates the total weight sum as $2.0f$ and reserves exactly $50\%$ of remaining height for each weighted child.
   - When `fill = false` is used on the sensor grid, if sensor fields only require e.g. $80\text{dp}$ out of their $400\text{dp}$ ($50\%$) allocation, Compose does not reallocate the remaining $320\text{dp}$ to the map. The map remains capped at its $50\%$ allocation, and the elevation profile is anchored directly below the map, leaving the entire unused $320\text{dp}$ as a blank white void at the bottom.
4. **Preservation of Core Invariants**:
   - Invariant 1: Multi-row sensor fields (e.g. 5+ rows) must remain scrollable and bounded without pushing the map or elevation profile off screen.
   - Invariant 2: When the map is disabled (`showMap = false`), the sensor grid must retain its flexible unbounded/scrollable behavior (`weight(1f, fill = false)`), and the elevation profile must anchor neatly below it (REQ-UI-346).
   - Invariant 3: Zero regression on rolling elevation profile window math, climbing markers, and lookahead distance (REQ-UI-355 / ATT-3317).
   - Invariant 4: 100% 9-language translation parity maintained across all supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 1. Problem Description & Architectural Context

In the tracking screen cockpit (`SensorGridScreen.kt`), athlete tabs can be configured with:
1. Sensor fields (1 to $N$ rows)
2. Interactive map (`TrackingMapContainer`)
3. Elevation profile (`ElevationProfile`)
4. Ambient navigation overlays and HUD cards

When viewing tracking tabs with 1–2 rows of sensors, map, and elevation profile, the map currently occupies only ~40% of the viewport. A massive white void (~40% of the screen height) sits completely blank at the bottom below the elevation profile.

Athletes rely on maximum map viewport visibility while navigating routes or monitoring telemetry. By replacing the symmetric `1f` weight allocation with an asymmetric model governed by `BoxWithConstraints` ($H_{\text{sensor}} \le 0.5 \times H_{\text{available}}$ without weight, Map $\text{weight}(1f, fill = true)$), the map dynamically claims 100% of all available vertical space between the sensor grid and the elevation profile.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Weight Distribution, and Zero Dead Space Anchoring*)
  * `REQ-UI-358.1`: Dynamic Viewport Height Measurement via `BoxWithConstraints`
  * `REQ-UI-358.2`: Asymmetric Weight Distribution between Sensor Grid and Map
  * `REQ-UI-358.3`: Seamless Elevation Profile Anchoring & Zero Dead Space
  * `REQ-UI-358.4`: System Invariants, Map-Disabled Fallback & Multi-Row Scrolling
* **Test Mapping**: `TST-UI-319` (*Dynamic Map Viewport Allocation, Asymmetric Weight Distribution, and Elevation Profile Anchoring*)
  * `TST-UI-319.1`: Compose Container & AST Layout Structure Tests (`SensorGridScreenLayoutTest.kt`)
  * `TST-UI-319.2`: Geometry & Layout Constraint Unit Verification (`SensorGridScreenLayoutTest.kt`)
  * `TST-UI-319.3`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-319.4`: Clean-Room Full Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Map Recomposition Isolation**: `TrackingMapContainer` must remain isolated behind its recomposition boundary (`REQ-UI-326`).
2. **Multi-Row Scrolling Invariant**: When sensor rows exceed 50% of the available viewport height, the sensor grid must vertically scroll without pushing the map or elevation profile off screen.
3. **Map-Disabled Invariance**: When `state.mapState.showMap` is false, the sensor grid receives `Modifier.weight(1f, fill = false)` and the elevation profile anchors directly beneath it (`REQ-UI-346`).
4. **Adaptive Profile Scaling**: `ElevationProfile` retains its canonical adaptive height and rolling lookahead interpolation (`REQ-UI-355`).
5. **No Regressions on Ambient Overlays**: Overlay HUD cards (navigation hints, fork cards, climb sheet) remain anchored at the top/bottom layers within the outer `BoxWithConstraints`.

---

## 4. Proposed Architectural Changes (SWE.2) & UI Consistency

### 4.1 UI Consistency (Governance Rule 23)
* **Closest Reference Screen**: `SensorGridScreen.kt` (Tracking Cockpit).
* **Reused Components**: `SensorFieldView`, `TrackingMapContainer`, `ElevationProfile`, `SpatialCockpitToggleCard`.
* **Theme Tokens**: Standard `MaterialTheme.colorScheme.surface`, `TTColor` for profile, `paddingValues.calculateTopPadding()`.
* **Zero One-Off Styles**: No custom color tokens or unconventional margins introduced; existing padding and spacing are preserved.

### 4.2 Component Architecture: `SensorGridScreen.kt`
* Wrap the Tracking & Preview modes container (lines 523–630) with `BoxWithConstraints`:
```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .padding(top = paddingValues.calculateTopPadding())
) {
    val availableHeight = maxHeight
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sensor Grid
        val sensorGridModifier = if (state.mapState.showMap) {
            Modifier
                .fillMaxWidth()
                .heightIn(max = availableHeight * 0.5f)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
        }

        Column(
            modifier = sensorGridModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Top
        ) {
            // Rows rendering (unchanged)
        }

        // Map Container
        if (state.mapState.showMap) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                TrackingMapContainer(...)
            }
        }

        // Elevation Profile
        if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                ElevationProfile(...)
            }
        }
    }
    // Overlays (unchanged)
}
```

---

## 5. Atomic Step-by-Step Implementation Sequence

### Step 1: Author Layout Verification Tests (`SensorGridScreenLayoutTest.kt`)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`.
* Implement AST structural checks:
  - Verify `BoxWithConstraints` is present.
  - Verify conditional modifier application (`heightIn(max = ... * 0.5f)` vs `weight(1f, fill = false)`).
  - Verify `weight(1f, fill = true)` on the map `Box`.
* Implement mathematical geometry simulations:
  - Simulate $H = 800\text{dp}$, $1$ row ($80\text{dp}$), profile ($128\text{dp}$) $\implies$ Map height $= 592\text{dp}$, dead space $= 0\text{dp}$.
  - Simulate $H = 800\text{dp}$, $6$ rows ($480\text{dp}$), map enabled $\implies$ Sensor grid clamped to $400\text{dp}$.
  - Simulate map disabled $\implies$ Sensor grid gets `weight(1f, fill = false)`.
* Target test command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`

### Step 2: Refactor `SensorGridScreen.kt` Layout Architecture
* Update `SensorGridScreen.kt`:
  - Replace `Box(...)` with `BoxWithConstraints(...)`.
  - Calculate `availableHeight = maxHeight`.
  - Apply `sensorGridModifier` conditionally based on `state.mapState.showMap`.
  - Set `weight(1f, fill = true)` on the map `Box`.
  - Preserve profile and overlays intact.
* Re-run targeted layout test:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`

### Step 3: Verify Tracking Suite & Localization Parity
* Execute `TranslationParityTest.kt` and `SensorGridScreenElevationProfileTest.kt`.
* Commands:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenElevationProfileTest"`

### Step 4: Full Clean-Room Regression Suite
* Run `./gradlew testDebugUnitTest`.

---

## 6. Verification & Test Execution Strategy

| Test Class | Focus / Assertion |
| :--- | :--- |
| `SensorGridScreenLayoutTest.kt` | AST checks and mathematical viewport height allocation ($0\text{dp}$ dead space) |
| `SensorGridScreenElevationProfileTest.kt` | Verify elevation profile rendering and scope integration remain intact |
| `TranslationParityTest.kt` | 100% 9-language translation parity |
| Full Suite (`testDebugUnitTest`) | 100% clean-room test pass across all project modules |

---

## 7. Rollback & Risk Mitigation Strategy

* **Zero Schema / Data Risk**: No database migrations or persistent DataStore schema changes are involved.
* **Layout Fallback**: If `BoxWithConstraints` behaves unexpectedly under edge screen densities, the modifier can be reverted to a fixed percentage clamp or standard Intrinsic height without impacting tracking business logic or GPS recording.
