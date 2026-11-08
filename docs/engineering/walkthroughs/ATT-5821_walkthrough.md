# Stage 5: Walkthrough & Verification - ATT-5821: EditSportTypeDialog Progressive Disclosure

**Ticket**: [ATT-5821](https://rainerblind.atlassian.net/browse/ATT-5821)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-365` (`REQ-UI-365.4`)  
**Test Mapping**: `TST-UI-328` (`TST-UI-328.6`)  
**Branch**: `feature/ATT-5821`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-07  

---

## 1. Executive Summary & Verification Overview
Refined `EditSportTypeDialog.kt` by applying progressive disclosure per Design Guidelines Section 3 (*Settings & Accordion Structure*):
- **Primary Surface Prominence**: Everyday configuration fields (Name, Base Sport Type, Linked Equipment, Strava Sport Mapping) are rendered consecutively at the top of the dialog surface.
- **Collapsible Advanced Options Accordion**: Technical speed thresholds (with explanatory auto-detection guidance text) and niche export mappings (TCX Export Name, GoldenCheetah Name) are encapsulated inside `TuningAccordionSection` with icon `Icons.Default.Tune` and title `sport_type_advanced_options` ("Erweiterte Optionen").
- **Default Collapsed State**: Initialized to collapsed (`isAdvancedExpanded = false` via `rememberSaveable`) keeping the dialog uncluttered and immediately scannable.
- **9-Language Parity**: Added `sport_type_advanced_options`, `sport_type_advanced_options_desc`, and `sport_type_speed_detection_hint` across EN, DE, ES, FR, IT, JA, NL, PL, PT with 100% parity.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-365.4a` | `TST-UI-328.6a` | Contract: `EditSportTypeDialogContractTest` (primary field ordering upfront) | **PASSED** | `Verified` |
| `REQ-UI-365.4b` | `TST-UI-328.6c` | Visual / Unit: Speed detection guidance string presence & clarity | **PASSED** | `Verified` |
| `REQ-UI-365.4c` | `TST-UI-328.6a` | Contract: `EditSportTypeDialogContractTest` (`TuningAccordionSection` wraps speed & export) | **PASSED** | `Verified` |
| `REQ-UI-365.4d` | `TST-UI-328.6a` | Contract: `EditSportTypeDialogContractTest` (default collapsed state verified) | **PASSED** | `Verified` |
| `REQ-UI-365.4e` | `TST-UI-328.6b` | Localization: `TranslationParityTest` (100% parity across all 9 locales) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Governance: `python3 tools/verify_requirement_governance.py` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-328.6c` | Suite: Tier 1 targeted unit & regression tests (79 tests) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
Executed incremental targeted unit test suite covering modified and related UI packages:
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.sporttype.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.sporttypes.*" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
    --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 9s (79 tests completed, 0 failures)
```
- Total targeted tests: 100% pass across 79 tests.
- Requirement governance: `PASS: Living documentation master tables match all 9 domain shards with 100% parity.` and `Requirement Archaeology verified`.

---

## 4. Hardware / Physical Verification (Pixel 10)
Deployed `./gradlew installDebug` to attached Pixel 10 (`66020DLCR002FL`), navigated to Sport Types -> Edit Sport Type Dialog, and captured visual verification:
- Collapsed initial state: `docs/engineering/walkthroughs/img/ATT-5821_collapsed.png` (hash `acc9e4171d76bc896a2136daef1e7cad`)
- Expanded state: `docs/engineering/walkthroughs/img/ATT-5821_expanded.png` (hash `e6d37bbe1f1d7ddb867d7a94cd34715c`)
- Visual Inspection: Inspected both screenshots via `read_file` (`Rule 71`). Confirmed proper field hierarchy, clean spacing, sharp accordion toggle, and clear detection guidance text.
- Crash Log: `adb logcat -b crash -d` returned 0 crashes.

````carousel
![Dialog Collapsed](/home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/walkthroughs/img/ATT-5821_collapsed.png)
<!-- slide -->
![Dialog Expanded](/home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/walkthroughs/img/ATT-5821_expanded.png)
````

---

## 5. Invariant & Governance Verification
1. **Preservation of Field Semantics**: All entity fields (`name`, `bSportType`, `minSpeed`, `maxSpeed`, `tcxName`, `gcName`, `stravaName`, `linkedEquipmentIds`) are faithfully persisted upon confirmation.
2. **Pure Jetpack Compose**: Follows pure Compose architecture with `rememberSaveable` state preservation across configuration changes.
3. **9-Language Parity**: 100% parity across all 9 canonical locales.
4. **Living Documentation Synchronized**: `REQ-UI-365` and `TST-UI-328` verified.
