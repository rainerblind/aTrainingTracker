# Implementation Plan - ATT-1123: Multi-Account Jira Authentication & Coordinator Role Governance

## 1. Problem Description & Background
Currently, all Jira interactions performed by AI agents in the repository share a single global account configured via `JIRA_USER` and `JIRA_TOKEN` in `.env.jira`. Comments, transitions, and issues lack granular actor attribution in Jira's audit history.
Furthermore, as we introduce multi-agent orchestration involving an implementer (Agent 1), an independent auditor (Agent 2), and a coordinator, we must ensure:
1. Clear identity attribution per role in Jira actions and comments.
2. Robust atomic pair credential resolution with safe fallback to base credentials if role keys are missing.
3. Anti-spoofing and role locking so Agent 1 cannot masquerade as Agent 2.
4. Formal governance for the Coordinator role (strictly administrative/orchestrator; cannot bypass gates or close tickets to `Erledigt`).
5. Secret masking across all output streams to prevent token exposure.

## 2. Traceability & Requirements Mapping
* **REQ-PRO-019**: Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling -> Verified by **TST-PRO-012**
* **REQ-PRO-020**: Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution -> Verified by **TST-PRO-013**
* **REQ-PRO-021**: Jira Credential & Token Secret Masking in Tooling -> Verified by **TST-PRO-014**

## 3. System Invariants & Preserved Behavior
1. **Inviolable Human Decision Gate**: Moving any Jira ticket or sub-task to `Erledigt` (via "Freigabe erteilt") remains strictly prohibited for all agents (Agent 1, Agent 2, Coordinator).
2. **Backward Compatibility**: Single-account `.env.jira` files containing only `JIRA_USER` and `JIRA_TOKEN` MUST work seamlessly with zero breakage.
3. **Atomic Pair Fallback**: A role configuration is only used if BOTH username and token are non-empty. If one is missing, the system atomically falls back to the default pair to prevent mismatched credential errors.
4. **Zero External Dependencies**: All tools and test suites MUST run strictly on the Python 3 standard library.
5. **APK Runtime Invariance**: Zero modifications to Android source files, resources, Gradle configurations, or APK artifacts.

## 4. Proposed Architectural Changes

### Component 1: `tools/jira_util.py` (Authentication, Precedence, Attribution & Masking)
* **Role Configuration Resolution**:
  * Define recognized roles: `agent1`, `agent2`, `coordinator`.
  * Define `resolve_account(role, config)`:
    * Map `agent1` -> `(JIRA_AGENT1_USER, JIRA_AGENT1_TOKEN)`
    * Map `agent2` -> `(JIRA_AGENT2_USER, JIRA_AGENT2_TOKEN)`
    * Map `coordinator` -> `(JIRA_COORDINATOR_USER, JIRA_COORDINATOR_TOKEN)`
    * If both are present and non-empty, return `(user, token)`.
    * If one is set and the other empty/unset, emit advisory notice to `sys.stderr` and fall back to `(JIRA_USER, JIRA_TOKEN)`.
    * If neither is set, fall back to `(JIRA_USER, JIRA_TOKEN)`.
    * If neither role nor default credentials exist, print diagnostic error to `sys.stderr` and exit with code 1.
* **Precedence Rule & CLI Argument Parsing**:
  * Precedence: `CLI flag (--as <role>)` > `Env var (JIRA_ACTOR)` > `Script default ('agent1')`.
  * Validate role against recognized roles; invalid role halts with exit code 1.
* **Comment Attribution Prefix**:
  * Format prefix dynamically based on active role:
    * `agent1`: `[Automated comment by AI Agent 1 (Implementer)]\n\n`
    * `agent2`: `[Automated comment by AI Agent 2 (Auditor)]\n\n`
    * `coordinator`: `[Automated comment by AI Coordinator]\n\n`
    * default / fallback: `[Automated comment by AI Agent]\n\n`
* **Secret Masking & Output Safety**:
  * Never print tokens or Authorization header contents.
  * In exception handlers, sanitize printed URLs and suppress raw auth payloads.

### Component 2: `tools/review_agent.py` (Hardwired Auditor Role & Multi-Model Stability)
* **Hardwired Auditor Role**:
  * Pass `role="agent2"` explicitly to all imported `jira_util` helpers (`jira_request`, `add_comment`, `transition_issue`).
  * Enforce that `review_agent.py` can only ever execute under role `agent2`.
* **Comment Prefix Alignment**:
  * Standardize comment body structure so that `review_agent.py` uses `add_comment(..., role="agent2")`.
* **Candidate Model Fallback List**:
  * Update candidate models list to prioritize active models and handle temporary 503 spikes gracefully.

### Component 3: Standalone Verification Suite `tools/test_jira_accounts.py`
* Create standalone automated test suite running with standard `unittest`:
  * Tests credential parsing from mock `.env.jira`.
  * Tests atomic pair fallback when user is set but token is missing.
  * Tests precedence rule: CLI flag > ENV var > default.
  * Tests role locking in `review_agent.py`.
  * Tests secret masking (ensures no tokens in captured stdout/stderr).
  * Tests terminal failure when no credentials exist.
  * Tests that `transition_issue` to `erledigt` is blocked across all roles.
  * Tests living documentation consistency (verifies protocol definitions).

### Component 4: Process Documentation & Governance
* Update `docs/project_protocol.md`:
  * Document the tri-agent structure: Agent 1 (Implementer), Agent 2 (Auditor), and Coordinator (Orchestrator).
  * Document `.env.jira` role variables and atomic fallback.
  * Document Coordinator role boundaries (cannot override Auditor verdicts, cannot skip gates, cannot close tickets to `Erledigt`).
* Update `.cursorrules`:
  * Reflect multi-account configuration and Coordinator persona.

## 5. Verification Plan
### Automated Tests
* Run unit test suite:
  ```bash
  python3 tools/test_jira_accounts.py
  ```
* Run existing clean-room unit regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Run review agent connection sanity test:
  ```bash
  python3 tools/review_agent.py test-connection
  ```
