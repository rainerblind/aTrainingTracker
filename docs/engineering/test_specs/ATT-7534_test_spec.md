# Stage 2: Requirement & Test Specification - ATT-7534: Official Google Drive and Health Connect Brand Vectors

**Ticket**: [ATT-7534](https://rainerblind.atlassian.net/browse/ATT-7534)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-524` (*Official Google Drive and Health Connect Brand Vector Fidelity*)  
**Test Spec ID**: `TST-UI-524` (`TST-UI-524.1`, `TST-UI-524.2`, `TST-UI-524.3`) | **Branch**: `feature/ATT-7534`  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-524)

### 1.1 Problem Statement & Rationale
In `AppNavigationDrawer.kt` (*Synchronisation*) and backup/health dialogs, `ic_google_drive.xml` contains disconnected polygons with a misaligned base, while `ic_health_connect.xml` renders an unofficial 4-color snowman-like shape. Replacing these drawables with authentic official vectors restores brand visual fidelity while ensuring high contrast across Light and Dark themes.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `app/src/main/res/drawable/ic_google_drive.xml` SHALL render the official 2020+ Google Drive triangle vector consisting of 6 distinct color paths (`#0066DA`, `#00AC47`, `#EA4335`, `#00832D`, `#2684FC`, `#FFBA00`), centered in a square 87.3x87.3 viewport (`translateY="4.65"`), eliminating polygonal gaps and unaligned segments.
2. `app/src/main/res/drawable/ic_health_connect.xml` SHALL render the official Google Health Connect dual interlocking loop brand geometry: left loop in Dark Teal (`#003144`), right loop in Google Blue (`#4285F4`), and upper-right circular node in Google Blue (`#4285F4`).
3. In both Light and Dark themes, `ic_health_connect.xml` SHALL maintain clear contrast and legibility without disappearing on dark backgrounds.
4. Both icons SHALL retain `tintIcon = false` in `AppNavigationDrawer.kt` and `Color.Unspecified` in Compose screens, displaying authentic multi-color brand palettes.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (Official Google Drive Brand Vector)**:
  * *Given* `res/drawable/ic_google_drive.xml` rendered in Navigation Drawer and Import/Backup screens,
  * *When* displayed,
  * *Then* it renders the official 6-segment Google Drive vector with horizontal blue base and continuous segment boundaries.
* **AC2 (Official Android Health Connect Brand Vector)**:
  * *Given* `res/drawable/ic_health_connect.xml` rendered in Navigation Drawer and Health Connect dialog,
  * *When* displayed in either Light or Dark mode,
  * *Then* it renders the official interlocking dual-loop heart with upper-right blue dot with high contrast.

### 1.5 System Invariants
- Resource IDs `R.drawable.ic_google_drive` and `R.drawable.ic_health_connect` remain stable.
- Zero changes to OAuth, SAF file picking, or Health Connect API logic.

---

## 2. Test Specification (TST-UI-524)

### Test Case 1: Google Drive Vector Geometry & Color Verification (`TST-UI-524.1`)
* **Scope**: Unit Test (`BrandIconContractTest.kt#testGoogleDriveVectorGeometryAndColors`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/BrandIconContractTest.kt`
* **Precondition**: `ic_google_drive.xml` present in `res/drawable/`.
* **Action**: Parse XML DOM; extract `<path>` fill colors and count.
* **Expected Result**: Exactly 6 paths matching official hex colors (`#0066DA`, `#00AC47`, `#EA4335`, `#00832D`, `#2684FC`, `#FFBA00`) and square 87.3x87.3 viewport.

### Test Case 2: Health Connect Vector Geometry & Color Verification (`TST-UI-524.2`)
* **Scope**: Unit Test (`BrandIconContractTest.kt#testHealthConnectVectorGeometryAndColors`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/BrandIconContractTest.kt`
* **Precondition**: `ic_health_connect.xml` present in `res/drawable/`.
* **Action**: Parse XML DOM; verify 2 interlocking loop paths (`fillType="evenOdd"`) and circular node.
* **Expected Result**: Contains `#003144` and `#4285F4`; zero red/yellow circular snowman nodes.

### Test Case 3: Tier 1 Resource Compilation & Navigation Suite (`TST-UI-524.3`)
* **Command**: `./gradlew processDebugResources && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`
* **Expected Result**: 100% PASS across resource compilation and navigation drawer tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-524.1` | Unit | `BrandIconContractTest.kt` | `REQ-UI-524.1` | Specified |
| `TST-UI-524.2` | Unit | `BrandIconContractTest.kt` | `REQ-UI-524.2` | Specified |
| `TST-UI-524.3` | Tier 1 Regr | `ui.navigation.*` | `REQ-PRO-001` | Specified |
