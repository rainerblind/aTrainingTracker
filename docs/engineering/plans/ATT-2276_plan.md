# Stage 3: Implementation Plan - ATT-2276: Implement In-App Feature Discovery and Tips Guide

**Ticket**: [ATT-2276](https://rainerblind.atlassian.net/browse/ATT-2276)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-2276`  
**Requirement Mapping**: `REQ-UI-432` (`REQ-UI-432.1`–`6`)  
**Test Mapping**: `TST-UI-397` (`TST-UI-397.1`–`5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Description & Architectural Context
Athletes currently lack an in-app discovery surface for flagship background capabilities (route clustering, sensor failover, gear tracking, live segments, physiological models). This plan designs an opt-in, non-intrusive "Tips & Tricks" screen (`TipsGuideScreen`) accessible via `R.id.drawer_tips_guide` in `AppNavigationDrawer` under the collapsible Settings group. It provides 7 curated feature cards with 1-tap deep links, zero blocking tutorial overlays, zero raster asset bloat (100% vector drawables), and full 9-language localization parity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-432` (*In-App Feature Discovery, Tips Guide & Navigation Drawer Integration*)
* **Test Mapping**: `TST-UI-397` (*In-App Feature Discovery & Tips Guide Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Drawer Structure & Density**: 5 category hubs strictly preserved. Total destinations expand from 22 to 23; default visible rows across non-collapsible groups strictly remain 9 (`drawer__training` [4] + `drawer__maps` [5] = 9).
2. **Terminal Positioning**: `drawer_advanced_tuning` strictly remains the final item of `drawer__settings`.
3. **Non-Intrusive & Asset Invariants**: 100% opt-in (no blocking popups/onboarding wizards); zero raster bitmaps (PNG/JPG) or GIF/Lottie bloat.
4. **Test & Governance Integrity**: Zero literal UI source-text grepping in contract tests (Rule 36 / REQ-TST-002); 100% clean-room test pass rate.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Navigation Layer (`ids.xml`, `NavRoutes.kt`, `AppNavigationDrawer.kt`)**: Add `drawer_tips_guide` resource ID, map `NavRoutes.TIPS_GUIDE = "tips_guide"` bidirectionally, and insert `DrawerItemConfig` in `drawer__settings` before `drawer_privacy_policy`.
* **Compose Destination (`TipsGuideScreen.kt`, `ATrainingTrackerApp.kt`)**: Host `TipsGuideScreen` in `NavHost`. Structure with `Scaffold`, `TopAppBar`, intro header, and scrollable expandable `ElevatedCard` accordion.
* **UI Consistency**: Aligns with `docs/design_guidelines.md` §3 & §5: Material 3 `ElevatedCard` with `RoundedCornerShape(12.dp)`, `MaterialTheme.colorScheme` tokens (Light/Dark/AMOLED contrast), `Button` / `OutlinedButton` deep links, and `@Preview` composables.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Confirm exit code 0 via `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Resource ID & Vector Asset
* **Files**: `app/src/main/res/values/ids.xml`, `app/src/main/res/drawable/ic_tips_and_updates.xml`
* **Changes**: Declare `<item name="drawer_tips_guide" type="id" />` and add 24dp Material vector asset `ic_tips_and_updates.xml`.

### Step 3: Navigation Routing & Drawer Integration
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`, `AppNavigationDrawer.kt`, `ATrainingTrackerApp.kt`
* **Changes**: Define `NavRoutes.TIPS_GUIDE`, implement bidirectional drawer ID mapping, insert `drawer_tips_guide` into `drawer__settings`, and register `composable(NavRoutes.TIPS_GUIDE)` in `NavHost`.

### Step 4: Feature Discovery Screen (`TipsGuideScreen.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreen.kt`
* **Changes**: Implement `TipItemConfig` data model for 7 curated tips, `TipCard` expandable composable, `TipsGuideScreen`, and `@Preview` composables for Light and Dark themes.

### Step 5: Unit & Contract Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreenContractTest.kt` (`TST-UI-397.3`, model & callback dispatch; no literal source-text grep)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt` (`TST-UI-397.1`, route resolution)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerStateTest.kt` (`TST-UI-397.2`, 23 items, 9 visible)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt` (tinting & non-zero IDs)

### Step 6: Targeted Incremental Test Execution & Governance Verification
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.help.*" --tests "com.atrainingtracker.trainingtracker.localization.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit & contract test suite, followed by Stage 5 on-device screenshot inspection.
* **Rollback**: Clean revert via `git checkout -- . && git clean -fd`.
