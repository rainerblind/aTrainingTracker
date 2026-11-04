# Stage 1 Analysis: ATT-1406 - Pro Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem & Share Watermark

**Ticket**: [ATT-1406](https://rainerblind.atlassian.net/browse/ATT-1406) | **Consolidates**: [ATT-5372](https://rainerblind.atlassian.net/browse/ATT-5372)  
**Sub-task**: [ATT-5350](https://rainerblind.atlassian.net/browse/ATT-5350) (`[Analysis]`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-1406`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Athletes supporting development via Supporter subscriptions (Bronze, Silver, Gold, Diamond) or Pioneer recognition (`ATT-51`) require visible launcher and in-app recognition. In Sprint 2026-45.4 Review, ATT-1406 was rejected on 3 items:
1. All 5 supporter launcher icon foregrounds used broken legacy geometry (`b404e0d0`) instead of the corrected `ATT-1482` base vector.
2. `SupporterQuickIconPickerDialog.kt:170` crashed with `IllegalArgumentException` on opening (`ATT-5372`).
3. Implementation lacked Gemini Pro visual fidelity verification (Rule 71).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **ATT-5372 Crash RCA**:
  * `SupporterQuickIconPickerDialog.kt:170` executes `Image(painter = painterResource(id = variant.iconRes))`.
  * `variant.iconRes` references `<adaptive-icon>` XMLs (`res/mipmap-anydpi-v26/ic_launcher*.xml`).
  * Jetpack Compose `painterResource` natively supports only `<vector>` (`VectorDrawable`) or raster assets; encountering `<adaptive-icon>` triggers `java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`.
  * *Fix*: Follow `AppNavigationDrawer.kt:349` pattern: load `variant.foregroundRes` (or `R.drawable.logo_512` for standard) via `safePainterResource` inside a circular `Surface(shape = CircleShape)`.
* **Base Geometry Discrepancy RCA**:
  * Supporter tier foregrounds (`ic_launcher_foreground_<tier>.xml`) retained polygon cuts and saddle artifacts from obsolete base geometry.
  * *Fix*: Rebuild all 5 tier foregrounds on top of `ATT-1482` base vector (smooth cubic Béziers for royal-blue cyclist hip/back at depth 50, zero saddle protrusion, flush pushers on thick `#FFC700` stems, safe-zone $33.0 \le r \le 34.5\text{dp}$) + Option A badge overlays and border rings.
* **Independent Peer Review & Verification**:
  * Verified `ic_launcher_foreground.xml` from `ATT-1482` commit `dcacacdc` is cleanly merged on `sprint/2026-45.5`.
  * Safe-zone containment validated: Option A top-right emblem (`cx=67.6, cy=31.8`) and bottom ribbon (`y=74.8..83.0`) stay within $r \le 34.5\text{dp}$.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Rebuild 5 supporter foreground vectors on `ATT-1482` base (`pioneer`, `bronze`, `silver`, `gold`, `diamond`).
  * Fix `SupporterQuickIconPickerDialog.kt:170` crash via `safePainterResource` + circular Surface container.
  * Maintain 6 `<activity-alias>` entries and deferred switching via `LauncherIconManager.kt` (`DONT_KILL_APP`).
  * Right-aligned 24dp Cockpit emblem in `SensorStatus.kt`, `DrawerHeader` branding, and `ShareUtils.kt` watermark.
* **Out-of-Scope Non-Goals**:
  * Modifying Google Play Billing subscription SKUs or pricing logic (`REQ-SET-086`).
  * Altering sensor acquisition pipelines, recording dispatchers, or export logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirement**: `REQ-UI-445` (Supporter Badges & Dynamic Icons), consolidating `ATT-5372`.
* **Chesterton's Fence**: Extends `REQ-UI-444` (`ATT-1482`, Adaptive Icon layers) and `REQ-SET-086` (`ATT-51`). Core brand invariants strictly protected: authentic Cervélo P3C frame (`#FF0000`), carbon wheels (`#000000`), golden bezel/stems (`#FFC700`), stopwatch pushers (`#000090`, `#FF0000`, `#009000`), and royal-blue athlete (`#0000FF`) remain 100% untouched.

---

## 5. Architectural Strategy & High-Level Solution
* **Vector Foreground Reconstruction**: Script/generate all 5 tier foreground XMLs using exact `ATT-1482` base paths + tier badge paths + outer ring ($r \le 34.5\text{dp}$).
* **Crash Immunity**: Refactor `LauncherIconItemCard` in `SupporterQuickIconPickerDialog.kt` to render `safePainterResource(id = variant.foregroundRes)` in a styled `Surface(shape = CircleShape)`.
* **Lifecycle & Side-Effects (Rule 68)**: `LauncherIconManager` defers `setComponentEnabledSetting` with `DONT_KILL_APP` to `ProcessLifecycleOwner.onStop` when `!TrainingApplication.isTracking()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% preservation of authentic Cervélo P3C and stopwatch colors and geometry.
  2. Zero interruption to active workouts or foreground session.
  3. Safe-zone containment strictly enforced ($33.0 \le r \le 34.5\text{dp}$).
  4. Core workout tracking remains 100% free and unmonetized.
* **Risk Rating**: **HIGH** (Rule 71 mandatory: complex vector graphics synthesis and critical UI entry-point crash fix require Gemini Pro model execution and visual inspection of rendered icon PNGs and on-device dialog).
