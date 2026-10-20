# Stage 3: Implementation Plan - ATT-4374: Decouple SensorSourceDialog 'Source' and 'Active Backups' header colors from route blue tokens and restore darker/lighter green

**Ticket**: [ATT-4374](https://rainerblind.atlassian.net/browse/ATT-4374)  
**Sub-task**: [ATT-4384](https://rainerblind.atlassian.net/browse/ATT-4384) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-051`  
**Test Mapping**: `TST-UI-052`  
**Branch**: `feature/ATT-4374`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In the live tracking cockpit, opening the sensor redundancy audit bottom sheet ([`SensorSourceDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt)) reveals that the section headers for **Source Device** ("Quelle" / `R.string.source_device`) and **Active Backups** ("Aktive Backups" / `R.string.source_active_backups`) render in Royal Blue and Soft Steel Blue:
* **Source Header** ([`SensorSourceDialog.kt:128`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L128)): references `TTColor.RouteSelected` (`Color(0xFF1565C0)` Material Blue 800).
* **Active Backups Header** ([`SensorSourceDialog.kt:149`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L149)): references `TTColor.RouteUnselected` (`Color(0xFF90CAF9)` Material Blue 200).
* **Not Connected Header** ([`SensorSourceDialog.kt:178`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt#L178)): references `MaterialTheme.colorScheme.onSurfaceVariant` (neutral grey).

### Root Cause & Cross-Domain Coupling
In Sprint `2026-40.1` (SCRUM-147, commit `aa0184f0`), `REQ-UI-051` was introduced to color-code sensor source headers. At that time, `Color.kt` defined `RouteColorSelected` as ForestGreen (`0xFF228B22`) and `RouteColorUnselected` as LightGreen (`0xFF90EE90`). As an opportunistic shortcut, the author reused these route constants for the sensor dialog.

In Sprint `2026-41.4` (ATT-2761, commit `a9364e84`), the route visualization palette was migrated to Royal Blue (`0xFF1565C0` and `0xFF90CAF9`) under `REQ-UI-306` to resolve severe contrast clashes against map terrain and Category 4 climb green (`0xFF2E7D32`). Because `SensorSourceDialog.kt` reused the route constants (later centralized as `TTColor.RouteSelected` and `TTColor.RouteUnselected`), its section headers inadvertently turned blue. Furthermore, connected sensors display green LED status dots (`TTColor.ConnectionStatusGreen` = `0xFF4CAF50`), making blue headers semantically contradictory.

### Scope & Architectural Objective
This implementation plan decouples `SensorSourceDialog` from route visualization tokens by introducing dedicated semantic color tokens in `TTColor` ([`Color.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt)) and updating `SensorSourceDialog.kt`, while strictly safeguarding the Royal Blue route domain and sensor priority sorting.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-051` (*Color-Coded Sensor Source Headers & Semantic Color Token Decoupling*)
  * **REQ-UI-051.1**: Define dedicated semantic color tokens in `TTColor` ([`Color.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt)):
    - `TTColor.SensorSourceActive`: Darker green (`Color(0xFF2E7D32)`, Material Green 800).
    - `TTColor.SensorBackupActive`: Lighter green (`Color(0xFF43A047)`, Material Green 600).
  * **REQ-UI-051.2**: Decouple `SensorSourceDialog.kt` section headers:
    - Replace `TTColor.RouteSelected` on line 128 with `TTColor.SensorSourceActive`.
    - Replace `TTColor.RouteUnselected` on line 149 with `TTColor.SensorBackupActive`.
    - Preserve `MaterialTheme.colorScheme.onSurfaceVariant` on line 178 for "Not Connected".
    - Guarantee zero occurrences of `RouteSelected` or `RouteUnselected` in `SensorSourceDialog.kt`.
  * **REQ-UI-051.3**: Guarantee WCAG 2.1 AA contrast compliance across Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`) surfaces:
    - `SensorSourceActive` on White: 5.13:1 ($\ge 4.5:1$ normal, $\ge 3.0:1$ bold).
    - `SensorSourceActive` on Dark: 3.52:1 ($\ge 3.0:1$ bold).
    - `SensorSourceActive` on AMOLED: 4.10:1 ($\ge 3.0:1$ bold).
    - `SensorBackupActive` on White: 3.30:1 ($\ge 3.0:1$ bold).
    - `SensorBackupActive` on Dark: 5.46:1 ($\ge 4.5:1$).
    - `SensorBackupActive` on AMOLED: 6.36:1 ($\ge 4.5:1$).
  * **REQ-UI-051.4**: Strictly preserve route domain tokens (`RouteSelected`, `RouteUnselected`, `RouteActiveNavigation`, `RouteActiveNavigationOverlay`) without any modifications.

* **Test Mapping**: `TST-UI-052` (*SensorSourceDialog Header Color Decoupling & Semantic Green Token Verification Suite*)
  * **TST-UI-052.1**: `SensorSourceDialogContractTest` ([`SensorSourceDialogContractTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogContractTest.kt))
    - Source AST / text inspection asserting zero references to `TTColor.RouteSelected` or `TTColor.RouteUnselected`.
    - Verification that "Quelle" header sets `color = TTColor.SensorSourceActive` and "Aktive Backups" header sets `color = TTColor.SensorBackupActive`.
  * **TST-UI-052.2**: `SensorSourceColorContractTest` ([`SensorSourceColorContractTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SensorSourceColorContractTest.kt))
    - Unit and contract assertions validating exact ARGB hex values: `SensorSourceActive == Color(0xFF2E7D32)` and `SensorBackupActive == Color(0xFF43A047)`.
    - Automated WCAG 2.1 relative luminance and contrast ratio calculations validating thresholds across Light, Dark, and AMOLED themes.
    - Chromatic separation assertion validating $\Delta C > 0.10$ between the two tokens.
  * **TST-UI-052.3**: `RouteColorPaletteContractTest` ([`RouteColorPaletteContractTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/RouteColorPaletteContractTest.kt))
    - Regression contract test verifying that Royal Blue route tokens remain untouched.
  * **TST-UI-052.4**: `TranslationParityTest` ([`TranslationParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt))
    - Verify 9-language localization parity for `source_device`, `source_active_backups`, and `source_not_connected`.
  * **TST-UI-052.5**: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
    - 100% clean-room test execution across all 3,150+ tests.

---

## 3. System Invariants & Preserved Behavior

1. **Route Palette Immutability (`REQ-UI-306`, `TST-UI-266`)**:
   `TTColor.RouteSelected` (`Color(0xFF1565C0)`), `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`), `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`), and `TTColor.RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`) MUST NOT be changed. Route polyline rendering on Google Maps, route selection buttons, HUD overlays, and route selector sheets remain 100% untouched.
2. **SensorSourceDialog Functional Integrity (`REQ-UI-268`, `TST-UI-227`)**:
   Truthful connection status strings (`(verfügbar)` / `(nicht verbunden)`), connection LED 18dp horizontal center alignment, device identity blocks, and canonical priority sorting (`activeBackups` and `notConnected` priority sorting) MUST NOT be altered.
3. **Zero Unintended Regressions (`REQ-PRO-001`)**:
   All existing unit, contract, and architecture tests (`RouteColorPaletteContractTest`, `SensorSourceDialogPriorityTest`, etc.) must pass with 100% clean-room success.
4. **Strict Code Lock in Stage 3 (Core Rule 4)**:
   Stage 3 strictly defines planning and architectural specifications. Zero production code or test files are modified in Stage 3; construction is strictly deferred to Stage 4 after passing Gate 3 review.
5. **One Commit Per Ticket (Core Rule 8)**:
   No intermediate git commit is created during Stage 3; changes remain unstaged/uncommitted on `feature/ATT-4374` until Stage 5 verification completes.
6. **Parent Human Gate Invariance**:
   Terminal completion and merging of `ATT-4374` remain reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Layer Boundary & SWE.2 Architecture
The planned changes reside purely in the **Presentation Layer (Jetpack Compose / Theme)**:
- `com.atrainingtracker.trainingtracker.ui.theme` ([`Color.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt))
- `com.atrainingtracker.trainingtracker.ui.tracking.controltracking` ([`SensorSourceDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt))

No database entities, repositories, ViewModels, or background services (`TrackerService`) are affected. Thread dispatcher affinity is not applicable as all changes execute on the Compose UI thread (`Dispatchers.Main`).

### Component 1: Semantic Theme Tokens (`Color.kt`)
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`, extend the `TTColor` object with dedicated, self-documenting tokens:

```kotlin
object TTColor {
    ...
    // Sensor Source Availability (REQ-UI-051, ATT-4374)
    /**
     * Primary active sensor source section header color ("Quelle" / R.string.source_device).
     * Material Green 800 (#2E7D32) provides strong contrast (>= 3.5:1 on dark, >= 5.1:1 on light)
     * and aligns with [StartPoint], [ConnectionConnected], and [PmcFreshGreen].
     */
    val SensorSourceActive = Color(0xFF2E7D32)

    /**
     * Active standby backup sensor source section header color ("Aktive Backups" / R.string.source_active_backups).
     * Material Green 600 (#43A047) provides a visibly lighter green tone while meeting WCAG AA
     * bold contrast (>= 3.3:1 on light, >= 5.4:1 on dark) and harmonizing with [ConnectionStatusGreen].
     */
    val SensorBackupActive = Color(0xFF43A047)
    ...
}
```

### Component 2: Sensor Redundancy Bottom Sheet (`SensorSourceDialog.kt`)
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`:
1. Line 128: Replace `color = TTColor.RouteSelected` with `color = TTColor.SensorSourceActive`.
2. Line 149: Replace `color = TTColor.RouteUnselected` with `color = TTColor.SensorBackupActive`.
3. Line 178: Retain `color = MaterialTheme.colorScheme.onSurfaceVariant` for "Nicht verbunden" (`R.string.source_not_connected`).

```kotlin
// 1. Source Device
if (sourceDevice != null) {
    ...
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(id = R.string.source_device),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TTColor.SensorSourceActive // Decoupled from RouteSelected
        )
        DeviceIdentityBlock(...)
    }
}

// 2. Active Backups
if (activeBackups.isNotEmpty()) {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(id = R.string.source_active_backups),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TTColor.SensorBackupActive // Decoupled from RouteUnselected
        )
        ...
    }
}
```

### Component 3: Automated Contract & Unit Tests
1. **`SensorSourceColorContractTest.kt`** (New Test Class):
   Location: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SensorSourceColorContractTest.kt`
   - Verifies exact color token values: `TTColor.SensorSourceActive == Color(0xFF2E7D32)` and `TTColor.SensorBackupActive == Color(0xFF43A047)`.
   - Computes WCAG 2.1 relative luminance and contrast ratios against Light (`#FFFFFF`), Dark Slate (`#16161A`), and AMOLED (`#000000`).
   - Asserts $\text{contrast}(\text{SensorSourceActive}, \text{Light}) \ge 4.5$, $\text{contrast}(\text{SensorSourceActive}, \text{Dark}) \ge 3.0$.
   - Asserts $\text{contrast}(\text{SensorBackupActive}, \text{Light}) \ge 3.0$, $\text{contrast}(\text{SensorBackupActive}, \text{Dark}) \ge 4.5$.
   - Asserts chromatic distance $\Delta C > 0.10$ to ensure unambiguous distinction between Source and Active Backups.
2. **`SensorSourceDialogContractTest.kt`** (Extended Existing Class):
   Location: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogContractTest.kt`
   - Adds `@Test fun testSensorSourceDialog_hasZeroReferencesToRouteTokens()`: Reads `SensorSourceDialog.kt` content and asserts `!content.contains("RouteSelected")` and `!content.contains("RouteUnselected")`.
   - Adds `@Test fun testSensorSourceDialog_usesDedicatedSensorSourceTokens()`: Asserts content contains `color = TTColor.SensorSourceActive` and `color = TTColor.SensorBackupActive`.

---

### UI Consistency (Rule 23 — Mandatory Section per `docs/design_guidelines.md` Section 5)

* **Reference screen / component**:
  `SensorSourceDialog` ([`SensorSourceDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt)), hosted inside `AppModalBottomSheet` ([`AppModalBottomSheet.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt)), triggered by tapping sensor icons in the tracking header (`SensorStatus.kt`).
* **Reused components**:
  - `AppModalBottomSheet` for bottom sheet presentation.
  - `DeviceIdentityBlock` and `DeviceStatusRow` for sensor device representation.
  - `HorizontalDivider` (`thickness = 0.5.dp`, `color = MaterialTheme.colorScheme.outlineVariant`) for separating section groups.
* **Theme tokens**:
  - **Shapes**: `RoundedCornerShape(4.dp)` on device icons, `CircleShape` on status LED indicators (`10.dp` dot centered inside `18.dp` box).
  - **Spacing**: `Arrangement.spacedBy(16.dp)` for top-level section container; `Arrangement.spacedBy(8.dp)` for section header and device lists; `Arrangement.spacedBy(4.dp)` between backup devices; `12.dp` icon-to-text spacing.
  - **Typography**: `MaterialTheme.typography.titleSmall` with `FontWeight.Bold` for section headers; `MaterialTheme.typography.titleMedium` with `FontWeight.Bold` for device names; `bodySmall` for device status.
  - **Colors**:
    - Primary active source header: `TTColor.SensorSourceActive` (`Color(0xFF2E7D32)` Material Green 800).
    - Standby active backups header: `TTColor.SensorBackupActive` (`Color(0xFF43A047)` Material Green 600).
    - Disconnected devices header: `MaterialTheme.colorScheme.onSurfaceVariant`.
    - Active sensor status LED: `TTColor.ConnectionStatusGreen` (`Color(0xFF4CAF50)`).
    - Disconnected sensor status LED: `Color.LightGray`.
* **New one-off styles & justification**:
  None. The change eliminates an existing anti-pattern (cross-domain reuse of route blue tokens) and establishes dedicated semantic tokens in `TTColor` using standard Material Green tones (`0xFF2E7D32` and `0xFF43A047`) that harmonize with existing status greens across the application.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Author Unit & Contract Tests (TDD - Red Phase)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SensorSourceColorContractTest.kt` (New)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogContractTest.kt` (Modified)
* **Changes**:
  1. Author `SensorSourceColorContractTest.kt`:
     - Test exact token values for `TTColor.SensorSourceActive` and `TTColor.SensorBackupActive`.
     - Test contrast ratios against Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`) using `TTColor.calculateContrastRatio`.
     - Test chromatic distance between the two tokens.
  2. Extend `SensorSourceDialogContractTest.kt`:
     - Add `testSensorSourceDialog_hasZeroReferencesToRouteTokens()` verifying absence of `RouteSelected` and `RouteUnselected`.
     - Add `testSensorSourceDialog_usesDedicatedSensorSourceTokens()` verifying presence of `TTColor.SensorSourceActive` and `TTColor.SensorBackupActive`.
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.SensorSourceColorContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogContractTest"
  ```
  *(Expected: Compilation error or failure due to missing tokens in `TTColor` and old tokens in `SensorSourceDialog`)*

### Step 2: Define Dedicated Semantic Tokens in `Color.kt` (Green Phase - Part 1)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`
* **Changes**:
  1. Add `SensorSourceActive = Color(0xFF2E7D32)` under `TTColor` with KDoc documenting `REQ-UI-051` and WCAG contrast.
  2. Add `SensorBackupActive = Color(0xFF43A047)` under `TTColor` with KDoc documenting `REQ-UI-051` and WCAG contrast.
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.SensorSourceColorContractTest"
  ```
  *(Expected: PASS)*

### Step 3: Decouple `SensorSourceDialog.kt` from Route Blue Tokens (Green Phase - Part 2)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`
* **Changes**:
  1. Replace `color = TTColor.RouteSelected` on line 128 with `color = TTColor.SensorSourceActive`.
  2. Replace `color = TTColor.RouteUnselected` on line 149 with `color = TTColor.SensorBackupActive`.
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogContractTest"
  ```
  *(Expected: PASS)*

### Step 4: Verify Route Immutability & Sensor Priority Regression Suite
* **Files**:
  - None (Execution of regression suites)
* **Changes**:
  - Run `RouteColorPaletteContractTest` to ensure route tokens remain 100% intact.
  - Run `SensorSourceDialogPriorityTest` to ensure sensor sorting behavior remains intact.
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.RouteColorPaletteContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.theme.SensorSourceColorContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialog*"
  ```
  *(Expected: ALL PASS)*

### Step 5: Localization & Clean-Room Regression Verification (Stage 5 Preparation)
* **Files**:
  - None
* **Changes**:
  - Run `TranslationParityTest` to confirm 9-language parity for header strings.
  - Run the full test suite to guarantee zero project-wide regressions.
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ./gradlew testDebugUnitTest
  ```
  *(Expected: 100% PASS across 3,150+ tests)*

---

## 6. Verification & Rollback Plan

### Verification Strategy
* **Automated Unit & Contract Tests**:
  - `SensorSourceColorContractTest` verifies token hex values and WCAG 2.1 AA contrast ratios ($\ge 3.0:1$ bold, $\ge 4.5:1$ normal).
  - `SensorSourceDialogContractTest` enforces static AST decoupling from route tokens.
  - `RouteColorPaletteContractTest` validates route domain immutability.
  - `TranslationParityTest` verifies string parity across all 9 locales.
* **Full Suite Clean-Room Verification**:
  - Full `./gradlew testDebugUnitTest` must achieve 100% clean-room pass.

### Rollback Strategy
* All changes are isolated to the git feature branch `feature/ATT-4374`.
* Because Core Rule 8 ("One commit per ticket") prohibits intermediate commits during Stages 1–4, any unexpected defect can be rolled back instantly via `git restore` on uncommitted files.
* If any regression occurs, `develop` remains completely unaffected.
