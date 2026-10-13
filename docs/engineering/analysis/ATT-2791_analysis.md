# Stage 1: Problem Domain & Root Cause Analysis - ATT-2791: Center sensor device tiles on ControlTrackingScreen with balanced header layout

**Ticket**: [ATT-2791](https://atrainingtracker.atlassian.net/browse/ATT-2791)  
**Sub-task**: [ATT-2880](https://atrainingtracker.atlassian.net/browse/ATT-2880) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-313` (*Control Tracking Screen Symmetrical Balanced Sensor Header Centering and Touch-Safe Accessibility Isolation*, refining `REQ-UI-301`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Problem Domain

On `ControlTrackingScreen.kt`, when an athlete has one or two connected sensors (e.g. cadence sensor "cad"), the sensor tiles do not appear centered relative to the smartphone display width. Instead, they are visibly shifted to the right side of the screen.

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
   - Ticket `ATT-2057` originally used a z-stacked `Box` where `ResearchButton` was aligned to `Alignment.TopStart` and `RemoteDevices` was aligned to `Alignment.TopCenter`. This worked when athletes had only 1 sensor, but when 5–6 sensors were paired, the wide `RemoteDevices` row spanned the full screen width, positioning its first tile directly underneath `ResearchButton`. Tapping the first sensor triggered `ResearchButton` instead.
   - Ticket `ATT-2479` (`REQ-UI-301`) eliminated the collision by placing `ResearchButton` and `RemoteDevices` into a side-by-side `Row`, giving `RemoteDevices` `Modifier.weight(1f)`.
   - While `ATT-2479` successfully eliminated the collision for 5+ sensors, it inadvertently introduced the rightward asymmetry for 1–2 sensors.
2. **Chesterton's Fence Purpose**:
   - The purpose of `ATT-2479` was to prevent `ResearchButton` from overlapping sensor tiles or being clipped by scrolling tiles.
   - The purpose of `ATT-2057` was to keep sensor tiles centered horizontally on the screen.
3. **Synthesis & Invariant Preservation**:
   - We must satisfy *both* requirements: eliminate hit-target and visual collisions for multi-device setups while guaranteeing true horizontal screen centering for 1–2 sensors without layout starvation or accessibility degradation.

---

## 3. Scope Bounding & Requirements Traceability

* **Primary Requirement**: `REQ-UI-313` (*Control Tracking Screen Symmetrical Balanced Sensor Header Centering and Touch-Safe Accessibility Isolation*)
* **Refined Baseline**: `REQ-UI-301` Clause 2 (*Side-by-Side Action & Sensor Container*)
* **Living Documentation Target**: `docs/requirements.md` (`REQ-UI-313`), `docs/tests.md` (`TST-UI-273`)

### In Scope
1. **Symmetrical Balancing Container**:
   - Restructure the sensor header container in `ControlTrackingScreen.kt` so that when `showResearchButton == true`, a symmetrical balancing anchor/spacer with identical width and padding is placed on the trailing (right) side of `RemoteDevices`.
2. **Accessibility & Semantics Hygiene (Addressing Auditor Challenge)**:
   - The balancing anchor MUST be strictly marked with `Modifier.clearAndSetSemantics { }` and `semantics { invisibleToUser() }`.
   - The balancing anchor MUST NOT receive clicks, touch events, or focus (`pointerInput(Unit) { }`, `focusable = false`), preventing phantom focus traps or accessibility reading of invisible elements.
3. **Viewport & Layout Bounds Resilience**:
   - On standard and compact phone viewports ($\ge 360\text{ dp}$), two 64 dp anchors leave $\ge 232\text{ dp}$ of horizontal clearance for `RemoteDevices`.
   - 1 device tile (72 dp) and 2 device tiles ($72 \times 2 + 8 = 152\text{ dp}$) fit with generous margins and are mathematically centered relative to the true screen width.
   - For 3+ devices, `LazyRow` scrolls within the allocated `weight(1f)` bounds without colliding with `ResearchButton`.
4. **Behavior when `devices.isEmpty()`**:
   - Maintain existing centered `ResearchButton` layout (`Arrangement.Center`) when `devices.isEmpty()`.
5. **Behavior when `showResearchButton == false`**:
   - When `showResearchButton == false`, `RemoteDevices` spans the full width (`fillMaxWidth()`) with no trailing spacer, centered naturally.
6. **Contract & Regression Testing**:
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
3. **Accessibility Invariant**: Symmetrical balancing anchor must be completely invisible to accessibility services (`clearAndSetSemantics`, `invisibleToUser()`, non-clickable, non-focusable).
4. **Localization Invariant**: Symmetrical balancing anchor must adapt to all 9 supported locales automatically.
5. **Full Suite Regression Invariant**: 100% test pass rate across `./gradlew testDebugUnitTest`.

---

## 5. Architectural Options & Detailed Comparative Analysis

### Option A: Symmetrical Accessibility-Cleared Balancing Anchor (Recommended)
Place an invisible `ResearchButton` on the trailing edge inside a container with `Modifier.clearAndSetSemantics { }`, `alpha(0f)`, and disabled touch/click handling:
```kotlin
if (showResearchButton) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .alpha(0f)
            .clearAndSetSemantics { }
    ) {
        ResearchButton(
            isEnabled = false,
            onClick = {}
        )
    }
}
```
* **Pros**:
  - Perfectly matches the width, height, margins, and locale-specific text length of the left button across all screen densities and font scalings without hardcoded pixel guesswork.
  - Mathematically guarantees true center alignment of `Modifier.weight(1f)`.
  - Zero collision between sensor tiles and `ResearchButton`.
  - `clearAndSetSemantics { }` completely removes the element from the accessibility tree, eliminating phantom TalkBack focus traps.
* **Cons**:
  - Small duplicate composable allocation (negligible memory and composition overhead).

### Option B: Absolute Z-Stacked Box with Padding Offsets
Use a `Box` where `RemoteDevices` spans full width with symmetric horizontal padding `PaddingValues(horizontal = researchButtonWidth)`.
* **Pros**: Single `ResearchButton` instance.
* **Cons**: Requires measuring the dynamic width of `ResearchButton` asynchronously or via `SubcomposeLayout`, introducing re-composition latency or layout jitter during orientation changes.

### Option C: Legacy Z-Stacked Box (Rejected)
Revert to `ATT-2057` layout.
* **Pros**: Simple.
* **Cons**: Re-introduces the severe collision defect where scrolling sensor tiles draw directly under `ResearchButton` on multi-device setups. Violates `REQ-UI-301`.

**Recommendation**: Adopt **Option A** with strict accessibility semantics clearing (`clearAndSetSemantics { }`). It provides mathematical centering, zero collision, perfect locale adaptation, and 100% accessibility compliance.
