# Stage 3 Implementation Plan: ATT-9360 — Gate ACWR Until CTL >= 15.0 With Building-Base Hint & Normalize Tier-4 Session-RPE to TSS Scale

**Ticket**: `ATT-9360` (`2 SP`, `Bug`, `severity-minor`, Risk: `LOW`, Graphical `Rule 8.5`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_02_Spät` (Sprint 469) | **Branch**: `feature/ATT-9360`  
**Traces**: `REQ-WKO-039` (`REQ-WKO-039.1`–`.3`) <-> `TST-WKO-039` (`TST-WKO-039.1`–`.2`); `REQ-UI-2792` (`REQ-UI-2792.1`–`.2`) <-> `TST-UI-2792` (`TST-UI-2792.1`–`.3`)  
**DB Migrations**: `None` (`0` schema bumps) | **Localization**: `9/9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT)` — 1 trademark-free key `athlete_profile_pmc_acwr_building_base` in `app/src/main/res/values*/strings.xml`

---

## 1. SWE.2 / SWE.3 Architectural Design & UI Consistency (`docs/design_guidelines.md`)
* **Layer Boundaries & Monolith Compliance (`ArchitectureConformanceContractTest`)**:
  - **Domain Math (`PowerPhysiologyCalculator.kt`, `1,114 -> ~1,145` LoC `<= 1,500`)**: Pure, stateless, thread-safe Kotlin functions (`@JvmStatic`). Zero Android framework or DB dependencies. `calculateSessionRpe()` (`REQ-WKO-038`, `WorkoutRpeFeelSection.kt:144`) remains 100% untouched.
  - **UI Presentation (`PerformanceManagementChartCard.kt`, `611 -> ~625` LoC `<= 1,500`)**: Extracted in `ATT-9357`; `AthleteProfileScreen.kt` (`1,596/1,975` LoC) and `AthleteProfileViewModel.kt` remain untouched.
  - **SWE.2 / SWE.3 Docs (`Rule 8.8`, `REQ-PRO-114`)**: `None` — zero new subsystems, services, DB managers, repositories, or prefixes (`docs/architecture.md` and `docs/architecture/detailed_design/*.md` unchanged).
  - **Production Reachability (`Rule 81`)**: `AthleteProfileViewModel.kt:280` -> `PowerPhysiologyCalculator.computePerformanceManagementChart()` -> `AthleteProfileScreen.kt:305` -> `PerformanceManagementChartCard(...)`.
* **UI Consistency (`docs/design_guidelines.md` §§2, 4, 5)**:
  - Preserves `ATT-9357` Row 2 neutral `MaterialTheme.colorScheme.outlineVariant` card border and `PmcStatBadge` `maxLines = 2, softWrap = true` (`IntrinsicSize.Min` uniform row heights, `0` Compose snapshot writes during composition per `Rule 66`).
  - When `pmcSummary.currentAcwr == null && (pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty())` (Gate 1 Advisory), ACWR renders `"—"` as `valueText` and localized `R.string.athlete_profile_pmc_acwr_building_base` (`EN`: `Building base`, `DE`: `Aufbauphase`, ...) in neutral `MaterialTheme.colorScheme.onSurfaceVariant` — NEVER red `error` / *Risikobereich*. When both `dailyPoints` and `pmcPlotPoints` are empty, `badgeText = null`.

---

## 2. Atomic Implementation Steps (`Stage 4` — Locked Until Gate 3 `PASS`)

| Step | Target File(s) | Atomic Change | Traces |
| :---: | :--- | :--- | :--- |
| **1** | `PowerPhysiologyCalculator.kt` | Add `const val MIN_CTL_FOR_ACWR: Double = 15.0` alongside `CTL_TAU_DAYS`/`ATL_TAU_DAYS`. In `computePerformanceManagementChart()` (`:1073`), gate `val acwr = if (ctl >= MIN_CTL_FOR_ACWR) atl / ctl else null` (propagating `null` to both `PmcDailyPoint.acwr`/`acwrZone` and `PmcSummaryResult.currentAcwr`/`currentAcwrZone`). Add `@JvmStatic fun calculateRpeTss(rpe: Int, activeTimeSeconds: Double): Double` mapping Borg CR10 `1..10 -> 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 100.0, 120.0, 140.0` TSS/h scaled by `(activeTimeSeconds / 3600.0)` (`0.0` when `rpe !in 1..10` or `!activeTimeSeconds.isFinite() || activeTimeSeconds <= 0.0`). Wire `resolveWorkoutTrainingLoad()` Tier 4 (`:993`) to `calculateRpeTss(rpe, activeSec)`, keeping `calculateSessionRpe()` (`REQ-WKO-038`) 100% untouched. | `REQ-WKO-039.1`, `.2`, `.3` |
| **2** | `PerformanceManagementChartCard.kt`, `app/src/main/res/values*/strings.xml` (9 locales) | Add trademark-free `athlete_profile_pmc_acwr_building_base` directly after `athlete_profile_pmc_acwr_danger` in all 9 `strings.xml` files (`values/`: `Building base`, `values-de/`: `Aufbauphase`, `values-es/`: `Creando base`, `values-fr/`: `Construction de base`, `values-it/`: `Costruzione base`, `values-ja/`: `ベース構築中`, `values-nl/`: `Basis opbouwen`, `values-pl/`: `Budowanie bazy`, `values-pt/`: `Construção de base`). In `PerformanceManagementChartCard.kt` (`:174-205`), resolve `acwrZoneText` for `null` zone to `stringResource(R.string.athlete_profile_pmc_acwr_building_base)` iff `pmcSummary.currentAcwr == null && (pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty())`, else `null`; retain `onSurfaceVariant` `statusColor`, neutral `outlineVariant` border, and `badgeTextTestTag = "pmc_acwr_zone_text"` for clean Compose assertions. Update Light/Dark `@Preview` helper or add a Building-Base preview. | `REQ-UI-2792.1`, `.2` |
| **3** | `PowerPhysiologyCalculatorTest.kt`, `WorkoutRpeFeelTest.kt`, `PerformanceManagementChartCardTest.kt`, `AthleteProfileContractAndLocalizationTest.kt` | (a) Update `PowerPhysiologyCalculatorTest`: Tier-4 `RPE 7 / 1h` -> `80.0`, Day 0 (`CTL = 2.38 < 15.0`) -> `acwr == null`/`acwrZone == null`; add boundary tests (`CTL = 14.99` -> `null`, `CTL >= 15.0` -> `atl / ctl` + zone), all 10 `calculateRpeTss` tiers (`1..10 -> 20..140`), `1800 s` scaling, invalid/non-finite (`0, 11, 0.0, -60.0, NaN, Infinity` -> `0.0`), Tier-4 `1h @ RPE 8 == 100.0`, and `calculateSessionRpe(7, 3600.0) == 420.0` invariant. (b) Update `WorkoutRpeFeelTest`: Tier-4 `RPE 6 / 1h` -> `70.0` while verifying `calculateSessionRpe` unchanged. (c) Add Robolectric `v2.createComposeRule()` tests in `PerformanceManagementChartCardTest`: low-CTL `currentAcwr == null` with populated points renders `"—"` + unclipped neutral DE `"Aufbauphase"` / EN `"Building base"` (zero `"Risikobereich"`/`"Danger Zone"`), empty history renders `"—"` with no building-base badge, plus `Rule 8.5` Light/Dark PNG (`/tmp/att9360_verify/ATT-9360_pmc_building_base_light_dark.png`) inspected via `read_file`. (d) Add `athlete_profile_pmc_acwr_building_base` to `AthleteProfileContractAndLocalizationTest` (18 keys) and run `TranslationParityTest` (`Rule 7.5`). | `TST-WKO-039.1`–`.2`, `TST-UI-2792.1`–`.3` |

---

## 3. Targeted Verification Command (Tier 1, `Rule 7.5` & `Rule 7.6`)
```bash
./gradlew :app:testDebugUnitTest --rerun -Dkotlin.daemon.jvm.options=-Xmx3072m \
  --tests "com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculatorTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRpeFeelTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.PerformanceManagementChartCardTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteProfileContractAndLocalizationTest" \
  --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"
```
* Plus `3/3` Python safety linters (`lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`), `verify_requirement_governance.py --sync-indexes --base-ref sprint/2026-11-21_02_Spät`, `Rule 8.5` PNG self-inspection via `read_file`, and live `emulator-5554` verification in Stage 5/6 (`Rule 7.6`).

---

## 4. Non-Negotiable System Invariants & Rollback Safety
* `calculateSessionRpe()` (`REQ-WKO-038`, `WorkoutRpeFeelSection.kt:144`) remains **100% untouched** (`RPE * minutes`); Banister/Coggan `CTL`/`ATL`/`TSB`/`RampRate`, Tier 1–3 precedence, and `AcwrZone` thresholds (`0.80 / 1.30 / 1.50`) remain untouched; `AthleteProfileScreen.kt` (`1,596/1,975` LoC) untouched; `PowerPhysiologyCalculator.kt <= 1,500` LoC & `PerformanceManagementChartCard.kt <= 1,500` LoC; `ATT-5678` trademark-free 9-locale parity (`TranslationParityTest`) green; `Rule 8.5` PNG + `Rule 7.6` `emulator-5554` verification enforced; Rule 3 Human Gate preserved.
