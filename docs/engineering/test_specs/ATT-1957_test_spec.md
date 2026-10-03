# Stage 2: Requirement & Test Specification - ATT-1957: [Settings/UI] Advanced Settings Accordion Subsections Should Be Initially Collapsed

**Ticket**: [ATT-1957](https://rainerblind.atlassian.net/browse/ATT-1957)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Requirement Mapping**: `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*)  
**Test Spec ID**: `TST-UI-187`  
**Branch**: `feature/ATT-1957`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-222 Refinement)

### 1.1 Problem Statement & Rationale
When athletes open the Advanced / Expert Settings dialog (`AdvancedTuningDialog.kt`), the first accordion subsection (`Cockpit & Typografie`) is currently expanded by default. This forces athletes to scroll past the font family dropdown, weight selector chips, and large live preview HUD card before reaching any of the other 4 categories (Battery Saver, Sensors/GPS, Aftermath Profile, or Workout Cards & Masks). Initializing all 5 accordion subsections in collapsed state provides an instantaneous, compact, and scannable dashboard of all categories with their live active-value summary subtitles on first display.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-UI-222` to enforce initial collapsed state across all accordion subsections in `AdvancedTuningDialog.kt`:

1. **Initial Expansion State Default**:
   - In `AdvancedTuningDialog.kt`, the multi-section expansion state `expandedSections` managed via `rememberSaveable` SHALL be initialized to `emptySet<String>()`.
   - Upon dialog launch, all 5 accordion subsections (`COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `AFTERMATH_ANALYSIS`, `WORKOUT_MASKS_CARDS`) SHALL be initially collapsed.

2. **Accordion Header Presentation in Collapsed State**:
   - Each collapsed subsection card SHALL render its leading category icon, category title, live active-value subtitle summary, and chevron in collapsed position (0° rotation).
   - Tapping any collapsed card header SHALL smoothly expand that subsection via `AnimatedVisibility`, revealing its dedicated control widgets.

3. **State Survival & Independent Toggling**:
   - Athletes MAY expand one, multiple, or all subsections concurrently.
   - Expansion state SHALL survive configuration changes (e.g., screen rotation) via `rememberSaveable`.

4. **Preservation of Core Invariants**:
   - Advisory warning card at top of dialog remains visible.
   - Factory reset button restores all 25+ parameters to defaults and updates live subtitles immediately.
   - Zero tonal elevation (`tonalElevation = 0.dp`) per `REQ-UI-218`.
   - DataStore keys, default values, and reactive consumers remain 100% unchanged.
   - 100% 9-language localization parity across all supported locales.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*), under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `e2303aa7`, ATT-1821 / ATT-1871).
3. *Root Reason for Existing Formulation*: In ATT-1821, the first section (`COCKPIT_TYPOGRAPHY`) was initialized as expanded by default (`setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)`) to immediately showcase the new typography HUD preview feature. However, this forced athletes wanting to adjust Battery Saver, GPS/Sensors, or Workout Cards to scroll through the entire cockpit preview every time they opened the dialog.
4. *Preservation of Core Invariants*: All 5 semantic sections, animated expand/collapse mechanics, live subtitles, warning card, factory reset, DataStore persistence, and zero tonal elevation remain 100% strictly preserved.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Initial Collapsed Presentation)**:
  - *Given* an athlete opening the Advanced Settings dialog (`AdvancedTuningDialog`),
  - *When* the dialog is initially rendered,
  - *Then* all 5 accordion subsections SHALL be in collapsed state, showing only category icons, titles, and live active-value summary subtitles without expanded control bodies.
- **AC-2 (Interactive Expansion)**:
  - *Given* `AdvancedTuningDialog` with all subsections collapsed,
  - *When* the athlete taps the card header of `SENSORS_GPS`,
  - *Then* `SENSORS_GPS` SHALL expand via `AnimatedVisibility` revealing the GPS accuracy, altitude filter window, and slope minimum speed sliders, while the other 4 subsections remain collapsed.
- **AC-3 (Multi-Section Coexistence & Persistence)**:
  - *Given* `AdvancedTuningDialog` with multiple subsections expanded,
  - *When* the device is rotated (configuration change),
  - *Then* the exact set of expanded subsections SHALL be preserved via `rememberSaveable`.

---

## 2. Test Specification (TST-UI-187)

### 2.1 Unit & Contract Tests (`AdvancedTuningVisualContractTest.kt`)
1. **Initial Expansion State Verification**:
   - Assert `AdvancedTuningDialog.kt` initializes `expandedSections` with `emptySet()`.
   - Assert `AdvancedTuningDialog.kt` does NOT initialize `expandedSections` with `setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)`.
2. **Structural Accordion Invariants**:
   - Verify all 5 `TuningSection` enum values are referenced and managed.
   - Verify `rememberSaveable` retains `expandedSections`.

### 2.2 Regression Verification
- Run targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ```
- Run clean-room full test suite in Stage 5:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Traceability Matrix

| Requirement | Test Spec | Verification Method | Target Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-222` (Initial Collapsed State) | `TST-UI-187.1` | Unit & Structural Contract Test (`AdvancedTuningVisualContractTest`) | `Approved` |
| `REQ-UI-222` (Accordion Architecture) | `TST-UI-187.2` | Structural Contract Test (`AdvancedTuningVisualContractTest`) | `Approved` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-187.3` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | `Approved` |
