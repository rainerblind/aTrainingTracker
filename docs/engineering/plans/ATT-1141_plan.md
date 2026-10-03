# Implementation Plan - ATT-1141: Requirement Archaeology & Chesterton's Fence Modification Hurdle

## 1. Problem Description & Background
In evolutionary software development, requirements in `docs/requirements.md` are sometimes modified, relaxed, or replaced without researching why they were originally created. In complex systems like `aTrainingTracker`, requirements often protect against subtle Android OS idiosyncrasies, race conditions, sensor quirks, or crash loops that are not obvious at first glance (e.g. `START_STICKY` background location behavior, WorkManager ContentProvider initialization, or multi-sport cluster score calculations).

Under Chesterton's Fence principle:
> *"Do not remove or modify a fence until you know why it was put there in the first place."*

This ticket introduces a mandatory, machine-checkable requirement archaeology hurdle into the repository's governance and review gates whenever an existing requirement is altered, while keeping net-new requirements lightweight and unaffected.

## 2. Traceability & Requirements Mapping
* **REQ-PRO-022**: Requirement Archaeology & Chesterton's Fence Modification Hurdle
  * *Given-When-Then Acceptance Criteria*: Full archaeology passes with exit code 0; missing fields fail with exit code 1 and schema diagnostics; net-new requirements bypass check cleanly; Gate 2 review agent challenges deliverables missing required archaeology.
* **TST-PRO-015**: Requirement Archaeology & Chesterton's Fence Governance Verification
  * Verified by 4 unit test scenarios in `tools/test_verify_requirement_governance.py`, Gate 2 checklist integration in `tools/review_agent.py`, living protocol inspection, and pure Python 3 standard library footprint.

## 3. System Invariants & Preserved Behavior
1. **Zero External Dependencies**: All governance and verification scripts MUST run strictly using Python 3 standard library (`sys`, `os`, `re`, `subprocess`, `unittest`). Zero external pip packages.
2. **APK Runtime Code & Asset Isolation**: Zero modifications to Android source files (`app/src/...`), assets, resources, Gradle build scripts, or release artifacts.
3. **Inviolable Human Decision Gate**: Gating tools provide deterministic verification (exit code 0 or 1) and recommendations (`RECOMMEND PASS` or `RECOMMEND REVISION`), but AI agents are strictly prohibited from moving tickets or sub-tasks to `Erledigt`.
4. **Net-New Requirement Exemption**: Introducing brand-new requirement rows (novel IDs absent from the base branch/commit) does NOT require an archaeology section.
5. **Legacy Requirement Grandfathering**: Historical unmodified requirements remain valid as-is without requiring retroactive archaeology backfill.

## 4. Proposed Architectural Changes

### Component 1: Standalone Governance Script (`tools/verify_requirement_governance.py`)
* **Diff Analysis Engine**:
  * Executes `git diff <base>..HEAD docs/requirements.md` (or inspects provided file/text input).
  * Parses git diff hunks to identify modified requirement rows versus net-new requirement rows.
  * Line pattern: `^[+-]\|\s*\*\*REQ-([A-Z0-9_-]+)\*\*`.
  * If a requirement ID appears in both `-` (deleted/old) and `+` (added/new) lines in `docs/requirements.md`, it is classified as **MODIFIED**.
  * If a requirement ID appears only in `+` lines and does not exist in the base version of `docs/requirements.md`, it is classified as **NET_NEW**.
* **Archaeology Section Schema Validator**:
  * If any requirement is classified as **MODIFIED**, the script inspects the deliverable text (provided via file argument or sub-task description / stdin).
  * Validates presence of header: `### Requirement Archaeology & Chesterton's Fence Audit` (or Jira markup equivalent `h3. Requirement Archaeology & Chesterton's Fence Audit`).
  * Enforces the 4 mandatory fields with multiline content parsing:
    1. `Original Requirement ID & Target`
    2. `Historical Origin & Commit Trace`
    3. `Root Reason for Existing Formulation`
    4. `Preservation of Core Invariants`
  * Emits clear schema diagnostic messages on `sys.stderr` when any field is missing or empty.
* **Exit Codes**:
  * `0`: Clean compliance (all modified requirements have complete archaeology sections, or only net-new requirements exist).
  * `1`: Schema validation error, missing field, or malformed template.

### Component 2: Automated Auditor Gate 2 Integration (`tools/review_agent.py`)
* **Gate 2 Checklist Update**:
  * Add item 3 to `GATE_DEFINITIONS["Gate 2"]["checklist"]`:
    * *Requirement Archaeology (Chesterton's Fence)*: If existing requirements in `docs/requirements.md` are modified, verify presence and completeness of the 4-field archaeology section (`Original Requirement ID & Target`, `Historical Origin & Commit Trace`, `Root Reason for Existing Formulation`, `Preservation of Core Invariants`).
* **Auditor System Instruction & Diff Context**:
  * Ensure `Gate 2` receives `diff_context` (currently Gate 2 only received sub-task description, while Gate 3-5 received git diffs). Giving Gate 2 git diff context allows the auditor model to see if `docs/requirements.md` has modified requirements.
  * Direct Agent 2 to issue `CHALLENGED` or `RECOMMEND REVISION` if existing requirements were modified without the complete archaeology audit block.

### Component 3: Standalone Verification Suite (`tools/test_verify_requirement_governance.py`)
* Automated test suite using Python `unittest`:
  * **Test Scenario 1 (Valid Full Archaeology)**: Text containing all 4 fields for a modified requirement passes with exit code 0.
  * **Test Scenario 2 (Missing or Blank Field)**: Omitting any of the 4 fields (e.g. missing `Root Reason for Existing Formulation` or leaving `Preservation of Core Invariants` empty) fails with exit code 1 and structured error messages.
  * **Test Scenario 3 (Net-New Requirement Invariance)**: Diff adding a new requirement row (e.g. `REQ-PRO-999`) exits with code 0 without requiring archaeology.
  * **Test Scenario 4 (Diff Detection against Base)**: Simulates git diff hunks with modified requirements versus added requirements, verifying accurate classification.
  * **Test Scenario 5 (Standard Library Footprint)**: Inspects `tools/verify_requirement_governance.py` imports to ensure zero third-party dependencies.

### Component 4: Living Governance Documentation (`docs/project_protocol.md` & `.cursorrules`)
* Update `docs/project_protocol.md`:
  * Document the Chesterton's Fence principle for requirements engineering.
  * Document the 4-field markdown/Jira schema template and required research steps (`git log -S <REQ-ID>`, Jira history).
  * Document Gate 2 auditor enforcement and developer pre-submission obligations.
* Update `.cursorrules`:
  * Add requirement archaeology check instructions to development workflow rules.

## 5. Verification Plan
### Automated Tests
1. Run standalone governance test suite:
   ```bash
   python3 -m unittest tools/test_verify_requirement_governance.py
   ```
2. Run Jira multi-account test suite to verify no regressions in governance tools:
   ```bash
   python3 tools/test_jira_accounts.py
   ```
3. Run full clean-room Android unit regression suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. Verify standard library footprint:
   ```bash
   python3 -c "import tools.verify_requirement_governance"
   ```

### Manual / Integration Verification
1. Test `tools/verify_requirement_governance.py` against sample valid and invalid markdown snippets directly from CLI.
2. Verify Gate 2 review agent prompt construction and review behavior.
