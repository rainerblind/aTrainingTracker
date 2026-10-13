# Stage 3: Implementation Plan - ATT-3314: Display link icon on equipment matrix tabs in Sensors screen to clarify sensor assignment

**Ticket**: [ATT-3314](https://rainerblind.atlassian.net/browse/ATT-3314)  
**Sub-task**: [ATT-3404](https://rainerblind.atlassian.net/browse/ATT-3404) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-284` (amended & extended)  
**Test Mapping**: `TST-UI-244` (extended)  
**Branch**: `feature/ATT-3314`  
**Author**: AI Agent 1 (Software Construction Architect)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*, `ATT-2465`), targeting `DevicesTabbedScreen.kt`, `EquipmentSensorMatrixScreen.kt`, and `EquipmentTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Tickets `ATT-2126` (commit `fc10ad81`) and `ATT-2382` (Sprint `2026-40.16`) established the equipment-sensor mapping matrix.
   - Sprint 2026-41.2 (`ATT-2465`, commit `3a18a931`) relocated the matrix to `DevicesTabbedScreen.kt` as Tab 3 ("Räder") and Tab 4 ("Schuhe") using `PrimaryScrollableTabRow`.
3. **Root Reason for Existing Formulation**:
   - In `ATT-2465`, tabs 3 and 4 were labeled simply with `@string/devices_tab_bikes` ("Räder") and `@string/devices_tab_shoes` ("Schuhe").
   - During the Sprint 2026-41.10 Joint Review of `ATT-3166`, user feedback indicated that without an icon, these labels read identically to equipment inventory tabs in `EquipmentTabsScreen.kt`, obscuring the fact that they represent the interactive sensor-to-equipment assignment matrix.
4. **Preservation of Core Invariants**:
   - 5-Tab Sequence: Tab order (0: Available, 1: Paired, 2: All Known, 3: Bikes Matrix, 4: Shoes Matrix) remains unchanged.
   - Uniform Tab Row Ergonomics: Tab height remains 48.dp without vertical inflation or baseline misalignment between text-only tabs (0–2) and matrix tabs (3–4).
   - Independent Scroll States: `bikesHorizontalScrollState` and `shoesHorizontalScrollState` remain completely isolated.
   - 1-Tap Checkbox Persistence: Immediate SQLite `LINKS` table persistence via `EquipmentViewModel.setSensorLink` remains intact.
   - 100% 9-Language Localization Parity: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) maintain complete translation parity without missing tokens or broken specifiers.
   - Clean-Room Full Suite Regression: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Architectural Decomposition & SWE.2 Design

### 1.1 Architecture & Component Boundaries
- **UI Presentation Layer (`DevicesTabbedScreen.kt`)**:
  - Encapsulated within `PrimaryScrollableTabRow` inside `DevicesTabbedScreen`.
  - Distinguishes sensor inventory tabs (indices 0, 1, 2) from equipment-sensor mapping matrix tabs (indices 3, 4).
  - Uses an inline `Row` inside the `text` slot of `Tab` for indices 3 and 4, pairing a 16.dp `Icons.Default.Link` with a 6.dp `Spacer` and `Text(title)`.
  - Icon tint dynamically inherits `LocalContentColor.current` to match standard M3 tab selection color semantics.
- **Contract Verification (`DevicesTabbedScreenContractTest.kt`)**:
  - Validates that `DevicesTabbedScreen.kt` references `Icons.Default.Link`.
  - Asserts that matrix tabs (3 and 4) render the leading icon with 16.dp size and 6.dp spacing.
  - Verifies that inventory tabs (0, 1, 2) remain text-only.

### 1.2 UI Consistency (Rule 23 Audit)
- **Reference Screen**: `DevicesTabbedScreen.kt` header tab row.
- **Reused Components**: Material 3 `PrimaryScrollableTabRow`, `Tab`, `Icon`, `Text`, `Spacer`, `Icons.Default.Link`.
- **Theme Tokens**:
  - `Icons.Default.Link` from `androidx.compose.material.icons.filled.Link`.
  - Size: `16.dp` (compact icon fitting inside standard 48.dp tab height without displacing baseline).
  - Spacing: `6.dp` horizontal spacer.
  - Tint: `LocalContentColor.current` (automatically resolves to primary on selected, on-surface-variant on unselected).
- **One-off Styles**: Zero custom styles; standard Material 3 tab mechanics and design tokens are preserved.

---

## 2. Step-by-Step Implementation Sequence

### Step 1: Update Contract Tests in `DevicesTabbedScreenContractTest.kt`
- Add tests in `DevicesTabbedScreenContractTest.kt` verifying:
  - Reference to `Icons.Default.Link`.
  - Matrix tab check (`index == 3 || index == 4` or `isMatrixTab`).
  - Leading `Icon` with `Modifier.size(16.dp)` and `Spacer(modifier = Modifier.width(6.dp))`.
  - Reactive tinting via `LocalContentColor.current`.
- Run: `./gradlew testDebugUnitTest --tests com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenContractTest` (assert test fails before implementation).

### Step 2: Implement Leading Link Icon in `DevicesTabbedScreen.kt`
- In `DevicesTabbedScreen.kt`, import `androidx.compose.material.icons.filled.Link` and `androidx.compose.material3.LocalContentColor`.
- In `PrimaryScrollableTabRow` (around line 322):
  - Determine `val isMatrixTab = index == 3 || index == 4`.
  - If `isMatrixTab`, render:
    ```kotlin
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = LocalContentColor.current
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(title)
    }
    ```
  - Otherwise, render `Text(title)`.

### Step 3: Verify Unit & Contract Tests
- Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenContractTest` asserting all tests pass.
- Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.TranslationParityTest` asserting 100% localization parity.

### Step 4: Full Clean-Room Regression Suite
- Run full suite: `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.

---

## 3. Invariants & Rollback Safety

- **Zero Regression on Equipment Matrix Functionality**: Checkboxes, sticky bike/shoe name column, horizontal scrolling, and database synchronization are completely untouched.
- **Visual Stability**: Standard 48.dp tab row height is strictly preserved across all screen sizes and densities.
- **Rollback Safety**: The change is strictly confined to the tab text composable lambda in `DevicesTabbedScreen.kt`. In case of unexpected visual regressions, reverting the git commit restores the previous text-only tab layout cleanly.
