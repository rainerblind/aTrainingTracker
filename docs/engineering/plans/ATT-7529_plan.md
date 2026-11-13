# Stage 3: Implementation Plan - ATT-7529: Guided DFA a1 Threshold Test Setup & Active Pacing Redesign

**Ticket**: [ATT-7529](https://rainerblind.atlassian.net/browse/ATT-7529) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5` | **Branch**: `feature/ATT-7529`  
**Requirement Mapping**: `REQ-TRK-072` (`REQ-TRK-072.1a/b`, `REQ-TRK-072.3a/b`, `REQ-TRK-072.4a`)  
**Test Mapping**: `TST-TRK-072` (`TST-TRK-072.1`..`TST-TRK-072.8`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
Guided DFA $\alpha_1$ Threshold Test setup (`HrvRampTestScreen.kt` / `HrvRampTestViewModel.kt`) suffers from usability defects: an unnecessary two-step pre-flight screen hop, missing live Speed (`km/h`) and side-by-side **Ziel** vs. **Aktuell (Ist)** pacing telemetry, Material 3 `SegmentedButton` checkmark clutter with text wrapping, missing dynamic protocol guidance (steps & pros/cons), and awkward aviation translations (*"Vorflug-Sensorkontrolle"*).

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-072.1a/b**: `SingleChoiceSegmentedButtonRow` with `icon = {}` and single-line ellipsis; dynamic protocol info card (title, badge, 3 steps, pros/cons) $\rightarrow$ `TST-TRK-072.3`, `TST-TRK-072.4`.
* **REQ-TRK-072.3a/b**: Eager telemetry binding in `ViewModel.init`, continuous readiness check in `CONFIG`, inline sensor status card directly above `Test starten`, natural copy $\times 9$ locales $\rightarrow$ `TST-TRK-072.1`, `TST-TRK-072.2`, `TST-TRK-072.7`.
* **REQ-TRK-072.4a**: `SensorType.SPEED_mps` collection, prominent **Ziel** vs. **Aktuell (Ist)** header, dual live Power/Speed row, uniform `height(44.dp)` action buttons $\rightarrow$ `TST-TRK-072.3`, `TST-TRK-072.5`.

---

## 3. System Invariants & Preserved Behavior
1. **Quality Gating**: Sensor artifact rate $> 5.0\%$ or missing RR stream strictly gates `canStartTest = false`.
2. **Estimator & Protocol Invariants**: `HrvThresholdEstimator` math, $R^2$, step evaluation $[t_{\text{end}}-120s, t_{\text{end}}]$, $\tau_{\text{MRT}}=45s$, and session metadata tags (`GUIDED_STEP`/`GUIDED_RAMP`) remain untouched.
3. **9-Language Parity**: 100% key parity across all 9 locales with zero missing resources.
4. **Design Guidelines Compliance**: Strict adherence to `docs/design_guidelines.md` §1 (no `FilterChip` for exclusive options, single-line buttons on 360–412dp viewports).

---

## 4. Proposed Architectural Changes

### Component 1: ViewModel & State (`HrvRampTestConfig.kt`, `HrvRampTestViewModel.kt`)
* Add `currentSpeedKmh: Double? = null` to `HrvRampTestUiState`.
* Move sensor binding (`bindToBANALService`, `startSearchingForPairedDevices`, `addRrBeatListener`) to `HrvRampTestViewModel.init`.
* Ingest `SensorType.SPEED_mps` to compute `currentSpeedKmh = speedMps * 3.6`; evaluate sensor quality continuously during `CONFIG` phase; launch `WARMUP` directly via `startProtocol()` from `CONFIG`.

### Component 2: Presentation Layer (`HrvRampTestScreen.kt`)
* `ProtocolConfigurationCard`: Set `icon = {}` and `maxLines = 1` on segmented buttons; embed dynamic Protocol Info box (badge, 3 steps, pros/cons); embed compact inline Sensor- & Signalstatus card directly above `Test starten` (`enabled = canStartTest`).
* `ActivePacingGuidanceCard`: Render prominent **Ziel** vs. **Aktuell (Ist)** comparison block; display both live Power (`W`) and live Speed (`km/h`) in telemetry row; enforce uniform `Modifier.height(44.dp)` on action buttons.

### Component 3: Localization (`strings_hrv.xml` $\times 9$ locales)
* Replace *"Vorflug-Sensorkontrolle"* with natural copy (DE: *"Sensor- & Signalstatus"*, EN: *"Sensor & Signal Status"*).
* Add strings for protocol info card, pros/cons, and pacing headers across all 9 locales.

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* **Reused Components**: M3 `SingleChoiceSegmentedButtonRow`, `SegmentedButton(..., icon = {})`, `Button`, `OutlinedButton`, `Card`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `TTColor.HrvQualityHigh`, `TTColor.HrvQualityModerate`, `MaterialTheme.colorScheme.error`.
* **Action Buttons**: Uniform single-line `height(44.dp)` with `maxLines = 1, overflow = TextOverflow.Ellipsis`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: State & Telemetry Ingestion (`HrvRampTestConfig.kt`, `HrvRampTestViewModel.kt`)
* Expose `currentSpeedKmh` in `HrvRampTestUiState`; bind sensor listener and evaluate quality in `ViewModel.init` for `CONFIG` phase; collect `SensorType.SPEED_mps`; gate `startProtocol()` directly on `canStartTest`.

### Step 2: 9-Locale String Parity (`strings_hrv.xml`)
* Replace literal pre-flight wording and add dynamic protocol info/pros-cons copy across all 9 localized files.

### Step 3: Setup & Pacing Screen Redesign (`HrvRampTestScreen.kt`)
* Refactor `ProtocolConfigurationCard` with `icon = {}`, dynamic protocol info box, inline sensor card, and direct `Test starten`; refactor `ActivePacingGuidanceCard` with **Ziel** vs. **Aktuell**, dual Power/Speed, and uniform 44dp buttons.

### Step 4: Verification Unit & Parity Tests
* Update `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt`, and run `TranslationParityTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Clean execution of `TST-TRK-072.1`..`TST-TRK-072.8` with 100% test pass rate.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7529`.
