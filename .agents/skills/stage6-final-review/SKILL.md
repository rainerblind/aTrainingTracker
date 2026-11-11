---
name: stage6-final-review
description: Executes Stage 6 ('Final Review (Agent)') of the agile ASPICE lifecycle. Conducts an independent, zero-context, adversarial Cold Carbon (Model: 'pro') code & architecture review of the squashed commit on the sprint branch, enforces up to 3 autonomous healing bounces back to the implementing pair, and posts a structured Executive Review Summary before advancing the ticket to 'Final Review (Human)'.
---

# Skill: stage6-final-review

## Overview
This skill guides the agent through **Stage 6 (`Final Review (Agent)` / Gate 6)**. While Stages 1–5 (`Analysis` $\rightarrow$ `Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test`) are executed by a persistent Epic-scoped `pro`/`flash` subagent pair (`Rules 60–63`), **Stage 6 (`Final Review (Agent)`)** introduces an orthogonal Swiss Cheese defensive barrier ($p_6$).

A freshly spawned **Cold `Gemini 4 Carbon` (`Model: "pro"`)** reviewer — with **zero shared conversational history** from Stages 1–5 — acts as a **skeptical, adversarial Principal Engineer** auditing the integrated commit on `sprint/<SPRINT_NAME>` to uncover subtle runtime bugs, math/unit errors, lifecycle leaks, incomplete UI/protocol wiring, and test blind spots *before* the ticket reaches the human Product Owner (`Final Review (Human)`).

---

## Mandatory Anti-Rubber-Stamping Rules

1. **Strict Scope Limit (1–3 Tickets Max per Review Turn/Subagent)**:
   - **NEVER** batch 10–16 tickets into a single Cold Reviewer invocation. Large batches cause LLM fatigue and superficial `git show --stat` rubber-stamping.
   - Each Cold Reviewer invocation MUST audit **1 ticket** (or at most **2–3 tightly coupled tickets** modifying the same subsystem) so it can read every modified production file and its callers/callees line by line.
2. **Strict Anti-Anchoring (Do NOT Read `walkthrough.md` First)**:
   - The Cold Reviewer MUST **NOT** read the implementing agent's `walkthrough.md`, `plan.md`, or `analysis.md` before auditing the code. Reading the author's self-justification anchors the reviewer and hides blind spots.
   - Start strictly from:
     1. The original Jira ticket requirements (`python3 tools/jira_util.py show <TICKET_KEY>`),
     2. The full production & test diff (`git show <COMMIT_HASH> -- app/src/main/ app/src/test/ tools/`), and
     3. The **full surrounding production source files** (`read_file` on the modified classes and their callers/callees).
3. **Full Production Code Diff Inspection is Mandatory**:
   - Running only `git show --stat` is a **severe governance violation**. You MUST read every line of production code in `git show <COMMIT_HASH>` plus the surrounding methods/classes in the working tree.
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
5. **UI/UX, Material 3 Guidelines, & 9-Language Localization**:
   - Are any user-facing strings hardcoded in Kotlin or English-only instead of localized across all 9 language `strings.xml` files?
   - Can Compose layouts collapse to `0px` height/width, clip text, leave ghost tap targets when hidden, or violate `docs/design_guidelines.md`?
6. **Test Authenticity & Hygiene Verification**:
   - Inspect `git show <COMMIT_HASH> -- app/src/test/`. Do the unit tests instantiate and exercise the **real production class/method**, or do they test a tautological mock / local copy that would pass even if the production code were deleted?
   - Are there any cross-ticket collateral files accidentally included in the commit?
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
  1. `jira_util.py` tags the comment `[FINAL REVIEW (AGENT) — BOUNCE X/3]`, transitions `<TICKET_KEY>` back to `Implementation` (or `Analysis` / `Zu erledigen`), and assigns it to `agent1`.
  2. The implementing agent/pair fixes the defect, runs the targeted tests and `verify_requirement_governance.py`, updates the commit on `sprint/<SPRINT_NAME>`, and moves the ticket back to `Final Review (Agent)`:
     ```bash
     python3 tools/jira_util.py --as agent1 move <TICKET_KEY> "final review (agent)"
     ```
  3. The Cold Reviewer re-audits the updated commit and records `final-review-verdict <TICKET_KEY> PASS ...` (or another `BOUNCE`).
- **Escalation After 3 Bounces (`> 3`)**:
  - If a 4th bounce is triggered, `jira_util.py` automatically tags `[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]`, transitions `<TICKET_KEY>` to `Final Review (Human)`, and assigns it to `human` so the Product Owner can inspect the disputed ticket during Ceremony 2 without an infinite loop.

### Case B — `PASS` (`I.O.` $\rightarrow$ `Final Review (Human)`)
Only when all 6 adversarial audit dimensions are verified clean (or after all bounced defects have been healed and re-verified), record the `PASS` verdict:
```bash
python3 tools/jira_util.py --as agent2 final-review-verdict <TICKET_KEY> PASS "h3. Cold Final Review (Agent) — Executive Summary
* *Commit*: <COMMIT_HASH>
* *What Changed*: <1-2 concise bullets summarizing the production change>
* *Adversarial Audit Checks*: <Specific callers, lifecycle paths, math boundaries, and counterpart classes inspected>
* *Requirements & Tests Verified*: <REQ-XXX / TST-XXX> (<TestClassName> — 100% pass)
* *Residual Risk / Human Focus*: <Low/Medium — specific area for PO spot-check on Pixel 10 if applicable>"
```
This atomically verifies deliverable/requirement hygiene, posts `[FINAL REVIEW (AGENT) — PASS]`, transitions `<TICKET_KEY>` to `Final Review (Human)`, and assigns it to `human`.
