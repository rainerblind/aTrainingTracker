# Test Execution Report - ATT-1437: In dark mode, the missing sensors at the top must be visible again

## 1. Executive Summary

* **Sub-task**: `ATT-1445` (Stage 5: Test Execution & Clean-Room Regression)
* **Parent Issue**: `ATT-1437` (*In dark mode, the missing sensors at the top must be visible again*)
* **Parent FixVersion**: `V4.9.38` (Sprint `2026-39.3`)
* **Target Requirement**: `REQ-UI-173` (Legible Inactive Sensor Status Indicators in Dark Mode)
* **Verification Test Specification**: `TST-UI-125`
* **Status**: **Verified** (100% Passing)

---

## 2. Test Execution Details

### A. Itemized Verification Results (`TST-UI-125.1` - `TST-UI-125.5`)

1. **`TST-UI-125.1` (Alpha and Tint State Resolution Unit Tests - `SensorStatusLegibilityTest.kt`)**:
   - `testInactiveSensorAlphaConstant_alignsWithMaterial3Standard`: **PASSED**.
     Asserts that `INACTIVE_SENSOR_ALPHA` equals `0.38f`, strictly adhering to Material Design 3 disabled content specifications.

2. **`TST-UI-125.2` (Contrast Ratio Calculation Verification - `SensorStatusLegibilityTest.kt`)**:
   - `testContrastRatioInAmoledDarkMode_exceedsWcagNonTextThreshold`: **PASSED**.
     - Background: AMOLED Pure Black `#000000` (L = 0.0)
     - Active sensor: `#FFFFFF` at $\alpha = 1.0f$ achieves **21.0:1** contrast ratio ($\ge 7:1$, WCAG AAA).
     - Inactive sensor: `#FFFFFF` at $\alpha = 0.38f$ composited over `#000000` produces sRGB `#616161`, achieving **3.38:1** contrast ratio ($\ge 3:1$, exceeding WCAG 2.1 Non-text Contrast SC 1.4.11).
     - Active vs Inactive contrast differential is **6.21:1** ($\ge 5:1$), ensuring unmistakable visual distinction.
   - `testContrastRatioInStandardDarkTheme`: **PASSED**.
     - Background: Standard dark surface `#1B1B1F`
     - Inactive sensor: achieves $\ge 3:1$ contrast ratio.

3. **`TST-UI-125.3` (Interactivity & Dialog Triggering Verification)**:
   - Verified live on Google Pixel 10 physical device in tracking cockpit.
   - Tapping an inactive sensor icon (e.g. Heart Rate at `bounds="[734,164][824,280]"`) triggers `selectedSensor = type` and immediately opens `SensorSourceDialog` (Herzfrequenz / Nicht verbunden / Magene HR).
   - Dismissing the dialog returns cleanly to the telemetry view.

4. **`TST-UI-125.4` (Visual Preview & Dark Mode Integration)**:
   - Added `@Preview` `PreviewSensorStatusRowAmoled` to [SensorStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt).
   - Verified visual legibility on physical Pixel 10 in both Light Mode (Page 0 / ambient theme) and AMOLED Dark Mode (Pages 1..N / tracking cockpit).
   - All 5 inactive sensor icons (Steps, Speed, Cadence, HR, Power) are crisp and readable in dimmed grey.

5. **`TST-UI-125.5` (Clean-Room Full Suite Regression Execution)**:
   - **Command**: `./gradlew testDebugUnitTest`
   - **Execution Time**: 3m 13s
   - **Result**: **BUILD SUCCESSFUL**, 32 actionable tasks, 0 failures, 0 regressions across all modules.

---

## 3. Requirement Governance Audit

* **Tool**: `python3 tools/verify_requirement_governance.py`
* **Output**:
  ```
  Net-new requirement(s) detected: REQ-UI-173. Bypassing archaeology check cleanly.
  ```
* **Status Updates**:
  - `REQ-UI-173` in `docs/requirements.md` transitioned to **Verified**.
  - `TST-UI-125` in `docs/tests.md` transitioned to **Verified**.

---

## 4. Preserved Invariants Verification

1. **Sensor Ordering**: Sensor sequence (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`) is 100% immutable.
2. **Icon Dimensions**: Icon size of `22.dp` and horizontal padding of `6.dp` are strictly preserved.
3. **Theme Scoping**: Root theme contracts and Page 0 ambient theme isolation (`REQ-UI-170`) remain completely intact.
4. **Interactivity**: Sensor selection triggering `SensorSourceDialog` works identically for both active and inactive states.
