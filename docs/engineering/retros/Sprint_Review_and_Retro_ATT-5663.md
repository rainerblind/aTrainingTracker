# Sprint Review & Retrospective: Sprint 2026-45.9 (`ATT-5663`)

**Ticket**: [ATT-5663](https://rainerblind.atlassian.net/browse/ATT-5663) (`Review & Retro`)  
**Sprint**: `Sprint 2026-45.9` (Jira Sprint ID `401`, Branch `sprint/2026-45.9`)  
**Target Release**: `V5.0.0`  
**Date**: 2026-11-07  

---

## 1. Sprint Velocity & Delivery Summary (`REQ-PRO-029`)

| Metric | Planned (Ceremony 1) | Completed & Accepted (Ceremony 2) | Deferred | Completion Rate |
| :--- | :---: | :---: | :---: | :---: |
| **Work Tickets** | 17 | **17** | 0 | **100%** |
| **Story Points (SP)** | 26 SP | **26 SP** | 0 SP | **100%** |
| **Accepted (`i.O.`)** | — | **17 / 17** (`100%` of completed) | — | **100%** |
| **Tier 2 Integration Gate** | — | **4,578 unit tests passed (`0` failed); `875` REQs / `877` TSTs (`100%` parity)** | — | **100%** |
| **Follow-Up Refinements Logged** | — | 2 (`ATT-5760`, `ATT-5761`) | — | — |

---

## 2. Ticket-by-Ticket Review Outcomes (Backlog Rank Order)

| # | Key | SP | Epic | Summary | Verdict | Follow-Up Ticket(s) |
| :-: | :--- | :-: | :--- | :--- | :---: | :--- |
| 1 | [ATT-5504](https://rainerblind.atlassian.net/browse/ATT-5504) | 1 | `ATT-111` | `ElevationProfile`: Restrict right-hand Altitude Histogram exclusively to Workout Details | **Accepted (`i.O.`)** | — |
| 2 | [ATT-5492](https://rainerblind.atlassian.net/browse/ATT-5492) | 1 | — | Remove obsolete Network Location sensor (`SPEED_AND_LOCATION_NETWORK`) and migrate `Devices.db` to v10 | **Accepted (`i.O.`)** | — |
| 3 | [ATT-5664](https://rainerblind.atlassian.net/browse/ATT-5664) | 1 | `ATT-176` | `ClusterNamingDialog`: Explicitly label new favorite route creation | **Accepted (`i.O.`)** | — |
| 4 | [ATT-5662](https://rainerblind.atlassian.net/browse/ATT-5662) | 1 | `ATT-176` | `ClusterNamingDialog`: Change subtitle to `'Nicht automatisch zuweisbare Strecke vom %1$s.'` | **Accepted (`i.O.`)** | — |
| 5 | [ATT-5661](https://rainerblind.atlassian.net/browse/ATT-5661) | 1 | `ATT-355` | `SwapOrUpgradeDialog` & `KeepTwoSensorsBottomSheet`: Replace `'Free-Tier'` with `'Basisversion'` & `'Entwicklung unterstützen'` | **Accepted (`i.O.`)** | — |
| 6 | [ATT-5660](https://rainerblind.atlassian.net/browse/ATT-5660) | 1 | `ATT-355` | `DeviceListViewModel.getFilteredDevices`: Stable sorting without position jumping on `isConnected` | **Accepted (`i.O.`)** | — |
| 7 | [ATT-5659](https://rainerblind.atlassian.net/browse/ATT-5659) | 1 | `ATT-355` | `SupporterScreen` & Dialogs: Grounded copy for app development support without buzzwords | **Accepted (`i.O.`)** | — |
| 8 | [ATT-5658](https://rainerblind.atlassian.net/browse/ATT-5658) | 2 | — | `FirstJubileeCelebrationDialog`: Reorder (`Email -> Play Store -> Financial Support`), show for Pioneers & grounded copy | **Accepted (`i.O.`)** | — |
| 9 | [ATT-5657](https://rainerblind.atlassian.net/browse/ATT-5657) | 1 | — | `SwapOrUpgradeDialog` & `SensorQuotaReconciliationManager`: Never recommend `Google Fused` or sole Location sensor | **Accepted (`i.O.`)** | — |
| 10 | [ATT-5656](https://rainerblind.atlassian.net/browse/ATT-5656) | 2 | `ATT-355` | `WorkoutShareBottomSheet` (*Foto mit Track & Daten*): 3-tier photo preselection cascade | **Accepted (`i.O.`)** | — |
| 11 | [ATT-5655](https://rainerblind.atlassian.net/browse/ATT-5655) | 2 | `ATT-355` | `WorkoutPhotoStrip` & `WorkoutDetailTabbedContent`: Full/half row width for Large/Medium & 1:1 Expert Settings sync | **Accepted (`i.O.`)** | [ATT-5760](https://rainerblind.atlassian.net/browse/ATT-5760) |
| 12 | [ATT-5654](https://rainerblind.atlassian.net/browse/ATT-5654) | 2 | `ATT-826` | *Steigungsverteilung* in Expert Settings: Sortable checkbox entry in Summary & Detail section lists | **Accepted (`i.O.`)** | [ATT-5761](https://rainerblind.atlassian.net/browse/ATT-5761) |
| 13 | [ATT-5649](https://rainerblind.atlassian.net/browse/ATT-5649) | 2 | — | `BikeBellAudioSynthesizer`: Fix clipped/muted bell audio across Android devices (warm `pause()`, conditional speaker, 48 kHz) | **Accepted (`i.O.`)** | — |
| 14 | [ATT-5570](https://rainerblind.atlassian.net/browse/ATT-5570) | 2 | `ATT-355` | `GarageLeagueDetector` & `GarageUpgradeNudgeDialog`: Detect Strava Pro (`summit`/`premium`) for Gold/Diamond League | **Accepted (`i.O.`)** | — |
| 15 | [ATT-3922](https://rainerblind.atlassian.net/browse/ATT-3922) | 2 | — | *Höchstmaß* — The Agile Agentic ASPICE (AAA) Skill Set (Part 1/3): Core Framework, Swiss Cheese README & Templates | **Accepted (`i.O.`)** | — |
| 16 | [ATT-5667](https://rainerblind.atlassian.net/browse/ATT-5667) | 2 | — | *Höchstmaß* — The Agile Agentic ASPICE (AAA) Skill Set (Part 2/3): Parameterized Governance & Jira CLI Tooling | **Accepted (`i.O.`)** | — |
| 17 | [ATT-5668](https://rainerblind.atlassian.net/browse/ATT-5668) | 2 | — | *Höchstmaß* — The Agile Agentic ASPICE (AAA) Skill Set (Part 3/3): Real-World Android Showcase & Community Posts | **Accepted (`i.O.`)** | — |

---

## 3. Root Cause Analysis of In-Sprint & Review Findings (`ATT-5663`)

1. **Repeated Unintended Opening of the Edit Workout Dialog During On-Device Verification (`ATT-5504` / Comment on `ATT-5663`)**:
   * *Observation*: During Stage 5 on-device verification of `ATT-5504`, `subagent_flash` (`58a910ab`, Steps 265–301) opened the *"Trainingseinheit überarbeiten"* (`EditWorkoutBottomSheet`) dialog twice while trying to switch to the *"Karte & Diagramme"* tab on `TrackOnMapScreen`.
   * *Root Cause*: Instead of querying the UI hierarchy for the exact pixel bounds of the tab, `subagent_flash` visually guessed `(500, 270)` from the downscaled screenshot (`read_file`). On the `1080x2424` screen, `y = 180..580` is occupied by the clickable Workout Summary Header, whereas the tab bar sits below it at `y = 650`. After dismissing the sheet, the agent failed to recognize that `(500, 270)` had opened the sheet and tapped `(480, 270)` a second time before targeting `(500, 650)` on the third attempt.
   * *Countermeasure (`Rule 76`, `.agents/skills/stage5-verification/SKILL.md`)*: Mandate `adb shell uiautomator dump /sdcard/window_dump.xml` coordinate resolution to obtain exact `bounds="[x1,y1][x2,y2]"` before executing `adb shell input tap` in Stage 5, and strictly prohibit repeating any `(x, y)` tap coordinate that opened an unintended dialog or sheet.

2. **Proportional Bitmap Decoding (`maxDim`) When Scaling Up Thumbnail Containers (`ATT-5655` $\rightarrow$ `ATT-5760`)**:
   * *Observation*: Scaling `PhotoThumbnailCard` in `WorkoutPhotoStrip` to full row width (`LARGE`) and half row width (`MEDIUM`) kept the static downsampling limit `maxDim = 240`, causing visible blurriness on large cards.
   * *Countermeasure (`Rule 76`, `docs/design_guidelines.md` Sec 5.17)*: Whenever an image container scales across responsive size tiers (`SMALL` / `MEDIUM` / `LARGE`), `decodeSampledBitmap` `maxDim` and `remember`/`LaunchedEffect` keys must scale proportionally with the target display width in pixels.

3. **Strict Status & Role Allowlist for `jira_util.py set-fixversion` (`ATT-5492` / `ATT-5663`)**:
   * *Observation*: During Stage 3 of `ATT-5492`, `subagent_pro` called `set-fixversion` while the ticket was in `Implementation Plan`, which bypassed the partial denylist in `tools/jira_util.py`.
   * *Countermeasure (`Rule 76`, `tools/jira_util.py`)*: Replaced the partial denylist in `set_fix_version` with a strict status allowlist (`{"Final Review (Human)", "Erledigt", "Done", "Closed"}`) and blocked `agent1`/`agent2` roles from calling `set-fixversion`.
