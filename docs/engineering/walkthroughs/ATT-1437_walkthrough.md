# Walkthrough: ATT-1437 In dark mode, the missing sensors at the top must be visible again

## Context & Problem
In dark mode (specifically the Pure Black AMOLED `#000000` cockpit background), missing/inactive sensor icons in the top `SensorStatus` bar were completely invisible. This was caused by two compounding attenuation factors:
1. `tint = MaterialTheme.colorScheme.outline` (which maps to `AmoledOutline = #38383A`).
2. An additional `.alpha(0.2f)` modifier applied on top of the already dark outline tint.

Over `#000000`, this compounded down to an effective sRGB color of `#0B0B0B`, giving a contrast ratio of only **1.02:1** (far below the WCAG 2.1 Non-text Contrast SC 1.4.11 requirement of $\ge 3:1$).

## Solution Implemented
1. **Decoupled Content Color**: Changed icon tint from `MaterialTheme.colorScheme.outline` to `MaterialTheme.colorScheme.onSurface` (pure `#FFFFFF` in dark mode, `#000000` in light mode).
2. **Standardized Disabled Opacity**: Set inactive sensor opacity to `INACTIVE_SENSOR_ALPHA = 0.38f`, adhering directly to the Material Design 3 disabled content standard.
3. **Contrast Compliance**:
   - Inactive icons on `#000000`: blends to `#616161`, achieving a contrast ratio of **3.38:1** (exceeding WCAG 2.1 AA $\ge 3:1$).
   - Active icons on `#000000`: `#FFFFFF` at `1.0f` achieves a contrast ratio of **21:1**.
   - Distinctiveness: Active icons are **6.2x** more prominent than inactive icons, maintaining clear visual hierarchy.
4. **Interaction & Sizing**: Icon sizing (`22.dp`), padding (`6.dp`), ordering (`TIME_ACTIVE` through `POWER`), and tap interaction to open `SensorSourceDialog` remain completely intact.
5. **Preview & Verification**: Added `@Preview` `PreviewSensorStatusRowAmoled()` to [SensorStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt) and comprehensive contrast unit tests in [SensorStatusLegibilityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatusLegibilityTest.kt).

## Verification Results
### Automated Unit Tests
Executed via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatusLegibilityTest"`:
- `testInactiveSensorAlphaConstant_alignsWithMaterial3Standard`: PASSED
- `testContrastRatioInAmoledDarkMode_exceedsWcagNonTextThreshold`: PASSED (contrast ratio 3.38:1 $\ge$ 3.0:1, differential 6.21:1 $\ge$ 5.0:1)
- `testContrastRatioInStandardDarkTheme`: PASSED

### Live Device Verification
Deployed to physical Pixel 10 (Android 17, API 35) in Light Mode with Cockpit AMOLED Theme:
- Inactive sensor icons (Steps, Speed, Cadence, Heart Rate, Power) are clearly visible and legible in dimmed grey against AMOLED black `#000000`.
- Active sensors (Timer, GPS, Altitude) are prominently displayed in crisp white `#FFFFFF`.
- Tapping an inactive sensor icon (e.g. Heart Rate) immediately opens the `SensorSourceDialog`.
