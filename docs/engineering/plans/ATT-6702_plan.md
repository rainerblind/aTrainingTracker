# Stage 3: Implementation Plan - ATT-6702: Mount RadarSettingsSection as TuningAccordionSection in AdvancedTuningScreen and AdvancedTuningDialog

**Ticket**: [ATT-6702](https://rainerblind.atlassian.net/browse/ATT-6702)  
**Sub-task**: [ATT-6908](https://rainerblind.atlassian.net/browse/ATT-6908) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-512`  
**Test Mapping**: `TST-UI-512`  
**Branch**: `feature/ATT-6702`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
`RadarSettingsSection.kt` (907 LOC) provides full cycling radar customization and traffic simulation but has zero callers in `app/src/main/`. This plan outlines mounting `RadarSettingsSection` as a collapsible `TuningAccordionSection` under Block 2 ("Sensoren & GPS") in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, with dynamic subtitle formatting, 9-language localization parity, and elimination of double outer horizontal padding.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-512` (*Mounting RadarSettingsSection as TuningAccordionSection*)
* **Test Mapping**: `TST-UI-512` (*Radar Accordion Section and Modularity Verification*)

---

## 3. System Invariants & Preserved Behavior
1. **Initial Collapse**: Sections default to collapsed (`expandedSections = emptySet()`) per Design Guidelines §3 / REQ-UI-222.
2. **Modularity Threshold**: All source files under `ui/settings/tuning/` strictly remain $< 400$ LOC (`AdvancedTuningModularityTest`).
3. **9-Language Parity**: 100% parity across all 9 locales with zero unindexed format specifiers.
4. **Instant Persistence**: Setting changes in `RadarSettingsSection` persist immediately to `TuningPreferencesDataStore`.

---

## 4. Proposed Architectural Changes
* **Component 1 (`AdvancedTuningAccordion.kt`)**: Add `CYCLING_RADAR` to `TuningSection`; implement `TuningSubtitleFormatter.formatCyclingRadarSubtitle(...)` reflecting active scenario (simulating) or edge, alert mode, and light mode (idle); compact existing methods to bring file to $< 380$ LOC.
* **Component 2 (`RadarSettingsSection.kt`)**: Remove outer `horizontal = 16.dp` from the 4 `ElevatedCard`s (`Modifier.fillMaxWidth().padding(vertical = 4.dp)`), eliminating double inset inside accordion body.
* **Component 3 (`AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`)**: Mount `RadarSettingsSection` inside a `TuningAccordionSection` under Block 2 with icon `Icons.Default.Sensors`, keeping both files strictly $< 400$ LOC.
* **Component 4 (`strings_radar.xml` in 9 locales)**: Localize `tuning_cat_cycling_radar`.
* **UI Consistency (Rule 23)**: Reuses `TuningAccordionSection`, `Icons.Default.Sensors`, `RoundedCornerShape(12.dp)`, `4.dp`/`16.dp` spacing, and theme colors; no new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization
* Files: `app/src/main/res/values*/strings_radar.xml` (all 9 locales)
* Action: Add `tuning_cat_cycling_radar` string resource.

### Step 2: TuningSection & Subtitle Formatter
* Files: `app/src/main/java/.../ui/settings/tuning/AdvancedTuningAccordion.kt`
* Action: Add `TuningSection.CYCLING_RADAR` and `formatCyclingRadarSubtitle`; compact lines to ensure $< 400$ LOC.

### Step 3: Card Padding Hygiene
* Files: `app/src/main/java/.../ui/settings/radar/RadarSettingsSection.kt`
* Action: Update four `ElevatedCard`s to `Modifier.fillMaxWidth().padding(vertical = 4.dp)`.

### Step 4: Accordion Mounting & Modularity Guard
* Files: `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`
* Action: Mount `RadarSettingsSection` in Block 2; compact code to maintain $< 400$ LOC.

### Step 5: Unit Tests & Regression Verification
* Files: `app/src/test/.../ui/settings/tuning/RadarAccordionSectionTest.kt`, `AdvancedTuningScreenTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`RadarAccordionSectionTest`, `AdvancedTuningModularityTest`, `AdvancedTuningScreenTest`) followed by Tier 1 suite.
* **Rollback**: Branch revert via `git checkout feature/ATT-6702`.
