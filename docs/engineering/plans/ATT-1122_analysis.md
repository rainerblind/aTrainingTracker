# Stage 1: Analysis - ATT-1122: Reviews by other agents (Independent External Reviewer)

## 1. Domain Comprehension & Motivation
### Problem Statement
Under the unified 5-stage ASPICE development workflow (`docs/project_protocol.md`), each lifecycle stage requires a dual-agent structure:
* **Agent 1 (Author)** performs the primary technical work (Analysis, Test Specification, Implementation Planning, Software Construction, or Test Verification) and updates the Jira sub-task Description.
* **Agent 2 (Senior Auditor)** conducts an independent review across 5 quality gates (Gate 1 through Gate 5), posts an evaluation comment on Jira, and moves the sub-task to `Freigabe (Human)`.

However, in actual single-session AI pair programming, the primary IDE AI model instance often performs both roles sequentially. While prompted with an "Auditor" persona, evaluating one's own freshly generated code within the same memory context suffers from severe confirmation bias and lacks genuine architectural independence.

### User Motivation & Strategic Goal
The objective of ATT-1122 is to provide **true independence** for the review role by establishing an automated external review runner:
1. **Model & Context Decoupling**: The review is conducted by a distinct LLM entity (such as Anthropic Claude via `.env.claude` or Google Gemini via `.env.gemini`, or an isolated non-overlapping session/process) that has zero shared conversation state or author bias.
2. **Deterministic Gate Enforcement**: The review agent focuses strictly on the ASPICE audit criteria defined in `docs/project_protocol.md`:
   - Gate 1: Problem domain comprehension, user motivation, scope boundaries, side-effects, call-site analysis, requirement mapping, and system invariants.
   - Gate 2: Requirement phrasing standards (SHALL/MUST, atomic, state-oriented, system invariants, Given-When-Then), and test procedure coverage in `docs/tests.md`.
   - Gate 3: Architectural layering boundaries (`SWE.2`), invariant safety, and test coverage mapping.
   - Gate 4: Code diff scrutiny against approved plan, adjacent call sites, self-documenting headers (KDoc/JavaDoc), and 9-language localization parity.
   - Gate 5: Clean-room full-suite regression logs (`./gradlew testDebugUnitTest`), living documentation synchronization (`Verified`), and mandatory `Lösungsversion` (*Fix Version/s*) assignment.
3. **Seamless Jira Integration**: The independent reviewer inspects the Jira parent and sub-task deliverables, queries Git status and diffs when applicable, formulates an objective technical critique with an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`), posts the comment prefixed with `[Automated comment by AI Agent (External Auditor)]`, and transitions the sub-task to `Freigabe (Human)`.

---

## 2. Architecture & Design Options Analysis

### Option A: Standalone Python Tool (`tools/review_agent.py`)
* **Mechanism**:
  - Implements an autonomous CLI runner invoked manually or via background process/hooks: `python3 tools/review_agent.py audit <SUBTASK_KEY> [--provider claude|gemini]`.
  - Reads API credentials from existing local `.env.claude` (`ANTHROPIC_API_KEY`) or `.env.gemini` (`GEMINI_API_KEY`).
  - Fetches sub-task description, parent ticket details, and active Git state (diff, changed files, requirements).
  - Prepares a specialized, highly critical auditor prompt detailing the specific Gate (1 to 5) checklist from `docs/project_protocol.md`.
  - Sends a single zero-shot evaluation request to the chosen external API using Python standard library (`urllib.request`) to ensure zero external pip dependency overhead.
  - Formats the resulting evaluation in Jira Wiki markup, posts the comment via `jira_util.py add_comment`, and transitions the sub-task to `Freigabe (Human)`.
* **Pros**:
  - 100% truly independent process and model family.
  - Zero pollution of the active IDE conversation history or context window.
  - Works consistently regardless of which IDE or AI assistant is acting as Agent 1.
  - Standard library only (`urllib`, `json`, `subprocess`, `os`, `sys`).
* **Cons**:
  - Requires valid API keys with active quotas in `.env.claude` or `.env.gemini` (both already present in repository).

### Option B: Built-in Subagent in Antigravity IDE
* **Mechanism**:
  - Agent 1 launches a specialized subagent with a dedicated auditor instruction.
* **Pros**:
  - Runs directly within the IDE platform.
* **Cons**:
  - Shares the same underlying LLM provider/account, and cannot easily be triggered outside the specific IDE or by external automation.

### Selection: Option A (Standalone Multi-Model Review Runner `tools/review_agent.py`)
Option A delivers genuine architectural independence, model diversity (e.g. Claude auditing Gemini, or Gemini auditing Claude), and seamless scriptable execution.

---

## 3. Scope Boundaries & Lifecycle Impact
* **Scope**:
  - Implement `tools/review_agent.py` supporting Anthropic Claude API (Messages API) and Google Gemini API (GenerateContent REST API).
  - Automatically detect the active stage / gate based on sub-task summary (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`).
  - Gather contextual artifacts: sub-task description, parent ticket summary/description, `docs/requirements.md`, `docs/tests.md`, git diff (for Gates 3-5), and unit test results (for Gate 5).
  - Construct auditor prompts adhering to Gate 1-5 checklists in `docs/project_protocol.md`.
  - Post the structured review to Jira and execute the transition to `Freigabe (Human)`.
  - Update `docs/project_protocol.md` and `.cursorrules` to document the independent reviewer tool and its invocation.
* **Exclusions**:
  - Transitioning tickets or sub-tasks to `Erledigt` remains strictly reserved for the human user.
  - The tool will NOT alter production source code directly.

---

## 4. Call Site & File Modification Audit
* Target files to add/modify:
  - `tools/review_agent.py` [NEW]
  - `docs/project_protocol.md` [MODIFY]
  - `.cursorrules` [MODIFY]
  - `docs/requirements.md` [MODIFY] (Add `REQ-PRO-018`)
  - `docs/tests.md` [MODIFY] (Add `TST-PRO-011`)
* Zero impact on Android runtime, `app/src/main/`, or battery/hardware layers.

---

## 5. System Invariants & Preserved Behavior
* **Invariant 1 (Human Gate)**: Moving sub-tasks or tickets to `Erledigt` remains strictly forbidden for both Agent 1 and the external review agent.
* **Invariant 2 (Jira Protocol Integrity)**: The existing 5-state lifecycle (`Zu erledigen` -> `In Bearbeitung` -> `In Überprüfung` -> `Freigabe (Human)` -> `Erledigt`) and automated sub-task flow must remain 100% compatible.
* **Invariant 3 (Zero External Pip Dependencies)**: The script MUST operate exclusively using the Python 3 standard library (`urllib.request`, `json`, `os`, `sys`, `subprocess`).
* **Invariant 4 (Credential Security)**: API keys must remain strictly in `.env.claude` and `.env.gemini` (both ignored in `.gitignore`) and never leaked in logs or committed.
