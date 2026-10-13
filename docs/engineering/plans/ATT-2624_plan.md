# Stage 3 Implementation Plan - ATT-2624: Harmonize cockpit tile grid spacing between preview and tracking screen and refine thick border ergonomics

**Ticket**: [ATT-2624](https://atrainingtracker.atlassian.net/browse/ATT-2624)  
**Sub-task**: [ATT-2697](https://atrainingtracker.atlassian.net/browse/ATT-2697) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2624`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary & Problem Domain

### Problem Domain
In Sprint 2026-41.1, `ATT-2456` and `ATT-2457` introduced granular customization sliders (`Corner Radius`, `Border Thickness`, `Border Contrast`) and a settings live preview for cockpit tiles. Physical device testing on Pixel 10 revealed two ergonomics and consistency flaws:
1. **Colliding Thick Borders**: When border thickness increases (e.g. 2.0 to 4.0 dp), adjacent tiles with static 0 dp or 4 dp spacing physically collide or touch, producing heavy, double-thickness borders and crowded corners.
2. **Preview vs. Real-Screen Discrepancy**: The settings live preview in `CockpitTypographySection.kt` hardcodes `baseStyle.gridSpacing.coerceAtLeast(6.dp)`, causing `CLASSIC_SEAMLESS` (0 dp) and `OUTLINED_TILES` (4 dp) to render with 6 dp spacing in the preview while appearing with 0 dp or 4 dp on the live tracking screen (`SensorGridScreen.kt`).
3. **Cramped Content Clearance**: Internal horizontal padding in preview tiles is fixed at 4 dp, causing metrics to crowd against 3–4 dp border strokes.

### Proposed Architecture
Centralize dynamic grid spacing resolution into `SensorFieldStyle.resolveGridSpacing(variant, cornerRadius, borderThickness): Dp` and consume this shared function in both `SensorGridScreen.kt` and `CockpitTypographySection.kt`. Dynamically expand inner padding in preview tiles when border thickness increases.

---

## 2. Technical Architecture & Component Mapping (SWE.2)

```
┌────────────────────────────────────────────────────────┐
│               SensorFieldStyle.kt                      │
│                                                        │
│  + resolveGridSpacing(variant, radius, thickness): Dp  │
│  + resolveShape(cornerRadius): Shape                   │
│  + resolveBorder(thickness, contrast, isDark): Border  │
└───────────────────────┬────────────────────────────────┘
                        │ shared calculation
          ┌─────────────┴─────────────┐
          ▼                           ▼
┌──────────────────────┐    ┌──────────────────────────────────┐
│ SensorGridScreen.kt  │    │  CockpitTypographySection.kt     │
│                      │    │                                  │
│ effectiveSpacing =   │    │  effectiveSpacing =              │
│ resolveGridSpacing(  │    │  resolveGridSpacing(             │
│   variant,           │    │    variant,                      │
│   radius,            │    │    radius,                       │
│   thickness          │    │    thickness                     │
│ )                    │    │  )                               │
│                      │    │                                  │
│ (Live Tracking HUD)  │    │  PreviewCockpitTile (with        │
│                      │    │  padding compensation)           │
└──────────────────────┘    └──────────────────────────────────┘
```

### Components Modified
1. `SensorFieldStyle.kt`:
   - Add `resolveGridSpacing(variant: SensorFieldVariant, cornerRadius: Dp, borderThickness: Dp): Dp` in `Companion`.
2. `SensorGridScreen.kt`:
   - Update `effectiveSpacing` to resolve via `SensorFieldStyle.resolveGridSpacing(tuningConfig.sensorFieldVariant, tuningConfig.sensorFieldCornerRadius.dp, tuningConfig.sensorFieldBorderThickness.dp)` when `isDefaultStyling` is true.
3. `CockpitTypographySection.kt`:
   - Excise `baseStyle.gridSpacing.coerceAtLeast(6.dp)` in favor of `SensorFieldStyle.resolveGridSpacing(...)`.
   - Update `PreviewCockpitTile` internal padding with border thickness compensation: `horizontal = (4.dp + borderThickness / 2).coerceAtLeast(4.dp)`.
4. `SensorFieldStyleContractTest.kt` & `CockpitPreviewTileContractTest.kt`:
   - Add unit tests verifying `resolveGridSpacing` across all combinations.
   - Update structural contract tests asserting unified resolution.

---

## 3. Step-by-Step Implementation Sequence

### Step 1: Implement `resolveGridSpacing` in `SensorFieldStyle.kt`
- Add to `SensorFieldStyle.Companion`:
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

### Step 2: Harmonize `SensorGridScreen.kt`
- In `SensorGridScreen.kt`:
  ```kotlin
  val effectiveSpacing = if (isDefaultStyling) {
      SensorFieldStyle.resolveGridSpacing(
          variant = tuningConfig.sensorFieldVariant,
          cornerRadius = tuningConfig.sensorFieldCornerRadius.dp,
          borderThickness = tuningConfig.sensorFieldBorderThickness.dp
      )
  } else gridSpacing
  ```

### Step 3: Harmonize `CockpitTypographySection.kt` and Compensate Padding
- In `CockpitTypographySection.kt`:
  - Calculate `effectiveSpacing` via `SensorFieldStyle.resolveGridSpacing`.
  - Pass `effectiveSpacing` to `Arrangement.spacedBy(effectiveSpacing, Alignment.CenterHorizontally)`.
  - Pass `borderThickness = sensorFieldBorderThickness.dp` to `PreviewCockpitTile`.
  - In `PreviewCockpitTile`, set `padding(vertical = (8.dp + borderThickness / 2f).coerceAtLeast(8.dp), horizontal = (4.dp + borderThickness / 2f).coerceAtLeast(4.dp))`.

### Step 4: Unit & Contract Tests
- In `SensorFieldStyleContractTest.kt`:
  - Add `testResolveGridSpacing_preservesBaselineForClassicSeamless`.
  - Add `testResolveGridSpacing_scalesWithBorderThicknessAndCornerRadius`.
  - Update `testSensorGridScreen_structuralContracts` to verify `resolveGridSpacing` is invoked.
- In `CockpitPreviewTileContractTest.kt`:
  - Update `testLivePreview_usesSensorFieldStyleAndDiscreteTiles` to verify `SensorFieldStyle.resolveGridSpacing` is invoked.

---

## 4. Invariants & Guardrails

1. **Zero Impact on Production Defaults**:
   When `variant == CLASSIC_SEAMLESS`, `cornerRadius == 0.dp`, and `borderThickness <= 1.0.dp`, spacing is strictly `0.dp`. The seamless edge-to-edge default is 100% preserved.
2. **File Size Boundaries**:
   `CockpitTypographySection.kt` (currently 377 lines) must remain strictly under 400 lines of code.
3. **No Network / External API Dependencies**:
   Entirely local Compose UI styling; zero SQLite or backend schema changes.
4. **Full Clean-Room Regression**:
   Zero regressions on `./gradlew testDebugUnitTest`.

---

## 5. UI Consistency (Rule 23)

* **Closest Reference Screen**: `SensorGridScreen.kt` (live tracking cockpit) and `CockpitTypographySection.kt` (settings preview card).
* **Reused Components**: `SensorFieldStyle`, `Card`, `MaterialTheme.colorScheme`.
* **Theme Tokens**:
  - Surfaces and borders utilize `MaterialTheme.colorScheme.surface`, `lerp(subtleColor, maxContrastColor, t)`.
  - Spacing dynamically derives from `SensorFieldStyle.resolveGridSpacing`, respecting the 4 dp / 8 dp scale anchor points.
* **Justification for Dynamic Style**: Proportional scaling is essential to prevent physical collision of adjacent thick card borders (up to 4.0 dp) without requiring an extra manual slider setting for athletes.

---

## 6. Verification & Test Plan

1. **Targeted Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldStyleContractTest"
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.CockpitPreviewTileContractTest"
   ```
2. **Full Regression Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 7. Gate 3 Readiness Checklist

- [x] Architectural decomposition complete (SWE.2).
- [x] Step-by-step sequencing defined.
- [x] Invariants and rollback safety verified.
- [x] UI Consistency section populated per Rule 23.
- [x] Ready for Gate 3 Audit submission.
