# Stage 2: Requirement & Test Specification - ATT-3557: Render Longitudinal HRV Readiness Trend in Periods Screen

**Ticket**: [ATT-3557](https://rainerblind.atlassian.net/browse/ATT-3557)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3557`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-033` (`docs/requirements/req_aftermath_periods.md`)  
**Test Spec ID**: `TST-PER-041` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Requirement Specification (REQ-PER-033)

### 1.1 Problem Statement & Rationale
Athletes tracking daily morning resting HRV require longitudinal autonomic recovery insights within `PeriodsScreen` to observe physiological adaptations relative to their 60-day Smallest Worthwhile Change (SWC) corridor and training load (*Plews et al. 2013*).

### 1.2 Functional & Architectural Requirements
1. **Posture Isolation & Default (`REQ-PER-033.1`)**: The trend card SHALL provide a posture selector chip row (`SUPINE`, `SEATED`, `STANDING`). Baselines SHALL calculate in strict posture isolation. Initial selection SHALL default to the posture with the greatest number of records.
2. **Scatter, Rolling Trend & SWC Corridor (`REQ-PER-033.2`)**: The chart SHALL render daily primary resting $\ln(\text{rMSSD})$ / $\text{rMSSD}$ points, continuous 7-day rolling average ($\mu_{7d}$ when $N_{7d} \ge 3$), and shaded 60-day SWC corridor ($[\mu_{60d} \pm 0.5\sigma_{60d}]$, $N_{60d} \ge 14$, Bessel $N-1$). Points and lines SHALL reflect `ReadinessStatus` colors (`OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`).
3. **Optional Training Load Overlay (`REQ-PER-033.3`)**: The card SHALL support toggling a synchronized training load overlay (daily load/TSS bars or ATL/CTL) on the shared time axis.
4. **Scrubbing & Gesture Disambiguation (`REQ-PER-033.4`)**: The chart SHALL support touch scrubbing for exact daily values and SWC boundaries, governed by `ChartGestureDisambiguator` (`isDominantHorizontal` vs `isDominantVertical`) so horizontal drag never blocks vertical parent scrolling.
5. **Graceful Empty Omission (`REQ-PER-033.5`)**: When zero morning HRV records exist in `HrvReadinessRepository`, the trend card SHALL be completely omitted from `PeriodsScreen`.
6. **Axis Scaling & Rule 48 Outlier Resilience (`REQ-PER-033.6`)**: Canvas SHALL render explicit X-axis date labels and Y-axis scale markers with outlier-resilient Y-clamping.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Trend, SWC Corridor & Load Overlay)**:
  * *Given* historical morning HRV entries in `HrvReadinessRepository`
  * *When* viewing `PeriodsScreen`
  * *Then* `HrvReadinessTrendCard` renders daily values, 7d rolling curve, and shaded 60d SWC corridor with optional load overlay.
* **Criterion 2 (Graceful Omission When Empty)**:
  * *Given* zero morning HRV entries
  * *When* opening `PeriodsScreen`
  * *Then* no empty HRV card is displayed.
* **Criterion 3 (Touch Scrubbing & Disambiguation)**:
  * *Given* dragging on the trend chart
  * *When* displacement is dominant horizontal
  * *Then* date tooltips scrub smoothly while parent vertical scroll is unconsumed.

### 1.4 System Invariants
* Zero schema changes to `PeriodSummaries.db` or `HrvReadiness.db`.
* Strict posture segregation: SWC baselines never mix readings across postures.
* 100% clean-room test pass rate.

---

## 2. Test Specification (TST-PER-041)

### Test Case 1: Posture Filtering & Series Calculation (`TST-PER-041.1`)
* **Scope**: Unit Test (`HrvReadinessTrendMathTest.kt`)
* **Assertions**: Segregates by posture, selects daily primary reading (`is_primary_for_day` then timestamp), filters out artifact rate $> 5.0\%$, and defaults to posture with max readings.

### Test Case 2: 7-Day Rolling Mean & 60-Day SWC Corridor (`TST-PER-041.2`)
* **Scope**: Unit Test (`HrvReadinessTrendMathTest.kt`)
* **Assertions**: 7d rolling mean requires $N_{7d} \ge 3$; 60d SWC corridor requires $N_{60d} \ge 14$ using Bessel's $N-1$ sample SD; classifies `OPTIMAL`, `FATIGUED_LOW`, `ABOVE_NORMAL`, `CALIBRATING`.

### Test Case 3: Training Load Alignment & Touch Scrubbing Math (`TST-PER-041.3`)
* **Scope**: Unit Test (`HrvReadinessTrendMathTest.kt`)
* **Assertions**: Time-series alignment of daily load (TSS/ATL) with HRV dates; touch X coordinate resolves nearest date point and tooltip payload.

### Test Case 4: PeriodsViewModel Integration & Graceful Empty State (`TST-PER-041.4`)
* **Scope**: ViewModel Test (`PeriodsViewModelTest.kt`)
* **Assertions**: Exposes `hrvReadinessTrendState`; emits empty state when repository has zero records; emits populated trend state when records exist.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-PER-041.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Assertions**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid `%1$s` specifiers in `strings_hrv.xml`.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-PER-041.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% clean-room pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-041.1` | Unit | `HrvReadinessTrendMath.calculateTrendSeries` | `REQ-PER-033.1`, `REQ-PER-033.2` | Specified |
| `TST-PER-041.2` | Unit | `HrvReadinessTrendMath.calculateSwcCorridor` | `REQ-PER-033.2` | Specified |
| `TST-PER-041.3` | Unit | `HrvReadinessTrendMath.alignTrainingLoad` | `REQ-PER-033.3`, `REQ-PER-033.4` | Specified |
| `TST-PER-041.4` | ViewModel | `PeriodsViewModel.hrvReadinessTrendState` | `REQ-PER-033.1`, `REQ-PER-033.5` | Specified |
| `TST-PER-041.5` | Localization | `TranslationParityTest` (`strings_hrv.xml`) | `REQ-PER-033`, `REQ-UI-106` | Specified |
| `TST-PER-041.6` | Regression | Tier 1 Unit Test Suite | `REQ-PRO-001` | Specified |
