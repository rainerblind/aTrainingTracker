# Stage 1 Analysis: ATT-6216 - AppNavigationDrawer (DrawerHeader): Position app icon and title higher in sky area

**Ticket**: [ATT-6216](https://rainerblind.atlassian.net/browse/ATT-6216)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6216`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In Sprint Review 2026-45.12 (`att_5839_drawer_header.png`), reducing `DrawerHeader` height in ATT-5839 eliminated bottom clipping of *Info & Unterstützung*. However, the app launcher icon and blue title *"a Training Tracker"* sit too low in the header—directly overlapping the dark forest treeline and horizon instead of floating in the open sky above, impairing text contrast.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Vertical Centering in Compressed Slot (`AppNavigationDrawer.kt:345-351`)**:
   - `DrawerHeader` Box specifies `height(76.dp + statusBarHeight)`. The inner branding `Row` specifies `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 6.dp)` with `verticalAlignment = Alignment.CenterVertically`.
   - `menu_header_background.jpg` (1000x750, 4:3) is scaled via `ContentScale.FillWidth` (225dp scaled height in 300dp drawer) with `Alignment.BottomCenter`.
   - The dark treeline/horizon occupies the bottom ~35dp of the image. With `bottom = 6.dp`, vertical centering of the 46dp icon places its bottom at 18dp from the bottom edge and the 21sp title baseline at ~28dp, directly over the dark treeline (`RGB=(103, 105, 116)`).
   - Meanwhile, the top 48dp (`statusBarHeight + 12.dp`) sits empty in pure blue sky (`RGB=(144, 180, 238)`).
2. **Independent Peer Review & Verification**:
   - Verified against `att_5839_drawer_header.png` (ID 11251) via `read_file`: The top 67% (y=170..410px) is unoccupied sky. Logo and text overlap the treeline at y=420..480px.
   - Verified ticket hypothesis: Shifting the Row upward by increasing `bottom` padding to `24.dp` (or reducing `top` offset) lifts the title to ~37dp from the bottom edge, placing it 100% in the high-contrast blue sky while preserving the scenic horizon below.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Adjust branding `Row` vertical placement in `AppNavigationDrawer.kt:348` (increasing `bottom` padding to `24.dp`, spacing scale token per Design Guideline 5.2) to lift icon and title into the sky area.
  - Update layout contract test `testDrawerHeaderBrandingRowAndRule72Invariant_REQ_UI_496_2` in `AppNavigationDrawerContractTest.kt`.
* **Out-of-Scope Non-Goals**:
  - Modifying the overall header height `76.dp + statusBarHeight` (`REQ-UI-496.1`), which preserves *Info & Unterstützung* clearance.
  - Modifying `menu_header_background.jpg` assets or drawer navigation items/groups.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-496.3` (*Branding Row Centration & Rule 72 Icon Invariant*).
* **Historical Origin & Commit Trace**: Introduced in ATT-5839 (commit `d22358e188150d59fccc929b78015a33d5a365de`).
* **Root Reason for Existing Formulation**: ATT-5839 used `top = statusBarHeight, bottom = 6.dp` with `Alignment.CenterVertically` assuming naive vertical centering would look balanced in the 76dp net height band.
* **Preservation of Core Invariants**: Increasing `bottom` padding to `24.dp` shifts the row into the upper sky area while strictly preserving: (1) `46.dp` launcher icon size and `1.81f` adaptive scaling (`Rule 72`), (2) single-line title layout, and (3) `76.dp + statusBarHeight` overall height bounding (`REQ-UI-496.1`).

---

## 5. Architectural Strategy & High-Level Solution
* In `AppNavigationDrawer.kt`:
  - Update `Row` modifier to `.padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)`.
* In `AppNavigationDrawerContractTest.kt`:
  - Update `testDrawerHeaderBrandingRowAndRule72Invariant_REQ_UI_496_2` to assert `bottom = 24.dp`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in drawer item visibility (*Info & Unterstützung*), `46.dp` icon size, `1.81f` supporter scaling, 100% unit test pass rate.
* **Risk Rating**: **LOW** (Declarative padding adjustment within a single Compose layout; zero state, concurrency, or DB impact).
