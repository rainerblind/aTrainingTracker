# Stage 5: Walkthrough & Verification - ATT-3852: Replace flat 40-item SensorType dropdown in EditSensorFieldDialog with categorized and searchable sensor value selector

**Ticket**: [ATT-3852](https://rainerblind.atlassian.net/browse/ATT-3852)  
**Sub-task**: [ATT-3868](https://rainerblind.atlassian.net/browse/ATT-3868) (`[Test]`)  
**Parent Epic**: [ATT-2177](https://rainerblind.atlassian.net/browse/ATT-2177) (*UI & UX Polishing*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-395`  
**Test Mapping**: `TST-UI-358`  
**Branch**: `feature/ATT-3852`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Verification Overview

Replaced the flat 43-item alphabetical `ExposedDropdownMenuBox` for `SensorType` in [`EditSensorFieldDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt) with a categorized and searchable selector (`SensorTypeSelectorSection`) backed by a canonical 7-group [`SensorCategory.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorCategory.java) partition across all 43 [`SensorType.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java) telemetry constants:

1. **Exhaustive 7-Category `SensorCategory` Domain Partition (`REQ-UI-395.1`)**:
   - Defined `SensorCategory` in `com.atrainingtracker.banalservice.sensor` with 7 groups and localized title/icon resource IDs:
     - `TIME_AND_LAPS` (`5` metrics)
     - `SPEED_PACE_AND_DISTANCE` (`4` metrics)
     - `CADENCE_AND_RUNNING` (`2` metrics)
     - `HEART_RATE_AND_ENERGY` (`2` metrics)
     - `POWER_AND_PEDALING` (`13` metrics)
     - `ELEVATION_AND_NAVIGATION` (`9` metrics)
     - `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` (`8` metrics)
   - Exposed `getCategory()` on all 43 `SensorType` constants (`5 + 4 + 2 + 2 + 13 + 9 + 8 = 43`).
2. **Categorized Accordion & Instant Search Selector (`REQ-UI-395.2`)**:
   - Implemented `SensorTypeSelectorSection` in `EditSensorFieldDialog.kt` with a current-selection summary card (category subtitle, localized full name, localized short name, and unit badge), an inline search `OutlinedTextField` (`R.string.sensor_type_search_hint`), and 7 collapsible `SensorCategory` accordions (`ElevatedCard` with `AnimatedVisibility`).
   - Implemented `SensorTypeSearchFilter.filterByQuery` for case-insensitive matching across localized full name, localized short name, localized unit, and enum identifier.
3. **Bottom Sheet Scroll Safety & 9-Language Localization Parity (`REQ-UI-395.3`)**:
   - Constructed `SensorTypeSelectorSection` as a standard `Column` without nested `verticalScroll` or `LazyColumn` inside `AppModalBottomSheet(scrollable = true)` (`docs/design_guidelines.md` Section 5.8).
   - Added all 9 new string keys (`sensor_category_*`, `sensor_type_search_hint`, `sensor_type_no_matches`) across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-395` | `TST-UI-358.1` | Exhaustive 43-Metric `SensorCategory` Partition & `SensorTypeSearchFilter` Unit Tests (`SensorCategoryAndSelectorContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-395` | `TST-UI-358.2` | Source Contract, Bottom Sheet Scroll Safety & 9-Locale Parity Audit (`SensorCategoryAndSelectorContractTest`, `ModalBottomSheetDialogsIntegrityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-358.3` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.SensorCategoryAndSelectorContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"
BUILD SUCCESSFUL in 5s
```

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23)
- `SensorTypeSelectorSection` follows the collapsible `ElevatedCard` (`RoundedCornerShape(12.dp)`) and `AnimatedVisibility` accordion pattern established in `AdvancedTuningAccordion.kt` (`docs/design_guidelines.md` Section 3) and obeys `AppModalBottomSheet` scroll safety (`docs/design_guidelines.md` Section 5.8).
- Full on-device verification on Google Pixel 10 will be conducted at the start of Ceremony 2 (Sprint Review) after `./gradlew installDebug` per Rule 10.
- * **Checked against `docs/design_guidelines.md` §5**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
- * **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-395`) and `docs/tests.md` (`TST-UI-358`) updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-3868`) transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket (`ATT-3852`) merged into `sprint/2026-42.7` (`--no-ff`) and transitioned to `Final Review (Human)`.
