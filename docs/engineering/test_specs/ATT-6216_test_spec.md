# Stage 2: Requirement & Test Specification - ATT-6216: Position DrawerHeader branding in sky area

**Ticket**: [ATT-6216](https://rainerblind.atlassian.net/browse/ATT-6216)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-UI-496` (*Navigation Drawer Header Height Compactness & Vertical Clearance Guard*)  
**Test Spec ID**: `TST-UI-496`  
**Branch**: `feature/ATT-6216`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-496)

### 1.1 Problem Statement & Rationale
In Sprint Review 2026-45.12 (`ATT-5839`), reducing `DrawerHeader` height eliminated bottom clipping of *Info & Unterstützung*. However, placing the branding row with `bottom = 6.dp` positioned the app icon and title directly on the dark forest treeline. Elevating the branding row into the upper sky area (`bottom = 24.dp`) guarantees optimal text contrast against the light sky while keeping the treeline and meadow clear below.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-496.1** (*Compact Header Height Bounding*): `DrawerHeader()` SHALL specify total height `76.dp + statusBarHeight`.
* **REQ-UI-496.2** (*Bottom Landscape Motif Preservation*): Background image `menu_header_background` SHALL remain anchored at `Alignment.BottomCenter` with `ContentScale.FillWidth`.
* **REQ-UI-496.3** (*Branding Row Sky Placement & Rule 72 Icon Invariant*):
  - In `AppNavigationDrawer.kt`, the branding row SHALL apply `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)` with `verticalAlignment = Alignment.CenterVertically`.
  - The Cervélo P3C launcher icon slot SHALL preserve its `46.dp` size and `1.81f` adaptive icon safe-zone scale factor per Rule 72 (`REQ-UI-445.4`), remaining single-line alongside the title without clipping.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Sky Placement & Contrast)**:
  - *Given* the Navigation Drawer opened in `AppNavigationDrawer`,
  - *When* `DrawerHeader` renders the branding row,
  - *Then* launcher icon and title sit vertically in the open sky area above the dark forest treeline with `bottom = 24.dp`, providing clear contrast against the light sky.
* **Criterion 2 (Header Height & Vertical Clearance)**:
  - *Given* the Navigation Drawer on a standard portrait mobile screen,
  - *When* the drawer is opened,
  - *Then* `DrawerHeader` renders with net height `76.dp`, and *Info & Unterstützung* remains fully visible without bottom vertical clipping.
* **Criterion 3 (Rule 72 Icon & Alignment Invariant)**:
  - *Given* any supporter tier icon in `DrawerHeader`,
  - *When* rendered,
  - *Then* the launcher icon retains `46.dp` size and `1.81f` scaling, and the background remains anchored at `Alignment.BottomCenter`.

### 1.4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-496.3` (*Branding Row Sky Placement & Rule 72 Icon Invariant*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-1083` (legacy header) $\rightarrow$ `ATT-5371`/`ATT-5397` (Rule 72 launcher branding) $\rightarrow$ `ATT-5839` (height reduction to 76dp, commit `d22358e188150d59fccc929b78015a33d5a365de`) $\rightarrow$ `ATT-6216`.
* **Root Reason for Existing Formulation**: `ATT-5839` used `top = statusBarHeight, bottom = 6.dp` with `Alignment.CenterVertically` assuming naive vertical centering would look balanced in the 76dp net height band, but unintentionally pushed the row down into the dark treeline.
* **Preservation of Core Invariants**: `Alignment.BottomCenter` landscape anchoring, single-line branding layout, `46.dp` icon size, `1.81f` safe-zone scaling, `76.dp + statusBarHeight` overall height bounding (preserving *Info & Unterstützung* clearance), dynamic status bar handling, and 100% test pass rate strictly preserved.

---

## 2. Test Specification (TST-UI-496)

### Test Case 1: Branding Row Sky Placement & Rule 72 Invariant Contract Test (`TST-UI-496.2`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerContractTest.kt`
* **Method**: `testDrawerHeaderBrandingRowAndRule72Invariant_REQ_UI_496_2()`
* **Assertion**: Verify source code in `AppNavigationDrawer.kt` declares `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)` and retains `Modifier.size(46.dp)` with `scaleX = 1.81f; scaleY = 1.81f`.

### Test Case 2: Drawer Header Height & Alignment Contract Test (`TST-UI-496.1`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerContractTest.kt`
* **Method**: `testDrawerHeaderHeightAndAlignmentContract_REQ_UI_496_1()`
* **Assertion**: Verify `.height(76.dp + statusBarHeight)`, `Alignment.BottomCenter`, and `ContentScale.FillWidth`.

### Test Case 3: Tier 1 Targeted Navigation Regression Suite (`TST-UI-496.3`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate across navigation package and theme audit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Test Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-496.1` | Contract | `AppNavigationDrawerContractTest.testDrawerHeaderHeightAndAlignmentContract_REQ_UI_496_1` | `REQ-UI-496.1`, `REQ-UI-496.2` | Specified |
| `TST-UI-496.2` | Contract | `AppNavigationDrawerContractTest.testDrawerHeaderBrandingRowAndRule72Invariant_REQ_UI_496_2` | `REQ-UI-496.3`, `REQ-UI-445.4` | Specified |
| `TST-UI-496.3` | Regression | `ui.navigation.*`, `ThemeColorCentralizationAuditTest`, `build.*` | `REQ-PRO-001` | Specified |
