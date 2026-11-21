# Stage 2 Requirement & Test Specification: ATT-9357 — PMC Chart Box-to-Curve Visual Mapping, Zero-Baseline CTL Fill, Rounded Y-Axis Ticks & Unclipped Badges

**Ticket**: `ATT-9357` (`2 SP`, `Bug`, `severity-minor`, Risk: `LOW`, Graphical `Rule 8.5`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Branch**: `feature/ATT-9357`  
**Living Docs**: `docs/requirements/req_ui_design_system_part5.md` (`v1.0.19`, `REQ-UI-2791` `[Planned]`), `docs/tests/tst_ui_design_system_part3.md` (`v1.0.20`, `TST-UI-2791` `[Planned]`)  
**Localization Parity**: `N/A - 0 new string resources` (reuses existing `ATT-5678` trademark-free 9-locale `athlete_profile_pmc_*` strings across `EN, DE, ES, FR, IT, JA, NL, PL, PT`)

---

## 1. Formal Requirements (`REQ-UI-2791`, `[Planned]`)
* **Attributes**: **Priority**: `Medium` | **Type**: `Functional` | **Verification Method**: `Unit, Integration` | **System Requirement(s)**: `Derived (SW-only — pure Compose UI refinement; zero SYS.2/SYS.3 impact)` | **Implementation**: `PerformanceManagementChartCard.kt`, `AthleteProfileScreen.kt`, `AthleteMetricsPlotMath.kt`

| ID | Normative Contract (`SHALL` / `MUST`) |
| :--- | :--- |
| **`REQ-UI-2791.1`** | Row 1 plotted KPI cards (`Fitness (42d)` -> `TTColor.PmcCtlBlue`, `Ermüdung (7d)` -> `TTColor.PmcAtlMagenta`, `Form (Balance)` -> `TTColor.PmcTsbAmber`) SHALL anchor card borders and visible curve swatch indicators strictly to their exact chart curve colors, while styling `Form (Balance)` readiness zone text (`TsbReadinessZone`) independently so readiness semantics MUST NEVER hijack curve identity border/swatch. Row 2 non-plotted KPIs (`ACWR`, `7T-Steigerung`) SHALL use neutral borders (`outlineVariant`) avoiding curve-color collisions while keeping semantic status coloring on status text/badge. A compact horizontal curve legend SHALL pair the 3 curve swatches with existing `ATT-5678` 9-locale `athlete_profile_pmc_*` labels. |
| **`REQ-UI-2791.2`** | `PmcStatBadge` readiness/status text (`badgeText`) SHALL wrap cleanly up to `2` lines (`maxLines = 2`, `softWrap = true`, `overflow = TextOverflow.Ellipsis`) so multi-word localized labels (specifically DE `Optimaler Aufbau` on `360–412 dp` 3-column mobile viewports) render completely without ellipsis clipping while preserving uniform aligned card heights (`Modifier.height(IntrinsicSize.Min)` + `fillMaxHeight()`). |
| **`REQ-UI-2791.3`** | `PerformanceManagementChartCanvas` SHALL anchor the CTL area `fillPath` closing baseline and vertical gradient `endY` strictly to clamped `zeroY = (topPad + plotH * (1f - ((0.0 - yMin) / yRange).toFloat())).coerceIn(topPad, topPad + plotH)` (`topPad + plotH` only when `yMin >= 0`), producing zero pale-blue CTL shading below `0` whenever `CTL = 0` and `yMin < 0`. |
| **`REQ-UI-2791.4`** | `AthleteMetricsPlotMath` SHALL provide a pure helper (`computePmcYAxisScale`) returning clean rounded Y-axis bounds and nice tick intervals (`5, 10, 15, 20, 25, 50, ...` scaled by powers of 10) explicitly including `0` whenever data spans or touches zero, rendering `0` once as an emphasized labeled tick and eliminating arbitrary ticks (`30 / 2 / -26`) and duplicate overlapping zero lines. |
| **`REQ-UI-2791.5`** | `PerformanceManagementChartCard` SHALL format `ACWR` using `currentLocale` (`LocalConfiguration.current.locales[0]`, `"%.2f"`) matching `CTL`/`ATL`/`TSB` (`0` `Locale.US` or `Locale.getDefault()` drift in `PerformanceManagementChartCard`). |
| **`REQ-UI-2791.6`** | `PerformanceManagementChartCard`, `PmcStatBadge`, and `PerformanceManagementChartCanvas` SHALL be extracted into `PerformanceManagementChartCard.kt` (`<= 1,500` LoC), keeping `AthleteProfileScreen.kt` cleanly within `(1500, 1975]` LoC (`ArchitectureConformanceContractTest.kt:125, :517`); Banister/Coggan `TSB_t = CTL_{t-1} - ATL_{t-1}` (`PowerPhysiologyCalculator.kt`) and `ATT-9360` stay untouched; `0` new strings. |

---

## 2. Acceptance Criteria (`Given-When-Then`)
1. **Box-to-Curve Mapping & Legend (`REQ-UI-2791.1`)**: *Given* populated PMC data with `TsbReadinessZone.OPTIMAL_OVERLOAD`, *When* `PerformanceManagementChartCard` renders, *Then* Row 1 cards show curve swatches & borders matching `PmcCtlBlue`, `PmcAtlMagenta`, and `PmcTsbAmber` alongside a 3-item curve legend, while Row 2 (`ACWR`, `7T-Steigerung`) uses neutral borders with semantic status text only.
2. **Unclipped Readiness Label & Uniform Row Heights (`REQ-UI-2791.2`)**: *Given* a `de-DE` `360 dp` viewport with `Optimaler Aufbau`, *When* rendered, *Then* `Optimaler Aufbau` renders completely (`hasVisualOverflow == false`) and all 3 Row 1 cards share equal height.
3. **CTL Fill Anchored to `zeroY` (`REQ-UI-2791.3`)**: *Given* a PMC series with `CTL = 0` days and negative `TSB` (`yMin < 0 < yMax`), *When* the canvas draws CTL fill, *Then* `fillPath` and gradient `endY` stop at `zeroY` with zero blue shading below `0`.
4. **Clean Rounded Y-Axis Ticks with Explicit `0` (`REQ-UI-2791.4`)**: *Given* a PMC series spanning `-21..+25`, all-zero, all-positive, or wide ranges, *When* `computePmcYAxisScale` computes ticks, *Then* ticks use clean rounded multiples including a single emphasized `0` when spanning/touching zero with no adjacent duplicate line.
5. **Locale Consistency & `Rule 8.5` Visual Verification (`REQ-UI-2791.5`, `.6`)**: *Given* `de-DE` vs `en-US` and Light/Dark Compose screenshots (`read_file` inspected) + `emulator-5554`, *Then* `ACWR` formats via `currentLocale`, zero clipping/overlap occurs, and `AthleteProfileScreen.kt` stays in `(1500, 1975]` LoC.

---

## 3. Traceability Matrix (`TST-UI-2791`, `[Planned]`)

| Test Case ID | Req ID | Target Test Suite | Verification Procedure & Expected Result |
| :--- | :--- | :--- | :--- |
| **`TST-UI-2791.1`** | `REQ-UI-2791.3`, `.4` | `AthleteMetricsDtoAndMathTest` | Pure unit tests for `computePmcYAxisScale` (zero-spanning `[-21, +25]`, all-zero `[0, 0]`, all-positive `[10, 85]`, negative-dominant `[-45, 5]`, elite `[-60, 140]`) confirming clean rounded multiples & single explicit `0`, plus `computePmcZeroBaselineY` clamping to `zeroY` vs `topPad + plotH`. |
| **`TST-UI-2791.2`** | `REQ-UI-2791.1`, `.2`, `.5` | `PerformanceManagementChartCardTest` | Robolectric `createComposeRule()` under `de-DE` `360 dp` & `en-US`: assert `Optimaler Aufbau` `hasVisualOverflow == false`, `ACWR` `"3,26"` vs `"3.26"` via `currentLocale`, Row 1 curve swatches/semantics (`PmcCtlBlue`/`PmcAtlMagenta`/`PmcTsbAmber`), equal Row 1/Row 2 heights, 3-item legend labels, and empty state. |
| **`TST-UI-2791.3`** | `REQ-UI-2791.1`–`.5` | `PerformanceManagementChartCardTest`, `emulator-5554` | Capture & visually inspect Light + Dark/AMOLED PNG (`docs/engineering/walkthroughs/img/ATT-9357_pmc_chart_light_dark.png`) via `read_file` (`Rule 8.5` 4-point checklist) reproducing Pixel 10 zero-CTL / negative-TSB / DE `Optimaler Aufbau` + live `emulator-5554` check. |
| **`TST-UI-2791.4`** | `REQ-UI-2791.6` | `AthleteProfileContractAndLocalizationTest`, `ArchitectureConformanceContractTest`, `UiReflectionOnlyTestAuditContractTest` | Verify updated contract test covers `PerformanceManagementChartCard.kt`, `AthleteProfileScreen.kt` stays cleanly within `(1500, 1975]` LoC (`PerformanceManagementChartCard.kt <= 1,500`), `0` reflection-only UI stubs, and `verify_requirement_governance.py` exit `0`. |

---

## 4. Chesterton's Fence Audit & System Invariants
* **Original Requirement Target**: Historical `ATT-3859` PMC UI, `ATT-5678` trademark-free 9-locale labels, and `ATT-4927` uniform PMC card heights/time-range selector (full legacy `REQ-*` cross-refs in `docs/requirements/req_ui_design_system_part5.md`).
* **Root Reason & Preserved Invariants**: Original `PmcStatBadge` shared one `accentColor` for border and status text and closed CTL fill at `topPad + plotH`. Fix preserves Banister/Coggan `TSB_t = CTL_{t-1} - ATL_{t-1}` (`PowerPhysiologyCalculator.kt` untouched), `ATT-9360` out-of-scope boundaries, `ATT-5678` trademark-free 9-locale strings (`0` new keys), `IntrinsicSize.Min` uniform heights, `CompactSelectorPill` time-range selector, and `AthleteProfileScreen.kt` inside `(1500, 1975]` LoC.
