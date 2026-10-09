# Stage 1: Problem Domain & Root Cause Analysis - ATT-2791: Center sensor device tiles on ControlTrackingScreen with balanced header layout

**Ticket**: [ATT-2791](https://atrainingtracker.atlassian.net/browse/ATT-2791)  
**Sub-task**: [ATT-2880](https://atrainingtracker.atlassian.net/browse/ATT-2880) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.5`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Problem Domain

On `ControlTrackingScreen.kt`, when an athlete has one or more connected sensors (e.g. cadence sensor "cad"), the sensor tiles do not appear centered relative to the smartphone screen width. Instead, they are visibly shifted to the right side of the screen.

### Root Cause Analysis
In `ControlTrackingScreen.kt` (lines 293–311):
```kotlin
if (devices.isNotEmpty()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showResearchButton) {
            ResearchButton(
                isEnabled = searchingFor == null,
                onClick = onSearch
            )
        }
        RemoteDevices(
            devices = devices,
            onDeviceClick = onDeviceClick,
            modifier = Modifier.weight(1f)
        )
    }
}
```
* `ResearchButton` occupies approximately 64–72 dp on the leading (left) edge of the `Row`.
* `RemoteDevices` is assigned `Modifier.weight(1f)`, granting it the *remaining* width $(\text{ScreenWidth} - W_{\text{ResearchButton}})$.
* Inside `RemoteDevices.kt`, the inner `LazyRow` uses `horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`.
* Consequently, `RemoteDevices` centers its sensor items inside the asymmetric remaining width block rather than the screen as a whole.
* For athletes with 1 or 2 sensors, the center of the sensor tile cluster is shifted to the right by $\frac{1}{2} W_{\text{ResearchButton}} \approx 32\text{--}36\text{ dp}$, producing an unbalanced, lopsided header.

---

## 2. Requirement Archaeology & Chesterton's Fence Investigation

1. **Historical Origin (`ATT-2057` & `ATT-2479` / `REQ-UI-301`)**:
   - Ticket `ATT-2057` originally used a z-stacked `Box` where `ResearchButton` was aligned to `Alignment.TopStart` and `RemoteDevices` was aligned to `Alignment.TopCenter`. This worked when athletes had only 1 sensor, but when 5–6 sensors were paired, the wide `RemoteDevices` row collided with and overlapped `ResearchButton`.
   - Ticket `ATT-2479` (`REQ-UI-301`) eliminated the collision by placing `ResearchButton` and `RemoteDevices` into a side-by-side `Row`, giving `RemoteDevices` `Modifier.weight(1f)`.
   - While `ATT-2479` successfully eliminated the collision for 5+ sensors, it inadvertently introduced the rightward asymmetry for 1–2 sensors.
2. **Chesterton's Fence Purpose**:
   - The purpose of `ATT-2479` was to prevent `ResearchButton` from overlapping sensor tiles or being clipped by scrolling tiles.
   - The purpose of `ATT-2057` was to keep sensor tiles centered horizontally on the screen.
3. **Synthesis**:
   - We must satisfy *both* intents: eliminate visual collision for multi-device setups while guaranteeing true horizontal screen centering for 1–2 sensors.

---

## 3. Scope Bounding

### In Scope
1. **Symmetrical Balancing Container**:
   - Restructure the sensor header container in `ControlTrackingScreen.kt` so that when `showResearchButton == true`, a symmetrical balancing anchor/spacer with identical width and padding is placed on the trailing (right) side of `RemoteDevices`.
   - Use an invisible `Box` with `wrapContentSize()` hosting an inactive, zero-alpha `ResearchButton` (cleared semantics) to ensure identical width matching across all screen densities, font scales, and 9 localized strings without hardcoded pixel estimates.
2. **Behavior when `devices.isEmpty()`**:
   - Maintain existing centered `ResearchButton` layout (`Arrangement.Center`) when `devices.isEmpty()`.
3. **Behavior when `showResearchButton == false`**:
   - When `showResearchButton == false`, `RemoteDevices` spans the full width (`fillMaxWidth()`) with no trailing spacer, centered naturally.
4. **Contract & Regression Testing**:
   - Update `ControlTrackingSensorHeaderContractTest.kt` to verify balanced symmetrical layout assertions.
   - Run full regression suite (`./gradlew testDebugUnitTest`).

### Out of Scope
- Modifying `RemoteDevices.kt` tile sizing (bounded 72 dp established in `ATT-2772` / `REQ-UI-304`).
- Changing sensor pairing or BLE/ANT+ scanning logic.
- Modifying `SearchArea.kt` or `BatteryOptimizationWarningBanner.kt`.

---

## 4. System Invariants & Preserved Behavior

1. **Zero Collision Invariant**: `ResearchButton` and `RemoteDevices` must never overlap, even with 6+ devices.
2. **True Screen Centering**: For 1 connected device, the sensor tile must be mathematically centered on the horizontal axis of the screen.
3. **Accessibility Invariant**: Symmetrical balancing anchor must be invisible to accessibility services (`invisibleToUser()` / cleared semantics).
4. **Localization Invariant**: Identical width matching must adapt to all 9 supported locales automatically.
5. **Full Suite Regression Invariant**: 100% test pass rate across `./gradlew testDebugUnitTest`.

---

## 5. Architectural Options & Recommendation

### Option A: Symmetrical Invisible Anchor (Recommended)
Place an invisible `ResearchButton` on the trailing edge inside a `clearAndSetSemantics { }` / `alpha(0f)` wrapper.
- **Pros**: Perfectly mirrors the width, height, margins, and locale-specific text length of the left button across all screen densities and font scalings without layout drift. Mathematically guarantees true center alignment of `Modifier.weight(1f)`.
- **Cons**: Small duplicate composable allocation (trivial).

### Option B: Absolute Coordinate Centering via `SubcomposeLayout` or `Box` with offsets
Measure `ResearchButton` and offset `RemoteDevices`.
- **Pros**: Single button node.
- **Cons**: Higher complexity, potential measurement lag / re-composition jitter during orientation changes.

**Recommendation**: Adopt **Option A**. It is robust, declarative, and 100% resilient across font scaling and localization.
