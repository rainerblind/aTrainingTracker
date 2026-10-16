# Stage 3 Implementation Plan: ATT-3772

## 1. Architectural Overview (SWE.2) & Exhaustive Call-Site Impact Analysis
- **Target Components**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBarTest.kt`
- **Target Requirement & Test**: `REQ-UI-387` / `TST-UI-350`
- **Exhaustive Call-Site Impact Audit (`grep -rn "ZoneDistributionStackedBar" app/src/`)**:
  - Every file in the repository referencing `ZoneDistributionStackedBar` was audited:
    1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt` (Line 79: Composable definition)
    2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt` (Line 104: `ZoneCardDisplayMode.STACKED_BAR` branch)
    3. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt` (Line 104: `ZoneCardDisplayMode.STACKED_BAR` branch)
    4. `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBarTest.kt` (Unit test suite)
  - **Zero other call sites** exist anywhere in the codebase. Both production call sites (`HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`) represent the `ZoneCardDisplayMode.STACKED_BAR` branch targeted by `ATT-3772` and are updated to pass `showLegend = false` explicitly alongside changing the parameter default to `showLegend: Boolean = false`.

## 2. UI Consistency (Rule 23) & Dynamic Container Layout Stability
- **Closest Existing Reference**: `ZoneDistributionStackedBar.kt` (`REQ-UI-372`), which renders a `16.dp`-tall `RoundedCornerShape(6.dp)` continuous horizontal stacked bar with a `0.5.dp` `outlineVariant` border and `1.dp` inter-segment dividers.
- **Dynamic Container Sizing & Zero Empty-Space Guarantee**:
  - In `ZoneDistributionStackedBar.kt` (lines 92–145), the root container is a `Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp))` with natural wrap-content height (`height` is not fixed on the `Column` or parent `Card`).
  - The `FlowRow` legend block (lines 131–144) is conditionally composed inside `if (showLegend && distribution.entries.isNotEmpty())`.
  - When `showLegend == false`, the `FlowRow` is not composed into the layout tree at all; Compose's `Arrangement.spacedBy(8.dp)` only inserts spacing between composed children, so with 1 child (the `16.dp` horizontal bar `Box`), the `Column` height is strictly `16.dp` with **zero trailing gap, zero empty space, and zero layout jitter** in `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard`.

## 3. Atomic Implementation Steps

### Step 1: Default `showLegend = false` in `ZoneDistributionStackedBar.kt`, Document `@param showLegend` KDoc, & Pass `showLegend = false` in Cards
- **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt`
- **Action**:
  - In `ZoneDistributionStackedBar.kt`:
    - Change `showLegend: Boolean = true` to `showLegend: Boolean = false`.
    - Add explicit KDoc `@param showLegend` contract documenting that `showLegend` defaults to `false` for compact single-bar `STACKED_BAR` mode (`REQ-UI-387`), and when explicitly set to `true`, renders the responsive `FlowRow` per-zone breakdown legend underneath.
  - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`:
    - Explicitly pass `showLegend = false` to `ZoneDistributionStackedBar(distribution = distribution, modifier = Modifier.fillMaxWidth(), showLegend = false)` in the `ZoneCardDisplayMode.STACKED_BAR` branch.

### Step 2: Update `ZoneDistributionStackedBarTest.kt` (`TST-UI-350`) with Positive & Regression Tests
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBarTest.kt`
- **Action**:
  - **Positive Contract Test**: Verify that `ZoneDistributionStackedBar.kt` defaults `showLegend: Boolean = false` and that both `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` explicitly invoke `ZoneDistributionStackedBar` with `showLegend = false`.
  - **Regression Test (`showLegend = true` path)**: Verify that `ZoneDistributionStackedBar.kt` retains the conditional `if (showLegend && distribution.entries.isNotEmpty())` `FlowRow` + `StackedBarLegendItem` rendering block so callers explicitly passing `showLegend = true` continue to render the wrapping legend without regression.
- **Targeted Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionStackedBarTest"
  ```

## 4. Invariants & Rollback Safety
- Preserves `ZoneDistributionStackedBarMath`, proportional segment weights, `16.dp` bar height, `RoundedCornerShape(6.dp)`, `0.5.dp` outline border, optional `showLegend = true` rendering capability, and `COLUMNS` / `HISTOGRAM` display modes.
- 100% clean-room unit test pass rate preserved.
