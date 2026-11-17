# Stage 3: Implementation Plan - ATT-8440: RadarTrafficSummaryCard Canonical Speed Units

**Ticket**: [ATT-8440](https://rainerblind.atlassian.net/browse/ATT-8440) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8440` | **Risk**: `LOW` (`1 SP`, single composable)  
**Requirement Mapping**: `REQ-UI-2784` (`REQ-UI-2784.1`, `REQ-UI-2784.2`, refining `REQ-RAD-005` & `Rule 36`)  
**Test Mapping**: `TST-UI-2784` (`TST-UI-2784.1`..`TST-UI-2784.4`)

---

## 1. Architecture, UI Consistency & Traceability
* **Layer Boundaries**: Presentation-only change inside `RadarTrafficSummaryCard.kt` (`ui/aftermath/`). Read `val speedFormatter = LocalMetricFormatter.current.speed` (`MetricFormatterContext.kt:32-41`, backed by `SpeedFormatter.kt:26-46` and `MyHelper.mps2userUnit()`). `LocalMetricFormatter` uses `staticCompositionLocalOf { MetricFormatterContext() }`, so `WorkoutDetailTabbedContent.kt:932`, `@Preview`s, and Compose tests work without extra `CompositionLocalProvider` boilerplate.
* **UI Consistency (`docs/design_guidelines.md`)**: Preserves existing `MetricItem` (`MetricLayout.VERTICAL`) card geometry, spacing, and `TTColor` / Material3 surface tokens without adding `FilterChip`, `Switch`, `ElevatedCard`, or button labels. Light & Dark theme readability under `MyUnits.IMPERIAL` (`"mile/h"`) verified via Robolectric Compose screenshot (`Rule 8.5`). Zero SQLite, DTO, or `strings*.xml` edits.

| Step | Requirement | Test Case | Target Files |
| :--- | :--- | :--- | :--- |
| 1 | `REQ-UI-2784.1`, `REQ-UI-2784.2` | `TST-UI-2784.1`, `TST-UI-2784.2` | `RadarTrafficSummaryCard.kt` |
| 2 | `REQ-UI-2784.1`, `REQ-UI-2784.2` | `TST-UI-2784.1`..`TST-UI-2784.3` | `RadarTrafficSummaryCardTest.kt` (new Robolectric Compose test) |
| 3 | `REQ-UI-2784.1`..`TST-UI-2784.4` | `TST-UI-2784.3`, `TST-UI-2784.4` | Targeted unit tests + `Rule 8.5` Light/Dark PNG inspection |

## 2. Atomic Implementation Steps (Stage 4 Construction)
1. **Step 1 — Canonical Speed Formatting in `RadarTrafficSummaryCard.kt`**:
   - Import `com.atrainingtracker.trainingtracker.ui.util.LocalMetricFormatter`.
   - Inside `RadarTrafficSummaryCard`, capture `val speedFormatter = LocalMetricFormatter.current.speed` after the `L56` null/zero guard (`if (summary == null || summary.totalVehicles <= 0) return`).
   - Replace `L131` (`String.format(locale, "%.1f km/h", summary.maxApproachSpeedMps * 3.6)`) with `speedFormatter.format_with_units(summary.maxApproachSpeedMps)`.
   - Replace `L138` (`String.format(locale, "%.1f km/h", summary.maxVehicleSpeedMps * 3.6)`) with `speedFormatter.format_with_units(summary.maxVehicleSpeedMps)`.
   - Preserve `locale` for `vehiclesPerKm` (`L110`) and `vehiclesPerHour` (`L117`), `minPassingDistanceM` (`L157`), `WorkoutDetailTabbedContent.kt:932` call site, and `RadarTrafficSummaryFormatter.kt:78-82` untouched.
2. **Step 2 — Robolectric Compose UI & Visual Tests (`RadarTrafficSummaryCardTest.kt`)**:
   - Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/RadarTrafficSummaryCardTest.kt` (`@RunWith(RobolectricTestRunner::class)` with `createComposeRule()`), wrapping `RadarTrafficSummaryCard` in `ATrainingTrackerTheme`.
   - `@After` tearDown resets `TrainingApplication.setUnitForTesting(null)` (zero `returnDefaultValues = true`).
   - `TST-UI-2784.1`: With `totalVehicles = 12, maxApproachSpeedMps = 10.0, maxVehicleSpeedMps = 20.0`, verify `MyUnits.METRIC` renders `"36.0 km/h"`/`"36,0 km/h"` and `"72.0 km/h"`/`"72,0 km/h"`, and `MyUnits.IMPERIAL` renders `"22.4 mile/h"`/`"22,4 mile/h"` and `"44.7 mile/h"`/`"44,7 mile/h"` (zero `"km/h"` nodes).
   - `TST-UI-2784.2`: Verify `RadarTrafficSummaryCard(null)` and `RadarTrafficSummaryCard(RadarTrafficSummary(totalVehicles = 0))` emit zero root nodes, and non-speed metrics remain rendered when `totalVehicles > 0`.
   - `TST-UI-2784.3`: Capture Light & Dark imperial card PNG screenshot (`Rule 8.5`) into `docs/engineering/walkthroughs/img/` or test output and inspect via `read_file`.
3. **Step 3 — Tier 1 Verification (`TST-UI-2784.4`)**:
   - Run targeted tests: `./gradlew :app:testDebugUnitTest --tests "*RadarTrafficSummary*" --tests "*SpeedFormatterTest*" --max-workers=2` (all cases passing, 0 skipped).
   - Update `REQ-UI-2784` & `TST-UI-2784` to `[Verified]` in Stage 5 and sync living-doc indexes.

## 3. System Invariants & Rollback
* **Invariants**: Null/zero-vehicle early return (`L56`), non-speed metrics (`L110`, `L117`, `L157`), `WorkoutDetailTabbedContent.kt:932`, `RadarTrafficSummaryFormatter.kt` (`REQ-RAD-007`), zero `ANT-Android-SDKs` edits (`0e8cea714a231fb9565170d4ed01eafa868f9798`).
* **Rollback**: Single `git revert` on `feature/ATT-8440` before Strategy A squash-merge.
