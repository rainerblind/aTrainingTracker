# Stage 1 Analysis: ATT-5839 - AppNavigationDrawer (DrawerHeader): Reduce header height by ~16dp (trim top sky) so 'Info & Unterstützung' fits without bottom clipping

**Ticket**: [ATT-5839](https://rainerblind.atlassian.net/browse/ATT-5839)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5839`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During the visual UI audit of the **Navigation Drawer** (`audit_02_drawer.png`), the bottom-most navigation entry **`Info & Unterstützung`** (`drawer__info_support`) is clipped at the bottom display boundary on standard portrait screens:
1. The section header is cut vertically in half, and its subtitle (*"Über die App, Spenden, Datenschutz"*) is obscured beneath the navigation bar.
2. Users must perform a vertical scroll gesture in the drawer simply to reveal or tap the final essential support, donation, and privacy menu items.
3. At the top of the drawer, `DrawerHeader()` allocates `92.dp + statusBarHeight`, where an expansive ~40% band of plain blue sky sits empty above the app icon and `"a Training Tracker"` title, unnecessarily consuming vertical viewport real estate.

---

## 2. Root Cause Analysis & Visual Inspection
1. **Excessive Header Allocation (`AppNavigationDrawer.kt:314`)**:
   - `DrawerHeader()` specifies `.height(92.dp + statusBarHeight)`.
   - The background artwork `R.drawable.menu_header_background` is anchored via `alignment = Alignment.BottomCenter` and `contentScale = ContentScale.FillWidth`.
   - The scenic landscape motif (meadow, trees, hills, road) is anchored to the bottom edge, while the top portion is monochromatic blue sky.
   - Net vertical height below `statusBarHeight` is `92.dp`, whereas the branding row requires only `46.dp` for the app icon and `21.sp` for the title.
2. **Visual Inspection (`audit_02_drawer.png` via `read_file`, Rule 71 / Rule 72)**:
   - Visual audit confirms `Info & Unterstützung` is clipped by ~15 dp at the screen bottom.
   - The header displays ~35–40 dp of unoccupied sky above the branding row.
   - Trimming `16.dp` from the top of the header (lowering net height from `92.dp` to `76.dp`) reclaims the exact vertical clearance required to bring `Info & Unterstützung` fully into view.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - In `DrawerHeader()` (`AppNavigationDrawer.kt:314`), reduce the height modifier from `92.dp + statusBarHeight` to `76.dp + statusBarHeight`.
  - Maintain `alignment = Alignment.BottomCenter` so the bottom landscape motif remains 100% visible and anchored at the bottom edge.
  - Adjust the branding `Row` padding (`top = statusBarHeight + 1.dp, bottom = 7.dp` or `verticalAlignment = Alignment.CenterVertically`) so the `46.dp` icon and `21.sp` title sit harmoniously in the `76.dp` net height band.
  - Add contract tests in `AppNavigationDrawerContractTest.kt` verifying the `76.dp` header height and bottom-center alignment contract.
* **Out-of-Scope Non-Goals**:
  - Modifying drawer navigation items, group ordering, typography, or icon assets.
  - Altering the `menu_header_background.png` raster resource file itself.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Historical Trace & Prior Tickets**:
  - `ATT-5371` / `ATT-5375` (Design Guideline 5.16 / Rule 72): `DrawerHeader` uses a `46.dp` app icon slot with `graphicsLayer { scaleX = 1.81f; scaleY = 1.81f }` for non-standard tier adaptive icons and `logo_512.png` for standard tier.
  - `ATT-1036` / `ATT-1037` (`REQ-STB-006`): `DrawerHeader` dynamic status bar handling (`statusBarHeight`) ensures edge-to-edge immunity.
* **Preservation of Core Invariants**:
  - Preserve `46.dp` icon size and `scaleX = 1.81f; scaleY = 1.81f` adaptive icon safe-zone scaling.
  - Preserve single-line layout (`Box(46.dp) + Spacer(10.dp) + Text(21.sp)`).
  - Preserve `alignment = Alignment.BottomCenter` and `ContentScale.FillWidth`.

---

## 5. Architectural Strategy & High-Level Solution
* In `AppNavigationDrawer.kt`:
  - Change `DrawerHeader` height to `.height(76.dp + statusBarHeight)`.
  - Set `Row` padding to `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 6.dp)` with `verticalAlignment = Alignment.CenterVertically`.
* In `AppNavigationDrawerContractTest.kt`:
  - Assert that `AppNavigationDrawer.kt` source declares `.height(76.dp + statusBarHeight)` and `Alignment.BottomCenter`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Header background remains anchored at `Alignment.BottomCenter`.
  2. Launcher icon retains `46.dp` size and tier-based `1.81f` scale factor.
  3. Dynamic status bar inset handling preserved.
* **Risk Rating**: **LOW** (Declarative Compose layout refinement in a single composable; zero database, state machine, or backend impact).
