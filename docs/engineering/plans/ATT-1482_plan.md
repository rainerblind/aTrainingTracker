# Stage 3: Implementation Plan - ATT-1482: App Icon Modernization

**Ticket**: [ATT-1482](https://rainerblind.atlassian.net/browse/ATT-1482)  
**Sub-task**: [ATT-5347](https://rainerblind.atlassian.net/browse/ATT-5347) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-UI-444` (`REQ-UI-444.1`–`.4`)  
**Test Mapping**: `TST-UI-444` (`TST-UI-444.1`–`.4`)  
**Branch**: `feature/ATT-1482`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
`AndroidManifest.xml` currently references legacy static bitmap `@drawable/logo`. Modern Android launchers (API 26+) mask icons into circle, squircle, or teardrop shapes, clipping unpadded peripheral elements (runner's limbs) and forcing artificial white container plates. On Android 13+, the app lacks dynamic Material You themed icon tinting.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-444.1` (Adaptive XML Architecture)** $\leftrightarrow$ `TST-UI-444.1`: Adaptive icon XML in `res/mipmap-anydpi-v26/` (`ic_launcher.xml`, `ic_launcher_round.xml`).
* **`REQ-UI-444.2` (Brand & 66dp Safe Zone)** $\leftrightarrow$ `TST-UI-444.2`, `TST-UI-444.3`: 108dp `ic_launcher_foreground.xml` vectorized from `Bilder/logo.fig` centered within $r \le 33\text{dp}$.
* **`REQ-UI-444.3` (Material You Themed Icons)** $\leftrightarrow$ `TST-UI-444.2`, `TST-UI-444.3`: Single-color `ic_launcher_monochrome.xml` silhouette.
* **`REQ-UI-444.4` (Manifest & Backward Compatibility)** $\leftrightarrow$ `TST-UI-444.1`, `TST-UI-444.4`: Manifest integration, 512x512 master asset, and notification icon preservation.

---

## 3. System Invariants & Preserved Behavior
1. **Brand Fidelity**: Exact Cervélo P3C frame (`#FF0000`), carbon wheels/saddle (`#000000`), stopwatch bezel (`#FFC700`), dial (`#F0F0F0`), pushers (`#000090`, `#FF0000`, `#009000`), and athletes (`#0000FF`) preserved.
2. **Safe-Zone Containment**: All foreground vector paths strictly enclosed within $r \le 33\text{dp}$ ($cx=54, cy=54$).
3. **Notification Stability**: Existing `R.drawable.logo` untouched for in-app notification managers and `Protocol.ALL`.

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference component: Master vector artwork `Bilder/logo.fig` and legacy `res/drawable/logo.png`.
  * Reused layers: Standard Android Adaptive Icon XML (`<adaptive-icon>`, `<background>`, `<foreground>`, `<monochrome>`).
  * Theme tokens: Authentic master hex tokens (`#FF0000`, `#000000`, `#FFC700`, `#F0F0F0`, `#000090`, `#009000`, `#0000FF`, background `#FFFFFF`).
  * New one-off styles: None (100% brand heritage preservation).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Vectorize `Bilder/logo.fig` to VectorDrawables
* Generate `app/src/main/res/drawable/ic_launcher_foreground.xml` (108dp canvas, $cx=54, cy=54, r \le 33\text{dp}$, depths 70–50).
* Generate `app/src/main/res/drawable/ic_launcher_background.xml` (`#FFFFFF`).
* Generate `app/src/main/res/drawable/ic_launcher_monochrome.xml` (unified silhouette for Material You tinting).

### Step 2: Adaptive Icon Mipmap Configurations
* Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.
* Populate legacy bitmap fallbacks (`mipmap-mdpi` through `mipmap-xxxhdpi`) and 512x512 Play Store master icon.

### Step 3: Manifest Integration
* Update `app/src/main/AndroidManifest.xml`: set `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.

### Step 4: Automated Contract Tests (`TST-UI-444.1`–`.3`)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/branding/AppIconAdaptiveContractTest.kt` verifying manifest references, XML structure, safe-zone radius $r \le 33\text{dp}$, and brand color tokens.

### Step 5: Verification & Governance
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
* Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Automated contract tests, AAPT2 resource compilation, and physical device launcher inspection.
* **Rollback**: Clean git revert of `feature/ATT-1482`; zero database schema or runtime state impacts.
