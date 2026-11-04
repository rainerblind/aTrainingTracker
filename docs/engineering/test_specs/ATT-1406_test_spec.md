# Stage 2: Requirement & Test Specification - ATT-1406: Pro Supporter Badges

**Ticket**: [ATT-1406](https://rainerblind.atlassian.net/browse/ATT-1406) | **Consolidates**: [ATT-5372](https://rainerblind.atlassian.net/browse/ATT-5372)  
**Sub-task**: [ATT-5351](https://rainerblind.atlassian.net/browse/ATT-5351) (`[Req & Test Spec]`) | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-45.5` | **Requirement**: `REQ-UI-445` | **Test Spec**: `TST-UI-445`  
**Branch**: `feature/ATT-1406` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-445)

### 1.1 Scope & Technical Requirements
Athletes supporting development via Supporter subscriptions or recognized as Pioneer Early Adopters (`ATT-51`) require visible launcher and in-app recognition. Sprint 2026-45.4 Review rejected ATT-1406 due to legacy base geometry and the `ATT-5372` icon picker crash. REQ-UI-445 establishes:
1. **Option A Supporter Adaptive Icons (`REQ-UI-445.1`)**: Rebuild all 5 supporter foreground vectors (`_pioneer`, `_bronze`, `_silver`, `_gold`, `_diamond`) on the corrected `ATT-1482` base: smooth cubic Bézier curves (`C`/`c`) with round caps for royal blue (`#0000FF`) cyclist hip/back at depth 50, flush pushers on thick `#FFC700` stems, safe zone $33.0 \le r \le 34.5\text{dp}$ on 108dp canvas, combined with Option A Top-Right Emblem (`cx=67.6, cy=31.8`) and Bottom Tier Ribbon (`y=74.8..83.0`).
2. **Activity-Alias Manifest Architecture (`REQ-UI-445.2`)**: Declare 6 `<activity-alias>` entries in `AndroidManifest.xml`. `LauncherIconManager` defers `setComponentEnabledSetting(..., DONT_KILL_APP)` to `ProcessLifecycleOwner.onStop` when `!TrainingApplication.isTracking()`.
3. **Live Cockpit Emblem & Crash-Free Quick-Picker (`REQ-UI-445.3` / `ATT-5372`)**: Render 24dp vector emblem in `SensorStatus.kt`. In `SupporterQuickIconPickerDialog.kt` (`LauncherIconItemCard`), render previews using `safePainterResource(id = variant.foregroundRes)` inside `Surface(shape = CircleShape)` and NEVER pass `<adaptive-icon>` `R.mipmap.*` XMLs to `painterResource` (`Rule 71`).
4. **Drawer Header & Share Watermark (`REQ-UI-445.4`, `.5`)**: Active Cervélo icon (46dp) and tier pill in `AppNavigationDrawer`; branded signature footer with tier pill in `ShareUtils.kt`.
5. **Tier Unlock Hierarchy & Debug Override (`REQ-UI-445.6`)**: Higher tiers unlock lower tier icons; developer tier selector in `AdvancedSettings` when `BuildConfig.DEBUG == true`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-445` (Supporter Badges & Customizable Launcher Icons).
* `Historical Origin & Commit Trace`: Commit `b404e0d0` (`ATT-1482`) and `c899cda1` (`ATT-51`); refined in Sprint 2026-45.5 to fix ATT-5372 crash and synchronize with corrected ATT-1482 base vector.
* `Root Reason for Existing Formulation`: Supporter badges previously used outdated polygon base geometry and passed mipmap XMLs directly to Compose `painterResource`, throwing `IllegalArgumentException`.
* `Preservation of Core Invariants`: 100% preservation of authentic Cervélo P3C (`#FF0000`/`#000000`) and stopwatch (`#FFC700`/`#F0F0F0`/`#000090`/`#009000`) colors; zero workout recording interruptions; core workout tracking remains 100% free.

### 1.3 Acceptance Criteria & Invariants
* **Criterion 1 (Option A Geometry & Bounds)**: All 6 launcher icon foregrounds incorporate smooth cubic Bézier curves, flush pushers, and fit within $33.0 \le r \le 34.5\text{dp}$.
* **Criterion 2 (Crash Immunity - ATT-5372)**: Opening `SupporterQuickIconPickerDialog` renders all tier preview cards via `variant.foregroundRes` without `IllegalArgumentException`.
* **Criterion 3 (Deferred Activation)**: Selecting a tier persists immediately and switches alias on backgrounding without killing active workouts.
* **Invariants**: Authentic Cervélo P3C and stopwatch colors preserved; core workout tracking remains free.

---

## 2. Test Specification (TST-UI-445 & TST-LOC-001)

* **TST-UI-445.1 (Adaptive Launcher Safe-Zone & Bézier Contract)**: In `SupporterBadgeContractTest.kt`, parse path coordinates for all 6 foregrounds; assert cubic Bézier presence (`C`/`c`), flush pushers, and $33.0 \le r \le 34.5\text{dp}$.
* **TST-UI-445.2 (Activity-Alias Manifest & Lifecycle Switching)**: Verify 6 manifest aliases; assert `LauncherIconManager` defers alias changes with `DONT_KILL_APP` when `isTracking == true`.
* **TST-UI-445.3 (Cockpit Emblem & Crash-Free Quick-Picker Contract)**: Verify `SensorStatus.kt` 24dp emblem layout symmetry; verify source code of `SupporterQuickIconPickerDialog.kt` passes `variant.foregroundRes` and zero `R.mipmap.*` to `painterResource`.
* **TST-UI-445.4 (Drawer Header & Share Watermark Contract)**: Assert `AppNavigationDrawer` and `ShareUtils` render active supporter tier badge and pill.
* **TST-LOC-001 / TST-UI-445.5 (9-Locale Localization Parity)**: Run `TranslationParityTest` verifying all supporter strings across all 9 supported locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-445.1` | Geometry/Branding | `SupporterBadgeContractTest.testSupporterAdaptiveIconSafeZoneContainment` | `REQ-UI-445.1` | Specified |
| `TST-UI-445.2` | Lifecycle/Manifest | `LauncherIconManagerTest.testDeferredActivation` | `REQ-UI-445.2`, `REQ-UI-445.6` | Specified |
| `TST-UI-445.3` | UI Cockpit/Dialog | `SupporterBadgeContractTest.testQuickPickerDialogUsesForegroundDrawables` | `REQ-UI-445.3` | Specified |
| `TST-UI-445.4` | UI Drawer/Share | `DrawerHeaderTest`, `ShareUtilsContractTest` | `REQ-UI-445.4`, `REQ-UI-445.5` | Specified |
| `TST-LOC-001` | Localization | `TranslationParityTest` | `REQ-UI-445` (9 locales) | Specified |
