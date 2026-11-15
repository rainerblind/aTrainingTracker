# Stage 3: Implementation Plan - ATT-7972: Cockpit Radar Tail Light Button & Action Row

**Ticket**: [ATT-7972](https://rainerblind.atlassian.net/browse/ATT-7972) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7972`  
**Requirement Mapping**: `REQ-CON-043.1`..`REQ-CON-043.5`  
**Test Mapping**: `TST-CON-043.1`..`TST-CON-043.6`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Cyclists require direct control and live visibility of radar tail light modes from the cockpit without leaving tracking. The system introduces `RadarLightButton` with live mode icon and `AUTO` micro-badge, short-tap cycling, and long-press quick-switch sheet invocation, integrated into a canonical `[RadarLightButton, LapButton, BikeBellButton]` row in `TrackingTabsScreen` backed by SQLite v21 `ShowRadarLight` persistence.

---

## 2. Traceability & Requirements Mapping
* `REQ-CON-043.1` -> `TST-CON-043.1`: `TrackingViewsDbHelper` v20->v21 migration and `TrackingViewsRepository`.
* `REQ-CON-043.2` -> `TST-CON-043.2`: `RadarLightButton` interactions, semantic icons, and `AUTO` micro-badge.
* `REQ-CON-043.3` -> `TST-CON-043.3`: `TrackingTabsScreen` canonical `[Licht, Lap, Klingel]` row and 360dp/130% fit.
* `REQ-CON-043.4` -> `TST-CON-043.4`: `SensorGridScreen` config toggle and `fabBottomClearance`.
* `REQ-CON-043.5` -> `TST-CON-043.5`: 9-language translation parity in `values*/strings.xml` without emojis.

---

## 3. System Invariants & Preserved Behavior
1. **DB Idempotency**: Migration from v20 to v21 uses `PRAGMA table_info` before `ALTER TABLE` to avoid crashes.
2. **Canonical Ordering**: Bottom action buttons are strictly ordered `[RadarLightButton, LapButton, BikeBellButton]`.
3. **Responsive Width Budget**: Row buttons use `Modifier.weight(1f)` with `maxLines = 1` for 360dp / 130% font scale.
4. **StVZO Compliance**: Radar tail light mode cycling respects active StVZO mode pruning.
5. **UI Thread Safety**: Button taps dispatch mode cycling asynchronously via `RadarLightController`.

---

## 4. Proposed Architectural Changes
* **Database & Repository (`SWE.2`)**: Increment `DB_VERSION` to 21 in `TrackingViewsDatabaseManager.java`. Add `SHOW_RADAR_LIGHT = "ShowRadarLight"` column (`int DEFAULT 0`, `1` in default cycling views). Update `CREATE_VIEWS_TABLE_V21`, `onUpgrade` v20->v21 idempotent migration, and `DatabaseSchemaRegistry`. Update `TrackingViewsRepository.kt` and `TrackingViewInfo`.
* **Component (`RadarLightButton.kt`)**: M3 `ElevatedButton` (`height = 72.dp`, `RoundedCornerShape(12.dp)`). Renders active mode vector icon and distinct `AUTO` micro-badge when `isAutoMode == true`. Handles short tap (`cycleNextMode()`) and long press (opens `RadarLightQuickSwitchSheet`) via `MutableInteractionSource`.
* **Cockpit Integration (`TrackingTabsScreen.kt`)**: Place `RadarLightButton` as first item in the bottom row: `[RadarLightButton, LapButton, BikeBellButton]`. Conditionally render based on `viewInfo.showRadarLight` and radar availability.
* **Configuration & Clearance (`SensorGridScreen.kt`)**: Add `SpatialCockpitToggleCard` for `ShowRadarLight` in cockpit settings. Dynamically compute `fabBottomClearance` (e.g. 68.dp) whenever any action button is visible.
* **UI Consistency (`Rule 23`)**: Complies with `docs/design_guidelines.md` §1.4, §5.3, §5.20. Follows `BikeBellButton` interaction architecture.
* **Existing User Migration Behavior (`Rule 5.6`)**: Preserves existing v20 layouts; sets `ShowRadarLight = 1` for cycling tracking views, `0` for running/other views.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (DB v21 Migration & Repository)**: Update `TrackingViewsDatabaseManager.java` (DB v21, column, migration, schema registry), `TrackingViewsRepository.kt`, and verify with `TrackingViewsDbHelperMigrationTest.kt`.
2. **Step 2 (RadarLightButton Composable)**: Author `RadarLightButton.kt` with tap/long-press demuxing, Auto badge, and mode icons; verify with `RadarLightButtonTest.kt`.
3. **Step 3 (Cockpit Action Row Integration)**: Wire `[RadarLightButton, LapButton, BikeBellButton]` into `TrackingTabsScreen.kt`; verify responsiveness and canonical order in `TrackingTabsScreenTest.kt`.
4. **Step 4 (SensorGridScreen Toggle & fabBottomClearance)**: Add toggle card and adjust FAB clearance in `SensorGridScreen.kt`; verify with `SensorGridScreenTest.kt`.
5. **Step 5 (9-Language Parity & Strings)**: Add strings to `values/` and 8 locale directories without emojis; verify with `TranslationParityTest.kt`.
6. **Step 6 (Clean-Room Verification Suite)**: Execute targeted unit tests and `:app:detekt` cleanly.

---

## 6. Verification, Risk Classification & Rollback Plan
* **Risk Classification**: **MEDIUM / Visual/UI** (Touches Compose cockpit layouts, DB schema migration, and interaction handling).
* **Verification**: Targeted SQLite migration tests, Compose UI tests, translation parity test, and Clean-Room regression.
* **Rollback**: Atomic revert of `feature/ATT-7972` branch prior to sprint merge.
