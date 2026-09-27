# Problem Domain & Root Cause Analysis - ATT-1472: [Share] Theme-aware post-workout share snapshot canvas and footer

**Parent Ticket**: [ATT-1472](https://rainerblind.atlassian.net/browse/ATT-1472)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Sub-task**: [ATT-1475](https://rainerblind.atlassian.net/browse/ATT-1475) (`[Subtask] [Analysis]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement Target**: `REQ-UI-177`  
**Test Spec Target**: `TST-UI-129`  
**Branch**: `feature/ATT-1472`  

---

## 1. Executive Summary & Problem Domain Comprehension

### 1.1 Context & User Motivation
aTrainingTracker offers comprehensive post-workout and period summary sharing functionality via `ShareUtils.kt`. Users can share an assembled composite snapshot containing the workout header metrics, interactive map route snapshot, elevation profile, and a branding footer ("aTrainingTracker" logo and typography).

Under Epic [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157), the application has introduced high-contrast Dark Mode and AMOLED Pure Black themes across tracking cockpits, workout lists, map styling, and settings. In dark mode, all rendered UI composables (workout header, elevation profile, Google Map tiles) render with dark/black surfaces.

### 1.2 The Bug (Symptom vs. Cause)
When an athlete generates a shareable snapshot in Dark Mode:
1. The composable graphics layers (`headerLayer`, `elevationLayer`) and the Google Map snapshot render with dark backgrounds (`#000000` / `#1B1B1F`).
2. However, `ShareUtils.kt` hardcodes light colors for the underlying composition canvas and footer:
   - Canvas background: `canvas.drawColor(Color.WHITE)` in `combineAndShare` (line 49) and `combineWorkoutAndShare` (line 85).
   - Footer background: `Color.parseColor("#F5F5F5")` in `drawFooter` (line 122).
   - Footer text: `Color.DKGRAY` in `drawFooter` (line 136).
3. As a result, the stitched composite image contains an unsightly, jarring bright white/light-gray footer bar (`#F5F5F5`) and bright white seam lines if section widths vary, completely breaking the dark mode visual aesthetic of the exported athletic summary.

---

## 2. Call-Site & Architecture Audit

### 2.1 Call Sites of Share Utilities
Grep search reveals the following call sites in production code:
1. **`MapDetailLayout.kt:136`**:
   ```kotlin
   combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp)
   ```
   Invoked when the athlete taps the floating share action button in `MapDetailLayout` (used by `TrackOnMapScreen`, `RouteOnMapScreen`, `SegmentOnMapScreen`, and `WorkoutClusterHeatmapScreen`).
2. **`PeriodMapScreen.kt:408`**:
   ```kotlin
   combineAndShare(context, headerBitmap, mapBitmap)
   ```
   Invoked when sharing period summaries (weekly, monthly, annual aggregates) with heatmaps.

### 2.2 Component Hierarchy & Responsibilities
- `combineAndShare(context: Context, header: Bitmap, map: Bitmap, isDark: Boolean = ...)`:
  Composites header and map snapshots, computes dimensions, fills background canvas, appends footer, writes PNG to cache, and dispatches `ACTION_SEND` intent.
- `combineWorkoutAndShare(context: Context, header: Bitmap?, map: Bitmap, elevation: Bitmap?, isDark: Boolean = ...)`:
  Composites optional header, map snapshot, optional elevation profile, computes dynamic height, fills canvas, appends footer, writes PNG to cache, and dispatches intent.
- `drawFooter(context: Context, canvas: Canvas, width: Int, top: Float, height: Int, isDark: Boolean = ...)`:
  Renders branding banner background rectangle, scales `R.drawable.logo_512`, and draws application title text.

---

## 3. Detailed Root Cause Analysis & Technical Solution

### 3.1 Hardcoded Values in `ShareUtils.kt`
Lines 49, 85, 122, and 136 in `ShareUtils.kt`:
```kotlin
// In combineAndShare / combineWorkoutAndShare:
canvas.drawColor(Color.WHITE)

// In drawFooter:
bgPaint.color = Color.parseColor("#F5F5F5")
textPaint.color = Color.DKGRAY
```

### 3.2 Proposed Decoupled Color Resolution Architecture
To support robust automated unit testing and maintain strict separation of concerns, the theme resolution should be encapsulated in a dedicated data structure and resolver:

```kotlin
data class ShareThemeColors(
    val canvasBackground: Int,
    val footerBackground: Int,
    val footerTextColor: Int
)

object ShareThemeResolver {
    val Light = ShareThemeColors(
        canvasBackground = Color.WHITE,
        footerBackground = Color.parseColor("#F5F5F5"),
        footerTextColor = Color.DKGRAY
    )

    val Dark = ShareThemeColors(
        canvasBackground = Color.BLACK,
        footerBackground = Color.parseColor("#1E1E1E"),
        footerTextColor = Color.parseColor("#E0E0E0")
    )

    fun resolveColors(isDark: Boolean): ShareThemeColors {
        return if (isDark) Dark else Light
    }

    fun isNightMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
```

### 3.3 Contrast Ratio & WCAG Compliance Analysis
1. **Light Mode**:
   - Footer Background: `#F5F5F5` (normalized relative luminance $L_1 \approx 0.91$)
   - Footer Text: `Color.DKGRAY` (`#444444`, $L_2 \approx 0.056$)
   - Contrast Ratio: $(0.91 + 0.05) / (0.056 + 0.05) \approx 9.0:1$
   - Compliance: Exceeds WCAG 2.1 Level AAA (7:1 threshold).
2. **Dark Mode**:
   - Footer Background: `#1E1E1E` ($L_2 \approx 0.0091$)
   - Footer Text: `#E0E0E0` ($L_1 \approx 0.751$)
   - Contrast Ratio: $(0.751 + 0.05) / (0.0091 + 0.05) \approx 13.5:1$
   - Compliance: Exceeds WCAG 2.1 Level AAA (7:1 threshold).
3. **Canvas Background**:
   - Light Mode: `Color.WHITE` (`#FFFFFF`), seamlessly matching light card backgrounds.
   - Dark Mode: `Color.BLACK` (`#000000`), seamlessly matching pure black AMOLED and dark map canvas borders.

---

## 4. System Invariants & Preservation Rules

The following core system invariants MUST NOT be altered or regressed:
1. **Light Mode Visual Identity Invariant**:
   Existing light mode snapshots must retain identical colors (`Color.WHITE`, `#F5F5F5`, `Color.DKGRAY`) with zero visual regression.
2. **Binary & Source Call-Site Compatibility**:
   `combineAndShare` and `combineWorkoutAndShare` must provide default argument `isDark: Boolean = ShareThemeResolver.isNightMode(context)`. Existing call sites in `MapDetailLayout.kt` and `PeriodMapScreen.kt` must remain fully operational without requiring mandatory signature changes.
3. **File Output & Sharing Pipeline Invariant**:
   PNG compression format (100% quality), file naming (`workout_summary.png`, `period_summary.png`), cache directory isolation (`context.cacheDir/images`), `FileProvider` authority, and chooser intent dispatch on `Dispatchers.Main` must remain unaltered.
4. **Hardware Bitmap Safety Invariant**:
   `ensureSoftwareBitmap()` must continue to safeguard against `HARDWARE` config bitmaps to avoid software Canvas crash.
5. **Branding Geometry Invariant**:
   Footer height (125px), logo size ($80\times 80$), horizontal margin (24f), and text size (42f) must remain strictly preserved.

---

## 5. Requirement & Test Specification Mapping

### Requirement Mapping
- **Requirement ID**: `REQ-UI-177`
- **Title**: *Theme-Aware Post-Workout and Period Share Snapshot Canvas and Footer.*
- **Scope**: `ShareUtils.kt`

### Test Specification Mapping
- **Test ID**: `TST-UI-129`
- **Title**: *Theme-Aware Share Snapshot Color Harmonization Verification.*
- **Scope**: Unit verification of `ShareThemeResolver` (Light and Dark color mappings, WCAG contrast compliance, default parameter fallback) and on-device visual validation on Google Pixel 10.

---

## 6. Risk Assessment & Technical Recommendation

- **Technical Complexity**: LOW. Changes are localized to `ShareUtils.kt` and a new unit test suite.
- **Regression Risk**: LOW. Light mode paths retain 100% identical color constants. Dark mode replaces hardcoded light values with harmonious dark values.
- **Architectural Recommendation**: **RECOMMEND PASS**. Proceed directly to Stage 2 (Test Specification & Requirements Definition).
