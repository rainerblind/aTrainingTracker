# Walkthrough - ATT-1141: Requirement Archaeology & Chesterton's Fence Modification Hurdle

Implemented a mandatory, machine-checkable requirement archaeology hurdle to enforce the Chesterton's Fence principle (*"Do not remove or modify a fence until you know why it was put there in the first place"*) whenever existing requirements in `docs/requirements.md` are modified, relaxed, or replaced.

## Changes Made

### 1. Standalone Requirement Governance Tool
* **File**: `tools/verify_requirement_governance.py`
* **Features**:
  * Implemented purely with the Python 3 standard library (`os`, `sys`, `re`, `subprocess`, `argparse`).
  * Parses git diff hunks on `docs/requirements.md` to classify requirements as modified versus net-new.
  * Validates the 4 mandatory fields of the `### Requirement Archaeology & Chesterton's Fence Audit` block using multiline regex:
    1. `Original Requirement ID & Target`
    2. `Historical Origin & Commit Trace`
    3. `Root Reason for Existing Formulation`
    4. `Preservation of Core Invariants`
  * Deterministic exit codes: `0` for clean validation or net-new requirement; `1` for missing/blank fields or malformed structures.

### 2. Gate 2 Independent Auditor Model Integration
* **File**: `tools/review_agent.py`
* **Features**:
  * Updated Gate 2 checklist to explicitly scrutinize requirement archaeology whenever existing requirements are altered.
  * Included git diff context in Gate 2 audit prompts so Agent 2 can detect modifications to `docs/requirements.md`.
  * Configured Agent 2 to issue `CHALLENGED` or `RECOMMEND REVISION` when archaeology is omitted on modified requirements.

### 3. Automated Unit Test Suite
* **File**: `tools/test_verify_requirement_governance.py`
* **Features**:
  * 5 automated test scenarios:
    1. *Scenario 1 (Valid Full Archaeology)*: Passes with exit code 0.
    2. *Scenario 2 (Missing or Blank Field)*: Fails with exit code 1 and schema diagnostics.
    3. *Scenario 3 (Net-New Requirement Invariance)*: Bypasses check cleanly without archaeology requirement.
    4. *Scenario 4 (Diff Hunk Detection)*: Accurately identifies modified requirement rows versus added rows.
    5. *Scenario 5 (Standard Library Footprint)*: AST inspection ensuring zero external pip packages.

### 4. Living Governance Documentation & Rules
* **Files**: `docs/project_protocol.md`, `.cursorrules`
* **Features**:
  * Documented the Chesterton's Fence principle for requirements engineering.
  * Documented the 4-field markdown/Jira schema template.
  * Documented Gate 2 auditor enforcement and grandfathering rules.

## Verification Results
* `python3 -m unittest tools/test_verify_requirement_governance.py`: 5/5 passed (0.006s).
* `python3 tools/test_jira_accounts.py`: 18/18 passed (0.007s).
* Zero APK runtime code or asset changes.
