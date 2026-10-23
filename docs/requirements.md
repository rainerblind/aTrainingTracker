# aTrainingTracker: Living Requirements Specification (Master Index)

This master index links to the 7 domain-sharded requirement files under `docs/requirements/`.
Living requirements use multi-line Markdown headings (`### REQ-XXX: Title [Status]`) to eliminate single-line table bloat (ATT-4594, `REQ-PRO-028`).

## Domain Requirement Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit](requirements/req_tracking_cockpit.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `FUSION`, `SEN`, `STP`, `TRK`, `WEA` | 80 | 27 | 53 |
| [Post-Workout Analytics & Periods](requirements/req_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers. | `PER`, `PERF` | 16 | 9 | 7 |
| [Maps, Polylines & Navigation](requirements/req_maps_navigation.md) | Map rendering, polylines, navigation overlays, GPX track follow-me, and routing. | `GEO`, `MAP`, `NAV`, `ROU` | 57 | 17 | 40 |
| [Persistence, Migration & Cloud Export](requirements/req_data_migration_export.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export. | `DAT`, `EXP`, `EXT`, `MIG` | 119 | 44 | 75 |
| [Settings, Profiles & Localization](requirements/req_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, and 9-locale parity. | `LOC`, `PRI`, `SET` | 83 | 55 | 28 |
| [UI Components & Design System](requirements/req_ui_design_system.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI`, `UNT` | 367 | 57 | 310 |
| [Engineering Process, Stability & Architecture](requirements/req_process_stability.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | 71 | 19 | 52 |

**Total Living Requirements**: 793 across 7 domain shards.

---

## Developer & AI Agent Guidelines
* **Reading Living Requirements**: View the relevant domain shard directly (`view_file` on `docs/requirements/req_<domain>.md`), keeping each file small (~100–350 KB) and well within tool output limits.
* **Adding / Modifying Requirements**: Append or amend requirements using the standard multi-line section format:
  ```markdown
  ### REQ-DOM-NNN: Formal Title [Status]
  - **Tests**: `TST-DOM-NNN` | **Tickets**: `ATT-XXXX`
  - **Implementation**: `TargetComponent.kt`
  - **Rationale**: Architectural justification...
  - **Description**: Normative contract specification...
  ```
* **Chesterton's Fence Archaeology**: When modifying an existing requirement, ensure the corresponding Stage 2 specification contains all 4 mandatory archaeology fields (`verify_requirement_governance.py`).
