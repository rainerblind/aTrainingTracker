# Stage 2: Requirement & Test Specification - ATT-7529: Redesign Guided DFA a1 Threshold Test Setup and Active Pacing Screens

**Ticket**: [ATT-7529](https://rainerblind.atlassian.net/browse/ATT-7529) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5` | **Branch**: `feature/ATT-7529`  
**Requirement Mapping**: `REQ-TRK-072` (`REQ-TRK-072.1`, `REQ-TRK-072.3`, `REQ-TRK-072.4`)  
**Test Spec ID**: `TST-TRK-072` (`TST-TRK-072.1`..`TST-TRK-072.8`) | **Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-TRK-072)

### 1.1 Problem Statement & Rationale
The Guided DFA $\alpha_1$ Threshold Test setup currently forces an awkward two-step pre-flight screen flow, lacks live Speed (`km/h`) and side-by-side **Ziel** vs. **Aktuell (Ist)** pacing telemetry, clutters Material 3 `SegmentedButton` controls with default checkmarks and wrapping text, lacks dynamic protocol pros/cons guidance, and uses literal aviation translations (*"Vorflug-Sensorkontrolle"*).

### 1.2 Attributes & Functional Requirements
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
1. `SingleChoiceSegmentedButtonRow` controls (Sport, Protocol, Duration) in `HrvRampTestScreen.kt` SHALL render without checkmark icons (`icon = {}`) and enforce single-line text formatting (`maxLines = 1, overflow = TextOverflow.Ellipsis`) (`REQ-TRK-072.1a`).
2. `ProtocolConfigurationCard` SHALL render a dynamic Protocol Info card with title, badge, 3-step instructions (*Ablauf & Durchführung*), and protocol-specific *+ Vorteil* / *– Nachteil* bullet points updated on selector changes (`REQ-TRK-072.1b`).
3. `HrvRampTestViewModel` SHALL bind telemetry upon initialization and continuously assess sensor readiness in `CONFIG` phase, embedding the **Sensor- & Signalstatus** card directly on `ProtocolConfigurationCard` above `Test starten` (`enabled = canStartTest`) to launch `WARMUP` directly (`REQ-TRK-072.3a`).
4. `HrvRampTestViewModel` SHALL collect `SensorType.SPEED_mps` to compute `currentSpeedKmh`, and `ActivePacingGuidanceCard` SHALL display a prominent **Ziel** vs. **Aktuell (Ist)** comparison, dual Power (`W`) and Speed (`km/h`) telemetry, and uniform `height(44.dp)` action buttons (`REQ-TRK-072.4a`).
5. Terminology across all 9 localized `strings_hrv.xml` files SHALL use natural sensor status phrasing replacing *"Vorflug-Sensorkontrolle"* with 100% parity (`REQ-TRK-072.3b`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (Inline Sensor Quality & Direct Test Launch)**: *Given* `HrvRampTestScreen` opened, *When* in `CONFIG` phase, *Then* sensor quality status is displayed inline above `Test starten` (`enabled = canStartTest`), launching `WARMUP` directly when tapped without an intermediate pre-flight screen.
* **AC2 (Live Power, Speed & Pacing Comparison)**: *Given* active test guidance, *When* sensor telemetry updates arrive, *Then* **Ziel** vs. **Aktuell (Ist)** is prominently displayed along with live Power (`W`) and Speed (`km/h`).
* **AC3 (Clean Single-Line Controls & Action Buttons)**: *Given* setup and active pacing screens, *When* controls render, *Then* segmented buttons omit checkmarks (`icon = {}`) with single-line labels, and action buttons share uniform `height(44.dp)`.
* **AC4 (Dynamic Protocol Guidance & Pros/Cons)**: *Given* protocol configuration, *When* toggling Sport, Protocol, or Duration, *Then* instructions and pros/cons dynamically update.
* **AC5 (9-Language Parity & Natural Wording)**: *Given* the 9 supported locales, *When* running `TranslationParityTest`, *Then* all new keys exist with zero missing entries and natural sensor status copy.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-072`, extending `REQ-TRK-061` and companion to `REQ-PER-036`. Targets `HrvRampTestScreen.kt`, `HrvRampTestViewModel.kt`, `HrvRampTestConfig.kt`.
* **Historical Origin & Commit Trace**: Conceived in Epic `ATT-3539`, implemented in `ATT-4969` (commit `424580a2`), refined in `ATT-7317` (commit `fce6777b`), and ergonomically streamlined in `ATT-7529`.
* **Root Reason for Existing Formulation**: Separate pre-flight screen was added in ATT-4969 to mirror aerospace checklists, but created user friction; athletes expect instant sensor status on the setup page.
* **Preservation of Core Invariants**: Retains strict signal quality gating ($rMSSD$, artifact rate $\le 3.0\%$ for High, $> 5.0\%$ blocks start), early sub-45 s completion detection, and `GUIDED_STEP`/`GUIDED_RAMP` session tagging.

---

## 2. Test Specification (TST-TRK-072)
* `TST-TRK-072.1` (Unit, `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt`): Continuous sensor assessment in `CONFIG`; `canStartTest` enables `Test starten` to transition directly to `WARMUP`.
* `TST-TRK-072.2` (Unit, `HrvRampTestScreenTest.kt`): Inline sensor card renders on setup; disabled `Test starten` with clear diagnostics when sensors disconnected or artifact rate $> 5.0\%$.
* `TST-TRK-072.3` (Unit, `HrvRampTestScreenTest.kt`): Segmented buttons render with `icon = {}` and single-line ellipsis; action buttons share uniform `height(44.dp)`.
* `TST-TRK-072.4` (Unit, `HrvRampTestScreenTest.kt`): Dynamically updates badge, 3-step instructions, and + Vorteil / – Nachteil on Sport, Protocol, and Duration toggles.
* `TST-TRK-072.5` (Unit, `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt`): ViewModel ingests `SPEED_mps` to compute `currentSpeedKmh`; active screen displays **Ziel** vs. **Aktuell (Ist)**, live Power (W), and Speed (km/h).
* `TST-TRK-072.6` (Unit, `HrvRampTestViewModelTest.kt`): Sustained $\alpha_1 < 0.45$ for $\ge 60\text{ s}$ triggers early completion banner; session tagged with protocol type and parameters.
* `TST-TRK-072.7` (Unit, `TranslationParityTest.kt`): 100% string parity across all 9 locales with natural sensor status phrasing replacing *"Vorflug-Sensorkontrolle"*.
* `TST-TRK-072.8` (Clean-Room Test Suite): `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-072.1` | Unit | `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt` | `REQ-TRK-072.1`, `REQ-TRK-072.3a` | Specified |
| `TST-TRK-072.2` | Unit | `HrvRampTestScreenTest.kt` | `REQ-TRK-072.3a` | Specified |
| `TST-TRK-072.3` | Unit | `HrvRampTestScreenTest.kt` | `REQ-TRK-072.1a`, `REQ-TRK-072.4a` | Specified |
| `TST-TRK-072.4` | Unit | `HrvRampTestScreenTest.kt` | `REQ-TRK-072.1b` | Specified |
| `TST-TRK-072.5` | Unit | `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt` | `REQ-TRK-072.4a` | Specified |
| `TST-TRK-072.6` | Unit | `HrvRampTestViewModelTest.kt` | `REQ-TRK-072.5`, `REQ-TRK-072.6` | Specified |
| `TST-TRK-072.7` | Unit | `TranslationParityTest.kt` | `REQ-TRK-072.3b` | Specified |
| `TST-TRK-072.8` | Clean-Room | Targeted test suites | `REQ-PRO-001` | Specified |
