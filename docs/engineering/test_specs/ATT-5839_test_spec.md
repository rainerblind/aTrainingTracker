# Stage 2: Requirement & Test Specification - ATT-5839: AppNavigationDrawer (DrawerHeader): Reduce header height by ~16dp (trim top sky) so 'Info & Unterstützung' fits without bottom clipping

**Ticket**: [ATT-5839](https://rainerblind.atlassian.net/browse/ATT-5839)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5839`  
**Requirement Mapping**: `REQ-UI-496` (`REQ-UI-496.1`–`REQ-UI-496.3`) | **Test Spec ID**: `TST-UI-496` (`TST-UI-496.1`–`TST-UI-496.3`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-UI-445.4` (*Navigation Drawer Header Recognition & 9-Locale Tier Naming*) and `REQ-UI-159` (*Navigation Drawer*).
* `Historical Origin & Commit Trace`: Introduced in `ATT-1083` (legacy drawer header) and updated in `ATT-5371` / `ATT-5397` / `ATT-5496` (Cervélo P3C launcher icon integration).
* `Root Reason for Existing Formulation`: `92.dp + statusBarHeight` was an initial sizing placeholder before later sprint tickets added everyday drawer groups (`Info & Unterstützung`, Supporter / Pioneer).
* `Preservation of Core Invariants`: `Alignment.BottomCenter` background anchoring, `ContentScale.FillWidth`, single-line branding layout, `46.dp` icon size with `1.81f` adaptive icon safe-zone scaling (Rule 72), and dynamic status bar inset handling MUST remain strictly preserved.

---

## 1. Requirement Specification (`REQ-UI-496`)

### 1.1 Problem Statement & Rationale
Visual UI audits (`audit_02_drawer.png`) revealed that the bottom-most navigation entry `Info & Unterstützung` (`drawer__info_support`) is clipped at the bottom display boundary on standard portrait screens, forcing users to scroll the drawer to access support and privacy links. Meanwhile, `DrawerHeader()` allocates `92.dp + statusBarHeight`, where an expansive ~40% band of plain blue sky sits unoccupied above the app branding.

### 1.2 Functional & Architectural Requirements
1. **Compact Header Height Bounding (`REQ-UI-496.1`)**: In `AppNavigationDrawer.kt`, `DrawerHeader()` SHALL specify a total height of `76.dp + statusBarHeight` (reduced by 16dp from `92.dp + statusBarHeight`).
2. **Bottom Landscape Motif Preservation (`REQ-UI-496.2`)**: Background image `R.drawable.menu_header_background` SHALL remain anchored at `Alignment.BottomCenter` with `ContentScale.FillWidth`, preserving the meadow, trees, hills, and road landscape motif at the bottom edge while trimming only top sky.
3. **Branding Row Centration & Rule 72 Invariant (`REQ-UI-496.3`)**: The branding row SHALL apply `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 6.dp)` with `verticalAlignment = Alignment.CenterVertically`. The launcher icon slot SHALL preserve its `46.dp` size and `1.81f` adaptive icon scale factor without layout distortion.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Header Height & Vertical Clearance)**: *Given* the Navigation Drawer on a standard portrait mobile screen, *When* opened, *Then* `DrawerHeader()` renders with height `76.dp + statusBarHeight`, and all navigation groups down to and including `Info & Unterstützung` fit vertically without bottom clipping.
* **AC-2 (Landscape Artwork Preservation)**: *Given* `DrawerHeader()`, *When* rendered, *Then* `Alignment.BottomCenter` keeps 100% of the horizon landscape artwork visible at the bottom edge.
* **AC-3 (Icon Scaling Invariant)**: *Given* any supporter tier launcher icon, *When* rendered in `DrawerHeader()`, *Then* the icon retains `46.dp` size and `1.81f` scaling.

---

## 2. Test Specification (`TST-UI-496`) & Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-496.1` | Header Height & Alignment Contract | `AppNavigationDrawerContractTest.kt` | `REQ-UI-496.1`, `REQ-UI-496.2` | Specified |
| `TST-UI-496.2` | Rule 72 Icon Layout Invariant | `AppNavigationDrawerContractTest.kt` | `REQ-UI-496.3` | Specified |
| `TST-UI-496.3` | Tier 1 Targeted Regression | `ui.navigation.*`, `ThemeColorCentralizationAuditTest` | `REQ-PRO-001` | Specified |
