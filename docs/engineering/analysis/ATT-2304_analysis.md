# Stage 1 Analysis: ATT-2304 - Restore Immediate Collapsing Header Expansion When Scrolling Down in Workout Details

**Ticket**: [ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304)  
**Sub-task**: [ATT-2342](https://rainerblind.atlassian.net/browse/ATT-2342) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2304`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During Sprint Review testing of ATT-2178 on physical hardware, an essential collapsing header interaction was observed to be defective in the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`):
* **Expected Behavior**: When an athlete scrolls upward through extensive workout details (description, extrema, Strava segments, lap splits, elevation/telemetry charts), the upper header/metadata smoothly collapses upward out of view. As soon as the athlete reverses direction and scrolls downward (swiping downward anywhere within the screen), the collapsing header should immediately begin expanding and scrolling smoothly back down into view (standard Android "Quick Return" app bar interaction).
* **Observed Defect**: The collapsing header does not expand or scroll back down when scrolling downward within the list. The athlete cannot easily bring the header back into view during downward scrolling gestures, effectively locking the header in the collapsed state until the child list has scrolled all the way to its topmost boundary (item 0). For workouts with 30+ Strava segments or numerous laps (e.g. Einstein Halbmarathon 28.09.2014), the header is virtually unreachable during downward browsing.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic investigation into `CollapsingAppBarNestedScrollConnection.kt` and its commit history (`git log -p app/src/main/java/com/atrainingtracker/trainingtracker/ui/utils/CollapsingAppBarNestedScrollConnection.kt`) revealed the exact mechanism of the regression:

### 2.1 Commit Trace & The Shift in Scroll Dispatch
In commit `91fb65c1` (ATT-2198 / ATT-2178), `CollapsingAppBarNestedScrollConnection` was refactored to eliminate integer truncation micro-stutters and resolve perceived scrolling issues in child containers. As part of that change:
```kotlin
// In onPreScroll (ATT-2178):
override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    val delta = available.y
    // Only consume upward scroll (collapse) in onPreScroll
    if (delta < 0f && rawOffset > -appBarMaxHeight) {
        val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)
        val consumed = newRaw - rawOffset
        rawOffset = newRaw
        appBarOffset = newRaw.roundToInt()
        return Offset(0f, consumed)
    }
    return Offset.Zero
}

// In onPostScroll (ATT-2178):
override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    val delta = available.y
    // Consume downward scroll in onPostScroll when child container is at top boundary
    if (delta > 0f && rawOffset < 0f) {
        val newRaw = (rawOffset + delta).coerceIn(-appBarMaxHeight.toFloat(), 0f)
        val consumedY = newRaw - rawOffset
        rawOffset = newRaw
        appBarOffset = newRaw.roundToInt()
        return Offset(0f, consumedY)
    }
    return Offset.Zero
}
```

### 2.2 The Mechanism of Failure
1. In Jetpack Compose nested scrolling, `onPreScroll` is invoked before child scrollable components (such as `verticalScroll(rememberScrollState())` or `LazyColumn`) process available scroll deltas.
2. In ATT-2178, all downward scroll consumption (`available.y > 0f`) was intentionally removed from `onPreScroll` and moved to `onPostScroll`, with the intent of letting child containers scroll upward towards their top boundary without expanding the app bar.
3. However, when the child container is scrolled down (e.g. at position 500px in a 2000px list) and the user swipes downward, the child container eagerly consumes all available downward scroll delta (`available.y`).
4. Consequently, the delta passed to `onPostScroll` is `available.y == 0f` (`Offset.Zero`).
5. `onPostScroll` receives non-zero downward delta *only after* the child container has scrolled all the way to its topmost boundary (`scrollState.value == 0`).
6. This completely destroys the "Quick Return" interaction expected by athletes and standard in Material Design collapsing headers: dragging down fails to expand the header, forcing the athlete to swipe repeatedly until reaching the top before the header reappears.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restore immediate downward header expansion ("Quick Return") in `CollapsingAppBarNestedScrollConnection.kt`: downward scroll deltas (`available.y > 0f`) must be consumed in `onPreScroll` whenever the header is collapsed (`rawOffset < 0f`), expanding the header smoothly back towards `0f`.
  2. Maintain sub-pixel floating-point delta accumulation (`rawOffset`) to guarantee zero integer truncation jitter or micro-stutters.
  3. Ensure that once the header reaches full expansion (`rawOffset == 0f`), `onPreScroll` immediately returns `Offset.Zero`, allowing subsequent downward scroll deltas to pass completely to child containers without gesture trapping.
  4. Ensure upward scrolling (`available.y < 0f`) continues to smoothly collapse the header in `onPreScroll` until reaching `-appBarMaxHeight`, after which deltas pass to child containers.
  5. Update and expand `CollapsingAppBarNestedScrollConnectionTest.kt` to formally verify immediate downward expansion, upper/lower bounds, sub-pixel precision, and fluid child handoff.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do NOT modify the layout hierarchy or splitter math in `MapDetailLayout.kt` (interactive splitter ratio, 120dp minimum map height, and zoom toolbar remain untouched).
  2. Do NOT alter Strava segment row or lap split rendering in `StravaActivitySection.kt` or `LapSplitVisualizer.kt`.
  3. Do NOT introduce external third-party nested scrolling libraries.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-254` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout*), specifically Clause 1 (*Non-Blocking Nested Scroll Dispatch*).
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-40.14` under `ATT-2178` (commit `91fb65c1` and `083d1e04`).
* **Root Reason for Existing Formulation**: Clause 1 in `REQ-UI-254` prohibited eager downward scroll consumption in `onPreScroll` because of a concern that expanding the app bar would squash lower analytics graphs while scrolling within those graphs.
* **Preservation of Core Invariants**: 
  - Physical testing across actual devices and long workouts proved that deferring downward expansion entirely to `onPostScroll` causes severe UX degradation (the athlete cannot bring the header back without scrolling to the very top).
  - A balanced "Quick Return" dispatch in `onPreScroll` allows the athlete to summon the header with a short downward swipe at any time. Once the header is expanded (at `0f`), all subsequent downward scrolling belongs to the child.
  - Furthermore, sub-pixel precision accumulation (`rawOffset`), bounded clamping `[-appBarMaxHeight, 0]`, and full-screen map expansion upon collapse remain 100% intact.
  - We define **`REQ-UI-266`** to refine and supersede the downward scroll clause of `REQ-UI-254`, restoring immediate Quick Return header expansion while preserving all sub-pixel and structural invariants.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: `CollapsingAppBarNestedScrollConnection.kt`
* In `onPreScroll(available: Offset, source: NestedScrollSource)`:
  - When scrolling upward (`available.y < 0f`) and header is not fully collapsed (`rawOffset > -appBarMaxHeight`):
    - Consume delta, clamp `rawOffset` to `[-appBarMaxHeight, 0]`, update `appBarOffset = rawOffset.roundToInt()`, return consumed `Offset`.
  - When scrolling downward (`available.y > 0f`) and header is not fully expanded (`rawOffset < 0f`):
    - Consume delta, clamp `rawOffset` to `[-appBarMaxHeight, 0]`, update `appBarOffset = rawOffset.roundToInt()`, return consumed `Offset`.
  - When header is at boundary (fully collapsed on scroll up, or fully expanded on scroll down):
    - Return `Offset.Zero` so all remaining scroll delta is available to child scroll containers.
* In `onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource)`:
  - Retain safety check for unconsumed downward delta if needed, but primary Quick Return occurs in `onPreScroll`.
* Optional parameter `quickReturn: Boolean = true` to allow callers full configuration while defaulting to Quick Return across the application.

### Component 2: Verification in `CollapsingAppBarNestedScrollConnectionTest.kt`
* Update existing unit tests:
  - `testDownwardScroll_consumesInPreScroll_andExpandsImmediately`: Asserts that when partially or fully collapsed, downward scroll in `onPreScroll` immediately increases `appBarOffset` towards `0`.
  - `testDownwardScroll_clampsAtZero_andDefersExcessToChild`: Asserts that downward delta beyond `appBarMaxHeight` returns unconsumed offset for child processing.
  - `testSubPixelPrecision_accumulatesWithoutIntegerTruncationJitter`: Asserts that fractional float deltas accumulate accurately.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Upward collapsing behavior: scrolling up always collapses the header cleanly to `-appBarMaxHeight`.
  2. Bounded height: `appBarOffset` is strictly clamped within `[-appBarMaxHeight, 0]`.
  3. Minimum Map Height (`REQ-UI-250`): In uncollapsed base state, map height remains protected by `maxMetadataHeightDp`.
  4. 100% clean-room test suite pass rate across all modules.
* **Risk Rating**: **LOW**
  - Well-defined mathematical behavior in a single self-contained class (`CollapsingAppBarNestedScrollConnection.kt`).
  - No database, background service, or thread concurrency changes.
