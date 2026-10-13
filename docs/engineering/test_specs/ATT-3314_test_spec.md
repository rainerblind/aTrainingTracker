# Stage 2: Requirement & Test Specification - ATT-3314: Display link icon on equipment matrix tabs in Sensors screen to clarify sensor assignment

**Ticket**: [ATT-3314](https://rainerblind.atlassian.net/browse/ATT-3314)  
**Sub-task**: [ATT-3403](https://rainerblind.atlassian.net/browse/ATT-3403) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-284` (amended & extended)  
**Test Mapping**: `TST-UI-244` (extended)  
**Branch**: `feature/ATT-3314`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*, `ATT-2465`), targeting `DevicesTabbedScreen.kt`, `EquipmentSensorMatrixScreen.kt`, and `EquipmentTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Tickets `ATT-2126` (commit `fc10ad81`) and `ATT-2382` (Sprint `2026-40.16`) introduced the equipment-sensor mapping matrix.
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

## 1. Formal Requirement Specification

### REQ-UI-284 Amendment: Equipment Matrix Tab Link Indicators

The system SHALL amend `REQ-UI-284` clause 1 to incorporate a visual link indicator for matrix tabs in `DevicesTabbedScreen.kt` (ATT-3314):

1. **Sensors View Tab Leading Icon Integration (`DevicesTabbedScreen.kt`)**:
   - Tab 3 (Bikes Matrix, `@string/devices_tab_bikes`) and Tab 4 (Shoes Matrix, `@string/devices_tab_shoes`) SHALL display a link/chain symbol (`Icons.Default.Link`) as a leading icon next to the localized title text.
   - The leading icon SHALL be composed inside the `text` slot of the `Tab` composable using a horizontal `Row` containing:
     - `Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = LocalContentColor.current)`
     - `Spacer(modifier = Modifier.width(6.dp))`
     - `Text(text = title)`
   - Tabs 0 (Available), 1 (Paired), and 2 (All Known) SHALL remain text-only (`Text(title)`).
   - The tab row SHALL maintain standard 48.dp height across all 5 tabs without baseline displacement or height inflation.
   - The icon tint SHALL reactively follow `LocalContentColor.current`, ensuring selected tabs tint the link icon with the primary tab indicator color and unselected tabs tint it with on-surface-variant.

2. **Given-When-Then Acceptance Criteria**:
   - **AC-1: Matrix Tab Visual Link Indicators**:
     - *Given* an athlete opens the Sensors view (`DevicesTabbedScreen`),
     - *When* inspecting the tab bar in `PrimaryScrollableTabRow`,
     - *Then* Tab 3 ("Räder") and Tab 4 ("Schuhe") SHALL display `Icons.Default.Link` with 16.dp size and 6.dp spacing to the left of their localized titles.
   - **AC-2: Inventory Tabs Omission**:
     - *Given* an athlete inspects Tabs 0, 1, and 2 (Available, Paired, Known),
     - *When* examining the tab headers,
     - *Then* they SHALL NOT display the link icon and SHALL render text-only labels.
   - **AC-3: Tab Row Height & Visual Ergonomics**:
     - *Given* the 5-tab scrollable tab row in `DevicesTabbedScreen`,
     - *When* measuring tab dimensions and baselines,
     - *Then* all 5 tabs SHALL share identical 48.dp row height and consistent text baseline vertical alignment.
   - **AC-4: Selection State Dynamic Theming**:
     - *Given* an athlete selects Tab 3 ("Räder") or Tab 4 ("Schuhe"),
     - *When* the tab transitions between unselected and selected states,
     - *Then* the link icon tint SHALL dynamically update to match the active tab typography color (`LocalContentColor.current`).

---

## 2. Test Case Specification

### TST-UI-244 Extension: Architectural Contract & UI Verification

The following verification steps extend `TST-UI-244`:

#### TST-UI-244.1: DevicesTabbedScreen Tab Row Contract Test (`DevicesTabbedScreenContractTest.kt`)
- Verify `DevicesTabbedScreen.kt` references `Icons.Default.Link`.
- Verify `DevicesTabbedScreen.kt` renders `Icons.Default.Link` with `Modifier.size(16.dp)` when `index == 3 || index == 4` (or `isMatrixTab`).
- Verify `DevicesTabbedScreen.kt` includes `Spacer(modifier = Modifier.width(6.dp))` between icon and text.
- Verify `DevicesTabbedScreen.kt` binds `tint = LocalContentColor.current` to match tab typography state.
- Verify `DevicesTabbedScreen.kt` retains all existing tab contracts (`PrimaryScrollableTabRow`, `EquipmentViewModel`, independent horizontal scroll states, mapping to `EquipmentSportSensorMatrix`).

#### TST-UI-244.2: 9-Language Localization & Accessibility Audit (`TranslationParityTest.kt`)
- Verify `devices_tab_bikes` and `devices_tab_shoes` exist and are non-empty across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
- Verify no missing translations, no empty strings, and no mismatched format specifiers.

#### TST-UI-244.3: Clean-Room Full Suite Regression Execution
- Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions across the codebase.

---

## 3. Traceability Matrix

| Requirement Clause | Test Identifier | Verification Method | Target Artifact |
|---|---|---|---|
| `REQ-UI-284.1` (Matrix tab link icon) | `TST-UI-244.1` | Static AST / Source Contract Test | `DevicesTabbedScreenContractTest.kt` |
| `REQ-UI-284.1` (16dp size & 6dp spacing) | `TST-UI-244.1` | Static AST / Source Contract Test | `DevicesTabbedScreenContractTest.kt` |
| `REQ-UI-284.1` (Dynamic color tint) | `TST-UI-244.1` | Static AST / Source Contract Test | `DevicesTabbedScreenContractTest.kt` |
| `REQ-UI-284.4` (9-language parity) | `TST-UI-244.2` | Automated Translation Parity Test | `TranslationParityTest.kt` |
| `REQ-UI-284` (Zero regressions) | `TST-UI-244.3` | Clean-Room Full Suite Regression | Gradle testDebugUnitTest |
