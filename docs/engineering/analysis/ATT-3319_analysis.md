# Stage 1 Analysis: ATT-3319 - Ensure Segment and Climb bottom sheets always pop up to full height on new active entity

**Ticket**: [ATT-3319](https://rainerblind.atlassian.net/browse/ATT-3319)  
**Sub-task**: [ATT-3332](https://rainerblind.atlassian.net/browse/ATT-3332) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3319`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Statement & Motivation

In ticket ATT-3176 (`REQ-UI-343`), the live climb and segment bottom sheets (`LiveSegmentSheet` and `LiveClimbSheet`) inside `SensorGridScreen.kt` were configured to default to `SheetValue.Expanded` rather than `SheetValue.PartiallyExpanded`. This design decision was established to ensure that athletes riding or running do not need to take their hands off handlebars or perform touch gestures on smartphone screens to view climb/segment elevation profiles and HUD telemetry.

However, during real-world tracking and TCX replaying, athletes frequently observe that the bottom sheet remains stuck at peek height (`SheetValue.PartiallyExpanded` / 56 dp) when approaching or entering a segment or climb.

Expected behavior:
Whenever any new live segment or live climb becomes active, or when entering tracking mode with an active entity, the bottom sheet must reliably pop up to its fully expanded height (`SheetValue.Expanded`). Athletes must retain the ability to swipe down to peek height during an ongoing segment or climb without having the sheet continuously bounce back up during that same entity.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `SensorGridScreen.kt` (lines 241–252) identifies two interacting architectural flaws in the reactive sheet controller:

```kotlin
// Lines 241-252 in SensorGridScreen.kt:
LaunchedEffect(shouldShowBottomSheet) {
    if (!shouldShowBottomSheet) {
        if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
            scaffoldState.bottomSheetState.hide()
        }
    } else {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
            scaffoldState.bottomSheetState.expand()
        }
    }
}
```

1. **Strict Hidden-Only Guard Traps Sheet in Peek State**:
   The controller only calls `expand()` if `scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden`. If the athlete previously swiped the sheet down to peek height during an earlier segment or climb, or if the sheet entered `PartiallyExpanded` during composition, `currentValue` is `SheetValue.PartiallyExpanded`, NOT `SheetValue.Hidden`. Consequently, the guard condition evaluates to `false`, and `expand()` is never called. The sheet remains trapped at peek height (56 dp), hiding the elevation profile canvas and telemetry HUD.

2. **Coarse LaunchedEffect Keying Misses Entity Transitions**:
   The `LaunchedEffect` is keyed exclusively on the boolean `shouldShowBottomSheet`. When an athlete follows a route with consecutive climbs or segments (e.g. transitioning from Climb 1 to Climb 2, or approaching consecutive Strava segments), `shouldShowBottomSheet` remains continuously `true`. Because the key does not change, the coroutine effect never re-executes. The new entity simply inherits whatever collapsed or peek state was active at the end of the previous entity.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Define a composite active entity key (`activeEntityKey`) in `SensorGridScreen.kt` uniquely identifying the current active segment (`stravaId`) or active climb (`id`, `routeIndex`, `name`).
  * Key `LaunchedEffect` on both `shouldShowBottomSheet` and `activeEntityKey`.
  * Ensure that when `shouldShowBottomSheet == true`, `scaffoldState.bottomSheetState.expand()` executes unconditionally upon entity activation or entrance into tracking mode, expanding the sheet to `SheetValue.Expanded` even if currently in `SheetValue.PartiallyExpanded`.
  * Ensure that swiping down during an active entity is respected and not reverted while the entity remains active.
  * Update contract test `SensorGridScreenClimbSuppressionTest.kt` to verify entity-keyed expansion and behavior.
  * Update living documentation (`docs/requirements.md` / `REQ-UI-343` and `docs/tests.md` / `TST-UI-303`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to sheet contents (`LiveSegmentSheet.kt`, `LiveClimbSheet.kt`).
  * No changes to peek height calculation (`BottomSheetDesign.PeekHeightLiveSegment`).
  * No changes to climb detection algorithms in `LiveClimbsRepository` or segment math in `LiveSegmentsRepository`.
  * No changes to turn-by-turn navigation HUD or elevation profile geometry.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*), amending Clause 2.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.8 (`ATT-3176`, commit `6b9d0315`), which amended `REQ-UI-327` (Sprint 2026-41.7, `ATT-2945`).
* **Root Reason for Existing Formulation**: ATT-3176 added `if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) expand()` with the intention of avoiding redundant calls to `expand()` if already expanded. However, it inadvertently blocked expansion when the sheet was in `SheetValue.PartiallyExpanded`, and keyed only on `shouldShowBottomSheet`.
* **Preservation of Core Invariants**: Manual swipe-down gesture ergonomics during an ongoing entity remain strictly preserved because `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` does not re-fire when the athlete swipes. Tab isolation (`isTabActive`), hidden state handling (`hide()`), and 100% full-suite unit test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Active Entity Key Resolution**:
   Compute an active entity key in `SensorGridScreen.kt`:
   ```kotlin
   val activeEntityKey = when {
       showLiveSegments && activeSegment != null -> "segment_${activeSegment.staticData.summary.stravaId}"
       showLiveClimbs && activeLiveClimb != null -> "climb_${activeLiveClimb?.climb?.id}_${activeLiveClimb?.routeIndex}_${activeLiveClimb?.climb?.name}"
       else -> null
   }
   ```

2. **Entity-Aware Reactive LaunchedEffect**:
   Drive expansion reactively using both `shouldShowBottomSheet` and `activeEntityKey`:
   ```kotlin
   LaunchedEffect(shouldShowBottomSheet, activeEntityKey) {
       if (!shouldShowBottomSheet) {
           if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
               scaffoldState.bottomSheetState.hide()
           }
       } else {
           // Unconditionally expand to full height when a sheet activates or a new entity appears
           scaffoldState.bottomSheetState.expand()
       }
   }
   ```

3. **User Interaction Invariant**:
   While an entity is active, its `activeEntityKey` is constant. Swiping the sheet down to `SheetValue.PartiallyExpanded` alters `scaffoldState.bottomSheetState.currentValue`, but does NOT restart the `LaunchedEffect`. Thus, manual swipe-down is stable and undisturbed. When the entity completes and the next entity activates, `activeEntityKey` changes, re-triggering the `LaunchedEffect` and smoothly calling `expand()` back to full height.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Zero Telemetry Occlusion on Disabled Tabs*: Sheet remains hidden with 0 dp peek height when `!shouldShowBottomSheet`.
  2. *Interactive Swipe Ergonomics*: Athlete can collapse the sheet during a climb/segment to peek height.
  3. *Clean-Room Pass Rate*: 100% test pass rate across `./gradlew testDebugUnitTest`.
* **Risk Rating**: **LOW**.
  * Changes are localized strictly to Compose state observation and `LaunchedEffect` keying in `SensorGridScreen.kt`. Zero impact on database schemas, background services, or network repositories.
