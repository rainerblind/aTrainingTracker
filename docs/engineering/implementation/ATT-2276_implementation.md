# Stage 4: Implementation - ATT-2276: Implement In-App Feature Discovery and Tips Guide

**Ticket**: [ATT-2276](https://rainerblind.atlassian.net/browse/ATT-2276)  
**Parent Epic**: [ATT-2273](https://rainerblind.atlassian.net/browse/ATT-2273) (*Feature Discovery*)  
**Requirement Mapping**: `REQ-UI-432`  
**Test Spec Mapping**: `TST-UI-397`  
**Branch**: `feature/ATT-2276`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-23  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-422.3` (*Drawer Item Count & Settings Consolidation*) and `REQ-UI-159` (*Single-Activity Navigation*); introduces `REQ-UI-432` (*In-App Feature Discovery & Tips Guide*).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.16 (commit `04b6b158`, ATT-3851, 5-group drawer), Sprint 2026-41.2 (commit `4623bb6f`, ATT-3928, collapsible groups), and Sprint 2026-42.1 (commit `30954b03`, ATT-4289, 22 destinations).
3. **Root Reason for Existing Formulation**:
   - `REQ-UI-422` capped destination count to 22 when Sport Types was relocated to Settings to avoid visual crowding and enforce the collapsible accordion pattern.
4. **Preservation of Core Invariants**:
   - **5 Category Hubs**: Training, Maps & Navigation, My Stuff, Online Communities, General Settings.
   - **9 Non-Collapsible Rows**: Training (4) + Maps (5) = 9 default visible rows when secondary groups are collapsed.
   - **Terminal Position**: `drawer_advanced_tuning` strictly retained as the terminal entry of Settings.
   - **Zero Asset Bloat**: 100% Android XML drawables; 0 raster bitmaps (PNG/JPG/WEBP).
   - **Localization Parity**: 100% translation coverage across all 32 string resources in all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 1. Summary of Construction & Implementation

In accordance with the approved Stage 3 Implementation Plan (`docs/engineering/plans/ATT-2276_plan.md`):

1. **Navigation Drawer Item & Route Mapping**:
   - Defined `drawer_tips_guide` in `app/src/main/res/values/ids.xml`.
   - Added vector asset `app/src/main/res/drawable/ic_tips_and_updates.xml`.
   - Integrated `NavRoutes.TIPS_GUIDE = "tips_guide"` with bidirectional mapping in `fromDrawerItemId` and `toDrawerItemId`.
   - Inserted `DrawerItemConfig(R.id.drawer_tips_guide, R.drawable.ic_tips_and_updates, R.string.drawer_tips_guide)` into `drawer__settings` preceding `drawer_privacy_policy` and `drawer_advanced_tuning`.
   - Registered `composable(NavRoutes.TIPS_GUIDE)` in `ATrainingTrackerApp.kt` with deep link dispatch routing and safe back navigation.

2. **Compose Surface Construction**:
   - Implemented `app/src/main/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreen.kt`:
     - `TipItemConfig` data model and `TipsGuideData.TIPS` curated registry of 7 tips:
       1. Favorite Routes & Auto-Clustering -> `NavRoutes.LOCATIONS`
       2. Multi-Protocol Sensors & Failover -> `NavRoutes.SENSORS`
       3. Cockpit Displays & Bike Bell -> `NavRoutes.SPORT_TYPES`
       4. Strava Live Segments & Scrubbing -> `NavRoutes.SEGMENTS`
       5. Automated Gear & Equipment -> `NavRoutes.EQUIPMENT`
       6. Physiology & Training Zones -> `NavRoutes.trainingZones()`
       7. Data Sovereignty & Offline Backup -> `NavRoutes.BACKUP_RESTORE`
     - Header card introducing feature discovery.
     - `ElevatedCard` layout with `RoundedCornerShape(12.dp)`, badge icon container, title, subtitle, explanatory description, and `FilledTonalButton` deep link.
     - `Modifier.navigationBarsPadding()` edge-to-edge system bar inset clearance.
     - Light and dark theme previews.

3. **9-Language String Parity**:
   - All 32 string keys defined across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Validated via `TranslationParityTest`.

4. **Living Documentation & Governance**:
   - Updated `REQ-UI-432` to `[Implemented]` in `docs/requirements/req_ui_design_system.md`.
   - Updated index counts in `docs/requirements.md` (795 total).
   - Validated governance compliance via `python3 tools/verify_requirement_governance.py`.

5. **Targeted Unit & Contract Tests**:
   - Created `app/src/test/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreenContractTest.kt` (TST-UI-397.3).
   - Updated `SingleActivityNavigationTest.kt` (TST-UI-397.1).
   - Updated `NavigationDrawerStateTest.kt` (TST-UI-397.2).
   - Updated `AppNavigationDrawerTest.kt`.
   - Verified 100% test pass rate across all targeted tests.
