# aTrainingTracker: Living Test Specification & Verification Plan (Master Index)

This master index links to the 7 domain-sharded test specification files under `docs/tests/`.
Living test specifications use multi-line Markdown headings (`### TST-XXX: Title [Status]`) (ATT-4594, `REQ-PRO-028`).

## Domain Test Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit](tests/tst_tracking_cockpit.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `FUSION`, `SEN`, `STP`, `TRK`, `WEA` | 54 | 26 | 28 |
| [Post-Workout Analytics & Periods](tests/tst_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, graphs and visual scrubbers. | `PER`, `PERF` | 23 | 1 | 22 |
| [Maps, Polylines & Navigation](tests/tst_maps_navigation.md) | Map rendering, polylines, navigation overlays, GPX track follow-me, and routing. | `GEO`, `MAP`, `NAV`, `ROU` | 52 | 5 | 47 |
| [Persistence, Migration & Cloud Export](tests/tst_data_migration_export.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export. | `DAT`, `EXP`, `EXT`, `MIG` | 106 | 28 | 78 |
| [Settings, Profiles & Localization](tests/tst_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, and 9-locale parity. | `LOC`, `PRI`, `SET` | 69 | 1 | 68 |
| [UI Components & Design System](tests/tst_ui_design_system.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI`, `UNT` | 388 | 61 | 327 |
| [Engineering Process, Stability & Architecture](tests/tst_process_stability.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | 100 | 17 | 83 |

**Total Living Tests**: 792 across 7 domain shards.

---

## Verification Procedure
* **Unit Tests**: Targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.<package>.*"` during Stages 4 & 5; full suite at Sprint Integration Gate.
* **Requirement Governance**: Enforced via `python3 tools/verify_requirement_governance.py`.
* **Auto-Verification**: Flipped from `Planned` to `Verified` automatically on Stage 5 submission (`auto_verify_living_docs_on_stage5`).
