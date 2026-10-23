# Sprint Review & Retrospective — Sprint 2026-43.9 (`ATT-4718`)

* **Sprint**: `2026-43.9` (`id = 389`)
* **Sprint Branch**: `sprint/2026-43.9` $\rightarrow$ `develop`
* **Release Target**: `V4.9.41`
* **Date**: 2026-10-23

---

## 1. Sprint Review Summary (10 / 10 Tickets Accepted as `i.O.`)

| Rank | Ticket | Epic | Summary | Commit | Verdict & Follow-Up Tickets |
| :---: | :--- | :--- | :--- | :---: | :--- |
| 1 | **`ATT-3534`** | `ATT-232` | Codify AAA Governance, Hybrid Model Handoffs, SQLite Migration Parity, and Stage 5 AAPT2 Check | `39b3df08` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 2 | **`ATT-4594`** | `ATT-232` | Restructure living documentation into domain-sharded, multi-line sections | `06990c88` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 3 | **`ATT-4480`** | `ATT-4474` | Replace Gaussian point-cloud `HeatmapTileProvider` with `PolylineHeatmapTileProvider` and thermal Color LUT | `548f3995` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` — Follow-up: **`ATT-4769`** (Sequential Blue Color Palette) |
| 4 | **`ATT-4478`** | `ATT-4474` | Upgrade `PeriodBarGraph` with rest-period timeline spacing, sub-hour labels, X-axis labels, and metric/sport modes | `61180cc4` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` — Follow-ups: **`ATT-4770`** (Header Sport Filter Icons), **`ATT-4771`** (`'km'` Unit Label) |
| 5 | **`ATT-4477`** | `ATT-4474` | Replace 8-track compromise in `PeriodSummaryCard` mini-map with full-period simplified preview off the UI thread | `da70647f` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` — Follow-up: **`ATT-4772`** (Suppress redundant polylines when $>25$ workouts use Heatmap) |
| 6 | **`ATT-4476`** | `ATT-4474` | Make `PeriodSummaries.db` self-contained and eliminate N+1 `tempId` queries in `getPeriodsByType()` | `b38b96dc` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 7 | **`ATT-4475`** | `ATT-4474` | Fix `InteractivePeriodMap` double-rendering, `anchorTracks` OOM-guard bypass, and large-period track clickability | `e130f299` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 8 | **`ATT-52`** | `ATT-2144` | Support Landscape Orientation with Responsive Dual-Pane Map Layout and Widescreen Sensor Grid | `4dc064a6` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` — Follow-up: **`ATT-4773`** (Landscape Cockpit status bar icons & auto-hiding floating tab row) |
| 9 | **`ATT-2145`** | `ATT-2144` | Compact Aligned Marginal Zone Distribution Bars Right of Telemetry Graph in Landscape Mode | `dcb8f15f` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` — Follow-up: **`ATT-4774`** (Scroll-away header, 1-line Landscape header, inline zoom controls, 72% X-axis plot alignment) |
| 10 | **`ATT-2276`** | `ATT-2273` | Implement In-App Feature Discovery and Tips Guide | `99f66b16` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` — Follow-up: **`ATT-4775`** (Fix Cockpit Layout deep link & add dedicated Sport Types tip card) |

---

## 2. Tier 2 Sprint Integration Gate Verification

* **Initial Background Run at Ceremony 2 Start (`task-819`)**:
  * **3,938 / 3,939 unit tests passed** (`99.97%`).
  * **1 Failure Caught & Resolved Before Merge**: `ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt` flagged `BikeSportBarColor`, `RunSportBarColor`, and `OtherSportBarColor` in `PeriodBarGraph.kt:88-90` (`ATT-4478`). Moved all 3 tokens into `TTColor` (`Color.kt`).
* **Post-Fix Verification (`task-1026`)**:
  * `ThemeColorCentralizationAuditTest` + `PeriodBarGraphContractTest` + `python3 tools/verify_requirement_governance.py`: **100% PASSED** (**3,939 / 3,939** unit tests green across the entire repository).

---

## 3. Retrospective Root Cause Analysis & Permanent Countermeasures (`Rule 64`)

1. **Pre-Build Debug APK at Very End of Phase 2 (`"Build debug APK at very end of phase 2."`)**:
   * **Observation**: Waiting until Ceremony 2 Step 1 to compile the integrated sprint debug APK delays the start of the interactive review.
   * **Countermeasure (`Rule 64`)**: Codified in `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` that the Coordinator **MUST** run `./gradlew assembleDebug` (or `installDebug` if the device is connected) as the final step of Phase 2 immediately after squashing the last sprint ticket.
2. **Tier 1 Inclusion of `ThemeColorCentralizationAuditTest` for UI Changes**:
   * **Root Cause**: Tier 1 unit tests for `ATT-4478` ran `ui.aftermath.periodlist.*` and `build.*`, which did not include `ui.theme.ThemeColorCentralizationAuditTest`.
   * **Countermeasure (`Rule 64`)**: Updated `.agents/rules/aspice_governance.md` and `.agents/skills/stage5-verification/SKILL.md` so Tier 1 test runs on any ticket modifying UI/Compose files always include `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`.
3. **Strict Hub-and-Spoke Subagent Communication Topology (`ATT-4594` & `ATT-4476`)**:
   * **Root Cause**: When `subagent_pro` messaged `subagent_flash` directly via peer-to-peer `send_message` during `ATT-4476`, the Coordinator did not receive a `<SYSTEM_MESSAGE>` wakeup when `subagent_flash` finished.
   * **Countermeasure (`Rule 64`)**: Codified in `.agents/rules/aspice_governance.md` (Core Rule 7) that subagents must strictly report back to the Coordinator at the end of their turn and never invoke peer-to-peer `send_message`.
4. **Landscape Top Chrome Budget & Stacked Multi-Chart Horizontal Plot Alignment (`ATT-4773`, `ATT-4774`)**:
   * **Countermeasure (`Rule 64`)**: Codified in `docs/design_guidelines.md` Section 2.1 that all vertically stacked charts sharing a synchronized X-axis or scrubbing cursor must maintain identical horizontal plot bounds (`startPadding` and right plot edge) across both Portrait and Landscape orientations.
