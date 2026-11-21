# Stage 5 Verification Walkthrough: ATT-9360 — Gate ACWR Until CTL >= 15.0 With Building-Base Hint & Normalize Tier-4 Session-RPE to TSS Scale

**Ticket**: `ATT-9360` (`2 SP`, `Bug`, `severity-minor`, Risk: `LOW`, Graphical `Rule 8.5`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469) | **Verified Code SHA**: `51ce6830` (`feature/ATT-9360` preserved intact for Stage 6 Cold Neon Visual Review per `Rule 3.3`)  
**Living Docs Promoted**: `REQ-WKO-039` (`docs/requirements/req_aftermath_periods.md` `v1.0.34`, `[Verified]`) <-> `TST-WKO-039` (`docs/tests/tst_aftermath_periods.md` `v1.0.38`, `[Verified]`); `REQ-UI-2792` (`docs/requirements/req_ui_design_system_part5.md` `v1.0.25`, `[Verified]`) <-> `TST-UI-2792` (`docs/tests/tst_ui_design_system_part3.md` `v1.0.26`, `[Verified]`)  
**Localization**: `9/9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT)` — trademark-free `athlete_profile_pmc_acwr_building_base` (`TranslationParityTest` `11/11 PASS`)

---

## 1. Root Cause & Implemented Fixes (`51ce6830`)
1. **Low-CTL ACWR Floor Gate (`REQ-WKO-039.1`, `PowerPhysiologyCalculator.kt`, `1,142 <= 1,500` LoC)**: Added `const val MIN_CTL_FOR_ACWR: Double = 15.0` and updated `computePerformanceManagementChart()` so `acwr = if (ctl >= MIN_CTL_FOR_ACWR) atl / ctl else null` and `acwrZone = acwr?.let { classifyAcwrZone(it) }`. Cold starts (`CTL = 7.0`, `CTL = 14.99`) no longer suffer denominator explosion or false `DANGER_ZONE` alerts; `CTL >= 15.0` evaluates `atl / ctl` and classifies `AcwrZone` identically to before.
2. **Tier-4 RPE-to-TSS Scale Normalization & `REQ-WKO-038` Invariant (`REQ-WKO-039.2`/`.3`)**: Added `@JvmStatic fun calculateRpeTss(rpe: Int, activeTimeSeconds: Double): Double` mapping Borg CR10 `1..10 -> 20, 30, 40, 50, 60, 70, 80, 100, 120, 140` TSS/hour scaled linearly by `(activeTimeSeconds / 3600.0)` (`1h @ RPE 8 = 100.0` TSS, matching 1h @ FTP/LTHR; `0.0` on out-of-range RPE or non-positive/non-finite durations) and wired `resolveWorkoutTrainingLoad()` Tier 4 to `calculateRpeTss(...)`. Preserved `calculateSessionRpe()` (`REQ-WKO-038`, `RPE * minutes`, `WorkoutRpeFeelSection.kt:144`) 100% untouched.
3. **Neutral Building-Base ACWR Card & 9-Locale Parity (`REQ-UI-2792.1`/`.2`, `PerformanceManagementChartCard.kt`, `618 <= 1,500` LoC + 9 `values*/strings.xml`)**: Added trademark-free `athlete_profile_pmc_acwr_building_base` across all 9 locales (`EN`: `Building base`, `DE`: `Aufbauphase`, `ES`: `Creando base`, `FR`: `Construction de base`, `IT`: `Costruzione base`, `JA`: `ベース構築中`, `NL`: `Basis opbouwen`, `PL`: `Budowanie bazy`, `PT`: `Construção de base`). When `currentAcwr == null && (pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty())`, the ACWR badge displays `"—"` + neutral localized building-base hint (`onSurfaceVariant` text, neutral `outlineVariant` border, zero red warning), while keeping `badgeText = null` on completely empty history and leaving `AthleteProfileScreen.kt` untouched (`1,596/1,975` LoC).

---

## 2. Bug Fix Verification Evidence (`REQ-PRO-121.2`, `Rule 7.5` Doc-Only Fast Path & `Rule 8.5` Visual Certification)
* **Zero Code Drift Since Gate 4 (`Rule 7.5`)**: `git diff --name-only 51ce6830..HEAD` touches `0` files under `app/`; Stage 5 directly reuses Gate 4's independently verified **`67/67 PASS`** (`ATT-9534` @ `51ce6830`, `0` new compiler warnings in touched files) without redundant Gradle re-execution.

| Tier-1 Targeted Suite (`--rerun`, Verified SHA `51ce6830`) | Result | Traces |
| :--- | :---: | :--- |
| `PowerPhysiologyCalculatorTest` | **`16/16 PASS`** | `TST-WKO-039.1`/`.2` (`CTL = 14.99 -> null`, `CTL = 15.0 -> atl/ctl`, all 10 `calculateRpeTss` tiers `20..140`, invalid/non-finite -> `0.0`, Tier-4 `RPE 8 = 100.0`, `REQ-WKO-038` `420.0` invariant) |
| `WorkoutRpeFeelTest` | **`11/11 PASS`** | `TST-WKO-039.2`, `TST-WKO-038` (Tier-4 `RPE 6 / 1h == 70.0`, Foster `calculateSessionRpe` unchanged) |
| `PerformanceManagementChartCardTest` | **`6/6 PASS`** | `TST-UI-2792.1`/`.3` (`v2.createComposeRule()` DE `"—"` + `"Aufbauphase"` unclipped, zero `"Risikobereich"`, empty-state `badgeText == null`, `Rule 8.5` Light/Dark PNG) |
| `AthleteProfileContractAndLocalizationTest` | **`8/8 PASS`** | `TST-UI-2792.2` (18 `athlete_profile_pmc_*` keys across 9 locales, layout & `currentLocale` contracts) |
| `TranslationParityTest` | **`11/11 PASS`** | `TST-UI-2792.2` (100% 9-locale parity: `EN, DE, ES, FR, IT, JA, NL, PL, PT`) |
| `ArchitectureConformanceContractTest` | **`11/11 PASS`** | `TST-UI-2792.2` (`PowerPhysiologyCalculator.kt` `1,142 <= 1,500`, `PerformanceManagementChartCard.kt` `618 <= 1,500`, `AthleteProfileScreen.kt` `1,596/1,975`) |
| `UiReflectionOnlyTestAuditContractTest` | **`4/4 PASS`** | `TST-UI-2792.2` (`0` reflection-only UI stubs, `REQ-PRO-132`) |
| **Tier-1 Targeted Total (`51ce6830`)** | **`67/67 PASS`** (`0` fail/err/skip/warnings) | Scope: 7 physiology/aftermath/athleteprofile/localization/build suites; full suite deferred to Tier-2 (`Rule 7.5`) |

* **`Rule 8.5` 4-Point Visual Certification (`/tmp/att9360_verify/ATT-9360_pmc_building_base_light_dark.png`, inspected via `read_file` by both `agent1` & `agent2`)**:
  1. *Orientation & Geometry*: Row 1 & Row 2 cards align with equal `IntrinsicSize.Min` heights, flush `3.dp` top color bars on Row 1, single-line 3-curve legend, clean `30 / 20 / 10 / 0 / -10 / -20` Y-axis ticks, zero blue fill below `0`, and `23.08.26`/`20.11.26` DE dates.
  2. *Color & Semantic Restraint*: Low-CTL (`CTL = 7.0 < 15.0`) ACWR card renders `—` + `Aufbauphase` in calm neutral `onSurfaceVariant` inside a neutral `outlineVariant` border across Light & Dark AMOLED — zero false red `Risikobereich` alarm.
  3. *Typography & Wrapping*: `Belastungsquote (ACWR)`, `—`, and `Aufbauphase` fit cleanly on single lines (`0` clipping/ellipsis); `Optimaler Aufbau` wraps cleanly onto 2 lines.
  4. *Touch Ergonomics*: All 5 time-range selector pills (`1M`, `3M`, `6M`, `1J`, `Alle`) remain spacious and unobstructed.
* **Safety Linters, Governance & Target Verification (`Rule 3.3`, `Rule 7.6`)**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` (`3/3 PASS`); `verify_requirement_governance.py --sync-indexes`, `--check-living-doc-layout`, and `--check-verified-evidence` (`EXIT 0`, `0` unbacked); `ANT-Android-SDKs` (`160000`) intact. Independent live `emulator-5554` deployment (`com.atrainingtracker.debug`) and live `screencap` inspection execute in Stage 6 Cold Neon Visual Review (`Rule 3.3`); `feature/ATT-9360` remains intact (NOT merged onto `sprint/2026-11-21_02_Spät`).
