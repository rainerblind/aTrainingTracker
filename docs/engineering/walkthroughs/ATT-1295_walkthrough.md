# Walkthrough: Visual Pedal Power Balance Indicator Tile in Cockpit (ATT-1295)

## Fulfilling REQ-UI-377 & TST-UI-340

A real-time horizontal split indicator widget (`PedalPowerBalanceBar`) was engineered and integrated across `PedalBalanceState`, `PedalPowerBalanceMath`, `SensorFieldState`, `SensorFieldView`, and `TrackingViewModel`, conforming to ASPICE SWE.3, SWE.4, and SWE.5 standards.

---

### Implemented Architecture & Changes

#### 1. Pure Mathematical Engine & Telemetry Ingestion (`PedalPowerBalanceMath.kt`, `PedalBalanceState.kt`)
- **`PedalBalanceState.kt`**:
  - Encapsulates immutable UI state: `leftPercent: Int`, `rightPercent: Int`, `deviationPercent: Int`, `dominantSide: DominantSide`, `tier: PedalBalanceTier`, `tierColor: Color`, `indicatorFraction: Float?`, and `isStale: Boolean`.
- **`PedalPowerBalanceMath.kt`**:
  - Implements $O(1)$, allocation-free pure calculation consuming raw Right pedal contribution $P_R \in [0, 100]\%$ from ANT+ and Bluetooth LE telemetry drivers.
  - Complementary Left pedal calculation: $P_L = 100 - P_R$.
  - Absolute deviation magnitude from neutral center ($50\%$): $|\Delta| = |P_R - 50|\%$.
  - Normalized indicator position on the split track: $x = \frac{P_R}{100.0} \in [0.0, 1.0]$.
  - Dominant side classification: `LEFT` ($P_R < 50$), `RIGHT` ($P_R > 50$), or `BALANCED` ($P_R = 50$).
  - Physiological asymmetry tier mapping:
    - **Symmetrical / Optimal Tier** ($|\Delta| \le 2\%$, i.e. $48\% \le P_R \le 52\%$): Green (`TTColor.ConnectionStatusGreen` `#4CAF50`).
    - **Mild Asymmetry Tier** ($2\% < |\Delta| \le 5\%$, i.e. $45\% \le P_R < 48\%$ or $52\% < P_R \le 55\%$): Alert Amber (`TTColor.Zone3` `#FFA500`).
    - **Severe Asymmetry Tier** ($|\Delta| > 5\%$, i.e. $P_R < 45\%$ or $P_R > 55\%$): Critical Alert Red (`TTColor.Zone4` `#FF0000`).
  - Stale state handling: null, NaN, and non-finite inputs return `indicatorFraction = null`, `isStale = true`, and formatted string `"-- | --"`.
  - Dual split string formatting: `"48 | 52"` for active data.

#### 2. Visual Component & AMOLED Contrast (`PedalPowerBalanceBar.kt`)
- Compact horizontal split track (`8.dp` track height, `2.dp` needle overhang top & bottom = `12.dp` total height).
- Outer track stroked with `0.5.dp` `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ against True-Black AMOLED backgrounds (`#000000`).
- Prominent central vertical anchor divider at $50\%$ center ($x = 0.50$).
- Dynamic deviation fill extending from center anchor towards $x$, colored with `tierColor`.
- High-contrast needle cursor: Pure White (`#FFFFFF`) with 1dp dark shadow/border (`#1B1B1F`), achieving $> 12:1$ contrast against all fill colors.
- Peripheral "L" and "R" markers backed by localized strings and wrapped in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` to protect mechanical bicycle pedal geometry against RTL mirroring.
- Stale state: track dimmed to $0.35$ alpha, fill bar hidden.
- Includes Compose `@Preview` for Light, Dark, and AMOLED modes.

#### 3. Cockpit Tile & ViewModel Integration (`SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`)
- `SensorFieldState`: Added `val pedalBalanceState: PedalBalanceState? = null`.
- `SensorFieldView`: Renders `PedalPowerBalanceBar` directly below the value/units row when `fieldState.pedalBalanceState != null`.
- `TrackingViewModel`: In `baseFields` and `applySensorData`, when `sensorType == SensorType.PEDAL_POWER_BALANCE`, computes `pedalBalanceState` via `PedalPowerBalanceMath.calculateBalanceState(sensorData.value as? Number)` and formats value string as `"48 | 52"` (or `"-- | --"`).

#### 4. 9-Language Localization & Parity (`PedalBalanceLocalizationTest.kt`)
- Added `pedal_balance_left_marker` ("L") and `pedal_balance_right_marker` ("R") across all 9 application locales:
  - English (`values/`): `L` / `R`
  - German (`values-de/`): `L` / `R`
  - Spanish (`values-es/`): `L` / `R`
  - French (`values-fr/`): `L` / `R`
  - Italian (`values-it/`): `L` / `R`
  - Japanese (`values-ja/`): `L` / `R`
  - Dutch (`values-nl/`): `L` / `R`
  - Polish (`values-pl/`): `L` / `R`
  - Portuguese (`values-pt/`): `L` / `R`

---

### Verification & Test Results (`TST-UI-340`)

1. **`PedalPowerBalanceMathTest.kt`**:
   - `calculateBalanceState_perfectBalance5050`: PASSED (50/50, deviation 0, BALANCED, OPTIMAL Green, $x = 0.50$).
   - `calculateBalanceState_optimalLeftDominant`: PASSED (48% Right / 52% Left, deviation 2, LEFT, OPTIMAL Green, $x = 0.48$).
   - `calculateBalanceState_optimalRightDominant`: PASSED (52% Right / 48% Left, deviation 2, RIGHT, OPTIMAL Green, $x = 0.52$).
   - `calculateBalanceState_mildLeftDominant`: PASSED (46% Right / 54% Left, deviation 4, LEFT, MILD Amber, $x = 0.46$).
   - `calculateBalanceState_mildRightDominant`: PASSED (55% Right / 45% Left, deviation 5, RIGHT, MILD Amber, $x = 0.55$).
   - `calculateBalanceState_severeLeftDominant`: PASSED (40% Right / 60% Left, deviation 10, LEFT, SEVERE Red, $x = 0.40$).
   - `calculateBalanceState_severeRightDominant`: PASSED (62% Right / 38% Left, deviation 12, RIGHT, SEVERE Red, $x = 0.62$).
   - `calculateBalanceState_boundaryClampingNegative`: PASSED (clamped to 0% Right / 100% Left).
   - `calculateBalanceState_boundaryClampingAbove100`: PASSED (clamped to 100% Right / 0% Left).
   - `calculateBalanceState_staleNullHandling`: PASSED (null, NaN, Inf return isStale = true, indicatorFraction = null).
   - `formatBalanceString_validAndStale`: PASSED ("48 | 52" and "-- | --").
2. **`SensorFieldPedalBalanceTest.kt`**:
   - Tested default absence, optimal balance preservation, mild asymmetry preservation, severe asymmetry preservation, stale state, and copy mutation: PASSED.
3. **`PedalBalanceLocalizationTest.kt`**:
   - Verified 100% presence and non-empty translation of `pedal_balance_left_marker` and `pedal_balance_right_marker` across all 9 locales: PASSED.
