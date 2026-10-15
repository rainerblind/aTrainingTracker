# Stage 2 Test Specification: ATT-1295 - Visual pedal power balance indicator tile in cockpit

**Ticket**: [ATT-1295](https://rainerblind.atlassian.net/browse/ATT-1295)  
**Sub-task**: [ATT-3651](https://rainerblind.atlassian.net/browse/ATT-3651) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1295`  
**Requirement Mapping**: `REQ-UI-377` (extending `REQ-UI-171`, `REQ-UI-103`, and complementing `REQ-UI-376`)  
**Test Mapping**: `TST-UI-340`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-UI-377`)

### REQ-UI-377: Pedal Power Balance Real-Time Visual Split Indicator Cockpit Integration

The system SHALL provide an integrated real-time visual pedal power balance split-bar widget for dual-sided power meters in the live tracking cockpit, featuring dual split formatting, center-anchored bidirectional deviation gauging, physiological asymmetry tier color-coding, and strict True-Black AMOLED compliance (ATT-1295, extending `REQ-UI-171` and `REQ-UI-103`, and complementing `REQ-UI-376`):

1. **Mathematical Representation & Telemetry Ingestion (`REQ-UI-377.1`)**:
   - `PedalPowerBalanceMath.kt` SHALL consume raw telemetry representing Right pedal percentage $P_R \in [0, 100]\%$.
   - The system SHALL calculate Left pedal percentage $P_L = 100 - P_R$.
   - For valid readings, the deviation magnitude is $|\Delta| = |P_R - 50|\%$.
   - The normalized indicator position on the split track is $x_{\text{indicator}} = \frac{P_R}{100.0} \in [0.0, 1.0]$, where $x = 0.50$ represents perfect $50/50$ balance, $x < 0.50$ indicates Left dominance, and $x > 0.50$ indicates Right dominance.
   - Values below $0\%$ or above $100\%$ SHALL be clamped to $[0, 100]\%$.

2. **Physiological Asymmetry Tiers & Color Cues (`REQ-UI-377.2`)**:
   - The system SHALL classify instantaneous power balance into three physiological tiers:
     - **Symmetrical / Optimal Tier** ($|\Delta| \le 2\%$, i.e. $48\% \le P_R \le 52\%$): Rendered in Balanced Green (`TTColor.ConnectionStatusGreen` `#4CAF50` or `MaterialTheme.colorScheme.primary`).
     - **Mild Asymmetry Tier** ($2\% < |\Delta| \le 5\%$, i.e. $45\% \le P_R < 48\%$ or $52\% < P_R \le 55\%$): Rendered in Alert Amber (`TTColor.Zone3` `#FFA500`).
     - **Severe Asymmetry Tier** ($|\Delta| > 5\%$, i.e. $P_R < 45\%$ or $P_R > 55\%$): Rendered in Critical Alert Red (`TTColor.Zone4` `#FF0000`).

3. **Split Readout Formatting (`PedalPowerBalanceFormatter`) (`REQ-UI-377.3`)**:
   - For valid readings: Formats as `"<Left> | <Right>"` (e.g. `"48 | 52"`), with unit `"%"`.
   - When telemetry is absent, disconnected, non-finite, or when power is $0\text{ W}$: Formats as `"-- | --"` with unit `"%"`.

4. **Visual Component (`PedalPowerBalanceBar.kt`) & AMOLED Compliance (`REQ-UI-377.4`)**:
   - The widget SHALL render a horizontal track (`height = 8.dp`) with a prominent central $50/50$ anchor line.
   - Peripheral "L" and "R" labels SHALL indicate side orientation.
   - A high-contrast cursor / indicator marker (Pure White `#FFFFFF` with a 1dp dark border) centered at $x_{\text{indicator}}$ SHALL dynamically indicate the balance point.
   - When active, a color-coded fill bar extending from the $50\%$ center anchor to $x_{\text{indicator}}$ SHALL reflect the magnitude and tier color of the deviation.
   - When telemetry is stale or null: The track SHALL be rendered with dimmed opacity ($0.35$ alpha), the fill bar SHALL be hidden, and the indicator cursor SHALL rest at the neutral $50\%$ mark or remain hidden.
   - AMOLED True-Black Compliance: The track border SHALL be stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ on pure black surfaces.

5. **Cockpit Sensor Tile Integration (`REQ-UI-377.5`)**:
   - `SensorFieldState` SHALL carry `val pedalBalanceState: PedalBalanceState? = null`.
   - `SensorFieldView` SHALL render `PedalPowerBalanceBar` directly below the value/units row whenever `fieldState.pedalBalanceState != null` or sensor is `PEDAL_POWER_BALANCE`.
   - `TrackingViewModel` SHALL compute and update `pedalBalanceState` on every `PEDAL_POWER_BALANCE` telemetry packet.

6. **60 FPS Performance Budget (`REQ-UI-377.6`)**:
   - Math calculations SHALL execute in pure $O(1)$ time with zero object churn in draw scopes.

---

## 2. Test Specification (`TST-UI-340`)

### TST-UI-340: Real-Time Pedal Power Balance Verification & Precision Test Suite

1. **Mathematical Unit Tests (`PedalPowerBalanceMathTest.kt`)**:
   - `testPerfectBalance_50_50`:
     - $P_R = 50 \rightarrow P_L = 50, \Delta = 0\%$, tier = `OPTIMAL`, $x = 0.50$.
   - `testOptimalSymmetry_withinTwoPercent`:
     - $P_R = 48 \rightarrow P_L = 52, \Delta = 2\%$, tier = `OPTIMAL`, $x = 0.48$.
     - $P_R = 52 \rightarrow P_L = 48, \Delta = 2\%$, tier = `OPTIMAL`, $x = 0.52$.
   - `testMildAsymmetry_threeToFivePercent`:
     - $P_R = 46 \rightarrow P_L = 54, \Delta = 4\%$, tier = `MILD`, $x = 0.46$.
     - $P_R = 55 \rightarrow P_L = 45, \Delta = 5\%$, tier = `MILD`, $x = 0.55$.
   - `testSevereAsymmetry_beyondFivePercent`:
     - $P_R = 40 \rightarrow P_L = 60, \Delta = 10\%$, tier = `SEVERE`, $x = 0.40$.
     - $P_R = 62 \rightarrow P_L = 38, \Delta = 12\%$, tier = `SEVERE`, $x = 0.62$.
   - `testBoundaryClamping`:
     - $P_R = -10 \rightarrow P_R = 0, P_L = 100, x = 0.0$.
     - $P_R = 120 \rightarrow P_R = 100, P_L = 0, x = 1.0$.
   - `testNullAndNonFiniteInputs_returnsStale`:
     - `null`, `Double.NaN`, `Double.POSITIVE_INFINITY` return `isStale = true` and `null` indicator.

2. **Formatter Tests (`PedalPowerBalanceFormatterTest.kt`)**:
   - Valid: `52` -> `"48 | 52"`.
   - `50` -> `"50 | 50"`.
   - `null` / non-finite -> `"-- | --"`.

3. **Cockpit UI & SensorFieldView Integration Tests (`SensorFieldPedalBalanceTest.kt`)**:
   - Verify `SensorFieldState` carries `pedalBalanceState`.
   - Verify `SensorFieldView` renders `PedalPowerBalanceBar` when state is present.
   - Verify stale telemetry renders dimmed track (0.35 alpha).

4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification ID | Verification Method |
| :--- | :--- | :--- |
| `REQ-UI-377.1` (Mathematical Representation & Telemetry) | `TST-UI-340.1` | `PedalPowerBalanceMathTest.kt` |
| `REQ-UI-377.2` (Physiological Asymmetry Tiers & Colors) | `TST-UI-340.1` | `PedalPowerBalanceMathTest.kt` |
| `REQ-UI-377.3` (Split Readout Formatting) | `TST-UI-340.2` | `PedalPowerBalanceFormatterTest.kt` |
| `REQ-UI-377.4` (Visual Split Bar & AMOLED Contrast) | `TST-UI-340.3` | `SensorFieldPedalBalanceTest.kt` & Compose Preview |
| `REQ-UI-377.5` (Cockpit Sensor Tile Integration) | `TST-UI-340.3` | `SensorFieldPedalBalanceTest.kt` |
| `REQ-UI-377.6` (60 FPS Performance Budget) | `TST-UI-340.1` | Static analysis & pure $O(1)$ math |
| `REQ-PRO-001` (Clean-Room Regression) | `TST-UI-340.4` | `./gradlew testDebugUnitTest` |
