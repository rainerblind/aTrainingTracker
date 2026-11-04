# Stage 2: Requirement & Test Specification - ATT-1482: App Icon Modernization

**Ticket**: [ATT-1482](https://rainerblind.atlassian.net/browse/ATT-1482) | **Sub-task**: [ATT-5346](https://rainerblind.atlassian.net/browse/ATT-5346) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) | **Target Release**: `V5.0.0` | **Sprint**: `2026-45.5`  
**Requirement**: `REQ-UI-444` | **Test Spec**: `TST-UI-444` | **Branch**: `feature/ATT-1482` | **Author**: AI Agent 1 | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-444)

### 1.1 Scope & Technical Requirements
Sprint 2026-45.4 Review rejected commit `b404e0d0` (`n.i.O.`) due to spline/saddle misattribution, detached pusher stems, overly shrunk icon scaling, and overwritten full-bleed assets. REQ-UI-444 establishes the revised specification:
1. **Adaptive Layers (`REQ-UI-444.1`)**: `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` decoupling `<background>` (`#FFFFFF`), `<foreground>`, and `<monochrome>`.
2. **Spline Geometry & Safe-Zone Fill (`REQ-UI-444.2`)**:
   - Reconstruct cyclist back/hip via smooth cubic Bézier curves (`C`/`c`) with round caps in royal blue (`#0000FF`, depth 50) covering the frame junction, eliminating angular kinks and black saddle artifacts.
   - Calibrate stopwatch stems (`#FFC700`, depth 55) to overlap pusher caps (`#000090`, `#FF0000`, `#009000`), eliminating white gaps. Light dial (`#F0F0F0`), Cervélo red frame (`#FF0000`), black wheels/saddle (`#000000`).
   - Center within balanced safe zone ($cx=54, cy=54, r \le 34.5\text{dp}$) on 108dp canvas to generously fill masks without clipping.
3. **Themed Icons (`REQ-UI-444.3`)**: `res/drawable/ic_launcher_monochrome.xml` single-color silhouette (`#000000`) dynamically tinted by Material You on Android 13+.
4. **Asset Preservation & Manifest (`REQ-UI-444.4`)**: Declare `@mipmap/ic_launcher` in `AndroidManifest.xml`. Retain full-bleed `res/drawable/logo_512.png` and `images/logo_512.png` matching `drawable-xxxhdpi/logo_512.png` (`sha256: f84c6251...`). Store padded Play Store master separately (`images/play_store_icon_512.png`). Keep notification `logo.png` untouched.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-444` (Adaptive Icon Architecture, Safe-Zone Radius & Asset Preservation).
* `Historical Origin & Commit Trace`: Commit `b404e0d0` (`ATT-1482`) in Sprint 2026-45.4; returned to Analysis during review due to spline/saddle/pusher/scale regressions.
* `Root Reason for Existing Formulation`: Initial draft used straight polygon approximation (`M...L...`) with butt caps, conservative radius ($r \le 33\text{dp}$), and overwritten `logo_512.png`.
* `Preservation of Core Invariants`: Authentic Cervélo P3C bike colors (`#FF0000`, `#000000`), classic stopwatch colors (`#FFC700`, `#F0F0F0`, `#000090`, `#009000`), royal blue athletes (`#0000FF`), zero mask clipping ($r \le 34.5\text{dp}$), and full-bleed `logo_512.png` preserved.

### 1.3 Acceptance Criteria & Invariants
* **Criterion 1 (Mask Containment & Fill)**: Given any launcher mask (circle, squircle, teardrop), when viewing icon, then artwork fills mask boldly ($r \le 34.5\text{dp}$) with zero clipping.
* **Criterion 2 (Spline & Pusher Parity)**: Cyclist back forms smooth continuous blue curve without black saddle protrusion; pushers connect seamlessly without white gaps.
* **Criterion 3 (Themed & Full-Bleed Parity)**: Themed icon tints cleanly on Android 13+; `AppNavigationDrawer` renders full-bleed `logo_512.png` without padding.
* **Invariants**: 100% brand color tokens preserved; zero regression on in-app notifications.

---

## 2. Test Specification (TST-UI-444)

* **TST-UI-444.1 (Resource & Manifest Contract)**: Validate `ic_launcher.xml` and `ic_launcher_round.xml` in `mipmap-anydpi-v26/` resolving `<background>`, `<foreground>`, and `<monochrome>`, declared in `AndroidManifest.xml`.
* **TST-UI-444.2 (Safe-Zone & Geometry Bounds Audit)**: In `AppIconAdaptiveContractTest.kt`, parse lines (`L`/`l`), arcs (`A`/`a`), and Béziers (`C`/`c`/`S`/`s`/`Q`/`q`). Assert maximum radial distance from `(54, 54)` is $\le 34.5\text{dp}$ and $\ge 33.0\text{dp}$.
* **TST-UI-444.3 (Brand Color & Pusher Flush Audit)**: Assert presence of 7 mandatory brand hex tokens (`#FF0000`, `#000000`, `#FFC700`, `#F0F0F0`, `#000090`, `#009000`, `#0000FF`) in foreground; assert monochrome is `#000000`.
* **TST-UI-444.4 (Master Asset & Governance Parity)**: Assert `drawable/logo_512.png` matches `drawable-xxxhdpi/logo_512.png` sha256; separate `images/play_store_icon_512.png` exists; `verify_requirement_governance.py` exits 0.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-444.1` | Manifest & XML | `AppIconAdaptiveContractTest.testAdaptiveIconResourcesAndManifest` | `REQ-UI-444.1`, `REQ-UI-444.4` | Specified |
| `TST-UI-444.2` | Geometry Bounds | `AppIconAdaptiveContractTest.testSafeZoneContainment` | `REQ-UI-444.2` | Specified |
| `TST-UI-444.3` | Color & Splines | `AppIconAdaptiveContractTest.testBrandColorIntegrity` | `REQ-UI-444.2`, `REQ-UI-444.3` | Specified |
| `TST-UI-444.4` | Assets & Governance | `AppIconAdaptiveContractTest.testInAppCallerParityAndMasterAssets` | `REQ-UI-444.4` | Specified |
