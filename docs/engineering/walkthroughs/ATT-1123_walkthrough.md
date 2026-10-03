# Walkthrough - ATT-1123: Multi-Account Jira Authentication & Coordinator Role Governance

## Overview of Implemented Changes
In Stage 4, role-specific Jira authentication, anti-spoofing constraints, atomic pair fallback, secret masking, and Coordinator governance were implemented across our tooling ecosystem:

1. **`tools/jira_util.py`**:
   - **`resolve_account(role, config)`**: Atomically maps `agent1`, `agent2`, and `coordinator` to their respective `.env.jira` variables (`JIRA_AGENT1_USER/TOKEN`, `JIRA_AGENT2_USER/TOKEN`, `JIRA_COORDINATOR_USER/TOKEN`).
   - **Atomic Pair Fallback**: If either user or token is missing for a role, emits an advisory notice to `stderr` and falls back atomically to `(JIRA_USER, JIRA_TOKEN)` without mixing credentials.
   - **Terminal Failure**: Halts with exit code 1 if neither role nor default credentials exist.
   - **Role Precedence**: `CLI flag (--as <role>)` > `Env var (JIRA_ACTOR)` > `Default (agent1)`. Rejects invalid roles with exit code 1.
   - **Comment Attribution**: Automatically prefixes comments according to active role:
     - `agent1`: `[Automated comment by AI Agent 1 (Implementer)]`
     - `agent2`: `[Automated comment by AI Agent 2 (Auditor)]`
     - `coordinator`: `[Automated comment by AI Coordinator]`
   - **Secret Masking (`sanitize_secrets`)**: Token patterns (`ATATT3...`), passwords, and raw Basic Auth headers are scrubbed from stdout/stderr.
   - **Inviolable Human Gate Guard**: Transitioning tickets to `Erledigt` or `Freigabe erteilt` remains strictly prohibited across all roles.

2. **`tools/review_agent.py`**:
   - **Hardwired Auditor Role**: `AUDITOR_ROLE = "agent2"` is hardwired for all Jira requests, transitions, and comments. Agent 1 cannot spoof Agent 2.
   - **Model Fallback List**: Updated candidate models in descending order of recency (`gemini-3.8-flash` down to `gemini-3-flash-preview` and `gemini-2.5-flash`). Added `--model` flag.
   - **Header Sanitization**: Fixed regex placeholder replacement for `*Auditor Model*:`.

3. **`tools/test_jira_accounts.py`**:
   - Comprehensive automated unit test suite verifying `REQ-PRO-019` (`TST-PRO-012`), `REQ-PRO-020` (`TST-PRO-013`), and `REQ-PRO-021` (`TST-PRO-014`). All 17 tests pass in 0.005s.

4. **Living Documentation & Governance**:
   - Updated `docs/project_protocol.md` and `.cursorrules` with tri-agent personas, Coordinator governance rules (cannot override Auditor verdicts, cannot skip gates, cannot transition tickets to `Erledigt`), and atomic pair fallback standards.

---

## Verification Evidence

### Automated Unit Test Suite (`tools/test_jira_accounts.py`)
```
Ran 17 tests in 0.005s

OK
```

### Full-Suite Android Clean-Room Regression (`./gradlew testDebugUnitTest`)
```
BUILD SUCCESSFUL in 3m 9s
32 actionable tasks: 12 executed, 20 up-to-date
0 failures, 0 regressions across all modules.
```

### Git Commit & Clean Working Tree
- Commit: `dc9e7a1c` (`feat(tools): multi-account jira auth and coordinator governance (ATT-1123)`)
- Working tree: clean.
