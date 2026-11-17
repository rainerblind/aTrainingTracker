# Stage 1 Analysis — ATT-8440

- **Ticket**: `ATT-8440` (`1 SP`, Bug/Improvement) — *RadarTrafficSummaryCard (ATT-5088): Format speeds with canonical unit helper instead of hardcoded `* 3.6` + `"km/h"`*
- **Branch / Base**: `feature/ATT-8440` @ `7321089659da47f1a42299a6ff47368e3af05e1a` (`ANT-Android-SDKs` untouched @ `0e8cea714a231fb9565170d4ed01eafa868f9798`)

## 1. Independent Peer Review & Verification
- **Defect Confirmed**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/RadarTrafficSummaryCard.kt` (`L131`, `L138`) formats `summary.maxApproachSpeedMps` and `summary.maxVehicleSpeedMps` via `String.format(locale, "%.1f km/h", ... * 3.6)`.
- **Impact**: Imperial users (`MyUnits.IMPERIAL`) still see `km/h` values on the post-workout radar card, violating `Rule 36` (Single Source of Truth for metric presentation).
- **Canonical Solution Pattern**:
  - `LocalMetricFormatter.current.speed` (`MetricFormatterContext.kt:32-41`, `SpeedFormatter.kt:26-46`) calls `MyHelper.mps2userUnit(value.toDouble())` (`* 3.6` for `METRIC`, `* 2.23693629` for `IMPERIAL`) and `format_with_units(value)` appends `"km/h"` (`METRIC`) or `"mile/h"` (`IMPERIAL`) via `TrainingApplication.getUnit()` (`strings.xml:131-132`, `SpeedFormatterTest.kt:46-60`).
  - Sibling aftermath composables (`TrackOnMapScreen.kt:164,170`, `WorkoutSummaryCompact.kt:215`, `WorkoutDetails.kt:166,247`) already use `LocalMetricFormatter.current.speed`.
  - Note: `SpeedFormatter.format_with_units()` emits `"mile/h"` (not `R.string.units_speed_short_imperial` = `"mi/h"` in `MyHelper.getShortSpeedUnitNameId()`), so reusing `LocalMetricFormatter.current.speed.format_with_units(...)` keeps 100% UI parity with all other aftermath cards and `Rule 36`.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-RAD-005` / `TST-RAD-005` (`docs/requirements/req_aftermath_periods.md:1130-1154`) and `Rule 36` (`docs/requirements/req_process_stability.md:857`).
- **Historical Origin & Commit Trace**: Introduced in `624bea5e` (`ATT-5088`); updated in `6a694bb1` for `LocalConfiguration.current.locales[0]` lint compliance.
- **Root Reason for Existing Formulation**: Quick initial implementation used `String.format(locale, "%.1f km/h", ... * 3.6)` before `LocalMetricFormatter.current.speed` alignment.
- **Preservation of Core Invariants**: Keep early-return guard `if (summary == null || summary.totalVehicles <= 0) return` (`L56`), `locale` for `vehiclesPerKm` (`L110`) / `vehiclesPerHour` (`L117`), and `Locale.getDefault()` in `SpeedFormatter`.

## 3. Side-Effect, Caller & Out-of-Scope Audit
- **Callers**: `RadarTrafficSummaryCard` has exactly 1 caller in `app/src/main/`: `WorkoutDetailTabbedContent.kt:932` (`WorkoutDetailSectionType.DETAILS`). No `@Preview` exists in `RadarTrafficSummaryCard.kt`.
- **CompositionLocal Safety**: `LocalMetricFormatter` uses `staticCompositionLocalOf { MetricFormatterContext() }`, so unit/Compose tests and callers without an explicit `CompositionLocalProvider` resolve safely to `SpeedFormatter()`.
- **Out-of-Scope Siblings (preserved for `1 SP` focus)**:
  - `RadarTrafficSummaryCard.kt:110` (`vehiclesPerKm` / `R.string.radar_traffic_density_km`) and `L157` (`minPassingDistanceM` formatted as `"%.1f m"`): distance/density units, outside speed scope.
  - `RadarTrafficSummaryFormatter.kt:78-82` (`REQ-RAD-007`): Strava/share formatter using `Math.round(... * 3.6)` and `R.string.radar_summary_max_speed*`; leave untouched unless requested as a follow-up ticket.

## Change-Request Impact Analysis
1. **Affected Requirements**:
   - `REQ-RAD-005` (`docs/requirements/req_aftermath_periods.md`): Refine speed-unit clause in `RadarTrafficSummaryCard` to mandate user-configured speed units (`km/h` / `mile/h`) via `LocalMetricFormatter.current.speed` (`Rule 36`).
   - `TST-RAD-005`: Add metric + imperial formatting test coverage for `RadarTrafficSummaryCard` speed values (e.g., extracted formatting helper or Robolectric/Compose card test).
2. **Affected Architecture Elements**:
   - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/RadarTrafficSummaryCard.kt` (`L131`, `L138`, plus import `LocalMetricFormatter`).
   - No database, DAO, `RadarTrafficSummary` DTO, or `ANT-Android-SDKs` changes required.
3. **Verification Impact & Test Surface**:
   - Existing suite: `RadarTrafficSummaryTest` (model math), `SpeedFormatterTest` (metric/imperial unit formatting).
   - Planned Stage 4 tests: Verify `RadarTrafficSummaryCard` speed formatting under `MyUnits.METRIC` (`km/h`) and `MyUnits.IMPERIAL` (`mile/h`) using `TrainingApplication.setUnitForTesting(...)` with `TearDown` reset to `null`.
4. **Risk Assessment & Invariants**:
   - **Risk**: Low (`1 SP`, single composable, 2 lines replaced).
   - **Invariants**: Null/zero-vehicle guard (`L56`), `TrainingApplication.setUnitForTesting(null)` cleanup in tests, zero `returnDefaultValues = true`, and zero `ANT-Android-SDKs` modification.
