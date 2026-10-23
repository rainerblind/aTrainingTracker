# Stage 3: Implementation Plan - ATT-4775: Fix TipsGuideScreen Cockpit Layout Deep Link and Add Dedicated Sport Types Tip Card

**Ticket**: [ATT-4775](https://rainerblind.atlassian.net/browse/ATT-4775)  
**Sub-task**: [ATT-4779](https://rainerblind.atlassian.net/browse/ATT-4779) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4775`  
**Requirement Mapping**: `REQ-UI-432` (`REQ-UI-432.4`, `REQ-UI-432.6`)  
**Test Mapping**: `TST-UI-397` (`TST-UI-397.3`, `TST-UI-397.4`, `TST-UI-397.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Description & Architectural Context
In `TipsGuideScreen.kt`, the 3rd tip card (`cockpit_customization`) was miswired to `NavRoutes.SPORT_TYPES` with `ic_display_settings` rather than deep-linking to `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)` (`SettingsBottomSheetType.ACTIVITY_TYPE`) with canonical icon `ic_table_edit`. Furthermore, custom Sport Types had no dedicated tip card. This plan outlines the atomic changes to route `cockpit_customization` to the Cockpit Layout Configuration dialog, add a dedicated 8th card for custom Sport Types (`NavRoutes.SPORT_TYPES`, `ic_sports_combined`), and update contract and navigation tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-432` (*In-App Feature Discovery, Tips Guide & Navigation Drawer Integration*)
* **Test Mapping**: `TST-UI-397` (*In-App Feature Discovery & Tips Guide Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Opt-in & Zero Bloat**: Non-intrusive discovery (`REQ-UI-432.5`); 100% vector drawables (`ic_table_edit.xml`, `ic_sports_combined.xml`); zero raster asset bloat.
2. **Drawer & Density Invariants**: 5 category hubs, 23 total destinations, and non-collapsible 9 visible rows strictly preserved.
3. **9-Language Parity**: All 36 tip guide string resources maintain 100% parity across all 9 locales (`TranslationParityTest`).
4. **Code Lock & Human Decision Gate**: Code lock enforced until Gate 3 passes; parent ticket terminal transition reserved for human review.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Navigation Layer (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)**: Define `NavRoutes.TRACKING_LAYOUTS = "tracking_layouts"`. In `toDrawerItemId`, map `TRACKING_LAYOUTS` $\to$ `R.id.drawer_tracking_layouts`. In `ATrainingTrackerApp.kt`, forward `NavRoutes.TRACKING_LAYOUTS` $\to$ `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)`.
* **Tips Registry (`TipsGuideScreen.kt`)**: Update Tip 3 to `ic_table_edit` and `TRACKING_LAYOUTS`. Append Tip 8 (`sport_types`) with `ic_sports_combined` and `NavRoutes.SPORT_TYPES`.
* **UI Consistency (Rule 23)**:
  * *Reference*: `AppNavigationDrawer.kt:229-230` (shares identical `ic_table_edit` and `ic_sports_combined` vector icons).
  * *Reused components*: `TipCard`, `ElevatedCard(12.dp)`, `safePainterResource`.
  * *Theme tokens*: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `surface`, `onSurface`, `onSurfaceVariant`.
  * *New one-off styles*: None (100% standard theme tokens).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Run `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>` and verify exit code 0.

### Step 2: Navigation Layer Integration
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`, `ATrainingTrackerApp.kt`
* **Changes**: Add `const val TRACKING_LAYOUTS = "tracking_layouts"` and map in `toDrawerItemId`. Wire `NavRoutes.TRACKING_LAYOUTS` to `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)` in `TipsGuideScreen` host composable.

### Step 3: Tip Model & Screen Registry Update
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreen.kt`
* **Changes**: Update `cockpit_customization` to `R.drawable.ic_table_edit` and `NavRoutes.TRACKING_LAYOUTS`. Add 8th `TipItemConfig` (`sport_types`) with `R.drawable.ic_sports_combined` and `NavRoutes.SPORT_TYPES`.

### Step 4: Unit & Contract Test Updates
* **Files**: `TipsGuideScreenContractTest.kt`, `SingleActivityNavigationTest.kt`
* **Changes**: Update test assertions for 8 curated tips, new icons, route targets, dispatch list, and vector asset bloat checks.

### Step 5: Test Execution & Governance Verification
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.help.*" --tests "com.atrainingtracker.trainingtracker.localization.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TipsGuideScreenContractTest`, `SingleActivityNavigationTest`, `TranslationParityTest`), followed by Stage 5 verification.
* **Rollback**: Clean revert via `git restore .`.
