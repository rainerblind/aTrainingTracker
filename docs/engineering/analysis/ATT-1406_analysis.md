# Stage 1 Analysis: ATT-1406 - Pro Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem & Share Watermark

**Ticket**: [ATT-1406](https://rainerblind.atlassian.net/browse/ATT-1406)  
**Sub-task**: [ATT-5350](https://rainerblind.atlassian.net/browse/ATT-5350) (`[Analysis]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-1406`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Athletes supporting development via Supporter subscriptions (Bronze, Silver, Gold, Diamond) or recognized as Pioneer Early Adopters (`ATT-51`) require visible in-app and homescreen recognition. Current UI provides only a plain text drawer badge. This feature introduces customizable homescreen launcher icons (Option A: Top-Right Medal/Diamond/Star + Bottom Ribbon), a live Cockpit status emblem in `SensorStatus.kt`, Navigation Drawer branding, and a workout share watermark, while preserving 100% of authentic Cervélo P3C and stopwatch colors (`ATT-1482`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State Gap Analysis**:
  * `AndroidManifest.xml`: Direct `MAIN/LAUNCHER` filter on `MainActivityWithNavigation` prevents dynamic icon selection. Six `<activity-alias>` entries are required.
  * *Android Lifecycle Hazard*: Calling `PackageManager.setComponentEnabledSetting` during active sessions or workouts can trigger OS process restarts. Activation must be deferred to `ProcessLifecycleOwner` `onStop` when `!TrainingApplication.isTracking()`.
  * `SensorStatus.kt`: Left `24dp` menu button is currently unbalanced on the right. Placing a `24dp` vector emblem on the right creates visual symmetry without sacrificing telemetry space.
  * `ShareUtils.kt`: `drawFooter()` renders static text and `logo_512` without supporter recognition.
* **Independent Peer Review & Verification**:
  * *Geometry Audit*: Pocket at `cx=67.6, cy=31.8` has distance $26.03\text{dp} \le 33\text{dp}$ from center; bottom ribbon ($y=74.8..83.0$) fits within $|x| \le 15.7\text{dp}$ safe-zone envelope on 108dp canvas.
  * *Color Invariants*: Verified authentic Cervélo P3C frame (`#FF0000`), carbon wheels (`#000000`), golden bezel (`#FFC700`), and pushers (`#000090`, `#FF0000`, `#009000`) remain untouched.
  * *Debug Tier Override*: `SupporterDebugSection.kt` already supports `SupporterTierOverride` in `AdvancedSettings` (`if (BuildConfig.DEBUG)`), ready for 1-tap testing across all 6 tiers.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * 6 adaptive launcher icon variants (`STANDARD`, `PIONEER`, `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`) with Option A badges inside 66dp safe zone.
  * 6 `<activity-alias>` declarations in `AndroidManifest.xml` targeting `MainActivityWithNavigation`.
  * `LauncherIconManager.kt` applying deferred non-disruptive switches (`DONT_KILL_APP`) on `onStop` when not tracking.
  * `KEY_SELECTED_LAUNCHER_ICON` preference in `SettingsDataStore.kt`.
  * Right-aligned `24dp` vector emblem in `SensorStatus.kt` opening `SupporterQuickIconPickerDialog`.
  * Enhanced `DrawerHeader` with active Cervélo icon + tier pill.
  * Enhanced `combineWorkoutAndShare` watermark in `ShareUtils.kt`.
* **Out-of-Scope Non-Goals**:
  * Modifying Google Play Billing subscription SKUs or pricing rules (`REQ-SET-086`).
  * Changing core sensor telemetry, recording dispatchers, or export pipelines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirement**: `REQ-UI-445` (Supporter Badges, Custom Launcher Icons, Cockpit Status Emblem & Share Watermark).
* **Chesterton's Fence**: Extends `REQ-UI-444` (`ATT-1482`, Adaptive Icon layers) and `REQ-SET-086` (`ATT-51`, Monetization tiers). Core brand invariant strictly preserved: original Cervélo P3C and classic stopwatch pushers remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* **Resources & Manifest**: Add 6 adaptive icon configurations under `res/mipmap-anydpi-v26/` and foreground vectors `ic_launcher_foreground_<tier>.xml`. Declare 6 `<activity-alias>` entries in `AndroidManifest.xml`.
* **Deferred Lifecycle Engine**: Implement `LauncherIconManager` observing `ProcessLifecycleOwner.onStop`. When `!TrainingApplication.isTracking()`, toggles active alias via `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)`.
* **In-App Badges**: Integrate `24dp` vector emblem in `SensorStatus.kt` with `SupporterQuickIconPickerDialog`, update `DrawerHeader` in `AppNavigationDrawer.kt`, and add supporter watermark in `ShareUtils.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% preservation of authentic Cervélo P3C and stopwatch colors (`#FF0000`, `#000000`, `#FFC700`, `#000090`, `#009000`).
  2. Zero disruption to active workout tracking or foreground session.
  3. Strict 66dp circular safe-zone containment ($r \le 33\text{dp}$).
  4. Core workout tracking features remain 100% free and unmonetized forever.
* **Risk Rating**: **LOW** (Additive visual layers and lifecycle-guarded alias activation; decoupled from sensor telemetry).
