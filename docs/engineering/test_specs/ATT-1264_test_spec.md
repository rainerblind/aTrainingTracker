# Test Specification & Requirement Synchronization - ATT-1264: [Cockpit] High-contrast typography and subtle tile grid for sunlight legibility

## 1. Feature Overview & Test Scope

* **Issue Key**: `ATT-1264`
* **Sub-tasks**: `ATT-1424` (Analysis [Erledigt]), `ATT-1425` (Test-Spec [In Bearbeitung]), `[Impl-Plan]`, `[Implementation]`, `[Test]`
* **Parent Issue**: `ATT-1264` (*[Feature] [Cockpit] High-contrast typography and subtle tile grid for sunlight legibility*)
* **Parent Epic**: `ATT-1157` (*[Epic] Optimize dark mode*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-UI-171` (High-Contrast Cockpit Typography and Subtle Tile Grid), referencing `REQ-UI-169` (AMOLED Pure Black Cockpit Theme) and `REQ-UI-170` (Comprehensive Cockpit Dark Theme)
* **Associated Verification**: `TST-UI-123` (High-Contrast Typography & Cockpit Tile Grid Verification)

### Objective
Enhance outdoor legibility under direct sunlight and with tinted/polarized sports sunglasses by bolding primary metric values (`#FFFFFF`), styling metadata (labels, units, filter descriptions) in muted high-contrast grey (`#9E9E9E`), and refining tile dividers to subtle `#262626` borders in the AMOLED tracking cockpit.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-169` (*AMOLED Pure Black Cockpit Theme*) and `REQ-UI-101` (*Neutral Backgrounds*).
2. **Historical Origin & Commit Trace**: `ATT-1263` introduced `AmoledDarkColorScheme` (#000000) and initialized `AmoledOutlineVariant` to `#2C2C2E` and `onSurfaceVariant` to `#C4C6D0`. In `SensorFieldView.kt`, text colors were using `onSurface` for values, units, and labels, while font weights used default typography (`Normal`).
3. **Root Reason for Existing Formulation**: During the initial pure-black pass (`ATT-1263`), the priority was turning off OLED background pixels (`#000000`). Typography and subtle tile demarcation were left at default token mappings. However, in outdoor sunlight and when wearing tinted cycling sunglasses, normal-weight numerals and white unit/label text produce visual clutter with low contrast distinction between metric values and metadata, while borders at `#2C2C2E` can be overly noticeable or insufficiently subtle compared to `#262626`.
4. **Preservation of Core Invariants**:
   - Primary readings remain true white `#FFFFFF` (`onSurface`).
   - Standard app dark mode (`DarkColorScheme`, `DarkOnSurfaceVariant` `#C4C6D0`, `outlineVariant` `#44474F`) remains completely unchanged for non-cockpit views.
   - Contrast standards: `#9E9E9E` over `#000000` provides a contrast ratio of ~7.6:1, exceeding WCAG AAA (7.0:1) and AA (4.5:1). `#FFFFFF` over `#000000` provides 21:1 contrast.
   - Athletic zone color overlays (12% tint + 6dp vertical strip) remain fully visible and distinct.

---

## 2. Harmonized Requirement Specification (`REQ-UI-171`)

### REQ-UI-171: High-Contrast Cockpit Typography and Subtle Tile Grid for Sunlight Legibility
The system SHALL optimize typography weights, text color hierarchies, and tile divider borders within the workout tracking cockpit for instant legibility in direct sunlight and with sports sunglasses (ATT-1264):

1. **SemiBold Primary Sensor Metrics**:
   - In `SensorFieldView.kt`, the primary numeric reading `fieldState.value` SHALL render with semibold font weight (`FontWeight.SemiBold`) across all sensor view sizes (`XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE`).
   - In `AmoledDarkColorScheme`, `onSurface` SHALL be `Color(0xFFFFFFFF)` (pure white), ensuring maximum contrast against `#000000` (21:1 ratio).
2. **Muted Crisp Labels and Unit Annotations**:
   - In `AmoledDarkColorScheme`, `onSurfaceVariant` SHALL be `Color(0xFF9E9E9E)`.
   - In `SensorFieldView.kt`, metric labels (`fieldState.label`), unit annotations (`fieldState.units`), and filter descriptions (`fieldState.filterDescription`) SHALL render with `MaterialTheme.colorScheme.onSurfaceVariant` (`#9E9E9E` in AMOLED dark mode, 7.6:1 contrast ratio), establishing clear hierarchy and eliminating visual clutter.
3. **Subtle Tile Divider Grid**:
   - In `AmoledDarkColorScheme`, `outlineVariant` SHALL be `Color(0xFF262626)`.
   - Sensor field cards in `SensorFieldView.kt` SHALL render tile demarcations using `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`, cleanly separating fields without harsh contrast or distraction.
4. **Preserved App Isolation**:
   - Standard app dark mode (`DarkColorScheme`) SHALL retain `outlineVariant = Color(0xFF44474F)` and `onSurfaceVariant = Color(0xFFC4C6D0)`, strictly isolating non-cockpit destinations.

---

## 3. Detailed Test Specification (`TST-UI-123`)

### TST-UI-123: High-Contrast Typography & Cockpit Tile Grid Verification

1. **Color Scheme Token Parity Unit Tests (`AmoledThemeTest.kt`)**:
   - *Test 1.1*: Verify `AmoledDarkColorScheme.outlineVariant` equals `Color(0xFF262626)`.
   - *Test 1.2*: Verify `AmoledDarkColorScheme.onSurfaceVariant` equals `Color(0xFF9E9E9E)`.
   - *Test 1.3*: Verify `AmoledDarkColorScheme.onSurface` equals `Color(0xFFFFFFFF)`.
   - *Test 1.4*: Verify `DarkColorScheme.outlineVariant` equals `DarkOutlineVariant` (`Color(0xFF44474F)`).
   - *Test 1.5*: Verify `DarkColorScheme.onSurfaceVariant` equals `DarkOnSurfaceVariant` (`Color(0xFFC4C6D0)`).

2. **Sensor Field Typography Unit Tests (`SensorFieldTypographyTest.kt`)**:
   - *Test 2.1*: Verify that `getSensorValueTextStyle(ViewSize)` across all 7 sizes (`XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE`) consistently includes `FontWeight.SemiBold`.
   - *Test 2.2*: Verify that `getSensorUnitTextStyle(ViewSize)` across all 7 sizes retains normal/medium font weight without bold distortion.

3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate.
