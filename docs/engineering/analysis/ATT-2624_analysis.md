# Stage 1 Analysis: ATT-2624 - Harmonize cockpit tile grid spacing between preview and tracking screen and refine thick border ergonomics

**Ticket**: [ATT-2624](https://atrainingtracker.atlassian.net/browse/ATT-2624)  
**Sub-task**: [ATT-2695](https://atrainingtracker.atlassian.net/browse/ATT-2695) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2624`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 review on physical device (Google Pixel 10), custom cockpit tile styling introduced in `ATT-2456` and `ATT-2457` was evaluated:
* **Observation 1 (Thick Border Collisions & Cramped Ergonomics)**:
  When increasing the border width/thickness slider (up to 4.0 dp), the visual appearance degrades significantly ("looks heavy, cramped, and bad"). Thick borders on adjacent tiles touch or collide with each other, especially when combined with rounded corners. The 0 dp or fixed 4 dp spacing between tiles does not provide adequate separation for prominent outlines, and text metrics inside tiles feel crowded against thick border strokes.
* **Observation 2 (Preview vs. Real-Screen Geometry Discrepancy)**:
  The distance/spacing between tiles in the *Advanced Tuning* settings live preview (`CockpitTypographySection.kt`) differs noticeably from the actual spacing on the live tracking screen (`SensorGridScreen.kt`). The preview must be a faithful 1:1 What-You-See-Is-What-You-Get (WYSIWYG) representation of the live tracking cockpit across all variants and slider configurations.

### Expected Behavior
1. **Unified Proportional Grid Spacing**:
   Tile grid spacing must dynamically scale with both border thickness and corner radius across both the live cockpit (`SensorGridScreen.kt`) and the settings live preview (`CockpitTypographySection.kt`) using a single shared formula in `SensorFieldStyle`. When border thickness increases, tiles must float with sufficient breathing room (e.g. 6–8 dp) instead of colliding.
2. **Ergonomic Inner Content Padding Compensation**:
   In both `SensorFieldView.kt` and `PreviewCockpitTile`, internal padding must be compensated so labels and numbers remain comfortably inset and legible without feeling pinched against thick strokes.
3. **100% WYSIWYG Parity**:
   The live preview in `CockpitTypographySection.kt` and the live tracking grid in `SensorGridScreen.kt` must share the exact same spacing and geometry calculations.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Preview Hardcoding in `CockpitTypographySection.kt`
In `CockpitTypographySection.kt` (lines 287–293), the preview tile row layout hardcodes:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(
        baseStyle.gridSpacing.coerceAtLeast(6.dp),
        Alignment.CenterHorizontally
    ),
    verticalAlignment = Alignment.CenterVertically
)
```
* Even when `CLASSIC_SEAMLESS` (baseline gridSpacing = 0.dp) or `OUTLINED_TILES` (baseline gridSpacing = 4.dp) is active, the preview forces `coerceAtLeast(6.dp)`.
* Consequently, an athlete configuring `CLASSIC_SEAMLESS` sees 6 dp spacing in the preview, but upon returning to `SensorGridScreen`, the spacing drops to 0 dp!
* Furthermore, this preview calculation completely ignores the dynamic slider values `sensorFieldBorderThickness` and `sensorFieldCornerRadius`.

### 2.2 Static Grid Spacing in `SensorGridScreen.kt`
In `SensorGridScreen.kt` (lines 175–176):
```kotlin
val isDefaultStyling = gridSpacing == 0.dp && fieldShape == RectangleShape
val effectiveSpacing = if (isDefaultStyling) activeVariantStyle.gridSpacing else gridSpacing
```
* `effectiveSpacing` is drawn strictly from `activeVariantStyle.gridSpacing` (static preset values: 0 dp, 4 dp, 6 dp, 8 dp).
* When an athlete moves the `Border Thickness` slider from 1.0 dp to 4.0 dp or alters `Corner Radius`, `SensorGridScreen` does not adjust the inter-tile spacing at all.
* On adjacent cards with a 3.0 dp or 4.0 dp border, adjacent tiles with 0 dp or 4 dp spacing physically bump together or create cramped, double-thickness visual borders that touch corner curves awkwardly.

### 2.3 Preview Tile Inner Padding Depletion
In `CockpitTypographySection.kt` (lines 351–357), `PreviewCockpitTile` defines:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
)
```
* The horizontal padding is only 4 dp.
* When `sensorFieldBorderThickness` is set to 3.0 or 4.0 dp, the inner content has virtually zero clearance from the inside border stroke, causing numbers (like "1:24:35") to collide visually with the border line.

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Targeted Requirements
* **`REQ-UI-276`**: *Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast.* (ATT-2456)
* **`REQ-UI-277`**: *Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming.* (ATT-2457)

### 3.2 Four Mandatory Archaeology Fields
1. **Original Target & Historical Trace**:
   Introduced in Sprint 2026-41.1 (`ATT-2456`, commit `2f5c759a`; `ATT-2457`, commit `cd1c7849`) under Epic `ATT-355` (*Good and consistent UI*).
2. **Historical Origin & Commit Trace**:
   The static spacing constants (`SensorFieldStyle.Variant0_Baseline.gridSpacing = 0.dp`, etc.) were created to prototype discrete presets. When the granular sliders (`Corner Radius`, `Border Thickness`, `Border Contrast`) were added in `ATT-2456`, grid spacing was left static, and `CockpitTypographySection.kt` applied an ad-hoc `coerceAtLeast(6.dp)` clamp to prevent preview tiles from collapsing.
3. **Root Reason for Existing Formulation**:
   The existing formulation treated grid spacing as an immutable attribute of the preset enum rather than a dynamically derived ergonomic function of tile outline thickness and corner geometry.
4. **Preservation of Core Invariants**:
   * Production defaults (`CLASSIC_SEAMLESS`, 0 dp radius, 1.0 dp border, 0% contrast) MUST remain strictly 0 dp edge-to-edge seamless grid spacing.
   * Variant preset baselines (Outlined = 4 dp, Elevated = 6 dp, Capsules = 8 dp) MUST remain the anchor spacing when at baseline thickness.
   * Contrast resolution and selection border overrides MUST remain intact.
   * File modularity boundaries (< 400 lines for `CockpitTypographySection.kt`) MUST be preserved.

---

## 4. User Scope Grounding (ATT-1250)

* **In-Scope Objectives**:
  1. Add a centralized `resolveGridSpacing(variant: SensorFieldVariant, cornerRadius: Dp, borderThickness: Dp): Dp` method to `SensorFieldStyle.kt`.
  2. Implement proportional grid spacing expansion when `borderThickness > 1.0.dp` or `cornerRadius > 0.dp`, ensuring thick borders float with sufficient margin (6–8 dp) instead of colliding.
  3. Ensure `CLASSIC_SEAMLESS` baseline (0 dp radius, $\le 1.0\text{ dp}$ border) retains `0.dp` spacing for seamless edge-to-edge display.
  4. Adopt `SensorFieldStyle.resolveGridSpacing` in both `SensorGridScreen.kt` and `CockpitTypographySection.kt`, guaranteeing 100% WYSIWYG geometry parity.
  5. Refine inner padding compensation in `PreviewCockpitTile` (and check `SensorFieldView.kt`) to ensure numbers and labels do not feel cramped against thick border strokes.
  6. Add and update unit/contract tests in `SensorFieldStyleContractTest.kt` and `CockpitPreviewTileContractTest.kt`.

* **Out-of-Scope Non-Goals**:
  * Adding new tuning preference sliders or changing DataStore keys.
  * Modifying font family, weight, or typography rendering algorithms.
  * Modifying route auto-detector, live climbs, or navigation banners in `SensorGridScreen`.

---

## 5. Architectural Impact & SWE.2 Design

### 5.1 Dynamic Grid Spacing Resolution in `SensorFieldStyle.kt`
We centralize grid spacing calculation in `SensorFieldStyle.Companion`:
```kotlin
fun resolveGridSpacing(
    variant: SensorFieldVariant,
    cornerRadius: Dp,
    borderThickness: Dp
): Dp {
    val baseStyle = forVariant(variant)
    if (variant == SensorFieldVariant.CLASSIC_SEAMLESS && cornerRadius <= 0.dp && borderThickness <= 1.0.dp) {
        return 0.dp
    }
    val thicknessPadding = (borderThickness - 1.0.dp).coerceAtLeast(0.dp) * 1.5f
    val cornerPadding = if (cornerRadius > 0.dp) (cornerRadius * 0.15f).coerceAtLeast(1.dp) else 0.dp
    val computed = baseStyle.gridSpacing.coerceAtLeast(2.dp) + thicknessPadding + cornerPadding
    return computed.coerceIn(2.dp, 12.dp)
}
```
* When baseline `CLASSIC_SEAMLESS` is active (0 radius, 1 dp border): returns `0.dp` (clean edge-to-edge).
* When `OUTLINED_TILES` is active at baseline (8 dp radius, 2 dp border): returns `4.dp + 1.5.dp + 1.2.dp` $\approx 6.7\text{ dp}$ (comfortable whitespace preventing border touch).
* When border thickness reaches 4.0 dp: returns $\approx 8.5\text{--}10\text{ dp}$, giving floating card appearance without touching.

### 5.2 WYSIWYG Alignment in `CockpitTypographySection.kt`
Replace the hardcoded `baseStyle.gridSpacing.coerceAtLeast(6.dp)` with:
```kotlin
val effectiveSpacing = remember(sensorFieldVariant, sensorFieldCornerRadius, sensorFieldBorderThickness) {
    SensorFieldStyle.resolveGridSpacing(
        variant = sensorFieldVariant,
        cornerRadius = sensorFieldCornerRadius.dp,
        borderThickness = sensorFieldBorderThickness.dp
    )
}
```
And pass `effectiveSpacing` to `Arrangement.spacedBy(effectiveSpacing, Alignment.CenterHorizontally)`.

### 5.3 Inner Padding Compensation in `PreviewCockpitTile`
Compensate horizontal padding based on border thickness:
```kotlin
val horizontalPadding = (4.dp + (borderThicknessDp / 2f)).coerceAtLeast(4.dp)
```

---

## 6. Verification & Test Strategy

1. **Unit & Contract Testing**:
   * Add test cases in `SensorFieldStyleContractTest.kt` verifying `resolveGridSpacing` across all variants, radii, and border thicknesses.
   * Verify `CLASSIC_SEAMLESS` baseline returns `0.dp`.
   * Verify thick borders scale spacing proportionally.
   * Update `CockpitPreviewTileContractTest.kt` to assert that `CockpitTypographySection` calls `SensorFieldStyle.resolveGridSpacing`.
2. **Regression Testing**:
   * Execute `./gradlew testDebugUnitTest --tests "*SensorField*"` and `./gradlew testDebugUnitTest --tests "*Cockpit*"`.
   * Full clean-room regression test suite `./gradlew testDebugUnitTest`.

---

## 7. Review Gate 1 Readiness

* Forensic root cause identified and documented.
* Chesterton's Fence fields populated against `REQ-UI-276` and `REQ-UI-277`.
* Scope strictly bounded to grid spacing harmonization and border ergonomics.
* Ready for Gate 1 Audit submission.
