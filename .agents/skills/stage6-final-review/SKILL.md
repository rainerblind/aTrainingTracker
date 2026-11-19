---
name: stage6-final-review
description: Executes Stage 6 ('Final Review (Agent)') of the agile ASPICE lifecycle. Conducts an independent, zero-context, adversarial Cold Final Review (Model 'flash' for low-risk/1-2 SP/doc/tooling/single-component fixes; Model 'pro' for high-risk/architectural/complex UI/concurrency/bounce escalations) of git diff sprint/<SPRINT_NAME>...feature/<KEY>, enforces up to 3 autonomous healing bounces on feature/<KEY>, and triggers atomic --merge-ticket squash onto sprint/<SPRINT_NAME> strictly post-PASS before advancing to 'Final Review (Human)'.
---

# Skill: stage6-final-review

## Overview
This skill guides the agent through **Stage 6 (`Final Review (Agent)` / Gate 6)**. While Stages 1–5 (`Analysis` $\rightarrow$ `Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test`) are executed by a persistent Epic-scoped subagent pair (`Rule 7.2`, `Rule 7.4`), **Stage 6 (`Final Review (Agent)`)** introduces an orthogonal Swiss Cheese defensive barrier ($p_6$).

A freshly spawned **Cold Reviewer** (`Model: "flash"` for routine / Low-Risk / `1–2 SP` / tooling / doc / single-component fixes; `Model: "pro"` **only for complex or graphical tasks** — High-Risk / multi-component architectural, concurrency/DB/sensor-protocol, graphical UI/Canvas/Chart/Icon visual tasks, or Bounce 2–3 escalations per `Rule 7.2`) — with **zero shared conversational history** from Stages 1–5 — acts as a **skeptical, adversarial Principal Engineer** auditing the ticket branch diff (`git diff sprint/<SPRINT_NAME>...feature/<KEY>`) *before* atomic squash integration onto `sprint/<SPRINT_NAME>` and *before* the ticket reaches the human Product Owner (`Final Review (Human)`).

---

## Mandatory Anti-Rubber-Stamping Rules

1. **Strict Scope Limit (1–3 Tickets Max per Review Turn/Subagent)**:
   - **NEVER** batch 10–16 tickets into a single Cold Reviewer invocation. Large batches cause LLM fatigue and superficial `git show --stat` rubber-stamping.
   - Each Cold Reviewer invocation MUST audit **1 ticket** (or at most **2–3 tightly coupled tickets** modifying the same subsystem) so it can read every modified production file and its callers/callees line by line.
2. **Strict Anti-Anchoring (Do NOT Read `walkthrough.md` First)**:
   - The Cold Reviewer MUST **NOT** read the implementing agent's `walkthrough.md`, `plan.md`, or `analysis.md` before auditing the code. Reading the author's self-justification anchors the reviewer and hides blind spots.
   - Start strictly from:
     1. The original Jira ticket requirements (`python3 tools/jira_util.py show <TICKET_KEY>`),
     2. The full production & test diff (`git diff sprint/<SPRINT_NAME>...feature/<KEY> -- app/src/main/ app/src/test/ tools/`), and
     3. The **full surrounding production source files** (`read_file` on the modified classes and their callers/callees in the `feature/<KEY>` worktree).
3. **Full Production Code Diff Inspection + Mandatory Live Emulator/Simulator Verification (`emulator-5554`) & Visual `read_file` Critique (`Rule 3.3`, ASPICE `SWE.6` / `SYS.5`)**:
   - Running only `git diff --stat` / `git show --stat` is a **severe governance violation**. You MUST read every line of production code in `git diff sprint/<SPRINT_NAME>...feature/<KEY>` plus the surrounding methods/classes in the `feature/<KEY>` working tree.
   - **Live Target Verification on `emulator-5554` & Simulators (All App / UI / Sensor / Service / DB Tickets)**:
     1. Run **incremental** targeted unit tests strictly matching the ticket's modified/adjacent test classes (`python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --tests <TicketTouchedSuites>` — **NEVER `--rerun-tasks`**, and defer full-repo `build.*` contract suites to the Lean Tier-2 `Schichtwechsel` Gate per `Rule 7.5`).
     2. Ensure the agent emulator is booted and seeded (`python3 tools/agent_emulator.py ensure`), then install the `feature/<KEY>` build (`ANDROID_SERIAL=emulator-5554 ./gradlew installDebug`).
     3. Launch `com.atrainingtracker.debug` (`adb -s emulator-5554 shell am start -n com.atrainingtracker.debug/com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation`), navigate to the modified screen(s)/dialog(s), resolve tap coordinates deterministically via `uiautomator dump` before `adb -s emulator-5554 shell input tap`, and exercise the real interactive flow (including sensor simulators such as `tools/ble_radar_simulator.py`, mock streams, or seeded SQLite workouts).
     4. Inspect `adb -s emulator-5554 logcat -d` to verify zero crashes, zero self-cancelling coroutine exceptions (`ForgottenCoroutineScopeException`), zero ANRs, and zero multi-second UI stalls when a sensor is disconnected.
     5. Capture a live verification screenshot (`adb -s emulator-5554 exec-out screencap -p > /tmp/stage6_<KEY>_verify.png`) and inspect it with `read_file`.
     *(Note: Pure Python/markdown governance tickets without app runtime impact may skip APK installation while still auditing all diffed files and images; `--merge-ticket` acquires the merge lock strictly *after* `final-review-verdict PASS`).*
   - **Mandatory `read_file` Visual Critique of All Screenshots & Images (`Rule 8.5`)**:
     - Call `read_file` on **every** `.png`/`.jpg` asset created, modified, or referenced by the ticket (`docs/engineering/walkthroughs/img/*.png`, `docs/guide/img/*.png`, `docs/store_presence/screenshots/*.png`, and `/tmp/stage6_<KEY>_verify.png`).
     - Execute `BOUNCE` immediately if:
       * Any screenshot does not match its caption/section heading (swapped images or legacy V4 XML views).
       * Any screenshot shows an idle/unstarted workout (`"Nicht gestartet"`, `0,0 km/h`, `-- bpm`), a zoomed-out world map (zoom level 0), or scrolled content overlapping the Android status bar.
       * Badges, icons, or text are illegible at phone scale, or chart axes violate domain/physiological semantics (e.g., inverted effort direction).
   - **4-Point Real-Device Failure-Mode Audit**:
     1. *Self-Cancelling Composable Scopes*: Never launch an async action on a `rememberCoroutineScope()` inside a conditional Composable (`if (showBanner)`) that disappears when clicked (must use `viewModelScope` or screen-level scope).
     2. *Existing SQLite / SharedPreferences Upgrade Path*: New columns/fields (e.g., `disModelNumber`) must handle already-paired devices in an existing user database where the field is initially `null` or empty.
     3. *Sensor Threshold Hysteresis*: Warning colors or UI state transitions driven by live sensor streams must include hysteresis/deadbands so boundary values (e.g., `50.0 km/h`) do not oscillate at 1–5 Hz.
     4. *Disconnected Sensor Fast-Fail*: Stop-time / sync hooks must check connectivity/advertising state before blocking UI transitions for 15–45 seconds.
4. **Adversarial Bug-Hunter Mindset**:
   - Do **not** look for excuses to pass a ticket. Assume the implementing pair missed at least one subtle edge case, lifecycle race, sibling screen/protocol, or unit mismatch, and actively hunt for it. If you find *any* genuine defect, incomplete wiring, compiler warning, broken edge case, or governance discrepancy, you **MUST** execute `BOUNCE` (never excuse or rationalize it in `<thinking>`).
5. **Mandatory `Pros` vs. `Cons / Defects Identified` & *"Would You Recommend Its Approval?"* Gate (`Rule 63` — `ATT-4670`)**:
   - For every ticket, explicitly formulate **Pros** and **Cons / Defects Identified**, and answer the question: ***"Would you recommend its approval?"***
   - Whereas Ceremony 2 (under *"No code changes during Review"*) turned *Cons / Defects Identified* into *"Accept with Follow-Up Bug Ticket"*, **Stage 6 (`Final Review (Agent)`)** exists to heal those defects *before* the human review: **if *Cons / Defects Identified* is non-empty, your answer to *"Would you recommend its approval?"* MUST be `NO — BOUNCE`**, returning the ticket to `Implementation` so it is fixed immediately rather than deferring a follow-up ticket.

---

## 6-Point Adversarial Code & Architecture Audit (`--as agent2`)

For each ticket in `Final Review (Agent)`, trace the implementation across all 6 dimensions:

1. **End-to-End Execution & Lifecycle Trace (Callers $\rightarrow$ Callees $\rightarrow$ Storage/UI)**:
   - Open the modified production classes and trace every caller and callee.
   - Are new classes, methods, dialogs, `SensorType` enums, or settings preferences actually wired and reachable from live production entry points (`Rule 81`)?
   - What happens across lifecycle boundaries: workout pause/resume/stop, new workout start (are accumulators/buffers cleared?), sensor disconnect/reconnect (are listeners re-registered or leaked?), app backgrounding/process recreation, or dialog dismissal?
2. **Math, Units, & Protocol Forensics**:
   - Check every formula, threshold, and unit conversion: milliseconds vs. seconds, UTC epoch vs. monotonic/relative timestamps, `m/s` vs. `km/h`, `<` vs. `<=`, window boundary off-by-one errors, division by zero, `NaN`/`Infinity` propagation, and null/empty collection handling.
   - Check hardware/file protocol details (ANT+ vs. BLE GATT queueing, FIT/TCX/GPX timestamp & field semantics, SQLite cursor/transaction safety).
3. **Sibling & Counterpart Parity**:
   - If a bug was fixed or a feature added in one place (e.g., `AdvancedTuningScreen`, BLE radar, or workout export), did the author miss its sibling counterpart (e.g., `AdvancedTuningDialog`, ANT+ radar, `LegacyImportEngine`, or dark/light theme)?
4. **Concurrency, Threading, & 1-Hz Loop Performance**:
   - Is any SQLite query, SharedPreferences read, object allocation, or uncached baseline calculation happening on the Main UI thread or inside a 1-Hz sensor callback?
   - Are shared mutable state / `SnapshotStateObserver` / coroutine flows accessed safely across threads?
   - Are there any new Kotlin/Java compiler warnings (e.g., always-true/false conditions, unchecked casts, deprecated APIs)?
5. **UI/UX, Material 3 Guidelines (`docs/design_guidelines.md`), & 9-Language Localization**:
   - Are any user-facing strings hardcoded in Kotlin or English-only instead of localized across all 9 language `strings.xml` files?
   - Can Compose layouts collapse to `0px` height/width, clip text, or leave ghost tap targets when hidden?
   - **Mandatory `docs/design_guidelines.md` Bounce Triggers** (execute `BOUNCE` immediately if any of these appear in the diff):
     - **Binary / 3-Way Exclusive Choices**: Using `FilterChip` instead of `SingleChoiceSegmentedButtonRow` / `SegmentedButton(..., icon = {})` (or `TuningSegmentedButtonRow`).
     - **Expert Settings (`ui/settings/tuning/categories/`)**: Using raw unscaled `Switch(`, raw `Slider(`, raw `FilterChip(`, custom `titleSmall` headings, or nested `ElevatedCard(` inside an `ExpertCategoryCard` instead of the standardized components in `TuningFormControls.kt` (`TuningToggleItem`, `TuningSliderItem`, `TuningSegmentedButtonRow`, `TuningDropdownRow`).
     - **Side-by-Side Action Buttons & German String Length**: Side-by-side `Row` buttons where longer German labels (e.g., *"Messung speichern"*) wrap onto two lines on phone screens, or where a warning/discard recommendation still highlights the "Save" button as the filled primary CTA instead of swapping primary/secondary emphasis.
6. **Test Authenticity & Hygiene Verification**:
   - Inspect `git diff sprint/<SPRINT_NAME>...feature/<KEY> -- app/src/test/`. Do the unit tests instantiate and exercise the **real production class/method**, or do they test a tautological mock / local copy that would pass even if the production code were deleted?
   - Are there any cross-ticket collateral files accidentally included in `feature/<KEY>`?
   - Are all 4 canonical deliverables (`docs/engineering/{analysis,test_specs,plans,walkthroughs}/<KEY>_*.md`) present and all `REQ-XXX` / `TST-XXX` marked `[Verified]`? (Enforced deterministically by `jira_util.py`).

---

## Recording the Stage 6 Verdict (`final-review-verdict`)

### Case A — `BOUNCE` (3-Bounce Healing Loop $\rightarrow$ `Implementation` / `Analysis` / `Zu erledigen`)
Whenever **any** functional bug, lifecycle leak, math/unit flaw, missing counterpart wiring, compiler warning, fake test, or documentation defect is found:
```bash
python3 tools/jira_util.py --as agent2 final-review-verdict <TICKET_KEY> BOUNCE "h3. Cold Final Review (Agent) — Defect Report
* *Finding 1 (<Category>)*: <Exact File:Line & root cause explanation>
* *Finding 2 (<Category>)*: <Exact File:Line & root cause explanation>
* *Required Fix*: <Concrete code & test remediation required>
* *Verification*: <Exact test command that must pass>" --bounce-target=implementation
```
- **Bounces `1/3`, `2/3`, `3/3`**:
  1. `jira_util.py` tags the comment `[FINAL REVIEW (AGENT) — BOUNCE X/3]`, transitions `<TICKET_KEY>` back to `Implementation` (or `Analysis` / `Zu erledigen`), reopens target/downstream subtasks where Jira allows, and assigns `<TICKET_KEY>` to `agent1`. Because `feature/<KEY>` was NOT prematurely merged, `sprint/<SPRINT_NAME>` remains completely unpolluted.
  2. The implementing agent/pair fixes the defect on `feature/<KEY>`, runs the targeted tests and `verify_requirement_governance.py`, re-submits Stage 4/5 (`submit-stage` / `gate-verdict`, reusing active/reopened subtasks or numbered bounce subtasks when `Erledigt` has `transitions: []`), commits cleanly on `feature/<KEY>`, and advances back to `Final Review (Agent)`.
  3. The Cold Reviewer re-audits `git diff sprint/<SPRINT_NAME>...feature/<KEY>` and records `final-review-verdict <TICKET_KEY> PASS ...` (or another `BOUNCE`).
- **Escalation After 3 Bounces (`> 3`)**:
  - If a 4th bounce is triggered, `jira_util.py` automatically tags `[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]`, transitions `<TICKET_KEY>` to `Final Review (Human)`, and assigns it to `human` so the Product Owner can inspect the disputed `feature/<KEY>` branch during Ceremony 2 without an infinite loop or unapproved sprint-branch pollution.

### Case B — `PASS` (`I.O.` $\rightarrow$ Atomic Sprint Merge & `Final Review (Human)`)
Only when all 6 adversarial audit dimensions are verified clean (or after all bounced defects have been healed and re-verified), record the `PASS` verdict and then execute the single atomic `--merge-ticket` squash onto `sprint/<SPRINT_NAME>`:
```bash
python3 tools/jira_util.py --as agent2 final-review-verdict <TICKET_KEY> PASS "h3. Cold Final Review (Agent) — Executive Summary
* *Commit*: <FEATURE_HEAD_SHA>
* *What Changed*: <1-2 concise bullets summarizing the production change>
* *Adversarial Audit Checks*: <Specific callers, lifecycle paths, math boundaries, and counterpart classes inspected>
* *Pros (Was ist gut/gelungen?)*:
  * <Clean architectural separation, invariant protection, realistic test assertions>
* *Cons / Auffälligkeiten / Defects Identified (Was ist kritisch, verbesserungsbedürftig oder unvollständig?)*:
  * <Enumerate any observed flaws/quirks, or 'Keine kritischen Mängel festgestellt'>
* *Würdest du das freigeben? (Freigabe-Empfehlung)*:
  * **JA — Freigabe empfohlen** (bzw. **NEIN — BOUNCE** if any defect found) mit prägnanter Begründung.
* *Requirements & Tests Verified*: <REQ-XXX / TST-XXX> (<TestClassName> — 100% pass)
* *Residual Risk / Human Focus*: <Low/Medium — specific area for PO spot-check on Pixel 10 if applicable>"
```
This atomically verifies deliverable/requirement hygiene, validates presence of the mandatory review ceremony questions, posts `[FINAL REVIEW (AGENT) — PASS]`, transitions `<TICKET_KEY>` to `Final Review (Human)`, and assigns it to `human`. Immediately after `PASS` (or by Coordinator on `PASS` receipt), integrate `feature/<KEY>` onto `sprint/<SPRINT_NAME>` as strictly one squashed atomic commit (`REQ-PRO-125`):
```bash
python3 tools/parallel_sprint_lock.py --merge-ticket <TICKET_KEY> --sprint sprint/<SPRINT_NAME> --summary "<TICKET_KEY>: <Concise Ticket Summary>"
```
