# Implementation Plan - ATT-1437: [Cockpit] In dark mode, the missing sensors at the top must be visible again

**Parent Ticket**: [ATT-1437](https://rainerblind.atlassian.net/browse/ATT-1437)  
**Parent Epic**: [ATT-1157](https://rainerblind.atlassian.net/browse/ATT-1157) (*[Epic] Optimize dark mode*)  
**Sub-task**: [ATT-1443](https://rainerblind.atlassian.net/browse/ATT-1443) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-UI-173`  
**Test ID**: `TST-UI-125`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1437_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1437_test_spec.md`  
**Branch**: `feature/ATT-1437`  

---

## 1. Executive Summary & Architectural Scope

The objective of **ATT-1437** is to restore the legibility of missing / inactive sensor icons in the top `SensorStatus` header bar during workout tracking in Dark Mode and AMOLED Pure Black mode, resolving the visual defect where inactive sensors disappear into the black header surface (`#000000`).

The plan addresses the root cause by decoupling content color from opacity:
1. **Single-Source Content Color**: All sensor icons consistently evaluate `tint = MaterialTheme.colorScheme.onSurface`, eliminating the compounded low-luminance `outline` token (`#38383A`) on dark surfaces.
2. **Standardized Opacity Semantics**:
   - Active connected sensors (`isAvailable == true`): `alpha = 1.0f` (100% opacity, pure white `#FFFFFF` in dark mode, $21:1$ contrast ratio).
   - Inactive / disconnected sensors (`isAvailable == false`): `alpha = 0.38f` (Material Design 3 standard disabled content opacity, blending to `#616161` on `#000000`, $3.36:1$ contrast ratio).
3. **Contrast Compliance**: Inactive icons satisfy WCAG 2.1 Non-text Contrast (SC 1.4.11 $\ge 3:1$), while the $6.25\times$ contrast ratio differential between active and inactive states ensures immediate, unambiguous visual distinction for athletes on bike handlebars or running paths.

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Composable Refactoring in `SensorStatus.kt`
In [SensorStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt):
1. Define constant `private const val INACTIVE_SENSOR_ALPHA = 0.38f` (or local resolution).
2. Refactor the `Icon` composable within the `sensorDefinitions.forEach` loop:
   ```kotlin
   sensorDefinitions.forEach { type ->
       val isAvailable = activeSensors.contains(type)
       val iconAlpha = if (isAvailable) 1.0f else INACTIVE_SENSOR_ALPHA

       Icon(
           painter = painterResource(id = type.iconResId),
           contentDescription = type.name,
           modifier = Modifier
               .padding(horizontal = 6.dp)
               .size(22.dp)
               .alpha(iconAlpha)
               .clickable {
                   selectedSensor = type
               },
           tint = MaterialTheme.colorScheme.onSurface
       )
   }
   ```
3. Update Compose Previews in `SensorStatus.kt`:
   - `PreviewSensorStatusRow()` (Light Mode): displays active and inactive sensors with updated legibility.
   - `PreviewSensorStatusRowDark()` (Dark Mode): displays active and inactive sensors with dark theme surface.
   - Add `PreviewSensorStatusRowAmoled()` to explicitly preview against pure black AMOLED surface.

### Phase 2: Unit Testing & Verification (`SensorStatusLegibilityTest.kt`)
Create unit test in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatusLegibilityTest.kt`:
1. `testActiveSensorAlphaAndTint`: Asserts `1.0f` alpha and `onSurface` tint for active sensors.
2. `testInactiveSensorAlphaAndTint`: Asserts `0.38f` alpha and `onSurface` tint for inactive sensors, verifying absence of compounded `outline` color dimming.
3. `testContrastRatiosAcrossThemes`:
   - Validates mathematical contrast ratio of inactive icons against `#000000` is $\ge 3.0 : 1$ (passes SC 1.4.11).
   - Validates contrast ratio of active icons against `#000000` is $\ge 7.0 : 1$ (exceeds WCAG AAA).
   - Validates contrast ratio gap between active and inactive is $\ge 5.0 : 1$.
   - Validates inactive icons against `#FFFFFF` light surface is $\ge 3.0 : 1$.

### Phase 3: Physical Device Verification (Google Pixel 10)
1. Deploy build to connected Pixel 10 (`66020DLCR002FL`).
2. Verify live tracking top bar in AMOLED Dark Mode:
   - Inactive sensors (e.g. Speed, Cadence, HR, Power when sensors are disconnected) are clearly discernible as muted white/grey icons against `#000000`.
   - Active sensors (e.g. Active Time, GPS Accuracy, Altitude) are prominently bright white.
   - Tapping inactive sensor icon opens `SensorSourceDialog`.
3. Verify live tracking top bar in Light Mode:
   - Inactive sensors are clearly legible against the light container without disappearing.
4. Capture screenshots and verify visual appeal with the user.

### Phase 4: Clean-Room Full Suite Regression Execution
Execute `./gradlew testDebugUnitTest` across all modules to verify 100% clean-room test pass rate.

---

## 3. Preserved Invariants & Boundary Verification

1. **Interactivity Invariant**: Tapping any sensor icon (active or inactive) continues to trigger `onDeviceClick` / opens `SensorSourceDialog` (REQ-UI-049).
2. **Layout & Dimensions Invariant**: Icon dimensions remain strictly 22dp, horizontal padding remains 6dp, total height of top bar remains unchanged (REQ-UI-048).
3. **Sensor Order Invariant**: Fixed order sequence (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`) remains immutable.
4. **Theme Mode Invariant**: `CockpitThemeMode` (Always Dark, Always Light, Follow System) and `TrackingTabsScreen` root theming contracts remain untouched (REQ-UI-170).
5. **No Regressions on Page 0**: Control Tracking screen remains in ambient system theme.
6. **Zero Performance Overhead**: Pure Compose UI modifier adjustments; zero allocations, zero disk I/O, zero state drift.
