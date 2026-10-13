# Stage 4: Implementation Deliverable - ATT-3314: Display link icon on equipment matrix tabs in Sensors screen to clarify sensor assignment

**Ticket**: [ATT-3314](https://rainerblind.atlassian.net/browse/ATT-3314)  
**Sub-task**: [ATT-3405](https://rainerblind.atlassian.net/browse/ATT-3405) (`[Implementation]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-284` (amended & extended)  
**Test Mapping**: `TST-UI-244` (extended)  
**Branch**: `feature/ATT-3314`  
**Author**: AI Agent 1 (Software Construction Engineer)  
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

## 1. Summary of Changes

### 1.1 UI Presentation Layer (`DevicesTabbedScreen.kt`)
- Added import for `androidx.compose.material.icons.filled.Link`.
- In `PrimaryScrollableTabRow`, modified the `Tab` composable text rendering lambda:
  - Check `val isMatrixTab = index == 3 || index == 4`.
  - For matrix tabs (Bikes at index 3, Shoes at index 4), composed a horizontal `Row` containing:
    - `Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = LocalContentColor.current)`
    - `Spacer(modifier = Modifier.width(6.dp))`
    - `Text(title)`
  - For inventory tabs (0, 1, 2), preserved clean text-only rendering (`Text(title)`).
  - Maintained uniform 48.dp tab height and seamless dynamic theming where `tint` follows tab selection state.

### 1.2 Contract & Regression Testing (`DevicesTabbedScreenContractTest.kt`)
- Added `testDevicesTabbedScreen_matrixTabsLinkIconContract()` asserting:
  - Presence of `Icons.Default.Link`.
  - Check for matrix tabs at indices 3 and 4 (`index == 3 || index == 4`).
  - Strict 16.dp icon size (`Modifier.size(16.dp)`).
  - 6.dp spacing between icon and title (`Spacer(modifier = Modifier.width(6.dp))`).
  - Dynamic tinting via `LocalContentColor.current`.

---

## 2. Verification & Targeted Test Results

1. **`DevicesTabbedScreenContractTest`**:
   - `testDevicesTabbedScreen_fiveTabsContract`: **PASSED**
   - `testDevicesTabbedScreen_matrixTabsLinkIconContract`: **PASSED**
2. **`TranslationParityTest`**:
   - `testStringParityAcrossAllLocales`: **PASSED** (100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT).
