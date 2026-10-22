# Sprint Review & Retrospective: Sprint 2026-43.7 (ATT-4670)

**Sprint**: `2026-43.7`  
**Retrospective Ticket**: [ATT-4670](https://rainerblind.atlassian.net/browse/ATT-4670)  
**Target Release**: `V4.9.40`  
**Date**: 2026-10-22  

---

## 1. Sprint Delivery Summary (`sprint/2026-43.7`)

All 7 implementation tickets (plus 1 consolidated duplicate `ATT-4663`) were completed across Stages 1–5, verified via the Tier 2 clean-room unit test suite (`3,842+` tests, `0` failures) and on-device Pixel 10 deployment, and accepted (`Erledigt` / `V4.9.40`):

| Rank | Ticket | Commit | Summary | Outcome |
| :---: | :--- | :---: | :--- | :--- |
| 1 | **`ATT-4669`** | `ce9bcd3a` | `[Bug] [High] Fix full-suite unit test failures (todo() contract collision, AltitudeAwareClusterMatchingTest mock, and static mock teardown leakage)` | Accepted (`Erledigt`, `V4.9.40`) |
| 2 | **`ATT-4668`** | `2ff1f4c7` | `[Verbesserung] Scope Strava OAuth redirect URI by applicationId to prevent debug/release app chooser dialog` | Accepted (`Erledigt`, `V4.9.40`) + Follow-up **`ATT-4706`** (`android:scheme="strava.${applicationId}"`) |
| 3 | **`ATT-4667`** | `4a02f932` | `[Bug] [Highest] Add @JvmOverloads to WorkoutClustersViewModel constructor to fix NoSuchMethodException crash on Clusters screen` | Accepted (`Erledigt`, `V4.9.40`) |
| 4 | **`ATT-4665`** | `dfdc602c` | `[Feature] [Medium] Add granular Dropbox sync preferences (Workouts, Backups, Wi-Fi-only, Last Sync, Account Email) and enforce in ExportManager & BackupWorker` | Accepted (`Erledigt`, `V4.9.40`) |
| 5 | **`ATT-4666`** | `07ac3cd6` | `[Verbesserung] [Medium] Unify DropboxSettingsDialog and GoogleDriveSettingsDialog with shared layout, granular toggles, backup interval, and last backup/sync status` | Accepted (`Erledigt`, `V4.9.40`) |
| 6 | **`ATT-4664`** | `8020d902` | `[Verbesserung] [Medium] Unify Restore Backup card button labels, icons, and disconnected visibility` | Accepted (`Erledigt`, `V4.9.40`) |
| 7 | **`ATT-4663`** | *(dup of 4667)* | `[Bug] Fix NoSuchMethodException crash when instantiating WorkoutClustersViewModel via AndroidViewModelFactory` | Consolidated into `ATT-4667` (`V4.9.40`) |
| 8 | **`ATT-4033`** | `25d8997b` | `[Verbesserung] [Low] Improve climb detection performance and route/TCX import fidelity` | Accepted (`Erledigt`, `V4.9.40`) |

---

## 2. Retrospective Root-Cause Analysis & Permanent Countermeasures (`Rule 63`)

### 2.1 Concurrent Background Pre-Warming of `agent2` (`subagent_flash`) at Ticket/Epic Start
* **Observation**: During Stage 1 (`[Analysis]`), the Coordinator previously spawned only `subagent_pro` (`agent1`) and waited until Stage 1 finished before spawning `subagent_flash` (`agent2`), serializing `subagent_flash`'s cold-start and context-reading latency at the Stage 1 $\rightarrow$ Gate 1 boundary.
* **Countermeasure (`aspice_governance.md` Core Rule 7 / `Rule 63`)**: Launch **both** `subagent_pro` (`agent1` executing Stage 1 Doing) and `subagent_flash` (`agent2` pre-warming its context in the background by reading `.agents/rules/aspice_governance.md`, the domain standard, `.agents/skills/stage2-req-test-spec/SKILL.md`, and the Jira ticket/target files) concurrently in the initial `invoke_subagent(Subagents=[...])` call. Because `subagent_pro`'s Stage 1 analysis takes longer than `subagent_flash`'s context warm-up, `subagent_flash` is already warm and idle when Stage 1 finishes.

### 2.2 Mandatory Proactive `Recommendation` Section in Ceremony 2 Sprint Review
* **Observation**: Including an explicit agent recommendation (verdict suggestion, technical rationale, and whether/where to test on the phone vs. pure backend/unit-test verification) significantly streamlined ticket evaluation during Ceremony 2.
* **Countermeasure (`sprint-planner/SKILL.md` Ceremony 2 Step 3 / `Rule 63`)**: Every ticket presentation in Ceremony 2 must conclude with a dedicated **`Recommendation`** section stating the recommended verdict (`i.O.`, follow-up ticket, or deferred field test) and on-device verification guidance.

### 2.3 Variant-Scoped Custom URI Schemes Over Path Suffixes (`ATT-4668` $\rightarrow$ `ATT-4706`)
* **Observation**: Scoping `<data android:scheme="strava" android:host="rainerblind.github.io" android:path="/${applicationId}" />` still triggered Android's `ResolverActivity` ("Öffnen mit") on Pixel 10 because the co-installed older Release APK (`2026-10-21`) declared `scheme="strava"` and `host="rainerblind.github.io"` without `android:path`. In Android's `IntentFilter.matchData()`, an omitted path acts as a wildcard (`/*`, `MATCH_DATA_AUTHORITY = 0x308000`), and `IntentResolver` includes all matching filters of equal `priority` in the chooser.
* **Countermeasure (`docs/coding_standards.md` §3.1 / `Rule 63` / `ATT-4706`)**: Scope custom OAuth/deep-link `<intent-filter>`s between co-installed build variants by `android:scheme` (e.g., `android:scheme="strava.${applicationId}"`) whenever the OAuth provider validates only the host domain.

### 2.4 Duplicate Ticket Detection in Ceremony 1 & Strict Zero-Tool-Call Turn Termination
* **Observation**: Detecting that `ATT-4663` was a duplicate of `ATT-4667` during Ceremony 1 saved a full 5-stage ticket cycle; and ensuring zero tool calls follow `invoke_subagent` / `send_message` guarantees immediate reactive `<SYSTEM_MESSAGE>` wakeup.
* **Countermeasure (`sprint-planner/SKILL.md` Ceremony 1 Step 3 & `aspice_governance.md` Core Rule 7 / `Rule 63`)**: Proactively screen the sprint backlog for duplicate/overlapping tickets in Ceremony 1, and enforce `invoke_subagent` / `send_message` as the strictly final tool call of a Coordinator turn.
