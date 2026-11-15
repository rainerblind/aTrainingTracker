# Stage 2: Requirement & Test Specification - ATT-7972: Cockpit Radar Tail Light Button & Action Row

**Ticket**: [ATT-7972](https://rainerblind.atlassian.net/browse/ATT-7972) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Radar*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7972`  
**Requirement Mapping**: `REQ-CON-043` (`REQ-CON-043.1`..`REQ-CON-043.5`) | **Test Spec ID**: `TST-CON-043` (`TST-CON-043.1`..`TST-CON-043.6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-CON-043)

### 1.1 Problem Statement & Rationale
During rides, cyclists cannot quickly inspect or adjust radar tail light operating modes without leaving the cockpit or digging through menus. The cockpit tracking screen requires a prominent tail light quick action button within a canonical `[RadarLightButton, LapButton, BikeBellButton]` row, featuring real-time mode icons, `AUTO` micro-badge, short-tap mode cycling, and long-press quick-switch sheet invocation, backed by schema version 21 `ShowRadarLight` persistence.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional / UI | **Verification Method**: Unit Test / Compose Test / DB Migration Test

### 1.3 Functional & Architectural Requirements
1. `TrackingViewsDbHelper` SHALL increment schema from v20 to v21, adding `ShowRadarLight INTEGER NOT NULL DEFAULT 0` (or `1` for cycling default view), maintaining contract parity in `onCreate` and `DatabaseSchemaRegistry`.
2. `RadarLightButton` SHALL display semantic mode icons/colors, render an `AUTO` micro-badge when `isAutoMode` is active, short-tap to `cycleNextMode()`, and long-press to open `RadarLightQuickSwitchSheet`.
3. `TrackingTabsScreen` SHALL arrange `[RadarLightButton, LapButton, BikeBellButton]` in a canonical bottom row fitting 360.dp viewports at 130% font scale without text truncation.
4. `SensorGridScreen` SHALL expose a configuration toggle for `ShowRadarLight` and adapt `fabBottomClearance`.
5. All radar light button strings SHALL maintain 100% 9-language translation parity without emojis.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* DB v20 database, *When* upgraded to v21, *Then* `ShowRadarLight` column is added idempotently without data loss.
* *Given* cockpit view with radar light enabled, *When* cyclist short-taps the button, *Then* `RadarLightController.cycleNextMode()` is triggered.
* *Given* tail light in `Automatik`, *When* inspected on cockpit, *Then* live mode icon is displayed with distinct `AUTO` micro-badge.
* *Given* 360.dp width screen at 130% font scale, *When* action row is rendered, *Then* `[Licht, Lap, Klingel]` buttons fit without overlapping or clipping.

### 1.5 System Invariants
Zero SQLite corruption during v20->v21 migration; StVZO compliance preserved; 9-language translation parity; zero UI thread blocking during mode cycle dispatch.

---

## 2. Test Specification (TST-CON-043)

### Test Case 1: `testTrackingViewsDbHelper_v20ToV21Migration` (`TST-CON-043.1`)
* **Scope**: SQLite Migration Test (`TrackingViewsDbHelperMigrationTest.kt`)
* **Expected Result**: Incremental migration adds `ShowRadarLight` column idempotently; fresh `onCreate()` matches upgraded schema.

### Test Case 2: `testRadarLightButton_tapCycle_andLongPressSheet` (`TST-CON-043.2`)
* **Scope**: Compose UI Test (`RadarLightButtonTest.kt`)
* **Expected Result**: Short tap calls `cycleNextMode()`; long press opens `RadarLightQuickSwitchSheet`; `AUTO` micro-badge renders when `isAutoMode` is true.

### Test Case 3: `testActionRow_canonicalOrder_andResponsiveFit` (`TST-CON-043.3`)
* **Scope**: Compose UI Test (`TrackingTabsScreenTest.kt`)
* **Expected Result**: Renders `[RadarLightButton, LapButton, BikeBellButton]` order; fits 360.dp viewport at 130% font scaling cleanly.

### Test Case 4: `testSensorGridScreen_showRadarLightToggle_andClearance` (`TST-CON-043.4`)
* **Scope**: Unit & Compose UI Test (`SensorGridScreenTest.kt`)
* **Expected Result**: Toggle modifies `ShowRadarLight` preference and updates `fabBottomClearance` appropriately.

### Test Case 5: 9-Language Localization & Translation Parity (`TST-CON-043.5`)
* **Scope**: Android Lint & Unit Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across `values/` and 8 locale directories without emoji characters.

### Test Case 6: Clean-Room Regression Verification (`TST-CON-043.6`)
* **Scope**: Targeted test suite (`REQ-PRO-001`) passes with zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-043.1` | SQLite | `TrackingViewsDbHelper.onUpgrade` (v20->v21) | `REQ-CON-043.1` | Specified |
| `TST-CON-043.2` | Compose | `RadarLightButton` interactions & Auto badge | `REQ-CON-043.2` | Specified |
| `TST-CON-043.3` | Compose | `TrackingTabsScreen` action row & responsiveness | `REQ-CON-043.3` | Specified |
| `TST-CON-043.4` | Compose | `SensorGridScreen` toggle & `fabBottomClearance` | `REQ-CON-043.4` | Specified |
| `TST-CON-043.5` | Localization | `TranslationParityTest` for radar light strings | `REQ-CON-043.5` | Specified |
| `TST-CON-043.6` | Regression | Targeted package unit & Compose tests | `REQ-PRO-001` | Specified |
