# Stage 3: Implementation Plan - ATT-4042: Single-line Power row and clearer visual separation between domain rows in WorkoutDetails

**Ticket**: [ATT-4042](https://rainerblind.atlassian.net/browse/ATT-4042)  
**Sub-task**: [ATT-4228](https://rainerblind.atlassian.net/browse/ATT-4228) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-415` (*Single-Line Power Row and Visual Domain Row Separation with Subtle Horizontal Dividers in WorkoutDetails*)  
**Test Mapping**: `TST-UI-378` (*WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Fallback, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-4042`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

During the Sprint 2026-42.8 Joint Review of *ATT-3930* on Google Pixel 10 (attached review screenshot `ATT-3930_review_feedback.png`), visual inspection of the redesigned `WorkoutDetails` telemetry card highlighted two design cadence and ergonomics issues:
1. **Stacked Power Rows**: `PowerTrainingMetricsSection` rendered two stacked horizontal lines (`NP | IF | TSS` on row 1, and `Work (kJ) | W/kg` on row 2). All other domain rows (*Höhenmeter*, *Effizienz*, *W'bal*) are single horizontal lines; doubling the height of the Power row breaks visual rhythm and crowds the summary with secondary metrics.
2. **Visual Separation Between Domain Rows**: The consecutive domain rows (*Zeit aktiv / Distanz*, *Höhenmeter*, *Leistung*, *Effizienz*, *W'bal*) are positioned in an unseparated vertical column with `Arrangement.spacedBy(16.dp)`, causing domain blocks of disparate internal structures to visually blend together vertically.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-415` (*Single-Line Power Row and Visual Domain Row Separation with Subtle Horizontal Dividers in WorkoutDetails*)
  * `REQ-UI-415.1`: Single-Line Power & Coggan Load Row (`PowerTrainingMetricsSection` with priority fallback)
  * `REQ-UI-415.2`: Visual Separation Between Domain Rows in `WorkoutDetails` (subtle `HorizontalDivider`)
  * `REQ-UI-415.3`: Preservation of Design System Standards & Line Count Modularity (`docs/design_guidelines.md` Section 5.10, < 500 lines)
  * `REQ-UI-415.4`: 9-Language Localization Parity
* **Test Mapping**: `TST-UI-378` (*WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Fallback, and 9-Language Parity Verification Suite*)
  * `TST-UI-378.1`: Power Training Metrics Priority & Single-Line Fallback Unit Test (`WorkoutDetailsLayoutTest.kt`)
  * `TST-UI-378.2`: WorkoutDetails Layout & Divider Source Contract Test (`WorkoutDetailsLayoutTest.kt`)
  * `TST-UI-378.3`: Section 5.10 & Physiology Visibility Contract Test (`PhysiologyModelsVisibilityContractTest.kt`)
  * `TST-UI-378.4`: 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)
  * `TST-UI-378.5`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Canonical Domain Row Structure**: Adhere strictly to `docs/design_guidelines.md` Section 5.10 (`WorkoutDomainRow` with `24.dp` domain icon, `20.dp` spacer, and `Modifier.weight(1f)` item columns).
2. **Priority Fallback Invariant**: When Coggan metrics are enabled and NP is present:
   - Full Coggan (`IF` and `TSS` present): strictly render `[NP (with secondary VI), IF, TSS]` (3 columns).
   - Partial Coggan (`IF` or `TSS` missing due to unconfigured athlete FTP): start with `NP` (with secondary `VI`) and fill available slots up to 3 columns from `IF`, `TSS`, `Work (kJ)`, `W/kg`.
   - Disabled Coggan or NP absent: render available basic physics `[Work (kJ), W/kg]`.
3. **Divider Guard Invariant (Index-Based First-Element Guard)**:
   - Visible domain sections are collected into a sequential list.
   - Dividers are rendered via `visibleSections.forEachIndexed { index, section -> if (index > 0) HorizontalDivider(...); section() }`.
   - This mathematically guarantees ZERO leading divider (index 0 has no divider), ZERO trailing divider, and ZERO consecutive duplicate dividers, regardless of which subset of domain rows is visible.
4. **Anti-Pattern Prohibition**: Zero `FlowRow` and zero `surfaceContainerHighest` pill badges in `WorkoutDetails.kt`.
5. **Modularity Invariant**: `WorkoutDetails.kt` remains strictly under 500 lines.
6. **Layout Robustness & Font Scaling**:
   - `MetricItem(layout = MetricLayout.VERTICAL)` stacks label above value in equal-weight columns (`Modifier.weight(1f)`).
   - Labels and values use `maxLines = 1` with `TextOverflow.Ellipsis` and defensive auto-sizing to prevent line wrapping or vertical overflow on small displays (Pixel 10 or compact density buckets) across all 9 languages (including German compound words).
7. **Pure State Mapping & Recomposition Stability**:
   - The slot resolution logic is encapsulated in a pure, deterministic mapping function `resolvePowerMetricSlots(data, enableCogganPowerMetrics)`.
   - Free of side-effects, guaranteeing zero layout flicker and idempotent UI state updates.
8. **9-Language Parity**: All string resources and format specifiers remain 100% synchronized across all 9 application locales.

---

## 4. Proposed Architectural Changes

### Component 1: Pure Power Metric Slot Resolver & Single-Line Row

To guarantee pure mapping, testability, and state stability, declare an internal enum and pure function:

```kotlin
internal enum class PowerMetricSlot {
    NORMALIZED_POWER,
    INTENSITY_FACTOR,
    TSS,
    WORK_KJ,
    POWER_TO_WEIGHT
}

internal fun resolvePowerMetricSlots(
    data: WorkoutDetailsData,
    enableCogganPowerMetrics: Boolean
): List<PowerMetricSlot> {
    val showCoggan = enableCogganPowerMetrics && data.normalizedPowerWatts != null
    return when {
        showCoggan && data.intensityFactor != null && data.tss != null -> {
            listOf(PowerMetricSlot.NORMALIZED_POWER, PowerMetricSlot.INTENSITY_FACTOR, PowerMetricSlot.TSS)
        }
        showCoggan -> {
            val slots = mutableListOf(PowerMetricSlot.NORMALIZED_POWER)
            data.intensityFactor?.let { slots.add(PowerMetricSlot.INTENSITY_FACTOR) }
            data.tss?.let { slots.add(PowerMetricSlot.TSS) }
            if (slots.size < 3 && data.workKj != null) slots.add(PowerMetricSlot.WORK_KJ)
            if (slots.size < 3 && data.avgPowerToWeight != null) slots.add(PowerMetricSlot.POWER_TO_WEIGHT)
            slots
        }
        data.workKj != null || data.avgPowerToWeight != null -> {
            val slots = mutableListOf<PowerMetricSlot>()
            data.workKj?.let { slots.add(PowerMetricSlot.WORK_KJ) }
            data.avgPowerToWeight?.let { slots.add(PowerMetricSlot.POWER_TO_WEIGHT) }
            slots
        }
        else -> emptyList()
    }
}
```

In `PowerTrainingMetricsSection`:
- Evaluates `val slots = resolvePowerMetricSlots(data, enableCogganPowerMetrics)`.
- If `slots.isEmpty()`, returns immediately without rendering.
- Renders `WorkoutDomainRow` with exactly one horizontal `Row(modifier = Modifier.fillMaxWidth())`.
- For each slot, renders the corresponding `MetricItem(..., layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))`.

### Component 2: Declarative Section Composition & Index-Based Divider Guard

In `WorkoutDetails.kt`, structure the domain rows as a sequential collection with an index-based divider guard:

```kotlin
@Composable
fun WorkoutDetails(...) {
    val formatters = LocalMetricFormatter.current

    val visibleSections = buildList<@Composable () -> Unit> {
        // Section 1: Active Time & Distance (always visible)
        add {
            MetricsTimeDistanceRow(data = data, formatters = formatters)
        }

        // Section 2: Altitude (conditional on ascent or descent > 0)
        if (data.ascentMeters > 0 || data.descentMeters > 0) {
            add {
                AltitudeRow(ascentMeters = data.ascentMeters, descentMeters = data.descentMeters)
            }
        }

        // Section 3: Power (conditional on non-empty power slots)
        val powerSlots = resolvePowerMetricSlots(data, enableCogganPowerMetrics)
        if (powerSlots.isNotEmpty()) {
            add {
                PowerTrainingMetricsSection(data = data, slots = powerSlots)
            }
        }

        // Section 4: Efficiency Factor & Decoupling (conditional on model & EF presence)
        if (enableAerobicEfficiencyModel && data.efficiencyFactor != null) {
            add {
                AerobicEfficiencyRow(data = data)
            }
        }

        // Section 5: Anaerobic Battery (conditional on model & W'bal presence)
        if (enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null) {
            add {
                AnaerobicBatteryRow(data = data)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        visibleSections.forEachIndexed { index, section ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            section()
        }
    }
}
```

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `WorkoutDetails.kt` (`docs/design_guidelines.md` Section 5.10 `AltitudeRow` pattern) and `WorkoutSummary.kt` domain card dividers.
* **Reused components**: `HorizontalDivider` (`androidx.compose.material3`), `WorkoutDomainRow`, `MetricItem` (`ui/components/MetricItem.kt`).
* **Theme tokens**:
  - Color: `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)` for subtle horizontal dividers.
  - Spacing: `12.dp` column vertical spacing, `12.dp` padding, `20.dp` domain row horizontal spacer.
  - Typography: `labelMedium` domain label, `titleMedium` / `bodyMedium` in `MetricItem`.
* **New one-off styles & justification**: None. Employs canonical Material 3 `HorizontalDivider` with the established project-standard `outlineVariant.copy(alpha = 0.5f)` token.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Unit Test Implementation (`WorkoutDetailsLayoutTest.kt`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`
* Test cases:
  1. `testResolvePowerMetricSlots_FullCogganTrio`: Verify `[NP, IF, TSS]` is returned when Coggan is enabled and all three are present.
  2. `testResolvePowerMetricSlots_MissingFtpFallback`: Verify `[NP, WORK_KJ, POWER_TO_WEIGHT]` is returned when IF/TSS are null.
  3. `testResolvePowerMetricSlots_BasicPhysicsOnly`: Verify `[WORK_KJ, POWER_TO_WEIGHT]` is returned when Coggan is disabled or NP is null.
  4. `testResolvePowerMetricSlots_AllNull`: Verify empty list is returned.
  5. `testSingleLinePowerRowAndDividerContract`: Inspect source code of `WorkoutDetails.kt` to assert:
     - `PowerTrainingMetricsSection` contains at most one `Row(modifier = Modifier.fillMaxWidth())`.
     - `visibleSections.forEachIndexed` and `if (index > 0)` index-based divider guard is used.
     - Spacing uses `Arrangement.spacedBy(12.dp)`.
     - File length is strictly < 500 lines.

### Step 2: Refactor `PowerTrainingMetricsSection` in `WorkoutDetails.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`
* Add `PowerMetricSlot` enum and `resolvePowerMetricSlots` pure resolver function.
* Update `PowerTrainingMetricsSection` to render a single horizontal row distributing slots equally with `Modifier.weight(1f)`.

### Step 3: Implement Visual Separation with Index-Based Divider Guard in `WorkoutDetails.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt`
* Extract `MetricsTimeDistanceRow` private composable for section 1.
* Compose `visibleSections` list and render with `visibleSections.forEachIndexed { index, section -> if (index > 0) HorizontalDivider(...); section() }`.
* Set column vertical spacing to `Arrangement.spacedBy(12.dp)`.

### Step 4: Verification via Targeted & Full Test Suites
* Run targeted tests: `./gradlew testDebugUnitTest --tests "*WorkoutDetails*"`
* Run contract tests: `./gradlew testDebugUnitTest --tests "*PhysiologyModelsVisibilityContractTest*"` and `AthleteProfileContractAndLocalizationTest`
* Run full suite: `./gradlew testDebugUnitTest`
