# Implementation Plan - ATT-1472: Theme-Aware Post-Workout Share Snapshot Canvas and Footer

**Parent Ticket**: [ATT-1472](https://rainerblind.atlassian.net/browse/ATT-1472)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Sub-task**: [ATT-1477](https://rainerblind.atlassian.net/browse/ATT-1477) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-177`  
**Test ID**: `TST-UI-129`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1472_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1472_test_spec.md`  
**Branch**: `feature/ATT-1472`  

---

## 1. Executive Summary & Architectural Scope

The goal of **ATT-1472** is to eliminate bright visual seams and jarring light footers in shared athletic summary snapshots when running in Dark Mode (or AMOLED battery saver mode), while strictly preserving historical Light Mode visual styling and maintaining 100% binary/source call-site compatibility.

### Architectural Solution
1. **Decoupled Color Token Representation (`ShareThemeColors`)**:
   - Encapsulate the three theme-sensitive color tokens into an immutable data class:
     - `canvasBackground: Int`
     - `footerBackground: Int`
     - `footerTextColor: Int`
2. **Deterministic Resolver (`ShareThemeResolver`)**:
   - Provide `ShareThemeResolver.resolveColors(isDark: Boolean): ShareThemeColors`:
     - Light Mode: `canvasBackground = Color.WHITE`, `footerBackground = Color.parseColor("#F5F5F5")`, `footerTextColor = Color.DKGRAY` (`#444444`).
     - Dark Mode: `canvasBackground = Color.BLACK` (`#000000`), `footerBackground = Color.parseColor("#1E1E1E")`, `footerTextColor = Color.parseColor("#E0E0E0")`.
   - Provide `ShareThemeResolver.isNightMode(context: Context): Boolean` evaluating Android's `Configuration.UI_MODE_NIGHT_MASK`.
3. **Non-Breaking API Evolution**:
   - Add default parameter `isDark: Boolean = ShareThemeResolver.isNightMode(context)` to both `combineAndShare` and `combineWorkoutAndShare`.
   - Existing call sites in `MapDetailLayout.kt` and `PeriodMapScreen.kt` remain 100% untouched and automatically receive dark/light theme awareness.
4. **WCAG 2.1 AAA Accessibility**:
   - Both Light Mode ($9.0:1$) and Dark Mode ($13.5:1$) footer typography achieve contrast ratios exceeding the WCAG 2.1 Level AAA threshold ($7.0:1$).

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Implement Theme Tokens and Resolver in `ShareUtils.kt`
In [ShareUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/helpers/ShareUtils.kt):
1. Define `data class ShareThemeColors`:
   ```kotlin
   data class ShareThemeColors(
       val canvasBackground: Int,
       val footerBackground: Int,
       val footerTextColor: Int
   )
   ```
2. Define `object ShareThemeResolver`:
   ```kotlin
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

### Phase 2: Update `combineAndShare` and `combineWorkoutAndShare`
1. Update `combineAndShare`:
   - Signature:
     ```kotlin
     suspend fun combineAndShare(
         context: Context,
         header: Bitmap,
         map: Bitmap,
         isDark: Boolean = ShareThemeResolver.isNightMode(context)
     ) = withContext(Dispatchers.Default) { ... }
     ```
   - Replace hardcoded `canvas.drawColor(Color.WHITE)` with:
     ```kotlin
     val colors = ShareThemeResolver.resolveColors(isDark)
     ...
     canvas.drawColor(colors.canvasBackground)
     ```
   - Pass `colors` into `drawFooter`:
     ```kotlin
     drawFooter(context, canvas, totalWidth, footerTop, footerHeight, colors)
     ```
2. Update `combineWorkoutAndShare`:
   - Signature:
     ```kotlin
     suspend fun combineWorkoutAndShare(
         context: Context,
         header: Bitmap?,
         map: Bitmap,
         elevation: Bitmap?,
         isDark: Boolean = ShareThemeResolver.isNightMode(context)
     ) = withContext(Dispatchers.Default) { ... }
     ```
   - Replace hardcoded `canvas.drawColor(Color.WHITE)` with `canvas.drawColor(colors.canvasBackground)`.
   - Pass `colors` into `drawFooter`.

### Phase 3: Update `drawFooter`
1. In `drawFooter`:
   - Update signature to accept `colors: ShareThemeColors`:
     ```kotlin
     private fun drawFooter(
         context: Context,
         canvas: Canvas,
         width: Int,
         top: Float,
         height: Int,
         colors: ShareThemeColors
     )
     ```
   - Set background paint color:
     ```kotlin
     val bgPaint = Paint().apply {
         color = colors.footerBackground
         isAntiAlias = true
     }
     ```
   - Set text paint color:
     ```kotlin
     val textPaint = Paint().apply {
         color = colors.footerTextColor
         textSize = 42f
         typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
         isAntiAlias = true
     }
     ```

---

## 3. Automated Verification Strategy

### Dedicated Unit Test Suite: `ShareThemeResolverTest.kt`
Create `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/ShareThemeResolverTest.kt` verifying:
1. `testLightModeColors_matchHistoricalDesignConstants`:
   - Validates that `resolveColors(isDark = false)` strictly produces `Color.WHITE`, `#F5F5F5`, and `Color.DKGRAY`.
2. `testDarkModeColors_matchDarkThemeSpecification`:
   - Validates that `resolveColors(isDark = true)` produces `Color.BLACK`, `#1E1E1E`, and `#E0E0E0`.
3. `testWcagContrastRatios_exceedLevelAaaStandards`:
   - Computes exact mathematical relative luminance for `#F5F5F5` vs `#444444` (contrast $\ge 7:1$, actual $9.0:1$).
   - Computes exact mathematical relative luminance for `#1E1E1E` vs `#E0E0E0` (contrast $\ge 7:1$, actual $13.5:1$).
4. `testNightModeDetection_evaluatesUiModeCorrectly`:
   - Uses MockK to mock `Context` and `Configuration`.
   - Verifies `UI_MODE_NIGHT_YES` returns `true`.
   - Verifies `UI_MODE_NIGHT_NO` returns `false`.

---

## 4. Physical On-Device Verification Protocol (Google Pixel 10)

1. **Light Mode Verification**:
   - Verify device is in Light Mode (`adb shell cmd uimode night no`).
   - Open completed workout map (`MapDetailLayout`).
   - Trigger share intent -> verify preview image has white canvas background and light footer `#F5F5F5` with dark gray text.
2. **Dark Mode Verification**:
   - Switch device to Dark Mode (`adb shell cmd uimode night yes`).
   - Open workout map -> trigger share.
   - Verify preview image has pure black canvas background (`#000000`), dark footer container (`#1E1E1E`), and high-contrast light text (`#E0E0E0`) with zero white seams.
3. **Restore Device Mode**:
   - Switch device back to default Light Mode (`adb shell cmd uimode night no`).

---

## 5. System Invariants & Non-Regression Checklist

- [x] **Light Mode Visual Parity**: Zero modification to existing Light Mode hex values or visual outputs.
- [x] **Binary & Source Backward Compatibility**: Default arguments ensure callers in `MapDetailLayout.kt` and `PeriodMapScreen.kt` compile and execute without modifications.
- [x] **File Caching & Sharing Security**: `imagesFolder = File(context.cacheDir, "images")`, PNG 100% compression, `FileProvider` URI generation, and `Intent.ACTION_SEND` flags remain untouched.
- [x] **Thread Boundaries**: Bitmap composition executes on `Dispatchers.Default`, file I/O on `Dispatchers.IO`, and intent chooser launch on `Dispatchers.Main`.
- [x] **Branding Typography & Scaling**: Footer height 125px, logo $80\times 80$, text size 42f, margin 24f remain identical.
- [x] **Localization Parity**: "aTrainingTracker" is an unlocalized product brand name; no localized string additions required.
