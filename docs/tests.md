# aTrainingTracker: Living Test Specification & Verification Plan (Master Index)

This master index links to the 9 domain-sharded test specification files under `docs/tests/`.
Living test specifications use multi-line Markdown headings (`### TST-XXX: Title [Status]`) (ATT-4594, `REQ-PRO-028`).

## Domain Test Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Implemented | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit](tests/tst_tracking_cockpit.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA` | 109 | 106 | 0 | 3 |
| [Post-Workout Analytics & Periods](tests/tst_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers. | `PER`, `PERF`, `SPT` | 51 | 51 | 0 | 0 |
| [Maps, Polylines & Navigation](tests/tst_maps_navigation.md) | Map rendering, polylines, navigation overlays, GPX track follow-me, and routing. | `GEO`, `MAP`, `NAV`, `ROU` | 70 | 70 | 0 | 0 |
| [Persistence, Migration & Cloud Export](tests/tst_data_migration_export.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export. | `DAT`, `EXP`, `EXT`, `MIG` | 119 | 119 | 0 | 0 |
| [Settings, Profiles & Localization](tests/tst_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity. | `LOC`, `MON`, `PRI`, `SET` | 91 | 91 | 0 | 0 |
| [UI Components & Design System](tests/tst_ui_design_system.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI`, `UNT` | 471 | 471 | 0 | 0 |
| [Engineering Process, Stability & Architecture](tests/tst_process_stability.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | 179 | 178 | 1 | 0 |
| [Jubilee Milestones & App Reviews](tests/tst_aftermath_reviews.md) | Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions. | `REV` | 14 | 14 | 0 | 0 |
| [Store Presence](tests/tst_store_presence.md) | Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance. | `STP` | 10 | 10 | 0 | 0 |

**Total Living Tests**: 1114 across 9 domain shards.

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
* **Auto-Verification**: Flipped from `Planned` to `Verified` automatically on Stage 5 submission (`auto_verify_living_docs_on_stage5`).
