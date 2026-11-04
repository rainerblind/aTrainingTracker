# Stage 2: Requirement & Test Specification - ATT-1406: Pro Supporter Badges

**Ticket**: [ATT-1406](https://rainerblind.atlassian.net/browse/ATT-1406)  
**Sub-task**: [ATT-5351](https://rainerblind.atlassian.net/browse/ATT-5351) (`[Req & Test Spec]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-1406`  
**Requirement Mapping**: `REQ-UI-445` (*Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem, Navigation Drawer & Share Watermark*)  
**Test Spec ID**: `TST-UI-445`, `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-445)

### 1.1 Problem Statement & Rationale
Athletes supporting development via Supporter subscriptions or recognized as Pioneer Early Adopters (`ATT-51`) require visible homescreen and in-app recognition. This specification introduces 6 customizable homescreen launcher icons (Option A), a live Cockpit status emblem, Navigation Drawer branding, and a workout share watermark, while preserving 100% of authentic Cervélo P3C and classic stopwatch colors (`ATT-1482`).

### 1.2 Functional & Architectural Requirements
1. **Option A Adaptive Launcher Icons (`REQ-UI-445.1`)**: Provide 6 adaptive launcher icon variants (`STANDARD`, `PIONEER`, `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`) under `res/mipmap-anydpi-v26/` and `res/drawable/`. Each supporter variant combines the base Cervélo P3C and stopwatch artwork with a Top-Right Emblem pocket (`cx=67.6, cy=31.8`) and Bottom Tier Ribbon (`y=74.8..83.0`), strictly contained within the 66dp circular safe zone ($r \le 33\text{dp}$).
2. **Activity-Alias Manifest & Deferred Switching (`REQ-UI-445.2`)**: Declare 6 `<activity-alias>` entries in `AndroidManifest.xml` targeting `MainActivityWithNavigation`. Implement `LauncherIconManager` to apply `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)` deferred to `ProcessLifecycleOwner.onStop` when `!TrainingApplication.isTracking()`.
3. **Live Cockpit Status Emblem & Quick-Picker (`REQ-UI-445.3`)**: Render a 24dp vector emblem at the right end of `SensorStatus.kt` balancing the left hamburger menu button. Tapping opens `SupporterQuickIconPickerDialog` for 1-tap launcher icon switching.
4. **Navigation Drawer Header Branding (`REQ-UI-445.4`)**: In `AppNavigationDrawer.DrawerHeader`, display the active Cervélo P3C icon (46dp) and supporter tier pill beneath the application title.
5. **Workout Share Watermark (`REQ-UI-445.5`)**: In `ShareUtils.kt`, include a branded signature bar with mini Cervélo P3C icon, "Tracked with aTrainingTracker", and user's supporter tier pill.
6. **Tier Unlock Hierarchy & Debug Override (`REQ-UI-445.6`)**: Higher tiers unlock all lower-tier icons. Developer override selector in `AdvancedSettings` (`if (BuildConfig.DEBUG)`) allows 1-tap testing across all 6 tiers.

### 1.3 System Invariants & Acceptance Criteria
* **Invariants**: 100% preservation of authentic Cervélo P3C (`#FF0000`/`#000000`) and stopwatch (`#FFC700`/`#F0F0F0`/`#000090`/`#009000`) colors; 100% geometry containment inside $r \le 33\text{dp}$ safe zone; zero workout/session interruptions on icon switch.
* **Given-When-Then**: Given an athlete selecting an unlocked launcher icon, when chosen, then selection persists immediately in DataStore without interrupting active foreground sessions, and alias activates via `DONT_KILL_APP` on background transition.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-444` (`ATT-1482`, Adaptive Icon Layers) and `REQ-SET-086` (`ATT-51`, Supporter Tiers).
* `Historical Origin & Commit Trace`: Commit `b404e0d0` (`ATT-1482`) introduced adaptive vector layers; commit `c899cda1` (`ATT-51`) introduced monetization tiers.
* `Root Reason for Existing Formulation`: `REQ-UI-444` established 66dp safe-zone boundary rules and authentic Cervélo P3C brand preservation.
* `Preservation of Core Invariants`: Option A overlays occupy unused safe-zone pockets (`cx=67.6, cy=31.8` and `y=74.8..83.0`), keeping base bike and stopwatch vectors 100% intact and free tracking 100% unmonetized.

---

## 3. Test Specification (TST-UI-445 & TST-LOC-001)

### Test Case 1: Adaptive Launcher Icon Safe-Zone & Brand Color Contract (`TST-UI-445.1`)
* **Procedure**: Parse all 6 foreground vector drawables; verify max radial distance from `(54, 54)` $\le 33\text{dp}$; verify exact color tokens: `#FF0000`, `#000000`, `#FFC700`, `#F0F0F0`, `#000090`, `#009000`, `#0000FF`.
* **Expected Result**: 100% safe-zone containment and zero color substitution errors.

### Test Case 2: Activity-Alias Manifest & Deferred Lifecycle Switching (`TST-UI-445.2`)
* **Procedure**: Assert 6 `<activity-alias>` declarations in manifest; test `LauncherIconManager` deferral logic with `PackageManager.DONT_KILL_APP` during active foreground/tracking.
* **Expected Result**: Alias switch deferred until `onStop` when not tracking; zero session kills.

### Test Case 3: Live Cockpit Emblem & Quick-Picker Contract (`TST-UI-445.3`)
* **Procedure**: Validate `SensorStatus.kt` layout symmetry (24dp menu button vs 24dp emblem); test quick-picker dialog launch.
* **Expected Result**: Right-aligned emblem renders; dialog opens with unlocked tiers.

### Test Case 4: Drawer Header & Share Watermark Contract (`TST-UI-445.4`)
* **Procedure**: Assert `DrawerHeader` and `ShareUtils.drawFooter` render active Cervélo icon and supporter tier pill.
* **Expected Result**: Correct badge icons and tier pills rendered across both surfaces.

### Test Case 5: 9-Locale Localization Parity (`TST-LOC-001` / `TST-UI-445.5`)
* **Procedure**: Execute `TranslationParityTest` for all newly introduced supporter and launcher icon strings across all 9 supported locales.
* **Expected Result**: 100% parity across all 9 locales; zero missing keys or format errors.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-445.1` | Geometry/Branding | `SupporterLauncherIconContractTest.testSafeZoneAndBrandColors` | `REQ-UI-445.1` | Planned |
| `TST-UI-445.2` | Lifecycle/Manifest | `LauncherIconManagerTest.testDeferredActivation` | `REQ-UI-445.2`, `REQ-UI-445.6` | Planned |
| `TST-UI-445.3` | UI Cockpit | `SensorStatusContractTest.testEmblemAndQuickPicker` | `REQ-UI-445.3` | Planned |
| `TST-UI-445.4` | UI Drawer/Share | `DrawerHeaderTest`, `ShareUtilsContractTest` | `REQ-UI-445.4`, `REQ-UI-445.5` | Planned |
| `TST-LOC-001` | Localization | `TranslationParityTest` | `REQ-UI-445` (9 locales) | Planned |
