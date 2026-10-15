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

### 3.1 7-Band Elevation Gradient Threshold Scale & Exact Interval Semantics
The elevation slope resolver will be formalized as a top-level, testable utility function `getElevationGradeColor(gradePercent: Double): Color`:
1. **Zone 1 (`TTColor.Zone1` Chartreuse `#7FFF00`)**: $\text{Grade} < 2.0\%$ (Flat, rolling, and descents)
2. **Zone 2 (`TTColor.Zone2` Green `#00E676`)**: $2.0\% \le \text{Grade} < 5.0\%$ (Gentle incline)
3. **Zone 3 (`TTColor.Zone3` Orange `#FFA500`)**: $5.0\% \le \text{Grade} < 10.0\%$ (Moderate climb)
4. **Zone 4 (`TTColor.Zone4` Red `#FF0000`)**: $10.0\% \le \text{Grade} < 15.0\%$ (Steep climb)
5. **Zone 5 (`TTColor.Zone5` Dark Violet `#8A2BE2`)**: $15.0\% \le \text{Grade} < 20.0\%$ (Very steep climb)
6. **Zone 6 (`TTColor.Zone6` Dark Chestnut Brown `#4E342E`)**: $20.0\% \le \text{Grade} < 25.0\%$ (Extreme ramp)
7. **Zone 7 (`TTColor.Zone7` Black `#000000`)**: $\text{Grade} \ge 25.0\%$ (Monster grade / Wall)

### 3.2 Explicit Descent & Negative Slope Handling
In endurance sports (cycling and trail running), elevation profile gradient coloring maps climbing effort and physiological resistance. All grades $< 2.0\%$ (including $0.0\%$, slight false flats, and all negative descent slopes such as $-5\%$ or $-15\%$) map to **Zone 1** (`TTColor.Zone1`). Downhills represent gravity-assisted riding with zero upward climbing resistance. This design choice aligns with industry standards (Garmin, Wahoo, Strava) where elevation profiles emphasize climb severity and treat descents as baseline recovery terrain.

### 3.3 Floating-Point Determinism
Threshold evaluations use standard sequential half-open intervals $[x_{n-1}, x_n)$ in Kotlin:
```kotlin
fun getElevationGradeColor(gradePercent: Double): Color {
    return when {
        gradePercent < 2.0 -> TTColor.Zone1
        gradePercent < 5.0 -> TTColor.Zone2
        gradePercent < 10.0 -> TTColor.Zone3
        gradePercent < 15.0 -> TTColor.Zone4
        gradePercent < 20.0 -> TTColor.Zone5
        gradePercent < 25.0 -> TTColor.Zone6
        else -> TTColor.Zone7
    }
}
```
This guarantees deterministic evaluation across exact boundaries (e.g. `20.0%` maps strictly to Zone 6, `25.0%` maps strictly to Zone 7).

### 3.4 GradeLegend Harmonization & Accessibility (WCAG 2.1 AA)
- `GradeLegend` in `ElevationProfile.kt` is expanded from 6 to 7 legend items:
  - `< 2%` (`TTColor.Zone1`)
  - `2 - 5%` (`TTColor.Zone2`)
  - `5 - 10%` (`TTColor.Zone3`)
  - `10 - 15%` (`TTColor.Zone4`)
  - `15 - 20%` (`TTColor.Zone5`)
  - `20 - 25%` (`TTColor.Zone6`)
  - `> 25%` (`TTColor.Zone7`)
- **Contrast Audit**: In `GradeLegendItem`, text labels are placed adjacent to the swatch (not inside it). The label text uses `MaterialTheme.colorScheme.onSurface` rendered on `MaterialTheme.colorScheme.surface`, providing a contrast ratio $> 10:1$ in both light and dark themes (exceeding WCAG 2.1 AA 4.5:1 requirement).
- **Dark Mode Swatch Border**: For `TTColor.Zone7` (`#000000`) and dark swatches, `GradeLegendItem` renders a subtle outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` around the swatch surface, preventing optical blending into dark surfaces.
- Legend container width (`widthIn(max = 300.dp)`) gracefully accommodates 7 wrapped items across small and large displays.

---

## 4. Cache Invalidation & Call Site Audit

### 4.1 Storage & Cache Invalidation Audit
An exhaustive audit of the codebase confirms:
- Elevation profiles are rendered **strictly on-the-fly** via Jetpack Compose `Canvas` drawing directly from in-memory `List<PathPoint>`.
- There are **no persistent cached bitmaps**, **no stored raster files**, and **no SQLite elevation image tables**.
- No PDF or external image export mechanisms depend on legacy 6-band slope arrays.
- Consequently, updating `ElevationProfile.kt` introduces **zero cache invalidation risks or migration artifacts**: all elevation charts throughout the app immediately reflect the new 7-color scale on their next recomposition.

### 4.2 Call Site Audit
The updated slope coloring applies to all standard elevation profile renderings:
- `ElevationProfile.kt` (Workout Aftermath summary card, Route Detail previews, Navigation HUD).
- `SensorGridScreen.kt` (Real-time live elevation profile in tracking cockpit).
- `LiveClimbSheet.kt` retains its dedicated `climbGradientShading = true` (`getClimbGradeColor`) according to `REQ-UI-352`.

---

## 5. Bounded Impact & Verification Strategy

- **Impact Surface**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
  - Net-new unit test: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`
- **Zero Schema Mutations**: Pure visual presentation and rendering layer.
- **Verification**:
  - Unit tests asserting exact boundary thresholds ($-10.0\%, -0.1\%, 0.0\%, 1.99\%, 2.0\%, 4.99\%, 5.0\%, 9.99\%, 10.0\%, 14.99\%, 15.0\%, 19.99\%, 20.0\%, 24.99\%, 25.0\%, 35.0\%$).
  - Full regression test suite (`./gradlew testDebugUnitTest`).
