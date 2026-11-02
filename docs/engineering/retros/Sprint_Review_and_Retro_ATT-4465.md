# Sprint Review & Retrospective: Sprint 2026-43.4 (`ATT-4465`)

**Date**: 2026-10-21
**Sprint**: `2026-43.4` (`id 384`)
**Integration Branch**: `sprint/2026-43.4` $\rightarrow$ `develop`
**Target Release Version**: `V4.9.40`

---

## 1. Sprint Summary & Scope Outcome

All 9 committed feature, improvement, and bugfix tickets in Sprint `2026-43.4` were executed autonomously across the full 5-stage ASPICE lifecycle (`Analysis` $\rightarrow$ `Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test`), verified against the 3,664+ clean-room unit test suite and 9-locale translation parity checks, deployed to the connected Pixel 10 (`./gradlew installDebug`), and integrated onto `sprint/2026-43.4` as strictly one atomic squashed commit per ticket (Core Rule 8):

| Rank | Ticket Key | Summary | Commit on `sprint/2026-43.4` | Status / Outcome | Fix Version |
| :---: | :--- | :--- | :---: | :---: | :---: |
| 1 | **`ATT-4464`** | Significantly reduce switch size (`0.6f`) and visual weight (secondary colors) for subordinate `'Show in Workout Summary'` toggles in Physiology & Training Models | `0deaf91a` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 2 | **`ATT-4460`** | Clean up unused `smoothedBearings`/`calcWindowBearing` dead code and sparse-vertex windowing in `TurnCueDetector` | `8bbde3c6` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 3 | **`ATT-4459`** | Fix legacy Dropbox credential key mismatch in `AuthCredentialsStore` causing Dropbox backup failure (`PARTIAL_SUCCESS`) | `8ad50842` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 4 | **`ATT-4458`** | Fix `LOCAL_SUCCESS` / `PARTIAL_SUCCESS` status display and expose local rolling backup restore in UI | `22916f21` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 5 | **`ATT-4456`** | Stop-tracking coachmark immediately self-dismisses during scroll from Control tab to Cockpit | `0d2d30f4` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 6 | **`ATT-4455`** | Add abortable first-install permission onboarding flow on initial app launch | `7c9d917f` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 7 | **`ATT-4452`** | Prompt athlete to configure HR or Power zones via dialog/action when a newly discovered sensor is auto-paired | `4ba0de9d` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 8 | **`ATT-4449`** | Granting Bluetooth scan permission after selecting Bluetooth via `'+'` FAB cancels pairing flow and stays on Known/Paired tab + `neverForLocation` manifest flag | `b9b595cd` | **Accepted (`Erledigt`)** | `V4.9.40` |
| 9 | **`ATT-4447`** | Remove Torque (`Moment`) from default Bike Power tab so Pedal Power Balance spans full row width | `1f088f71` | **`Final Review (Human)`** | `V4.9.40` |

*Older carried-over refactoring backlog tickets (`ATT-4002` .. `ATT-4033`) were left untouched for future sprints.*

---

## 2. Retrospective & Process Hardening (`Rule 60`)

### 2.1 What Went Well
* **Fused Same-Agent Zipper Handoffs Proven Live (`ATT-4458` .. `ATT-4447`)**: Starting mid-sprint, instructing `subagent_flash` to fuse **Gate 1 Review + Stage 2 Doing** and **Gate 3 Review + Stage 4 Doing** into single turns, and `subagent_pro` to fuse **Gate 2 Review + Stage 3 Doing** into a single turn, cut Coordinator relay round-trips from 10 down to 6 per ticket while preserving 100% of our 4-eyes model separation (`pro` $\leftrightarrow$ `flash`).
* **Concurrent Ceremony 2 & Dual-Install Verification (Clean Release APK + Veteran Debug DB)**: Testing a freshly wiped clean installation alongside the long-lived developer debug installation uncovered critical first-install onboarding, coachmark pager-settle, and runtime Bluetooth permission edge cases (`ATT-4456`, `ATT-4455`, `ATT-4452`, `ATT-4449`), all of which were resolved in this sprint.
* **Zero Merge Conflicts via Strategy A & One Commit per Ticket**: All 9 tickets touched overlapping files (`docs/requirements.md`, `docs/tests.md`, `strings.xml` across 9 locales, `DevicesTabbedScreen.kt`, `BackupStatusCard.kt`) and integrated onto `sprint/2026-43.4` with zero merge conflicts.

### 2.2 Codified Process Improvements: 4 Velocity Optimizations (`Rule 60`)
To bring per-ticket wall-clock execution time down from ~36 minutes to ~15–18 minutes without sacrificing any of the 5 ASPICE stages or 4-eyes quality gates, the following 4 optimizations have been codified in `.agents/rules/aspice_governance.md`, `.agents/skills/stage1-analysis/SKILL.md` .. `.agents/skills/stage5-verification/SKILL.md`, `docs/coding_standards.md` (Section 5.2), and `docs/engineering/rule_history_archive.md`:
1. **Two-Tier Unit Test Execution Strategy**:
   * **Tier 1 (Per-Ticket in Stages 4 & 5, ~10–20s)**: Run targeted package/contract unit tests (`--tests ...`), `TranslationParityTest` (when `strings.xml` changes), and `verify_requirement_governance.py` (reserving per-ticket full suite runs for High-Risk core infrastructure changes).
   * **Tier 2 (Sprint Integration Gate in `Review & Retro`, ~4m)**: Run the full 3,664+ test suite (`./gradlew testDebugUnitTest`) once before merging `sprint/<SPRINT_NAME>` into `develop`.
2. **Fused Same-Agent Handoffs in the Zipper Loop**:
   * Fuse `Gate 1 (flash) -> Stage 2 (flash)`, `Gate 2 (pro) -> Stage 3 (pro)`, and (for Standard tickets) `Gate 3 (flash) -> Stage 4 (flash)` into single subagent turns.
3. **Concise Deliverable Cap ($\le 60\text{--}80$ Lines)**:
   * Cap Stage 1–5 markdown deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`) at $\le 60\text{--}80$ lines using compact tables, bullet points, and exact file/line references.
4. **Conditional On-Device ADB Cycle in Stage 5**:
   * Restrict `./gradlew installDebug` + ADB screenshot verification in Stage 5 to tickets touching UI, Compose, resources, navigation, or foreground services.
