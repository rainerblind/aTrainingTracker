# Sprint Review & Retrospective — Sprint 2026-43.2 (`ATT-4376`)

**Ticket**: [ATT-4376](https://rainerblind.atlassian.net/browse/ATT-4376)  
**Sprint**: `2026-43.2`  
**Branch**: `sprint/2026-43.2` $\rightarrow$ `develop`  
**Target Release**: `V4.9.40`  
**Date**: 2026-10-20  

---

## 1. Sprint Review Summary (11 Completed Tickets)

All 11 completed tickets on `sprint/2026-43.2` were deployed to the Google Pixel 10 (`./gradlew installDebug`), reviewed in strict backlog rank order (`ORDER BY rank ASC`), and accepted with `FixVersion = V4.9.40`:

| Rank | Ticket | Summary | Commit | Review Outcome & Follow-Ups |
| :---: | :--- | :--- | :---: | :--- |
| 1 | **`ATT-4375`** | Align location and sensor priority order across `DeviceType`, `MySensorManager`, `DevicesDatabaseManager`, and `SensorSourceDialog` | `1f00f881` | **Accepted (i.O.)** |
| 2 | **`ATT-4374`** | Decouple `SensorSourceDialog` 'Source' and 'Active Backups' header colors from route blue tokens and restore darker/lighter green | `8ee8d14f` | **Accepted (i.O.)** |
| 3 | **`ATT-4373`** | Do not navigate to first Cockpit tab when Auto-Pause ends | `c4834283` | **Accepted (i.O.)** |
| 4 | **`ATT-4372`** | Polish `BatchImportSportTypeDialog` layout (fix truncated segmented buttons and vertical 'Andere' chip wrap) and default to Other | `49c12c7a` | **Accepted (i.O.)** — Follow-up **`ATT-4430`** created to place `Other` (`Andere`) chip on the far right (`[Bike, Run, Other]`). |
| 5 | **`ATT-4371`** | Fix Bike Bell audio playback (`USAGE_MEDIA` stream, static `AudioTrack` replay, and enable outside `TRACKING` mode) | `bac64ff9` | **Accepted (i.O.)** — Follow-up **`ATT-4431`** created to restore long-press continuous ring (`"Brriiiing!"`) on `BikeBellButton`. |
| 6 | **`ATT-4370`** | Align Power Histogram tail cutoff to zone boundary and make header range chip highlighting consistent | `5886aeca` | **Accepted (i.O.)** |
| 7 | **`ATT-4369`** | Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings | `157684c0` | **Accepted (i.O.)** — Follow-up **`ATT-4432`** created to remove the unrequested `Basic Power Metrics (Work, W/kg)` toggle and style subordinate switches with lighter text and a smaller `Switch`. |
| 8 | **`ATT-4368`** | Hide `W'bal` row in Workout Summary when workout has no recorded power data | `9fce3cd7` | **Accepted (i.O.)** |
| 9 | **`ATT-4367`** | Support elevation gain interval filtering (min/max range and preset intervals) in Routes, Favorite Routes, and Workouts | `820ae263` | **Accepted (i.O.)** |
| 10 | **`ATT-3998`** | `[High]` Bluetooth and notification permissions only requested when location is missing | `21f5c90e` | **Accepted (i.O.)** |
| 11 | **`ATT-3999`** | `[High]` Remove blocking `runBlocking` DataStore reads from UI and sensor hot paths | `846373e5` | **Accepted (i.O.)** |

---

## 2. Retrospective: What Went Well

1. **In-Sprint Process Improvement: Persistent `pro`/`flash` Ping-Pong Subagents per Ticket (Core Rule 7)**:
   - Empirical timestamp analysis of `ATT-4375` (`76.5m`), `ATT-4374` (`44.4m`), and `ATT-4373` (`58.0m`) showed that spawning up to 10 fresh subagents per ticket caused severe "Stage Amnesia" (19–28 minutes per ticket spent re-reading the same Jira issue, governance rules, and source files on cold starts).
   - Adopting **two persistent `self` subagents per ticket** (`subagent_pro` and `subagent_flash`) reused via `send_message` across Stages 1–5 eliminated cold-start overhead from `ATT-4372` onward, cutting per-ticket execution time roughly in half while keeping each subagent warm from its previous Gate Review.
2. **Clean Atomic Commit History & Full-Suite Stability**:
   - Every ticket integrated onto `sprint/2026-43.2` as a single squashed atomic commit (`Core Rule 8`), with all **3,587 unit tests** passing at 100% (`0` failures).

---

## 3. Root Cause Analysis of Anomalies & Permanent Countermeasures (Rules 56–59)

### 3.1 Rule 56: Reactive Subagent Turn Boundaries — No Polling Loops or Premature `send_message` Queueing (`.agents/rules/aspice_governance.md` Core Rule 7)
* **Symptom**: During `ATT-4367` Stage 1, after `subagent_flash` finished its pre-warm turn early and woke the orchestrator while `subagent_pro` was still running, the orchestrator entered a busy-wait polling loop calling `manage_subagents(Action="list")` and `manage_task(Action="status")` repeatedly, and on `ATT-4368`/`ATT-4367` pre-queued `send_message` instructions for subsequent stages before the active stage subtask had been created.
* **Root Cause**: Failure to respect the reactive wakeup model (`<messaging>` / `<subagents>`) when one of two concurrent subagents reports back first.
* **Permanent Countermeasure (Rule 56)**:
  - Never poll `manage_subagents(Action="list")` or `manage_task(Action="status")` in a loop, and never pre-queue `send_message` instructions for a future stage before the current stage has completed and created its Jira subtask.
  - Whenever waiting on a running subagent, immediately stop calling tools to end the turn so the reactive wakeup system wakes the orchestrator only when the subagent completes its turn.

### 3.2 Rule 57: Canonical Sport Type Chip Ordering (`Other` Always on the Far Right) (`docs/design_guidelines.md` §1.2 — `ATT-4430`)
* **Symptom**: In `ATT-4372`, changing the default preselected sport in `BatchImportSportTypeDialog` from `BSportType.BIKE` to `BSportType.UNKNOWN` (`Other` / `Andere`) also reordered the chip list to `listOf(BSportType.UNKNOWN, BSportType.BIKE, BSportType.RUN)`, placing `Andere` on the far left instead of the far right.
* **Root Cause**: Conflating the *default selected value* (`selectedSport = BSportType.UNKNOWN`) with the *visual display order* of the chips.
* **Permanent Countermeasure (Rule 57)**:
  - Across all sport type selector rows (`FilterChip` rows, segmented rows), specific primary sports (`Bike`, `Run`) **MUST** appear first and the catch-all `Other` (`BSportType.UNKNOWN` / `Andere`) **MUST** always be placed on the **far right** (`[Bike, Run, Other]` or `[Run, Bike, Other]`), regardless of which sport is preselected by default.

### 3.3 Rule 58: Preserving Press-and-Hold Actions When Resolving Button Click Contention (`docs/design_guidelines.md` §5.12 — `ATT-4431`)
* **Symptom**: In `ATT-4371`, removing the nested `Modifier.pointerInput { detectTapGestures(...) }` wrapper around `ElevatedButton(onClick)` resolved single-tap pointer contention, but dropped the `onLongPress` / release callbacks that triggered the continuous Bike Bell roll (`"Brriiiing!"`).
* **Root Cause**: Replacing a multi-gesture detector with a single-click `ElevatedButton(onClick)` without migrating the press-and-hold listener to the button's `MutableInteractionSource` or `combinedClickable`.
* **Permanent Countermeasure (Rule 58)**:
  - When a button supports both single-tap (`onClick`) and press-and-hold / long-press actions (such as `BikeBellButton`), never delete the long-press handler to fix pointer contention. Observe `PressInteraction.Press` / `Release` / `Cancel` on the button's `MutableInteractionSource` (or use a `Surface` with `combinedClickable` / unified gesture handling) so both single-tap and press-and-hold work reliably.

### 3.4 Rule 59: Visual Hierarchy for Subordinate Settings Toggles & No Unrequested Model Toggles (`docs/design_guidelines.md` §3.3 — `ATT-4432`)
* **Symptom**: In `ATT-4369`, `PhysiologyModelsSection.kt` added an unrequested `"Basic Power Metrics (Work, W/kg)"` toggle and rendered the subordinate `"Show in Workout Summary"` toggles using full-sized `TuningToggleItem` rows (with only `16.dp` start padding) that visually competed with the parent model headers.
* **Root Cause**: Reusing the primary card/section toggle component verbatim for a secondary child option, and over-generalizing power slot fallbacks into a new user-facing settings toggle.
* **Permanent Countermeasure (Rule 59)**:
  - Subordinate child switches nested under a primary feature toggle **MUST** use a visually lighter hierarchy: smaller/lighter text (`bodySmall` / `labelMedium` in `MaterialTheme.colorScheme.onSurfaceVariant`), compact vertical spacing, and a scaled-down `Switch` (`Modifier.scale(0.8f)`).
  - Do not introduce extra top-level settings toggles for baseline metrics (such as mechanical work `kJ` or `W/kg`) that were not requested in the ticket specification.
