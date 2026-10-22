# Stage 2: Requirement & Test Specification - ATT-4374: Decouple SensorSourceDialog 'Source' and 'Active Backups' header colors from route blue tokens and restore darker/lighter green

**Ticket**: [ATT-4374](https://rainerblind.atlassian.net/browse/ATT-4374)  
**Sub-task**: [ATT-4383](https://rainerblind.atlassian.net/browse/ATT-4383) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-051` (*Color-Coded Sensor Source Headers & Semantic Color Token Decoupling*)  
**Test Spec ID**: `TST-UI-052`  
**Branch**: `feature/ATT-4374`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-051)

### 1.1 Problem Statement & Rationale
In the live tracking cockpit, opening the sensor redundancy audit bottom sheet ([`SensorSourceDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt)) reveals that the section headers for **Source Device** ("Quelle" / `R.string.source_device`) and **Active Backups** ("Aktive Backups" / `R.string.source_active_backups`) currently render in Royal Blue and Soft Steel Blue:
* **Source Header** ([`SensorSourceDialog.kt:128`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L128)): uses `TTColor.RouteSelected` (`Color(0xFF1565C0)` Material Blue 800).
* **Active Backups Header** ([`SensorSourceDialog.kt:149`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L149)): uses `TTColor.RouteUnselected` (`Color(0xFF90CAF9)` Material Blue 200).
* **Not Connected Header** ([`SensorSourceDialog.kt:178`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L178)): uses `MaterialTheme.colorScheme.onSurfaceVariant` (neutral grey).

This visual presentation has two severe defects:
1. **Semantic Inconsistency**: Connected, transmitting sensors display green status LED dots (`TTColor.ConnectionStatusGreen` = `0xFF4CAF50`), but their section headers are rendered in map route blue.
2. **Direct Requirement Violation**: `REQ-UI-051` explicitly specifies that `SensorSourceDialog` SHALL use color-coded section headers: **Dark Green** for "Source Device", **Light Green** for "Active Backups", and **Grey** for "Not Connected Devices".
3. **Cross-Domain Coupling Anti-Pattern**: Sensor hardware availability opportunistically borrowed color tokens from the map route navigation domain (`TTColor.RouteSelected` / `TTColor.RouteUnselected`). When route visualization colors were changed to Royal Blue for map contrast in ATT-2761, sensor headers inadvertently turned blue as an unintended side effect.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-051.1: Dedicated Semantic Color Tokens in `TTColor` (`Color.kt`)**:
  * The system SHALL define `TTColor.SensorSourceActive`: Darker green (`Color(0xFF2E7D32)`, Material Green 800) for the primary active "Source Device" section header.
  * The system SHALL define `TTColor.SensorBackupActive`: Lighter green (`Color(0xFF43A047)`, Material Green 600) for the standby "Active Backups" section header.
  * Both tokens SHALL be declared in [`Color.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt) under `TTColor` with explicit documentation referencing `REQ-UI-051`.
* **REQ-UI-051.2: Decoupling of `SensorSourceDialog.kt` from Route Tokens**:
  * In [`SensorSourceDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt), line 128 SHALL replace `color = TTColor.RouteSelected` with `color = TTColor.SensorSourceActive`.
  * In `SensorSourceDialog.kt`, line 149 SHALL replace `color = TTColor.RouteUnselected` with `color = TTColor.SensorBackupActive`.
  * In `SensorSourceDialog.kt`, line 178 SHALL retain `color = MaterialTheme.colorScheme.onSurfaceVariant` for "Not Connected" devices.
  * `SensorSourceDialog.kt` SHALL contain ZERO references to `TTColor.RouteSelected` or `TTColor.RouteUnselected`.
* **REQ-UI-051.3: WCAG 2.1 AA Contrast Compliance**:
  * `TTColor.SensorSourceActive` (`Color(0xFF2E7D32)`) SHALL achieve $\ge 3.0:1$ WCAG AA bold contrast on Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`) surfaces:
    * Contrast on White (`#FFFFFF`): **5.13:1** (exceeds normal text 4.5:1 and bold 3.0:1).
    * Contrast on Dark (`#16161A`): **3.52:1** (exceeds bold 3.0:1 threshold).
    * Contrast on AMOLED (`#000000`): **4.10:1** (exceeds bold 3.0:1 threshold).
  * `TTColor.SensorBackupActive` (`Color(0xFF43A047)`) SHALL achieve $\ge 3.0:1$ WCAG AA bold contrast on Light surfaces ($3.30:1$) and $\ge 4.5:1$ on Dark surfaces ($5.46:1$) and AMOLED ($6.36:1$).
* **REQ-UI-051.4: Route Domain Immutability**:
  * `TTColor.RouteSelected` (`Color(0xFF1565C0)`), `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`), `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`), and `TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`) MUST remain 100% unmodified.
  * All map route polyline renderers, route card components, and return navigation HUDs MUST remain 100% untouched.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-051` (*Color-Coded Sensor Source Headers*), targeting `SensorSourceDialog.kt`, `Color.kt`, and `TST-UI-052`.
* `Historical Origin & Commit Trace`: Introduced in Sprint `2026-40.1` (SCRUM-147, commit `aa0184f0`), centralized into `TTColor` in commit `9691da82` (SCRUM-150), and inadvertently turned blue in Sprint `2026-41.4` (ATT-2761, commit `a9364e84`) when map route tokens were transitioned to Royal Blue (`REQ-UI-306`).
* `Root Reason for Existing Formulation`: In SCRUM-147, `RouteColorSelected` (`0xFF228B22`) and `RouteColorUnselected` (`0xFF90EE90`) were reused opportunistically because they were already defined in `Color.kt` as dark and light green constants. There was no architectural reason to couple sensor hardware availability with map route navigation lines; it was an opportunistic code reuse that created technical debt.
* `Preservation of Core Invariants`: Decoupling these headers from route tokens restores the original green intent of `REQ-UI-051` without impacting route visualization. Map route tokens, polyline renderers, truthful connection status indicators (`REQ-UI-268`), and canonical priority sorting remain 100% intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Primary Source Header Rendering in Dark Green**:
  * *Given* an athlete in the tracking cockpit opening `SensorSourceDialog`,
  * *When* the "Quelle" ("Source Device") section header renders,
  * *Then* it SHALL render with `TTColor.SensorSourceActive` (`Color(0xFF2E7D32)`), maintaining $\ge 3.0:1$ WCAG AA bold contrast across Light, Dark, and AMOLED themes.
* **Criterion 2: Active Backups Header Rendering in Lighter Green**:
  * *Given* active backup sensors streaming telemetry,
  * *When* the "Aktive Backups" section header renders,
  * *Then* it SHALL render with `TTColor.SensorBackupActive` (`Color(0xFF43A047)`), clearly differentiated from the primary source header and achieving $\ge 3.0:1$ contrast.
* **Criterion 3: Disconnected Devices Header in Neutral Grey**:
  * *Given* paired sensors that are not connected,
  * *When* the "Nicht verbunden" section header renders,
  * *Then* it SHALL render in neutral grey using `MaterialTheme.colorScheme.onSurfaceVariant`.
* **Criterion 4: Strict Architectural Decoupling from Route Blue Tokens**:
  * *Given* static code analysis of `SensorSourceDialog.kt`,
  * *When* inspecting import and color references,
  * *Then* zero occurrences of `TTColor.RouteSelected` or `TTColor.RouteUnselected` SHALL exist.
* **Criterion 5: Route Domain Immutability**:
  * *Given* map route visualization components,
  * *When* route polylines and HUD cards render,
  * *Then* `TTColor.RouteSelected` (`0xFF1565C0`) and `TTColor.RouteUnselected` (`0xFF90CAF9`) SHALL retain their Royal Blue values with zero visual or behavioral regressions.

### 1.4 System Invariants
1. **Route Palette Immutability**: `TTColor.RouteSelected` (`Color(0xFF1565C0)`), `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`), and related navigation tokens MUST NOT be modified (`REQ-UI-306`, `TST-UI-266`).
2. **SensorSourceDialog Functional Integrity**: Truthful connection status indicators (`(verfügbar)` / `(nicht verbunden)`), LED 18dp horizontal center alignment, and canonical priority sorting (`REQ-UI-268`, `TST-UI-227`) MUST remain 100% preserved.
3. **Clean-Room Test Pass Rate**: All existing unit and contract tests MUST pass with 100% clean-room success (`REQ-PRO-001`).

---

## 2. Test Specification (TST-UI-052)

### Test Case 1: `SensorSourceDialog` Color Token Decoupling Contract (`TST-UI-052.1`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogContractTest.kt`
* **Preconditions**: `SensorSourceDialog.kt` source code is available on classpath.
* **Action**: Perform AST / source code inspection on `SensorSourceDialog.kt`.
* **Expected Result**:
  1. `SensorSourceDialog.kt` contains ZERO references to `TTColor.RouteSelected`.
  2. `SensorSourceDialog.kt` contains ZERO references to `TTColor.RouteUnselected`.
  3. "Quelle" header explicitly assigns `color = TTColor.SensorSourceActive`.
  4. "Aktive Backups" header explicitly assigns `color = TTColor.SensorBackupActive`.
  5. "Nicht verbunden" header explicitly assigns `color = MaterialTheme.colorScheme.onSurfaceVariant`.

### Test Case 2: Semantic Green Token Values & WCAG Contrast Evaluation (`TST-UI-052.2`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SensorSourceColorContractTest.kt`
* **Preconditions**: `TTColor` object initialized.
* **Action**: Evaluate exact ARGB hex values and calculate WCAG 2.1 contrast ratios against White (`#FFFFFF`), Dark Slate (`#16161A`), and AMOLED (`#000000`).
* **Expected Result**:
  1. `TTColor.SensorSourceActive == Color(0xFF2E7D32)` (Material Green 800).
  2. `TTColor.SensorBackupActive == Color(0xFF43A047)` (Material Green 600).
  3. `TTColor.calculateContrastRatio(TTColor.SensorSourceActive, Color.White) >= 4.5`.
  4. `TTColor.calculateContrastRatio(TTColor.SensorSourceActive, Color(0xFF16161A)) >= 3.0`.
  5. `TTColor.calculateContrastRatio(TTColor.SensorBackupActive, Color.White) >= 3.0`.
  6. `TTColor.calculateContrastRatio(TTColor.SensorBackupActive, Color(0xFF16161A)) >= 4.5`.
  7. Chromatic distance between `SensorSourceActive` and `SensorBackupActive` is $> 0.10$, ensuring clear visual differentiation.

### Test Case 3: Route Domain Immutability Contract (`TST-UI-052.3`)
* **Scope**: Regression Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/RouteColorPaletteContractTest.kt`
* **Preconditions**: Full route palette defined in `TTColor`.
* **Action**: Execute `RouteColorPaletteContractTest.testRouteColorTokens_haveExactRoyalBlueValues`.
* **Expected Result**:
  1. `TTColor.RouteSelected == Color(0xFF1565C0)`.
  2. `TTColor.RouteUnselected == Color(0xFF90CAF9)`.
  3. `TTColor.RouteActiveNavigation == Color(0xFF1E88E5)`.
  4. `TTColor.RouteActiveNavigationOverlay == Color(0xFF0D47A1)`.

### Test Case 4: 9-Language Localization Audit (`TST-UI-052.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify string presence and non-empty translations across all 9 locales:
  * `source_device`, `source_active_backups`, `source_not_connected`
  * Locales: EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero formatting syntax errors.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-052.5`)
* **Scope**: Whole-Project Regression Test
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.
* **Expected Result**: All 3,150+ unit and contract tests pass with zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-052.1` | Contract | `SensorSourceDialogContractTest` | `REQ-UI-051.2` | Specified |
| `TST-UI-052.2` | Unit / Contract | `SensorSourceColorContractTest` | `REQ-UI-051.1`, `REQ-UI-051.3` | Specified |
| `TST-UI-052.3` | Contract | `RouteColorPaletteContractTest` | `REQ-UI-051.4`, `REQ-UI-306` | Specified |
| `TST-UI-052.4` | Localization | `TranslationParityTest` | `REQ-UI-051`, `REQ-UI-106` | Specified |
| `TST-UI-052.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
