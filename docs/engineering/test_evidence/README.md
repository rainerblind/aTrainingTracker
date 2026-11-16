# Test Evidence Directory (`docs/engineering/test_evidence/`)

This canonical directory stores persistent, baseline-controlled verification and test coverage evidence per sprint completion and release candidate tag (ASPICE SWE.4 BP5, SWE.5 BP7, SWE.6 BP5, SUP.8; `REQ-PRO-100` / `TST-PRO-100`).

## Purpose & Architecture
Replaces the volatile `docs/engineering/test_results` build symlink with immutable, git-versioned verification records. Each record aggregates:
1. **JUnit Test Metrics**: Total test counts, passed, failures, errors, skipped, execution time, commit SHA, and date.
2. **Per-TST Verification Records**: Bidirectional correlation of test cases to formal `TST-XXX` IDs with pass/fail statuses.
3. **Kover Code Coverage**: Measured line and branch coverage percentages for the global project and critical subsystems (`tracking`, `filters`, `sensors`) extracted from `app/build/reports/kover/reportDebug.xml`.

## Artifact Naming Conventions
- **Sprint Completions**: `<sprint_name>.md` and `<sprint_name>.json` (e.g. `sprint_2026-46.8.md`, `sprint_2026-46.8.json`).
- **Release Candidates**: `<tag>.md` and `<tag>.json` (e.g. `V5.0.0.md`, `V5.0.0.json`).

## Tooling & Generation
- Generated automatically during Ceremony 2 (Sprint Review) or Release Candidate pipelines via:
  ```bash
  python3 tools/jira_util.py export-test-evidence --sprint <SPRINT_NAME>
  python3 tools/jira_util.py export-test-evidence --release <TAG>
  ```
- Or directly via generator:
  ```bash
  python3 tools/generate_test_evidence.py --sprint <SPRINT_NAME>
  python3 tools/generate_test_evidence.py --release <TAG>
  ```
- Governance verified via:
  ```bash
  python3 tools/verify_requirement_governance.py --check-test-evidence
  ```
