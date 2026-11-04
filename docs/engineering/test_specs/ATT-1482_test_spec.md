# Stage 2: Requirement & Test Specification - ATT-1482: App Icon Modernization

**Ticket**: [ATT-1482](https://rainerblind.atlassian.net/browse/ATT-1482)  
**Sub-task**: [ATT-5346](https://rainerblind.atlassian.net/browse/ATT-5346) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-UI-444` (*Android Adaptive Icon Support, 66dp Safe-Zone Padding & Brand Preservation*)  
**Test Spec ID**: `TST-UI-444`  
**Branch**: `feature/ATT-1482`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-444)

### 1.1 Problem Statement & Rationale
Modern Android launchers automatically mask app icons into round, squircle, or teardrop shapes, clipping unpadded legacy static icons (`@drawable/logo`) and enclosing them in artificial white background plates. The app also lacks dynamic Material You themed launcher icon support on Android 13+.

### 1.2 Functional & Architectural Requirements
1. **Adaptive Icon Layer Architecture (`REQ-UI-444.1`)**: Provide standard adaptive icon XML resources under `res/mipmap-anydpi-v26/` (`ic_launcher.xml`, `ic_launcher_round.xml`) decoupling `<background>` (`ic_launcher_background.xml`, `#FFFFFF`), `<foreground>` (`ic_launcher_foreground.xml`), and `<monochrome>` (`ic_launcher_monochrome.xml`).
2. **Brand Preservation & 66dp Safe Zone (`REQ-UI-444.2`)**: Vectorize `Bilder/logo.fig` geometry (depths 70–50) into `ic_launcher_foreground.xml`, preserving 100% authentic Cervélo P3C time-trial bike (red frame `#FF0000`, carbon wheels/saddle `#000000`), classic stopwatch (golden bezel `#FFC700`, dial `#F0F0F0`, pushers `#000090`, `#FF0000`, `#009000`), and royal blue athletes (`#0000FF`). Scale and center all paths strictly within the 66dp circular safe zone ($cx=54, cy=54, r \le 33\text{dp}$) of the 108dp canvas.
3. **Android 13+ Themed Icons (`REQ-UI-444.3`)**: Define `ic_launcher_monochrome.xml` as a single-color vector silhouette dynamically tinted by system Material You palettes.
4. **Manifest Integration & Backward Compatibility (`REQ-UI-444.4`)**: Update `AndroidManifest.xml` to `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`. Retain `res/drawable/logo.png` untouched for in-app notification drawables. Provide high-resolution 512x512 Play Store master asset.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Mask Containment)**: Given an Android 8.0+ launcher with circle, squircle, or teardrop masking, when viewing the app icon, then zero artwork elements are clipped and no artificial outer container plate is injected.
* **Criterion 2 (Themed Icon Dynamic Tinting)**: Given an Android 13+ device with Themed Icons enabled, when changing system wallpaper/theme, then the launcher icon tints dynamically.
* **Criterion 3 (Notification Parity)**: Given in-app notification services using `R.drawable.logo`, when notifications fire, then existing bitmap drawables render without regression.

### 1.4 System Invariants
* 100% Cervélo P3C frame and classic stopwatch color tokens preserved.
* 100% foreground vector artwork contained inside $r \le 33\text{dp}$ safe zone on 108dp canvas.
* In-app notification icon resolution unchanged.

---

## 2. Test Specification (TST-UI-444)

### Test Case 1: Adaptive Resource & Manifest Contract (`TST-UI-444.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/branding/AppIconAdaptiveContractTest.kt`
* **Procedure**: Validate presence and structural XML tags of `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`, ensuring `<background>`, `<foreground>`, and `<monochrome>` drawables resolve, and `AndroidManifest.xml` points to `@mipmap/ic_launcher`.
* **Expected Result**: All assertions PASS.

### Test Case 2: Safe-Zone Vector Bounds Audit (`TST-UI-444.2`)
* **Target File**: `AppIconAdaptiveContractTest.kt`
* **Procedure**: Parse vector path coordinates in `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`; calculate maximum radial distance from canvas center `(54, 54)`.
* **Expected Result**: Maximum radial distance $\le 33.0\text{dp}$ ($r \le 33$).

### Test Case 3: Brand Color Token Integrity (`TST-UI-444.3`)
* **Procedure**: Parse fill colors in `ic_launcher_foreground.xml` and assert exact hex parity with master tokens: `#FF0000`, `#000000`, `#FFC700`, `#F0F0F0`, `#000090`, `#009000`, `#0000FF`.
* **Expected Result**: Exact 100% token parity; zero unauthorized color substitutions.

### Test Case 4: Governance & Test Suite Verification (`TST-UI-444.4`)
* **Commands**: `python3 tools/verify_requirement_governance.py` & `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.branding.*"`
* **Expected Result**: Zero governance errors and 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-444.1` | Contract | `AppIconAdaptiveContractTest.testAdaptiveIconResourcesAndManifest` | `REQ-UI-444.1`, `REQ-UI-444.4` | Specified |
| `TST-UI-444.2` | Geometry | `AppIconAdaptiveContractTest.testSafeZoneContainment` | `REQ-UI-444.2` | Specified |
| `TST-UI-444.3` | Branding | `AppIconAdaptiveContractTest.testBrandColorIntegrity` | `REQ-UI-444.2`, `REQ-UI-444.3` | Specified |
| `TST-UI-444.4` | Governance | `verify_requirement_governance.py` | `REQ-UI-444.1`–`.4` | Specified |
