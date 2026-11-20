# aTrainingTracker: Living Requirements Specification (Master Index)

**Document ID**: `DOC-REQ-INDEX`  
**Version**: 1.0.74  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

This master index links to the 16 domain-sharded requirement files under `docs/requirements/`.
Living requirements use multi-line Markdown headings (`### REQ-XXX: Title [Status]`) to eliminate single-line table bloat (ATT-4594, `REQ-PRO-028`) and adhere to the `<= 4,000`-line shard cap (`REQ-PRO-093.3`, `REQ-PRO-127.3`).

## Domain Requirement Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Implemented | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit (Part 1)](requirements/req_tracking_cockpit_part1.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `SEN`, `TRK`, `WEA` | 109 | 98 | 11 | 0 |
| [Tracking & Live Sensor Cockpit (Part 2)](requirements/req_tracking_cockpit_part2.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `SEN`, `TRK` | 76 | 76 | 0 | 0 |
| [Post-Workout Analytics & Periods](requirements/req_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, HRV/workout dynamics, radar analytics. | `HRV`, `PER`, `RAD`, `SPT`, `WKO` | 66 | 50 | 16 | 0 |
| [Maps, Polylines & Navigation](requirements/req_maps_navigation.md) | Map rendering, polylines, climbs, navigation overlays, GPX track follow-me, radar map overlays. | `CLM`, `GEO`, `MAP`, `NAV`, `RAD` | 76 | 61 | 15 | 0 |
| [Persistence, Migration & Cloud Export (Part 1)](requirements/req_data_migration_export_part1.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX/radar export. | `DAT`, `EXP`, `EXT`, `MIG`, `RAD` | 133 | 117 | 16 | 0 |
| [Persistence, Migration & Cloud Export (Part 2)](requirements/req_data_migration_export_part2.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export. | `EXP` | 2 | 2 | 0 | 0 |
| [Settings, Profiles & Localization](requirements/req_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity. | `LOC`, `PRI`, `SET` | 118 | 63 | 55 | 0 |
| [UI Components & Design System (Part 1)](requirements/req_ui_design_system_part1.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 148 | 87 | 61 | 0 |
| [UI Components & Design System (Part 2)](requirements/req_ui_design_system_part2.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 95 | 93 | 2 | 0 |
| [UI Components & Design System (Part 3)](requirements/req_ui_design_system_part3.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 88 | 88 | 0 | 0 |
| [UI Components & Design System (Part 4)](requirements/req_ui_design_system_part4.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 91 | 91 | 0 | 0 |
| [UI Components & Design System (Part 5)](requirements/req_ui_design_system_part5.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 45 | 45 | 0 | 0 |
| [Engineering Process, Stability & Architecture (Part 1)](requirements/req_process_stability_part1.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `LIV`, `NFR`, `PRO`, `SEC`, `STB`, `TOOL`, `TST` | 125 | 96 | 29 | 0 |
| [Engineering Process, Stability & Architecture (Part 2)](requirements/req_process_stability_part2.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `PRO` | 76 | 75 | 0 | 1 |
| [Jubilee Milestones & App Reviews](requirements/req_aftermath_reviews.md) | Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions. | `REV` | 14 | 14 | 0 | 0 |
| [Store Presence](requirements/req_store_presence.md) | Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance. | `STP` | 11 | 11 | 0 | 0 |

**Total Living Requirements**: 1273 across 16 domain shards.

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
| `1.0.21` | 2026-11-18 | AI Agent (`ATT-8710`) | `ATT-8710` | Added `REQ-HRV-044` / `TST-HRV-044` [Verified] for DFA alpha 1 3-zone graph and scatter coloring. |
| `1.0.23` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Added `REQ-UI-2785` [Verified] (175x120 dp Lieblingsorte map preview) post-`ATT-8714` (`1.0.22`) sync. |
| `1.0.24` | 2026-11-18 | AI Agent (`ATT-8205`) | `ATT-8205` | Added `REQ-PRO-128` [Verified] (hardened `sprint_metrics`, `jira_util`, and Höchstmaß gate/verdict/ADF/risk parity). |
| `1.0.25` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Refined `REQ-PRO-062` (`[Planned]`) in `req_process_stability_part2.md` (`record_review()` human vs AI classification & `sprint_2026-46.4.md`/`sprint_2026-47.1.md` correction). |
| `1.0.26` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Promoted `REQ-PRO-062` to `[Implemented]` in Stage 4 (`tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `sprint_2026-46.4.md`, `sprint_2026-47.1.md`). |
| `1.0.27` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Promoted `REQ-PRO-062` to `[Verified]` after Stage 5 verification (`4/4` ATT-8204 tests PASS, `--check-reviews` `13/13`, governance `exit 0`). |
| `1.0.28` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Updated `REQ-TRK-062` & `REQ-TRK-064` [Verified] (remove green `RadarStatusBadgePill` clear-state circle permanently). |
| `1.0.29` | 2026-11-18 | AI Agent (`ATT-8748`) | `ATT-8748` | Refined `REQ-UI-493` [Verified] (`175x120 dp` Lieblingsstrecken thumbnail + `360.dp` guard + bounds seeding). |
| `1.0.31` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Added `REQ-PRO-129` [Verified] (ADR-001 reconciliation, 16-file God-class ratchet, `workout_repository.md`, layout guard, `tst_process_stability_part{1,2}.md`). |
| `1.0.32` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Updated `REQ-CON-047` & `REQ-CON-048` [Verified] (shared `RadarLightModeGrid` extracted across `RadarLightQuickSwitchSheet` & `EditDeviceDialog`). |
| `1.0.33` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Added `REQ-UI-2786` [Verified] (uniform `32.dp` primary header actions, `[Tune]` pill + divider, universal `⇄` cluster switcher, 9-locale tooltips). |
| `1.0.34` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Added `REQ-TRK-088` [Verified] (`MyHelper.getUnitsId`/`getShortUnitsId` `RADAR_APPROACH_SPEED_mps` unit mapping, `SensorType.java` & `sensor_units.md` sync). |
| `1.0.35` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Added `REQ-PRO-130` [Verified] (SWE.2 heap/cache/map budgets, `LruCache(64)` `recentHrvThresholdsCache`, `ClusterWorkoutDataPort` `IF-SWE-05` decoupling, `10`-file upward-import ratchet). |
| `1.0.36` | 2026-11-18 | AI Agent (`ATT-8881`) | `ATT-8881` | Added `REQ-FIL-026` [Verified] (1-Hz barometric ingestion rate-matching & adaptive cyclic pocket-jitter `varBaro` scaling in `AltitudeAndSlopeDevice_ATTFused` & `KinematicAltitudeSlopeEkf`). |
| `1.0.37` | 2026-11-18 | AI Agent (`ATT-8733`) | `ATT-8733` | Added `REQ-TRK-089` [Verified] (`RadarFullScreenThreatOverlay` non-clipping V2 markers, touch-transparent `Box`, `.layout` clamping, `Color.kt` map-backdrop tokens, `Rule 8.5` PNG). |
| `1.0.38` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Added `REQ-PRO-131` [Verified] (`21/21` `SYSREQ-*` covering Polar `8.6.0`/DEM/Maps/IMU/Radar/Swim/HRV/Recovery, `SYS.3` v1.2.1 sync, `GRANDFATHERED_SYSREQS` removed, Stage 1/2 `SYS.2/SYS.3` impact gate). |
| `1.0.39` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Refined `REQ-PRO-074` (`[Planned]`, `req_process_stability_part2.md`) for `ERROR:`/`FAIL:` threshold gating, `category_3_code_tags_without_spec`/`category_4_tsts_not_cited_back` keys, subprocess JSON parsing, and `V<ver>__<code>` dossier normalization. |
| `1.0.40` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Promoted `REQ-PRO-074` to `[Implemented]` in Stage 4 (`tools/generate_release_verification_summary.py`, `docs/releases/README.md`, `tools/test_generate_release_verification_summary.py`). |
| `1.0.41` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Promoted `REQ-PRO-074` to `[Verified]` after Stage 5 verification (`tools.test_generate_release_verification_summary` `22/22` OK, `ReleaseManagerSystemEvidenceContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.44` | 2026-11-18 | AI Agent (`ATT-8875`) | `ATT-8875` | Promoted `REQ-PRO-100` to `[Verified]` after Stage 5 verification (`tools.test_jira_accounts` `102/102` OK, `tools/hoechstmass/tools` `62/62` OK, `tools.test_generate_test_evidence` `8/8` OK, `HoechstmassToolingContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.45` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Added `REQ-FIL-027` [Verified] (monotonic `TIME_TOTAL` timestamp reconstruction, rolling 1.0-s gyro averaging, stationary ZUPT/RTS decoupling, `DISTANCE_m_LAP` & stream subsampling parity, 9-locale `filterSlewOnly()` + `2.0 m` hysteresis). |
| `1.0.46` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Stage 4 Bounce 1/3 heal: wheel-speed column match hardening, `.isFinite()` sanitization, `baseEpochMs`/`wallJumpPastTimeTotal` re-anchoring (`REQ-FIL-027` -> `[Implemented]`). |
| `1.0.47` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `REQ-FIL-027` to `[Verified]` in Stage 5 (Bounce 1/3) on `8a976a35` (`58/58` + `62/62` Tier 1 PASS, governance `exit 0`). |
| `1.0.48` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Added `REQ-CON-051` [Implemented] (non-blocking BLE search-queue timeout handoff & decoupled mid-session background reconnect, `DeviceManager.java` `1,707` LoC). |
| `1.0.49` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Promoted `REQ-CON-051` to `[Verified]` in Stage 5 following targeted unit/contract verification, static baselines, and emulator check. |
| `1.0.50` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Stage 6 Bounce 1/3 heal: guarded `mSearchTimeoutRunnable` on `CONNECTED_TO_GATT`/`CONNECTED_WITH_SERVICE` (`REQ-CON-051` -> `[Implemented]`). |
| `1.0.51` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Promoted `REQ-CON-051` to `[Verified]` in Stage 5 (Bounce 1/3) (`125/125` `--rerun-tasks` PASS, governance `exit 0`). |
| `1.0.64` | 2026-11-19 | AI Agent (`ATT-8891`, `--as agent1`) | `ATT-8891` | Added & verified `REQ-FIL-030` (`[Verified]`, `Total=1265`, `Verified=1023`) for kinematic `BSportType` inference & moving-speed sport identity. |
| `1.0.67` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Added & verified `REQ-FIL-031` (`[Verified]`, `Total=1266`, `Verified=1036`, Bounce 1 healed atop `73ff0cdd`) for triathlon/duathlon/brick multisport transition detection, retroactive anchoring & automatic named laps. |
