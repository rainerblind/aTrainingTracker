# Stage 3: Implementation Plan - ATT-6216: Position DrawerHeader branding higher in sky area

**Ticket**: [ATT-6216](https://rainerblind.atlassian.net/browse/ATT-6216)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6216`  
**Requirement Mapping**: `REQ-UI-496` (`REQ-UI-496.1`–`REQ-UI-496.3`)  
**Test Mapping**: `TST-UI-496` (`TST-UI-496.1`–`TST-UI-496.3`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
In Sprint Review 2026-45.12 (`att_5839_drawer_header.png`), reducing `DrawerHeader` height in ATT-5839 ensured *Info & Unterstützung* fits without bottom clipping. However, placing the branding row with `bottom = 6.dp` positioned the app icon and blue title *"a Training Tracker"* directly on the dark forest treeline (~35dp from bottom edge), impairing contrast. The top 67% of the header is empty blue sky. Increasing `bottom` padding to `24.dp` lifts the branding row into the clear sky while keeping the treeline and meadow intact below.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-496` (*Navigation Drawer Header Height Compactness & Vertical Clearance Guard*)
  * `REQ-UI-496.1`: `DrawerHeader()` SHALL specify total height `76.dp + statusBarHeight`.
  * `REQ-UI-496.2`: `menu_header_background` SHALL remain anchored at `Alignment.BottomCenter` with `ContentScale.FillWidth`.
  * `REQ-UI-496.3`: Branding row SHALL apply `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)` with `verticalAlignment = Alignment.CenterVertically`. Icon slot retains `46.dp` size and `1.81f` adaptive scaling (Rule 72).
* **Test Mapping**: `TST-UI-496` (*Navigation Drawer Header Verification Suite*)
  * `TST-UI-496.1`: Header height and bottom-center alignment contract test in `AppNavigationDrawerContractTest.kt`.
  * `TST-UI-496.2`: Branding row padding and Rule 72 icon invariant contract test in `AppNavigationDrawerContractTest.kt`.
  * `TST-UI-496.3`: Tier 1 targeted unit test regression suite (`ui.navigation.*`, `ThemeColorCentralizationAuditTest`, `build.*`).

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-496.3` (*Branding Row Sky Placement & Rule 72 Icon Invariant*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-1083`, refined in `ATT-5371`/`ATT-5397`, and adjusted in `ATT-5839` (commit `d22358e188150d59fccc929b78015a33d5a365de`) and `ATT-6216`.
* **Root Reason for Existing Formulation**: ATT-5839 applied `bottom = 6.dp` assuming vertical centering inside the 76dp net height band would balance elements, but unintentionally forced the branding row down into the dark treeline at the bottom of the 4:3 image.
* **Preservation of Core Invariants**: Increasing `bottom` padding to `24.dp` shifts the row into the upper sky area while strictly preserving: (1) `46.dp` launcher icon size and `1.81f` adaptive scaling (`Rule 72`), (2) single-line title layout, and (3) `76.dp + statusBarHeight` overall height bounding (`REQ-UI-496.1`).

---

## 3. Proposed Architectural Changes & UI Consistency
* **`AppNavigationDrawer.kt`**:
  * Line 348: Update `Row` padding from `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 6.dp)` to `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)`.
* **`AppNavigationDrawerContractTest.kt`**:
  * Update `testDrawerHeaderBrandingRowAndRule72Invariant_REQ_UI_496_2()` to assert `bottom = 24.dp`.
* **UI Consistency**:
  * Reference composable: `DrawerHeader()` in `AppNavigationDrawer.kt`.
  * Spacing Scale: `24.dp` is a first-class standard token in Design Guideline Section 5.2 (`4.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp`).
  * Palette Tokens: `R.color.my_blue`, `MaterialTheme.colorScheme.surfaceVariant`, `menu_header_background.jpg`. Zero ad-hoc colors.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Verify Gate 3 Pass & Code Lock Release**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **Step 2: Update AppNavigationDrawer.kt**: Adjust `Row` padding to `bottom = 24.dp`.
3. **Step 3: Update Contract Test**: Update `AppNavigationDrawerContractTest.kt` assertion for `bottom = 24.dp`.
4. **Step 4: Execute Tier 1 Targeted Unit Tests**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`.
5. **Step 5: Verify Requirement Governance**: Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`.

---

## 5. System Invariants & Rollback Plan
* **Core Invariants**: Zero regression in drawer item visibility (*Info & Unterstützung*), `46.dp` launcher icon size, `1.81f` supporter scaling, 100% unit test pass rate.
* **Rollback Plan**: Atomic commit on `feature/ATT-6216`; revert cleanly via `git checkout .` if any step fails.
