# Stage 1: Problem Domain & Root Cause Analysis - ATT-4860: [Feature] Zone bar: highlight current zone

**Ticket**: [ATT-4860](https://rainerblind.atlassian.net/browse/ATT-4860)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Problem Domain & Forensic Root Cause

In `ZoneGaugeBar.kt`, all 5 (Heart Rate) or 7 (Power) zone segments are currently rendered with identical uniform vertical height (`trackHeight = 6.dp`). In outdoor sports with glance-based peripheral vision, identifying the active training zone relies solely on the 4dp blue needle or the small bottom badge text (`"Z3"`). Furthermore, with the introduction of bar-only tiles in `ATT-4841` (`showValue = false, showBar = true`), the standard `6.dp` bar height leaves excessive vertical empty space in the tile card, diminishing readability and visual impact.

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)

1. **Active Zone Height & Geometry Emphasis (`ZoneGaugeBar.kt`)**:
   - For the currently active zone segment (`index == state.activeZoneIndex - 1`), expand its vertical height to $1.6\times$ the base track height (`activeTrackHeight = trackHeight * 1.6f`), centered vertically relative to the inactive track segments.
   - Give the active segment rounded corners (`RoundedCornerShape(activeTrackHeight / 4)`) and ensure it renders slightly elevated over adjacent dividers so the zone block pops out clearly in peripheral vision.
   - Adjust needle container height (`activeTrackHeight + 4.dp`) so the Primary Blue needle cleanly spans and frames the elevated active segment with its contrasting halo outline.
   - When telemetry is stale or null (`state.activeZoneIndex == null`), all segments retain standard un-elevated `trackHeight`.
2. **Dynamic Height Scaling in Bar-Only Mode (`SensorFieldView.kt`, `ZoneGaugeBar.kt`, `PedalPowerBalanceBar.kt`, `WPrimeBatteryGaugeBar.kt`)**:
   - In bar-only mode (`!fieldState.showValue && fieldState.showBar`), scale track height based on `viewSize`:
     - `ZoneGaugeBar`: `SMALL = 10.dp`, `NORMAL = 14.dp`, `LARGE = 18.dp`, `EXTRA_LARGE = 22.dp` (vs standard `4.dp`, `6.dp`, `8.dp`, `10.dp`).
     - `PedalPowerBalanceBar`: `SMALL = 12.dp`, `NORMAL = 16.dp`, `LARGE = 20.dp`, `EXTRA_LARGE = 24.dp` (vs standard `6.dp`, `8.dp`, `10.dp`, `12.dp`).
     - `WPrimeBatteryGaugeBar`: Scale capsule height proportionally (`SMALL = 14.dp`, `NORMAL = 20.dp`, `LARGE = 26.dp`, `EXTRA_LARGE = 32.dp` vs standard `16.dp`).
3. **Contrast & Theme Invariants**:
   - Maintain 100% compliance with True-Black AMOLED contrast (`MaterialTheme.colorScheme.onSurface`, `primary`, and `outlineVariant`).

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. *Original Requirement & Target*: Extends `REQ-UI-376` (Zone Gauge Bar), `REQ-UI-390` (Proportional Zone Track Scaling), and complements `REQ-TRK-046` (Per-Field Element Display Configuration).
2. *Historical Origin & Commit Trace*: `ATT-1293` (original `ZoneGaugeBar`), `ATT-3708` (proportional segment scaling & blue needle), `ATT-4841` (per-field bar flag).
3. *Root Reason for Existing Formulation*: `ZoneGaugeBar` originally clipped all segments inside a single uniform outer `Box(height = trackHeight)`. Introducing active zone emphasis requires per-segment height differentiation while preserving outer proportional horizontal weights.
4. *Preservation of Core Invariants*: Proportional weighting (`state.segmentWeights`), needle interpolation math (`ZoneGaugeMath`), 0.35 stale alpha, and bottom `"Z1"`..`"Z7"` badges remain 100% preserved.

---

## 4. Scope Grounding & Call Sites

- **In-Scope**:
  - `ZoneGaugeBar.kt`: Active segment vertical elevation ($1.6\times$), rounded geometry, and needle height synchronization.
  - `SensorFieldView.kt`: Pass scaled `trackHeight` and `batteryHeight` when `!fieldState.showValue`.
  - `PedalPowerBalanceBar.kt` & `WPrimeBatteryGaugeBar.kt`: Support customizable track/capsule height.
  - `SensorFieldZoneGaugeTest.kt`: Unit tests verifying active segment height calculation and bar-only sizing.
- **Out-of-Scope**: Altering zone cutoff calculation algorithms or adding multi-needle indicators.

---

## 5. Invariants & Risk Assessment

- **Risk Level**: **LOW** (UI rendering refinement).
- **Invariants**: 100% backwards-compatible composable signatures with sensible defaults; zero impact on sensor streaming or database schemas.
