# Stage 3 Implementation Plan - ATT-7539: Standardize Category Segmented Rows & Contract Test

**Ticket**: [ATT-7539](https://rainerblind.atlassian.net/browse/ATT-7539)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-262.5`, `REQ-CLM-010.3` | **Test Spec ID**: `TST-UI-221.6`, `TST-CLM-010.6`  
**Branch**: `feature/ATT-7539` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Architecture & Component Decomposition
- **Target Components**:
  - `ui/settings/tuning/categories/NavigationSection.kt`: Replace `FilterChip` binary selector for climb scoring formula (`ClimbScoringFormula.FIETS` vs. `STRAVA_GARMIN`) with standardized `TuningSegmentedButtonRow` (`bodyMedium` `SemiBold` title, `bodySmall` `onSurfaceVariant` helper text, `icon = {}`). Purge unused `FilterChip` import.
  - `ui/settings/tuning/categories/AftermathAnalysisSection.kt`: Replace two legacy binary `FilterChip` selectors (`elevationXAxisDomain` and `telemetryXAxisDomain`, `DISTANCE` vs. `TIME`) with `TuningSegmentedButtonRow`. Purge unused `FilterChip` import.
  - `ui/settings/tuning/categories/CockpitTypographySection.kt`: Standardize 3-option `CockpitFontWeight` selector (`NORMAL`, `SEMI_BOLD`, `BOLD`) with `TuningSegmentedButtonRow`. Purge unused `FilterChip` import.
  - `ui/settings/tuning/TuningDesignGuidelinesContractTest.kt`: Create JUnit architecture contract test scanning all files in `ui/settings/tuning/categories/` asserting zero disallowed `FilterChip(` (allowlisting `SupporterDebugSection.kt` developer tier overrides), zero raw unscaled `Switch(`, and zero nested `ElevatedCard(`.
  - `ui/settings/tuning/AdvancedTuningVisualContractTest.kt`: Verify `NavigationSection` and `AftermathAnalysisSection` contract assertions.

---

## 2. Atomic Implementation Steps
1. **Step 1 - NavigationSection Form Control Standardization (`REQ-CLM-010.3`, `TST-CLM-010.6`)**:
   - In `NavigationSection.kt:136-168`, replace the `FilterChip` `Row` with `TuningSegmentedButtonRow` passing options `[ClimbScoringFormula.FIETS, ClimbScoringFormula.STRAVA_GARMIN]`, `selectedOption = climbScoringFormula`, `onOptionSelected = onClimbScoringFormulaChange`, and localized string labels.
   - Remove unused imports (`FilterChip`, `Spacer`, `height`).
2. **Step 2 - AftermathAnalysisSection Binary Chip Purge (`REQ-UI-262.5a`, `TST-UI-221.6`)**:
   - In `AftermathAnalysisSection.kt:106-158`, replace the two `FilterChip` rows for `elevationXAxisDomain` and `telemetryXAxisDomain` with `TuningSegmentedButtonRow` using `ProfileXAxisDomain.DISTANCE` and `TIME`.
   - Remove unused imports (`FilterChip`, `Spacer`, `height`).
3. **Step 3 - CockpitTypographySection Segmented Standardization (`REQ-UI-262.5b`, `TST-UI-221.6`)**:
   - In `CockpitTypographySection.kt:138-157`, replace the `FilterChip` `Row` for `CockpitFontWeight` with `TuningSegmentedButtonRow` passing `CockpitFontWeight.values().toList()`.
   - Remove unused `FilterChip` import, maintaining line count strictly `< 400` lines (~370 lines).
4. **Step 4 - TuningDesignGuidelinesContractTest Construction (`REQ-UI-262.5c`, `TST-UI-221.6`)**:
   - Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TuningDesignGuidelinesContractTest.kt`.
   - Implement JVM source scanning of `ui/settings/tuning/categories/` asserting:
     - No raw `FilterChip(` outside `SupporterDebugSection.kt`.
     - No raw unscaled `Switch(`.
     - No nested `ElevatedCard(`.
5. **Step 5 - Automated Test & Governance Verification**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`.
   - Run `python3 tools/verify_requirement_governance.py`.

---

## 3. Invariant Protection & System Properties
- **DataStore Invariants**: All preference keys, default values (`FIETS`, `DISTANCE`, `TIME`, `NORMAL`), and flow bindings remain 100% unchanged.
- **UI Consistency**: Zero checkmark text shifting (`icon = {}` enforced by `TuningSegmentedButtonRow`).
- **File Length Guard**: All category files remain strictly below the 400-line modularity threshold (`REQ-UI-262.3`).

---

## 4. UI Consistency & Design Guidelines Compliance
- Complies strictly with `docs/design_guidelines.md §1.1` (Binary Mutually Exclusive Mode Selections) and §1.4 (Expert Settings Form Controls: `TuningSegmentedButtonRow`). Completely eliminates the binary `FilterChip` anti-pattern across all tuning categories.

---

## 5. Verification Strategy & Living Documentation
- Test Suite: `TuningDesignGuidelinesContractTest.kt`, `AdvancedTuningVisualContractTest.kt`, `MetricFormatterContractTest.kt`, `TuningFormControlsTest.kt`.
- Status Lifecycle: Update `REQ-UI-262.5` and `REQ-CLM-010.3` to `[Implemented]` in Stage 4, then `[Verified]` in Stage 5.
