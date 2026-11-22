# aTrainingTracker: Living Test Specification & Verification Plan (Master Index)

**Document ID**: `DOC-TST-INDEX`  
**Version**: 1.0.126  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

This master index links to the 13 domain-sharded test specification files under `docs/tests/`.
Living test specifications use multi-line Markdown headings (`### TST-XXX: Title [Status]`) (ATT-4594, `REQ-PRO-028`) and adhere to the `<= 4,000`-line shard cap (`REQ-PRO-093.3`, `REQ-PRO-127.3`).

## Domain Test Shards

| Domain Shard | Scope & Functional Areas | Prefixes | Total | Verified | Implemented | Planned / Open |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| [Tracking & Live Sensor Cockpit](tests/tst_tracking_cockpit.md) | Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle. | `CON`, `FIL`, `FUSION`, `SEN`, `TRK` | 173 | 173 | 0 | 0 |
| [Tracking & Live Sensor Cockpit (Part 2)](tests/tst_tracking_cockpit_part2.md) | Tracking lifecycle, non-blocking shutdown, and sensor connectivity continuation shard. | `CON`, `FIL`, `FUSION`, `SEN`, `TRK` | 1 | 1 | 0 | 0 |
| [Post-Workout Analytics & Periods](tests/tst_aftermath_periods.md) | Post-workout analytics, period statistics, zone distributions, HRV/workout dynamics, radar analytics. | `HRV`, `PER`, `PERF`, `RAD`, `SPT`, `WKO` | 77 | 72 | 5 | 0 |
| [Maps, Polylines & Navigation](tests/tst_maps_navigation.md) | Map rendering, polylines, climbs, navigation overlays, GPX track follow-me, radar map overlays. | `CLM`, `GEO`, `MAP`, `NAV`, `RAD` | 71 | 70 | 1 | 0 |
| [Persistence, Migration & Cloud Export](tests/tst_data_migration_export.md) | Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX/radar export. | `DAT`, `EXP`, `EXT`, `MIG`, `RAD` | 131 | 131 | 0 | 0 |
| [Settings, Profiles & Localization](tests/tst_settings_profiles.md) | Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity. | `LOC`, `PRI`, `SET` | 116 | 112 | 4 | 0 |
| [UI Components & Design System (Part 1)](tests/tst_ui_design_system_part1.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI`, `UNT` | 244 | 226 | 18 | 0 |
| [UI Components & Design System (Part 2)](tests/tst_ui_design_system_part2.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 193 | 193 | 0 | 0 |
| [UI Components & Design System (Part 3)](tests/tst_ui_design_system_part3.md) | Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts. | `UI` | 53 | 52 | 1 | 0 |
| [Engineering Process, Stability & Architecture (Part 1)](tests/tst_process_stability_part1.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | 148 | 140 | 8 | 0 |
| [Engineering Process, Stability & Architecture (Part 2)](tests/tst_process_stability_part2.md) | ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools. | `PRO` | 100 | 92 | 0 | 8 |
| [Jubilee Milestones & App Reviews](tests/tst_aftermath_reviews.md) | Milestone workout auto-naming, jubilee achievements, Google Play in-app reviews, athlete feedback, and supporter tier promotions. | `REV` | 18 | 18 | 0 | 0 |
| [Store Presence](tests/tst_store_presence.md) | Store presence, Google Play listings, screenshots, store metadata contract testing, and release governance. | `STP` | 11 | 11 | 0 | 0 |

**Total Living Tests**: 1336 across 13 domain shards.

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
| `1.0.29` | 2026-11-18 | AI Agent (`ATT-8748`) | `ATT-8748` | Updated `TST-UI-493` [Verified] (`ClusterItemThumbnailComposeTest` + `151/151` PASS + `Rule 8.5` PNG). |
| `1.0.31` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Added `TST-PRO-129` [Verified] (`35/35` Kotlin contract suites + `TestLivingDocDirectoryLayout` `4/4` + `28/28 <= 4000`). |
| `1.0.32` | 2026-11-18 | AI Agent (`ATT-8678`) | `ATT-8678` | Updated `TST-CON-047` & `TST-CON-048` [Verified] (`14` suites / `109/109` PASS, `EditDeviceDialog` `2004->1843` LoC, `Rule 8.5` PNGs). |
| `1.0.33` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Added `TST-UI-2786` [Verified] (`19` XML suites / `90/90` PASS, 9-locale `strings_workout_header.xml`, `Rule 8.5` PNG). |
| `1.0.34` | 2026-11-18 | AI Agent (`ATT-8676`) | `ATT-8676` | Added `TST-TRK-088` [Verified] (`9/9` scoped suites / `51/51` PASS incl. `MyHelperRadarUnitsTest` `7/7`, `SensorTypeUnitsContractTest` `2/2`, governance `exit 0`). |
| `1.0.35` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Added `TST-PRO-130` [Verified] (`47/47` scoped XMLs / `242/242` PASS, `:app:lintDebug` `0` errors, `WorkoutRepository` `5048` LoC, `WorkoutClusterEngine` `1634` LoC / `0` `.ui.*` imports). |
| `1.0.36` | 2026-11-18 | AI Agent (`ATT-8881`) | `ATT-8881` | Added `TST-FIL-026` [Verified] (clean-room `:app:compileDebugKotlin` `0` new warnings & `8/8` XML suites / `67/67` PASS incl. synthetic `Zum Bäcker #57` 5-Hz replay). |
| `1.0.37` | 2026-11-18 | AI Agent (`ATT-8733`) | `ATT-8733` | Added `TST-TRK-089` [Verified] (`RadarEdgeOverlayTest` `37/37` PASS w/ direct pointer pass-through & Frame-1 `.layout`, `:app:lintDebug` `0` errors, `Rule 8.5` Light/Dark/Map PNG). |
| `1.0.38` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Added `TST-PRO-131` [Verified] (`13/13` contract suites / `79/79` PASS, `37/37` `hoechstmass` PASS, `TestGrandfatheredSysreqsRemoved` PASS, `21/21` `SYSREQ` parity). |
| `1.0.39` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Refined `TST-PRO-074` (`[Planned]`, `tst_process_stability_part2.md`) covering `ERROR: Gate violated:` blocking, `cat3`/`cat4` JSON keys, subprocess JSON parsing, and `V5.0.0__273` tag normalization + JUnit lookup. |
| `1.0.40` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Promoted `TST-PRO-074` to `[Implemented]` in Stage 4 (`tools/test_generate_release_verification_summary.py` `22/22` OK, `ReleaseManagerSystemEvidenceContractTest` `4/4` PASS). |
| `1.0.41` | 2026-11-18 | AI Agent (`ATT-8200`) | `ATT-8200` | Promoted `TST-PRO-074` to `[Verified]` after Stage 5 verification (`tools.test_generate_release_verification_summary` `22/22` OK, `ReleaseManagerSystemEvidenceContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.44` | 2026-11-18 | AI Agent (`ATT-8875`) | `ATT-8875` | Promoted `TST-PRO-100` to `[Verified]` after Stage 5 verification (`tools.test_jira_accounts` `102/102` OK, `tools/hoechstmass/tools` `62/62` OK, `tools.test_generate_test_evidence` `8/8` OK, `HoechstmassToolingContractTest` `4/4` PASS, governance `exit 0`). |
| `1.0.45` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Added `TST-FIL-027` [Verified] (Tier 1 targeted `54/54` PASS across 6 suites; monotonic `TIME_TOTAL`, rolling 1.0-s gyro mean, stationary ZUPT/RTS decoupling, `DISTANCE_m_LAP` & stream subsampling, 9-locale `filterSlewOnly()` + `2.0 m` hysteresis). |
| `1.0.46` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Stage 4 Bounce 1/3 heal: added `RADAR`/`YAW`/`NaN` & leading-null/`>=6s`-stall/multi-pause regression tests (`TST-FIL-027` -> `[Implemented]`). |
| `1.0.47` | 2026-11-18 | AI Agent (`ATT-8880`) | `ATT-8880` | Promoted `TST-FIL-027` to `[Verified]` in Stage 5 (Bounce 1/3) on `8a976a35` (`58/58` + `62/62` Tier 1 PASS, governance `exit 0`). |
| `1.0.48` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Added `TST-CON-051` [Implemented] (`DeviceManagerSearchQueueTest`, `BLEReconnectRobustnessTest`, `MyBTLEDeviceAddressAndSearchTest`, `MyBTLEDeviceLifecycleTest`). |
| `1.0.49` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Promoted `TST-CON-051` to `[Verified]` in Stage 5 following targeted unit/contract verification, static baselines, and emulator check. |
| `1.0.50` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Stage 6 Bounce 1/3 heal: added `testSearchTimeout_whenAlreadyConnectedToGatt_abortsWithoutReconnectOrStopSearching` (`TST-CON-051` -> `[Implemented]`). |
| `1.0.51` | 2026-11-19 | AI Agent (`ATT-8998`) | `ATT-8998` | Promoted `TST-CON-051` to `[Verified]` in Stage 5 (Bounce 1/3) (`125/125` `--rerun-tasks` PASS, governance `exit 0`). |
| `1.0.64` | 2026-11-19 | AI Agent (`ATT-8891`, `--as agent1`) | `ATT-8891` | Added & verified `TST-FIL-030` (`[Verified]`, `Total=1272`, `Verified=1058`) reusing Gate 4 Pass (`ATT-9091`: `19` suites / `147/147` PASS). |
| `1.0.67` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Added & verified `TST-FIL-031` (`[Verified]`, `Total=1273`, `Verified=1076`, Bounce 1 healed atop `73ff0cdd`) reusing Gate 4 (`ATT-9113`: `140/140`) + Gate 4 Bounce 1 (`ATT-9128`: `123/123`) PASS evidence. |
| `1.0.112` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Synchronized master index for `TST-SET-128` (`[Planned]`, `Total=1324`, `Verified=1278`, `Implemented=37`, `Planned=9`). |
| `1.0.113` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Synchronized master index for `TST-SET-128` (`[Implemented]`, `Total=1324`, `Verified=1278`, `Implemented=38`, `Planned=8`). |
| `1.0.114` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Synchronized master index for `TST-SET-128` (`[Verified]`, `Total=1324`, `Verified=1279`, `Implemented=37`, `Planned=8`). |
| `1.0.115` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Synchronized master index for `TST-SET-128` Bounce 1 heal (`[Implemented]`, `Total=1324`, `Verified=1278`, `Implemented=38`, `Planned=8`). |
| `1.0.116` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Synchronized master index for `TST-SET-128` Bounce 1 (`[Verified]`, `Total=1324`, `Verified=1279`, `Implemented=37`, `Planned=8`). |
| `1.0.117` | 2026-11-22 | Lane 2 Author (`ATT-9567`, `--as agent1`) | `ATT-9567` | Synchronized master index for `TST-PRO-136` (`[Verified]`, `Total=1326`, `Verified=1281`, `Implemented=37`, `Planned=8`). |
| `1.0.118` | 2026-11-22 | Lane 2 Author (`ATT-9553`, `--as agent1`) | `ATT-9553` | Synchronized master index for `TST-UI-2794` (`[Verified]`, `Total=1328`, `Verified=1283`, `Implemented=37`, `Planned=8`). |
| `1.0.120` | 2026-11-22 | Lane 1 Author (`ATT-6036`, `--as agent1`) | `ATT-6036` | Synchronized master index for `TST-DAT-043` Bounce 1 (`[Verified]`, `Total=1330`, `Verified=1285`, `Implemented=37`, `Planned=8`). |
| `1.0.121` | 2026-11-22 | Lane 2 Author (`ATT-9508`, `--as agent1`) | `ATT-9508` | Synchronized master index for `TST-UI-2795` (`[Verified]`, `Total=1331`, `Verified=1286`, `Implemented=37`, `Planned=8`). |
| `1.0.123` | 2026-11-22 | Lane 3 Author (`ATT-8122`, `--as agent1`) | `ATT-8122` | Synchronized master index for `[Planned]` `TST-MIG-057` (classpath 22-DB schema parity, non-eager quiescence & unmodified `.attbackup` restore cycle). |
| `1.0.124` | 2026-11-22 | Lane 3 Author (`ATT-8122`, `--as agent1`) | `ATT-8122` | Synchronized master index for `[Verified]` `TST-MIG-057` (`94/94` PASS across 19 suites + live `emulator-5554` boot smoke `0` crashes). |
