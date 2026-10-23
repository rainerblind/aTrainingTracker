# Stage 1 Analysis: ATT-4775 - Fix TipsGuideScreen Cockpit Layout Deep Link and Add Dedicated Sport Types Tip Card

**Ticket**: [ATT-4775](https://rainerblind.atlassian.net/browse/ATT-4775)  
**Sub-task**: [ATT-4777](https://rainerblind.atlassian.net/browse/ATT-4777) (`[Analysis]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4775`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
In `TipsGuideScreen.kt` (introduced in `ATT-2276`), two feature discovery defects were identified during Sprint Review:
1. *Mismatched Deep Link & Icon on Cockpit Tip*: The 3rd tip card (`cockpit_customization` — "Cockpit Displays & Bike Bell") deep-links to `NavRoutes.SPORT_TYPES` (`R.id.drawer_sport_types`) instead of opening the Cockpit Layout Configuration dialog (`R.id.drawer_tracking_layouts` / `SettingsBottomSheetType.ACTIVITY_TYPE`), and uses `R.drawable.ic_display_settings` instead of the canonical `R.drawable.ic_table_edit`.
2. *Missing Sport Types Tip Card*: Custom Sport Types (`NavRoutes.SPORT_TYPES`) lacks its own dedicated power tip card, leaving custom sub-sports, automatic speed-based detection thresholds, default gear linking, and Strava/TCX export mappings undiscovered.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause Analysis**:
  * In `ATT-2276`, Cockpit layout configuration is initiated per activity type via `ActivityTypeSelectionDialog`, opened by drawer item `R.id.drawer_tracking_layouts`. Because this is a bottom sheet dialog rather than a standalone `NavHost` screen, the tip author mistakenly wired `cockpit_customization` to `NavRoutes.SPORT_TYPES`.
  * `AppNavigationDrawer.kt:229` binds `drawer_tracking_layouts` to `ic_table_edit` and `drawer_sport_types` to `ic_sports_combined`. The tip card failed to reflect this visual baseline.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Audit of `TipsGuideScreen.kt:106-114`*: Confirmed `cockpit_customization` uses `ic_display_settings` and `targetRoute = NavRoutes.SPORT_TYPES`.
  * *Audit of `ATrainingTrackerApp.kt:571-582`*: Confirmed `onNavigateToRoute` forwards `NavRoutes.SPORT_TYPES` to `activity.navigateToDrawerItem(R.id.drawer_sport_types)`. Lacks routing for `R.id.drawer_tracking_layouts`.
  * *Audit of `MainActivityWithNavigation.kt:764-768`*: Calling `navigateToDrawerItem(R.id.drawer_tracking_layouts)` resolves to `SettingsBottomSheetType.ACTIVITY_TYPE`, launching `ActivityTypeSelectionDialog` as expected.
  * *Drawables*: Verified `ic_table_edit.xml` and `ic_sports_combined.xml` exist as pure vector drawables.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update `cockpit_customization` in `TipsGuideScreen.kt` and `ATrainingTrackerApp.kt` to route to `R.id.drawer_tracking_layouts` and use `R.drawable.ic_table_edit`.
  * Add dedicated 8th power tip card (`sport_types`) in `TipsGuideData.TIPS` routing to `NavRoutes.SPORT_TYPES` with `R.drawable.ic_sports_combined`.
  * Add 100% 9-language localization parity for the new tip across all `values*/strings.xml` locales.
  * Update `TipsGuideScreenContractTest.kt` for 8 tips, new icons, and route dispatch verification.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to `ActivityTypeSelectionDialog`, `TrackingTabsViewModel`, or `SportTypeViewModel`.
  * No modifications to drawer structure, drawer item ordering, or group definitions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-432.4` (*Seven Curated Feature Discovery Tips*) $\to$ 8 curated tips, and `REQ-UI-432.6` (*9-Language Localization Parity*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-43.9 via commit `99f66b16e` (`ATT-2276`, "Implement In-App Feature Discovery and Tips Guide").
* **Root Reason for Existing Formulation**: Conflation of sport-specific cockpit layouts with the sport types manager in the navigation drawer during rapid initial creation.
* **Preservation of Core Invariants**: Opt-in non-intrusive discovery (`REQ-UI-432.5`), zero raster asset bloat (`REQ-UI-432.5`), non-collapsible drawer density of 9 visible items preserved, and clean-room unit test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
* Define `NavRoutes.TRACKING_LAYOUTS = "tracking_layouts"` in `NavRoutes.kt`.
* In `TipsGuideData.TIPS`: update `cockpit_customization` to `R.drawable.ic_table_edit` and `NavRoutes.TRACKING_LAYOUTS`; append `sport_types` tip with `R.drawable.ic_sports_combined` and `NavRoutes.SPORT_TYPES`.
* In `ATrainingTrackerApp.kt`: route `NavRoutes.TRACKING_LAYOUTS` $\to$ `activity.navigateToDrawerItem(R.id.drawer_tracking_layouts)`.
* Define localized strings (`tip_sport_types_title`, `tip_sport_types_subtitle`, `tip_sport_types_desc`, `tip_sport_types_action`) across all 9 `strings.xml` locales.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in navigation; zero raster bloat; 100% 9-language parity; Human Decision Gate enforced.
* **Risk Rating**: **LOW** (Confined Compose UI and localized resource additions; zero DB or background service changes).
