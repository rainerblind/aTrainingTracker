# Stage 3: Implementation Plan - ATT-4969: Implement Guided DFA a1 Ramp/Step Test Protocol Screen for High-Confidence HRVT1 and HRVT2 Determination

**Ticket**: [ATT-4969](https://rainerblind.atlassian.net/browse/ATT-4969)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Screen*)  
**Test Mapping**: `TST-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Suite*)  
**Branch**: `feature/ATT-4969` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Athletes currently lack an in-app guided protocol screen to perform structured aerobic-to-anaerobic DFA $\alpha_1$ tests. Uncontrolled outdoor rides result in noisy non-monotonic telemetry that degrades linear regression $R^2$ and causes span failures in `HrvThresholdEstimator` (`ATT-3554`). Additionally, athletes often needlessly exhaust themselves because the app lacks early completion detection when $\alpha_1 < 0.45$ is reached. ATT-4969 delivers `HrvRampTestConfig.kt`, `HrvRampTestViewModel.kt`, and `HrvRampTestScreen.kt` with pre-flight sensor validation, live step countdown pacing, zero-bleed evaluation window tagging, and automated sub-exhaustion early completion detection.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Screen*, `REQ-TRK-072.1` to `REQ-TRK-072.6`)
* **Test Mapping**: `TST-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Suite*, `TST-TRK-072.1` to `TST-TRK-072.7`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing `TrackerService`, `MorningHrvCheckScreen`, and `HrvThresholdEstimator` suites continue to pass 100%.
2. **Decimated Grid Quality Invariant**: `Confidence.HIGH` strictly requires $R^2 \ge 0.70$ on the decimated grid, mean artifact rate $\le 3.0\%$, and span coverage without bypass.
3. **Zero Next-Step Bleed**: For `GUIDED_STEP`, step telemetry evaluation is strictly bounded to $[t_{\text{end},j} - 120\text{ s}, t_{\text{end},j}]$.
4. **Sub-Exhaustion Completion Guard**: Early completion requires sustained $\alpha_1 < 0.45$ for $\ge 60\text{ s}$ to prevent premature termination on transient dips.
5. **Human Decision Gate**: Parent ticket ATT-4969 advances to `Final Review (Human)` and is never closed by agents.

---

## 4. Proposed Architectural Changes

### Component 1: Protocol Models & Configuration (`ui/hrv/HrvRampTestConfig.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.hrv`
* **Models & Enums**:
  * `enum class HrvTestSport { CYCLING, RUNNING }`
  * `enum class HrvTestProtocolType { GUIDED_STEP, GUIDED_RAMP }`
  * `enum class HrvTestPhase { CONFIG, PRE_FLIGHT, WARMUP, TESTING, EARLY_COMPLETION, COOLDOWN, FINISHED }`
  * `enum class SensorQualityTier { HIGH, MODERATE, UNSUITABLE }`
  * `data class HrvRampTestConfig(...)`
  * `data class HrvRampTestUiState(...)`

### Component 2: Protocol Orchestrator (`ui/hrv/HrvRampTestViewModel.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.hrv`
* **Responsibilities**:
  * Consumes `BANALServiceRepository` for live HR, RR intervals, DFA $\alpha_1$, and artifact rate.
  * Pre-flight quality assessment: $\le 3.0\%$ artifacts (High), $3.0\text{--}5.0\%$ (Moderate), $> 5.0\%$ or missing RR (Unsuitable / Blocked).
  * State transitions: `CONFIG` $\rightarrow$ `PRE_FLIGHT` $\rightarrow$ `WARMUP` $\rightarrow$ `TESTING` $\rightarrow$ `COOLDOWN` $\rightarrow$ `FINISHED`.
  * Early completion detector: evaluates sustained $\alpha_1 < 0.45$ for $\ge 60\text{ s}$.

### Component 3: Presentation Screen (`ui/hrv/HrvRampTestScreen.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.hrv`
* **Composable**: `fun HrvRampTestScreen(viewModel: HrvRampTestViewModel, onNavigateBack: () -> Unit)`
* **Layout**:
  * Material 3 layout with top app bar.
  * Protocol config card (Sport, Protocol, starting load, step duration, increment).
  * Pre-flight sensor card displaying signal quality tier and warnings.
  * In-workout guidance card: Target load, step countdown timer, live HR, live $\alpha_1$, live artifact %.
  * Early completion banner inviting transition to cool-down.

### Component 4: Live Host Mounting (`NavRoutes.kt` & `ATrainingTrackerApp.kt`, Rule 81)
* Add `NavRoutes.HRV_TEST = "hrv_test"`.
* Register `composable(NavRoutes.HRV_TEST)` in `ATrainingTrackerApp.kt`.
* Wire navigation entry point in `AthleteProfileScreen.kt` alongside `onNavigateToMorningHrv`.

### UI Consistency (Rule 23)
* **Reference Component**: `MorningHrvCheckScreen.kt` and `LiveTrackingCockpit`.
* **Reused Components**: `MetricItem`, `SegmentedButton`, `HorizontalDivider`, `ElevatedCard`, `TTColor`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `16.dp` card padding, `MaterialTheme.colorScheme`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `HrvRampTestConfig.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestConfig.kt`
* Details: Define enums, configuration data class, and UI state container.

### Step 2: Implement `HrvRampTestViewModel.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestViewModel.kt`
* Details: Implement state machine, pre-flight sensor check, step/ramp pacing logic, and sustained sub-0.45 auto-completion detection.

### Step 3: Implement Unit Tests in `HrvRampTestViewModelTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestViewModelTest.kt`
* Verification: Test state transitions, pre-flight quality tiers, 60s sustained early completion detection, and step window bounds.

### Step 4: Implement `HrvRampTestScreen.kt` Composable
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestScreen.kt`
* Details: Implement Compose UI with pre-flight check, live pacing cards, countdown timer, and completion banner.

### Step 5: Mount in Host Navigation (Rule 81)
* Files: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `AthleteProfileScreen.kt`
* Details: Register `NavRoutes.HRV_TEST`, wire destination in `ATrainingTrackerApp.kt`, and add trigger in `AthleteProfileScreen.kt`.

### Step 6: Implement Compose & Contract Tests in `HrvRampTestScreenTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestScreenTest.kt`
* Verification: Test screen composable contract, test tags, and navigation route wiring.

### Step 7: Target Test Execution & Localization Parity
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and Compose tests in Step 7, followed by clean-room Tier 1 suite in Stage 5.
* **Rollback Plan**: All changes isolated on `feature/ATT-4969`. Clean branch checkout reverts cleanly.
