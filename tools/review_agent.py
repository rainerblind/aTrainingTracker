#!/usr/bin/env python3
"""
Gate Checklist Definitions & Legacy Auditor Shim (Agent 2) for aTrainingTracker.

NOTE (Sprint 2026-43.1 / ATT-4290 — Option A):
External HTTP REST LLM calls (call_gemini_api / call_claude_api via .env.gemini / .env.claude)
have been retired in favor of native Antigravity subagent delegation (`invoke_subagent` with
`TypeName: "research"` and the Zipper model tier: `flash` for Gates 1 & 3, `pro` for Gates 2, 4 & 5)
paired with `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> <PASS|REVISION> <TEXT_OR_@FILE>`.

The canonical Gate 1–5 checklists are embedded directly in `.agents/skills/stage1-analysis/SKILL.md`
through `.agents/skills/stage5-verification/SKILL.md` and preserved in `GATE_DEFINITIONS` below
for programmatic governance verification (`TST-PRO-013`, `TST-PRO-015`).
"""

import sys

# Anti-Spoofing & Role-Locking: Hardwired strictly to role "agent2"
AUDITOR_ROLE = "agent2"

# Supported Gate Definitions (Zipper Principle: Gate 1/3 -> flash, Gate 2/4/5 -> pro)
GATE_DEFINITIONS = {
    "Gate 1": {
        "prefix": "[Analysis]",
        "name": "Gate 1: Problem Domain & Analysis Review",
        "default_tier": "flash",
        "checklist": """1. Scrutinize Problem Domain & Analysis:
   - For bug tickets: Stress-test RCA conclusions (symptoms vs. cause, call-site audit, reproducible traces).
   - For features/improvements: Verify deep domain comprehension, user motivation, scope boundaries, and architectural side effects.
   - User Scope Grounding (ATT-1250): Ground review expectations strictly in the user's specific problem statement and parent ticket description. Do NOT challenge deliverables or mandate out-of-scope refactorings, speculative edge cases, or theoretical redesigns not requested by the user.
2. Call Site Audit:
   - Verify that all affected callers, classes, and resources are identified.
3. Requirement Mapping Audit:
   - Check mapped Requirement IDs against docs/requirements.md.
4. System Invariants Verification:
   - Confirm explicit system invariants (what existing behavior, data precision, schema, or API contracts MUST NOT change).
5. Risk Rating & Recommendation:
   - Assign risk rating (LOW, MEDIUM, HIGH) with technical justification.
   - Issue an explicit recommendation: RECOMMEND PASS, CHALLENGED, or RECOMMEND REVISION."""
    },
    "Gate 2": {
        "prefix": "[Test-Spec]",
        "name": "Gate 2: Test Specification & Requirements Review",
        "default_tier": "pro",
        "checklist": """1. Requirement Phrasing Standards:
   - Verify standard syntax: SHALL for functional behavior, MUST for technical constraints.
   - Verify atomic, unambiguous, system-centric, and state-oriented descriptions (not implementation steps).
   - Verify explicit Given-When-Then acceptance criteria and System Invariants.
   - User Scope Grounding (ATT-1250): Verify requirements strictly focus on the scoped feature or bugfix. Do NOT require expanding requirements to unrelated components or unrequested workflows.
2. Test Case Traceability:
   - Verify concrete test procedure and expected result in docs/tests.md.
   - Ensure complete bidirectional traceability between requirements and test cases.
   - Stage Awareness: In Gate 2 (Stage 2), test cases and procedures are SPECIFIED in docs/tests.md and the deliverable. Implementation code and unit test files are constructed during Stage 4. Do NOT reject Gate 2 for uncommitted test code files.
3. Requirement Archaeology (Chesterton's Fence):
   - If existing requirements are modified, relaxed, or replaced in docs/requirements.md, verify presence and validity of the 4-field archaeology section:
     * Original Requirement ID & Target
     * Historical Origin & Commit Trace
     * Root Reason for Existing Formulation ("Why was this fence built?")
     * Preservation of Core Invariants ("Why is it safe to modify now?")
   - If existing requirements are altered without this full 4-field section, issue CHALLENGED or RECOMMEND REVISION.
   - Net-new requirements (novel IDs absent from base) are exempt from this hurdle.
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION with itemized notes."""
    },
    "Gate 3": {
        "prefix": "[Impl-Plan]",
        "name": "Gate 3: Architectural & Invariant Plan Review",
        "default_tier": "flash",
        "checklist": """1. Architectural Integrity & Layering:
   - Verify component boundaries, layering rules (SWE.2), and interface stability. Note: This gate audits the architectural implementation plan document (Stage 3). Under ASPICE / TDD process governance, production code implementation is strictly deferred to Stage 4 after the plan passes Gate 3 audit. Do not penalize Stage 3 for not yet having code modifications in the git diff.
2. Invariant Verification:
   - Confirm all system invariants, non-target metrics, and file schemas are explicitly protected.
3. Verification Coverage:
   - Ensure every proposed change maps to an automated or manual test case in docs/tests.md.
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION."""
    },
    "Gate 4": {
        "prefix": "[Implementation]",
        "name": "Gate 4: Code Quality, Localization & Side-Effect Review",
        "default_tier": "pro",
        "checklist": """1. Diff Scrutiny:
   - Inspect git diff against the approved implementation plan. Confirm zero unapproved files or unintended modifications.
2. Side-Effect Audit:
   - Verify adjacent callers, interfaces, and non-target metrics/features were NOT altered or broken.
3. Quality & Compliance:
   - Verify class/method headers comply with self-documenting standards (KDoc/JavaDoc).
   - Verify 9-language localization parity across all user-facing strings (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION with itemized audit notes."""
    },
    "Gate 5": {
        "prefix": "[Test]",
        "name": "Gate 5: Clean-Room Full-Suite Regression & Release Review",
        "default_tier": "pro",
        "checklist": """1. Full-Suite Regression Execution:
   - Verify ./gradlew testDebugUnitTest executed with 100% success (0 failures, 0 regressions) across all project modules.
2. Living Documentation Parity:
   - Confirm docs/requirements.md and docs/tests.md status fields are updated to Verified.
3. Mandatory Lösungsversion (Fix Version/s) Governance Audit:
   - In accordance with ASPICE Rule 8 ("FixVersion set only upon completion") and sub-tasks never having fixVersions, active in-sprint tickets legitimately have Parent Lösungsversion as 'None' until final acceptance during Joint Review or release. Verify that the deliverable references the target release version (e.g. V4.9.40) and that sub-tasks have no fix version assigned.
4. Recommendation:
   - Issue an explicit recommendation: RECOMMEND PASS or RECOMMEND REVISION."""
    }
}


if __name__ == "__main__":
    print(
        "ERROR: External HTTP LLM calls in tools/review_agent.py have been retired (ATT-4290 Option A).\n"
        "Conduct Gate 1-5 audits by invoking an independent read-only 'research' subagent via invoke_subagent\n"
        "(Zipper model tier: flash for Gates 1 & 3, pro for Gates 2, 4 & 5) and recording the verdict via:\n"
        "  python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> <PASS|REVISION> <TEXT_OR_@FILE>",
        file=sys.stderr
    )
    sys.exit(1)
