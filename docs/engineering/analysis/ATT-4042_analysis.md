# Stage 1 Analysis: ATT-4042 - Single-line Power row and clearer visual separation between domain rows in WorkoutDetails

**Ticket**: [ATT-4042](https://rainerblind.atlassian.net/browse/ATT-4042)  
**Sub-task**: [ATT-4226](https://rainerblind.atlassian.net/browse/ATT-4226) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Branch**: `feature/ATT-4042`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-42.8 Joint Review of *ATT-3930* on Google Pixel 10 (see ticket attachment `ATT-3930_review_feedback.png`), visual inspection of the redesigned `WorkoutDetails` telemetry card revealed two usability and design cadence shortcomings:

1. **Two Stacked Lines in Power Domain Row**:
   - The *Leistung* (Power) domain row in `PowerTrainingMetricsSection` currently stacks two horizontal rows when both Coggan metrics and basic physics metrics are present:
     - Row 1: `NP` (with `VI`), `IF`, `TSS`
     - Row 2: `Arbeit (kJ)`, `W/kg`
   - While all other domain rows (*Höhenmeter*, *Effizienz*, *W'bal*) maintain a clean, compact single-line horizontal rhythm, the Power row expands to double height. This disrupts the visual cadence of the card and clutters the telemetry summary with secondary metrics even when primary load metrics are present.

2. **Insufficient Visual Separation Between Consecutive Domain Rows**:
   - The consecutive domain rows in `WorkoutDetails.kt` (*Zeit aktiv / Distanz*, *Höhenmeter*, *Leistung*, *Effizienz*, *W'bal*) are arranged in a plain vertical column with `verticalArrangement = Arrangement.spacedBy(16.dp)` without horizontal dividers.
   - Because each domain row contains disparate internal typography, icon badges, and data columns, the domain rows visually blend into each other vertically, making it harder for the athlete to distinguish separate physiological and mechanical telemetry domains at a glance.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic examination of `WorkoutDetails.kt` and its evolutionary history reveals:

1. **Evolutionary Background in ATT-3930 (`c8939a3f` / `docs/design_guidelines.md` Section 5.10)**:
   - In Sprint 2026-42.8, `ATT-3930` refactored `WorkoutDetails.kt` to eliminate legacy `FlowRow` pill badges and comply with the `AltitudeRow` canonical pattern (`REQ-UI-404`).
   - In that refactoring, to avoid omitting any existing metrics, `PowerTrainingMetricsSection` implemented two separate conditional rows:
     ```kotlin
     if (showCogganRow) {
         Row(modifier = Modifier.fillMaxWidth()) {
             // NP (with VI), IF, TSS
         }
     }
     if (showBasicPhysicsRow) {
         Row(modifier = Modifier.fillMaxWidth()) {
             // Work (kJ), W/kg
         }
     }
     ```
   - When an athlete with power meter and configured FTP rides a workout, both `showCogganRow` and `showBasicPhysicsRow` evaluate to `true`, causing both rows to render simultaneously.

2. **Absence of Subtle Domain Row Dividers**:
   - The top-level `Column` in `WorkoutDetails` hosts:
     - `Row` (Active Time & Distance)
     - `AltitudeRow` (Ascent & Descent, conditional)
     - `PowerTrainingMetricsSection` (Power, conditional)
     - `AerobicEfficiencyRow` (EF & Decoupling, conditional)
     - `AnaerobicBatteryRow` (W'bal, conditional)
   - No dividers were placed between these sections. Furthermore, because each section is conditionally visible depending on sensor presence and settings toggles (`enableCogganPowerMetrics`, `enableAerobicEfficiencyModel`, `enableCriticalPowerModel`), dividers cannot be statically placed; they must only render when a subsequent domain row is actually visible.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Single-Line Power Row**:
    - Consolidate `PowerTrainingMetricsSection` into strictly *one single horizontal row* displaying the 2 or 3 most important power metrics:
      - When Coggan power metrics are enabled (`enableCogganPowerMetrics == true`) and `normalizedPowerWatts != null`:
        - If FTP-dependent metrics `IF` and `TSS` are available: render `[NP (with VI), IF, TSS]`.
        - If `IF` or `TSS` are absent (e.g. athlete has no FTP configured in profile): fallback to available physics metrics: `NP (with VI)`, `Work (kJ)`, `W/kg` (or subset thereof, max 3 columns).
      - When Coggan power metrics are disabled or `normalizedPowerWatts == null`:
        - Render `[Work (kJ), W/kg]` (or whichever is present, max 2 columns).
    - Every rendered column uses equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))`.
  * **Subtle Horizontal Dividers Between Visible Domain Rows**:
    - Insert a subtle `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` between consecutive visible domain rows in `WorkoutDetails.kt`.
    - Dividers are only rendered directly preceding a domain row if that domain row is actually visible, preventing trailing, leading, or duplicate consecutive dividers.
    - Set spacing rhythm to `verticalArrangement = Arrangement.spacedBy(12.dp)` to ensure balanced vertical margins around dividers.
  * **Modularity and Code Integrity**:
    - Maintain `WorkoutDetails.kt` clean, concise, and well under the 500-line modularity threshold.
  * **Unit & Contract Testing**:
    - Provide unit / contract tests verifying that `PowerTrainingMetricsSection` renders at most one `Row`, verifies priority fallback rules, and asserts divider presence between visible domain rows.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering the mathematical computation of NP, IF, TSS, Work kJ, or W/kg in `Banalservice` or repositories.
  * Modifying `AltitudeRow`, `AerobicEfficiencyRow`, or `AnaerobicBatteryRow` metric calculations or domain icons.
  * Changing summary cards in `WorkoutSummary.kt` or `MapDetailLayout.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Refines `REQ-UI-404` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails*).
* **Historical Origin & Commit Trace**: Sprint 2026-42.8 (`ATT-3930`, commit `c8939a3f`).
* **Root Reason for Existing Formulation**: When replacing legacy `FlowRow` pill badges, `REQ-UI-404.2` retained both Coggan metrics (`NP`, `IF`, `TSS`) and physics metrics (`Work`, `W/kg`) by rendering two consecutive `Row` composables in `PowerTrainingMetricsSection` to guarantee that no telemetry field was lost.
* **Preservation of Core Invariants**:
  - The canonical `WorkoutDomainRow` layout (`24.dp` domain icon, `20.dp` spacer, right-hand `MetricItem` column with `Modifier.weight(1f)`) defined in `docs/design_guidelines.md` Section 5.10 remains 100% intact.
  - Basic physics metrics (`Work kJ`, `W/kg`) remain available as primary metrics when Coggan metrics are disabled or when FTP is unconfigured.
  - Zero `FlowRow` and zero `surfaceContainerHighest` pill badges.
  - 100% 9-language translation parity.

---

## 5. Architectural Strategy & UI Layout Design

### Visual Layout Schema

```
+---------------------------------------------------------------------------------+
| WorkoutDetails (padding = 12.dp, spacedBy = 12.dp)                             |
|                                                                                 |
| [Active Time] (weight 1f)               [Distance] (weight 1f)                  |
| ------------------------------------------------------------------------------- | (HorizontalDivider)
| Höhenmeter (Icon)   | [Ascent] (weight 1f)        [Descent] (weight 1f)         |
| ------------------------------------------------------------------------------- | (HorizontalDivider)
| Leistung (Icon)     | [NP / VI] (weight 1f)  [IF] (weight 1f)  [TSS] (weight 1f)| (Single Line!)
| ------------------------------------------------------------------------------- | (HorizontalDivider)
| Effizienz (Icon)    | [EF] (weight 1f)            [Pw:Hr] (weight 1f)           |
| ------------------------------------------------------------------------------- | (HorizontalDivider)
| W'bal (Icon)        | [Min W'bal kJ] (weight 1f)  [Min W'bal %] (weight 1f)    |
+---------------------------------------------------------------------------------+
```

### Power Row Priority Resolution Logic

```kotlin
val showCoggan = enableCogganPowerMetrics && data.normalizedPowerWatts != null

if (showCoggan && data.intensityFactor != null && data.tss != null) {
    // Standard Coggan Trio: NP, IF, TSS
    renderItems(NP, IF, TSS)
} else if (showCoggan) {
    // Coggan with missing FTP: NP + fallback physics metrics
    val items = mutableListOf(NP)
    data.intensityFactor?.let { items.add(IF) }
    data.tss?.let { items.add(TSS) }
    data.workKj?.takeIf { items.size < 3 }?.let { items.add(Work) }
    data.avgPowerToWeight?.takeIf { items.size < 3 }?.let { items.add(Wkg) }
    renderItems(items)
} else if (data.workKj != null || data.avgPowerToWeight != null) {
    // Basic Physics: Work kJ, W/kg
    val items = mutableListOf()
    data.workKj?.let { items.add(Work) }
    data.avgPowerToWeight?.let { items.add(Wkg) }
    renderItems(items)
}
```

---

## 6. Verification Strategy

1. **Layout & Priority Unit Tests**:
   - Create `WorkoutDetailsLayoutTest.kt` verifying:
     - Full Coggan scenario (`NP`, `IF`, `TSS` present) renders exactly 3 metric items in a single row without a second row.
     - Partial Coggan scenario (`NP` present, `IF`/`TSS` null) falls back to `NP`, `Work`, `W/kg`.
     - Physics-only scenario (`enableCogganPowerMetrics = false` or `NP = null`) renders `Work` and `W/kg`.
     - Null scenario renders nothing.
2. **Horizontal Divider Visibility Contract Tests**:
   - Verify that `HorizontalDivider` instances are rendered between visible sections and omitted when adjacent sections are hidden.
3. **Existing Contract Test Parity**:
   - Run `PhysiologyModelsVisibilityContractTest.kt` and `AthleteProfileContractAndLocalizationTest.kt` to guarantee all existing Section 5.10 invariants and string keys remain satisfied.
4. **Clean-Room Test Regression**:
   - Run `./gradlew testDebugUnitTest` ensuring 100% pass rate.
