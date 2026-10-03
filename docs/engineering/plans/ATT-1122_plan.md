# Implementation Plan: Autonomous Multi-Model Independent Review Agent & Automated Gate Audits (ATT-1122)

## 1. Goal & Context
Upgrade developer tooling and ASPICE process infrastructure to establish genuine cognitive and architectural independence for AI quality gate audits (Gates 1 through 5).

To eliminate self-review confirmation bias when pairing with single AI sessions, we implement `tools/review_agent.py`, a dedicated out-of-process runner using the Python 3 standard library. It connects to external LLM providers (Google Gemini REST API via `.env.gemini` and Anthropic Claude Messages API via `.env.claude`), automatically determines the active stage and gate checklist from the sub-task, ingests repository context and active git diffs, executes an objective audit, posts the formatted review comment to Jira, and transitions the sub-task to `Freigabe (Human)`.

## 2. Traceability & Scope Mapping
* **Requirement**: `REQ-PRO-018` (Autonomous Multi-Model Independent Review Agent & Automated Gate Audits)
* **Verification Test**: `TST-PRO-011` (Autonomous Multi-Model Independent Review Agent & Automated Gate Audits Verification)
* **Target Files**:
  1. `tools/review_agent.py` [NEW]
  2. `docs/project_protocol.md` [MODIFY]
  3. `.cursorrules` [MODIFY]
  4. `tools/jira_util.py` [MODIFY] (integrate review agent helper command if appropriate)

## 3. Detailed Component Changes

### A. New Runner: `tools/review_agent.py`
1. **Zero External Dependencies**:
   * Uses only `urllib.request`, `json`, `os`, `sys`, `subprocess`, `re`, `argparse`.
2. **Provider Adapters**:
   * **Gemini Adapter**:
     - Loads `GEMINI_API_KEY` from `.env.gemini`.
     - Calls Google Generative Language REST API (`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key={API_KEY}`).
   * **Claude Adapter**:
     - Loads `ANTHROPIC_API_KEY` from `.env.claude`.
     - Calls Anthropic Messages REST API (`https://api.anthropic.com/v1/messages`) using `anthropic-version: 2023-06-01`.
   * **Provider Fallback & Selection**:
     - Default provider preference: Gemini (active working quota verified), with CLI override `--provider [gemini|claude]`.
     - Automatic fallback if quota or network errors occur.
3. **Context Assembly Engine**:
   * Inspects sub-task via `jira_util.jira_request`: fetches `summary`, `description`, `parent`, and `status`.
   * Maps sub-task summary prefix to ASPICE Gate:
     - `[Analysis]` -> Gate 1: Problem Domain & Analysis Review
     - `[Test-Spec]` -> Gate 2: Test Specification & Requirements Review
     - `[Impl-Plan]` -> Gate 3: Architectural & Invariant Plan Review
     - `[Implementation]` -> Gate 4: Code Quality, Localization & Side-Effect Review
     - `[Test]` -> Gate 5: Clean-Room Full-Suite Regression & Release Review
   * Gathers live context:
     - For all gates: Parent issue description and requirements.
     - For Gate 1 & 2: `docs/requirements.md` and `docs/tests.md`.
     - For Gate 3: `docs/engineering/plans/` artifact if referenced.
     - For Gate 4: `git diff HEAD~1` or working tree diff against base branch, modified files list.
     - For Gate 5: `./gradlew testDebugUnitTest` execution output or logs, parent ticket `Lösungsversion`.
4. **Deterministic System Auditor Prompting**:
   * Injects the strict auditor persona: *"You are Agent 2, the Senior Independent ASPICE Quality & Safety Auditor for aTrainingTracker. You must conduct a critical, adversarial review without bias. Check all items for Gate X defined in docs/project_protocol.md."*
   * Formats response strictly in Jira Wiki markup (`h2.`, `h3.`, `*bold*`, `{code}`).
   * Must include:
     - *Audit Decision*: `RECOMMEND PASS`, `CHALLENGED`, or `RECOMMEND REVISION`.
     - *Risk Level*: `LOW`, `MEDIUM`, or `HIGH` with technical justification.
     - Itemized gate checks.
     - Preserved invariants check.
5. **Jira Transition & Commenting**:
   * Posts audit report comment prefixed with `[Automated comment by AI Agent (External Auditor)]` via Jira API.
   * Transitions sub-task to `Freigabe (Human)`.
   * Strictly enforces that transitioning to `Erledigt` is prohibited.
6. **CLI Interface**:
   * `python3 tools/review_agent.py audit <SUBTASK_KEY> [--provider gemini|claude]`
   * `python3 tools/review_agent.py test-connection [--provider gemini|claude]`

### B. `docs/project_protocol.md`
* Update Section 4 (*Jira Ticket Management & Dual-Agent Workflow*) and Section 12 (*Five-Gate AI Review Protocol*):
  - Codify the independent review runner `tools/review_agent.py`.
  - Detail that Agent 2 audits can be triggered autonomously via `tools/review_agent.py` to guarantee genuine cognitive separation from Agent 1.

### C. `.cursorrules`
* Add explicit reference to `tools/review_agent.py` under the Mandatory Workflow and Quality Standards, instructing agents that Gate 1-5 reviews can be dispatched to the independent reviewer tool.

## 4. Verification Plan (TST-PRO-011)
1. **Static / Unit Verification**:
   * Run syntax validation on `tools/review_agent.py`.
   * Test provider credential loader with both `.env.gemini` and `.env.claude`.
   * Test connection command: `python3 tools/review_agent.py test-connection`.
2. **Mock / Dry-Run Verification**:
   * Verify gate stage detection across `[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`.
   * Verify human gate protection: confirm CLI refuses to execute transitions to `Erledigt`.
3. **Live Gate Audit Verification**:
   * Execute live audit on a real sub-task or test issue to verify Jira comment formatting and transition to `Freigabe (Human)`.
4. **Clean-Room Regression**:
   * Run `./gradlew testDebugUnitTest` to confirm zero regressions in existing code.

## 5. System Invariants & Preserved Behavior
* **Human Gate**: Moving sub-tasks or parent tickets to `Erledigt` remains strictly reserved for the human user.
* **Dependencies**: Zero external pip dependencies; runs on standard Python 3.
* **Security**: API keys are read strictly from local `.env.*` files and never logged, output, or committed.
* **Runtime Impact**: Zero modifications to Android runtime source code or APK size.
