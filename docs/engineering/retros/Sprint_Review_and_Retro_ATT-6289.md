# Sprint Review & Retrospective: Sprint `2026-46.1` (`ATT-6289`)

- **Sprint**: `2026-46.1` (Jira Sprint ID `438`)
- **Retrospective Ticket**: `ATT-6289`
- **Target Release**: `V5.0.0`
- **Sprint Branch**: `sprint/2026-46.1`

---

## 1. Sprint Velocity & Scope Summary (`REQ-PRO-029`)

| Metric | Value | Notes |
| :--- | :---: | :--- |
| **Total Sprint Queue (`Rule 1` Deviations #1 & #2)** | **112 Tickets (`254.0 SP`)** | Multi-wave backlog queue (`+ ATT-6289 = 113` sprint issues) executed under `Concurrency = 4` `Workspace: "share"` pilot |
| **Completed & Jointly Reviewed Tickets** | **66 Tickets (`146.0 SP`)** | **`66 / 66` (`100.0%`) accepted as `i.O.`** (`FixVersion = V5.0.0`) |
| **Untouched `Zu erledigen` Rollover (`Rule 78`)** | **46 Tickets (`108.0 SP`)** | Kept cleanly in `Zu erledigen` per Just-In-Time `Analysis` transition (`Rule 78`) for seamless rollover |
| **Follow-Up Backlog Tickets Created** | **62 Tickets** | `ATT-6680` through `ATT-6741` (all ranked at top of backlog, zero `FixVersion`) |

---

## 2. Quantitative Sprint Process Metrics (`PA 4.1` & `PA 4.2`)

- **Ticket Throughput**: `66/66` reviewed & accepted (`58.4%` raw board ratio `66/113` including 46 untouched `Zu erledigen` rollover tickets and open ceremony ticket `ATT-6289`) | **Velocity**: **`146.0 SP`**
- **Overall Gate 1–5 FTPR**: **`100.0%`** across `330` stage sub-tasks (`0` revisions)
  - **Gate 1 (`Analysis`)**: `100.0%` (`66/66`)
  - **Gate 2 (`Req & Test Spec`)**: `100.0%` (`66/66`)
  - **Gate 3 (`Implementation Plan`)**: `100.0%` (`66/66`)
  - **Gate 4 (`Implementation`)**: `100.0%` (`66/66`)
  - **Gate 5 (`Verification`)**: `100.0%` (`66/66`)
- **Empirical Swiss Cheese Escape Rates**: $p_1=0.0, p_2=0.0, p_3=0.0, p_4=0.0, p_5=0.0 \rightarrow P(\text{escaped defect}) = 3.2 \times 10^{-9}$
- **SPC Control Bands (`PA 4.2`, Sample Size: 6 Sprints)**:
  - `OVERALL_FTPR`: $\mu = 85.75\%, \sigma = 15.76\% \rightarrow [54.23\%, 117.27\%]$ (**In Control**: `100.0%`)
  - `HUMAN_ACCEPTANCE` (raw board ratio): $\mu = 85.30\%, \sigma = 17.82\% \rightarrow [49.66\%, 120.94\%]$ (**In Control**: `58.4%`; **`100.0%` on reviewed tickets**)
  - `COMPLETED_TICKETS`: $\mu = 31.33, \sigma = 17.94 \rightarrow [0.00, 67.21]$ (**In Control**: `66`)
  - `COMPLETED_SP`: $\mu = 36.50, \sigma = 53.84 \rightarrow [0.00, 144.17]$ (**Positive Special Cause Variation**: `146.0 SP` > `144.17 SP`)
- **Tier 2 Sprint Integration & CI Static-Analysis Gate**: **`100.0% PASS`** (`5,294 / 5,294` unit tests in `testDebugUnitTest` with `0` failures, `:app:lintDebug` `0` errors, `:app:detekt` `0` issues, `:app:compileDebugAndroidTestKotlin` `PASS`, `verify_requirement_governance.py` `PASS`, `assembleDebug` `PASS`)

### PA 4.2 Special Cause Variation & Tooling Pagination RCA
1. **Positive Velocity Surge (`COMPLETED_SP = 146.0 SP` > UCL `144.17 SP` / prior 5-sprint UCL `67.7 SP`)**:
   - **Root Cause**: Human-approved `Rule 1` Deviations #1 & #2 scaled execution to `Concurrency = 4` isolated `Workspace: "share"` Git worktrees across disjoint Epics (`Cycling Radar`, `HRV & Readiness`, `Cardio-Power Dynamics`, `Repository Migration`, and `ASPICE CL2 Governance`) over a multi-wave sprint run, nearly tripling previous peak Story Point throughput (`58.0 SP` $\rightarrow$ `146.0 SP`).
   - **Quality Trade-Off Assessment & Action**: While `Concurrency = 4` achieved `146.0 SP` and `100%` Gate 1–5 FTPR, Ceremony 2 Joint Review uncovered `62` follow-up tickets (`ATT-6680`–`ATT-6741`) concentrated in cross-ticket integration seams (unmounted UI/settings composables, parallel Lint/Detekt/Repository collisions, and unverified class/ID citations in `SWE.2`/`SYS.2`/`SYS.3` docs) and created a multi-hour serial review bottleneck at Sprint-End. **Rule 81** directly hardens Gate 3–5 and Tier 2 against these exact failure classes and introduces concurrent human evaluation during Phase 2.
2. **Jira Agile API `maxResults=100` Pagination Fix (`tools/jira_util.py`)**:
   - **Root Cause**: Sprint `438` contained `113` parent issues, causing unpaginated `/rest/agile/1.0/sprint/{id}/issue` calls in `collect_sprint_metrics_for_id` and `cmd_sprint_metrics` to inspect only the first 100 issues (`62` `Erledigt`) and miss `ATT-6289` at rank 113.
   - **Countermeasure Applied**: Added `fetch_all_sprint_issues()` (`startAt` pagination loop) to `tools/jira_util.py`, recording the exact `66/113` (`146.0 SP`, `330` sub-tasks) scorecard in `process_metrics.jsonl` and posting directly to `ATT-6289`.

---

## 3. Joint Sprint Review Verdict Log (`66 / 66 Accepted as i.O.`)

| # | Key | Summary | Epic | SP | Commit | Verdict | Follow-Up Ticket(s) |
| :-: | :--- | :--- | :-: | :-: | :---: | :---: | :--- |
| 1 | **`ATT-6256`** | `[High] SpeedAndLocationDevice` (Google Fused): Prevent speed toggling between `0.0 km/h` and plausible speed | `ATT-166` | 2 | `196b3e67` | **`i.O.`** (`V5.0.0`) | **`ATT-6682`**, **`ATT-6683`**, **`ATT-6684`** |
| 2 | **`ATT-6265`** | `AltitudeFromPressureDevice`: Non-linear outlier-rejecting filter & retroactive DB column sync | `ATT-166` | 2 | `e079f7a9` | **`i.O.`** (`V5.0.0`) | — |
| 3 | **`ATT-6264`** | `MySensorManager` & `AltitudeFromPositionDevice`: Prioritize DEM altitude above barometric while disabled by default | `ATT-166` | 1 | `e7a74a6e` | **`i.O.`** (`V5.0.0`) | — |
| 4 | **`ATT-6165`** | `[P1] SegmentsRepository` & `SegmentListViewModel`: Catch uncaught exceptions during Strava segment sync | `ATT-6033` | 2 | `1fefffbd` | **`i.O.`** (`V5.0.0`) | — |
| 5 | **`ATT-6162`** | `[P1] BLE CSC/CPS`: Fix 16-bit crank revolution rollover causing negative cadence spikes | `ATT-6033` | 2 | `18d6297d` | **`i.O.`** (`V5.0.0`) | — |
| 6 | **`ATT-6159`** | `[P1] WorkoutClusterEngine.moveWorkoutToCluster`: Fix stale-copy & DB write order overwriting centroid/sport | `ATT-6033` | 2 | `07f4fcac` | **`i.O.`** (`V5.0.0`) | — |
| 7 | **`ATT-6290`** | `SupporterScreen` (`PioneerStatusBanner`): Replace `'early ANT+ days'` wording with neutral appreciation | `ATT-355` | 1 | `84183fb2` | **`i.O.`** (`V5.0.0`) | **`ATT-6680`** |
| 8 | **`ATT-6282`** | `DisplaySettingsDialog`: Add descriptive subtitles for top three display toggles | `ATT-355` | 1 | `6753b825` | **`i.O.`** (`V5.0.0`) | **`ATT-6681`** |
| 9 | **`ATT-6323`** | `SupporterPlanCard`: Prevent single-letter line wrap on `'Bronze-Unterstützer'` with `'Wechseln'` button | `ATT-355` | 1 | `65866477` | **`i.O.`** (`V5.0.0`) | — |
| 10 | **`ATT-6322`** | `[High] WorkoutDetailTabbedContent` & `WorkoutShareBottomSheet`: Fix 50% tab transition, duplicate header & restore | `ATT-68` | 2 | `4fb53a57` | **`i.O.`** (`V5.0.0`) | **`ATT-6685`** |
| 11 | **`ATT-6321`** | `KnownLocationCard` (*Lieblingsorte*): Filter workout list by clicked `BSportType` badge | `ATT-1396` | 1 | `c209aef0` | **`i.O.`** (`V5.0.0`) | — |
| 12 | **`ATT-6320`** | `KnownLocations` & `MapScreenWithTrack`: Centered Material heart marker on detail view & general map | `ATT-1396` | 1 | `aac07440` | **`i.O.`** (`V5.0.0`) | — |
| 13 | **`ATT-6319`** | `[High] WorkoutClusterEngine` & `LegacyImportEngine`: Migrate existing `A->B` apex coordinates & unify import | `ATT-176` | 2 | `8938afbb` | **`i.O.`** (`V5.0.0`) | **`ATT-6686`** |
| 14 | **`ATT-6318`** | `WorkoutClusterSplitSheet`: Embed color-coded map preview directly above corridor sensitivity slider | `ATT-176` | 2 | `66dd7cf7` | **`i.O.`** (`V5.0.0`) | — |
| 15 | **`ATT-6046`** | `[P1] BANALService` (ANT+ & BLE): Fix PCC handle leak, pedal smoothness bug, thread leak & filter seeding | `ATT-6033` | 2 | `80991160` | **`i.O.`** (`V5.0.0`) | — |
| 16 | **`ATT-6381`** | `[High] BTLERunSpeedDevice`: Fix dead `mTotalDistance` state, inverted reset condition & `mAddress` shadowing | `ATT-6033` | 1 | `a8ec34d8` | **`i.O.`** (`V5.0.0`) | — |
| 17 | **`ATT-6382`** | `[High] MaxValueFilter` & `ExponentialSmoothingFilter`: Fix unbounded `LinkedList` leak, thread safety & alpha | `ATT-6033` | 2 | `a3af5f50` | **`i.O.`** (`V5.0.0`) | — |
| 18 | **`ATT-6044`** | `[P1] ZoneDistributionCards`: Consolidate Power, HeartRate, and Grade distribution cards into generic component | `ATT-6033` | 1 | `da7f2ae0` | **`i.O.`** (`V5.0.0`) | — |
| 19 | **`ATT-1298`** | `[Cockpit]` Live Mean Maximal Power (MMP) curve chart and rolling/peak duration metrics (`5s..20m`) | `ATT-5908` | 3 | `b27a94aa` | **`i.O.`** (`V5.0.0`) | **`ATT-6687`**, **`ATT-6688`** |
| 20 | **`ATT-1294`** | `[Cockpit]` Configurable live sparkline graph tiles across numeric `SensorType`s (`1m` to Full Workout) | `ATT-5908` | 3 | `a1a1b562` | **`i.O.`** (`V5.0.0`) | **`ATT-6689`**, **`ATT-6690`**, **`ATT-6695`**, **`ATT-6710`** |
| 21 | **`ATT-2766`** | Populate `Climbs.db` from workouts and add toggleable Climbs layer to Map screen | `ATT-2565` | 3 | `6a07fa22` | **`i.O.`** (`V5.0.0`) | **`ATT-6691`**, **`ATT-6692`** |
| 22 | **`ATT-936`** | Decouple Compose screens from formatting and math via dedicated UI State models | `ATT-431` | 3 | `e5d7d19f` | **`i.O.`** (`V5.0.0`) | — |
| 23 | **`ATT-938`** | Refactor TCX/XML import and export using structured DTO serialization | `ATT-281` | 3 | `d1e744ac` | **`i.O.`** (`V5.0.0`) | **`ATT-6693`**, **`ATT-6694`** |
| 24 | **`ATT-432`** | Access to Database only via Repository (Domain-driven Migration — Phase A) | `ATT-431` | 2 | `d650ce5a` | **`i.O.`** (`V5.0.0`) | **`ATT-6696`**, **`ATT-6697`** |
| 25 | **`ATT-6426`** | Access to Database only via Repository — Phase B: Engines, Exporters & Migration Layer | `ATT-431` | 3 | `2685fd4d` | **`i.O.`** (`V5.0.0`) | **`ATT-6698`** |
| 26 | **`ATT-5083`** | Implement BLE Cycling Radar protocol driver, domain model, scalar `SensorType`s, and Bike integration | `ATT-1286` | 3 | `dfbe3979` | **`i.O.`** (`V5.0.0`) | **`ATT-6699`**, **`ATT-6700`**, **`ATT-6701`** |
| 27 | **`ATT-5085`** | Render live rearview radar vertical edge overlay across Tracking tabs with fail-safe disconnect state | `ATT-1286` | 3 | `2a3baf12` | **`i.O.`** (`V5.0.0`) | **`ATT-6702`**, **`ATT-6703`** |
| 28 | **`ATT-5087`** | Provide built-in Cycling Radar Traffic Simulator for indoor testing, UI preview, and demo mode | `ATT-1286` | 2 | `cc6cfa0d` | **`i.O.`** (`V5.0.0`) | **`ATT-6704`** |
| 29 | **`ATT-5086`** | Implement radar audio chime and haptic alert engine with headphone routing and media ducking | `ATT-1286` | 3 | `90bd1445` | **`i.O.`** (`V5.0.0`) | **`ATT-6705`** |
| 30 | **`ATT-5095`** | Decode Garmin Varia BLE V2 high-resolution radar stream (`6a4e3204`) with lateral offset & vehicle size | `ATT-1286` | 3 | `2f93ceeb` | **`i.O.`** (`V5.0.0`) | **`ATT-6706`** |
| 31 | **`ATT-5102`** | Implement BLE radar tail light mode control (`Day Flash`, `Peloton`, `Solid`, `Radar-Only`) & workout auto-switch | `ATT-1286` | 2 | `c1d27049` | **`i.O.`** (`V5.0.0`) | **`ATT-6707`** |
| 32 | **`ATT-5119`** | Implement 1-Tap Radar Audio Snooze, Peloton-mode audio sync, and smart urban/convoy suppression | `ATT-1286` | 2 | `908a1431` | **`i.O.`** (`V5.0.0`) | **`ATT-6708`** |
| 33 | **`ATT-5120`** | Implement 3D stereo audio panning and approach-speed pitch modulation for headphone radar alerts | `ATT-1286` | 2 | `c8fea2b0` | **`i.O.`** (`V5.0.0`) | **`ATT-6709`** |
| 34 | **`ATT-3052`** | Synchronize completed workouts and telemetry series to Android Health Connect | `ATT-162` | 3 | `8a09a58f` | **`i.O.`** (`V5.0.0`) | **`ATT-6711`** |
| 35 | **`ATT-4981`** | Import morning and overnight resting HRV (`rMSSD`) from Android Health Connect into `HrvReadinessRepository` | `ATT-3539` | 2 | `287d8a6b` | **`i.O.`** (`V5.0.0`) | **`ATT-6712`** |
| 36 | **`ATT-3540`** | Parse Beat-to-Beat RR Intervals from BLE and ANT+ Heart Rate Sensors | `ATT-3539` | 3 | `98df60c9` | **`i.O.`** (`V5.0.0`) | **`ATT-6713`** |
| 37 | **`ATT-3541`** | Implement Lipponen & Tarvainen (2019) RR artifact correction, live `HRV_RMSSD`/`HRV_DFA_A1` & golden tests | `ATT-3539` | 3 | `4bad7272` | **`i.O.`** (`V5.0.0`) | **`ATT-6714`** |
| 38 | **`ATT-3542`** | Display Live HRV Metrics and Aerobic Threshold Zone Indicators in Tracking Cockpit | `ATT-3539` | 2 | `14292b35` | **`i.O.`** (`V5.0.0`) | **`ATT-6715`** |
| 39 | **`ATT-3543`** | Persist lossless `WorkoutRrIntervals` table, centered-window post-workout HRV & FIT `HrvMesg` export/import | `ATT-3539` | 3 | `e87080a3` | **`i.O.`** (`V5.0.0`) | **`ATT-6716`** |
| 40 | **`ATT-3544`** | Render interactive DFA $\alpha_1$ and `rMSSD` time-series charts and $\alpha_1$ vs. Power/HR scatter plot | `ATT-3539` | 3 | `3db57718` | **`i.O.`** (`V5.0.0`) | **`ATT-6717`** |
| 41 | **`ATT-3545`** | Create Morning HRV Readiness Database (with backup/restore) & per-posture 7d vs. 60d SWC calculator | `ATT-3539` | 3 | `669146df` | **`i.O.`** (`V5.0.0`) | **`ATT-6718`** |
| 42 | **`ATT-3556`** | Implement Guided 60–120s Morning Resting HRV Measurement Screen | `ATT-3539` | 3 | `dc034c08` | **`i.O.`** (`V5.0.0`) | **`ATT-6719`**, **`ATT-6720`** |
| 43 | **`ATT-3557`** | Render longitudinal HRV readiness trend, posture-matched 60d SWC corridor & training-load overlay | `ATT-3539` | 2 | `4df73e2f` | **`i.O.`** (`V5.0.0`) | **`ATT-6721`** |
| 44 | **`ATT-3904`** | Implement 1st-order dynamical Power-HR identification engine, expert switch & explanation dialog | `ATT-3903` | 3 | `1f70fb15` | **`i.O.`** (`V5.0.0`) | **`ATT-6722`** |
| 45 | **`ATT-3905`** | Display Cardio-Power Dynamics card and Fresh Model vs. Actual HR graph overlay in Summaries & Details | `ATT-3903` | 2 | `1b33fcba` | **`i.O.`** (`V5.0.0`) | **`ATT-6723`** |
| 46 | **`ATT-3906`** | Plot longitudinal season trends for Fresh `HR@200W`, Time Constant $\tau$, and Slope `1/K` in Athlete Profile | `ATT-3903` | 2 | `178589a7` | **`i.O.`** (`V5.0.0`) | **`ATT-6724`** |
| 47 | **`ATT-3907`** | Add real-time `LIVE_HR_DRIFT` sensor metric to `CyclingPowerMetricsDevice` and tracking cockpit | `ATT-3903` | 2 | `11197f2b` | **`i.O.`** (`V5.0.0`) | **`ATT-6725`**, **`ATT-6726`** |
| 48 | **`ATT-6177`** | Remove stale Travis CI configuration | `ATT-6142` | 1 | `54cf5814` | **`i.O.`** (`V5.0.0`) | — |
| 49 | **`ATT-6148`** | Move release keystore password out of working tree | `ATT-6141` | 2 | `cfc7bbfe` | **`i.O.`** (`V5.0.0`) | — |
| 50 | **`ATT-6153`** | Remove stale process artifacts from repository | `ATT-6141` | 1 | `bd18435d` | **`i.O.`** (`V5.0.0`) | — |
| 51 | **`ATT-6179`** | Resynchronize architecture document with code | `ATT-6142` | 3 | `882e2718` | **`i.O.`** (`V5.0.0`) | **`ATT-6727`** |
| 52 | **`ATT-6173`** | Run Android Lint in CI and reduce lint baseline | `ATT-6142` | 2 | `08a744c1` | **`i.O.`** (`V5.0.0`) | **`ATT-6728`** |
| 53 | **`ATT-6175`** | Introduce detekt with baseline and complexity rules | `ATT-6142` | 2 | `549f27cd` | **`i.O.`** (`V5.0.0`) | **`ATT-6729`** |
| 54 | **`ATT-6171`** | Add Kover coverage measurement with CI threshold | `ATT-6142` | 2 | `d1e51c91` | **`i.O.`** (`V5.0.0`) | **`ATT-6730`** |
| 55 | **`ATT-6186`** | Inventory and phase out source-text-reading tests | `ATT-6142` | 2 | `c91e62b6` | **`i.O.`** (`V5.0.0`) | **`ATT-6731`** |
| 56 | **`ATT-6180`** | Specify software interfaces in architecture | `ATT-6142` | 3 | `10c3366b` | **`i.O.`** (`V5.0.0`) | **`ATT-6732`** |
| 57 | **`ATT-6152`** | Write configuration management plan and pin submodule baseline | `ATT-6141` | 2 | `57dfe9ec` | **`i.O.`** (`V5.0.0`) | **`ATT-6733`** |
| 58 | **`ATT-6182`** | Add automated architecture conformance tests | `ATT-6142` | 3 | `f1bc836e` | **`i.O.`** (`V5.0.0`) | **`ATT-6734`** |
| 59 | **`ATT-6151`** | Introduce project risk register | `ATT-6141` | 2 | `68debf04` | **`i.O.`** (`V5.0.0`) | — |
| 60 | **`ATT-6181`** | Define resource and timing budgets in architecture | `ATT-6142` | 2 | `fb45b2bd` | **`i.O.`** (`V5.0.0`) | **`ATT-6735`** |
| 61 | **`ATT-6183`** | Write software integration verification strategy | `ATT-6142` | 2 | `4e41001c` | **`i.O.`** (`V5.0.0`) | **`ATT-6736`** |
| 62 | **`ATT-6184`** | Set up `androidTest` infrastructure with emulator CI job | `ATT-6142` | 3 | `0aae44bd` | **`i.O.`** (`V5.0.0`) | **`ATT-6737`** |
| 63 | **`ATT-6158`** | Define system context and external system elements | `ATT-6142` | 2 | `4ced957a` | **`i.O.`** (`V5.0.0`) | **`ATT-6738`** |
| 64 | **`ATT-6160`** | Specify system requirements for live tracking with sensors | `ATT-6142` | 3 | `1c055509` | **`i.O.`** (`V5.0.0`) | **`ATT-6739`** |
| 65 | **`ATT-6161`** | Specify system requirements for data exchange and cloud integration | `ATT-6142` | 2 | `348ff4a7` | **`i.O.`** (`V5.0.0`) | **`ATT-6740`** |
| 66 | **`ATT-6164`** | Create system architectural design | `ATT-6142` | 3 | `7563a9c2` | **`i.O.`** (`V5.0.0`) | **`ATT-6741`** |

---

## 4. Retrospective Findings, PO Strategic Directives & Permanent Countermeasures (`Rule 81`)

### A. Product Owner Strategic Workflow Directives (`ATT-6289` Comments)
1. **Manual PO Coordinator Model Tier Policy & Phase-Boundary Handoff Checkpoints**:
   - **Directive** (*Rainer Blind, 2026-11-10*): Use `Gemini 4 Carbon` as `Pro` model specifically for Sprint and Epic Reviews, switching manually across phases:
     - **Planning (Ceremony 1)**: `Argon` or `Carbon`
     - **Main Execution (Phase 2 Coordinator)**: `Flash` *(while persistent Epic subagent pairs follow Core Rule 7 `pro`/`flash` zipper roles)*
     - **Review & Retro (Ceremony 2)**: `Carbon`
   - **Action**: Codified in [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md) (`Rule 2`) and [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md) with two explicit phase-boundary pause checkpoints (**End of Ceremony 1 $\rightarrow$ switch to `Flash`** and **End of Phase 2 + Tier 2 Gate $\rightarrow$ switch to `Carbon`**) while preserving 100% uninterrupted execution inside Phase 2.
2. **Concurrent / Two-Tab Ticket Evaluation vs. ASPICE Conformity (`"Evaluate Tickets while Agents are still working"`)**:
   - **Observation & ASPICE Conformity Assessment**: Evaluating `Final Review (Human)` tickets in a second `Carbon` tab while Phase 2 `Flash` agents are still committing downstream tickets onto `sprint/<SPRINT_NAME>` raises three legitimate ASPICE conformity risks:
     1. **`SUP.8` Moving Baseline**: Stamping `Erledigt` + `FixVersion` against an intermediate commit $C_k$ before downstream commits $C_{k+1..N}$ land means qualification was not performed against the final integrated sprint baseline $C_{\text{final}}$.
     2. **`SWE.5` $\rightarrow$ `SWE.6` Ordering**: Final `SWE.6` qualification acceptance logically follows `SWE.5` full sprint integration verification (the End-of-Phase-2 Tier 2 Gate).
     3. **`Rule 77` Downstream Branch Inheritance**: Rejecting (`n.i.O.`) an early ticket on `sprint/<SPRINT_NAME>` after later worktrees already branched from it contaminates downstream branches.
   - **Decision**: **Do NOT adopt the Two-Tab Concurrent Review approach.** Ceremony 2 remains strictly sequential *after* Phase 2 and the End-of-Phase-2 Tier 2 Gate complete on the integrated sprint branch, preserving 100% unambiguous ASPICE `SUP.8` / `SWE.5` $\rightarrow$ `SWE.6` baseline conformity. Review duration is instead bounded by adhering to compact sprint scopes (`REQ-PRO-029`).
3. **Parallel Agent Concurrency Calibration (`Concurrency = 4` Pilot $\rightarrow$ `Concurrency = 3` Sweet Spot)**:
   - **Empirical Finding**: Scaling from `2` to `4` parallel `Workspace: "share"` lanes ($\binom{4}{2} = 6$ pairwise interaction surfaces) nearly tripled velocity (`146.0 SP`), but forced overlapping touches across `BANALService`, `SafePainterResource`, `Repository` encapsulation, and `detekt-baseline.xml` (causing 44 cross-lane unit test failures, 13 Lint errors, and 1 Detekt baseline overrun caught at Tier 2).
   - **Decision (*„Aller guten Dinge sind 3“*)**: Calibrate **`Rule 9`** to **`concurrency = 3`** across strictly disjoint Parent Epics ($\binom{3}{2} = 3$ pairwise surfaces — **50% fewer collision pairs** than $N=4$, `/tmp/att_build_merge.lock` utilization $\rho \approx 0.45$, and serialized `installDebug` + `adb` screenshot verification under `/tmp/att_build_merge.lock` when $>1$ UI lane is active).

### B. Technical & Process Root-Cause Analyses (`Rule 81` Hardening)
1. **Eliminating Zero-Caller "Island" Components (`ATT-5085`/`ATT-6702`, `ATT-3052`/`ATT-6711`, `ATT-4981`/`ATT-6712`, `ATT-1298`/`ATT-6687`/`ATT-6688`, `ATT-3905`/`ATT-6723`)**:
   - **Root Cause**: Subagents authored well-tested standalone composables/importers (`RadarSettingsSection` across 6 tickets, `HealthConnectSettingsDialog`, `HealthConnectHrvImporter`, `LiveMmpCurveChart`, `CardioPowerDynamicsCard`, and 8 MMP `SensorType`s) and unit-tested them in isolation without mounting them into `AdvancedTuningScreen`, `AppNavigationDrawer`, `SensorGridScreen`, `WorkoutDetailTabbedContent`, or `ActivityType.getSensorTypeArray()`.
   - **Permanent Countermeasure (`Rule 81` / `docs/coding_standards.md` §1.12, `stage3-impl-plan`, `stage4-implementation`)**: Every Stage 3 plan MUST schedule an explicit host-mounting step, and Stage 4 / Gate 4 MUST execute `grep -rn "<Symbol>" app/src/main/` proving live reachability from `MainActivityWithNavigation` / `ActivityType.getSensorTypeArray()` / background startup hooks.
2. **Dynamic Code-Grounding & Bidirectional ID Equality Across `SWE.2` / `SYS.2` / `SYS.3` Docs (`ATT-6179`/`ATT-6727`, `ATT-6180`/`ATT-6732`, `ATT-6158`..`ATT-6164`/`ATT-6738`..`ATT-6741`)**:
   - **Root Cause**: Agents authoring `docs/architecture/*.md` and `docs/system/*.md` checked only prompt examples while inventing plausible-sounding class/DTO names (`RadarAlertAudioEngine`, `WorkoutSamplesDbHelper`, `Retrofit`, `11` stale export/import filenames) and leaving `13` `SYSREQ-*` IDs unallocated in `system_architecture.md`.
   - **Permanent Countermeasure (`Rule 81` / `docs/coding_standards.md` §5.9, `stage4-implementation`, `stage5-verification`, `sprint-planner`)**: Automated symbol-vs-source sweeps verifying that **every** backticked `.kt`/`.java`/`.xml` filename, class, method signature, and manifest attribute exists in `app/src/main/`, plus exact `declared == allocated` set equality across `SYS.2 <-> SYS.3`, are now mandatory across Stage 4, Gate 5, and Ceremony 2.
3. **True Repository Encapsulation Over Backdoor `*DatabaseManager` Getters (`ATT-6426` $\rightarrow$ `ATT-6698`, `ATT-432` $\rightarrow$ `ATT-6696`)**:
   - **Root Cause**: `RepositoryEngineCleanlinessContractTest` checked only for literal `*DatabaseManager.getInstance(`, allowing callers to bypass encapsulation via `WorkoutRepository.getInstance(ctx).workoutSummariesDatabaseManager`.
   - **Permanent Countermeasure (`Rule 81` / `docs/coding_standards.md` §2.5)**: Public/internal `get*DatabaseManager()`, `get*DbHelper()`, `SQLiteDatabase`, and raw `Cursor` getters on Repositories are strictly prohibited.
4. **Tier 2 CI Static-Analysis Parity, Plain-JVM SDK Stub Guards & `Workspace: "share"` Hygiene (`ATT-5102`, `ATT-6173`, `ATT-6175`, `ATT-3543`, `ATT-6318`)**:
   - **Root Cause**: Parallel tickets merged onto `sprint/2026-46.1` caused 44 cross-ticket unit test failures (`RadarLightController` unmocked/stub `filesDir` throwing or leaking async `DataStore` exceptions; `ATT-6173` `ContextCompat.registerReceiver` calling unmocked `Process.myPid()` when `SDK_INT == 0`, `Build.VERSION.SDK_INT >= R` overriding injectable `sdkInt`, `SafePainterResource` dropping `id < 0`, and manifest/strings XML attributes breaking strict regexes; `ATT-3543` adding `WorkoutRrDatabaseManager.getInstance` in files cleaned by `ATT-6426`; `ATT-3545` drawer count `25 -> 26`; `ATT-6168` `FilterFormattingUtils` extraction; and post-`ATT-6175` files exceeding `detekt-baseline.xml`), plus a `Workspace: "share"` worktree staging a `120000` symlink over the `160000` `ANT-Android-SDKs` submodule gitlink.
   - **Permanent Countermeasure (`Rule 81` / `aspice_governance.md` Rules 7–9)**: All 7 integration fixes were committed as attributed commits (`66dd7cf7`, `89e29078`, `9f6a73a9`, `5808179d`, `6c8e6161`, `e9a83912`, `d6543904`, plus `detektBaseline` sync); Tier 2 Gate now mandates `:app:lintDebug :app:detekt :app:compileDebugAndroidTestKotlin`, `160000` gitlink verification, and `--detach` worktree branch cleanup before Ceremony 2.
5. **Ceremony 1 Upstream Ticket Dependency Check, Ceremony 2 Risk Register Review & Hard Python Tripwire Follow-Up (`ATT-6181`/`ATT-6139`, `ATT-6151`, `ATT-6743`)**:
   - **Action**: Hardened `sprint-planner/SKILL.md` Ceremony 1 to verify upstream Jira `Dependencies` (flagging backlog prerequisites such as `ATT-6139` for `ATT-6181`) and Ceremony 2 Step 5 to update `docs/management/risk_register.md`; broadened Stage 4 `High-Risk` (`subagent_pro` Doing) to binary protocols/parsers (BLE GATT, ANT+, FIT/TCX) and physiological DSP engines with adversarial Gate 4 auditing; and created top-of-backlog governance ticket **`ATT-6743`** (`2 SP`) to encode `Rule 81` (`@Composable` island checks, Repository `*DatabaseManager` getter bans, and `SWE.2`/`SYS.2`/`SYS.3` code-grounding) as deterministic `exit 1` tripwires in `tools/verify_requirement_governance.py` and sync `REQ-PRO-041.1` (`concurrency = 3`).
