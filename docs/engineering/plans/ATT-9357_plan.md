# Stage 3 Implementation Plan: ATT-9357 — PMC Chart Box-to-Curve Mapping, Zero-Baseline CTL Fill, Rounded Y-Axis Ticks & Unclipped Badges

**Ticket**: `ATT-9357` (`2 SP`, `Bug`, `severity-minor`, Risk: `LOW`, Graphical `Rule 8.5`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Branch**: `feature/ATT-9357`  
**Traces**: `REQ-UI-2791` (`REQ-UI-2791.1`–`.6`) <-> `TST-UI-2791` (`TST-UI-2791.1`–`.4`)  
**DB Migrations**: `None` (`0` schema bumps) | **Localization**: `N/A - 0 new strings` (reuses `ATT-5678` 9-locale `athlete_profile_pmc_*` strings)

---

## 1. SWE.2 / SWE.3 Architectural Design & UI Consistency (`docs/design_guidelines.md`)
* **Layer Boundaries & Monolith Compliance (`ArchitectureConformanceContractTest.kt:125, :517`)**:
  - `AthleteProfileScreen.kt` currently sits at `1,975 / 1,975` LoC (`0` headroom). Extract `PerformanceManagementChartCard`, `PmcStatBadge`, and `PerformanceManagementChartCanvas` into sibling **`PerformanceManagementChartCard.kt`** (`~430` LoC `<= 1,500`, modeled on `CardioPowerTrendsCard.kt`), reducing `AthleteProfileScreen.kt` to **`~1,600` LoC** — cleanly inside `(1500, 1975]` (neither exceeding `1,975` nor tripping the `<= 1,500` stale-baseline check).
  - Pure Y-axis tick/bound math lives in **`AthleteMetricsPlotMath.kt`** (`245 -> ~320` LoC). `PowerPhysiologyCalculator.kt` (Banister/Coggan `TSB_t = CTL_{t-1} - ATL_{t-1}` & `ATT-9360`), `AthleteProfileViewModel.kt`, and `WorkoutSummariesDatabaseManager.java` remain strictly untouched.
  - **SWE.2 / SWE.3 Docs (`Rule 8.8`, `REQ-PRO-114`)**: Zero new subsystems, services, DB managers, repositories, or prefixes -> `docs/architecture.md` and `docs/architecture/detailed_design/*.md` remain unchanged.
  - **Production Reachability (`Rule 81`)**: `AthleteProfileScreen.kt:311` continues invoking `PerformanceManagementChartCard(...)` directly in the live `Fitness & Fatigue` tab.
* **UI Consistency (`docs/design_guidelines.md` §§2, 4, 5)**:
  - **Spacing & Shapes**: Strictly `4.dp`/`8.dp`/`12.dp` spacing, `RoundedCornerShape(12.dp)` card, `RoundedCornerShape(8.dp)` badges, `RoundedCornerShape(2.dp)` curve swatches (`12.dp x 3.dp` horizontal bar).
  - **Color Separation**: Row 1 (`Fitness (42d)`=`TTColor.PmcCtlBlue`, `Ermüdung (7d)`=`TTColor.PmcAtlMagenta`, `Form (Balance)`=`TTColor.PmcTsbAmber`) uses curve color strictly for card border (`alpha = 0.55f`) and curve swatch, while `Form (Balance)`'s readiness text uses semantic `tsbZoneColor` independently (`PmcFreshGreen` / `onSurfaceVariant` / `PmcOptimalBlue` / `error`). Row 2 (`ACWR`, `7T-Steigerung`) uses neutral `MaterialTheme.colorScheme.outlineVariant` borders (`0` collision with `PmcCtlBlue`/`PmcAtlMagenta`) with semantic status coloring strictly on `badgeText`. Zero inline `Color(0x...)` literals.
  - **Typography & Wrapping**: `PmcStatBadge` `badgeText` uses `maxLines = 2`, `softWrap = true`, `overflow = TextOverflow.Ellipsis` so DE `Optimaler Aufbau` wraps cleanly without ellipsis on `360 dp` while `Modifier.height(IntrinsicSize.Min)` + `fillMaxHeight()` keeps equal row heights. Compact horizontal legend directly above the canvas reuses `athlete_profile_pmc_ctl_label`, `athlete_profile_pmc_atl_label`, `athlete_profile_pmc_tsb_label`. Light & Dark/AMOLED `@Preview` composables included; `0` Compose snapshot writes during composition (`Rule 66`).

---

## 2. Atomic Implementation Steps (`Stage 4` — Locked Until Gate 3 `PASS`)

| Step | Target File(s) | Atomic Change | Traces |
| :---: | :--- | :--- | :--- |
| **1** | `AthleteMetricsPlotMath.kt` | Add `data class PmcYAxisScale(val yMin: Float, val yMax: Float, val ticks: List<Float>)`, `computePmcYAxisScale(rawMin: Float, rawMax: Float): PmcYAxisScale` (+ `List<PmcDailyPoint>` overload) selecting nice steps (`5, 10, 15, 20, 25, 50, 100, ...`, `3..6` ticks) that explicitly include `0f` whenever data spans/touches zero (handling flat all-zero `[0, 10]`/`[0, 5, 10]`, all-positive, all-negative, tiny & elite `> 100` ranges without duplicate zero ticks), and `computePmcZeroBaselineY(yMin, yMax, topPad, plotH): Float` clamped to `[topPad, topPad + plotH]`. | `REQ-UI-2791.3`, `REQ-UI-2791.4` |
| **2** | `PerformanceManagementChartCard.kt` *(new)*, `AthleteProfileScreen.kt` | Extract `PerformanceManagementChartCard`, `PmcStatBadge`, and `PerformanceManagementChartCanvas` into `PerformanceManagementChartCard.kt` (shrinking `AthleteProfileScreen.kt` to `~1,600` LoC inside `(1500, 1975]`). Split `PmcStatBadge` into `borderColor`, `curveSwatchColor: Color?`, and `statusColor` + `badgeText` `maxLines = 2, softWrap = true` (`IntrinsicSize.Min` preserved). Anchor Row 1 to `PmcCtlBlue`/`PmcAtlMagenta`/`PmcTsbAmber` with visible swatches + compact legend row; give Row 2 neutral `outlineVariant` borders; format `ACWR` with `currentLocale` (`LocalConfiguration.current.locales[0]`). In `PerformanceManagementChartCanvas`, anchor CTL `fillPath` & gradient `endY` to `zeroY = computePmcZeroBaselineY(...)` and draw `PmcYAxisScale.ticks` with `0` emphasized once (`0` duplicate zero line). Add Light & Dark/AMOLED `@Preview`s. | `REQ-UI-2791.1`, `.2`, `.3`, `.4`, `.5`, `.6` |
| **3** | `AthleteMetricsDtoAndMathTest.kt`, `PerformanceManagementChartCardTest.kt` *(new)*, `AthleteProfileContractAndLocalizationTest.kt` | (a) Add pure unit tests in `AthleteMetricsDtoAndMathTest` for `computePmcYAxisScale` (Pixel 10 `[-21, +25]`, flat-zero, all-positive, negative-dominant, wide `[-60, 140]`) & `computePmcZeroBaselineY`. (b) Add Robolectric `createComposeRule()` UI & `Rule 8.5` Light/Dark PNG screenshot tests (`docs/engineering/walkthroughs/img/ATT-9357_pmc_chart_light_dark.png`, inspected via `read_file`) verifying DE `360 dp` `Optimaler Aufbau` (`hasVisualOverflow == false`), `de-DE` vs `en-US` `ACWR` formatting, Row 1 swatches/legend, equal heights, and empty state. (c) Update `AthleteProfileContractAndLocalizationTest` to assert `PerformanceManagementChartCard(` call in `AthleteProfileScreen.kt` + PMC contracts in `PerformanceManagementChartCard.kt` (`0` `Locale.US`), and verify `ArchitectureConformanceContractTest` & `UiReflectionOnlyTestAuditContractTest`. | `TST-UI-2791.1`–`.4` |
| **4** | Tier 1 Verification, `Rule 8.5` PNG Audit & Living Docs | Run Tier-1 targeted Gradle suites (below), inspect `ATT-9357_pmc_chart_light_dark.png` via `read_file` against 4-point checklist, promote `REQ-UI-2791`/`TST-UI-2791` to `[Implemented]`, sync indexes, and verify `verify_requirement_governance.py`; live `emulator-5554` verification executes in Stage 5/6 (`Rule 7.6`). | `TST-UI-2791.3`, `.4` |

---

## 3. Targeted Verification Command (Tier 1, `Rule 7.5`)
```bash
./gradlew :app:testDebugUnitTest --rerun -Dkotlin.daemon.jvm.options=-Xmx3072m \
  --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsDtoAndMathTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.PerformanceManagementChartCardTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestScreenTest" \
  --tests "com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculatorTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"
```

---

## 4. Non-Negotiable System Invariants & Rollback Safety
* `AthleteProfileScreen.kt` strictly inside `(1500, 1975]` LoC (`PerformanceManagementChartCard.kt <= 1,500` LoC); `PowerPhysiologyCalculator.kt` (`TSB_t = CTL_{t-1} - ATL_{t-1}`) & `ATT-9360` untouched; `ATT-5678` trademark-free 9-locale strings reused (`0` new strings); `IntrinsicSize.Min` + `fillMaxHeight()` uniform heights and `CompactSelectorPill` time-range selector preserved; `0` Compose snapshot writes during composition (`Rule 66`); `Rule 8.5` PNG + `Rule 7.6` `emulator-5554` verification enforced; Rule 3 Human Gate preserved.
