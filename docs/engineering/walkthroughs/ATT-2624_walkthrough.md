# Walkthrough - ATT-2624: Harmonize cockpit tile grid spacing between preview and tracking screen and refine thick border ergonomics

**Ticket**: [ATT-2624](https://atrainingtracker.atlassian.net/browse/ATT-2624)
**Sub-task**: ATT-2699 (`[Test]`)
**Date**: 2026-10-08
**Author**: Antigravity (AI Assistant)
**Reviewer**: Agent 2 (Auditor) / Rainer Blind (Human User)
**Target Branch**: `feature/ATT-2624`

---

## 1. Problem Domain & Objective

During physical testing on Pixel 10 (Sprint 2026-41.1 review), custom cockpit tile styling revealed two noticeable issues:
1. **Colliding Thick Borders**: When border thickness was increased up to 4.0 dp, adjacent tiles with 0 dp or 4 dp spacing touched or collided, resulting in cramped corners and heavy visual borders.
2. **Preview vs. Real-Screen Discrepancy**: The settings live preview in `CockpitTypographySection.kt` hardcoded a minimum spacing of 6 dp (`baseStyle.gridSpacing.coerceAtLeast(6.dp)`), showing wide gaps even when `CLASSIC_SEAMLESS` (0 dp) or `OUTLINED_TILES` (4 dp) was selected. Furthermore, slider movements were completely ignored by grid spacing.
3. **Cramped Content Clearance**: Internal horizontal padding in preview tiles was fixed at 4 dp, causing metrics to crowd against 3–4 dp border strokes.

**Objective**:
- Centralize dynamic grid spacing resolution into `SensorFieldStyle.resolveGridSpacing(...)` and share it across `SensorGridScreen.kt` and `CockpitTypographySection.kt`.
- Scale whitespace proportionally with border thickness and corner radius so tiles with thick outlines float comfortably (6–10 dp) instead of colliding.
- Preserve the 0 dp seamless edge-to-edge baseline for `CLASSIC_SEAMLESS`.
- Compensate inner tile padding in preview cards when border thickness increases.

---

## 2. Changes Implemented

### 2.1 Dynamic Grid Spacing Resolution
- **File**: [SensorFieldStyle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldStyle.kt)
  - Implemented `resolveGridSpacing(variant: SensorFieldVariant, cornerRadius: Dp, borderThickness: Dp): Dp` in `Companion`.
  - Preserves `0.dp` for `CLASSIC_SEAMLESS` baseline (0 radius, $\le 1.0\text{ dp}$ border).
  - Dynamically calculates proportional padding:
    `thicknessPadding = (borderThickness - 1.0.dp).coerceAtLeast(0.dp) * 1.5f`
    `cornerPadding = if (cornerRadius > 0.dp) (cornerRadius * 0.15f).coerceAtLeast(1.dp) else 0.dp`
    `val computed = baseStyle.gridSpacing.coerceAtLeast(2.dp) + thicknessPadding + cornerPadding`
    clamped within `[2.dp, 12.dp]`.

### 2.2 Live Tracking Screen Integration
- **File**: [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt)
  - Replaced static `activeVariantStyle.gridSpacing` with dynamic resolution:
    ```kotlin
    val effectiveSpacing = if (isDefaultStyling) {
        SensorFieldStyle.resolveGridSpacing(
            variant = tuningConfig.sensorFieldVariant,
            cornerRadius = tuningConfig.sensorFieldCornerRadius.dp,
            borderThickness = tuningConfig.sensorFieldBorderThickness.dp
        )
    } else gridSpacing
    ```

### 2.3 Settings Live Preview Harmonization & Padding Compensation
- **File**: [CockpitTypographySection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/CockpitTypographySection.kt)
  - Excised hardcoded `baseStyle.gridSpacing.coerceAtLeast(6.dp)` in favor of `SensorFieldStyle.resolveGridSpacing(...)`.
  - Passed `borderThickness = sensorFieldBorderThickness.dp` to `PreviewCockpitTile`.
  - Updated `PreviewCockpitTile` padding to dynamically expand with border thickness:
    `val hPadding = (4.dp + borderThickness / 2f).coerceAtLeast(4.dp)`
    `val vPadding = (8.dp + borderThickness / 2f).coerceAtLeast(8.dp)`
  - Preserved file modularity (390 lines, strictly $< 400$ lines).

### 2.4 Structural Contract & Unit Tests
- **File**: [SensorFieldStyleContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldStyleContractTest.kt)
  - Added unit test `testResolveGridSpacing_preservesBaselineForClassicSeamless`.
  - Added unit test `testResolveGridSpacing_scalesWithBorderThicknessAndCornerRadius`.
  - Verified `SensorGridScreen` resolves `effectiveSpacing` via `SensorFieldStyle.resolveGridSpacing`.
- **File**: [CockpitPreviewTileContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/CockpitPreviewTileContractTest.kt)
  - Verified `CockpitTypographySection` calls `SensorFieldStyle.resolveGridSpacing`.
  - Verified padding compensation with `borderThickness` parameter.

---

## 3. Verification & Test Results

### 3.1 Targeted Test Execution
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldStyleContractTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.CockpitPreviewTileContractTest"
```
**Result**: BUILD SUCCESSFUL in 23s (100% pass rate).

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
**Result**: Passed (BUILD SUCCESSFUL across all modules).

### 3.3 On-Device / Physical Verification
- `adb devices`: No physical or emulator devices connected in execution environment. UI contract tests and unit tests assert geometry calculations and WYSIWYG parity.

---

## 4. Living Documentation Updates
- Updated [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): `REQ-UI-292` status advanced to `Verified`.
- Updated [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): `TST-UI-252` status advanced to `Verified`.
