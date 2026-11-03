# Stage 2: Requirement & Test Specification - ATT-2276: Implement In-App Feature Discovery and Tips Guide

**Ticket**: [ATT-2276](https://rainerblind.atlassian.net/browse/ATT-2276)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*User Documentation, Feature Showcase and Video Guides*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-2276`  
**Requirement Mapping**: `REQ-UI-432` (*In-App Feature Discovery, Tips Guide & Navigation Drawer Integration*)  
**Test Spec ID**: `TST-UI-397` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (REQ-UI-432)

### 1.1 Problem Statement & Rationale
Athletes currently lack an in-app discovery surface for flagship background capabilities (route clustering, sensor failover, gear tracking, live segments, physiological models). The system SHALL provide an opt-in, glanceable "Tips & Tricks" screen (`TipsGuideScreen`) accessible via `R.id.drawer_tips_guide` under the collapsible Settings group in `AppNavigationDrawer`, featuring 7 curated tips with 1-tap deep links, zero blocking tutorial overlays, zero raster asset bloat, and full 9-locale parity.

### 1.2 Functional & Architectural Requirements
1. **Drawer Placement (`REQ-UI-432.1`)**: Navigation drawer SHALL add `R.id.drawer_tips_guide` (`ic_tips_and_updates`) inside collapsible `drawer__settings` preceding `drawer_privacy_policy`. Total destinations expand from 22 to 23; default visible count across non-collapsible groups strictly remains 9.
2. **Navigation Route (`REQ-UI-432.2`)**: `NavRoutes` SHALL map `NavRoutes.TIPS_GUIDE = "tips_guide"` bidirectionally with `R.id.drawer_tips_guide`.
3. **TipsGuideScreen Layout (`REQ-UI-432.3`)**: Compose `Scaffold` + `TopAppBar` + `LazyColumn` of expandable `ElevatedCard`s (`12.dp` radius) with title, subtitle, expandable body, and deep-link action button.
4. **7 Curated Power Tips (`REQ-UI-432.4`)**:
   - Tip 1: Favorite Routes & Auto-Clustering $\to$ `NavRoutes.LOCATIONS`
   - Tip 2: Multi-Protocol Sensors & Failover $\to$ `NavRoutes.SENSORS`
   - Tip 3: Cockpit Displays & Bike Bell $\to$ `NavRoutes.SPORT_TYPES`
   - Tip 4: Strava Live Segments & Scrubbing $\to$ `NavRoutes.SEGMENTS`
   - Tip 5: Automated Gear & Equipment $\to$ `NavRoutes.EQUIPMENT`
   - Tip 6: Physiology & Zones $\to$ `NavRoutes.trainingZones()`
   - Tip 7: Data Sovereignty & Offline Backup $\to$ `NavRoutes.BACKUP_RESTORE`
5. **Non-Intrusive Invariant (`REQ-UI-432.5`)**: 100% opt-in (no blocking popups/onboarding wizards); 100% vector drawables (zero raster/GIF/Lottie bloat).
6. **9-Language Parity (`REQ-UI-432.6`)**: Complete coverage for all 32 string resources across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then) & Archaeology
* *Given* an athlete expanding Settings in the navigation drawer, *when* viewing items, *then* `drawer_tips_guide` is visible before Privacy Policy.
* *Given* selecting `drawer_tips_guide`, *when* navigated, *then* `TipsGuideScreen` is displayed at `NavRoutes.TIPS_GUIDE`.
* *Given* any tip card, *when* clicking its action button, *then* the app navigates directly to the target destination without backstack corruption.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-422.3` (*Drawer Item Count & Settings Consolidation*) and `REQ-UI-159` (*Single-Activity Navigation*); introduces `REQ-UI-432` (*In-App Feature Discovery & Tips Guide*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (commit `04b6b158`, ATT-3851, 5-group drawer), Sprint 2026-41.2 (commit `4623bb6f`, ATT-3928, collapsible groups), and Sprint 2026-42.1 (commit `30954b03`, ATT-4289, 22 destinations).
3. *Root Reason for Existing Formulation*: `REQ-UI-422` capped destination count to 22 when Sport Types was relocated to Settings to avoid visual crowding and enforce the collapsible accordion pattern.
4. *Preservation of Core Invariants*: The 5 category hubs, non-collapsible 9 visible rows (`drawer__training` [4] + `drawer__maps` [5] = 9), `drawer_advanced_tuning` terminal positioning, type-safe `NavRoutes` architecture, and 100% clean-room test pass rate are strictly preserved.

---

## 2. Test Specification (TST-UI-397) & Independent Verification

* **Independent Peer Review & Verification (ATT-3534)**:
  - Audited `AppNavigationDrawer.kt`, `ids.xml`, `NavRoutes.kt`, and `MainActivityWithNavigation.kt`: confirmed `drawer_tips_guide` integrates cleanly into `drawer__settings`.
  - Audited strings across all 9 `values*/strings.xml`: 32 strings added with zero format specifier divergence; verified by `TranslationParityTest` (100% pass) and `processDebugResources`.
* **TST-UI-397.1 (NavRoutes Bidirectional Mapping)**: Assert `fromDrawerItemId` and `toDrawerItemId` resolve `drawer_tips_guide` $\leftrightarrow$ `"tips_guide"`, and `toSettingsBottomSheetType` returns null.
* **TST-UI-397.2 (Drawer Integrity & 23 Destinations)**: Assert `createDrawerGroups` has 5 hubs, `drawer_tips_guide` in Settings, total 23 items, non-collapsible count = 9.
* **TST-UI-397.3 (TipsGuideScreen Contract & Deep Links)**: Assert 7 tip items, model properties, and all 7 navigation callbacks dispatch cleanly.
* **TST-UI-397.4 (9-Language Parity)**: `TranslationParityTest` asserts all 32 strings present across 9 locales.
* **TST-UI-397.5 (Regression)**: Full clean-room pass on navigation and localization packages.

---

## 3. Traceability Matrix

| Test Spec ID | Scope | Target / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-397.1` | Unit | `NavRoutes` (`SingleActivityNavigationTest`) | `REQ-UI-432.2` | Specified |
| `TST-UI-397.2` | Unit | `AppNavigationDrawer` (`NavigationDrawerStateTest`) | `REQ-UI-432.1` | Specified |
| `TST-UI-397.3` | Contract | `TipsGuideScreen` (`TipsGuideScreenContractTest`) | `REQ-UI-432.3`, `.4`, `.5` | Specified |
| `TST-UI-397.4` | Localization | `TranslationParityTest` across 9 locales | `REQ-UI-432.6` | Verified |
| `TST-UI-397.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
