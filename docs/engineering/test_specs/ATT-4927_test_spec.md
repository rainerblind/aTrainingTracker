# Stage 2: Requirement & Test Specification - ATT-4927: Sport-dependent bar support, empty tile fallback & upgrade parity

**Ticket**: [ATT-4927](https://rainerblind.atlassian.net/browse/ATT-4927) | **Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2` | **Branch**: `feature/ATT-4927`  
**Requirement Mapping**: `REQ-TRK-046` (*Per-Field Display Elements Configuration*)  
**Test Spec ID**: `TST-TRK-046` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-01  

---

## 1. Requirement Specification (REQ-TRK-046)
### 1.1 Problem Statement & Rationale
`ATT-4841` introduced per-field Value/Bar flags, but evaluated bar support purely by sensor type, displayed invalid switches for sports without zone support, forced bars on upgrade by ignoring legacy global `ZoneDisplayOptions.showGauge`, and left bar-only cards completely blank when no bar could be drawn.

### 1.2 Functional & Architectural Requirements
* `REQ-TRK-046.1`: `TrackingViewsDatabaseManager` SHALL bump `DB_VERSION = 18`. The v18 migration joins `ROWS_TABLE` with `VIEWS_TABLE` (`ActivityType` -> `sportType`): sets `ShowBar` for HR/Power from legacy `showGauge` if sport supported, else 0; sets `ShowBar = 1` for Balance/W'Bal on Bike, else 0; sets other sensors to 0; enforces `ShowValue = 1` if `ShowBar == 0`. `DatabaseSchemaRegistry` references `DB_VERSION` directly.
* `REQ-TRK-046.2`: Canonical predicate `isBarSupported(SensorType, BSportType)`: HR -> Run/Bike; Power/Balance/W'Bal -> Bike; others -> false. In `EditSensorFieldDialog`, element switches hide when unsupported and save `showValue = true, showBar = false`.
* `REQ-TRK-046.3`: In `SensorFieldView.kt`, numeric value row SHALL render if `fieldState.showValue == true || !hasActiveBar` (where `hasActiveBar = fieldState.showBar && (zoneGaugeState != null || pedalBalanceState != null || wPrimeBatteryState != null)`), ensuring zero empty tiles.
* `REQ-TRK-046.6`: `TrackingViewModel` SHALL restrict `pedalBalanceState` and `wPrimeBatteryState` publishing strictly to `BSportType.BIKE`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* an existing user database upgrading to v18 where legacy `showGauge = false`,
  * *When* migration runs,
  * *Then* `ShowBar` evaluates to `0` and `ShowValue` evaluates to `1` on existing HR/Power fields.
* *Given* editing an HR field in an "Other"/generic sport layout,
  * *When* `EditSensorFieldDialog` opens,
  * *Then* Value/Bar switches are hidden and saving enforces `showValue = true, showBar = false`.
* *Given* a sensor field with `showValue = false, showBar = true` before sport resolution,
  * *When* `SensorFieldView` renders,
  * *Then* it falls back to rendering the numeric value row so the tile is never empty.

### 1.4 System Invariants
Zero empty cockpit tiles, backward-compatible migration, user settings preservation, 100% test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-TRK-046`, amending `REQ-UI-376`, `REQ-UI-377`, `REQ-UI-416`, and `REQ-MIG-041`.
2. *Historical Origin & Commit Trace*: `ATT-3574` (`showGauge` in `ZoneDisplayOptions`), `ATT-4841` (commit `050f1437`), `ATT-4861` (commit `e3713601`).
3. *Root Reason for Existing Formulation*: `ATT-4841` assumed sensor type alone governed bar feasibility and defaulted `ShowBar = 1` for all HR/Power fields, ignoring legacy `showGauge` and sport validity.
4. *Preservation of Core Invariants*: Mutual exclusion, layout stability, AMOLED compliance, zero empty tiles, and user settings preservation are 100% preserved.

---

## 3. Test Specification (TST-TRK-046)
* **TST-TRK-046.1 (DB Migration)**: `TrackingViewsDatabaseManagerTest` verifies v18 migration re-applies legacy `showGauge` per zone profile, restricts Balance/W'Bal to Bike, enforces `ShowValue = 1` when `ShowBar == 0`, and confirms `DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 18)`.
* **TST-TRK-046.2 (Repository)**: `TrackingViewsRepositoryTest` verifies `showValue`/`showBar` persistence and flow projection.
* **TST-TRK-046.3 (Dialog)**: `EditSensorFieldViewModelTest` verifies `isBarSupported(sensorType, sportType)` and toggle visibility/saving.
* **TST-TRK-046.4 (Fallback)**: `SensorFieldZoneDisplayTest` verifies fallback rendering of value row when `!hasActiveBar`.
* **TST-TRK-046.5 (Localization)**: `TranslationParityTest` verifies element display string parity.
* **TST-TRK-046.6 (Regression)**: Full targeted test suite execution (`ui.tracking.*`, `TrackingViewsDatabaseManagerTest`).

---

## 4. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-046.1` | Migration | `TrackingViewsDatabaseManager.java` | `REQ-TRK-046.1` | Specified |
| `TST-TRK-046.2` | Repo | `TrackingViewsRepository.kt` | `REQ-TRK-046.1` | Specified |
| `TST-TRK-046.3` | ViewModel | `EditSensorFieldViewModel.kt` | `REQ-TRK-046.2` | Specified |
| `TST-TRK-046.4` | UI | `SensorFieldView.kt`, `TrackingViewModel.kt` | `REQ-TRK-046.3`, `REQ-TRK-046.6` | Specified |
| `TST-TRK-046.5` | Localization | `TranslationParityTest.kt` | `REQ-TRK-046.5` | Specified |
| `TST-TRK-046.6` | Regression | Tier 1 targeted suite | `REQ-PRO-001` | Specified |
