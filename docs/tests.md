# aTrainingTracker: Living Test Specification & Verification Plan (Master Index)

**Document ID**: `DOC-TST-INDEX`  
**Version**: 1.0.28  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

This master index links to the 11 domain-sharded test specification files under `docs/tests/`.
Living test specifications use multi-line Markdown headings (`### TST-XXX: Title [Status]`) (ATT-4594, `REQ-PRO-028`) and adhere to the `<= 4,000`-line shard cap (`REQ-PRO-093.3`, `REQ-PRO-127.3`).

## Domain Test Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Implemented | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit](tests/tst_tracking_cockpit.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `FUSION`, `SEN`, `TRK` | 148 | 145 | 3 | 0 |
| [Post-Workout Analytics & Periods](tests/tst_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, HRV/workout dynamics, radar analytics. | `HRV`, `PER`, `PERF`, `RAD`, `SPT`, `WKO` | 67 | 48 | 19 | 0 |
| [Maps, Polylines & Navigation](tests/tst_maps_navigation.md) | Map rendering, polylines, climbs, navigation overlays, GPX track follow-me, radar map overlays. | `CLM`, `GEO`, `MAP`, `NAV`, `RAD` | 70 | 62 | 8 | 0 |
| [Persistence, Migration & Cloud Export](tests/tst_data_migration_export.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX/radar export. | `DAT`, `EXP`, `EXT`, `MIG`, `RAD` | 122 | 97 | 25 | 0 |
| [Settings, Profiles & Localization](tests/tst_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity. | `LOC`, `PRI`, `SET` | 106 | 63 | 43 | 0 |
| [UI Components & Design System (Part 1)](tests/tst_ui_design_system_part1.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI`, `UNT` | 249 | 182 | 67 | 0 |
| [UI Components & Design System (Part 2)](tests/tst_ui_design_system_part2.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 192 | 192 | 0 | 0 |
| [UI Components & Design System (Part 3)](tests/tst_ui_design_system_part3.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 38 | 38 | 0 | 0 |
| [Engineering Process, Stability & Architecture](tests/tst_process_stability.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | 232 | 175 | 49 | 8 |
| [Jubilee Milestones & App Reviews](tests/tst_aftermath_reviews.md) | Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions. | `REV` | 15 | 15 | 0 | 0 |
| [Store Presence](tests/tst_store_presence.md) | Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance. | `STP` | 11 | 11 | 0 | 0 |

**Total Living Tests**: 1250 across 11 domain shards.

---

## Verification Procedure
* **Unit Tests**: Targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.<package>.*"` during Stages 4 & 5; full suite at Sprint Integration Gate.
* **Kover Code Coverage Floors (`app/build.gradle`, REQ-PRO-089 / ATT-7563)**:
  * Global verification floor: $\ge 40\%$ line coverage, $\ge 35\%$ branch coverage.
  * Dedicated subsystem bounds:
    * Tracking (`com.atrainingtracker.trainingtracker.tracker`): $\ge 55\%$ line, $\ge 45\%$ branch.
    * Filters (`com.atrainingtracker.banalservice.filters`): $\ge 70\%$ line, $\ge 50\%$ branch.
    * Sensors & Devices (`com.atrainingtracker.banalservice.devices`, `com.atrainingtracker.banalservice.sensor`): $\ge 60\%$ line, $\ge 50\%$ branch.
  * Verified via `./gradlew koverVerifyDebug`.
* **Requirement Governance**: Enforced via `python3 tools/verify_requirement_governance.py`.
* **Auto-Verification**: Status promotion to `Verified` on Stage 5 submission (`auto_verify_living_docs_on_stage5`) is conditioned on machine-readable test results and backing test evidence (REQ-PRO-099); unbacked or failing items remain `[Implemented]`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-TST-INDEX` work-product control header and Change History. |
| `1.0.6` | 2026-11-17 | AI Agent (`ATT-8168`) | `ATT-8168` | Promoted `TST-PRO-122` to `[Verified]` and synced shard counts. |
| `1.0.20` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Reconciled master tests index across `27` <=4000-line split shards (`1239` REQs / `1246` TSTs) post `ATT-8717`/`ATT-8712` integration. |
| `1.0.21` | 2026-11-18 | AI Agent (`ATT-8710`) | `ATT-8710` | Added `REQ-HRV-044` / `TST-HRV-044` [Verified] for DFA alpha 1 3-zone graph and scatter coloring. |
| `1.0.23` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Added `TST-UI-2785` [Verified] (175x120 dp Lieblingsorte map preview) post-`ATT-8714` (`1.0.22`) sync. |
| `1.0.24` | 2026-11-18 | AI Agent (`ATT-8205`) | `ATT-8205` | Added `TST-PRO-128` [Verified] (25 root + 20 Höchstmaß metrics, ADF parent, role & negation regression suite). |
| `1.0.25` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Refined `TST-PRO-062` (`[Planned]`) in `tst_process_stability.md` (`record_review()` human vs AI classification, Höchstmaß parity, & review file fixes). |
| `1.0.26` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Promoted `TST-PRO-062` to `[Implemented]` in Stage 4 (`tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/test_verify_requirement_governance.py`). |
| `1.0.27` | 2026-11-18 | AI Agent (`ATT-8204`) | `ATT-8204` | Promoted `TST-PRO-062` to `[Verified]` after Stage 5 verification (`4/4` ATT-8204 tests PASS, `TST-PRO-062` evidence `True`, `3979/4000` lines, governance `exit 0`). |
| `1.0.28` | 2026-11-18 | AI Agent (`ATT-8679`) | `ATT-8679` | Updated `TST-TRK-062` & `TST-TRK-064` [Verified] (`126/126` unit/Compose UI PASS, `:app:lintDebug` `0` errors, `Rule 8.5` PNG). |
