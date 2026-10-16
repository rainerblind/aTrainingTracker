# Stage 1 Analysis: ATT-3696 - Make Zone 6 color (TTColor.Zone6 / R.color.zone_6) distinctly browner and lighter for clear visual separation from Zone 7 (Black)

**Ticket**: [ATT-3696](https://rainerblind.atlassian.net/browse/ATT-3696)  
**Sub-task**: `ATT-3835` (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `TBD (Assigned on Completion per Rule 14)`  
**Active Sprint**: `2026-42.6` (`sprint/2026-42.6`)  
**Branch**: `feature/ATT-3696`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

On physical OLED device screens (Google Pixel 10), **Zone 6** (`TTColor.Zone6` / `R.color.zone_6`) is currently defined as `#4E342E` (Material Brown 800, *"Dark Chestnut Brown"*).

Because `#4E342E` has a very low relative luminance ($L \approx 0.036$) and low chromatic saturation ($S \approx 26\%$), it renders nearly black on OLED displays and is visually indistinguishable from **Zone 7** (`TTColor.Zone7` / `R.color.zone_7` = `#000000` Pure Black) in:
1. Workout Summary & Workout Details power zone distribution bars, columns, and histograms (`ZoneDistributionStackedBar`, `ZoneDistributionColumnChart`, `ZoneDistributionHistogram`).
2. Live Tracking cockpit zone gauges (`ZoneGaugeBar`) and sensor tile zone background/border highlights (`SensorFieldState`).
3. Zone Settings configuration cards and preview selector pills (`ZonesSettingsActivity`, `ZoneCalculatorDialog`).
4. Elevation profile extreme gradient shading (`20%..25%` grade band in `ElevationProfile.kt`).

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Colorimetric Audit of `#4E342E` vs `#8B5A2B`**:
   - Current `#4E342E` (`R=78, G=52, B=46`):
     - Relative luminance $L \approx 0.036$.
     - Contrast ratio against Zone 7 Black (`#000000`, $L = 0.0$): $(0.036 + 0.05) / (0.0 + 0.05) = 1.72 : 1$ — far below human perceptual separability on small UI elements.
   - Target `#8B5A2B` (`R=139, G=90, B=43`, Warm Earthy / Saddle Ochre Brown):
     - Relative luminance $L \approx 0.122$ (over $3.3\times$ brighter than `#4E342E`).
     - Contrast ratio against Zone 7 Black (`#000000`): $(0.122 + 0.05) / 0.05 = \mathbf{3.44 : 1}$ (exceeds the WCAG 2.1 AA $3.0:1$ non-text graphical object contrast threshold!).
     - Clear hue and lightness separation from Zone 5 (`#9400D3` Dark Violet) and Zone 3 (`#FFA500` Orange).
2. **Single Source of Truth Audit Across Codebase**:
   - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` (line 200): `val Zone6 = Color(0xFF4E342E)`
   - `app/src/main/res/values/color.xml` (line 100): `<color name="zone_6">#4E342E</color>`
   - All downstream consumers (`ZoneGaugeMath.POWER_SEGMENT_COLORS`, `TelemetryZoneMath`, `ZoneDistributionCalculator`, `ElevationProfile`, `ZonesSettingsActivity`, `ZoneCalculatorDialog`, `TrackingViewModel`) reference either `TTColor.Zone6` or `R.color.zone_6`.
   - Two unit test files assert the literal hex value `Color(0xFF4E342E)`:
     - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneDisplayTest.kt` (line 133)
     - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneGaugeTest.kt` (line 102)

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update `TTColor.Zone6` in `Color.kt` and `<color name="zone_6">` in `color.xml` from `#4E342E` to `#8B5A2B` (Warm Earthy Brown).
  * Update `SensorFieldZoneDisplayTest.kt` and `SensorFieldZoneGaugeTest.kt` to assert `#8B5A2B` and verify `TTColor.Zone6` vs `TTColor.Zone7` luminance separation.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `Zone1`..`Zone5` or `Zone7` colors.
  * Modifying zone calculation math or thresholds.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-392`, amending the Zone 6 color hex value introduced in `REQ-UI-370` (`ATT-3184`, 7-zone Coggan cycling power) and `REQ-UI-371` (`ATT-3391`, elevation profile 7-zone gradient shading). Targets `Color.kt`, `color.xml`, `SensorFieldZoneDisplayTest.kt`, and `SensorFieldZoneGaugeTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-42.4` (`ATT-3184` commit `0da639ba`, `ATT-3391` commit `ca77b115`) when expanding the 5-zone palette to 7 zones (`Zone6 = #4E342E`, `Zone7 = #000000`).
* **Root Reason for Existing Formulation**: Material Brown 800 (`#4E342E`) was initially picked as a standard Material Design brown swatch without measuring its non-text contrast ratio ($1.72:1$) against adjacent `#000000` (Zone 7) on OLED displays.
* **Preservation of Core Invariants**: Updating `TTColor.Zone6` and `R.color.zone_6` to `#8B5A2B` preserves 100% parity between XML and Compose tokens while boosting contrast against `#000000` from $1.72:1$ to $3.44:1$ ($\ge 3.0:1$ WCAG 2.1 AA graphical contrast).

---

## 5. Architectural Strategy & High-Level Solution

1. **Theme & Resource Token Update**:
   - In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`, update `val Zone6 = Color(0xFF8B5A2B) // Warm Earthy Brown`.
   - In `app/src/main/res/values/color.xml`, update `<color name="zone_6">#8B5A2B</color> <!-- Warm Earthy Brown -->`.
2. **Unit & Contract Test Synchronization**:
   - Update `SensorFieldZoneDisplayTest.kt` and `SensorFieldZoneGaugeTest.kt` to expect `Color(0xFF8B5A2B)`, and add an explicit contract test asserting parity between `TTColor.Zone6` (`0xFF8B5A2B`) and `res/values/color.xml` (`#8B5A2B`) as well as $\ge 3.0:1$ relative luminance contrast against `TTColor.Zone7` (`0xFF000000`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Strict 1:1 hex parity between `TTColor.Zone6` in `Color.kt` and `<color name="zone_6">` in `color.xml`.
  2. Non-text contrast ratio between `TTColor.Zone6` and `TTColor.Zone7` $\ge 3.0:1$.
  3. Zero regression in existing features and unit tests.
* **Risk Rating**: **LOW** — Pure color token refinement across two canonical files and two unit tests.
