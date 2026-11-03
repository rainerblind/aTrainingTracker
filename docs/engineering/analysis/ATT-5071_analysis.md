# Stage 1 Analysis: ATT-5071 - Profile & Zones: Zone visualization preview tile (HR / Power) is vertically clipped/collapsed

**Ticket**: [ATT-5071](https://rainerblind.atlassian.net/browse/ATT-5071)  
**Sub-task**: [ATT-5174](https://rainerblind.atlassian.net/browse/ATT-5174) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5071`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
* On the *Profil & Zonen* screen (`AthleteProfileScreen.kt`), under *Zonendarstellung -> Vorschau* across the *Lauf HF*, *Rad HF*, and *Rad Leistung* tabs (hosted via `ZoneProfileTabContent` / `ZonesSettingsActivity.kt`), the live preview sensor tile (`SensorFieldView`) at the bottom is vertically collapsed and clipped.
* Only the top header label (*"Power"* or *"Heart Rate"*) is partially visible, while the numeric telemetry value, unit label, zone gauge bar, and left/right indicator strips are cut off.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**:
  * In `ZonesSettingsActivity.kt:658-678`, `SensorFieldView` is embedded inside `SettingsScreenContent`'s `Column(Modifier.verticalScroll(scrollState))` wrapped in an unconstrained `Box(Modifier.fillMaxWidth().padding(vertical = 4.dp))` and `Surface`.
  * `SensorFieldView`'s internal layout contract (`REQ-UI-438`, introduced in `ATT-4878`) applies `Card(Modifier.fillMaxWidth().fillMaxHeight())`, inner `Row(Modifier.fillMaxWidth().fillMaxHeight())`, and inner telemetry `Column(Modifier.fillMaxWidth().weight(1f))`.
  * Under `verticalScroll`, incoming vertical constraints are unbounded (`maxHeight = Constraints.Infinity`). When measured against unbounded height in Compose, `fillMaxHeight()` collapses to `minHeight = 0`, leaving zero vertical space for `weight(1f)`. Consequently, the telemetry value, units, gauge bar, and vertical indicator strips collapse to 0 height.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified hypothesis against `sprint/2026-45.1`: `SensorGridScreen.kt:741, 1081` encloses multi-column rows in `Row(Modifier.height(IntrinsicSize.Min))`, which provides bounded height. `ZonesSettingsActivity.kt` lacks `Modifier.height(IntrinsicSize.Min)` on the enclosing container.
  * Verified call-site audit: grep confirmed `ZonesSettingsActivity.kt:672` is the sole unconstrained call site of `SensorFieldView`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Enclose the `SensorFieldView` preview tile in `ZonesSettingsActivity.kt` within a container that applies `Modifier.height(IntrinsicSize.Min)`, restoring full visibility of the numeric value, units, zone gauge bar, and full-height indicator strips.
  2. Add automated contract/layout test ensuring standalone preview tile containers enforce `height(IntrinsicSize.Min)` to prevent future clipping regressions under scrollable containers (Rule 67 / Section 5.14).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Altering internal `SensorFieldView` height stretching or `.weight(1f)` behavior required by `REQ-UI-438` for cockpit grid rows.
  2. Modifying zone threshold calculations, DataStore persistence, or `ZoneCalculatorDialog` logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-UI-438` (*Vertical Card Height and Border Stretching in Cockpit Sensor Grid*) and targets `ZonesSettingsActivity.kt`.
* **Historical Origin & Commit Trace**: `ATT-4878` (commit `12682cfaf66a224127dbd5eb014ed1970f0f38e2`) introduced `Modifier.fillMaxHeight()` and inner `.weight(1f)` in `SensorFieldView` to match the tallest sibling card in cockpit rows.
* **Root Reason for Existing Formulation**: Equalizing row height required `SensorFieldView` to fill maximum container height. However, the preview container in `ZonesSettingsActivity.kt` was not updated with `Modifier.height(IntrinsicSize.Min)`.
* **Preservation of Core Invariants**: `SensorFieldView`'s internal layout contract for `REQ-UI-438` and `SensorGridScreen` equal-height behavior remain strictly untouched; only the caller container in `ZonesSettingsActivity.kt` is bounded.

---

## 5. Architectural Strategy & High-Level Solution
* **Preview Container Height Bounding (`ZonesSettingsActivity.kt`)**:
  * Apply `Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)` to the enclosing `Box` (or `Surface`) around `SensorFieldView` in `SettingsScreenContent`.
  * With `height(IntrinsicSize.Min)`, Compose measures the min intrinsic height of `SensorFieldView` (~96 dp), bounds the height, and allows `.fillMaxHeight()` and `.weight(1f)` to render the complete card with centered telemetry and flush indicator strips.
* **Verification Contract Test (`ZonesSettingsPreviewLayoutTest.kt`)**:
  * Verify `ZonesSettingsActivity.kt` enforces `height(IntrinsicSize.Min)` on the preview container wrapping `SensorFieldView`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Cockpit sensor grid layout (`SensorGridScreen.kt`) and multi-column equal-height behavior (`REQ-UI-438`) remain 100% regression-free.
  2. Monotonic zone editing, DataStore persistence, and 9-language translation parity remain invariant.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Pure Compose layout constraint bounding in `ZonesSettingsActivity.kt`, zero database or background service impact.
