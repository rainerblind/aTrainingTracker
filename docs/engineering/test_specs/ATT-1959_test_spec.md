# Stage 2: Requirement & Test Specification - ATT-1959: [Aftermath/Zones] Refine Zone Card Header Layout and Streamline Telemetry Histogram Readout

**Ticket**: [ATT-1959](https://rainerblind.atlassian.net/browse/ATT-1959)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-231` (*Aftermath/Zones: Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading*)  
**Test Spec ID**: `TST-UI-189`  
**Branch**: `feature/ATT-1959`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-231 Refinement)

### 1.1 Problem Statement & Rationale
On-device inspection during the Sprint Review of ATT-1845 revealed two UI defects:
1. **Header Row Overcrowding**: In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, placing `SingleChoiceSegmentedButtonRow` on the same horizontal row as the icon, localized card title (e.g. "Herzfrequenz-Zonen"), and total active duration squeezes the duration string (e.g. "1:20:27") into a narrow sliver where characters wrap vertically into an unreadable column.
2. **Readout Clutter & Non-Localized Text**: In `TelemetryHistogramChart.kt`, the unscrubbed default readout line includes a middle label (`"X active bins"`), which is hardcoded in English and adds visual noise without actionable training utility.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-UI-231` to enforce clean header layout and streamlined readout presentation:

1. **Card Header Row Refactoring (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)**:
   - The card header `Row` SHALL contain only:
     - Leading category icon (Heart Rate / Power)
     - Localized title (`R.string.aftermath_hr_zones_title` / `R.string.aftermath_power_zones_title`)
     - Flexible spacer (`Modifier.weight(1f)`)
     - Formatted total active duration (`ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec)`)
   - The header row SHALL NOT contain the mode switcher toggle, ensuring 100% horizontal freedom and preventing vertical text wrapping across all languages and font scales.

2. **Dedicated Bottom Mode Toggle Placement**:
   - When `distribution.histogram != null`, the card SHALL render the mode switcher toggle (`SingleChoiceSegmentedButtonRow`) directly below the chart, centered horizontally (`horizontalArrangement = Arrangement.Center`).
   - The toggle SHALL retain compact height (`28.dp`) and localized options (`zone_mode_5_zones` and `zone_mode_histogram`).

3. **Streamlined Default Histogram Readout (`TelemetryHistogramChart.kt`)**:
   - In the resting/unscrubbed state, the readout surface SHALL display only:
     - Left: Metric span `${histogram.dataMin}–${histogram.dataMax} $unit`
     - Right: Bin width delta `Δ ${histogram.binWidth} $unit`
   - The middle `"active bins"` label SHALL be removed.
   - Interactive touch and drag scrubbing behavior (displaying bin range, duration, percentage, and zone badge) SHALL be strictly preserved.

4. **Preservation of Core Invariants**:
   - Zero modifications to SQLite databases, repositories, or calculation math.
   - 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-231`), extending `REQ-UI-202` and `REQ-UI-203` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*). Refined in ATT-1959.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `bf624382`, ATT-1845) and Sprint 2026-40.9 (`ATT-1959`).
3. *Root Reason for Existing Formulation*: In ATT-1845, the segmented button toggle was placed in the header row adjacent to the title to keep the card compact. However, testing on physical devices revealed that on standard viewports, placing the title, toggle, and total duration on a single line causes catastrophic text wrapping of the duration string in languages with longer words (e.g. German "Herzfrequenz-Zonen"). Furthermore, the default histogram readout contained a non-localized English label ("active bins"). Placing the toggle below the chart provides ample room for both the header elements and the toggle labels, while removing the active bins label streamlines the readout.
4. *Preservation of Core Invariants*: Full backward compatibility with default 5-zone mode, theme contrast parity, asynchronous execution, 9-language localization parity, and zero schema mutations are 100% preserved.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Header Row Independence)**:
  - *Given* an athlete viewing `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` on any screen width or locale,
  - *When* the card renders,
  - *Then* the header row SHALL display the icon, localized title, and total duration on a single un-wrapped line without collisions.
- **AC-2 (Below-Chart Toggle Placement)**:
  - *Given* a workout with histogram telemetry data,
  - *When* the card renders,
  - *Then* the `SingleChoiceSegmentedButtonRow` mode toggle SHALL be centered horizontally directly below the chart.
- **AC-3 (Streamlined Default Readout)**:
  - *Given* `TelemetryHistogramChart` in resting un-scrubbed state,
  - *When* rendered,
  - *Then* the readout line SHALL display only the range and bin delta, omitting any `"active bins"` text.
- **AC-4 (Scrubbing Integrity)**:
  - *Given* an athlete touching or scrubbing a histogram bar,
  - *When* highlighted,
  - *Then* the readout line SHALL display the exact bin range, duration with percentage, and zone tag.

---

## 2. Test Specification (TST-UI-189)

### 2.1 Unit & Contract Tests (`TelemetryHistogramCardTest.kt`)
1. **Card Header Structure Verification**:
   - Assert `HeartRateZoneDistributionCard.kt` header Row does not host `SingleChoiceSegmentedButtonRow`.
   - Assert `PowerZoneDistributionCard.kt` header Row does not host `SingleChoiceSegmentedButtonRow`.
2. **Bottom Toggle Verification**:
   - Assert both cards render `SingleChoiceSegmentedButtonRow` below the chart composable.
3. **Streamlined Readout Verification**:
   - Assert `TelemetryHistogramChart.kt` does not contain `"active bins"`.
   - Assert `TelemetryHistogramChart.kt` retains range and bin width delta in unscrubbed state.

### 2.2 Regression Verification
- Run targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"
  ```
- Run clean-room full test suite in Stage 5:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Traceability Matrix

| Requirement | Test Spec | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-231` (Card Header Row Independence) | `TST-UI-189.1` | Structural Contract Test (`TelemetryHistogramCardTest`) | `Approved` |
| `REQ-UI-231` (Below-Chart Toggle Placement) | `TST-UI-189.2` | Structural Contract Test (`TelemetryHistogramCardTest`) | `Approved` |
| `REQ-UI-231` (Streamlined Readout Line) | `TST-UI-189.3` | Structural Contract Test (`TelemetryHistogramCardTest`) | `Approved` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-189.4` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | `Approved` |
