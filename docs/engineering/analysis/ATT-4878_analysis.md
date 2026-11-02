# Stage 1 Analysis: ATT-4878 - Stretch Cockpit Sensor Field Card Height and Border to Match Tallest Field in Row

**Ticket**: [ATT-4878](https://rainerblind.atlassian.net/browse/ATT-4878)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4878`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
In `SensorGridScreen.kt`, multi-column sensor rows use `Row(modifier = Modifier.height(IntrinsicSize.Min))`. However, the wrapping per-cell `Box(modifier = Modifier.weight(...))` and `SensorFieldView` `Card` lack `.fillMaxHeight()`, and the inner card `Row` hardcodes `.height(IntrinsicSize.Min)`. When fields of different sizes (e.g. `SMALL` and `HUGE`) or layouts (numeric value next to a tall zone gauge) share a row, the smaller card floats at its intrinsic height, leaving disjointed top and bottom borders.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: While outer `Row` declares `Modifier.height(IntrinsicSize.Min)`, height propagation is broken across intermediate composables:
  * `SensorGridScreen.kt` (lines 741, 1060): per-cell `Box` lacks `.fillMaxHeight()`.
  * `SensorFieldView.kt` (lines 265, 293): `Card` lacks `.fillMaxHeight()`, and inner layout `Row` declares `.height(IntrinsicSize.Min)`, preventing expansion.
  * Internal vertical arrangement in `SensorFieldView.kt`: `Column` does not vertically center telemetry when extra vertical space is available.
* **Independent Peer Review & Verification**:
  * Verified `SensorGridScreen.kt`: `Box(modifier = Modifier.weight(effectiveWeight))` omits `.fillMaxHeight()` in both tracking (`SensorGridContent`) and configuration modes.
  * Verified `SensorFieldView.kt`: `Card` only applies `modifier.fillMaxWidth()`; inner `Row` clamps height to its own intrinsic minimum.
  * Layout geometry verification: In Compose, `Row(modifier = Modifier.height(IntrinsicSize.Min))` sizes to the maximum minimum intrinsic height of its children. Adding `.fillMaxHeight()` to the `Box` and `Card` stretches the smaller card flush with its tallest neighbor without altering row height.
  * Content vertical alignment: Wrapping value/gauge content in `Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center)` cleanly centers data readouts while preserving top label anchoring and bottom action button docking.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Apply `.fillMaxHeight()` to per-cell `Box` wrappers and `SensorFieldView` `Card` in `TRACKING`, `PREVIEW`, and `CONFIGURATION` modes.
  * Update `SensorFieldView.kt` inner `Row` to `.fillMaxHeight()` and center value/gauge content vertically within available height.
  * Ensure flush border alignment across mixed `ViewSize` combinations (e.g. `SMALL` next to `LARGE`/`HUGE`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Changing font scale or typography tokens across `ViewSize` enums (preserved as-is).
  * Altering horizontal column width weights or split presets (established in ATT-4861).
  * Changing section divider geometry or elevation profile heights.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-438`, extending `REQ-UI-200` (Sensor Grid Layout) and `REQ-UI-437` (Horizontal Width Ratios).
* **Historical Origin & Commit Trace**: Commit `a6920388` introduced initial Compose sensor grid with unconstrained card heights.
* **Root Reason for Existing Formulation**: Initial implementation focused on basic grid assembly; intrinsic height mismatch became prominent with diverse `ViewSize` selections and zone gauge bars.
* **Preservation of Core Invariants**: Intrinsic row height remains governed by the tallest tile; single-field rows and uniform-size rows remain visually identical.

---

## 5. Architectural Strategy & High-Level Solution
* **Grid Container (`SensorGridScreen.kt`)**:
  * In `SensorGridContent` and configuration mode `fieldsInThisRow.forEach`: update `Box(modifier = Modifier.weight(effectiveWeight).fillMaxHeight())` and pass `modifier = Modifier.fillMaxHeight()` to `SensorFieldView`.
* **Tile Component (`SensorFieldView.kt`)**:
  * In `Card`: apply `modifier.fillMaxWidth().fillMaxHeight()`.
  * In inner `Row`: replace `.height(IntrinsicSize.Min)` with `.fillMaxHeight()`.
  * In inner `Column`: assign `Modifier.weight(1f).fillMaxHeight()`, keeping label `Row` at top, wrapping value and gauge in a centered `Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center)`, and anchoring configuration `IconButton`s at the bottom.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `Row(modifier = Modifier.height(IntrinsicSize.Min))` strictly avoids infinite height expansion constraints.
  2. Indicator strip (`Spacer`) and card `border` cleanly span 100% of the stretched height.
  3. Zero visual regression on rows with identical `ViewSize` tiles.
* **Risk Rating**: **LOW** — Pure Jetpack Compose layout modifier alignment, zero database schema impact, zero service/telemetry lifecycle risk.
