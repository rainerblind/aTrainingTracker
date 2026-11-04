# Stage 3: Implementation Plan - ATT-1482: App Icon Modernization

**Ticket**: [ATT-1482](https://rainerblind.atlassian.net/browse/ATT-1482)  
**Sub-task**: [ATT-5347](https://rainerblind.atlassian.net/browse/ATT-5347) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-45.5` | **Risk**: `HIGH` (Rule 71: Pro model for Stage 4)  
**Requirements**: `REQ-UI-444` (`REQ-UI-444.1`–`.4`) | **Tests**: `TST-UI-444` (`TST-UI-444.1`–`.4`)  
**Branch**: `feature/ATT-1482` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Sprint 2026-45.4 Review rejected commit `b404e0d0` (`n.i.O.`) due to:
1. Cyclist back/hip straight polygon kinks and black saddle protrusion artifact.
2. Detached pusher buttons with white gaps and thin stems.
3. Excessive shrinking ($r=30.8\text{dp}$) causing empty border inside launcher masks.
4. Overwritten full-bleed `logo_512.png` corrupting in-app callers (`AppNavigationDrawer.kt`).
5. Missing Pro-tier execution for vector graphics (mandated by Rule 71).

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-444.1` (Adaptive XML Architecture)** $\leftrightarrow$ `TST-UI-444.1`: `ic_launcher.xml` / `ic_launcher_round.xml` in `res/mipmap-anydpi-v26/`.
* **`REQ-UI-444.2` (Spline Geometry & Balanced Safe Zone)** $\leftrightarrow$ `TST-UI-444.2`, `TST-UI-444.3`: Cubic Bézier paths (`C`/`c`) in `ic_launcher_foreground.xml`, royal blue hip (`#0000FF`), flush pushers, scaled to $r \approx 34.0\text{dp}$ ($33.0 \le r \le 34.5\text{dp}$).
* **`REQ-UI-444.3` (Material You Themed Icons)** $\leftrightarrow$ `TST-UI-444.2`, `TST-UI-444.3`: Unified `#000000` silhouette in `ic_launcher_monochrome.xml`.
* **`REQ-UI-444.4` (Full-Bleed Asset & In-App Parity)** $\leftrightarrow$ `TST-UI-444.1`, `TST-UI-444.4`: Restore full-bleed `drawable/logo_512.png` (`sha256: f84c6251...`); separate `images/play_store_icon_512.png`.

---

## 3. System Invariants & Preserved Behavior
1. **Brand Fidelity**: Exact Cervélo P3C frame (`#FF0000`), carbon wheels/saddle (`#000000`), stopwatch bezel/stems (`#FFC700`), dial (`#F0F0F0`), pushers (`#000090`, `#FF0000`, `#009000`), and royal blue athletes (`#0000FF`).
2. **Safe-Zone Bounds**: All vector geometry centered at `(54, 54)` with $33.0 \le r \le 34.5\text{dp}$ (zero mask clipping).
3. **In-App Integrity**: `AppNavigationDrawer.kt` standard icon renders unpadded full-bleed `logo_512.png`; notification `logo.png` untouched.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 — `subagent_pro`)

### Step 1: Mathematical X-Spline to Cubic Bézier Conversion & Geometry Realignment
* Convert `Bilder/logo.fig` objects (depths 70 down to 50) using cubic Bézier curves (`C`/`c`) with round caps (`strokeLineCap="round"`), calibrated against `/home/rainer/Dropbox/workspace/TrainingTracker4/Bilder/logo.png`.
* Preserve royal blue (`#0000FF`) for `logo.fig:65` hip/back curve at depth 50 covering the frame junction, eliminating angular kinks and black saddle protrusion. Place saddle (`#000000`) underneath at depth 53.
* Overlap colored pusher buttons (thickness 50) onto thicker `#FFC700` stems (thickness 30), eliminating white gaps.
* Center geometry at `(54, 54)` and scale to $r \approx 34.0\text{dp}$ ($33.0 \le r \le 34.5\text{dp}$) on the 108dp canvas.

### Step 2: Regenerate Vector Drawables & Master Assets
* Generate `app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`.
* Restore full-bleed `app/src/main/res/drawable/logo_512.png` and `images/logo_512.png` identical to `drawable-xxxhdpi/logo_512.png` (`sha256: f84c6251...`).
* Generate high-res Play Store master asset `images/play_store_icon_512.png` with padded safe-zone geometry.

### Step 3: Visual Screenshot & Side-by-Side Inspection (Rule 71)
* Render a raster preview PNG of reconstructed adaptive icon and visually inspect via `read_file` side-by-side against `drawable-xxxhdpi/logo_512.png`, confirming smooth cyclist back, zero saddle protrusion, and flush pushers.

### Step 4: Update Contract Tests & Verification (`TST-UI-444.1`–`.4`)
* Update `AppIconAdaptiveContractTest.kt` regex parser to support Bézier commands (`C`/`c`/`S`/`s`/`Q`/`q`).
* Assert cubic Bézier presence on cyclist back, absence of black saddle protrusion, safe-zone bounds ($33.0 \le r \le 34.5\text{dp}$), and SHA-256 match for full-bleed `logo_512.png`.
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
* Run `python3 tools/verify_requirement_governance.py`.

---

## 5. Rollback Plan
* Atomic git reset / revert of `feature/ATT-1482`. Zero database schema or runtime state impacts.
