# Stage 1 Analysis: ATT-7529 - Redesign Guided DFA a1 Threshold Test Setup and Active Pacing Screens

**Ticket**: [ATT-7529](https://rainerblind.atlassian.net/browse/ATT-7529)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7529` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
The Guided DFA $\alpha_1$ Threshold Test (`HrvRampTestScreen.kt` / `HrvRampTestViewModel.kt`) suffers from usability and layout issues:
1. **Redundant 2-Step Pre-Flight**: Athletes configure parameters on `ProtocolConfigurationCard`, click *"Vorflug-Sensorkontrolle"*, and navigate to a separate `PreFlightSensorCard` just to see if their ECG strap is streaming clean RR intervals before clicking *"Test starten"*.
2. **Missing Speed & Power Telemetry**: `HrvRampTestViewModel` never collects `SensorType.SPEED_mps`. In running mode, zero live speed/pace is shown; in cycling mode, the target is shown without a side-by-side **Aktuell (Ist)** comparison, and speed is omitted from the telemetry row.
3. **Cluttered & Wrapping Buttons**: Material 3 `SegmentedButton` defaults to showing checkmark icons (`icon` not empty), causing long labels to wrap onto 2 lines with uneven heights. On `ActivePacingGuidanceCard`, *"Abwärmen beginnen"* wraps vertically.
4. **Missing Protocol Guidance**: Athletes receive only a static 1-line warm-up description with no explanation of protocol execution or physiological trade-offs (pros/cons).
5. **Awkward Translation**: Aviation jargon *"Vorflug-Sensorkontrolle"* / *"Pre-Flight Sensor Check"* is used instead of natural sensor status terminology.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Delayed Sensor Binding)**: `banalServiceRepository.bindToBANALService()` and `addRrBeatListener()` are only called inside `startPreFlight()`, postponing quality grading until an explicit screen transition.
* **RCA 2 (Missing Speed Telemetry Stream)**: `HrvRampTestViewModel` filters out `SensorType.SPEED_mps`, leaving `currentSpeedKmh` unexposed in `HrvRampTestUiState`.
* **RCA 3 (M3 SegmentedButton Defaults & Hardcoded Padding)**: `SegmentedButton` lacks `icon = {}` overrides; button labels lack single-line constraints (`maxLines = 1, overflow = TextOverflow.Ellipsis`).
* **Independent Peer Review & Verification**:
  * Verified `HrvRampTestViewModel.kt#L101-L144`: `allFilteredSensorData` ignores `SPEED_mps`; preflight evaluation is gated on `phase == PRE_FLIGHT`.
  * Verified `HrvRampTestScreen.kt#L211-L278`: all segmented buttons omit `icon = {}`, causing checkmarks and text wrapping.
  * Verified `ActivePacingGuidanceCard` (`#L471-L537`): speed is completely absent; only cycling power is conditionally shown.
  * Verified `strings_hrv.xml` across all 9 locales: `hrv_test_preflight_title` translates "Pre-Flight" literally.
  * Verified complexity: pure UI/ViewModel ergonomics (3 SP); zero DSP math or SQLite schema changes needed.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Bind BANAL sensor listener in `HrvRampTestViewModel.init` and embed inline **Sensor- & Signalstatus** directly in `ProtocolConfigurationCard` above `Test starten`.
  2. Collect `SensorType.SPEED_mps`, expose `currentSpeedKmh: Double?` in `HrvRampTestUiState`, and render side-by-side **Ziel** vs. **Aktuell (Ist)** plus live Power & Speed on `ActivePacingGuidanceCard`.
  3. Enforce `icon = {}` and single-line ellipsis on all segmented buttons; enforce uniform `height(44.dp)` on active-screen action buttons.
  4. Render dynamic Protocol Info Box (3-step instructions + Pros/Cons) updating on Sport, Protocol, and Duration toggles per prototype.
  5. Replace *"Vorflug-Sensorkontrolle"* with natural wording across all 9 locales and verify `TranslationParityTest`.
* **Out-of-Scope Non-Goals**:
  * Modifying `HrvThresholdEstimator` regression, $R^2$, or Mean Response Time ($\tau_{\text{MRT}}$) math.
  * Modifying SQLite `WorkoutRrIntervals` or summary database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-072` (`REQ-TRK-072.1`, `REQ-TRK-072.3`, `REQ-TRK-072.4`) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Conceived in Epic `ATT-3539`, implemented in `ATT-4969` (commits `424580a2`, `f1408eb5`) and refined in `ATT-7317` (commit `fce6777b`).
* **Root Reason for Existing Formulation**: ATT-4969 initially adopted a separate `PRE_FLIGHT` screen to mirror aerospace checks before starting tracking. Experience demonstrated this adds friction; athletes expect instant sensor status on the setup page.
* **Preservation of Core Invariants**: Retains strict signal quality gating ($rMSSD$, artifact rate $\le 3.0\%$ for `HIGH`, $> 5.0\%$ blocks start), early sub-45 s completion detection, and `GUIDED_STEP`/`GUIDED_RAMP` session tagging.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-072.1`, `REQ-TRK-072.3`, `REQ-TRK-072.4`.
* **Affected Architecture Elements**: `HrvRampTestConfig.kt`, `HrvRampTestViewModel.kt`, `HrvRampTestScreen.kt`, `strings_hrv.xml` (all 9 locales).
* **Verification Impact & Test Surface**: `HrvRampTestViewModelTest.kt`, `HrvRampTestScreenTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Isolated to UI and ViewModel presentation layer. No impact on background service or database storage.

---

## 6. Architectural Strategy & High-Level Solution
1. **ViewModel (`HrvRampTestViewModel.kt`)**: Start sensor search/binding in `init`; evaluate preflight quality continuously in `CONFIG`; ingest `SPEED_mps` to compute `currentSpeedKmh = speedMps * 3.6`.
2. **UI Redesign (`HrvRampTestScreen.kt`)**: Embed inline sensor quality card directly into `ProtocolConfigurationCard`; render dynamic Protocol Info Box; add `icon = {}` to segmented buttons; render **Ziel** vs. **Aktuell (Ist)** and dual Power/Speed telemetry on `ActivePacingGuidanceCard`.
3. **Localization (`strings_hrv.xml` $\times 9$)**: Update `hrv_test_preflight_*` to natural sensor status strings and add protocol pros/cons text with 100% parity.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Sensor quality gating strictly blocks test start if artifact rate $> 5.0\%$ or no RR stream.
  2. Protocol session tagging (`GUIDED_STEP` / `GUIDED_RAMP`) remains unchanged.
  3. 100% 9-language translation parity maintained with zero missing keys.
* **Risk Rating**: **LOW** (Clean presentation-layer ergonomics refactoring with zero architectural regression risk).
