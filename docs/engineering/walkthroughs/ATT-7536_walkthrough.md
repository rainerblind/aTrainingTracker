# Stage 5: Walkthrough & Verification - ATT-7536: Standardize Expert Settings Form Controls & Design Guidelines

**Ticket**: [ATT-7536](https://rainerblind.atlassian.net/browse/ATT-7536)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7536`  
**Requirement Mapping**: `REQ-UI-262` (`REQ-UI-262.2`, `REQ-UI-262.5`)  
**Test Mapping**: `TST-UI-221` (`TST-UI-221.5`, `TST-UI-221.6`)  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview

ATT-7536 standardizes form controls in Expert Settings, eliminates nested `ElevatedCard` containers from `RadarSettingsSection.kt`, and codifies design guidelines:
1. **Standardized Tuning Form Controls (`TuningFormControls.kt`)**: Added `TuningSegmentedButtonRow` (single-line label, compact padding, `@Composable labelProvider`) and `TuningDropdownRow` (wrapping `ExposedDropdownMenuBox` with single-line labels and standard padding).
2. **Radar Settings Section Refactoring (`RadarSettingsSection.kt`)**:
   - Eliminated all 6 nested `ElevatedCard` containers, replacing them with `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` and section subheaders matching `PhysiologyModelsSection.kt`.
   - Replaced all 8 raw `Switch(` calls with `TuningToggleItem` (0.8f scale) or `SubordinateTuningToggleItem` (0.6f scale).
   - Replaced custom Alert Volume `Slider` with `TuningSliderItem`.
   - Replaced raw `SingleChoiceSegmentedButtonRow` blocks with `TuningSegmentedButtonRow`.
   - Fully preserved all DataStore keys, default values, reactive flow bindings, and preview synthesizers.
3. **Design Guidelines Codification (`docs/design_guidelines.md`)**:
   - Updated §1.4 / §3.3 to mandate `TuningFormControls.kt` controls in Expert Settings and forbid nested `ElevatedCard`s inside accordion bodies.
   - Added §2.8 codifying single-line button rules and dynamic weighting for German compound nouns and extended translations.
4. **Contract & Hygiene Testing (`TuningFormControlsTest.kt`)**: Added JVM reflection assertions for `TuningSegmentedButtonRow` and `TuningDropdownRow`, and verified `RadarSettingsSection.kt` contains 0 `ElevatedCard` occurrences and 0 raw `Switch(` calls.
5. **Ceremony 1 Q1 Scope Boundary**: Migration of `NavigationSection.kt`, `AftermathAnalysisSection.kt`, and `TuningDesignGuidelinesContractTest.kt` is strictly deferred to `ATT-7539`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-262.2` | `[TST-UI-221.5]` | Unit Test (`TuningFormControlsTest.testTuningSegmentedButtonRow_existsViaReflection`) | **PASSED** | `Verified` |
| `REQ-UI-262.2` | `[TST-UI-221.5]` | Unit Test (`TuningFormControlsTest.testTuningDropdownRow_existsViaReflection`) | **PASSED** | `Verified` |
| `REQ-UI-262.5` | `[TST-UI-221.5]` | Unit Test (`TuningFormControlsTest.testRadarSettingsSection_eliminatesNestedElevatedCardsAndRawSwitches`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-221.6]` | Fast AAPT2 Resource Compilation (`./gradlew processDebugResources`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsTest`
- Result: **10/10 tests passed** (100% green in 0.35s under parallel sprint lock).
- Resource Compilation: `./gradlew processDebugResources` $\to$ **BUILD SUCCESSFUL** (0 XML/AAPT2 errors across all 9 locales in 2s).
- Governance Verification: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-46.5` $\to$ **PASSED** (100% System-to-Software requirement traceability).

---

## 4. Invariant & Governance Verification

1. **Card & Switch Hygiene**: `RadarSettingsSection.kt` contains 0 `ElevatedCard`s and 0 raw `Switch(` invocations, eliminating nested border elevation visual debt.
2. **DataStore & Preview Invariants**: All preference keys (`radarClosePassThresholdM`, `radarEdgeSide`, `radarAudioRoute`, `radarTrafficOrientation`, `radarAutoLightControl`, etc.), preview synthesizers, and simulator scenarios remain 100% functional and backward-compatible.
3. **Design Guidelines Invariants**: §1.4, §2.8, and §3.3 accurately codify Expert Settings form controls and German side-by-side button length rules.
4. **Target Release Governance**: Aligned to active unreleased release `V5.0.0` (Core Rule 8).
