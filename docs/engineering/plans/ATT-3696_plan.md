# Stage 3: Implementation Plan - ATT-3696: Make Zone 6 color (TTColor.Zone6 / R.color.zone_6) distinctly browner and lighter for clear visual separation from Zone 7 (Black)

**Ticket**: [ATT-3696](https://rainerblind.atlassian.net/browse/ATT-3696)  
**Sub-task**: `ATT-3837` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Requirement Mapping**: `REQ-UI-392`  
**Test Mapping**: `TST-UI-355`  
**Branch**: `feature/ATT-3696`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

`TTColor.Zone6` (`Color.kt`) and `<color name="zone_6">` (`color.xml`) are currently `#4E342E` (Material Brown 800, $L \approx 0.036$), which has only a $1.72:1$ contrast ratio against `TTColor.Zone7` (`#000000` Black) and appears nearly black on OLED screens. We will update both tokens to `#8B5A2B` (`Color(0xFF8B5A2B)`, Warm Earthy Brown, $L \approx 0.122$, $3.44:1$ contrast against `#000000`) and synchronize unit and contract tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-392` (*High-Contrast Warm Earthy Brown Zone 6 Color Token & Zone 7 Visual Separation*)
* **Test Mapping**: `TST-UI-355` (*Warm Earthy Brown Zone 6 Color Token, XML/Compose Parity & Zone 7 Luminance Contrast Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **1:1 XML & Compose Parity**: `TTColor.Zone6` and `<color name="zone_6">` remain strictly identical (`#8B5A2B`).
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Theme & Resource Color Definitions (`Color.kt`, `color.xml`)
* Update `TTColor.Zone6` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` (line 200) to `Color(0xFF8B5A2B) // Warm Earthy Brown`.
* Update `<color name="zone_6">` in `app/src/main/res/values/color.xml` (line 100) to `<color name="zone_6">#8B5A2B</color> <!-- Warm Earthy Brown -->`.

### Component 2: Unit & Contract Test Suite (`SensorFieldZoneDisplayTest.kt`, `SensorFieldZoneGaugeTest.kt`)
* Update `SensorFieldZoneDisplayTest.kt` to assert `Color(0xFF8B5A2B)` for `stateZ6.zoneColor`, verify `<color name="zone_6">#8B5A2B</color>` in `color.xml`, and assert WCAG 2.1 non-text contrast ratio $\ge 3.0:1$ against `TTColor.Zone7`.
* Update `SensorFieldZoneGaugeTest.kt` line 102 to use `Color(0xFF8B5A2B)`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `PowerZoneDistributionCard.kt`, `ZoneGaugeBar.kt`, `ZonesSettingsActivity.kt`, `ElevationProfile.kt`
* **Reused components**: Canonical `TTColor.Zone6` and `R.color.zone_6` tokens
* **Theme tokens**: `TTColor.Zone6 = Color(0xFF8B5A2B)` (`#8B5A2B`)
* **New one-off styles & justification**: None — updates the canonical `TTColor.Zone6` / `R.color.zone_6` design token.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `Color.kt` and `color.xml`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`
  * `app/src/main/res/values/color.xml`
* Changes: Replace `#4E342E` (`0xFF4E342E`) with `#8B5A2B` (`0xFF8B5A2B`) for Zone 6.

### Step 2: Update Unit & Contract Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneDisplayTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneGaugeTest.kt`
* Changes: Update `0xFF4E342E` assertions to `0xFF8B5A2B` and add XML parity + WCAG $\ge 3.0:1$ luminance contrast assertions against `TTColor.Zone7`.

### Step 3: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneDisplayTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneGaugeTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneLocalizationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by clean-room suite in Stage 5.
* **Rollback**: Branch isolation allows full revert without affecting `develop`.
