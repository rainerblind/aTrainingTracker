# Stage 3: Implementation Plan - ATT-5839: AppNavigationDrawer (DrawerHeader): Reduce header height by ~16dp (trim top sky) so 'Info & Unterstützung' fits without bottom clipping

**Ticket**: [ATT-5839](https://rainerblind.atlassian.net/browse/ATT-5839)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5839`  
**Requirement Mapping**: `REQ-UI-496` (`REQ-UI-496.1`–`REQ-UI-496.3`)  
**Test Mapping**: `TST-UI-496` (`TST-UI-496.1`–`TST-UI-496.3`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
On standard mobile portrait displays (`audit_02_drawer.png`), the bottom-most Navigation Drawer entry **`Info & Unterstützung`** (`drawer__info_support`) is partially clipped at the bottom display boundary (~15 dp cutoff beneath the system navigation bar). Users must scroll the drawer just to discover or tap the support, donation, and privacy links.
`DrawerHeader()` in `AppNavigationDrawer.kt:314` currently allocates `92.dp + statusBarHeight`. Because the background artwork is anchored at `Alignment.BottomCenter`, the upper ~40% of the header is empty blue sky. Trimming 16 dp from the header height (to `76.dp + statusBarHeight`) reclaims the exact vertical space needed to fit all drawer categories without bottom clipping.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-496` (*Navigation Drawer Header Height Compactness & Full Viewport Fit*)
  * `REQ-UI-496.1`: `DrawerHeader()` in `AppNavigationDrawer.kt` SHALL specify total height `76.dp + statusBarHeight`.
  * `REQ-UI-496.2`: `R.drawable.menu_header_background` SHALL remain anchored at `Alignment.BottomCenter` with `ContentScale.FillWidth`, keeping the horizon landscape artwork 100% visible while trimming only top sky.
  * `REQ-UI-496.3`: Branding row SHALL apply `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 6.dp)` with `verticalAlignment = Alignment.CenterVertically`. Icon slot SHALL preserve `46.dp` size and `1.81f` adaptive icon safe-zone scaling (Rule 72).
* **Test Mapping**: `TST-UI-496` (*Navigation Drawer Header Verification Suite*)
  * `TST-UI-496.1`: Header height and bottom-center alignment contract test in `AppNavigationDrawerContractTest.kt`.
  * `TST-UI-496.2`: Rule 72 branding row and icon invariant contract test in `AppNavigationDrawerContractTest.kt`.
  * `TST-UI-496.3`: Tier 1 targeted regression suite (`ui.navigation.*`, `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Landscape Artwork Anchoring**: `Alignment.BottomCenter` ensures meadow, trees, hills, and road remain 100% visible at the bottom edge.
2. **Design Guideline 5.16 / Rule 72 Invariant**: Launcher icon preserves `46.dp` size and tier-based `1.81f` safe-zone scaling factor without layout distortion.
3. **Single-Line Branding Invariant**: Branding icon and `"a Training Tracker"` title sit side-by-side in a single row without subtitles or extra text pills.
4. **Dynamic Inset Handling**: Edge-to-edge status bar handling (`statusBarHeight = if (topInset > 0.dp) topInset else 36.dp`) remains strictly preserved.

---

## 4. Proposed Architectural Changes & UI Consistency
* **`AppNavigationDrawer.kt`**:
  * Line 314: Update `Box` modifier from `.height(92.dp + statusBarHeight)` to `.height(76.dp + statusBarHeight)`.
  * Line 348: Adjust `Row` padding from `padding(start = 16.dp, end = 16.dp, top = statusBarHeight + 2.dp, bottom = 14.dp)` to `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 6.dp)`.
* **`AppNavigationDrawerContractTest.kt`**:
  * Add unit contract test `testDrawerHeaderHeightAndAlignmentContract_REQ_UI_496` asserting that `AppNavigationDrawer.kt` source declares `.height(76.dp + statusBarHeight)`, `Alignment.BottomCenter`, `Modifier.size(46.dp)`, and `scaleX = 1.81f`.
* **UI Consistency**:
  * Closest reference composable: existing `DrawerHeader()` in `AppNavigationDrawer.kt`.
  * Reused tokens: `R.drawable.menu_header_background`, `R.string.TrainingTracker`, `R.color.my_blue`, `MaterialTheme.colorScheme.surfaceVariant`. Zero one-off styles or new colors.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Production Code Edit (`AppNavigationDrawer.kt`)**:
   - Change `DrawerHeader()` height modifier to `.height(76.dp + statusBarHeight)`.
   - Update `Row` padding to `top = statusBarHeight, bottom = 6.dp`.
2. **Step 2: Automated Contract Test (`AppNavigationDrawerContractTest.kt`)**:
   - Implement `testDrawerHeaderHeightAndAlignmentContract_REQ_UI_496` covering `TST-UI-496.1` and `TST-UI-496.2`.
3. **Step 3: Verification**:
   - Execute: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
   - Execute: `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit and contract tests in Stage 4; on-device visual inspection in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5839`; revert via `git reset --hard` if regression occurs.
