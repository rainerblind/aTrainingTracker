# Stage 2: Requirement & Test Specification - ATT-1739: [Aftermath/Zones] Redesign Zone Distribution (HR & Power) from horizontal stacked bar to vertical column chart

**Ticket**: [ATT-1739](https://atrainingtracker.atlassian.net/browse/ATT-1739)  
**Sub-task**: [ATT-1750](https://atrainingtracker.atlassian.net/browse/ATT-1750) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*), `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*)  
**Test Spec ID**: `TST-UI-156`, `TST-UI-157`  
**Branch**: `feature/ATT-1739`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-202 & REQ-UI-203)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-40.5, user review rejected the horizontal stacked bar layout for Heart Rate and Power zone distributions:
> *"The zones (Zone 1, Zone 2, .., Zone 5) must be on the x-Axis; the time in the corresponding Zone must be on the y-Axis."*

In sports analytics, discrete intensity tiers belong on the horizontal axis as category bins, with duration plotted vertically as column height. A vertical column chart (histogram) makes narrow zones readily discernible, provides a consistent visual baseline, and matches standard athlete mental models.

### 1.2 Functional & Architectural Requirements
1. **Vertical Column Histogram Layout**:
   - The system SHALL render a 5-column vertical histogram for Heart Rate (`HeartRateZoneDistributionCard.kt`) and Cycling Power (`PowerZoneDistributionCard.kt`).
   - The X-axis SHALL feature exactly 5 discrete columns corresponding to Zone 1 through Zone 5.
   - The Y-axis column height SHALL be proportional to the duration spent in each zone.
2. **Column Height Scaling & Normalization**:
   - For an aggregate zone distribution with durations $t_1, t_2, t_3, t_4, t_5$ and maximum duration $T_{\max} = \max_j(t_j)$:
     - If $T_{\max} > 0$: $\text{heightFraction}_i = \frac{t_i}{T_{\max}}$.
     - If $t_i > 0$: The rendered bar height SHALL be at least `4.dp` to guarantee visual visibility.
     - If $t_i == 0$: The rendered bar height SHALL be `0.dp`.
3. **Column Visual Aesthetics & Styling**:
   - Each column SHALL be filled with its corresponding zone color (`TTColor.Zone1` through `TTColor.Zone5`).
   - Top corners of each column SHALL be rounded (`RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)`).
   - Above/within each column: The system SHALL display formatted duration (`m:ss` or `h:mm:ss`) and percentage (`X%`).
   - Below each column: The system SHALL display the zone label (`Z1`..`Z5`).
4. **Header & Card Container Integrity**:
   - Card container styling (`RoundedCornerShape(12.dp)`, `surfaceVariant.copy(alpha = 0.5f)`), header row with icon (`ic_heart_rate` / `ic_power`), localized title (`R.string.aftermath_hr_zones_title` / `R.string.aftermath_power_zones_title`), and total active duration display SHALL be strictly preserved.
5. **Component Reusability (`ZoneDistributionColumnChart.kt`)**:
   - The core histogram rendering SHALL be factored into a reusable composable `@Composable fun ZoneDistributionColumnChart(distribution: ZoneDistributionData, modifier: Modifier = Modifier)` to avoid duplication between HR and Power cards.
6. **Graceful Telemetry Omission**:
   - If telemetry is absent (total active time 0 or null distribution), the card SHALL evaluate to null and consume 0 vertical space.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution Bar Architecture*)
  - `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution Bar Architecture*)
* **Historical Origin & Commit Trace**:
  - Sprint `2026-40.5`, Tickets `ATT-1389` & `ATT-1390`.
* **Root Reason for Existing Formulation**:
  - The original specification selected a horizontal stacked bar to minimize vertical height. However, during sprint review the human user determined that discrete vertical columns (X-axis = Zones 1..5, Y-axis = time in zone) are required for intuitive data comprehension.
* **Preservation of Core Invariants**:
  - Domain models (`ZoneDistributionData`, `ZoneTimeEntry`), mathematical calculation engine (`ZoneDistributionCalculator`), database queries on `Dispatchers.IO`, ViewModel state flow, and 9-language localization parity remain 100% intact. Only visual presentation switches from horizontal stacked bar to vertical column chart.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Heart Rate Vertical Histogram)**:
  * *Given* a workout recorded with Heart Rate telemetry,
  * *When* the athlete views the workout in Aftermath (`TrackOnMapScreen`),
  * *Then* `HeartRateZoneDistributionCard` SHALL render a 5-column vertical histogram with Zones 1–5 on the X-axis and duration on the Y-axis.
* **Criterion 2 (Cycling Power Vertical Histogram)**:
  * *Given* a workout recorded with Cycling Power telemetry,
  * *When* the athlete views the workout in Aftermath (`TrackOnMapScreen`),
  * *Then* `PowerZoneDistributionCard` SHALL render a 5-column vertical histogram with Zones 1–5 on the X-axis and duration on the Y-axis.
* **Criterion 3 (Proportional Scaling & Non-Zero Minimum)**:
  * *Given* a workout where Zone 2 is 30 minutes and Zone 5 is 10 seconds,
  * *When* the histogram is rendered,
  * *Then* Zone 2 column height SHALL reach 100% of the chart bar area, and Zone 5 SHALL render with at least 4.dp height with label "0:10".
* **Criterion 4 (Zero-Duration Suppression)**:
  * *Given* a zone with 0 seconds duration,
  * *When* the histogram is rendered,
  * *Then* that zone column SHALL render with 0 height, displaying "0:00" and "0%".
* **Criterion 5 (Absent Telemetry Omission)**:
  * *Given* a workout without Heart Rate or Power telemetry,
  * *When* viewed in Aftermath,
  * *Then* the respective zone card SHALL be completely omitted with 0 space consumption.

### 1.5 System Invariants
1. Single-thread SQLite confinement on `Dispatchers.IO` preserved.
2. 9-language localization parity maintained across all supported locales.
3. No degradation of existing elevation profile, GPS map rendering, or snapshot generation.

---

## 2. Test Specification (TST-UI-156 & TST-UI-157)

### Test Case 1: Column Chart Height Calculation & Scaling (`[TST-UI-156.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionChartTest.kt`
* **Preconditions**: `ZoneDistributionData` containing 5 zones with varying durations (e.g. Z1: 60s, Z2: 1200s, Z3: 300s, Z4: 0s, Z5: 15s).
* **Action**: Compute column relative height fractions.
* **Expected Result**:
  - Max duration zone (Z2, 1200s) has fraction 1.0f.
  - Z1 (60s) has fraction 60 / 1200 = 0.05f.
  - Z4 (0s) has fraction 0.0f.
  - Z5 (15s) has fraction 15 / 1200 = 0.0125f.

### Test Case 2: Duration Formatting in Histogram Columns (`[TST-UI-156.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionChartTest.kt`
* **Preconditions**: Various durations (0s, 45s, 600s, 3665s).
* **Action**: Invoke duration formatter.
* **Expected Result**:
  - 0s $\implies$ `"0:00"`
  - 45s $\implies$ `"0:45"`
  - 600s $\implies$ `"10:00"`
  - 3665s $\implies$ `"1:01:05"`

### Test Case 3: 9-Language Localization & Specifier Audit (`[TST-UI-156.3]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneLocalizationTest.kt` and `PowerZoneLocalizationTest.kt`
* **Goal**: Verify string presence and matching format specifiers across all 9 locales:
  - `aftermath_hr_zones_title` and `aftermath_power_zones_title`
  - EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Full Suite Regression (`[TST-UI-156.4]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-156.1]` | Unit | `ZoneDistributionChartMath` | `REQ-UI-202`, `REQ-UI-203` | Specified |
| `[TST-UI-156.2]` | Unit | `formatZoneDuration` | `REQ-UI-202`, `REQ-UI-203` | Specified |
| `[TST-UI-156.3]` | Localization | `HeartRateZoneLocalizationTest`, `PowerZoneLocalizationTest` | `REQ-UI-202`, `REQ-UI-203` | Specified |
| `[TST-UI-156.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
