# aTrainingTracker: Living Requirements Specification (Master Index)

**Document ID**: `DOC-REQ-INDEX`  
**Version**: 1.0.20  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

This master index links to the 16 domain-sharded requirement files under `docs/requirements/`.
Living requirements use multi-line Markdown headings (`### REQ-XXX: Title [Status]`) to eliminate single-line table bloat (ATT-4594, `REQ-PRO-028`) and adhere to the `<= 4,000`-line shard cap (`REQ-PRO-093.3`, `REQ-PRO-127.3`).

## Domain Requirement Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Implemented | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit (Part 1)](requirements/req_tracking_cockpit_part1.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `SEN`, `TRK`, `WEA` | 109 | 95 | 14 | 0 |
| [Tracking & Live Sensor Cockpit (Part 2)](requirements/req_tracking_cockpit_part2.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `SEN`, `TRK` | 62 | 62 | 0 | 0 |
| [Post-Workout Analytics & Periods](requirements/req_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, HRV/workout dynamics, radar analytics. | `HRV`, `PER`, `RAD`, `SPT`, `WKO` | 60 | 38 | 22 | 0 |
| [Maps, Polylines & Navigation](requirements/req_maps_navigation.md) | Map rendering, polylines, climbs, navigation overlays, GPX track follow-me, radar map overlays. | `CLM`, `GEO`, `MAP`, `NAV`, `RAD` | 75 | 57 | 18 | 0 |
| [Persistence, Migration & Cloud Export (Part 1)](requirements/req_data_migration_export_part1.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX/radar export. | `DAT`, `EXP`, `EXT`, `MIG`, `RAD` | 133 | 93 | 40 | 0 |
| [Persistence, Migration & Cloud Export (Part 2)](requirements/req_data_migration_export_part2.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export. | `EXP` | 2 | 2 | 0 | 0 |
| [Settings, Profiles & Localization](requirements/req_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity. | `LOC`, `PRI`, `SET` | 117 | 62 | 55 | 0 |
| [UI Components & Design System (Part 1)](requirements/req_ui_design_system_part1.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 148 | 85 | 63 | 0 |
| [UI Components & Design System (Part 2)](requirements/req_ui_design_system_part2.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 95 | 93 | 2 | 0 |
| [UI Components & Design System (Part 3)](requirements/req_ui_design_system_part3.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 88 | 88 | 0 | 0 |
| [UI Components & Design System (Part 4)](requirements/req_ui_design_system_part4.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 91 | 91 | 0 | 0 |
| [UI Components & Design System (Part 5)](requirements/req_ui_design_system_part5.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 42 | 42 | 0 | 0 |
| [Engineering Process, Stability & Architecture (Part 1)](requirements/req_process_stability_part1.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `LIV`, `NFR`, `PRO`, `SEC`, `STB`, `TOOL`, `TST` | 125 | 96 | 29 | 0 |
| [Engineering Process, Stability & Architecture (Part 2)](requirements/req_process_stability_part2.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `PRO` | 67 | 66 | 0 | 1 |
| [Jubilee Milestones & App Reviews](requirements/req_aftermath_reviews.md) | Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions. | `REV` | 14 | 14 | 0 | 0 |
| [Store Presence](requirements/req_store_presence.md) | Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance. | `STP` | 11 | 11 | 0 | 0 |

**Total Living Requirements**: 1239 across 16 domain shards.

---

## Developer & AI Agent Guidelines
* **Reading Living Requirements**: View the relevant domain shard directly (`view_file` on `docs/requirements/req_<domain>.md`), keeping each file small (~100–350 KB) and well within tool output limits.
* **Adding / Modifying Requirements**: Append or amend requirements using the standard multi-line section format:
  ```markdown
  ### REQ-DOM-NNN: Formal Title [Status]
  - **Tests**: `TST-DOM-NNN` | **Tickets**: `ATT-XXXX`
  - **Priority**: Critical / High / Medium / Low
  - **Type**: Functional / Non-Functional
  - **Verification Method**: Unit / Integration / On-Device Manual / Review / Formal Analysis
  - **Implementation**: `TargetComponent.kt`
  - **Rationale**: Architectural justification...
  - **Description**: Normative contract specification...
  ```
* **Chesterton's Fence Archaeology**: When modifying an existing requirement, ensure the corresponding Stage 2 specification contains all 4 mandatory archaeology fields (`verify_requirement_governance.py`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-REQ-INDEX` work-product control header and Change History. |
| `1.0.6` | 2026-11-17 | AI Agent (`ATT-8168`) | `ATT-8168` | Promoted `REQ-PRO-122` to `[Verified]` and synced shard counts. |
| `1.0.20` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Reconciled master requirements index across `27` <=4000-line split shards (`1239` REQs / `1246` TSTs) post `ATT-8717`/`ATT-8712` integration. |
