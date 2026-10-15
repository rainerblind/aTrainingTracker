# Stage 2 Test Specification: ATT-1293 - Visual zone gauge tile across training zones in cockpit

**Ticket**: [ATT-1293](https://rainerblind.atlassian.net/browse/ATT-1293)  
**Sub-task**: [ATT-3646](https://rainerblind.atlassian.net/browse/ATT-3646) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1293`  
**Requirement Mapping**: `REQ-UI-376` (extending `REQ-UI-172`, `REQ-UI-370`, `REQ-UI-372`, `REQ-UI-373`, and `REQ-UI-374`)  
**Test Mapping**: `TST-UI-339`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-UI-376`)

### REQ-UI-376: Real-Time Visual Training Zone Gauge Cockpit Integration

The system SHALL provide an integrated real-time visual training zone gauge bar for Heart Rate and Cycling Power sensor tiles in the live tracking cockpit, dynamically adapting to the athlete's configured zone model (5-zone Heart Rate or 7-zone Coggan Cycling Power), calculating high-precision intra-zone needle positions, providing ambient peripheral comprehension, and maintaining strict AMOLED contrast and performance budgets (ATT-1293, extending `REQ-UI-172` and complementing `REQ-UI-370`, `REQ-UI-372`, `REQ-UI-373`, and `REQ-UI-374`):

1. **User Configuration & DataStore Persistence (`REQ-UI-376.1`)**:
   - `ZoneDisplayOptions.kt` SHALL define property `val showGauge: Boolean = false`.
   - `SettingsDataStore.kt` SHALL serialize and persist `showGauge` across application restarts for all three zone profiles (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) using preference keys `hr_run_show_zone_gauge`, `hr_bike_show_zone_gauge`, and `pwr_bike_show_zone_gauge`.
   - `ZonesSettingsActivity.kt` SHALL include a toggle row "Zonen-Balken / Zone gauge bar" (`R.string.zone_display_gauge`) in the "Zonendarstellung / Zone Visualization" card, updating the live preview and persisting to DataStore.

2. **Decoupled Segment Geometry & Equal Visual Width (`REQ-UI-376.2`)**:
   - The visual gauge track SHALL be partitioned into $K$ contiguous, equal-width horizontal segments matching the active metric's physiological model:
     - For Heart Rate (`HR_RUN`, `HR_BIKE`): Exactly $K = 5$ segments using `TTColor.Zone1..Zone5`.
     - For Cycling Power (`PWR_BIKE`): Exactly $K = 7$ segments using `TTColor.Zone1..Zone7`.
   - Equal visual width per zone ($\frac{1}{K}$ width each) guarantees that narrow high-intensity target zones (e.g. Z4 Sweetspot, Z5 VO2max) remain clear, readable, and trackable without shrinking to unreadable slivers.

3. **Intra-Zone Needle Interpolation & Clamping Math (`ZoneGaugeMath.kt`) (`REQ-UI-376.3`)**:
   - For interior closed zones $i \in \{1 \dots K-1\}$ with bounds $[L_i, U_i]$:
     $$\text{fraction}_i = \frac{v - L_i}{U_i - L_i} \in [0.0, 1.0]$$
     $$x_{\text{needle}} = \frac{(i - 1) + \text{fraction}_i}{K} \in [0.0, 1.0]$$
   - Baseline lower bounds: $L_1 = 0.0\text{ W}$ for Power; $L_1 = 30.0\text{ bpm}$ for Heart Rate.
   - For readings below baseline ($v \le L_1$): Needle clamps cleanly to $x_{\text{needle}} = 0.0$ (left edge of Zone 1).
   - **Open-Ended Top Zone Dynamic Ceiling**:
     - For HR Zone 5: Lower bound $L_5 = Z_{4\text{max}}$. Nominal ceiling $U_5 = \max(H\!R_{\max}, Z_{4\text{max}} + 25\text{ bpm})$.
     - For Power Zone 7: Lower bound $L_7 = Z_{6\text{max}}$. Nominal ceiling $U_7 = \min(2500\text{ W}, \max(Z_{6\text{max}} + 300\text{ W}, \text{FTP} \times 2.0))$, conforming to the $2500\text{ W}$ physical ceiling of `REQ-UI-373.1`.
     - For top zone ($v \ge L_{\text{top}}$):
       $$\text{fraction}_{\text{top}} = \min\left(1.0, \frac{v - L_{\text{top}}}{U_{\text{top}} - L_{\text{top}}}\right)$$
       $$x_{\text{needle}} = \frac{(K - 1) + \text{fraction}_{\text{top}}}{K}$$
     - Peak sprints ($v \ge U_{\text{top}}$) clamp cleanly to $x_{\text{needle}} = 1.0$ without overflow.

4. **Telemetry Stale / Disconnected / Null State Handling (`REQ-UI-376.4`)**:
   - When telemetry is absent, disconnected, unconfigured, or stale (`v == null`, `v.isNaN()`, `!v.isFinite()`, or stale packet age $> 5\text{ s}$):
     - The needle SHALL be completely hidden (needle alpha 0.0).
     - The segmented zone track SHALL remain rendered with dimmed opacity (alpha 0.35), preserving full visual context of the zone boundaries.

5. **Cockpit UI & AMOLED Contrast Compliance (`REQ-UI-376.5`)**:
   - `ZoneGaugeBar.kt` SHALL render a compact horizontal track (`height = 6.dp`, `shape = RoundedCornerShape(3.dp)`).
   - Segments SHALL be separated by thin vertical dividers (`1.dp`).
   - The outer track and segment dividers SHALL be stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ against True-Black AMOLED backgrounds (`#000000`) for dark zones (`TTColor.Zone6` and `TTColor.Zone7`).
   - The indicator needle SHALL be rendered with a high-contrast inverted marker (Pure White `#FFFFFF` with a 1dp dark shadow/border) with minimum height of `10.dp` centered vertically over the track, achieving $> 12:1$ contrast against any zone segment color.
   - When configured, a compact active zone badge (`Z1`..`Z7`) rendered in the active zone color SHALL appear adjacent to the gauge.

6. **60 FPS Performance & Recomposition Decoupling (`REQ-UI-376.6`)**:
   - The gauge needle position SHALL be calculated via pure $O(1)$ math.
   - Needle rendering in Compose SHALL execute via canvas drawing or drawWithContent without triggering full tile recompositions on high-frequency power packets (2Hz–4Hz).

7. **9-Language Localization Parity (`REQ-UI-376.7`)**:
   - Add string token `zone_display_gauge` across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Test Specification (`TST-UI-339`)

### TST-UI-339: Real-Time Visual Training Zone Gauge Verification & Precision Test Suite

1. **Mathematical Unit Tests (`ZoneGaugeMathTest.kt`)**:
   - `testHeartRate5Zones_intraZoneNeedlePositioning`:
     - Test Z1 midpoint: $v = (30 + Z_{1\text{max}}) / 2 \rightarrow x \approx 0.10$.
     - Test Z1 upper boundary: $v = Z_{1\text{max}} \rightarrow x = 0.20$.
     - Test Z2 lower/upper bounds: $v = Z_{1\text{max}} + 1 \rightarrow x \approx 0.20$, $v = Z_{2\text{max}} \rightarrow x = 0.40$.
     - Test Z3, Z4 boundaries mapping to exact quintiles ($0.60, 0.80$).
   - `testHeartRate5Zones_openEndedZone5CeilingAndClamping`:
     - Test Z5 entry: $v = Z_{4\text{max}} \rightarrow x = 0.80$.
     - Test Z5 nominal ceiling: $v = U_5 \rightarrow x = 1.00$.
     - Test Z5 extreme value: $v = 250\text{ bpm} > U_5 \rightarrow x = 1.00$ (strictly clamped).
   - `testHeartRate5Zones_belowBaselineClamping`:
     - Test $v = 0$ or $v = 20\text{ bpm} < 30\text{ bpm} \rightarrow x = 0.00$.
   - `testPower7Zones_intraZoneNeedlePositioning`:
     - Test 7-zone partition: Z1 upper boundary maps to $1/7 \approx 0.1428$.
     - Test Z2 upper boundary maps to $2/7 \approx 0.2857$.
     - Test Z3..Z6 upper boundaries map to $3/7, 4/7, 5/7, 6/7$.
   - `testPower7Zones_openEndedZone7DynamicCeilingAndClamping`:
     - Test Z7 entry: $v = Z_{6\text{max}} \rightarrow x = 6/7 \approx 0.8571$.
     - Test Z7 dynamic ceiling: $v = U_7 \rightarrow x = 1.00$.
     - Test Z7 extreme sprint: $v = 2200\text{ W} > U_7 \rightarrow x = 1.00$ (strictly clamped).
     - Test Z7 physical cap at $2500\text{ W}$.
   - `testNullAndNonFinitePower_returnsNullNeedlePosition`:
     - Test $v = \text{null}$, `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` all return `null` (needle hidden).

2. **DataStore Preference Persistence Tests (`SettingsDataStoreZoneGaugeTest.kt`)**:
   - Verify default `showGauge` is `false` across all profiles.
   - Verify updating and saving `showGauge = true` persists and emits on `getZoneDisplayOptionsFlow(type)`.
   - Verify backward compatibility when loading legacy preferences lacking `show_zone_gauge` key.

3. **Cockpit UI & SensorFieldView Integration Tests (`SensorFieldZoneGaugeTest.kt`)**:
   - Verify `SensorFieldState` carries `showGauge` property via `ZoneDisplayOptions`.
   - Verify `SensorFieldView` renders `ZoneGaugeBar` when `showGauge == true` and sensor is `HR` or `POWER`.
   - Verify `SensorFieldView` does NOT render `ZoneGaugeBar` for non-zone sensors (`CADENCE`, `SPEED`, `DISTANCE`).
   - Verify needle is invisible when value is `"--"` or null.

4. **9-Language Localization Parity Tests (`TranslationParityTest.kt`)**:
   - Verify string key `zone_display_gauge` exists across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification ID | Verification Method |
| :--- | :--- | :--- |
| `REQ-UI-376.1` (User Configuration & DataStore) | `TST-UI-339.2` | Unit test (`SettingsDataStoreZoneGaugeTest.kt`) |
| `REQ-UI-376.2` (Decoupled Geometry & Equal Width) | `TST-UI-339.1` | Algorithmic test (`ZoneGaugeMathTest.kt`) |
| `REQ-UI-376.3` (Intra-Zone Math & Open Top Ceiling) | `TST-UI-339.1` | Algorithmic test (`ZoneGaugeMathTest.kt`) |
| `REQ-UI-376.4` (Stale / Null Telemetry Handling) | `TST-UI-339.1`, `TST-UI-339.3` | Algorithmic & UI test (`ZoneGaugeMathTest.kt`, `SensorFieldZoneGaugeTest.kt`) |
| `REQ-UI-376.5` (Cockpit UI & AMOLED Contrast) | `TST-UI-339.3` | Compose UI test (`SensorFieldZoneGaugeTest.kt`) |
| `REQ-UI-376.6` (60 FPS Performance Budget) | `TST-UI-339.1` | Static analysis & benchmark |
| `REQ-UI-376.7` (9-Language Parity) | `TST-UI-339.4` | `TranslationParityTest.kt` |
| `REQ-PRO-001` (Clean-Room Regression) | `TST-UI-339.5` | `./gradlew testDebugUnitTest` |
