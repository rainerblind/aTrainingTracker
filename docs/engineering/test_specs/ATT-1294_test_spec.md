# Stage 2: Requirement & Test Specification - ATT-1294: Live Sparkline Graph Tiles & Time Horizon

**Ticket**: [ATT-1294](https://rainerblind.atlassian.net/browse/ATT-1294) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-1294`  
**Requirement Mapping**: `REQ-TRK-059` (`docs/requirements/req_tracking_cockpit.md`)  
**Test Spec ID**: `TST-TRK-059` (`docs/tests/tst_tracking_cockpit.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TRK-059)

### 1.1 Problem Statement & Rationale
Athletes need continuous temporal sparkline trends within cockpit tiles across all numeric metrics with selectable horizons (1m to Full Workout) to monitor pacing trends without cognitive overload or battery impact.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-059.1** (*DB v19 Persistence & Migration*): Increment `TrackingViewsDatabaseManager` to `DB_VERSION = 19`, adding `ShowGraph INTEGER DEFAULT 0` and `GraphHorizonS INTEGER DEFAULT 300` to `ROWS_TABLE` with complete backward compatibility.
* **REQ-TRK-059.2** (*Numeric Metric Support Gating*): `TrackingViewsDbHelper.isGraphSupported(sensorType)` SHALL return `true` for all numeric metrics (`DOUBLE`/`INTEGER`), and `false` for strings, timers, and lat/lon coordinates.
* **REQ-TRK-059.3** (*Edit Dialog Configuration*): In `EditSensorFieldDialog.kt`, surface a `Graph` chip when supported and a `Time Horizon` selector with 7 durations: 1m (60s), 2m (120s), 5m (300s), 10m (600s), 20m (1200s), 1h (3600s), Full Workout (0s).
* **REQ-TRK-059.4** (*Live Sparkline Buffer Engine*): In-memory `LiveSparklineBuffer` maintains circular ring buffers for 1m–1h and pairwise decimation for Full Workout ($\le 300$ samples, $O(1)$ sample additions, $< 1\text{ KB}$ memory).
* **REQ-TRK-059.5** (*Sparkline Rendering & Composition Safety*): `SensorFieldView.kt` renders `SensorSparklineView` below value or as full tile with zero Compose snapshot writes during composition (`Rule 66`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* DB v18, *When* upgraded to v19, *Then* `ShowGraph` (0) and `GraphHorizonS` (300) are added without data loss.
* *Given* a numeric sensor (HR, Power), *When* editing tile, *Then* `Graph` chip and 7 horizons appear.
* *Given* a non-numeric sensor (Time, Position), *When* editing tile, *Then* `Graph` chip is hidden.
* *Invariants*: Existing display elements (`showValue`, `showBar`) and gauge bar rendering preserved.

---

## 2. 9-Language Localization Plan (`strings_cockpit.xml`)

| Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `sensor_field_show_graph` | Graph | Diagramm | Gráfico | Graphique | Grafico | グラフ | Grafiek | Wykres | Gráfico |
| `sensor_field_graph_horizon` | Time horizon | Zeithorizont | Horizonte temporal | Horizon temporel | Orizzonte temporale | タイムホライズン | Tijdshorizon | Horyzont czasowy | Horizonte temporal |
| `graph_horizon_1m` | 1 min | 1 Min. | 1 min | 1 min | 1 min | 1分 | 1 min | 1 min | 1 min |
| `graph_horizon_2m` | 2 min | 2 Min. | 2 min | 2 min | 2 min | 2分 | 2 min | 2 min | 2 min |
| `graph_horizon_5m` | 5 min | 5 Min. | 5 min | 5 min | 5 min | 5分 | 5 min | 5 min | 5 min |
| `graph_horizon_10m` | 10 min | 10 Min. | 10 min | 10 min | 10 min | 10分 | 10 min | 10 min | 10 min |
| `graph_horizon_20m` | 20 min | 20 Min. | 20 min | 20 min | 20 min | 20分 | 20 min | 20 min | 20 min |
| `graph_horizon_1h` | 1 hour | 1 Stunde | 1 hora | 1 heure | 1 ora | 1時間 | 1 uur | 1 godzina | 1 hora |
| `graph_horizon_full` | Full Workout | Gesamtes Training | Entrenamiento completo | Entraînement complet | Allenamento completo | ワークアウト全体 | Volledige training | Cały trening | Treino completo |

---

## 3. Test Specification (TST-TRK-059) & Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-059.1` | Unit | `TrackingViewsDbMigrationV19Test.kt` | `REQ-TRK-059.1` | Planned |
| `TST-TRK-059.2` | Unit | `TrackingViewsSparklineSupportTest.kt` | `REQ-TRK-059.2` | Planned |
| `TST-TRK-059.3` | Unit | `LiveSparklineBufferTest.kt` | `REQ-TRK-059.4` | Planned |
| `TST-TRK-059.4` | Contract | `EditSensorFieldDialogSparklineTest.kt`, `TranslationParityTest.kt` | `REQ-TRK-059.3` | Planned |
| `TST-TRK-059.5` | Regression | `database.*`, `ui.tracking.*` | `REQ-PRO-001`, `REQ-TRK-059.5` | Planned |
