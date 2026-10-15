# Stage 1: Problem Domain & Root Cause Analysis - ATT-3391: Extend elevation profile slope shading and legend to use full 7-color zone palette

**Ticket**: [ATT-3391](https://rainerblind.atlassian.net/browse/ATT-3391)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Problem Domain

In `ElevationProfile.kt`, terrain slope gradient shading and the interactive `GradeLegend` classify elevation incline into 6 discrete color bands:
- `< 2%`: `TTColor.Zone1` (Chartreuse)
- `2% - 5%`: `TTColor.Zone2` (Green)
- `5% - 10%`: `TTColor.Zone3` (Orange)
- `10% - 15%`: `TTColor.Zone4` (Red)
- `15% - 20%`: `TTColor.Zone5` (Dark Violet)
- `>= 20%`: `Color.Black`

With the implementation of the canonical 7-zone Coggan palette in ATT-3184 (`TTColor.Zone6` = Dark Chestnut Brown `#4E342E`, `TTColor.Zone7` = Black `#000000`), the elevation profile can now be seamlessly harmonized with the rest of the application's visual design system.

Currently, all climbs of $20\%$ or steeper are lumped into a single black band. This loses critical visual nuance on extreme climbs (e.g. separating very steep $20\%-25\%$ mountain ramps from brutal $>25\%$ wall gradients). Furthermore, `ElevationProfile.kt` implements the slope color mapping inline within a private `when` block rather than exposing a testable helper, and `GradeLegend` does not render a visible outline for the pure black swatch, causing it to blend into dark mode backgrounds.

---

## 2. Chesterton's Fence Forensic Archaeology

### 2.1 Historical Precedent & Origin
- **Elevation Profile Gradient Shading**: Introduced to provide immediate visual feedback on elevation profile charts across workout aftermath, route detail screens, and live cockpit elevation views.
- **Climb Gradient Shading (`REQ-UI-352`)**: Sprint 2026-42.1 (ATT-3343) introduced `climbGradientShading = true` and `getClimbGradeColor` in `LiveClimbSheet.kt` to support real-time climb sheet HUDs with standard 6-band cycling climbing thresholds ($<3\%, 3-6\%, 6-9\%, 9-12\%, 12-20\%, \ge 20\%$) backed by unit test `ElevationProfileClimbShadingTest.kt`.
- **Chesterton's Fence Finding**: The general `ElevationProfile` slope shading ($<2\%, 2-5\%, 5-10\%, 10-15\%, 15-20\%, \ge 20\%$) was originally constrained to 6 colors because the application's global palette only defined 5 training zone colors plus generic `Color.Black`. Now that ATT-3184 has established `TTColor.Zone6` and `TTColor.Zone7`, extending `ElevationProfile` slope shading to 7 zones respects both design continuity and domain fidelity.
- **Invariance Mandate**: `getClimbGradeColor` in `LiveClimbSheet.kt` belongs to `REQ-UI-352` (Climb Sheet) and MUST NOT be broken, preserving 100% regression test passes in `ElevationProfileClimbShadingTest.kt`.

---

## 3. Scope & Target Architecture (SWE.1 / SWE.2)

### 3.1 7-Band Elevation Gradient Threshold Scale
The elevation slope resolver will be formalized as a top-level, testable utility function `getElevationGradeColor(gradePercent: Double): Color`:
1. **Zone 1 (`TTColor.Zone1` Chartreuse `#7FFF00`)**: $\text{Grade} < 2.0\%$ (Flat / rolling)
2. **Zone 2 (`TTColor.Zone2` Green `#00E676`)**: $2.0\% \le \text{Grade} < 5.0\%$ (Gentle incline)
3. **Zone 3 (`TTColor.Zone3` Orange `#FFA500`)**: $5.0\% \le \text{Grade} < 10.0\%$ (Moderate climb)
4. **Zone 4 (`TTColor.Zone4` Red `#FF0000`)**: $10.0\% \le \text{Grade} < 15.0\%$ (Steep climb)
5. **Zone 5 (`TTColor.Zone5` Dark Violet `#8A2BE2`)**: $15.0\% \le \text{Grade} < 20.0\%$ (Very steep climb)
6. **Zone 6 (`TTColor.Zone6` Dark Chestnut Brown `#4E342E`)**: $20.0\% \le \text{Grade} < 25.0\%$ (Extreme ramp)
7. **Zone 7 (`TTColor.Zone7` Black `#000000`)**: $\text{Grade} \ge 25.0\%$ (Monster grade / Wall)

Negative grades (descents) have grade $< 2.0\%$ and correctly resolve to `TTColor.Zone1`.

### 3.2 GradeLegend Harmonization & Dark Mode Accessibility
- `GradeLegend` in `ElevationProfile.kt` is expanded from 6 to 7 legend items:
  - `< 2%` (`TTColor.Zone1`)
  - `2 - 5%` (`TTColor.Zone2`)
  - `5 - 10%` (`TTColor.Zone3`)
  - `10 - 15%` (`TTColor.Zone4`)
  - `15 - 20%` (`TTColor.Zone5`)
  - `20 - 25%` (`TTColor.Zone6`)
  - `> 25%` (`TTColor.Zone7`)
- In `GradeLegendItem`, `Surface` swatch renders a subtle border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` when displaying `TTColor.Zone7`, ensuring accessibility and visual contrast against dark backgrounds.
- Legend container width (`widthIn(max = 300.dp)`) gracefully accommodates 7 wrapped items across small and large displays.

---

## 4. Bounded Impact & Verification Strategy

- **Impact Surface**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
  - Net-new unit test: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`
- **Zero Schema Mutations**: Pure visual presentation and rendering layer.
- **Verification**:
  - Unit tests asserting exact boundary thresholds ($-5.0\%, 0.0\%, 1.99\%, 2.0\%, 4.99\%, 5.0\%, 9.99\%, 10.0\%, 14.99\%, 15.0\%, 19.99\%, 20.0\%, 24.99\%, 25.0\%, 35.0\%$).
  - Full regression test suite (`./gradlew testDebugUnitTest`).
