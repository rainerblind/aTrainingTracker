# Stage 2 Requirement & Test Specification: ATT-9360 — Gate ACWR Until CTL >= 15.0 & Normalize Tier-4 Session-RPE to TSS Scale

**Ticket**: `ATT-9360` (`2 SP`, `Bug`, `severity-minor`, Risk: `LOW`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469) | **Base SHA**: `50578916` (`feature/ATT-9360`)  
**Living Docs Updated (Roomy Shards Only)**:
- Refined `REQ-WKO-039` (`[Planned]`) in `docs/requirements/req_aftermath_periods.md` (`v1.0.32`) <-> `TST-WKO-039` (`[Planned]`) in `docs/tests/tst_aftermath_periods.md` (`v1.0.36`)
- Added `REQ-UI-2792` (`[Planned]`) in `docs/requirements/req_ui_design_system_part5.md` (`v1.0.23`) <-> `TST-UI-2792` (`[Planned]`) in `docs/tests/tst_ui_design_system_part3.md` (`v1.0.24`)
- **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: `None` (`SWE-Derived` pure physiology math & Compose UI badge refinement; zero hardware/sensor/HAL/FGS/cloud API impact).

---

## 1. Formal Requirements Summary (`SHALL` / `MUST`)
* **`REQ-WKO-039` (High | Functional | Unit)**:
  1. `REQ-WKO-039.1` (*Low-CTL ACWR Gate*): `PowerPhysiologyCalculator` SHALL define `const val MIN_CTL_FOR_ACWR: Double = 15.0`. `computePerformanceManagementChart()` SHALL compute `acwr = atl / ctl` strictly when `ctl >= MIN_CTL_FOR_ACWR`, returning `acwr = null` and `acwrZone = null` (including `PmcSummaryResult.currentAcwr` / `currentAcwrZone`) whenever `ctl < MIN_CTL_FOR_ACWR`.
  2. `REQ-WKO-039.2` (*Tier-4 RPE-to-TSS Scale*): `PowerPhysiologyCalculator` SHALL expose `@JvmStatic fun calculateRpeTss(rpe: Int, activeTimeSeconds: Double): Double` mapping Borg CR10 `1..10` to `20, 30, 40, 50, 60, 70, 80, 100, 120, 140` TSS/hour scaled linearly by `(activeTimeSeconds / 3600.0)` (`1h @ RPE 8 = 100.0` TSS), returning `0.0` for `rpe !in 1..10` or `!activeTimeSeconds.isFinite() || activeTimeSeconds <= 0.0`.
  3. `REQ-WKO-039.3` (*Tier-4 Resolution & `REQ-WKO-038` Invariant*): `resolveWorkoutTrainingLoad()` Tier 4 SHALL invoke `calculateRpeTss(rpe, activeSec)`, preserving Tier 1–3 precedence (`powerTss` -> derived `TSS` -> `hrTSS`) and `FitFileWriter` metadata export. `calculateSessionRpe()` (`REQ-WKO-038`) MUST remain 100% unchanged (`RPE * minutes`) for `WorkoutRpeFeelSection.kt`.
* **`REQ-UI-2792` (Medium | Functional | Unit, Integration — refines `REQ-UI-401.1`/`.3`/`.4` & `ATT-9357`)**:
  1. `REQ-UI-2792.1` (*Neutral Building-Base ACWR Badge*): In `PerformanceManagementChartCard.kt`, when `pmcSummary.currentAcwr == null && (pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty())` (Gate 1 Advisory), the ACWR card SHALL remain visible showing `"—"` and `R.string.athlete_profile_pmc_acwr_building_base` in neutral `onSurfaceVariant` text + neutral `outlineVariant` border (MUST NEVER show red `error` / *Risikobereich*). When both lists are empty, `valueText` SHALL be `"—"` and `badgeText` SHALL be `null`. When `currentAcwr != null`, `currentLocale` `"%.2f"` formatting and semantic `AcwrZone` styling remain intact.
  2. `REQ-UI-2792.2` (*9-Locale Parity*): `athlete_profile_pmc_acwr_building_base` SHALL exist and be non-blank across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT` — 18 `athlete_profile_pmc_*` keys total).

---

## 2. Acceptance Criteria (`Given-When-Then`)
1. **Given** `CTL_t < 15.0` (e.g. `CTL = 7.0`, `ATL = 22.7`, or Day 0 `CTL = 2.38`), **When** `computePerformanceManagementChart()` evaluates day `t`, **Then** `acwr == null` and `acwrZone == null`; once `CTL_t >= 15.0`, `acwr == ATL_t / CTL_t` and `acwrZone` classifies normally.
2. **Given** a 60-min (`3600 s`) RPE-only workout (`RPE = 8`), **When** `resolveWorkoutTrainingLoad()` runs, **Then** it returns `100.0` TSS (all `1..10` levels match `20..140` TSS/h; invalid/non-finite return `0.0`), while `calculateSessionRpe(7, 3600.0)` still returns `420.0`.
3. **Given** `currentAcwr == null` with non-empty `dailyPoints` or `pmcPlotPoints`, **When** `PerformanceManagementChartCard` renders at `360 dp` (Light & Dark/AMOLED), **Then** ACWR displays `"—"` + unclipped neutral `"Aufbauphase"` (DE) / `"Building base"` (EN) with zero red alarm; when both lists are empty, `badgeText == null`.
4. **Given** all 9 locale `strings.xml` files, **When** `TranslationParityTest` and `AthleteProfileContractAndLocalizationTest` execute, **Then** 100% 9-locale parity passes.

---

## 3. 9-Language Localization Parity Table (`ATT-5678` Trademark-Free)
| Key | EN (`values/`) | DE (`values-de/`) | ES (`values-es/`) | FR (`values-fr/`) | IT (`values-it/`) | JA (`values-ja/`) | NL (`values-nl/`) | PL (`values-pl/`) | PT (`values-pt/`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `athlete_profile_pmc_acwr_building_base` | `Building base` | `Aufbauphase` | `Creando base` | `Construction de base` | `Costruzione base` | `ベース構築中` | `Basis opbouwen` | `Budowanie bazy` | `Construção de base` |

---

## 4. Bidirectional Traceability Matrix & Verification Plan
| Requirement | Test Spec | Test File / Suite | Concrete Verification Procedure |
| :--- | :--- | :--- | :--- |
| `REQ-WKO-039.1` | `TST-WKO-039.1` | `PowerPhysiologyCalculatorTest.kt` | Assert `acwr == null`/`acwrZone == null` for `CTL < 15.0` (Day 0 `2.38`, `7.0`, `14.99`) and exact `atl / ctl` + zone once `CTL >= 15.0`. |
| `REQ-WKO-039.2`/`.3` | `TST-WKO-039.2` | `PowerPhysiologyCalculatorTest.kt`, `WorkoutRpeFeelTest.kt` | Assert all 10 `calculateRpeTss` levels (`20..140` TSS/h), half-hour scaling, invalid/non-finite -> `0.0`, Tier-4 `RPE 8 = 100.0` / `RPE 7 = 80.0` / `RPE 6 = 70.0`, Tier 1–3 precedence, and `calculateSessionRpe(7, 3600.0) == 420.0` (`REQ-WKO-038`). |
| `REQ-UI-2792.1` | `TST-UI-2792.1`, `TST-UI-2792.3` | `PerformanceManagementChartCardTest.kt` + `emulator-5554` | Robolectric `v2.createComposeRule()` asserting `"—"` + unclipped neutral `"Aufbauphase"`/`"Building base"` when `currentAcwr == null` & non-empty points, `badgeText == null` on empty history, `Rule 8.5` Light/Dark PNG (`/tmp/att9360_verify/ATT-9360_pmc_building_base_light_dark.png`) inspected via `read_file`, plus live `emulator-5554` check. |
| `REQ-UI-2792.2` | `TST-UI-2792.2` | `TranslationParityTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`, `ArchitectureConformanceContractTest.kt` | Assert 100% 9-locale parity (18 `athlete_profile_pmc_*` keys), `PowerPhysiologyCalculator.kt <= 1,500` LoC, `PerformanceManagementChartCard.kt <= 1,500` LoC, `AthleteProfileScreen.kt` untouched (`1,596/1,975` LoC). |

---

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Modifies `REQ-WKO-039` (`PowerPhysiologyCalculator.kt`, `FitFileWriter.kt`) and refines `REQ-UI-401` (`REQ-UI-401.1`/`.3`/`.4`, `PerformanceManagementChartCard.kt`, `values*/strings.xml`).
2. **Historical Origin & Commit Trace**: `ATT-3859` (`dc62bb3c`) introduced `ctl > 0.01` ACWR guard and 17 `athlete_profile_pmc_*` strings; `ATT-6879` (`2c999318`) added `REQ-WKO-038`/`REQ-WKO-039` wiring raw `calculateSessionRpe()` into Tier 4; `ATT-8196` added FIT feel/RPE serialization; `ATT-9357` extracted `PerformanceManagementChartCard.kt`.
3. **Root Reason for Existing Formulation**: `ctl > 0.01` prevented divide-by-zero without addressing low-CTL denominator explosion; `ATT-6879` reused Foster `calculateSessionRpe()` (`RPE * minutes`) directly in Tier 4 without normalizing onto the `100` TSS/h scale; `currentAcwrZone == null` assumed empty history only.
4. **Preservation of Core Invariants**: Preserves `REQ-WKO-038` authentic Foster sRPE (`calculateSessionRpe()` 100% untouched for `WorkoutRpeFeelSection.kt:144`), Banister/Coggan `CTL`/`ATL`/`TSB`/`RampRate`, Tier 1–3 load precedence, `AcwrZone` thresholds (`0.80 / 1.30 / 1.50`), `FitFileWriter` metadata export, `ATT-9357` card/chart layout & `currentLocale` formatting, `AthleteProfileScreen.kt` (`1,596/1,975` LoC untouched), and `ATT-5678` trademark-free 9-locale strings.
