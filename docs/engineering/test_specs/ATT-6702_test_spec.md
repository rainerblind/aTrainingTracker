# Stage 2: Requirement & Test Specification - ATT-6702: Mount RadarSettingsSection as TuningAccordionSection in AdvancedTuningScreen and AdvancedTuningDialog

**Ticket**: [ATT-6702](https://rainerblind.atlassian.net/browse/ATT-6702)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-512` (*Mounting RadarSettingsSection as TuningAccordionSection*)  
**Test Spec ID**: `TST-UI-512` | **Branch**: `feature/ATT-6702`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-UI-512)

### 1.1 Problem Statement & Rationale
`RadarSettingsSection.kt` (907 LOC) provides comprehensive settings and an interactive simulator for cycling radar, but currently has zero production callers. The system SHALL mount `RadarSettingsSection` as a collapsible `TuningAccordionSection` under Block 2 ("Sensoren & GPS") in both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, backed by a dynamic summary subtitle in `AdvancedTuningAccordion.kt` and 100% 9-language localization parity.

### 1.2 Functional & Architectural Requirements
1. **TuningSection Enum Extension (`REQ-UI-512.1`)**: `TuningSection` in `AdvancedTuningAccordion.kt` SHALL include `CYCLING_RADAR`, positioned in athletic sequence within Block 2 ("Sensoren & GPS").
2. **Dynamic Subtitle Formatter (`REQ-UI-512.2`)**: `TuningSubtitleFormatter` SHALL provide `formatCyclingRadarSubtitle(...)` returning localized scenario and running status when simulating, or active screen edge, audio alert mode, and tail light default mode when idle.
3. **Accordion Mounting (`REQ-UI-512.3`)**: `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` SHALL mount `RadarSettingsSection` inside a collapsible `TuningAccordionSection` with icon `Icons.Default.Sensors`, title `R.string.tuning_cat_cycling_radar`, and the dynamic subtitle, defaulting to collapsed (`expandedSections = emptySet()`).
4. **Visual Alignment & Padding Hygiene (`REQ-UI-512.4`)**: Inner `ElevatedCard` containers in `RadarSettingsSection.kt` SHALL use `Modifier.fillMaxWidth().padding(vertical = 4.dp)` without horizontal padding, eliminating double margins inside `TuningAccordionSection`.
5. **Modularity Constraint (`REQ-UI-512.5`)**: All source files in `com.atrainingtracker.trainingtracker.ui.settings.tuning.*` SHALL strictly maintain $< 400$ lines of code (`AdvancedTuningModularityTest`).
6. **9-Language Parity (`REQ-UI-512.6`)**: `tuning_cat_cycling_radar` SHALL be localized across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* default advanced tuning configuration, *When* viewing Expert Settings, *Then* `CYCLING_RADAR` is listed under Block 2 in collapsed state displaying an idle summary subtitle.
* *Given* `RadarTrafficSimulator` active, *When* collapsed, *Then* the header subtitle displays active scenario and running indicator.
* *Given* an expanded `CYCLING_RADAR` section, *When* rendered, *Then* cards align flush with sibling accordion bodies without double horizontal inset.

### 1.4 System Invariants
* Zero schema migrations; settings persist immediately to `TuningPreferencesDataStore`.
* Strict $< 400$ LOC limit for all files under `ui/settings/tuning/`.

---

## 2. Test Specification (TST-UI-512)

### Test Case 1: Radar Accordion Section & Subtitle Formatting (`TST-UI-512.1`)
* **Scope**: JVM Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/RadarAccordionSectionTest.kt`)
* **Assertions**: Verifies `TuningSection.CYCLING_RADAR` enum presence/order, subtitle formatting for idle and running simulator states, and non-null title resource.

### Test Case 2: Tuning Modularity Threshold Enforcement (`TST-UI-512.2`)
* **Scope**: Modularity Test (`AdvancedTuningModularityTest.kt`)
* **Assertions**: Verifies all Kotlin files in `ui/settings/tuning/` remain strictly $< 400$ LOC.

### Test Case 3: 9-Language Localization Parity Audit (`TST-UI-512.3`)
* **Scope**: Localization Parity Test
* **Assertions**: 100% presence of `tuning_cat_cycling_radar` across all 9 locales with zero unindexed specifiers.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-512.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-512.1` | Unit | `RadarAccordionSectionTest` | `REQ-UI-512.1`, `REQ-UI-512.2` | Specified |
| `TST-UI-512.2` | Modularity | `AdvancedTuningModularityTest` | `REQ-UI-512.5` | Specified |
| `TST-UI-512.3` | Localization | 9-Language Strings Audit | `REQ-UI-512.6` | Specified |
| `TST-UI-512.4` | Regression | Tier 1 Suite | `REQ-UI-512.3`, `REQ-UI-512.4` | Specified |
