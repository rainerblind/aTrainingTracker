# Stage 3: Implementation Plan - ATT-5198: DEM Device Details Internet Notice Across 9 Locales

**Ticket**: [ATT-5198](https://rainerblind.atlassian.net/browse/ATT-5198)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-SEN-026` (`REQ-SEN-026.3`, `REQ-SEN-026.4`)  
**Test Mapping**: `TST-SEN-026` (`TST-SEN-026.4`)  
**Branch**: `feature/ATT-5198`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `EditDeviceDialog.kt`, string resource `device_altitude_position_pros_cons` details the advantages and disadvantages of position-based elevation. While it currently details GPS accuracy and terrain stepped response on bridges/cliffs, it does not explicitly disclose the active internet connection requirement for uncached areas outside saved routes.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-026.3` (Educational Trade-Offs Details in `EditDeviceDialog`), `REQ-SEN-026.4` (100% 9-Language Parity & Two-Line `\n` Format).
* **Test Mapping**: `TST-SEN-026.4` (`SpecializedAltitudeDevicesLocalizationTest.kt` & `TranslationParityTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Formatting Invariant**: Two-line structure (`+ <Pros>\n- <Cons>`) using literal `\n` linebreaks (never XML entity `&#10;`).
2. **Translation Completeness**: 100% parity across all 9 supported locales without missing keys or untranslated fallback English.
3. **UI Layout Invariant**: No changes to composable layouts, M3 typography, or container cards in `EditDeviceDialog.kt`.
4. **Subtask Self-Sufficiency & Human Gate**: Subtasks transition via `freigabe`; parent ticket advances to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Localized String Resources (`strings_devices.xml`)
* Update `device_altitude_position_pros_cons` in all 9 directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* Line 2 (`- ...`) explicitly adds the active internet requirement for uncached areas outside saved routes while preserving existing GPS and terrain trade-off details.

### Component 2: Automated Localization Verification (`SpecializedAltitudeDevicesLocalizationTest.kt`)
* Add test `testDeviceAltitudePositionProsCons_mentionsInternetRequirementAcrossAll9Locales` verifying that each of the 9 string resources contains 2 lines, starts with `+` and `-`, and contains localized network keywords.

### UI Consistency (Rule 23)
* No UI composable or layout changes (string resources and unit test suite only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update String Resources Across All 9 Locales
* Files: `app/src/main/res/values*/strings_devices.xml` (9 files)
* Changes: Update `device_altitude_position_pros_cons` with the exact localized translations specified in Stage 2.

### Step 2: Implement Automated Localization Verification Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/SpecializedAltitudeDevicesLocalizationTest.kt`
* Changes: Add test asserting structure, two-line `\n` separation, and localized internet keyword presence across all 9 locales.

### Step 3: Run Targeted Unit & Resource Verification
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.devices.SpecializedAltitudeDevicesLocalizationTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  - `./gradlew processDebugResources`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests in `SpecializedAltitudeDevicesLocalizationTest.kt`, `TranslationParityTest`, AAPT2 `processDebugResources`, and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Clean git branch revert on `feature/ATT-5198`.
