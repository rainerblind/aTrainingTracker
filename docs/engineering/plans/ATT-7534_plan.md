# Stage 3: Implementation Plan - ATT-7534: Official Google Drive and Health Connect Brand Vectors

**Ticket**: [ATT-7534](https://rainerblind.atlassian.net/browse/ATT-7534)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-524` (*Official Google Drive and Health Connect Brand Vector Fidelity*)  
**Test Mapping**: `TST-UI-524` (`TST-UI-524.1`, `TST-UI-524.2`, `TST-UI-524.3`)  
**Branch**: `feature/ATT-7534` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
`ic_google_drive.xml` and `ic_health_connect.xml` currently contain approximate polygons and an invented snowman-like shape that violate brand guidelines. This plan replaces both drawables with authentic official vectors while guaranteeing contrast across Light and Dark themes.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-524` (*Official Google Drive and Health Connect Brand Vector Fidelity*)
  - `REQ-UI-524.1`: Official 6-segment Google Drive 2020+ vector geometry.
  - `REQ-UI-524.2`: Official Android Health Connect interlocking dual-loop geometry with theme contrast.
* **Test Mapping**: `TST-UI-524`
  - `TST-UI-524.1`: Unit test verifying Google Drive path count (6) and brand palette in `BrandIconContractTest.kt`.
  - `TST-UI-524.2`: Unit test verifying Health Connect interlocking loops and colors in `BrandIconContractTest.kt`.
  - `TST-UI-524.3`: AAPT2 compilation and navigation regression suite.

---

## 3. System Invariants & Preserved Behavior
1. **Resource Stability**: `R.drawable.ic_google_drive` and `R.drawable.ic_health_connect` IDs remain stable.
2. **Brand Rendering**: `tintIcon = false` in `AppNavigationDrawer.kt` and `Color.Unspecified` in Compose screens preserved.
3. **Logic Isolation**: Zero modifications to Google Drive OAuth, SAF document picking, or Health Connect synchronization engines.

---

## 4. Proposed Architectural Changes
* **Drawable Assets Layer (`res/drawable/`)**:
  - `res/drawable/ic_google_drive.xml`: 6 official paths (`#0066DA`, `#00AC47`, `#EA4335`, `#00832D`, `#2684FC`, `#FFBA00`) centered in square 87.3x87.3 viewport.
  - `res/drawable/ic_health_connect.xml`: Authentic Google dual loops (`#003144`, `#4285F4`) and upper-right circular node with white interior contrast fill.
* **UI Consistency (`docs/design_guidelines.md`)**:
  - Standard 24dp vector bounding box matching Strava and Dropbox brand icons in the Navigation Drawer.
  - Authentic multi-color palettes rendered untinted (`tintIcon = false`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Replace `ic_google_drive.xml`
* **File**: `app/src/main/res/drawable/ic_google_drive.xml`
* **Changes**: Replace 3 legacy polygon paths with official 6-segment Google Drive vector geometry.

### Step 2: Replace `ic_health_connect.xml`
* **File**: `app/src/main/res/drawable/ic_health_connect.xml`
* **Changes**: Replace unofficial heart/circle paths with authentic interlocking loops, upper-right dot, and contrast fills.

### Step 3: Implement `BrandIconContractTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/BrandIconContractTest.kt`
* **Changes**: Add DOM-based contract assertions verifying paths, viewport dimensions, and hex color tokens.

### Step 4: Run Targeted Tests & Resource Compilation
* **Command**: `./gradlew processDebugResources && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.BrandIconContractTest" --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: AAPT2 resource parsing (`processDebugResources`) and `BrandIconContractTest` execution under build lock.
* **Rollback**: Branch isolation (`feature/ATT-7534`) allows complete revert via `git checkout sprint/2026-46.5`.
