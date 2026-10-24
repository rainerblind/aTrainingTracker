# Stage 2: Requirement & Test Specification - ATT-4775: Fix TipsGuideScreen Cockpit Layout Deep Link and Add Dedicated Sport Types Tip Card

**Ticket**: [ATT-4775](https://rainerblind.atlassian.net/browse/ATT-4775)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4775`  
**Requirement Mapping**: `REQ-UI-432` (refining `REQ-UI-432.4`, `REQ-UI-432.6`)  
**Test Spec ID**: `TST-UI-397` (`TST-UI-397.3`, `TST-UI-397.4`, `TST-UI-397.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-UI-432.4`, `REQ-UI-432.6`)

### 1.1 Problem Statement & Rationale
In `TipsGuideScreen.kt`, the 3rd tip card (`cockpit_customization` — "Cockpit Displays & Bike Bell") mistakenly routed to `NavRoutes.SPORT_TYPES` (`R.id.drawer_sport_types`) and used `R.drawable.ic_display_settings` rather than opening the Cockpit Layout Configuration dialog (`R.id.drawer_tracking_layouts` / `SettingsBottomSheetType.ACTIVITY_TYPE`) with canonical icon `R.drawable.ic_table_edit`. Furthermore, custom Sport Types had no dedicated tip card. Correcting the deep-link route and adding a dedicated 8th card (`sport_types` $\to$ `NavRoutes.SPORT_TYPES`, `R.drawable.ic_sports_combined`) provides athletes with accurate, 1-tap feature discovery for both capabilities.

### 1.2 Functional & Architectural Requirements
1. **Cockpit Customization Deep Link & Icon (`REQ-UI-432.4`)**: In `TipsGuideData.TIPS`, `cockpit_customization` SHALL use icon `R.drawable.ic_table_edit` and target route `NavRoutes.TRACKING_LAYOUTS` (`"tracking_layouts"`). In `ATrainingTrackerApp.kt`, `NavRoutes.TRACKING_LAYOUTS` SHALL trigger `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)` to open `ActivityTypeSelectionDialog`.
2. **Dedicated Sport Types Tip Card (`REQ-UI-432.4`)**: `TipsGuideData.TIPS` SHALL include an 8th tip card (`sport_types`) with title `tip_sport_types_title`, subtitle `tip_sport_types_subtitle`, description `tip_sport_types_desc`, action button `tip_sport_types_action` ("Manage Sport Types"), vector icon `R.drawable.ic_sports_combined`, and target route `NavRoutes.SPORT_TYPES`.
3. **9-Language Localization Parity (`REQ-UI-432.6`)**: All 36 tip guide string resources (including the 4 new `tip_sport_types_*` strings) SHALL maintain 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT.
4. **Preserved Invariants (`REQ-UI-432.5`)**: Non-intrusive opt-in guide, zero bitmap/raster asset bloat, navigation drawer 9-item default density, and clean-room unit test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-432.4` (*Curated Feature Discovery Tips*) and `REQ-UI-432.6` (*9-Language Localization Parity*), refining `REQ-UI-432`.
* **Historical Origin & Commit Trace**: Sprint 2026-43.9 via commit `99f66b16e` (`ATT-2276`, "Implement In-App Feature Discovery and Tips Guide").
* **Root Reason for Existing Formulation**: Cockpit customization was initially wired to `NavRoutes.SPORT_TYPES` with `ic_display_settings` because cockpit layouts are partitioned by sport type, leaving custom sport types without its own tip card and incorrectly routing cockpit configuration to the sport types manager instead of the cockpit layouts dialog (`R.id.drawer_tracking_layouts`).
* **Preservation of Core Invariants**: Opt-in non-intrusive discovery (`REQ-UI-432.5`), zero raster asset bloat, non-collapsible drawer density of 9 visible items preserved, and 100% clean-room test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (8 Curated Cards)**: *Given* an athlete viewing `TipsGuideScreen`, *When* rendered, *Then* exactly 8 curated cards are displayed.
* **Criterion 2 (Cockpit Layout Navigation)**: *Given* `TipsGuideScreen`, *When* tapping Cockpit Customization action button, *Then* `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)` is invoked and `ActivityTypeSelectionDialog` opens.
* **Criterion 3 (Sport Types Navigation)**: *Given* `TipsGuideScreen`, *When* tapping Sport Types action button, *Then* `activity.navigateToDrawerItem(R.id.drawer_sport_types)` is invoked.
* **Criterion 4 (Localization Parity)**: *Given* the 9 supported locales, *When* `TranslationParityTest` runs, *Then* 100% parity is verified with 0 missing strings.

---

## 2. Test Specification (`TST-UI-397.3`, `TST-UI-397.4`, `TST-UI-397.5`)

### Test Cases
* `TST-UI-397.3` (**TipsGuideScreen Model & Deep Link Contract Test**): Assert `TipsGuideData.TIPS` contains 8 items; verify `cockpit_customization` uses `ic_table_edit` and `NavRoutes.TRACKING_LAYOUTS`; verify `sport_types` uses `ic_sports_combined` and `NavRoutes.SPORT_TYPES`; verify all 8 deep-link callbacks dispatch expected routes; verify zero raster asset bloat.
* `TST-UI-397.4` (**9-Language Localization Parity Test**): Run `TranslationParityTest`; assert 100% translation coverage, matching specifiers, and literal `\n` linebreaks across all 9 locales.
* `TST-UI-397.5` (**Clean-Room Regression Execution**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.help.*"` and verify 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-397.3` | Contract / Unit | `TipsGuideData.TIPS`, `TipsGuideScreenContractTest` | `REQ-UI-432.4` | Planned |
| `TST-UI-397.4` | Parity / Unit | `values*/strings.xml`, `TranslationParityTest` | `REQ-UI-432.6` | Planned |
| `TST-UI-397.5` | Regression | Full Help & Navigation Test Suite | `REQ-UI-432.5` | Planned |
