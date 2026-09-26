# Architectural Analysis - ATT-1437: [Cockpit] In dark mode, the missing sensors at the top must be visible again

## 1. Context & Executive Summary

* **Issue Key**: `ATT-1437`
* **Sub-tasks**: `ATT-1441` (Stage 1: Analysis [In Bearbeitung])
* **Parent Issue**: `ATT-1437` (*[Verbesserung] In dark mode, the missing sensors at the top must be visible again*)
* **Parent Epic**: `ATT-1157` (*[Epic] Optimize dark mode*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-173` (Legible Inactive Sensor Status Indicators in Dark Mode), referencing `REQ-UI-048` (Clean Sensor Status Header), `REQ-UI-169` (AMOLED Pure Black Theme), and `REQ-UI-170` (Comprehensive Cockpit Dark Theme)
* **Associated Verification**: `TST-UI-125` (Sensor Status Inactive Indicator Legibility Verification)
* **Branch**: `feature/ATT-1437`

---

## 2. Problem Statement & Root Cause Analysis

In [ATT-1413](https://rainerblind.atlassian.net/browse/ATT-1413), the workout cockpit dark mode was extended across the entire layout of `TrackingTabsScreen.kt`, including the top header surface (`MaterialTheme.colorScheme.primaryContainer = Color(0xFF000000)` in AMOLED dark mode).

However, during active workouts and in telemetry cockpit views, athletes observed that all **missing / inactive sensor icons at the top of the screen become completely invisible** in dark mode.

### Deep-Dive Root Cause: Compounded Dimming Defect
The top sensor status bar is implemented in [SensorStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt) (lines 102–117):

```kotlin
sensorDefinitions.forEach { type ->
    val isAvailable = activeSensors.contains(type)

    Icon(
        painter = painterResource(id = type.iconResId),
        contentDescription = type.name,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(22.dp)
            .alpha(if (isAvailable) 1f else 0.2f)
            .clickable {
                selectedSensor = type
            },
        tint = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    )
}
```

When a sensor is inactive / not connected (`isAvailable == false`):
1. **Color Dimming**: `tint` evaluates to `MaterialTheme.colorScheme.outline`.
   - In `AmoledDarkColorScheme`: `outline = AmoledOutline = Color(0xFF38383A)` (rgb: 56, 56, 56).
   - In `DarkColorScheme`: `outline = DarkOutline = Color(0xFF8E9099)`.
   - In `LightColorScheme`: `outline = LightOutline = Color(0xFF74777F)`.
2. **Alpha Dimming**: A hardcoded modifier `.alpha(0.2f)` (20% opacity) is applied on top of the already dimmed color.

### Mathematical Contrast Failure in Dark / AMOLED Mode:
* On a pitch black AMOLED background (`#000000`):
  - Inactive sensor color: `Color(0xFF38383A)` with `alpha = 0.2f`.
  - Effective rendered pixel value: $56 \times 0.2 = 11.2 \rightarrow \text{rgb}(11, 11, 11)$ (`#0B0B0B`).
  - **Contrast Ratio against `#000000`**:
    $$\frac{L_1 + 0.05}{L_2 + 0.05} = \frac{(11.2 / 255)^{2.2} + 0.05}{0.00 + 0.05} = \frac{0.0010 + 0.05}{0.05} = \mathbf{1.02 : 1}$$
  - A contrast ratio of 1.02:1 is below the human visual perception threshold under ambient illumination. The icons literally blend into the black surface.
* On standard Dark Theme surface (`#1B1B1F`):
  - Inactive sensor color: `Color(0xFF8E9099)` ($142, 144, 153$) at `0.2f` alpha.
  - Effective rendered pixel value: $142 \times 0.2 + 27 \times 0.8 = 28.4 + 21.6 = 50 \rightarrow \text{rgb}(50, 50, 50)$ (`#323232`).
  - Contrast ratio against `#1B1B1F` is merely $1.4:1$, virtually indistinguishable on a moving bike or in sunlight.
* In Light Mode:
  - On `#D3E3FD` (light blue) or `#FFFFFF`, `0.2f` alpha against white produces `#E0E0E0`, which is faint but perceptually discernible because light backgrounds reflect ambient light differently. However, even there, legibility is suboptimal.

---

## 3. Call Site Audit

A project-wide code search reveals all call sites and usages of `SensorStatus`:
1. **[SensorStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt)**:
   - Primary declaration of `SensorStatus(activeSensors, sourceMapping, allTelemetry, allDevices, onDeviceClick, onMenuClick, modifier)`.
   - Preview composables: `PreviewSensorStatusRow()` and `PreviewSensorStatusRowDark()`.
2. **[TrackingTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt)** (line 325):
   - Invocation in the tracking cockpit header inside `Surface(color = MaterialTheme.colorScheme.primaryContainer)`.
3. **[TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt)**:
   - Imports `SensorStatus` (unused import from legacy refactoring).

No other screen invokes `SensorStatus`.

---

## 4. Proposed Solution & Ergonomic Contrast Architecture

### A. Principle of Single-Source Opacity
The root flaw was the compounding of a low-luminance tint (`outline`) with an aggressive opacity reduction (`0.2f`).
According to **Material Design 3 Design System Specifications** (and WCAG 2.1 Success Criterion 1.4.11 Non-text Contrast):
* Inactive UI glyphs / icons should use the primary content color (`MaterialTheme.colorScheme.onSurface`) with standard disabled opacity ($\alpha \approx 0.38$, `ContentAlpha.disabled`), OR `onSurfaceVariant` with moderate alpha.

### B. Contrast Evaluation of Alternatives:

| Approach | Active Sensor ($\alpha = 1.0$) | Inactive Sensor on AMOLED (`#000000`) | Inactive Sensor on Light (`#FFFFFF`) | WCAG 2.1 AA (1.4.11 $\ge 3:1$) | Evaluation |
|---|---|---|---|---|---|
| **Current Defect** (`outline` + `0.2f` $\alpha$) | `#FFFFFF` (21:1) | `#0B0B0B` (**1.02:1**) | `#D8D8D8` (1.4:1) | **FAIL** | Completely invisible in dark mode. |
| **Option 1: `outline` without $\alpha$** (`#38383A`) | `#FFFFFF` (21:1) | `#38383A` (**1.7:1**) | `#74777F` (4.5:1) | **FAIL** (on dark) | Still too dark on black; asymmetrical light vs dark. |
| **Option 2 (Recommended): `onSurface` with $\alpha = 0.38f$** | `#FFFFFF` (21:1) | `#616161` (**3.36:1**) | `#9E9E9E` (**3.1:1**) | **PASS** ($\ge 3:1$ in both) | Perfect balance: clearly legible as inactive, 6.25x contrast gap from active icons. |
| **Option 3: `onSurface` with $\alpha = 0.30f$** | `#FFFFFF` (21:1) | `#4D4D4D` (**2.2:1**) | `#B0B0B0` (2.1:1) | **FAIL** ($< 3:1$) | Slightly too faint in bright sunlight with sunglasses. |
| **Option 4: `onSurface` with $\alpha = 0.45f$** | `#FFFFFF` (21:1) | `#737373` (**4.4:1**) | `#8C8C8C` (4.0:1) | **PASS** | High legibility, but risks visually competing with active icons. |

### C. Recommended Architectural Formulation
In `SensorStatus.kt`:
```kotlin
val inactiveAlpha = 0.38f // Material 3 standard disabled content alpha
val iconAlpha = if (isAvailable) 1.0f else inactiveAlpha

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
```
* **Active Sensors**: Render with full `onSurface` (pure white `#FFFFFF` in dark mode, pure black `#000000` in light mode) at `1.0f` alpha ($21:1$ contrast ratio).
* **Inactive Sensors**: Render with `onSurface` at `0.38f` alpha:
  - On AMOLED `#000000`: blends to `#616161` (3.36:1 contrast ratio).
  - On Dark `#1B1B1F`: blends to `#727275` (3.2:1 contrast ratio).
  - On Light `#FFFFFF`: blends to `#9E9E9E` (3.1:1 contrast ratio).
  - Inactive icons are clearly recognizable, yet unmistakably subordinate to active sensors.

---

## 5. Requirement Specification (`REQ-UI-173`)

### REQ-UI-173: Legible Inactive Sensor Status Indicators in Dark Mode
The system SHALL ensure that inactive / missing sensor icons in the top `SensorStatus` header bar remain clearly legible across all application themes (Light, Dark, and AMOLED Pure Black) while preserving visual subordination to active connected sensors (ATT-1437):

1. **Content Color & Alpha Decoupling**:
   - In `SensorStatus.kt`, sensor icons SHALL evaluate `tint = MaterialTheme.colorScheme.onSurface`.
   - Inactive sensors (`isAvailable == false`) SHALL render with a defined disabled opacity of `0.38f` (`alpha = 0.38f`).
   - Active sensors (`isAvailable == true`) SHALL render with full opacity (`alpha = 1.0f`).
2. **Contrast & WCAG 2.1 Compliance**:
   - Inactive sensor icons SHALL achieve a minimum contrast ratio of $\ge 3:1$ against the underlying header surface in AMOLED Pure Black (`#000000`), standard Dark (`#1B1B1F`), and Light (`#FFFFFF` / `#D3E3FD`) modes, complying with WCAG 2.1 Non-text Contrast (SC 1.4.11).
   - Active sensor icons SHALL maintain a minimum contrast ratio of $\ge 7:1$ (exceeding WCAG AAA).
3. **Preserved Invariants & Interactivity**:
   - Tapping an inactive sensor icon SHALL continue to open the `SensorSourceDialog` (REQ-UI-049) to allow sensor inspection, pairing, or diagnostics.
   - Sensor icon ordering (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`) and size (22dp with 6dp horizontal padding) MUST NOT be altered.
   - The hamburger navigation menu button and top bar layout in `TrackingTabsScreen.kt` MUST NOT be affected.

---

## 6. System Invariants Verification

1. **Interactivity Invariant**: Tapping any sensor icon (active or inactive) continues to trigger `onDeviceClick` / opens `SensorSourceDialog`.
2. **Layout & Dimensions Invariant**: Icon size remains strictly 22dp, horizontal padding remains 6dp, total height of top bar remains unchanged.
3. **Sensor Order Invariant**: Fixed order sequence (`TIME_ACTIVE` through `POWER`) remains immutable.
4. **Theme Mode Invariant**: `CockpitThemeMode` (Always Dark, Always Light, Follow System) and `TrackingTabsScreen` root theming contracts remain untouched.
5. **No Regressions on Page 0**: Control Tracking screen remains in ambient system theme.

---

## 7. Risk Rating & Gate 1 Recommendation

* **Risk Rating**: **LOW**
  - Confined strictly to [SensorStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt).
  - No database schema, networking, protocol, or background tracking services involved.
  - Zero disk I/O; pure Compose UI styling adjustment.
* **Recommendation**: **RECOMMEND PASS** for Gate 1.
