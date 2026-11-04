# Stage 1 Analysis: ATT-1482 - App Icon Modernization: Android Adaptive Icon Support & Safe Zone Parity

**Ticket**: [ATT-1482](https://rainerblind.atlassian.net/browse/ATT-1482)  
**Sub-task**: [ATT-5345](https://rainerblind.atlassian.net/browse/ATT-5345) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-1482`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
The application currently references a legacy static bitmap icon (`android:icon="@drawable/logo"` in `AndroidManifest.xml`). On Android 8.0+ (API 26–16), modern launchers mask icons into circle, squircle, or teardrop shapes:
1. *Launcher Masking & Clipping*: Non-square legacy artwork extends to the outer canvas, causing the runner's outstretched arm and leg to be clipped by launcher masks.
2. *Unintended Background Enclosure*: Launchers place static legacy icons inside an artificial white container plate, degrading home-screen visual quality.
3. *Missing Themed Icon Support*: The app lacks an adaptive `<monochrome>` layer for dynamic Android 13+ (API 33+) Material You wallpaper palette tinting.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  * `AndroidManifest.xml:74` specifies `android:icon="@drawable/logo"` with no `roundIcon` or `@mipmap/ic_launcher` resource.
  * No `res/mipmap-anydpi-v26/` adaptive icon XML exists to decouple background, foreground, and monochrome layers.
  * In `Bilder/logo.fig`, vector geometry spans `X=[3728, 10356]` and `Y=[-1620, 3915]` ($W=6628, H=5535$). Without safe-zone scale mapping, peripheral limbs land outside the central 66% viewport.
* **Independent Peer Review & Verification**:
  * Audited `Bilder/logo.fig`: 30 vector objects across depths 70–50: golden stopwatch ring/stems (`#FFC700`), light dial (`#F0F0F0`), 3 pushers (Navy `#000090`, Red `#FF0000`, Green `#009000`), Cervélo P3C frame (`#FF0000`), black wheels/saddle (`#000000`), and royal blue cyclist & runner (`#0000FF`).
  * Enclosing circle analysis: Center at `(7080, 1140)` with radius `3433.2`. Mapping into the 108dp adaptive canvas safe circle (`cx=54, cy=54, r=31`) with scale factor $S \approx 0.00888$ guarantees 100% containment within the 66dp safe zone.
  * Audited codebase call sites: `R.drawable.logo` is referenced by `TrainingApplication`, `TrackerService`, notification managers, and `Protocol.ALL`, and must remain untouched.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Vectorize `Bilder/logo.fig` into `res/drawable/ic_launcher_foreground.xml` (108dp canvas, centered in 66dp safe zone).
  * Create `res/drawable/ic_launcher_background.xml` (clean neutral background surface `#FFFFFF`).
  * Create `res/drawable/ic_launcher_monochrome.xml` for Android 13+ Material You themed icons.
  * Add `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` referencing foreground, background, and monochrome.
  * Update `AndroidManifest.xml` to `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
  * Generate 512x512 Play Store master icon asset and legacy mipmap density fallbacks (`mdpi`..`xxxhdpi`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT delete or modify existing `res/drawable/logo.png` (retained for in-app notification icons).
  * Zero changes to in-app drawer or cockpit branding layouts.
  * Zero deviation from authentic Cervélo P3C frame and stopwatch colors (100% brand preservation).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Status**: Net-new requirement only (`REQ-UI-444`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
1. **Adaptive Vector Pipeline**: Convert `Bilder/logo.fig` objects (depth 70 down to 50) into `<path>` elements within `ic_launcher_foreground.xml` using safe-zone coordinate translation.
2. **Monochrome Silhouette**: Combine all foreground elements into a single-color silhouette for `ic_launcher_monochrome.xml`.
3. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**: Legacy notification icons and protocol references retain `R.drawable.logo`. Manifest launcher intent points to new `@mipmap/` adaptive resources.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% brand fidelity: exact Cervélo P3C geometry, stopwatch pushers, and athlete colors preserved.
  2. All foreground artwork contained strictly within $r \le 33\text{dp}$ circular safe zone on 108dp canvas.
  3. Zero regressions in notification icons or pre-API 26 device rendering.
* **Risk Rating**: **LOW**. Pure asset and manifest configuration enhancement; zero runtime logic modifications.
