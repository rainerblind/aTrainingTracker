# ATT-3906 Walkthrough: Longitudinal Season Trends for Fresh HR@200W, Time Constant tau, and Slope 1/K in Athlete Profile

**Ticket**: [ATT-3906](https://rainerblind.atlassian.net/browse/ATT-3906)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: [`REQ-REV-011`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_aftermath_reviews.md) | **Test Mapping**: [`TST-REV-012`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_reviews.md)  
**Branch**: `feature/ATT-3906`

---

## 1. Executive Summary

`ATT-3906` completes the 3-ticket Cardio-Power epic (`ATT-3903`) by delivering longitudinal season trend monitoring for 1st-order dynamical Power-HR parameters in Athlete Profile:
- **Database Time-Series Ingestion (`WorkoutSummariesDatabaseManager.java`)**: Added `CardioPowerTrendSample` DTO and `getCardioPowerDynamicsFitnessTrendSamples(double minR2)` joining `cardio_power_dynamics` and `WorkoutSummaries`, filtering by model quality ($R^2 \ge 0.50$) and sorted ascending by workout start timestamp.
- **Pure Domain Math & Outlier Resilience (`CardioPowerTrendsPlotMath.kt`)**: Implemented pure conversion functions (`convertSamplesToPoints`, `filterPointsByTimeRange`, `computeRollingTrend`), 30-day centered rolling trend curves, and Tukey IQR outlier-resilient Y-domain clamping ($[Q_1 - 1.5\cdot\text{IQR}, Q_3 + 1.5\cdot\text{IQR}]$ for $N \ge 4$) to resist acute distortion while preserving longitudinal signal.
- **Reactive State Machine (`AthleteProfileViewModel.kt`)**: Added reactive `cardioStateFlow` dispatching on `Dispatchers.IO`, combining samples, metric selector (`HR_200W`, `TAU`, `SLOPE`), and time range (`3M`, `6M`, `1Y`, `ALL`), gated cleanly by `enableCardioPowerDynamics`. Preserved `@JvmOverloads constructor` (Rule 62).
- **Dedicated Chart Card & Rule 48 Conformance (`CardioPowerTrendsCard.kt`, `AthleteProfileScreen.kt`)**: Implemented modular Jetpack Compose card with domain header, info action triggering `PowerHrDynamicsInfoDialog`, semantic direction note subtitles (Rule 48), metric pills, range pills, Canvas scatter points + rolling trend curve, and explicit X-axis dates & Y-axis scale markers (`"bpm"`, `"s"`, `"W/bpm"`). Wired into Profile tab LazyColumn below PMC chart card with 0% overlap on `PeriodsScreen.kt` (parallel sprint isolation with `ATT-3556`).
- **100% 9-Locale Parity**: All new UI tokens added to `res/values*/strings_cardio_power.xml` with zero inline emojis, verified via `TranslationParityTest`.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-011.1` | `TST-REV-012.1` | `WorkoutSummariesDatabaseManager.java`, `CardioPowerDynamicsRecord.kt` | Time-series DB query joining CPD and summaries ($R^2 \ge 0.50$) | **Verified** |
| `REQ-REV-011.2` | `TST-REV-012.2` | `CardioPowerTrendsPlotMath.kt` | 30d rolling trend, IQR outlier clamping, Rule 48 formatting | **Verified** |
| `REQ-REV-011.3` | `TST-REV-012.3` | `AthleteProfileViewModel.kt` | Reactive state machine, time-range filtering, school gating | **Verified** |
| `REQ-REV-011.4` | `TST-REV-012.4` | `CardioPowerTrendsCard.kt`, `AthleteProfileScreen.kt` | Canvas chart, pills, scale markers, Info action, screen wiring | **Verified** |
| `REQ-REV-011.5` | `TST-REV-012.5` | `strings_cardio_power.xml`, `TranslationParityTest.kt` | 100% 9-locale string parity across all supported locales | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope (Rule 60)**:
  - Scope: `Tier 1 targeted: ui.athleteprofile.*, database.*CardioPower*, localization.TranslationParityTest, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, build.*`
  - Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.database.*CardioPower*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2`
  - Result: **100% PASS** (187 tests completed, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - Checked `adb devices` (no physical test hardware connected; no emulator booted per Rule 79). Conformance verified via Compose previews, Robolectric/JUnit contract tests, and `TranslationParityTest` per Rule 60 / Rule 79.
