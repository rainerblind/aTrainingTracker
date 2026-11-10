# Stage 2: Requirement & Test Specification - ATT-3906: Cardio-Power Longitudinal Trends

**Ticket**: [ATT-3906](https://rainerblind.atlassian.net/browse/ATT-3906)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3906`  
**Requirement Mapping**: `REQ-REV-011` | **Test Spec ID**: `TST-REV-012`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-REV-011: Cardio-Power Longitudinal Trends)

### 1.1 Problem Statement & Rationale
Single-workout details capture acute physiology, but endurance athletes track fitness longitudinally. Outdoor fresh model parameters ($\text{HR@200W}$, $\tau$, $1/K$) track aerobic economy and cardiorespiratory kinetics across seasons without exhaustion testing. `AthleteProfileScreen.kt` requires a dedicated "Cardio-Power Fitness Trends" chart card with outlier-resistant Y-scaling and explicit Rule 48 axis markers.

### 1.2 Functional & Architectural Requirements
1. **Database Time-Series Ingestion (`WorkoutSummariesDatabaseManager.java`)**:
   - Provide `getCardioPowerDynamicsFitnessTrendSamples(minR2: Double = 0.50)` querying `cardio_power_dynamics` joined with `WorkoutSummaries` sorted by workout start time ascending.
2. **Dedicated Trends Card (`CardioPowerTrendsCard.kt` in Athlete Profile)**:
   - When `enableCardioPowerDynamics == true`, render `CardioPowerTrendsCard` in `AthleteProfileScreen.kt`.
   - Provide metric selector pills for $\text{HR@200W}$, Time Constant $\tau$, and Slope $1/K$.
   - Provide timeframe filter pills (3M, 6M, 1Y, All).
   - Card header SHALL provide an `(i)` info action opening `PowerHrDynamicsInfoDialog`.
3. **Canvas Trend Rendering & Rule 48 Compliance**:
   - Canvas SHALL plot individual workout scatter points and a 30-day rolling trend curve.
   - Canvas SHALL render explicit X-axis date labels (`"dd.MM."` / `"MMM yy"`) and Y-axis scale markers (`"bpm"`, `"s"`, `"W/bpm"`).
   - Y-axis scaling SHALL apply interquartile range (IQR) domain clamping for $N \ge 4$ to resist noisy outlier compression.
   - Card SHALL display semantic direction subtitles ($\text{HR@200W}$: *"Lower = Better Aerobic Economy"*; $\tau$: *"Lower = Faster HR Kinetics"*; $1/K$: *"Higher = More Watts per bpm"*).
4. **Parallel Sprint Isolation & Localization**:
   - UI changes SHALL be restricted strictly to `AthleteProfileScreen.kt` / `AthleteProfileViewModel.kt` with zero modification to `PeriodsScreen.kt` (preserving `ATT-3556`).
   - All newly introduced UI strings SHALL reside in `res/values*/strings_cardio_power.xml` with 100% 9-locale parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Trends Card & Markers)**: *Given* qualifying rides ($R^2 \ge 0.50$) and `enableCardioPowerDynamics == true`, *When* viewing Athlete Profile, *Then* `CardioPowerTrendsCard` renders scatter points, 30-day rolling curve, Rule 48 explicit axis markers, and metric direction subtitle.
* **AC 2 (Reactive Filtering & Switching)**: *Given* `CardioPowerTrendsCard`, *When* switching metric pills ($\text{HR@200W}$, $\tau$, $1/K$) or timeframe pills (3M, 6M, 1Y, All), *Then* plot domain and points update reactively without UI leaks.
* **AC 3 (Gating & Empty State)**: *Given* zero qualifying rides or `enableCardioPowerDynamics == false`, *When* viewing Athlete Profile, *Then* clean empty-state message or omission occurs with zero visual distortion.

---

## 2. Test Specification (TST-REV-012: Cardio-Power Trends Verification)

### Test Case 1: Database Trend Query Test (`TST-REV-012.1`)
* **Scope**: `WorkoutSummariesCardioPowerTrendTest.kt`. Verify `getCardioPowerDynamicsFitnessTrendSamples` filters $R^2 \ge 0.50$ and sorts ascending by start timestamp.

### Test Case 2: ViewModel Reactive State & Gating Test (`TST-REV-012.2`)
* **Scope**: `AthleteProfileViewModelCardioPowerTest.kt`. Verify timeframe filtering, metric selection, and `enableCardioPowerDynamics` gating.

### Test Case 3: Canvas Plot Math & Rule 48 Audit Test (`TST-REV-012.3`)
* **Scope**: `CardioPowerTrendsPlotMathTest.kt`. Verify IQR domain clamping, date labeling, Y-axis scale value formatting, and rolling 30-day average calculations.

### Test Case 4: UI Component Contract Test (`TST-REV-012.4`)
* **Scope**: `CardioPowerTrendsCardTest.kt`. Verify info action, metric/range selector pill clicks, empty-state message rendering, and layout stability.

### Test Case 5: 9-Locale Localization Parity Test (`TST-REV-012.5`)
* **Scope**: `TranslationParityTest.kt` verifying all new cardio-power trend tokens in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-012.1]` | DB Unit | `WorkoutSummariesCardioPowerTrendTest`: Filter $R^2 \ge 0.50$, order ASC | `REQ-REV-011.1` | Specified |
| `[TST-REV-012.2]` | ViewModel | `AthleteProfileViewModelCardioPowerTest`: Timeframe & metric state | `REQ-REV-011.2` | Specified |
| `[TST-REV-012.3]` | Unit Math | `CardioPowerTrendsPlotMathTest`: IQR clamping, Rule 48 markers, rolling avg | `REQ-REV-011.3` | Specified |
| `[TST-REV-012.4]` | UI Compose | `CardioPowerTrendsCardTest`: Header info, selector pills, empty state | `REQ-REV-011.2` | Specified |
| `[TST-REV-012.5]` | Localization | `TranslationParityTest`: 9-locale parity in `strings_cardio_power.xml` | `REQ-REV-011.4`, `REQ-LOC-001` | Specified |
