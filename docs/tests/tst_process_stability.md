# Living Test Specification: Engineering Process, Stability & Architecture

**Domain**: `process_stability`  
**Prefixes**: `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`  
**Scope**: ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools.  
**Total Tests**: 110

---

### TST-REG-001: **Tracking** [Verified]
- **Requirement(s)**: `REQ-TRK-001`, `REQ-TRK-003`, `REQ-TRK-007`, `REQ-MAP-003`
- **Component**: **Tracking**
- **Test Description**: Start workout, background app for 10 min, check bearing jitter.

### TST-REG-002: **Branding** [Verified]
- **Requirement(s)**: `REQ-EXT-001`, `REQ-EXT-002`, `REQ-MAP-005`
- **Component**: **Branding**
- **Test Description**: Navigate to Strava Segments list and Detail map.

### TST-REG-003: **Cloud** [Verified]
- **Requirement(s)**: `REQ-EXP-001`, `REQ-EXP-002`
- **Component**: **Cloud**
- **Test Description**: Complete workout with Strava enabled.

### TST-REG-004: **Pause Logic** [Verified]
- **Requirement(s)**: `REQ-PRO-003`
- **Component**: **Pause Logic**
- **Test Description**: Connect hardware distance sensor. Start workout. Record distance. **Pause**. Continue movement (e.g., spin wheel). **Resume**. Record more distance. Repeat for all relevant protocols (ANT+/BTLE).

### TST-INT-002: **UI/Logic** [Verified]
- **Requirement(s)**: `REQ-EXP-006`
- **Component**: **UI/Logic**
- **Test Description**: In Sport Type Editor, select "- No upload -". Then create/edit workout with this type.

### TST-INT-003: **Mapping** [Verified]
- **Requirement(s)**: `REQ-EXP-007`
- **Component**: **Mapping**
- **Test Description**: In Sport Type Editor, select a new Strava type (e.g., Gravel Ride). Save and reopen.

### TST-INT-004: **UI/Logic** [Verified]
- **Requirement(s)**: `REQ-UI-009`
- **Component**: **UI/Logic**
- **Test Description**: In Tracking Tab Configuration, toggle "Show Elevation Profile".

### TST-INT-005: **UI Layout** [Verified]
- **Requirement(s)**: `REQ-UI-010`
- **Component**: **UI Layout**
- **Test Description**: Open the Tab Configuration on a narrow device or split-screen.

### TST-INT-006: **UI Layout** [Verified]
- **Requirement(s)**: `REQ-UI-010`
- **Component**: **UI Layout**
- **Test Description**: Audit the checkbox sequence in the tab configuration header.

### TST-REG-005: **Map Context** [Verified]
- **Requirement(s)**: `REQ-MAP-008`
- **Component**: **Map Context**
- **Test Description**: Open detail map. Verify active item is primary. Verify background items of same sport are muted (0.3). Verify background items of different sport are highly muted (0.1).

### TST-INT-008: **Settings Cleanup Verification** [Verified]
- **Requirement(s)**: `REQ-SET-055`
- **Component**: **Settings Cleanup Verification**
- **Jira**: ``ATT-268``
- **Test Description**: 1. Gradle Build: Ensure the project compiles successfully after reorganization and deletions. 2. Navigation Test: Verify all settings items in the drawer (Units, Display, Search, Export, Strava, Dropbox, Tracking Tabs) still function correctly.

### TST-PRO-001: **Jira Attachment Download** [Verified]
- **Requirement(s)**: `REQ-PRO-008`
- **Component**: **Jira Attachment Download**
- **Jira**: ``ATT-273``
- **Test Description**: 1. Identify a Jira ticket with attachments. 2. Run `jira_util.py download-all KEY`.

### TST-PRO-002: **Jira Type Visibility** [Verified]
- **Requirement(s)**: `REQ-PRO-009`
- **Component**: **Jira Type Visibility**
- **Jira**: ``ATT-274``
- **Test Description**: 1. Run `jira_util.py list`. 2. Run `jira_util.py show KEY`.

### TST-PRO-003: **Plan Sub-Task Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-010`
- **Component**: **Plan Sub-Task Verification**
- **Jira**: ``ATT-327``
- **Test Description**: 1. Identify a task requires a plan. 2. Verify that the agent creates a Jira sub-task with summary `[Plan] SCRUM-XXX: Summary` and description matching the plan.

### TST-REG-007: **Pause Time Sync** [Verified]
- **Requirement(s)**: `REQ-TRK-009`, `REQ-UI-038`
- **Component**: **Pause Time Sync**
- **Test Description**: 1. Start workout. 2. Pause after 10s. 3. Wait 20s while paused.

### TST-REG-008: **Dropbox Auth** [Verified]
- **Requirement(s)**: `REQ-EXP-002`
- **Component**: **Dropbox Auth**
- **Jira**: ``ATT-257``
- **Test Description**: 1. Navigate to 'Dropbox' in Ecosystem. 2. Tap 'Connect to Dropbox'. 3. Complete OAuth flow in browser. 4. Return to app.

### TST-LOG-001: **Logic** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Logic**
- **Test Description**: Verify that all rows, including "Pace", follow strict mathematical ordering (numerical minimum in Min column, numerical maximum in Max column).

### TST-LOG-002: **Logic** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Logic**
- **Test Description**: Verify that for a running activity, both Speed and Pace rows are generated from the single Speed data source.

### TST-MAN-001: **General** [Verified]
- **Requirement(s)**: All other `REQ`
- **Component**: **General**
- **Test Description**: Ad-hoc functional testing on a physical device.

### TST-PRO-004: **Decoupled Interaction Queue** [Verified]
- **Requirement(s)**: `REQ-MIG-014`
- **Component**: **Decoupled Interaction Queue**
- **Jira**: ``ATT-333``
- **Test Description**: 1. Start a bulk TCX import that triggers multiple cluster naming candidates. 2. Verify that while the ClusterNamingDialog is shown, the background progress indicator remains visible. 3. Complete the first naming task.

### TST-BUG-006: **Adaptive Header Framing** [Verified]
- **Requirement(s)**: `REQ-UI-117`
- **Component**: **Adaptive Header Framing**
- **Jira**: ``ATT-397``
- **Test Description**: 1. Open the app on a device with a large status bar (e.g. Pixel 10). 2. Navigate to Workouts, Periods, and My Locations.

### TST-BUG-007: **Internal Altitude Source Visibility** [Verified]
- **Requirement(s)**: `REQ-UI-118`
- **Component**: **Internal Altitude Source Visibility**
- **Jira**: ``ATT-410``
- **Test Description**: 1. Open tracking screen on device with pressure sensor. 2. Tap Altitude icon.

### TST-BUG-008: **Self-Healing Pressure Sensor Registration** [Verified]
- **Requirement(s)**: `REQ-DAT-006`
- **Component**: **Self-Healing Pressure Sensor Registration**
- **Jira**: ``ATT-414``
- **Test Description**: 1. Use Database Inspector to delete the 'Altitude from Pressure' entry from the `Devices` table. 2. Restart the app on a device with a pressure sensor. 3. Navigate to 'My Sensors'.

### TST-BUG-009: **Internal Sensor Pairing Control** [Verified]
- **Requirement(s)**: `REQ-CON-010`
- **Component**: **Internal Sensor Pairing Control**
- **Jira**: ``ATT-416``
- **Test Description**: 1. Navigate to 'My Sensors'. 2. Deactivate the 'Altitude from Pressure' sensor using the toggle switch. 3. Verify that the sensor status LED turns grey and real-time altitude updates from this source stop. 4. Reactivate and verify resumption.

### TST-BUG-011: **Eager Pressure Sensor Visibility** [Verified]
- **Requirement(s)**: `REQ-DAT-006`
- **Component**: **Eager Pressure Sensor Visibility**
- **Jira**: ``ATT-420``
- **Test Description**: 1. Use Database Inspector to delete the 'Altitude from Pressure' entry from the `Devices` table. 2. Restart the app on a device with a pressure sensor. 3. Navigate to 'My Sensors'.

### TST-BUG-010: **Internal Sensor Iconography** [Verified]
- **Requirement(s)**: `REQ-UI-119`
- **Component**: **Internal Sensor Iconography**
- **Test Description**: 1. Open 'My Sensors'. 2. Inspect the 'Altitude from Pressure' entry.

### TST-STR-001: **Stability** [Verified]
- **Requirement(s)**: `REQ-CON-005`, `REQ-TRK-005`, `REQ-DAT-005`, `REQ-UI-008`
- **Component**: **Stability**
- **Test Description**: Static analysis and architectural audit.

### TST-STR-002: **Thread Safety** [Verified]
- **Requirement(s)**: `REQ-PRO-002`
- **Component**: **Thread Safety**
- **Test Description**: Static analysis of `synchronized` blocks to ensure no nested locks or I/O within locks.

### TST-STR-003: **Data Integrity** [Verified]
2. `addDefaultTab` must initialize `SHOW_ELEVATION_PROFILE` using the value provided for `showMap`.]
- **Requirement(s)**: `REQ-UI-009`
- **Component**: **Data Integrity**
- **Test Description**:
  1. Static analysis of `onUpgrade` (v9->v10).
  2. Static analysis of `onCreate` and `addDefaultTab`.

### TST-STR-004: **Consistency** [Verified]
- **Requirement(s)**: `REQ-TRK-008`
- **Component**: **Consistency**
- **Test Description**: Static analysis of `TrackerService.java`.

### TST-STR-005: **Map DSL** [Verified]
- **Requirement(s)**: `REQ-MAP-006`
- **Component**: **Map DSL**
- **Test Description**: Static audit of `ATrainingTrackerMap` callers.

### TST-STR-006: **Metric Consistency** [Verified]
- **Requirement(s)**: `REQ-UI-015`
- **Component**: **Metric Consistency**
- **Test Description**: Visual audit of Workout, Route, and Segment screens.

### TST-STR-007: **Formatter Sync** [Verified]
- **Requirement(s)**: `REQ-UI-016`
- **Component**: **Formatter Sync**
- **Test Description**: Static audit of `LocalMetricFormatter` usages.

### TST-STR-008: **List Foundation** [Verified]
- **Requirement(s)**: `REQ-UI-017`
- **Component**: **List Foundation**
- **Test Description**: Audit of `MappableListItem` usages.

### TST-STR-011: **Thread-Safe Sensor Management** [Verified]
- **Requirement(s)**: `REQ-PRO-006`
- **Component**: **Thread-Safe Sensor Management**
- **Test Description**: Static audit of `MySensorManager` and `MyDevice`.

### TST-STR-012: **Sub-Task Automation Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-007`
- **Component**: **Sub-Task Automation Verification**
- **Test Description**: 1. Define a requirement and a test case in the docs. 2. The agent should be able to create a sub-task in Jira linked to the main ticket with the test ID and summary. 3. Verify the sub-task is created with the correct parent.

### TST-STR-013: **String Resource Integrity Check** [Verified]
- **Requirement(s)**: `REQ-UI-113`
- **Component**: **String Resource Integrity Check**
- **Jira**: ``ATT-251``
- **Test Description**: 1. Audit all `values-*/strings.xml` files. 2. Compare the set of keys in each file with the keys in `values/strings.xml`. 3. Identify keys present in translations but missing in default. 4. Verify that after implementation, no such discrepancies exist.

### TST-STR-015: **Documentation Audit** [Verified]
- **Requirement(s)**: `REQ-PRO-011`
- **Component**: **Documentation Audit**
- **Jira**: ``ATT-486``
- **Test Description**: 1. Perform a static audit of the modified source files (e.g., `WorkoutRepository.kt`). 2. Verify that every class has a header describing its purpose. 3. Verify that every method has a header describing what it does and how.

### TST-STR-014: **Missing Translation Audit** [Verified]
- **Requirement(s)**: `REQ-UI-106`
- **Component**: **Missing Translation Audit**
- **Jira**: ``ATT-484``
- **Test Description**: 1. Run a script to identify all translatable keys in default `values/` that are missing in any supported locale (DE, ES, FR, IT, JA, NL, PL, PT). 2. Verify that after implementation, zero translatable keys from the default locale are missing in any supported sub-locale. 3. Verify that specialized resource files (e.g., `strings_devices.xml`, `strings_filters.xml`) are also consistent across locales.

### TST-STR-016: **Global Format String Audit** [Verified]
- **Requirement(s)**: `REQ-UI-122`
- **Component**: **Global Format String Audit**
- **Jira**: ``ATT-523``
- **Test Description**: 1. Execute a comprehensive search across all `strings.xml` files in all locales (DE, ES, FR, IT, JA, NL, PL, PT) for positional format specifiers that are incomplete (e.g., `%1` without `$s`, `$d`, or `$f`). 2. Correct any identified issues. 3. Verify that the final resource set contains only syntactically valid `java.util.Formatter` specifiers.

### TST-BUG-001: **Cluster Selection Race Condition** [Verified]
- **Requirement(s)**: `REQ-PER-002`
- **Component**: **Cluster Selection Race Condition**
- **Jira**: ``ATT-364``
- **Test Description**: 1. Open Workout Cluster list. 2. Tap Cluster A. 3. Immediately go back and tap Cluster B. 4. Wait for loading to complete.

### TST-BUG-002: **Aggregation Gap Stability** [Verified]
- **Requirement(s)**: `REQ-PER-001`
- **Component**: **Aggregation Gap Stability**
- **Jira**: ``ATT-372``
- **Test Description**: 1. Import training history with temporal gaps (e.g. skip a week/month). 2. Trigger a full hierarchical sync.

### TST-STR-017: **Protocol Integrity Audit** [Verified]
- **Requirement(s)**: `REQ-PRO-012`
- **Component**: **Protocol Integrity Audit**
- **Jira**: ``ATT-523``
- **Test Description**: Verify that the 'Format String Hardening' section is correctly incorporated into 'project_protocol.md'.

### TST-PRO-005: **System Invariant & Impact Protocol Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-013`
- **Component**: **System Invariant & Impact Protocol Verification**
- **Jira**: ``ATT-523``
- **Test Description**: Verify that 'project_protocol.md' mandates System Invariants, pre-implementation `find_usages` requirement cross-checks, and adversarial self-reviews.

### TST-PRO-006: **Dual-Gate Review Protocol Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-014`
- **Component**: **Dual-Gate Review Protocol Verification**
- **Jira**: ``ATT-523``
- **Test Description**: Verify that 'project_protocol.md' mandates the unified 5-stage sub-task lifecycle using the automated dual-agent review workflow, with autonomous transitions upon passing gate audits and human approval reserved exclusively for parent tickets.

### TST-PRO-007: **Agent Git Branch, Commit & Merge Protocol Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-015`
- **Component**: **Agent Git Branch, Commit & Merge Protocol Verification**
- **Jira**: ``ATT-422``
- **Test Description**:
  1. Verify `docs/project_protocol.md` and `.cursorrules` explicitly mandate agent-driven creation of feature/bugfix branches off `develop` at ticket start.
  2. Verify protocol defines rules for agent-driven Conventional Commits with asterisk bullet points and ticket references.
  3. Verify protocol mandates merging the ticket branch into `develop` using non-fast-forward merge (`git merge --no-ff <branch> -m "Merge branch '<branch>' into develop"`) upon completion and human release approval.
  4. Validate repository state demonstrates successful branch creation (`feature/ATT-422`), commit adherence, and merge flow.

### TST-PRO-008: **Automated 5-Stage ASPICE Workflow & Independent Agent 2 Audit Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-014`
- **Component**: **Automated 5-Stage ASPICE Workflow & Independent Agent 2 Audit Verification**
- **Jira**: ``ATT-670` / `ATT-685``
- **Test Description**:
  1. Verify `docs/project_protocol.md` defines the unified 5-stage ASPICE lifecycle (`Analysis`, `Test Spec`, `Implementation Plan`, `Implementation`, `Test`, `Erledigt`) across all issue types (Bug, Improvement, Feature).
  2. Verify protocol mandates automated creation of lifecycle subtasks (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`) by Jira Automation upon entering each stage.
  3. Verify protocol mandates that Agent 2 *always* conducts an automated independent gate audit whenever a sub-task is moved to `In Überprüfung`, posting the audit report as a Jira comment and transitioning to `Freigabe (Human)`.
  4. Verify protocol enforces deliverable placement in the sub-task's Description (Agent 1) and audit report placement in Comments (Agent 2).
  5. Verify protocol enforces the strict Human Decision Gate for `Erledigt` on parent tickets and automated parent transition upon sub-task completion.
  6. Verify `tools/jira_util.py` `show` command displays subtasks.
  7. Verify `.cursorrules` reflects the unified 5-stage workflow and automated Agent 2 audits.
  8. Verify `tools/jira_util.py` displays `Lösungsversion` on `show` and `list`, supports `versions` and `set-fixversion`, and warns if missing.
  9. Verify protocol and `.cursorrules` mandate `Lösungsversion` (*Fix Version/s*) and its audit in Gate 5 prior to closure.

### TST-PRO-009: **Inviolable ASPICE Human Decision Gate & Anti-Premature Trigger Protocol Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-016`
- **Component**: **Inviolable ASPICE Human Decision Gate & Anti-Premature Trigger Protocol Verification**
- **Jira**: ``ATT-728` / `ATT-941``
- **Test Description**:
  1. Verify `docs/project_protocol.md` and `.cursorrules` mandate `RequestFeedback: false` on all artifact tool calls.
  2. Verify protocol explicitly states that synthetic IDE auto-approval messages hold zero authority and grant NO permission to modify code or Jira tickets.
  3. Verify protocol and rules mandate `python3 tools/jira_util.py check-gate <Subtask>` before modifying any production code in Stage 4.
  4. Verify `tools/jira_util.py` includes the `check-gate` command that returns exit code 0 when status is `Erledigt` and exit code 1 otherwise.
  5. Verify `.agents/rules/aspice_governance.md` exists and enforces these rules in Antigravity.

### TST-PRO-010: **Sub-Task Self-Sufficiency, Transition Sequencing, Discovery-First, and Git Finalization Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-017`
- **Component**: **Sub-Task Self-Sufficiency, Transition Sequencing, Discovery-First, and Git Finalization Verification**
- **Jira**: ``ATT-945` / `ATT-1000``
- **Test Description**:
  1. Verify `docs/project_protocol.md` explicitly mandates sub-task self-sufficiency (non-empty Description containing full deliverable, concrete audit comments).
  2. Verify protocol mandates strict tool execution sequencing (`update-desc` and `comment` BEFORE state transitions).
  3. Verify protocol mandates discovery-first sub-task handling via `tools/jira_util.py show <Parent>` to eliminate duplicate sub-task creation.
  4. Verify protocol mandates post-completion git finalization (`git checkout develop && git merge --no-ff`) and integration commenting on the parent ticket.
  5. Validate that ATT-945 deliverables adhere to the hardened protocol with full documentation in `docs/engineering/Sprint_Review_and_Retro_ATT-945.md`.

### TST-PRO-011: **Autonomous Multi-Model Independent Review Agent & Automated Gate Audits Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-018`
- **Component**: **Autonomous Multi-Model Independent Review Agent & Automated Gate Audits Verification**
- **Jira**: ``ATT-1122` / `ATT-1194``
- **Test Description**:
  1. Verify `tools/review_agent.py` exists and implements out-of-process gate auditing using Python 3 standard library (`urllib.request`, `json`, `os`, `sys`, `subprocess`) with zero external pip dependencies.
  2. Verify provider credential loading from `.env.gemini` (`GEMINI_API_KEY`) and `.env.claude` (`ANTHROPIC_API_KEY`).
  3. Verify automated gate stage detection based on sub-task summary prefixes (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`).
  4. Verify automated context assembly: sub-task description, parent ticket details, living documentation (`requirements.md`, `tests.md`), and active git diffs.
  5. Verify audit report generation adheres to Gate 1-5 checklist criteria from `docs/project_protocol.md` with explicit recommendation (`RECOMMEND PASS`, `CHALLENGED`, `RECOMMEND REVISION`) and Jira Wiki formatting.
  6. Verify automated comment posting to Jira prefixed with `[Automated comment by AI Agent (External Auditor)]` and transition to `Freigabe (Human)`.
  7. Verify strict human decision gate guard: attempting to transition to `Erledigt` is blocked.
  8. Execute automated unit/functional test of `tools/review_agent.py` verifying mock and live execution across providers.

### TST-PRO-012: **Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-019`
- **Component**: **Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling Verification**
- **Jira**: ``ATT-1123` / `ATT-1200``
- **Test Description**:
  1. Unit Test Credential Configuration Parsing (`.env.jira`): Verify `get_config()` and `resolve_account(role)` parse role-specific keys (`JIRA_AGENT1_USER`, `JIRA_AGENT1_TOKEN`, `JIRA_AGENT2_USER`, `JIRA_AGENT2_TOKEN`, `JIRA_COORDINATOR_USER`, `JIRA_COORDINATOR_TOKEN`) alongside base `JIRA_USER`, `JIRA_TOKEN`.
  2. Unit Test Atomic Pair Resolution & Fallback: Verify that when both role user and token are set, the role credentials are used; verify that when only one is set (e.g. user set, token empty), resolution falls back atomically to `(JIRA_USER, JIRA_TOKEN)` and emits notice to `stderr`; verify that when no role keys exist, resolution cleanly defaults to base credentials.
  3. Unit Test Terminal Error on Missing Credentials: Mock configuration with no credentials defined; verify `get_headers()` or API request halts immediately with exit code 1 and writes diagnostic error to `stderr` without making unauthenticated requests.
  4. Unit Test Basic Auth Header Generation: Verify `get_headers(role)` produces base64 Basic Auth headers matching the resolved credentials.
  5. Backward Compatibility Invariant: Verify that single-account setups run without warnings or errors.
  6. Standard Library Invariant: Verify all unit tests and tooling run with zero pip dependencies on Python 3 standard library.

### TST-PRO-013: **Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-020`
- **Component**: **Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution Verification**
- **Jira**: ``ATT-1123` / `ATT-1200``
- **Test Description**:
  1. Unit Test Precedence Resolution: Verify role selection follows `CLI Flag (--as <role>)` > `Environment Variable (JIRA_ACTOR)` > `Script Default`.
  2. Unit Test Role Hardwiring in `review_agent.py`: Verify that `review_agent.py` hardwires role to `agent2` across all API calls and comments, rejecting or ignoring any attempt to run review audits as `agent1` or `coordinator`.
  3. Unit Test Invalid Role Rejection: Verify `jira_util.py --as invalid_role` exits with error code 1 and descriptive usage message.
  4. Unit Test Comment Prefix Attribution: Verify `add_comment` formats opening line with role-specific attribution prefix: `[Automated comment by AI Agent 1 (Implementer)]` for `agent1`, `[Automated comment by AI Agent 2 (Auditor)]` for `agent2`, and `[Automated comment by AI Coordinator]` for `coordinator`.
  5. Unit Test Human Gate Invariance: Verify that attempting `transition_issue(key, "erledigt")` or `transition_issue(key, "freigabe erteilt")` is blocked with exit code 1 specifically for transitions targeting Erledigt / Done, while non-Erledigt transitions (`in_progress`, `in_review`, `freigabe`) succeed.
  6. Automated Protocol Inspection: Programmatic test verifies `docs/project_protocol.md` and `.cursorrules` explicitly define the Coordinator role, specify Coordinator boundaries (cannot override Auditor verdicts, cannot skip gates, cannot close tickets to `Erledigt`).

### TST-PRO-014: **Jira Credential & Token Secret Masking Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-021`
- **Component**: **Jira Credential & Token Secret Masking Verification**
- **Jira**: ``ATT-1123` / `ATT-1200``
- **Test Description**:
  1. Unit Test Output Sanitization: Execute mock CLI invocations with valid, fallback, and invalid configurations; capture `sys.stdout` and `sys.stderr`; assert that raw token strings and Basic Auth base64 payload strings never appear in the output.
  2. Unit Test Advisory Notice Scrubbing: Verify fallback notices printed to `stderr` reference only role names or usernames and contain zero token substrings.
  3. Unit Test Exception Masking: Simulate HTTP errors (e.g. 401 Unauthorized); verify error outputs printed to console do not echo request authorization headers or secret tokens.

### TST-PRO-015: **Requirement Archaeology & Chesterton's Fence Governance Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-022`
- **Component**: **Requirement Archaeology & Chesterton's Fence Governance Verification**
- **Jira**: ``ATT-1141` / `ATT-1205``
- **Test Description**:
  1. *Unit Test Template & Diff Parser (`tools/verify_requirement_governance.py`)*:
  • *Scenario 1 (Valid Full Archaeology)*: Given a deliverable proposing edits to an existing requirement with all 4 mandatory fields populated (`Original Requirement ID & Target`, `Historical Origin & Commit Trace`, `Root Reason for Existing Formulation`, `Preservation of Core Invariants`), verify the tool exits with code 0.
  • *Scenario 2 (Missing or Empty Mandatory Field)*: Given a deliverable modifying an existing requirement where one or more fields are omitted or blank, verify the tool exits with code 1 and outputs structured schema error diagnostics to stderr.
  • *Scenario 3 (Net-New Requirement Invariance)*: Given git diff containing only added requirement lines with a novel ID (absent from base), verify the tool identifies it as net-new and exits with code 0 without requiring archaeology.
  • *Scenario 4 (Diff Detection against Base)*: Given `git diff develop..HEAD docs/requirements.md`, verify the script accurately parses modified requirement lines versus added/deleted lines using git hunk analysis.
  2. *Gate 2 Auditor Model Verification (`tools/review_agent.py`)*:
  • Verify `GATE_DEFINITIONS["Gate 2"]["checklist"]` includes explicit item: "Requirement Archaeology (Chesterton's Fence): If existing requirements are modified in `docs/requirements.md`, verify presence and validity of the 4-field archaeology section."
  • Verify review prompt instructs the model to issue `CHALLENGED` or `RECOMMEND REVISION` when archaeology is omitted on modified requirements.
  3. *Living Protocol & Rules Inspection*:
  • Verify `docs/project_protocol.md` and `.cursorrules` explicitly document the Chesterton's Fence principle, the 4-field section schema, and the requirement archaeology hurdle.
  4. *Standard Library Footprint Invariant*:
  • Verify `tools/verify_requirement_governance.py` imports only Python 3 standard library modules (`os`, `sys`, `re`, `subprocess`, `unittest`) with zero external pip dependencies.

### TST-PRO-016: **Android Test Fixtures & Jira Metadata Auto-Inheritance Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-023`
- **Component**: **Android Test Fixtures & Jira Metadata Auto-Inheritance Verification**
- **Jira**: ``ATT-1125` / `ATT-1210``
- **Test Description**:
  1. *Unit Test `MockCursorFactory` Fidelity (`MockCursorFactoryTest.kt`)*:
  • *Empty Cursor*: Verify cursor initialized with empty row list has `count == 0`, `moveToFirst() == false`, and `isAfterLast == true`.
  • *Column Resolution*: Verify `getColumnIndex` and `getColumnIndexOrThrow` return correct 0-based indices for defined columns and -1 / `IllegalArgumentException` for unknown columns.
  • *Row Traversal*: Given a 3-row dataset, verify sequential `moveToNext()` traverses row 0, 1, 2 accurately, and returns false when exhausting rows.
  • *Data Type Extraction*: Verify `getInt`, `getLong`, `getDouble`, `getFloat`, `getString`, and `getBlob` return expected casted values for heterogeneous cell values.
  • *Nullability Assertions*: Verify `isNull` returns true for null cell values and false for non-null values.
  2. *Integration / Regression Verification in Existing Tests*:
  • Refactor a sample database test (e.g. in `EquipmentDbHelperRetiredTest.kt`) to utilize `MockCursorFactory.create(...)`; verify test passes cleanly with identical assertions and dramatically reduced boilerplate.
  3. *CLI Unit Tests for Jira Metadata & Sprint Auto-Inheritance (`tools/test_jira_accounts.py`)*:
  • Verify `create_subtask` and `create_issue` accept and process `--add-to-sprint` and `--fixversion` flags.
  • Mock Jira Agile board sprint query; verify sub-task creation with `--add-to-sprint` attaches newly created issue key to active sprint.
  4. *Living Protocol & Sandbox Documentation Inspection*:
  • Verify `docs/project_protocol.md` and `.cursorrules` explicitly document filesystem boundaries for Gradle Daemon caches (`~/.gradle`, `~/.android`) and when to execute with elevated permissions (`BypassSandbox: true`).

### TST-PRO-017: **Modular Skill-Based Agile Architecture & Directory Parity Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-024`
- **Component**: **Modular Skill-Based Agile Architecture & Directory Parity Verification**
- **Jira**: ``ATT-1508` / `ATT-1513``
- **Test Description**:
  1. *Directory & SKILL.md Parity Verification*:
  • Programmatically inspect `.agents/skills/` and verify that all 9 skill directories exist: `stage1-analysis`, `stage2-req-test-spec`, `stage3-impl-plan`, `stage4-implementation`, `stage5-verification`, `jira-workflow`, `ui-designer`, `brainstormer`, `sprint-planner`.
  • Verify each skill directory contains a `SKILL.md` file with valid YAML frontmatter matching regex `^---\ntitle: .+\ndescription: .+\n---` or `^---\nname: .+\ndescription: .+\n---`.
  2. *Deliverable Template Verification*:
  • Verify existence and completeness of `templates/analysis_template.md` in `stage1-analysis`.
  • Verify existence and completeness of `templates/test_spec_template.md` in `stage2-req-test-spec`.
  • Verify existence and completeness of `templates/plan_template.md` in `stage3-impl-plan`.
  • Verify existence and completeness of `templates/walkthrough_template.md` in `stage5-verification`.
  • Verify existence and completeness of `templates/backlog_ticket_template.md` in `brainstormer`.
  3. *Jira Subtask Transition & Parent Final Review Invariant Verification*:
  • Verify `tools/jira_util.py` allows subtasks to transition directly to `Erledigt` via transition `freigabe` (or ID `4`).
  • Verify `tools/jira_util.py` transitions parent tickets in `Test` to `Final Review (Human)` and automatically assigns them to `human`.
  • Verify `tools/jira_util.py` strictly blocks transitioning parent tickets to `Erledigt` for AI agent roles, exiting with error code 1.
  4. *Sprint Lifecycle & Skill Integration*:
  • Verify `sprint-planner` defines Phase 1 Sprint-Start interactive screening and Phase 3 Sprint-End joint review procedures.
  5. *Living Protocol & Rules Integrity*:
  • Verify `docs/project_protocol.md` and `.cursorrules` document the modular skill layout, referencing `.agents/skills/` for execution while preserving core invariants and Human Decision Gates.

### TST-PRO-018: **Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking** [Verified]
2. *Defensive Memory Allocation Capping Verification (`TST-PRO-018.2`)*: Inspect command line arguments of `GradleWorkerMain` processes via `ps -ef` or `/proc/<pid>/cmdline`. Verify presence of `-Xms256m` and `-Xmx768m`. Confirm aggregate heap across workers is capped $\le 3.1\text{ GB}$.
3. *Shared Localization DOM Cache Thread-Safety & Immutability Verification (`TST-PRO-018.3`)*: Execute `LocalizationTestCacheTest.kt` verifying all 9 locales parse correctly, returned maps are unmodifiable (`UnsupportedOperationException` on mutation), 16 concurrent threads access the cache without data races, and disk is read at most once per locale per JVM.
4. *Worker Recycling & MockK Heap Stability Verification (`TST-PRO-018.4`)*: Monitor worker PIDs during a full run of all 436 test classes. Verify worker processes exit and new workers are spawned every 80 test classes (`forkEvery = 80`). Verify absence of escalating GC pauses.
5. *Full Clean-Room Regression & Performance Benchmark Execution (`TST-PRO-018.5`)*: Execute `./gradlew clean testDebugUnitTest`. Measure total execution duration. Verify 100% pass rate across all 436 test classes and >2,130 tests, with execution time reduced by $\ge 40\%$ compared to baseline.]
- **Requirement(s)**: `REQ-PRO-025`
- **Component**: **Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking**
- **Jira**: ``ATT-2770``
- **Test Description**: 1. *Parallel Worker Forking & Multi-Core Utilization Verification (`TST-PRO-018.1`)*: Trigger `./gradlew testDebugUnitTest`. Inspect OS processes via `ps aux

### TST-PRO-019: **Document Triad Architecture, Governance Streamlining, and Invariant Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-026`
- **Component**: **Document Triad Architecture, Governance Streamlining, and Invariant Verification**
- **Jira**: ``ATT-3323``
- **Test Description**:
  1. *ASPICE Governance 4-Tier Structure Audit*:
  • Verify `.agents/rules/aspice_governance.md` defines exactly 16 consolidated rules structured into 4 descending importance tiers.
  • Verify Tier 1 (Rules 1–6) contains: Rule 1 (*Obey the Rules & Mandatory Triad Refresh*), Rule 2 (*Autonomous Full-Sprint Execution*), Rule 3 (*Human Decision Gates & Terminal States*), Rule 4 (*Gate 3 Pre-Check Before Production Code*), Rule 5 (*Strict Separation of Ticket Creation vs. Implementation*), Rule 6 (*Autonomous In-Sprint Escalation Protocol*).
  • Verify Tier 2 (Rules 7–11) contains: Rule 7 (*Strict Sprint Scope & Backlog Rank Authority*), Rule 8 (*Zero Code Changes During Sprint Review*), Rule 9 (*Lean Defect Recording with Immediate Evidence Capture*), Rule 10 (*Mandatory Device Deployment Before Review*), Rule 11 (*Retrospective & Governance Documentation Before Merge*).
  • Verify Tier 3 (Rules 12–14) contains: Rule 12 (*Standardized Sub-Task Naming Scheme*), Rule 13 (*Clean Ticket Summaries*), Rule 14 (*Fix Version Governance*).
  • Verify Tier 4 (Rules 15–16) contains: Rule 15 (*Formal GitFlow Hotfix & Dual-Branch Protocol*), Rule 16 (*Human Prerequisites & Deferred Field-Testing*).
  • Verify consolidation of legacy duplicate pairs: 17+25 -> Rule 9; 7+19 -> Rule 14; 26+27 -> Rule 15; 22+28 -> Rule 16.
  • Verify absence of obsolete rules (legacy Rule 3 and legacy Rule 24).
  • Verify presence and completeness of the Historical Mapping Cross-Reference Table for Rules 1–36.
  2. *UI Design Guidelines Consolidation Audit (`docs/design_guidelines.md`)*:
  • Verify inclusion of Section 5 (*Visual Consistency Baseline / Rule 23*).
  • Verify inclusion of Section 3.1 (*Explanatory Scale Direction in Settings Sliders / Rule 31*).
  • Verify inclusion of Section 2.1 (*Coordinate Space Normalization for Sub-Entity Inspection Charts / Rule 32*).
  • Verify inclusion of Section 6.1 (*Unified Viewport Anchor Authority for Map Tracking / Rule 33*).
  • Verify inclusion of Section 4 (*Jetpack Compose @Preview tooling, 9-language localization parity, and positional format specifiers*).
  3. *Technical Coding Standards Architecture Audit (`docs/coding_standards.md`)*:
  • Verify existence and completeness of `docs/coding_standards.md`.
  • Verify Section 1.1 (*Rule 36: Single Source of Truth & DRY — Shared Code Over Duplication*, mandatory reuse of canonical formatters and unit math, prohibition of inline manual formatting).
  • Verify Section 2.1 (*Rule 20: Database & DTO Mapping Symmetry*).
  • Verify Section 3.1 (*Rule 21: Specific Direct Platform Intents Over Generic App Settings*).
  • Verify Section 3.2 (*Rule 29: Defensive Service Lifecycle Finalization & FGS Permission Gating*).
  • Verify Section 4.1 (*Rule 30: Developer Tool Host Pre-Flight Diagnostics*).
  • Verify Section 1.2 (*Rule 34: Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests*).
  • Verify Section 3.3 (*Rule 35: Clean Hardware Teardown and Orphan Defense on Persistence Deletion*).
  • Verify Section 5 (*Test Framework Invariants: Prohibition of returnDefaultValues, Stage 4 unit tests vs Stage 5 clean-room regression*).
  4. *Document Triad Cross-Reference Audit*:
  • Verify `docs/project_protocol.md` and `.agents/skills/` cross-reference the Document Triad without broken links.
  5. *Clean-Room Governance & Regression Execution*:
  • Execute `python3 tools/verify_requirement_governance.py` asserting exit code 0.
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-STB-001: **Resilient WorkManager Initialization & Startup Protection Verification** [Verified]
- **Requirement(s)**: `REQ-STB-001`
- **Component**: **Resilient WorkManager Initialization & Startup Protection Verification**
- **Jira**: ``ATT-621``
- **Test Description**:
  1. Verify `AndroidManifest.xml` configures `androidx.startup.InitializationProvider` with `<meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove" />`.
  2. Verify `TrainingApplication.onCreate()` encapsulates `WorkManager.initialize(...)` within a `try ... catch (Throwable)` block that logs warnings on failure.
  3. Verify `ExportManager.java` and `BackupWorker.kt` guard `WorkManager.getInstance()` invocations against `IllegalStateException` / `Throwable`.
  4. Verify unit test confirms safe error handling when WorkManager is uninitialized.
  5. Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-002: **TrackerService Foreground Service Resilience Verification** [Verified]
4. Create and run unit tests verifying safe handling of `null` intent restarts without background location permission and graceful recovery when `startForeground` throws `SecurityException`.
5. Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.]
- **Requirement(s)**: `REQ-STB-002`
- **Component**: **TrackerService Foreground Service Resilience Verification**
- **Jira**: ``ATT-622``
- **Test Description**:
  1. Verify `TrackerService.onStartCommand` returns `Service.START_STICKY`.
  2. Verify that when `intent == null` and `hasBackgroundLocationPermission()` is false, `startForeground` is not called with location type, `stopSelf()` is invoked, and workout state is preserved.
  3. Verify that all `startForeground` calls are wrapped in `try ... catch (SecurityException

### TST-STB-003: **Interrupted Workout Resumption & Recovery Verification** [Verified]
- **Requirement(s)**: `REQ-STB-003`
- **Component**: **Interrupted Workout Resumption & Recovery Verification**
- **Jira**: ``ATT-635``
- **Test Description**:
  1. Verify `TrackerService.showTrackingInterruptedNotification()` attaches `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` and `SELECTED_FRAGMENT = START_OR_TRACKING` to its `PendingIntent`.
  2. Verify `MainActivityWithNavigation.handleIntent()` detects the resumption extra, sets `cResumeFromCrash = true`, cancels the notification, and resumes tracking via `REQUEST_START_TRACKING`.
  3. Verify `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` accurately identifies unfinished workouts (`FINISHED == 0`).
  4. Verify app launch prompts `StartOrResumeDialog` when an unfinished workout exists.
  5. Execute unit tests verifying Intent extras, database detection, and resume flag handling.
  6. Perform on-device validation on Pixel 10 (Android 16): start tracking, kill process via `run-as`, tap interrupted notification, confirm workout resumes in foreground without data loss.
  7. Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-004: **Location Provider Availability & Exception Shielding Verification** [Verified]
3. Verify that when GPS or Network provider is missing or throws `IllegalArgumentException: provider does not exist`, the device calls `LocationUnavailable()` and does not crash.
4. Verify `DeviceManager` checks provider availability and isolates device initialization failures.
5. Execute unit tests in `SpeedAndLocationDeviceResilienceTest.kt` verifying missing provider and exception handling.
6. Run full regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.]
- **Requirement(s)**: `REQ-STB-004`
- **Component**: **Location Provider Availability & Exception Shielding Verification**
- **Jira**: ``ATT-623``
- **Test Description**:
  1. Verify `SpeedAndLocationDevice_GPS` checks `mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null` before requesting location updates.
  2. Verify all `LocationManager.requestLocationUpdates()` calls in `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_Network` are wrapped in `try ... catch (IllegalArgumentException

### TST-STB-005: **Equipment Database Boundary Validation & Error Log Shielding Verification** [Verified]
- **Requirement(s)**: `REQ-STB-005`
- **Component**: **Equipment Database Boundary Validation & Error Log Shielding Verification**
- **Jira**: ``ATT-644``
- **Test Description**:
  1. Verify `EquipmentDbHelper.getEquipmentNameFromId()` returns `null` immediately for non-positive IDs (`0`, `-1`, `-100`) without querying SQLite or calling `Log.e`.
  2. Verify `EquipmentDbHelper.getStravaIdFromId()` returns `null` immediately for non-positive IDs without querying SQLite or calling `Log.e`.
  3. Verify `EquipmentDbHelper.getDeviceIdsForEquipment()` returns an empty list for non-positive IDs without database queries.
  4. Verify valid positive IDs (`> 0`) resolve equipment names and Strava IDs accurately.
  5. Verify non-existent positive IDs (`> 0`) continue to log diagnostic `ERROR` messages.
  6. Verify `WorkoutDataMapper`, `EquipmentDataProvider`, `StravaUploader`, `EquipmentAndSportTypeDiscoveryManager`, and `SportTypeViewModel` short-circuit lookups when `equipmentId <= 0`.
  7. Execute unit tests in `EquipmentDbHelperResilienceTest.kt`.
  8. Run full regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-006: **Navigation Drawer Header Background Resource Availability & Crash Immunity Verification** [Verified]
- **Requirement(s)**: `REQ-STB-006`
- **Component**: **Navigation Drawer Header Background Resource Availability & Crash Immunity Verification**
- **Jira**: ``ATT-1036`, `ATT-1086``
- **Test Description**:
  1. *Resource File Existence Verification*:
  • Verify that `app/src/main/res/drawable-nodpi/menu_header_background.jpg` exists in the codebase.
  • Verify that the resource can be resolved across all screen densities without falling into a missing-resource state.
  2. *Defensive Fallback & Unit Test Verification (`AppNavigationDrawerTest.kt`)*:
  • Verify that `DrawerHeader()` encapsulates background painter resolution in safe defensive error handling.
  • Verify via automated unit test that when resource resolution throws `Resources$NotFoundException`, `DrawerHeader()` does NOT propagate the fatal exception and renders fallback UI cleanly.
  3. *Clean-Room Regression Verification*:
  • Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.
  4. *On-Device / Density Verification*:
  • Deploy debug APK to connected device (`./gradlew installDebug`).
  • Verify navigation drawer opens cleanly with landscape header background.
  • Adjust system display size / text zoom setting and verify drawer continues to display without crashing.

### TST-STB-007: **AndroidX Core 1.15.0 Alignment & Insets Crash Immunity Verification** [Verified]
- **Requirement(s)**: `REQ-STB-007`
- **Component**: **AndroidX Core 1.15.0 Alignment & Insets Crash Immunity Verification**
- **Jira**: ``ATT-1037`, `ATT-1092``
- **Test Description**:
  1. *Dependency Graph Verification*:
  • Run `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.core:core` and verify that the resolved version across all configurations is strictly `1.15.0`.
  • Run `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.core:core-ktx` and verify that the resolved version is strictly `1.15.0`.
  2. *Automated Unit & Compatibility Verification*:
  • Create automated unit test `CoreDependencyAlignmentTest.kt` verifying that `WindowInsetsCompat` does not invoke missing platform methods on API 34 configurations.
  3. *Clean-Room Regression Verification*:
  • Run full test suite (`./gradlew testDebugUnitTest`) to ensure 100% of existing unit tests pass without regressions under `androidx.core:1.15.0`.
  4. *On-Device / Compose Verification*:
  • Deploy debug APK to connected device (`./gradlew installDebug`).
  • Verify application startup, Compose navigation drawer, and bottom popups render properly without insets crashes.

### TST-STB-008: **Resilient Permission-Gated Location Provider Access & SecurityException Immunity Verification** [Verified]
- **Requirement(s)**: `REQ-STB-008`
- **Component**: **Resilient Permission-Gated Location Provider Access & SecurityException Immunity Verification**
- **Jira**: ``ATT-1243``
- **Test Description**:
  1. Verify `MainActivityWithNavigation.onCreate()` evaluates `ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED` prior to querying `LocationManager.getProvider(LocationManager.GPS_PROVIDER)`.
  2. Verify that when `ACCESS_FINE_LOCATION` is missing or revoked on startup, `LocationManager.getProvider()` is bypassed and `GPSDisabledDialog` is suppressed.
  3. Verify all calls to `LocationManager.getProvider()` and `isProviderEnabled()` are encapsulated in localized exception handling catching `SecurityException` and `IllegalArgumentException`.
  4. Verify that when `LocationManager.getProvider()` throws `SecurityException` or returns `null`, the exception is logged cleanly without crashing the application process.
  5. Verify that in `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_Network`, `getProvider()` is guarded inside existing try-catch blocks and calls `LocationUnavailable()` safely on failure.
  6. Create and run automated unit tests in `LocationPermissionSafetyTest.kt` verifying unpermissioned cold starts, missing providers, and permission grant handling.
  7. Run full clean-room regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-STB-009: **Asynchronous Workout Initialization, Barrier Synchronization & ANR Immunity Verification** [Verified]
- **Requirement(s)**: `REQ-STB-009`
- **Component**: **Asynchronous Workout Initialization, Barrier Synchronization & ANR Immunity Verification**
- **Jira**: ``ATT-1245` / `ATT-1260``
- **Test Description**:
  1. *Main-Thread Execution Latency Verification*:
  • Verify that `TrackerService.onStartCommand(START_NORMAL)` returns immediately (`Service.START_STICKY`) on the calling thread (< 10ms) without blocking on SQLite schema DDL (`CREATE TABLE`, `DROP TABLE`) or disk page reads.
  2. *Asynchronous Table Commitment & CompletableFuture Verification*:
  • Verify that `mTableInitializationFuture` completes successfully upon table creation on `mDbExecutor`.
  • Verify that sensor samples dispatched to `mDbExecutor` await `mTableInitializationFuture.get(5, TimeUnit.SECONDS)` and persist without `SQLiteException: no such table`.
  3. *Zero Sample Loss / Buffer Verification*:
  • Verify that early sensor ticks arriving while table creation is queued on `mDbExecutor` are buffered in the executor queue and persisted once the table future completes.
  4. *Broadcast Deferral Verification*:
  • Verify that `notifyTrackingStarted(workoutId)` broadcast is emitted strictly after table creation completes, preventing downstream UI observers from querying uninitialized tables.
  5. *Defensive Error Recovery & Graceful Teardown*:
  • Verify that if table creation throws `SQLException`, `mTableInitializationFuture` completes exceptionally, `mTrackerHandle` is cancelled, `showTrackingInterruptedNotification()` is shown, and `performStopSelf()` is called without process crash.
  6. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-STB-010: **Compose List Map Lite-Mode Enforcement & RenderThread Deadlock Immunity Verification** [Verified]
- **Requirement(s)**: `REQ-STB-010`
- **Component**: **Compose List Map Lite-Mode Enforcement & RenderThread Deadlock Immunity Verification**
- **Jira**: ``ATT-1246` / `ATT-1272``
- **Test Description**:
  1. *Lite Mode Configuration Verification*:
  • Verify that `PathPreviewMap.kt` configures `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
  • Verify that `WorkoutClusterComponents.kt` (`ClusterCard` and `ClusterWorkoutCard`) configures `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
  2. *Visual & Functional Integrity Verification*:
  • Verify that `PathPreviewMap` in Lite Mode correctly renders polyline coordinates, Start marker, End marker, and Apex marker.
  • Verify that `CameraUpdateFactory.newLatLngBounds` correctly executes bounds clamping without exceptions in Lite Mode.
  • Verify that `onMapClick` callbacks continue to trigger detail navigation when tapped.
  3. *Interactive Map Preservation Verification*:
  • Verify that full-screen interactive maps (`ATrainingTrackerMap.kt`, `LapEditBottomSheet.kt`, `ManualClusterScreen.kt`) maintain interactive mode (`liteMode = false`) with user gestures fully functional.
  4. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.
  5. *Physical Device Verification*:
  • Deploy debug build onto connected device `66020DLCR002FL`.
  • Rapidly scroll through route cluster list and workout list containing embedded preview maps.
  • Perform backgrounding and task switching during scroll; inspect Logcat to verify 0 ANRs, 0 native deadlocks, and clean UI thread responsiveness.

### TST-STB-011: **DevicesTabbedViewModel SavedStateHandle Fallbacks & Crash Immunity Verification** [Verified]
- **Requirement(s)**: `REQ-STB-011`
- **Component**: **DevicesTabbedViewModel SavedStateHandle Fallbacks & Crash Immunity Verification**
- **Jira**: ``ATT-1308``
- **Test Description**:
  1. *Empty SavedStateHandle Instantiation Verification*:
  • Instantiate `DevicesTabbedViewModel` with an empty `SavedStateHandle()`.
  • Verify `protocol` defaults cleanly to `Protocol.ALL` without throwing `NullPointerException`.
  • Verify `uiState.value` resolves to `UiState.DisplayingTabs(DeviceType.ALL)` without stalling on `UiState.AwaitingDeviceTypeSelection`.
  • Verify `SavedStateHandle` persists the default values.
  2. *Malformed / Unknown Value Fallback Verification*:
  • Instantiate with invalid/corrupted strings (e.g. `"UNKNOWN_PROTO"`, `"UNKNOWN_DEV"`).
  • Verify safe fallback to `Protocol.ALL` and `DeviceType.ALL` without exceptions.
  3. *Explicit Parameter Preservation Verification*:
  • Instantiate with valid parameters (`Protocol.ANT_PLUS`, `DeviceType.HEART_RATE`).
  • Verify explicit values are accurately resolved and preserved in state.
  4. *Dynamic Filter Update Verification*:
  • Invoke `updateFilters(Protocol.BLUETOOTH_LE, DeviceType.POWER)`.
  • Verify new values are reflected in `protocol`, `uiState`, and `SavedStateHandle`.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-STR-018: **Global Translation Parity, Array Integrity & Formatter Audit** [Verified]
- **Requirement(s)**: `REQ-LOC-001`
- **Component**: **Global Translation Parity, Array Integrity & Formatter Audit**
- **Jira**: ``ATT-525``
- **Test Description**:
  1. Verify all 9 supported locales possess 100% of translatable string keys (0 missing keys, 0 orphaned keys).
  2. Verify all localized format strings adhere strictly to `REQ-UI-122` positional specifier syntax (`%1$s`), with 100% parity in specifier count, indices, and conversion types against default English resources.
  3. Verify all localized string-arrays have identical item counts to base `values/arrays.xml`.
  4. Verify `filter_max` is defined in `strings_filters.xml` across all locales and removed from `arrays.xml` and `strings.xml`.
  5. Verify `devices_sensors_title` is defined in `strings_devices.xml` in Spanish.
  6. Verify `backup_interval_values` has `translatable="false"` and obsolete `Basic_Sport_Types_values` is removed.
  7. Execute automated tests in `TranslationParityTest.kt`.
  8. Run full regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-012: **Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance Verification** [Verified]
- **Requirement(s)**: `REQ-STB-012`
- **Component**: **Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance Verification**
- **Jira**: ``ATT-2079``
- **Test Description**:
  1. *Forensic Classification & Idempotence Test (`ProcessExitReasonHelperTest.kt`)*:
  • Verify `ProcessExitReasonHelper` maps `REASON_EXCESSIVE_RESOURCE_USAGE` to `BATTERY_KILL`.
  • Verify repeated calls / screen rotations DO NOT increment `battery_kill_count` without a new timestamp.
  • Verify new exit timestamp increments `battery_kill_count` by 1.
  • Verify `battery_kill_count` resets to 0 when `isIgnoringBatteryOptimizations` becomes true.
  • Verify `openBatteryOptimizationSettings` prioritizes direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` URI per Rule 21.
  • Verify API < 30 safe fallback without reflection errors.
  2. *Dialog Dynamic Titles & Empathetic Copy Test (`StartOrResumeDialogContractTest.kt`)*:
  • Verify dialog title dynamically adapts to specific cause (`unfinished_workout_title_battery`, `unfinished_workout_title_memory`, `unfinished_workout_title_permission`, or generic).
  • Verify body copy uses constructive, empathetic athletic-partnership phrasing free of sarcasm.
  • Verify Stage 1 appends Strava acknowledgment when `uploadToStrava == true`.
  • Verify "Disable Battery Optimization" action is provided on `BATTERY_KILL`.
  • Verify `chooseResume()` and `chooseStart()` callbacks remain wired.
  3. *9-Language Localization Audit*:
  • Verify all dynamic titles, de-escalated escalation copies, Strava text, and button labels exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-STB-013: **Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection Verification** [Verified]
- **Requirement(s)**: `REQ-STB-013`
- **Component**: **Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection Verification**
- **Jira**: ``ATT-2309``
- **Test Description**:
  1. *Single-Pass Sequential Streaming Unit Tests (`WorkoutRepositoryStreamTest.kt`)*:
  • Verify that `loadAllWorkouts()` executes in a single forward pass without calling `c.moveToPosition(...)` or seeking backward across rows.
  • Verify that chunks of 50 are extracted into `RawCursorSnapshot` and vectorized metadata queries (`extrema`, `stravaData`, `clusterNames`, `laps`) execute in batch.
  • Verify that all loaded workouts are accurately mapped with their metadata and export statuses.
  2. *CursorWindow Boundary & Synthetic 2,000 Workouts Stress Test (`WorkoutRepositoryStressTest.kt`)*:
  • Simulate an extensive dataset of 2,000 synthetic workouts backed by a MockCursor.
  • Verify that all 2,000 workouts stream completely to completion into `allWorkouts` without throwing `IllegalStateException`.
  • Verify that progressive UI updates are emitted at expected thresholds (first 10, each batch of 50, and completion).
  3. *Defensive Row Exception Shielding Verification*:
  • Simulate a cursor row throwing `IllegalStateException` or `CursorWindowAllocationException` during row extraction.
  • Verify that the repository catches the exception, logs a warning, skips the faulty row, and completes streaming the remaining rows without crashing the process.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-STB-014: **TrainingApplication Foreground Service Startup Delegation & Exception Shielding Verification** [Verified]
- **Requirement(s)**: `REQ-STB-014`
- **Component**: **TrainingApplication Foreground Service Startup Delegation & Exception Shielding Verification**
- **Jira**: ``ATT-2661``
- **Test Description**:
  1. *Foreground Service Startup Delegation Unit Test (`TrainingApplicationTrackingTest.kt`)*:
  • Verify that `startTracking()` delegates to `ContextCompat.startForegroundService()` instead of `startService()` when targeting `TrackerService`.
  • Verify tracking mode transitions to `TrackingMode.TRACKING`.
  2. *Background Launch Restriction Shielding Unit Test (`TrainingApplicationTrackingTest.kt`)*:
  • Simulate `ContextCompat.startForegroundService()` throwing `IllegalStateException("Not allowed to start service ...: app is in background")`.
  • Verify the exception is caught cleanly without terminating the application process.
  • Verify tracking mode resets to `TrackingMode.READY`.
  • Verify `TrackerService.showTrackingInterruptedNotification()` is displayed to provide user recovery.
  3. *SecurityException Permission Shielding Unit Test (`TrainingApplicationTrackingTest.kt`)*:
  • Simulate `ContextCompat.startForegroundService()` throwing `SecurityException("Missing FGS permission")`.
  • Verify the exception is caught cleanly and logged, preventing crash.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-STB-015: **Simulated Process Exit Reason Injection & Native PyQt6 Desktop Kill Simulator Verification** [Verified]
- **Requirement(s)**: `REQ-STB-015`
- **Component**: **Simulated Process Exit Reason Injection & Native PyQt6 Desktop Kill Simulator Verification**
- **Jira**: ``ATT-3377``
- **Test Description**:
  1. *Debug Injection & Idempotence Unit Tests (`ProcessExitReasonHelperTest.kt`)*:
  • In debug mode: Inject `pref_debug_simulated_kill_reason = "LOW_MEMORY"` into SharedPreferences.
  • Invoke `resolveKillReason(context)`; verify diagnosis reason is `KillReason.LOW_MEMORY`.
  • Verify `pref_debug_simulated_kill_reason` is immediately removed from SharedPreferences.
  • Verify second invocation without injection evaluates standard OS exit reason or generic.
  • Repeat for `"PERMISSION_REVOKED"`, `"BATTERY_KILL"`, and `"GENERIC"`.
  2. *Release Build Isolation Test (`ProcessExitReasonHelperTest.kt`)*:
  • Simulate release build (`isDebug = false`); verify `pref_debug_simulated_kill_reason` is ignored even if present in SharedPreferences.
  3. *Tool Injection CLI & Controller Contract Tests (`tools/test_simulate_kill.py`)*:
  • Verify `inject_simulated_kill_reason()` formats valid XML and executes `adb shell run-as`.
  • Verify `kill_lmk_simulation()` invokes injection before termination.
  • Verify `--kill lmk` CLI argument triggers injection.
  4. *PyQt6 GUI Instantiation & Fallback Contract Tests (`tools/test_simulate_kill.py`)*:
  • Verify `KillSimulatorWindow` instantiates with obsidian theme styling, status cards, and action buttons when PyQt6 is available.
  • Verify graceful fallback to terminal menu when `HAS_PYQT6 == False`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-TOOL-001: **External ADB GPX Replay Tool Verification & Simulation Engine Unit Tests** [Verified]
- **Requirement(s)**: `REQ-TOOL-001`
- **Component**: **External ADB GPX Replay Tool Verification & Simulation Engine Unit Tests**
- **Jira**: ``ATT-2467``
- **Test Description**:
  1. *Fake GPS Engine Unit Tests (`tools/test_fake_gps.py`)*:
  • Verify geodesic distance calculation (Haversine) matches reference distances within 0.1% error.
  • Verify forward travel bearing calculation correctly calculates headings across all 4 cardinal quadrants.
  • Verify forward travel bearing calculation derives heading strictly from coordinate difference between previous and current emitted location fixes.
  • Verify polyline interpolation advances distance monotonically with elapsed time and speed.
  • Verify speed multipliers ($1\times, 2\times, 5\times, 10\times$) scale advancement distance proportionally.
  • Verify deliberate off-route deviation offsets coordinates $\ge 60\text{ m}$ perpendicular to forward bearing.
  • Verify seeking / scrubbing accurately snaps simulation state to target route progress ratio and updates forward bearing from coordinate deltas.
  • Verify GPX parser extracts trackpoints and handles missing elevation/timestamps gracefully.
  2. *ADB Multi-Provider Lifecycle & Process Guard Verification*:
  • Verify multi-provider synchronization: test provider commands (`add-test-provider`, `set-test-provider-enabled`, `set-test-provider-location`, `remove-test-provider`) target all 3 providers (`gps`, `network`, `fused`) to prevent OS location oscillation with live Wi-Fi fixes.
  • Verify single-instance process lock: identifies and terminates previous running background instances (`pgrep -f fake_gps.py`) before starting playback.
  • Verify exit handlers ensure `remove-test-provider` across all registered providers on termination (`SIGINT`, `SIGTERM`, `atexit`).
  3. *Live Device Verification on Google Pixel 10*:
  • Verify `python3 tools/fake_gps.py --sample munich` injects mock locations across `gps`, `network`, and `fused` into attached Pixel 10 and drives aTrainingTracker live tracking/navigation.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` and `python3 tools/test_fake_gps.py` asserting 100% test pass rate with 0 regressions.

### TST-TOOL-002: **Synchronized TCX Workout Replay Tool with PyQt6 GUI & Telemetry Verification** [Verified]
- **Requirement(s)**: `REQ-TOOL-002`
- **Component**: **Synchronized TCX Workout Replay Tool with PyQt6 GUI & Telemetry Verification**
- **Jira**: ``ATT-2969``
- **Test Description**:
  1. *TCX Parsing Unit Tests (`tools/test_replay_workout.py`)*:
  • Verify standard Garmin/Strava `.tcx` XML is parsed extracting latitude, longitude, altitude, distance, HR, cadence, and power.
  • Verify default handling for optional or missing fields.
  2. *Playback Engine & Timeline Scrubbing Tests*:
  • Verify transport transitions (`PLAYING`, `PAUSED`, `STOPPED`).
  • Verify timeline scrubbing snaps telemetry accurately.
  • Verify speed multiplier scaling ($1\times, 2\times, 5\times, 10\times$).
  3. *BLE Payload Construction & BlueZ GATT Server Contract Tests*:
  • Verify `build_power_payload(watts, cadence)` produces valid 8-byte GATT payload for UUID `0x2A63`.
  • Verify `build_hr_payload(bpm)` produces valid 2-byte GATT payload for UUID `0x2A37`.
  • Verify BlueZ GATT Application, Cycling Power Service (`0x1818`), Heart Rate Service (`0x180D`), and dual LE Advertisements (`1818` / `180d`) registration structure.
  4. *Android 14+ Mock Location Command Syntax Tests*:
  • Verify ADB location injection command strictly conforms to `cmd location providers set-test-provider-location <p> --location <lat,lon> --accuracy 2.5 --time <ms>` without illegal `--altitude`, `--speed`, or `--bearing` flags.
  5. *Rule 30 Pre-Flight Self-Diagnostics Tests*:
  • Verify detection of Bluetooth daemon status, adapter power, LE advertising manager, ADB device presence, and shell mock location permission.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py`.
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-TOOL-003: **TCX Replay BLE Battery Service Simulation, GATT Notification & GUI Control Verification** [Verified]
- **Requirement(s)**: `REQ-TOOL-003`
- **Component**: **TCX Replay BLE Battery Service Simulation, GATT Notification & GUI Control Verification**
- **Jira**: ``ATT-3178``
- **Test Description**:
  1. *Battery Payload Generation & Clamping Tests (`tools/test_replay_workout.py`)*:
  • Verify `build_battery_payload(100)` produces `[100]`.
  • Verify `build_battery_payload(0)` produces `[0]`.
  • Verify `build_battery_payload(15)` produces `[15]`.
  • Verify values < 0 are clamped to 0 and values > 100 are clamped to 100.
  2. *Battery Service & Characteristic D-Bus Contract Tests (`tools/test_replay_workout.py`)*:
  • Verify `BATTERY_SERVICE_UUID` equals `'0000180f-0000-1000-8000-00805f9b34fb'`.
  • Verify `BATTERY_LEVEL_CHARACTERISTIC_UUID` equals `'00002a19-0000-1000-8000-00805f9b34fb'`.
  • Verify `BatteryLevelCharacteristic` read/notify flags and value updates.
  3. *BleReplayServer Dual Battery State Tests (`tools/test_replay_workout.py`)*:
  • Verify default initial battery levels are 100% for both HR and Power.
  • Verify constructor accepts `hr_battery` and `power_battery` parameters.
  • Verify `set_hr_battery(val)` and `set_power_battery(val)` update internal state and characteristics.
  4. *PyQt6 Battery Widget & Interaction Tests (`tools/test_replay_workout.py`)*:
  • Verify sliders, status labels, and preset buttons exist in `ReplayGuiWindow`.
  • Verify adjusting sliders updates BLE server battery levels and GUI labels.
  • Verify clicking preset buttons (100%, 50%, 15%, 5%) sets correct slider and BLE server values.
  5. *CLI Argument Parsing Tests (`tools/test_replay_workout.py`)*:
  • Verify `--hr-battery` and `--power-battery` CLI arguments.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py`.
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-TOOL-004: **TCX Replay Per-Device Kill, Disconnection, Reconnection & GUI/CLI Control Verification** [Verified]
- **Requirement(s)**: `REQ-TOOL-004`
- **Component**: **TCX Replay Per-Device Kill, Disconnection, Reconnection & GUI/CLI Control Verification**
- **Jira**: ``ATT-3179``
- **Test Description**:
  1. *BleReplayServer Kill & Revive Lifecycle Unit Tests (`tools/test_replay_workout.py`)*:
  • Verify `kill_hr()` sets `is_hr_killed = True`, unregisters advertisement, and suppresses HR notifications.
  • Verify `revive_hr()` sets `is_hr_killed = False`, re-registers advertisement, and resumes HR notifications.
  • Verify `kill_power()` sets `is_power_killed = True`, unregisters advertisement, and suppresses CP notifications.
  • Verify `revive_power()` sets `is_power_killed = False`, re-registers advertisement, and resumes CP notifications.
  • Verify telemetry values catch up to current TCX timestamp upon revival.
  2. *AdbGpsInjector Kill & Revive Unit Tests (`tools/test_replay_workout.py`)*:
  • Verify `kill()` sets `is_killed = True` and suppresses coordinate enqueuing in `inject_location()`.
  • Verify `revive()` sets `is_killed = False` and resumes coordinate injection.
  3. *PyQt6 GUI Connectivity Controls Contract Tests (`tools/test_replay_workout.py`)*:
  • Verify toggle buttons exist for HR, Power, and GPS in `ReplayGuiWindow`.
  • Verify clicking toggle buttons triggers kill/revive methods and updates status badges.
  4. *CLI Argument Parsing & Automated Trigger Tests (`tools/test_replay_workout.py`)*:
  • Verify `--kill-hr-at`, `--revive-hr-at`, `--kill-power-at`, `--revive-power-at`, `--kill-gps-at`, `--revive-gps-at` parsing.
  • Verify automated schedule triggers execute at target timestamps.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py`.
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-LIV-001: **Active and Approaching Live Segments Numeric Distance Sorting Verification** [Verified]
- **Requirement(s)**: `REQ-LIV-005`
- **Component**: **Active and Approaching Live Segments Numeric Distance Sorting Verification**
- **Jira**: ``ATT-3324``
- **Test Description**:
  1. *Active On-Segment Crossing Unit Boundary Sorting Tests (`LiveSegmentSortingTest.kt`)*:
  • Given Segment A with `remainingDistance_raw = 995.0` (`remainingDistance = "995 m"`) and Segment B with `remainingDistance_raw = 2400.0` (`remainingDistance = "2.40 km"`), both in `LiveSegmentStatus.ON_SEGMENT`.
  • Verify that sorting places Segment A first.
  • Verify that sorting is identical regardless of whether imperial or metric distance formatting is configured.
  2. *Approaching Segment Crossing Unit Boundary Sorting Tests (`LiveSegmentSortingTest.kt`)*:
  • Given Segment A with `distanceToStart_raw = 950.0` (`distanceToStart = "950 m"`) and Segment B with `distanceToStart_raw = 1200.0` (`distanceToStart = "1.20 km"`), both in `LiveSegmentStatus.APPROACHING`.
  • Verify that sorting places Segment A first.
  3. *Fallback Offset & Status Weight Priority Preservation Tests (`LiveSegmentSortingTest.kt`)*:
  • Verify that `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH` (weight 4) outranks `ON_SEGMENT` (weight 3).
  • Verify that finished segments tie-break by `segmentOffset_raw`.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-ARC-001: **Safe Enum Deserialization, Observability & Architecture Guard Verification Suite** [Verified]
- **Requirement(s)**: `REQ-ARC-001`
- **Component**: **Safe Enum Deserialization, Observability & Architecture Guard Verification Suite**
- **Jira**: ``ATT-3562``
- **Test Description**:
  1. *Central Utility Unit Tests (`EnumUtilsTest.kt`)*:
  • Verify valid enum names match corresponding constants.
  • Verify case-sensitive exact match vs whitespace trimming.
  • Verify null input returns fallback or null without exception.
  • Verify empty string and whitespace-only string returns fallback or null.
  • Verify unrecognized enum constant string returns fallback or null.
  • Verify Kotlin reified extension functions `safeEnumValueOf` and `safeEnumValueOfOrNull`.
  2. *Tier A (Hardware & Lifecycle) Validation Tests (`DeviceManagerTest.kt` / `TrackerServiceTest.kt`)*:
  • Verify invalid `Protocol` extra does not trigger BLE/ANT search and logs error.
  • Verify invalid `DeviceType` extra does not trigger BLE/ANT search and logs error.
  • Verify invalid `StartType` in `TrackerService` does not crash service lifecycle.
  3. *Tier B (Database Multi-Record Queries) Deserialization Tests (`WorkoutSummariesDatabaseTest.kt`)*:
  • Verify corrupted or null `SensorType` or `ExtremaType` rows are skipped without throwing NPE or IAE.
  • Verify valid extrema rows in the same cursor are successfully parsed and returned.
  4. *Tier C (Preferences & Configuration) Deserialization Tests (`PeriodSummariesDatabaseTest.kt`, `EquipmentDbTest.kt`)*:
  • Verify null or invalid `BSportType` / `PeriodType` falls back to default values with warning log.
  5. *Static Architectural Audit Guard (`SafeEnumDeserializationAuditTest.kt`)*:
  • Run source code AST / regex scan over all Java and Kotlin source files in `app/src/main`.
  • Assert that no unshielded `valueOf(cursor.getString...)`, `valueOf(intent.getStringExtra...)`, `valueOf(arguments.getString...)`, or `valueOf(sharedPreferences.getString...)` calls exist.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-STB-016: **Lifecycle Safety, Guarded Service Launches & Idempotent Receiver Teardown Verification Suite** [Verified]
- **Requirement(s)**: `REQ-STB-016`
- **Component**: **Lifecycle Safety, Guarded Service Launches & Idempotent Receiver Teardown Verification Suite**
- **Jira**: ``ATT-3563``
- **Test Description**:
  1. *Central Utility Unit Tests (`LifecycleUtilsTest.kt`)*:
  • Verify `safeStartService` returns `ComponentName` on successful service start.
  • Verify `safeStartService` returns `null` and logs `Log.w` when `Context.startService` throws `IllegalStateException` (`BackgroundServiceStartNotAllowedException`), `ForegroundServiceStartNotAllowedException`, or `SecurityException`.
  • Verify `safeStartService` returns `null` when context or intent is null.
  • Verify `safeUnregisterReceiver` returns `true` on successful receiver unregistration.
  • Verify `safeUnregisterReceiver` returns `false` and logs `Log.w` when `Context.unregisterReceiver` throws `IllegalArgumentException: Receiver not registered` or `IllegalStateException`.
  • Verify `safeUnregisterReceiver` returns `false` when context or receiver is null.
  • Verify Kotlin extension functions `Context?.safeStartService(...)` and `Context?.safeUnregisterReceiver(...)`.
  • Verify logging resilience against unmocked `android.util.Log` in local JVM test runners.
  2. *Static Architecture Audit Guard (`LifecycleServiceReceiverSafetyAuditTest.kt`)*:
  • Run source code scan over all Java and Kotlin source files in `app/src/main` (excluding `LifecycleUtils.kt`).
  • Assert zero unshielded `unregisterReceiver(` calls.
  • Assert zero unshielded `startService(` calls.
  3. *CLI Linter Verification (`tools/lint_lifecycle_safety.py`)*:
  • Execute python linter script asserting zero lifecycle violations.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all 2900+ tests.

### TST-TOOL-005: **BLE Cycling Power Simulator Pedal Power Balance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TOOL-005`
- **Component**: **BLE Cycling Power Simulator Pedal Power Balance Verification Suite**
- **Jira**: ``ATT-3678``
- **Test Description**:
  1. *Cycling Power Feature Bitmask Unit Tests (`TestBlePowerSimulator` in `tools/test_ble_power_simulator.py`)*:
  • Verify `CyclingPowerFeatureCharacteristic.value` includes bit 0 (`0x01`, Pedal Power Balance Supported) and bit 3 (`0x08`, Crank Revolution Data Supported), yielding `0x09`.
  • Verify `ReadValue()` returns byte array `[0x09, 0x00, 0x00, 0x00]`.
  2. *Measurement Payload Encoding & Offset Tests (`TestBlePowerSimulator` in `tools/test_ble_power_simulator.py`)*:
  • Verify default payload: `pedal_balance = 50.0` produces `flags = 0x0021` (bits 0 and 5 set), total payload length 9 bytes, byte 4 raw balance = 100 ($50.0\%$), followed by 4 crank revolution data bytes.
  • Verify asymmetry tiers: `pedal_balance = 54.0` (Mild Asymmetry Amber) encodes raw balance 108; `pedal_balance = 40.0` (Severe Asymmetry Red) encodes raw balance 80; `pedal_balance = 48.0` (Balanced Green) encodes raw balance 96.
  • Verify clamping: `pedal_balance = -5.0` clamps to raw balance 0 ($0.0\%$); `pedal_balance = 105.0` clamps to raw balance 200 ($100.0\%$).
  • Verify balance disabled (`pedal_balance = None`): flags = `0x0020` (bit 0 cleared), payload length 8 bytes, no balance byte present.
  3. *Interactive CLI Command Parser Tests (`TestBlePowerSimulator` in `tools/test_ble_power_simulator.py`)*:
  • Verify parsing `b 50`, `b50`, `b 48`, `b 54`, `b 60` sets `pedal_balance` accordingly.
  • Verify parsing `b off` sets `pedal_balance = None`.
  • Verify invalid command rejection (`b abc`, `b -10`, `b 150`) leaves state unchanged without throwing exceptions.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_ble_power_simulator.py` and `./gradlew testDebugUnitTest` asserting 100% test pass rate.

### TST-TOOL-006: **Variable-Interval TCX Interpolation, Elapsed-Time Playback Stepping & Workout-Relative ADB GPS Timestamp Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TOOL-006`
- **Component**: **Variable-Interval TCX Interpolation, Elapsed-Time Playback Stepping & Workout-Relative ADB GPS Timestamp Verification Suite**
- **Jira**: ``ATT-3927``
- **Test Description**:
  1. *Variable-Interval TCX Parsing, Coordinate Interpolation & Fallback Speed Unit Tests (`TestTcxParser` in `tools/test_replay_workout.py`)*:
  • Verify `TcxParser.parse_file()` retains intermediate `<Trackpoint>` elements lacking `<Position>` when valid `<Position>` fixes exist in the file, preserving $1\text{ Hz}$ `hr_bpm`, `cadence_rpm`, and `power_watts` telemetry.
  • Verify `lat`, `lon`, and `ele` are linearly interpolated by `time_sec` across interior `<Position>` gaps.
  • Verify `speed_mps = step_d / dt` is calculated automatically when `<Speed>` extensions are absent from `<Trackpoint>` elements, and verify that duplicate timestamps (`dt < 0.001`) safely set `speed_mps = 0.0` without `ZeroDivisionError`, `NaN`, or `Inf`.
  2. *Elapsed-Time Playback Stepping Unit Tests (`TestReplayEngine` in `tools/test_replay_workout.py`)*:
  • Verify `ReplayEngine.step(dt_sec=1.0)` advances `elapsed_time_sec += 1.0 * speed_multiplier` and selects the corresponding `current_index` by `time_sec` rather than incrementing array index blindly across variable-interval trackpoints.
  • Verify `ReplayEngine.step()` cleanly clamps `current_index` to `len(points) - 1` and transitions to `PAUSED` at the final trackpoint without index-out-of-bounds errors.
  • Verify `seek_ratio()`, `seek_time()`, and `stop()` synchronize `elapsed_time_sec` with `points[current_index].time_sec`.
  3. *Replay Session Epoch Workout-Relative ADB GPS Timestamp Unit Tests (`TestAdbGpsInjector` in `tools/test_replay_workout.py`)*:
  • Verify `AdbGpsInjector.inject_location(..., time_sec=t)` computes `timestamp_ms = base_epoch_ms + int(round(t * 1000.0))` and passes `--time <timestamp_ms>` to `cmd location providers set-test-provider-location`.
  • Verify consecutive forward fixes separated by $\Delta t_{\text{workout}} = 5.0\text{ s}$ produce $\Delta\text{timestamp\_ms} = 5000\text{ ms}$ regardless of replay speed multiplier ($1\times, 2\times, 5\times, 10\times$), and verify `reset_epoch(time_sec)` resets `base_epoch_ms` to maintain the exact relative `time_sec` offset from the new position.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py` and `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-BLD-001: **R8 Release Build Minification, ProGuard Keep Rules & DEX Obfuscation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-BLD-001`
- **Component**: **R8 Release Build Minification, ProGuard Keep Rules & DEX Obfuscation Verification Suite**
- **Jira**: ``ATT-3770``
- **Test Description**:
  1. *Release Build Assembly & Mapping Generation Test (`TST-BLD-001.1`)*:
  • Execute `./gradlew :app:assembleRelease` and verify the build completes successfully with zero compilation or R8 shrinking errors.
  • Verify that `app/build/outputs/mapping/release/mapping.txt` is generated with non-zero size and is bundled into the release AAB/APK.
  2. *DEX Obfuscation Ratio Threshold Verification (`TST-BLD-001.2`)*:
  • Inspect classes in release DEX output; verify the percentage of obfuscated classes is $\ge 25\%$ (target $> 50\%$), satisfying Google Play Console Android vitals threshold.
  3. *ProGuard Rules Contract & Source Verification (`TST-BLD-001.3`, `ProGuardRulesContractTest.kt`)*:
  • Verify `app/proguard-rules.pro` does NOT contain blanket `-keep class androidx.compose.**`.
  • Verify `app/proguard-rules.pro` contains explicit keep rules for domain enums (`com.atrainingtracker.**`), Garmin FIT SDK (`com.garmin.fit.**`), ANT+ Plugin (`com.dsi.ant.**`), PebbleKit (`com.getpebble.android.kit.**`), Dropbox SDK (`com.dropbox.core.**`), and Kotlinx Serialization descriptors.
  4. *Domain Enum SQLite & DataStore Reflection Safety Test (`TST-BLD-001.4`, `ProGuardRulesContractTest.kt`)*:
  • Verify that all domain enums (`SensorType`, `BSportType`, `DeviceType`, `MyUnits`, `ZoneType`, `FilterType`, `FileFormat`, `ExportStatus`, `EquipmentType`) resolve their names via `Enum.valueOf` without throwing `IllegalArgumentException`.
  5. *Clean-Room Regression Suite Execution (`TST-BLD-001.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.
  6. *9-Language Localization Parity (`TST-BLD-001.6`)*:
  • Verify `TranslationParityTest` passes cleanly across all 9 languages.

### TST-BLD-002: **Release CI Workflow Automated Test Gating, Manifest Hygiene & Lint Baseline Verification Suite** [Verified]
- **Requirement(s)**: `REQ-BLD-002`
- **Component**: **Release CI Workflow Automated Test Gating, Manifest Hygiene & Lint Baseline Verification Suite**
- **Jira**: ``ATT-4025``
- **Test Description**:
  1. *CI Workflow Release Test Gating Contract Test (`TST-BLD-002.1`, `ReleaseWorkflowContractTest.kt`)*:
  • Verify that `.github/workflows/android-release.yml` invokes `./gradlew :app:testDebugUnitTest` prior to `:app:assembleRelease` and `:app:bundleRelease`.
  • Verify that release artifact assembly and release asset upload are sequenced strictly after unit tests.
  2. *Manifest MissingClass Hygiene Verification (`TST-BLD-002.2`)*:
  • Verify `app/src/main/AndroidManifest.xml` does not declare non-existent component classes, specifically confirming the excision of orphaned `StravaSegmentsIntentService` to eliminate `MissingClass` lint errors.
  3. *Build Configuration Lint Suppressions Documentation Audit (`TST-BLD-002.3`)*:
  • Verify `app/build.gradle` retains `checkReleaseBuilds = true` to run `lintVitalRelease`.
  • Verify `app/build.gradle` contains clear technical rationale comments for active suppressions: `DuplicatePlatformClasses`, `MissingTranslation`, and `Range`.
  4. *Lint Baseline Synchronization & Burn-Down Verification (`TST-BLD-002.4`)*:
  • Verify `app/lint-baseline.xml` is synchronized with current AGP release variant and contains no false-positive missing class errors.
  5. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-BLD-002.5`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% test pass rate with zero regressions across all 9 supported locales.

### TST-SEC-038: **Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SEC-041`, `REQ-EXT-009`
- **Component**: **Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation Verification Suite**
- **Jira**: ``ATT-3983``
- **Test Description**:
  1. *OAuth Credential Sanitization Test (`TST-SEC-038.1`, `LogcatPrivacySecurityTest.kt`)*:
  • Invoke `StravaHelper.storeJSONData` and token refresh with mock JSON containing `access_token` and `refresh_token`.
  • Verify that zero tokens or full JSON response bodies appear in log output.
  2. *Location & GPS Polyline PII Sanitization Test (`TST-SEC-038.2`, `LogcatPrivacySecurityTest.kt`)*:
  • Update workout in `WorkoutRepository` with dummy `mapPolyline` and coordinate data.
  • Verify that zero polyline strings or GPS points appear in log output.
  3. *HTTP Payload Sanitization Test (`TST-SEC-038.3`, `LogcatPrivacySecurityTest.kt`)*:
  • Mock `StravaUploader` and `StravaEquipmentSynchronizeThread` responses.
  • Verify raw response payloads are not emitted into logs.
  4. *Source Tree Static Lint Guard Test (`TST-SEC-038.4`, `LogcatPrivacySecurityTest.kt`)*:
  • Scan all Java and Kotlin source files in `app/src/main/`.
  • Assert zero un-commented occurrences of `DEBUG = true` or `DEBUG=true`.
  • Run AST/regex pattern verification ensuring no `Log.*` calls reference `mapPolyline`, `accessToken`, or `refreshToken`.
  5. *Mandatory Programmatic Credential Invalidation on Upgrade Test (`TST-SEC-038.5`, `LogcatPrivacySecurityTest.kt`)*:
  • Simulate an existing user with active Strava credentials upgrading to V4.9.40.
  • Verify `TrainingApplication` / `StravaHelper` executes remote deauthorization, purges stored credentials, and marks the migration key.
  6. *Clean-Room Full Suite Regression Execution (`TST-SEC-038.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-SEC-039: **BuildConfig Secret Reflection Audit, Keystore Exclusion & Defaults Configuration Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SEC-042`
- **Component**: **BuildConfig Secret Reflection Audit, Keystore Exclusion & Defaults Configuration Verification Suite**
- **Jira**: ``ATT-4004``
- **Test Description**:
  1. *Required BuildConfig Fields Reflection & Compilation Integrity Test (`TST-SEC-039.1`, `BuildConfigSecretsContractTest.kt`)*:
  • Inspect `BuildConfig::class.java.declaredFields` via reflection.
  • Assert required fields exist: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`.
  • Assert each required field has a non-null, non-blank string value.
  • Assert project compilation (`./gradlew compileDebugSources compileReleaseSources`) succeeds cleanly with zero missing symbol errors after pruning dead keys.
  2. *Forbidden Dead Secrets Exclusion Test (`TST-SEC-039.2`, `BuildConfigSecretsContractTest.kt`)*:
  • Inspect all field names in `BuildConfig::class.java.declaredFields`.
  • Assert that no field matches `RUNKEEPER_.*`, `TRAININGPEAKS_.*`, or `DROPBOX_APP_SECRET`.
  3. *Keystore and Signing Credential Exclusion Test (`TST-SEC-039.3`, `BuildConfigSecretsContractTest.kt`)*:
  • Inspect all field names in `BuildConfig::class.java.declaredFields`.
  • Assert that no field matches `(?i).*key(alias

### TST-STB-017: **MainActivity Launch Intent Lifecycle Sanitization & Safe Resumption Guards Verification Suite** [Verified]
- **Requirement(s)**: `REQ-STB-017`, `REQ-STB-003`, `REQ-UI-124`
- **Component**: **MainActivity Launch Intent Lifecycle Sanitization & Safe Resumption Guards Verification Suite**
- **Jira**: ``ATT-4001``
- **Test Description**:
  1. *Cold Start Intent Processing Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `EXTRA_RESUME_INTERRUPTED_WORKOUT`, removes `EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, and `EXTRA_DRAWER_ITEM_ID`, and calls `chooseResume()`.
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `EXTRA_DRAWER_ITEM_ID`, updates `mSelectedFragmentId`, and removes `EXTRA_DRAWER_ITEM_ID`.
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `SELECTED_FRAGMENT`, updates `mSelectedFragmentId`, and removes `SELECTED_FRAGMENT`.
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `EXTRA_FILTER_CRITICAL_BATTERY` and removes `EXTRA_FILTER_CRITICAL_BATTERY`.
  2. *Activity Recreation Isolation Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Simulate activity recreation with non-null `savedInstanceState` containing a restored fragment ID (e.g. `R.id.drawer_workouts`) while the activity intent contains `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` or `EXTRA_DRAWER_ITEM_ID = R.id.drawer_start_tracking`.
  • Verify that `handleIntent()` is NOT invoked, the restored fragment ID is preserved, and `chooseResume()` is NOT called.
  3. *Runtime New Intent Handling Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Simulate `onNewIntent` delivery while activity is alive.
  • Verify `setIntent(intent)` is updated and new extras are handled and consumed.
  4. *Defensive `chooseResume()` Guard Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Verify that if `TrainingApplication.isTracking()` is true, `chooseResume()` returns immediately without dispatching `REQUEST_START_TRACKING`.
  • Verify that if `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` is false, `chooseResume()` returns immediately without dispatching `REQUEST_START_TRACKING`.
  • Verify that if not tracking and an unfinished workout exists, `chooseResume()` sets `resumeFromCrash = true` and broadcasts `REQUEST_START_TRACKING`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-STB-018: **WorkoutRepository Concurrency, Conflated Dirty-Flag Reloads, Stale Snapshot Defense & Exception Shielding Verification Suite** [Verified]
- **Requirement(s)**: `REQ-STB-018`, `REQ-STB-013`
- **Component**: **WorkoutRepository Concurrency, Conflated Dirty-Flag Reloads, Stale Snapshot Defense & Exception Shielding Verification Suite**
- **Jira**: `ATT-4002`, `ATT-5565`
- **Test Description**:
  1. *Conflated Per-Workout Reload & Lifecycle Callback Unit Tests (`TST-STB-018.1`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify concurrent reload requests during an active reload mark the workout dirty and trigger a sequential re-query.
  • Verify transitions from `finished = false` to `true` trigger `PeriodsRepository.onWorkoutFinished` and `WorkoutClusterEngine.onWorkoutFinished`.
  2. *Concurrent Delete-During-Stream Stale Overwrite Defense Unit Tests (`TST-STB-018.2`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify workouts deleted via `deleteWorkout(id)` during progressive history streaming are not resurrected by subsequent progressive or terminal emissions.
  3. *Skipped Row Recovery & Guaranteed Terminal Batch Emission Unit Tests (`TST-STB-018.3`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify exceptions during row snapshot extraction or mapping are logged and skipped without crashing the loader.
  • Verify unconditional terminal emission publishes all valid loaded workouts including the final partial batch.
  4. *Atomic Loading Guard Concurrency Protection Unit Tests (`TST-STB-018.4`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify concurrent invocations of `loadAllWorkouts()` return immediately without spawning parallel cursor traversals.
  5. *Pure Functional CAS Transformation Contract Tests (`TST-STB-018.5`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify `updateWorkoutInMemory` and `saveWorkout` execute zero coroutines, database queries, or external mutations inside `_allWorkouts.update`.
  • Verify un-cached workouts trigger background fetch outside CAS lambda.
  6. *Defensive Deletion Reporting & Rule 74 Completion Ordering Unit Tests (`TST-STB-018.6`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify `deleteWorkout` posts `Pair(id, false)` when deletion returns false or throws an exception.
  • Verify `_deletionProgress` unconditionally resets to `DeletionProgress.Idle` prior to posting `deleteFinishedEvent`, preventing race conditions for completion event observers.
  7. *9-Language Localization Parity (`TST-STB-018.7`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity and matching string specifiers across all 9 supported locales.
  8. *Clean-Room Regression Suite Execution (`TST-STB-018.8`)*:
  • Run targeted unit tests and clean-room test suite verifying 100% pass rate.

### TST-STB-019: **MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Verification Suite** [Verified]
- **Requirement(s)**: `REQ-STB-019`, `REQ-UI-356`
- **Component**: **MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Verification Suite**
- **Jira**: ``ATT-4020``
- **Test Description**:
  1. *Service Connection & Runnable Cleanup Unit Tests (`TST-STB-019.1`, `MainActivityLifecycleTest.kt`)*:
  • Verify that `disconnectFromBANALService()` is invoked during `onDestroy()` even when `isChangingConfigurations == true`.
  • Verify that `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)` is called in `onDestroy()`, leaving zero pending runnables.
  2. *Structured Coroutine Scoping Contract Tests (`TST-STB-019.2`, `MainActivityLifecycleTest.kt`)*:
  • Verify battery checks and in-ride monitoring jobs are bound to `lifecycleScope`, cancelling cleanly when the Activity lifecycle is destroyed.
  3. *In-Ride Battery State Retention & Screen-Off Tracking Tests (`TST-STB-019.3`, `MainActivityLifecycleTest.kt`)*:
  • Verify alerted sensor IDs (`mInRideAlertedSensorIds`, `mInRideCriticalAlertedSensorIds`) survive Activity configuration changes / recreation so subsequent telemetry does not re-fire duplicate snackbars.
  • Verify battery monitoring does not stop on `onPause()` while `TrainingApplication.isTracking()` is true.
  4. *Off-Main-Thread Database Access Unit Tests (`TST-STB-019.4`, `MainActivityLifecycleTest.kt`)*:
  • Verify `onServiceConnected` creates filters asynchronously without blocking the main thread.
  • Verify `checkUnfinishedWorkout()` and dialog actions execute `hasUnfinishedWorkout()` and `discardOrFinishUnfinishedWorkout()` off the main thread on `Dispatchers.IO`.
  • Verify `checkGpsEnabledIfPermitted()` does not block the main thread with synchronous database queries.
  5. *Google Play Services Dialog Lifecycle Tests (`TST-STB-019.5`, `MainActivityLifecycleTest.kt`)*:
  • Verify that when the Play Services error dialog is displayed, it is safely dismissed on `onDestroy()` to prevent `WindowLeaked`.
  6. *9-Language Localization & Specifier Parity Audit (`TST-STB-019.6`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity and matching format specifiers across all 9 supported locales.
  7. *Clean-Room Full Suite Regression Execution (`TST-STB-019.7`)*:
  • Execute targeted tests and `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-TST-001: **Deterministic JVM Test State Isolation, Production Catch Excision & Exporter Test Coverage Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TST-001`
- **Component**: **Deterministic JVM Test State Isolation, Production Catch Excision & Exporter Test Coverage Verification Suite**
- **Jira**: ``ATT-4024``
- **Test Description**:
  1. *Locale and TimeZone Lifecycle Restoration Tests (`TST-TST-001.1`)*:
  • Verify that `IntensityFactorFormatterTest`, `BatteryRemainingTimeFormatterTest`, `BatteryDeviceTest`, `AdvancedTuningAccordionTest`, and `PeriodsAggregationTest` capture and restore original `Locale.getDefault()` and `TimeZone.getDefault()` in `@After` blocks, preventing JVM state pollution across subsequent tests.
  2. *Battery Test Strict Assertions (`TST-TST-001.2`)*:
  • Verify `BatteryRemainingTimeFormatterTest` and `BatteryDeviceTest` use strict `assertEquals("Charging", ...)` and deterministic expected value assertions, eliminating bare `assert` and tautological `

### TST-BLD-003: **Manifest Component Security, GPX Intent Filter Hardening & Permission Invariants Verification Suite** [Verified]
- **Requirement(s)**: `REQ-BLD-003`
- **Component**: **Manifest Component Security, GPX Intent Filter Hardening & Permission Invariants Verification Suite**
- **Jira**: ``ATT-4027``
- **Test Description**:
  1. *Manifest Security & Encapsulation Contract Test (`TST-BLD-003.1`, `ManifestSecurityContractTest.kt`)*:
  • Verify `ConfigPebbleViewsActivity` has `android:exported="false"`.
  • Verify `GpxImportActivity` does not specify `android:host="*"` or `android:scheme="file"`.
  • Verify `ACCESS_BACKGROUND_LOCATION` is declared in `<uses-permission>`.
  • Verify `application` declares `android:largeHeap="true"`.
  2. *GPX Intent Filter MIME & Path Specification Verification (`TST-BLD-003.2`, `ManifestSecurityContractTest.kt`)*:
  • Verify `GpxImportActivity` intent filters support `content://` scheme with `application/gpx+xml`, `application/xml`, and `text/xml`.
  • Verify `application/octet-stream` is scoped with `.gpx` path extension patterns.
  3. *Manifest Background Location Rationale Comment Audit (`TST-BLD-003.3`)*:
  • Verify `AndroidManifest.xml` does not contain misleading "Not necessary" comments regarding `ACCESS_BACKGROUND_LOCATION` and correctly documents Android 14+ FGS `START_STICKY` recovery contract (`REQ-TRK-015`).
  4. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-BLD-003.4`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate with zero regressions across all 9 supported locales.

### TST-TST-002: **Fragile UI Source-Grep Contract Test Modernization & Architectural Guard Preservation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TST-002`
- **Component**: **Fragile UI Source-Grep Contract Test Modernization & Architectural Guard Preservation Verification Suite**
- **Jira**: ``ATT-4028``
- **Test Description**:
  1. *Return Navigation HUD Contract Modernization Tests (`TST-TST-002.1`, `ReturnNavigationHudContractTest.kt`)*:
  • Verify that source-reading string assertions (`content.contains("RoundedCornerShape(12.dp)")`, `"TTColor.RouteSelected"`, emoji checks) are excised.
  • Verify that `ReturnNavigationState` model assertions thoroughly validate formatting, unit conversions, and metric visibility across all states.
  2. *Route Tabbed Clean Layout Reflection & Decoupling Tests (`TST-TST-002.2`, `RouteTabbedCleanLayoutTest.kt`)*:
  • Verify that file-grep substring assertions are excised from `RouteTabbedScreen`, `RouteList`, and `RouteItem` test methods.
  • Verify that dead class excision and decoupled properties are asserted via programmatic reflection (`Class.forName`, `declaredFields`, `declaredMethods`) and file existence checks.
  3. *Turn Direction Icon XML DOM & Geometric Milestone Tests (`TST-TST-002.3`, `TurnDirectionIconContractTest.kt`)*:
  • Verify that raw path string greps (`"V20"`, `"h-2"`, `"M6,20"`, `"V20z"`) are excised.
  • Verify that baseline grounding (y=20), stem width (2dp), margin clearance, and reflection symmetry are verified via XML DOM parsing and `evaluateAbsoluteMilestones` mathematical vertex analysis.
  4. *Architectural Governance Guard Invariance Audit (`TST-TST-002.4`)*:
  • Verify that structural policy guards (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`) pass with 100% success.
  5. *9-Language Localization Audit (`TST-TST-002.5`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 locales via `TranslationParityTest` (0 new user-facing strings).
  6. *Clean-Room Full Suite Regression Execution (`TST-TST-002.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-STR-019: **Locale Hygiene, Translation Completeness & ExtraTranslation De-duplication Verification Suite** [Verified]
- **Requirement(s)**: `REQ-LOC-002`, `REQ-LOC-001`
- **Component**: **Locale Hygiene, Translation Completeness & ExtraTranslation De-duplication Verification Suite**
- **Jira**: ``ATT-4029``
- **Test Description**:
  1. *Translation Completeness Audit (`TST-STR-019.1`)*:
  • Verify `values-de/strings.xml` contains German translations for `pebble_build_in`, `tss`, and `pedal_power_balance`.
  • Verify `values-nl/strings.xml` contains Dutch translations for `zone_2_label`, `zone_3_label`, and `zone_5_label`.
  • Verify `values-pl/strings.xml` contains Polish translations for `exporting_to_file*` (4 keys).
  • Verify `values-{es,fr,it,ja,nl,pl,pt}/strings.xml` contain localized translations for `backup_restore`.
  2. *ExtraTranslation Non-Translatable Redundancy Audit (`TST-STR-019.2`, `TranslationParityTest.kt`)*:
  • Execute `testNoExtraTranslationsInLocales` asserting zero keys marked `translatable="false"` in base `values/strings.xml` exist in any `values-<locale>/strings.xml` file.
  3. *Redundant Array Excision Audit (`TST-STR-019.3`)*:
  • Verify redundant string arrays in `values-de/arrays.xml` and `values-pl/arrays.xml` referencing `@string/...` are removed, and array resolution functions identically from base.
  4. *9-Language Localization Parity Test (`TST-STR-019.4`, `TranslationParityTest.kt`)*:
  • Verify all 9 locales pass 100% string key parity and format specifier parity under `TranslationParityTest.kt`.
  5. *Clean-Room Full Suite Regression Execution (`TST-STR-019.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full project test suite.

### TST-ARC-002: **Exception Handling Hygiene, Fatal Error Escalation & Diagnostic Observability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-ARC-002`
- **Component**: **Exception Handling Hygiene, Fatal Error Escalation & Diagnostic Observability Verification Suite**
- **Jira**: ``ATT-4030``
- **Test Description**:
  1. *Silent/Empty Catch Elimination & Warning Logging Unit Tests (`TST-ARC-002.1`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify `StravaUploader.parseTimeToEpochSeconds` handles invalid or corrupted date strings gracefully, returns `null` without throwing unhandled exceptions, and does not silently swallow errors.
  • Verify `WorkoutClusterDatabaseManager.deserializePreviewPaths` returns `emptyList()` without crashing when given malformed JSON.
  • Verify `FitFileWriter` handles invalid distance/timestamp values safely without silent swallows.
  2. *Fatal JVM Error Escalation & Non-Throwable Exception Scoping Tests (`TST-ARC-002.2`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify that `catch (Throwable)` is eradicated in audited operational paths in `PeriodsRepository`, `FitFileWriter`, and `MigrationEngine`.
  • Verify operational error catches use `catch (Exception)`, allowing `OutOfMemoryError` and coroutine cancellation (`CancellationException`) to propagate naturally.
  3. *Thread Interrupt Preservation Tests (`TST-ARC-002.3`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify that when `InterruptedException` is caught in `StravaUploader` backoff sleep, thread interrupt status is restored via `Thread.currentThread().interrupt()`.
  4. *Standard Android Logging & Safe Null Handling Tests (`TST-ARC-002.4`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify `MyBanalDebugHelper` logs via `Log.e(TAG, ...)` instead of `e.printStackTrace()`.
  • Verify `ForkNavigationRepository` eliminates unsafe force unwraps (`!!`) on `lastSearchPos` and handles null safely during rapid concurrency.
  5. *Structural Policy Guard Audit (`TST-ARC-002.5`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify source code AST/regex scan over audited production classes (`FitFileWriter`, `PeriodsRepository`, `StravaUploader`, `MyBanalDebugHelper`, `ForkNavigationRepository`, `WorkoutClusterDatabaseManager`, `MigrationEngine`) asserts zero `e.printStackTrace()` occurrences and zero `catch (Throwable)` in operational logic.
  6. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-ARC-002.6`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate with zero regressions across the codebase.

### TST-ARC-003: **Dead Code Elimination, Volatile Visibility & Lock-Screen API Modernization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-ARC-003`
- **Component**: **Dead Code Elimination, Volatile Visibility & Lock-Screen API Modernization Verification Suite**
- **Jira**: ``ATT-4031``
- **Test Description**:
  1. *Dead Legacy Class Elimination Verification (`TST-ARC-003.1`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify via programmatic reflection (`Class.forName` throwing `ClassNotFoundException`) and file existence that `SelectSportTypeDialog`, `MultiSelectionSpinner`, `DeviceIdAndNameArrayAdapter`, and `InstallANTShitDialog` are completely excised from the project.
  2. *TrainingApplication Dead Method Excision & Volatile State Verification (`TST-ARC-003.2`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify via reflection that `TrainingApplication` no longer declares methods `todo()`, `setIsSegmentListUpdating()`, or `isSegmentListUpdating()`, nor field `mSegmentListUpdating`.
  • Verify via reflection that field `TrainingApplication.cResumeFromCrash` has `Modifier.isVolatile(field.modifiers) == true`.
  3. *MainActivityWithNavigation Dead Method Excision & Lock Screen API Verification (`TST-ARC-003.3`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify via reflection that `MainActivityWithNavigation` no longer declares `checkBatteryOptimizations()` or `showInstallANTShitDialog()`.
  • Verify `NoUnlocking` window configuration branches on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1` calling `setShowWhenLocked(true/false)`.
  4. *AntStartupCheckContractTest Boundary Invariant Audit (`TST-ARC-003.4`, `AntStartupCheckContractTest.kt`)*:
  • Verify `AntStartupCheckContractTest` executes successfully with updated boundary string (`observeNavigationEvents`), maintaining 100% cold-start assertions.
  5. *Top-Level Compose Preview Private Visibility Audit (`TST-ARC-003.5`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify that all top-level `@Preview` composables across UI screens are declared with `private` visibility.
  6. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-ARC-003.6`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate with zero regressions.

### TST-TST-003: **Deterministic Full-Suite Unit Test Scaffolding & Teardown Hygiene Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TST-003`, `REQ-TST-001`, `REQ-BLD-002`
- **Component**: **Deterministic Full-Suite Unit Test Scaffolding & Teardown Hygiene Verification Suite**
- **Jira**: ``ATT-4669``
- **Test Description**:
  1. *Contract Alignment Test (`TST-TST-003.1`, `HardcodedStringsAndUnitsContractTest.kt`)*:
  • Verify that obsolete `TrainingApplication todo method is marked as Deprecated` test is excised and `HardcodedStringsAndUnitsContractTest` passes 100%.
  2. *Static Mock Scaffolding Test (`TST-TST-003.2`, `AltitudeAwareClusterMatchingTest.kt`)*:
  • Verify `testLearnFromWorkout_UpdatesAltitudeExtremaCentroidsInDatabase` mocks `KnownLocationsDatabaseManager` and completes without unmocked Android Location exceptions.
  3. *DataStore & FilesDir Injection Test (`TST-TST-003.3`, `DevicesTabbedViewModelTest.kt`)*:
  • Verify `DevicesTabbedViewModel` instantiations across `DevicesTabbedViewModelTest` pass `mockSettingsDataStore` and stub `mockApplication.filesDir`, executing without NPE.
  4. *Dispatcher Injection & Synchronization Test (`TST-TST-003.4`, `BackupRestoreViewModelLocalRestoreTest.kt`)*:
  • Verify `BackupRestoreViewModel` accepts `ioDispatcher` via `@JvmOverloads` and `testRestoreFromLocalBackup_whenRestoreThrowsException_emitsError` passes `testDispatcher`, synchronizing deterministically via `advanceUntilIdle()`.
  5. *Coroutine Teardown Lifecycle Hygiene Test (`TST-TST-003.5`, `TrackingTabsViewModelInteropTest.kt`)*:
  • Verify all test-created `TrackingTabsViewModel` instances have `viewModelScope.cancel()` invoked in `@After`, preventing `UncaughtExceptionsBeforeTest` cross-test leakage.
  6. *Clean-Room Full Suite Regression Execution (`TST-TST-003.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full 3,840+ unit test suite.

### TST-ARC-004: **Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene Verification Suite** [Verified]
- **Requirement(s)**: `REQ-ARC-004`, `REQ-ARC-003`
- **Component**: **Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene Verification Suite**
- **Jira**: ``ATT-4667``
- **Test Description**:
  1. *WorkoutClustersViewModel Reflection Constructor Test (`TST-ARC-004.1`, `AndroidViewModelReflectionContractTest.kt`)*:
  • Verify via Java reflection (`WorkoutClustersViewModel::class.java.getConstructor(Application::class.java)`) that `WorkoutClustersViewModel` exposes a public 1-arg `(Application)` constructor.
  • Verify that instantiating `WorkoutClustersViewModel` via `getConstructor(Application::class.java).newInstance(mockApplication)` succeeds without `NoSuchMethodException`.
  2. *Comprehensive AndroidViewModel Reflection Contract Test (`TST-ARC-004.2`, `AndroidViewModelReflectionContractTest.kt`)*:
  • Verify via automated reflection contract test that all `AndroidViewModel` subclasses instantiated via default framework factory (`DeviceListViewModel`, `EquipmentViewModel`, `KnownLocationsViewModel`, `MapFragmentWithTrackViewModel`, `RoutesViewModel`, `TrackOnMapAftermathViewModel`, `BackupRestoreViewModel`, `WorkoutClustersViewModel`, `ZonesSettingsViewModel`, `PeriodsViewModel`, `SportTypeViewModel`, `AthleteProfileViewModel`, `EditDeviceViewModel`, `WorkoutSummariesViewModel`) expose an accessible `(Application.class)` constructor.
  3. *Coding Standards Parity Audit (`TST-ARC-004.3`)*:
  • Verify `docs/coding_standards.md` §3.5 codifies the mandatory `@JvmOverloads constructor` rule for reflective framework classes.
  4. *Clean-Room Full Suite Regression Execution (`TST-ARC-004.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full unit test suite without regressions.

### TST-PRO-020: **Agile Agentic ASPICE (AAA) Governance, Constructive Resistance, Hypothesis Verification, Stage 5 Resource Gate & Ceremony 2 Completeness Sweep Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-027`
- **Component**: **Agile Agentic ASPICE (AAA) Governance, Constructive Resistance, Hypothesis Verification, Stage 5 Resource Gate & Ceremony 2 Completeness Sweep Verification**
- **Jira**: ``ATT-3534``
- **Test Description**:
  1. *Project Protocol & Governance Preamble Audit*:
  • Verify `docs/project_protocol.md` and `.agents/rules/aspice_governance.md` define Agile Agentic ASPICE (AAA) and the 3 Automotive SPICE pillars (Tripwires, Infinite Context, Constructive Resistance & Documented Deviations).
  2. *Stage 1 Hypothesis Verification Audit*:
  • Verify `.agents/skills/stage1-analysis/SKILL.md` and `templates/analysis_template.md` mandate independent hypothesis verification of ticket-provided problem domains and root causes against the active sprint branch.
  3. *Stage 5 AAPT2 Fast Resource Gate Audit*:
  • Verify `.agents/skills/stage5-verification/SKILL.md` mandates `./gradlew processDebugResources` in Stage 5 Tier 1 verification.
  4. *Ceremony 2 Completeness Sweep Audit*:
  • Verify `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 Step 3 mandates `git show` commit review and codebase completeness sweep (`grep`/`rg`) across `app/src/main` for refactoring/cleanup tickets.

### TST-PRO-021: **Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-028`
- **Component**: **Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Verification Suite**
- **Jira**: ``ATT-4594``
- **Test Description**:
  1. *Domain Shard Structure & Master Index Verification (`TST-PRO-021.1`)*:
  • Verify existence of 7 domain files in `docs/requirements/` and `docs/tests/`, and verify master index tables link to all shards.
  2. *Bitwise ID & Entity Migration Parity Test (`TST-PRO-021.2`)*:
  • Verify `tools/shard_living_docs.py` preserves 100% of all 762 `REQ-` and 764 `TST-` IDs, status tags, and sub-clauses.
  3. *Dual-Format Governance Verification (`TST-PRO-021.3`)*:
  • Verify `tools/verify_requirement_governance.py` and `tools/jira_util.py` detect requirements, diffs, archaeology sections, and update status from `[Planned]` to `[Verified]` in domain-sharded multi-line files.
  4. *Clean-Room Governance & Full Suite Regression Execution (`TST-PRO-021.4`)*:
  • Run `python3 tools/verify_requirement_governance.py` asserting exit code 0; execute `./gradlew testDebugUnitTest` verifying 100% test pass rate.

### TST-PRO-022: **AAA Story Point Estimation, CLI Operations, Complexity Split Guardrail & Sprint Capacity Tracking Verification** [Verified]
- **Requirement(s)**: `REQ-PRO-029`
- **Component**: **AAA Story Point Estimation, CLI Operations, Complexity Split Guardrail & Sprint Capacity Tracking Verification**
- **Jira**: ``ATT-5203``
- **Test Description**:
  1. *Jira CLI Story Point Unit & Integration Test (`TST-PRO-022.1`, `tools/test_jira_story_points.py`)*:
  • Test `set-points` payload generation for integer and float points (`float(points)`), string parsing, and clearing (`None`).
  • Test `create-issue` argument parsing for `--points=N` and `--story-points=N`.
  • Test `list` sprint points aggregation (handling issues with null points, zero points, and valid points, and verifying total calculation and unestimated count).
  • Test `show` display formatting with and without story points.
  2. *Live Jira REST API Integration Verification (`TST-PRO-022.2`)*:
  • Execute `python3 tools/jira_util.py set-points ATT-5203 2` and verify `python3 tools/jira_util.py show ATT-5203` displays `*Story Points*: 2.0 SP` (or `2 SP`).
  • Verify `python3 tools/jira_util.py list` lists all active sprint tickets with `[X SP]` tags and the total sprint summary line.
  3. *Skill & Presentation Governance Audit (`TST-PRO-022.3`)*:
  • Verify `.agents/skills/sprint-planner/SKILL.md` documents Ceremony 1 estimation, `\ge 5 SP` split guardrail, target capacity envelope (~24–30 SP), and Ceremony 2 retro points tracking.
  • Verify `.agents/skills/brainstormer/SKILL.md` documents `--points` flag and `\ge 5 SP` decomposition.
  • Verify `docs/presentations/agile_agentic_aspice_beamer.tex` references the 1/2/3/5+ Story Point Complexity Guardrail and human review capacity model.
  4. *Clean-Room Governance Verification (`TST-PRO-022.4`)*:
  • Execute `python3 tools/verify_requirement_governance.py` asserting exit code 0 and zero governance violations.

### TST-PRO-033: **Online User Guide Content, Exclusion & Asset Contract** [Verified]
- **Requirement(s)**: `REQ-PRO-033`
- **Component**: **Online User Guide Content, Exclusion & Asset Contract**
- **Jira**: ``ATT-2275``, ``ATT-5418``
- **Test Description**:
  1. *Jekyll Configuration & Exclusion Hygiene (`TST-PRO-033.1`)*:
  • Verify `docs/_config.yml` exists and explicitly excludes internal developer paths (`ci-secrets.md`, `project_protocol.md`, `coding_standards.md`, `design_guidelines.md`, `engineering/`, `requirements/`, `tests/`).
  2. *Landing Page & Bilingual Chapter Completeness (`TST-PRO-033.2`)*:
  • Verify `docs/index.md` exists and links to both German and English guides.
  • Verify all 18 chapter files exist across `docs/guide/de/` and `docs/guide/en/` with non-empty content.
  3. *Image Asset Resolution & Existence (`TST-PRO-033.3`)*:
  • Verify all markdown image references (`../img/...` or `img/...`) resolve to existing, non-empty PNG/JPG image assets under `docs/guide/img/`.
  4. *Anchor Parity & Deep Linking Contract (`TST-PRO-033.4`)*:
  • Verify all `<a id="...">` anchor identifiers match 1:1 between corresponding German and English chapter files.
  5. *Layout Structure & HTML/Markdown Asset Resolution (`TST-PRO-033.5`, ATT-5418)*:
  • Verify `OnlineUserGuideContractTest.kt` parses both Markdown `![]()` and HTML `<img src="...">` / `<a href="...">` syntax, asserting all referenced screenshot assets exist and are non-empty.
  • Verify 100% anchor parity across all 9 chapters between DE and EN.

### TST-PRO-034: **ASPICE SWE.1/2/4/6 Audit Hardening Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-034`
- **Component**: **ASPICE Audit Hardening Verification Suite**
- **Jira**: ``ATT-5431``
- **Test Description**:
  1. *Shard Status & Master Table Counter Parity Verification (`TST-PRO-034.1`)*:
  • Verify that all completed items in `docs/requirements/` and `docs/tests/` carry `[Verified]`, backlog items carry `[Planned]`, and tables in `docs/requirements.md` and `docs/tests.md` have 100% mathematical parity.
  2. *ASPICE SWE.2 Architecture Completeness & Dynamic Behavioral Models (`TST-PRO-034.2`)*:
  • Verify that `docs/architecture.md` covers all layers, maps all 9 domain shards and requirement prefix families, includes 3 valid Mermaid diagrams (Tracking lifecycle, Sensor failover, Post-workout pipeline), and specifies threading/migration invariants.
  3. *Automated Stage-5 Master Counter Recalculation Verification (`TST-PRO-034.3`)*:
  • Verify that `auto_verify_living_docs_on_stage5` in `tools/jira_util.py` correctly updates master tables on simulated/actual Stage 5 submissions and `verify_requirement_governance.py` validates counter consistency.
  4. *Universal Image Inspection, Code Grounding & Continuous SWE.2 Skill Governance (`TST-PRO-034.4`)*:
  • Verify that `aspice_governance.md`, `stage1-analysis`, `stage3-impl-plan`, `stage4-implementation`, and `stage5-verification` codify universal `read_file` image inspection, documentation code-grounding, and SWE.2 architecture updates.
  5. *Clean-Room Governance & Regression Execution (`TST-PRO-034.5`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted build tests.

### TST-PRO-035: **Standalone Agile Agentic ASPICE (AAA) Core Framework, Swiss Cheese Reliability & Starter Templates Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-035`
- **Component**: **Höchstmaß Core Framework & Starter Templates**
- **Jira**: `ATT-3922`, `ATT-5817`, `ATT-6092`
- **Test Description**:
  1. *README, Project Protocol, Epigraph, Etymology, 2-Column 5-Layer SVG & Entry Barrier Verification (`TST-PRO-035.1`, `HoechstmassCoreFrameworkContractTest.kt`)*:
  • Verify `tools/hoechstmass/README.md` and `tools/hoechstmass/docs/project_protocol.md` exist and contain `# Höchstmaß — The AAA Coding Framework` title, inside-out AAA structure (ASPICE -> Agentic -> Agile), the ChatGPT epigraph quote, human/AI boundary attribution, multi-model review (Claude, Gemini, ChatGPT), Höchstmaß etymology callout (`/ˈhøːçstˌmaːs/`), 2-column 5-layer Swiss Cheese SVG asset (`tools/hoechstmass/assets/swiss_cheese_layers.svg`) with `L1`–`L5` barrier plates and `✕` markers, 5-layer decorrelation formula ($P = p_1 \cdot p_2 \cdot p_3 \cdot p_4 \cdot p_5$), and Chapter 5 Entry Barrier.
  2. *Generalized Document Triad & Isolated In-Place Review Workflow Contract Verification (`TST-PRO-035.2`, `HoechstmassCoreFrameworkContractTest.kt`)*:
  • Verify existence and content of generalized `.agents/rules/aspice_governance.md` (8 Core Rules), `docs/coding_standards.md` (§1.1–§1.5 invariants), `docs/design_guidelines.md` (component heuristics), `tools/review_tool.py`, protocol `.reviews/` and `Emacs` workflow specification, and `.reviews/` isolation via `.git/info/exclude`.
  3. *10 Generalized Skills & Deliverable Templates Verification (`TST-PRO-035.3`, `HoechstmassCoreFrameworkContractTest.kt`)*:
  • Verify existence of all 10 project-agnostic `SKILL.md` files under `tools/hoechstmass/.agents/skills/` and all 6 markdown templates under `tools/hoechstmass/templates/`.
  4. *Clean-Room Governance & Regression Execution (`TST-PRO-035.4`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-PRO-036: **Parameterized Issue Tracker Automation & Requirement Governance Tooling Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-036`
- **Component**: **Höchstmaß Parameterized Tooling**
- **Jira**: `ATT-5667`
- **Test Description**:
  1. *Declarative Configuration Schema & Template Audit (`TST-PRO-036.1`, `HoechstmassToolingContractTest.kt`)*:
  • Verify existence and non-emptiness of `tools/hoechstmass/tools/hoechstmass_config.json` and `.env.jira.example`.
  • Assert `hoechstmass_config.json` declares `project_key`, `board_id`, `transitions`, `roles`, and `paths`.
  2. *Parameterized Tooling Source & Decoupling Contract (`TST-PRO-036.2`, `HoechstmassToolingContractTest.kt`)*:
  • Verify existence and non-emptiness of `tools/hoechstmass/tools/jira_util.py` and `tools/hoechstmass/tools/verify_requirement_governance.py`.
  • Assert neither tool contains hardcoded `ATT-` or proprietary developer account IDs.
  3. *Python Automated Tooling Unit Test Suite (`TST-PRO-036.3`, `test_hoechstmass_tools.py`)*:
  • Execute `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py` verifying config loading, role resolution, secret masking, and governance validation pass 100%.
  4. *Clean-Room Governance & Regression Execution (`TST-PRO-036.4`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-PRO-037: **Real-World Reference Showcase & Developer Community Launch Posts Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-037`
- **Component**: **Höchstmaß Showcase & Community Documentation**
- **Jira**: `ATT-5668`
- **Test Description**:
  1. *Real-World Reference Showcase & Case Studies Audit (`TST-PRO-037.1`, `HoechstmassShowcaseAndCommunityContractTest.kt`)*:
  • Verify existence of `coding_standards.md`, `design_guidelines.md`, and `CASE_STUDIES.md` under `tools/hoechstmass/examples/android-jetpack-compose/`.
  • Assert `CASE_STUDIES.md` details the 3 adversarial reviews (Mader-Heck Metabolic Model, DFA alpha-1 HRV, Radar bytecode audit).
  2. *Developer Community Launch Posts Integrity Audit (`TST-PRO-037.2`, `HoechstmassShowcaseAndCommunityContractTest.kt`)*:
  • Verify existence of all 5 community articles in `tools/hoechstmass/docs/community/` (`r_softwareengineering.md`, `r_androiddev.md`, `r_google_antigravity.md`, `r_vibecoding.md`, `hackernews_show_hn.md`).
  • Assert all 5 articles contain the ChatGPT epigraph, dual attribution reasons (provenance and cross-model Gemini + ChatGPT UI review), and Höchst·maß etymology box.
  3. *Clean-Room Governance & Regression Execution (`TST-PRO-037.3`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-PRO-038: **Automated Quantitative Sprint Metrics (PA 4.1), Statistical Process Control (PA 4.2) & Ceremony 2 Governance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-038`
- **Component**: **Jira Process Tooling, Sprint Retrospective Governance & Höchstmaß Framework**
- **Jira**: `ATT-5765`, `ATT-5764`
- **Test Description**:
  1. *Sprint Metrics Computation & Parsing (`TST-PRO-038.1`, `test_sprint_metrics.py`)*:
  • Verify parsing of subtask comments with `[ROLE: agent2]` and computation of Gate 1–5 FTPR, Swiss Cheese escape rates ($p_1 \dots p_5$), and Human Acceptance Rate.
  2. *Time-Series Persistence & Idempotent Upsert (`TST-PRO-038.2`, `test_sprint_metrics.py`)*:
  • Verify `--record` writes structured JSON lines to `docs/engineering/metrics/process_metrics.jsonl` and updates existing records idempotently.
  3. *Statistical Process Control (SPC) Outlier Detection (`TST-PRO-038.3`, `test_sprint_metrics.py`)*:
  • Verify computation of rolling mean $\mu$ and standard deviation $\sigma$, and assertion of warnings for metric variations exceeding $\mu \pm 2\sigma$.
  4. *Governance Whitelist & Directory Parity Contract (`TST-PRO-038.4`, `verify_requirement_governance.py`)*:
  • Assert `docs/engineering/metrics/` is whitelisted in `ALLOWED_ENGINEERING_ENTRIES` and passes `verify_requirement_governance.py`.
  5. *Ceremony 2 Scorecard & Retro Template Verification (`TST-PRO-038.5`)*:
  • Verify that `.agents/skills/sprint-planner/SKILL.md` (and hoechstmass mirror) mandates `sprint-metrics --record --post-to-retro` in Step 4.
  • Verify that `templates/sprint_retro_template.md` and `tools/hoechstmass/templates/sprint_retro_template.md` contain the Quantitative Sprint Scorecard section and `PA 4.2 -> PA 5.2` corrective action trigger.
  6. *ASPICE Level 1–5 Capability Matrix & Contract Verification (`TST-PRO-038.6`)*:
  • Verify `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md` codify `PA 1.1` through `PA 5.2`.
  • Assert `HoechstmassCoreFrameworkContractTest.kt` verifies the ASPICE Level 1–5 matrix and retro template parity.

### TST-PRO-039: **Jira Data Center & Enterprise On-Premise Tooling Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-039`
- **Component**: **Jira Process Tooling, Höchstmaß Framework & Deployment Adapters**
- **Jira**: `ATT-5830`
- **Test Description**:
  1. *Deployment Mode Configuration & Environment Parsing (`TST-PRO-039.1`, `test_jira_datacenter.py`)*:
  • Verify config parser correctly detects `deployment_type` from `hoechstmass_config.json` and overrides with `JIRA_DEPLOYMENT`.
  • Assert default deployment falls back to `cloud`.
  2. *Bearer PAT & Corporate CA Bundle Verification (`TST-PRO-039.2`, `test_jira_datacenter.py`)*:
  • Mock HTTP calls under Data Center mode and assert `Authorization: Bearer <token>` header.
  • Assert SSL context configuration passes `JIRA_CA_BUNDLE` path to urllib3 / requests or creates custom SSLContext.
  3. *Assignee Payload Username vs AccountId Dispatch (`TST-PRO-039.3`, `test_jira_datacenter.py`)*:
  • Assert `assign_issue` sends `{"name": "agent1"}` in Data Center mode and `{"accountId": "..."}` in Cloud mode.
  • Assert `get_issue` parses `fields.assignee.name` in Data Center mode.
  4. *Epic Link Custom Field vs Parent Payload Dispatch (`TST-PRO-039.4`, `test_jira_datacenter.py`)*:
  • Assert creating/linking top-level issues in Data Center mode populates `customfield_10014` (or configured custom field) and omits `fields.parent`.
  • Assert creating subtasks in Data Center mode populates `fields.parent` without `customfield_10014`.
  5. *JQL Search REST API v2 Routing (`TST-PRO-039.5`, `test_jira_datacenter.py`)*:
  • Assert `search_issues` dispatches to `/rest/api/2/search` in Data Center mode and `/rest/api/3/search/jql` in Cloud mode.
  6. *Tooling Mirror Synchronization & Regression Suite (`TST-PRO-039.6`)*:
  • Verify identical logic and hashes for shared functions between `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and Python unit tests.

### TST-PRO-040: **Epic Lifecycle Management, 'list-epics' / 'close-epic' CLI Commands, and Taxonomy Governance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-040`
- **Component**: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `.agents/skills/{brainstormer,sprint-planner,jira-workflow}/SKILL.md`
- **Jira**: `ATT-5911`
- **Test Description**:
  1. *List Epics Command & Child Status Aggregation (`TST-PRO-040.1`, `test_jira_epics.py`)*:
  • Mock JQL search returning Epics and child tickets across Cloud (`parent = ...`) and Data Center (`cf[10014] = ...`).
  • Verify `list-epics` computes open and done child ticket counts accurately and formats output.
  2. *Close Epic Triple Guardrail Enforcement (`TST-PRO-040.2`, `test_jira_epics.py`)*:
  • Role Guard: Verify non-coordinator/non-human roles (`--as agent1`, `--as agent2`) abort with exit code 1.
  • Issue Type Guard: Verify passing a standard Story/Bug/Task aborts with exit code 1 and Human Gate notice.
  • Completeness Guard: Verify passing an Epic with open child tickets aborts with exit code 1 listing the open tickets.
  • Success: Verify passing an Epic with 0 open child tickets under `--as coordinator` successfully executes transition to `Erledigt`.
  3. *Tooling Mirror Synchronization & Hoechstmass Parity (`TST-PRO-040.3`, `test_hoechstmass_tools.py`)*:
  • Verify `tools/hoechstmass/tools/jira_util.py` implements identical `list-epics` and `close-epic` subcommands with identical guardrails.
  4. *Skill Documentation & Canonical Epic Taxonomy Audit (`TST-PRO-040.4`)*:
  • Verify `.agents/skills/brainstormer/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, and `.agents/skills/jira-workflow/SKILL.md` (and their hoechstmass mirrors) define the Canonical Epic Taxonomy and human consultation requirement.
  5. *Clean-Room Governance & Verification Execution (`TST-PRO-040.5`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and `python3 -m unittest tools/test_jira_epics.py`.

### TST-PRO-041: **Parallel Sprint Execution Protocol for Disjoint Epics, Flock Serialization & Partitioned Localization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-041`
- **Component**: `.agents/rules/aspice_governance.md`, `.agents/skills/{jira-workflow,stage4-implementation,stage5-verification,sprint-planner}/SKILL.md`, `tools/run_serialized.sh`, `tools/parallel_sprint_lock.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
- **Jira**: `ATT-5914`, `ATT-6743`
- **Test Description**:
  1. *Subagent Worktree Isolation & Disjoint Epic Guardrail (`TST-PRO-041.1`)*:
  • Verify `Workspace: "share"` subagent pairs execute in independent worktrees without branch or working-tree collisions across up to 3 concurrent lanes (`concurrency = 3`).
  • Verify governance enforces strict disjoint Parent Epic constraint (rejecting parallel execution within the same Epic).
  2. *POSIX Flock Build, Device & Merge Mutual Exclusion (`TST-PRO-041.2`, `test_flock_serialization.py`)*:
  • Simulate concurrent processes competing for `/tmp/att_build_merge.lock`.
  • Verify mutual exclusion: exactly one process holds the lock at a time for heavy Gradle tests, `installDebug` / `adb` commands, and Stage 5 squash merges. Subsequent processes queue deterministically, and lock releases reliably upon completion or failure.
  3. *Dynamic Multi-File String Discovery & Parity Test Suite (`TST-PRO-041.3`, `TranslationParityTest.kt`)*:
  • Verify `TranslationParityTest.kt` dynamically scans all `strings*.xml` across `res/values*/`.
  • Assert partitioned domain string files (`strings_<domain>.xml`) maintain 100% key and placeholder parity across all 9 supported locales.
  4. *Rebase-then-Recalculate Living Docs Governance (`TST-PRO-041.4`)*:
  • Verify `recalculate_master_index_tables()` under merge lock after rebase maintains 100% parity across living doc tables with zero Git merge conflicts.
  5. *Deterministic Python Governance Tripwires Verification Suite (`TST-PRO-041.5`, `test_verify_requirement_governance.py`)*:
  • Verify Composable Island Check: newly added non-preview, non-private `@Composable` declarations without production call sites in `app/src/main/` trigger exit code 1.
  • Verify Repository Encapsulation Guard: non-private methods or properties in `*Repository.kt` or `*Repository.java` exposing `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor` trigger exit code 1.
  • Verify Architecture & System Spec Grounding & SYSREQ Guard: non-existent backticked source files or `SYSREQ-*` set mismatches trigger exit code 1.
  6. *Clean-Room Governance & Verification Execution (`TST-PRO-041.6`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and `python3 tools/test_verify_requirement_governance.py`.

### TST-PRO-042: **CI Configuration Integrity & Stale Travis Artifacts Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-042`
- **Component**: `ReleaseCiAndLintConfigContractTest.kt`, `app/build.gradle`
- **Jira**: `ATT-6177`
- **Test Description**:
  1. *Repository Root Stale CI File Absence Test (`TST-PRO-042.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Search repository root for `.travis.yml`.
  • Assert that `File(".travis.yml")` does not exist in any candidate repository root directory.
  2. *Build Script Travis Comment Hygiene Test (`TST-PRO-042.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read text content of `app/build.gradle`.
  • Assert that `"travis"` (case-insensitive) is not contained anywhere in the file.
  3. *Authoritative GitHub Actions Workflow Integrity Test (`TST-PRO-042.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Verify that existing tests validating `android-release.yml` continue to execute and pass.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-042.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.

### TST-PRO-043: **SWE.2 Architecture Conformance & Code-Grounding Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-043`
- **Component**: `docs/architecture.md`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6179`
- **Test Description**:
  1. *Authentic Component Presence Test (`TST-PRO-043.1`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` contains authentic class names: `WorkoutClusterEngine`, `GPXFileWriter`, `TCXFileWriter`, `WorkoutSamplesDatabaseManager`, and `MilestoneAutoNamingManager`.
  2. *Drifted Component Absence Test (`TST-PRO-043.2`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` does not contain `RouteLearningEngine`, `GpxExporter`, `TcxExporter`, `WorkoutSamplesDb`, `MilestoneDetector`, or `InAppReviewManager`.
  3. *Tracking State Machine & Dispatcher Conformance Test (`TST-PRO-043.3`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` specifies `TrackingMode` states (`IDLE`, `READY`, `TRACKING`, `PAUSED`) and retains required threading tokens (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-043.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` asserting 100% pass rate.

### TST-PRO-044: **ASPICE SWE.2 Software Interface Specification Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-044`
- **Component**: `docs/architecture/interfaces.md`, `docs/architecture.md`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6180`
- **Test Description**:
  1. *Interface File & Boundary Completeness Test (`TST-PRO-044.1`, `AspiceSweAuditContractTest.kt`)*:
  • Verify existence of `docs/architecture/interfaces.md`.
  • Assert that all 8 interface families (`IF-SWE-01` through `IF-SWE-08`) are defined.
  2. *Mandatory Interface Attribute Completeness Test (`TST-PRO-044.2`, `AspiceSweAuditContractTest.kt`)*:
  • For every interface `IF-SWE-01`..`IF-SWE-08`, assert the presence of Provider, Consumer, Operations/Payload, Threading/Concurrency, and Error Handling fields.
  3. *Architecture Document Integration Test (`TST-PRO-044.3`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` Section 5 contains the interface summary matrix referencing `IF-SWE-01` through `IF-SWE-08`.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-044.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` asserting 100% pass rate.


### TST-PRO-045: **ASPICE SWE.5 Software Integration Verification Strategy Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-045`
- **Component**: `docs/architecture/integration_strategy.md`, `docs/architecture.md`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6183`
- **Test Description**:
  1. *Integration Strategy Structural Completeness Test (`TST-PRO-045.1`, `AspiceSweAuditContractTest.kt`)*:
  • Verify existence of `docs/architecture/integration_strategy.md`.
  • Assert that core sections ("Integration Sequence", "Verification Levels", "Entry and Exit Criteria", "Regression Selection") are present.
  2. *Interface Integration Seam Grounding Test (`TST-PRO-045.2`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture/integration_strategy.md` explicitly references all 8 interfaces `IF-SWE-01` through `IF-SWE-08`.
  3. *Architecture Document Section 6 Conformance Test (`TST-PRO-045.3`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` contains Section 6 referencing `integration_strategy.md`.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-045.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` asserting 100% pass rate.



### TST-PRO-046: **Android Lint CI Quality Gate & Baseline Burn-down Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-046`
- **Component**: `.github/workflows/android-branch-build.yml`, `app/build.gradle`, `app/lint-baseline.xml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6173`
- **Test Description**:
  1. *CI Workflow Lint Step & Artifact Upload Conformance Test (`TST-PRO-046.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains `./gradlew :app:lintDebug` (or `lintDebug`).
  • Assert that the workflow uploads lint report artifacts via `actions/upload-artifact`.
  2. *Build Script Lint Configuration Conformance Test (`TST-PRO-046.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `app/build.gradle`.
  • Assert that the `lint` block specifies `abortOnError = true`, `htmlReport = true`, and `xmlReport = true`.
  3. *Lint Baseline Upper Bound & Issue Count Test (`TST-PRO-046.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Parse `app/lint-baseline.xml`.
  • Count `<issue` XML elements and assert the total count is <= 546.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-046.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.



### TST-PRO-047: **Detekt Static Analysis & Complexity Governance Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-047`
- **Component**: `build.gradle`, `app/build.gradle`, `config/detekt/detekt.yml`, `app/detekt-baseline.xml`, `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6175`
- **Test Description**:
  1. *Build Script Detekt Plugin & Configuration Conformance Test (`TST-PRO-047.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `build.gradle` and `app/build.gradle`.
  • Assert that the detekt plugin is applied and configured with `config/detekt/detekt.yml` and `app/detekt-baseline.xml`.
  2. *Detekt Rules Configuration Conformance Test (`TST-PRO-047.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `config/detekt/detekt.yml`.
  • Assert that complexity rules (`LargeClass`, `LongMethod`, and `CyclomaticComplexMethod`) are present with active thresholds.
  3. *CI Workflow Detekt Step & Artifact Upload Conformance Test (`TST-PRO-047.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains `./gradlew detekt` and an artifact upload step for detekt reports.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-047.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` and `./gradlew detekt` asserting 100% pass rate.



### TST-PRO-048: **Kover Code Coverage Measurement & CI Threshold Gating Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-048`
- **Component**: `build.gradle`, `app/build.gradle`, `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6171`
- **Test Description**:
  1. *Build Script Kover Plugin & Configuration Conformance Test (`TST-PRO-048.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `build.gradle` and `app/build.gradle`.
  • Assert that the Kover plugin (0.9.1) is applied and configured with XML/HTML reporting and verification rules.
  2. *CI Workflow Kover Step & Artifact Upload Conformance Test (`TST-PRO-048.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains Kover reporting/verify task execution and artifact upload for coverage reports.
  3. *Kover Report Generation & Verification Execution Test (`TST-PRO-048.3`, `app/build.gradle`)*:
  • Execute `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`.
  • Assert that XML and HTML reports are produced under `app/build/reports/kover/` and verification threshold rule succeeds.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-048.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.



### TST-PRO-049: **Source-Text Reading Test Audit Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-049`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, `docs/coding_standards.md` §5.4
- **Jira**: `ATT-6186`
- **Test Description**:
  1. *Audit Scanner Execution & Baseline Conformance Test (`TST-PRO-049.1`, `SourceTextReadingAuditContractTest.kt`)*:
  • Recursively scan all `.java` and `.kt` test files in `app/src/test/java/`.
  • Assert that zero tests outside the allow-list read `app/src/main/` production source files.
  2. *Governance Guard Exception Validation Test (`TST-PRO-049.2`, `SourceTextReadingAuditContractTest.kt`)*:
  • Assert that all 13 legitimate architectural governance contract tests are registered in the allow-list.
  3. *Tripwire Violation Detection Test (`TST-PRO-049.3`, `SourceTextReadingAuditContractTest.kt`)*:
  • Validate that unallow-listed test files containing `.readText()` targeting `src/main/` trigger a test failure.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-049.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` asserting 100% pass rate.


### TST-PRO-050: **Automated Architecture Conformance Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-050`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `docs/architecture.md`
- **Jira**: `ATT-6182`
- **Test Description**:
  1. *UI Layer DatabaseManager Isolation Test (`TST-PRO-050.1`, `ArchitectureConformanceContractTest.kt`)*:
  • Recursively scan all `.java` and `.kt` source files in `app/src/main/java/**/ui/`.
  • Assert that zero classes outside the recorded baseline import or reference `*DatabaseManager` or `*DbHelper`.
  2. *Domain Calculation Layer Purity Test (`TST-PRO-050.2`, `ArchitectureConformanceContractTest.kt`)*:
  • Recursively scan all files in domain calculation packages.
  • Assert that zero classes reference Android UI frameworks or SQLite persistence classes.
  3. *Ratcheting Tripwire Violation Detection Test (`TST-PRO-050.3`, `ArchitectureConformanceContractTest.kt`)*:
  • Validate that synthetic unallow-listed UI database references or domain UI imports trigger assertion failures.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-050.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` asserting 100% pass rate and confirm `ArchitectureConformanceContractTest` is registered in `LEGITIMATE_GOVERNANCE_GUARDS`.


### TST-PRO-051: **Android Instrumented Test Infrastructure & Emulator CI Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-051`
- **Component**: `app/build.gradle`, `.github/workflows/android-branch-build.yml`, `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6184`
- **Test Description**:
  1. *Build Script Instrumentation Runner & Dependency Conformance Test (`TST-PRO-051.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `app/build.gradle`.
  • Assert that `testInstrumentationRunner` is configured as `"androidx.test.runner.AndroidJUnitRunner"`.
  • Assert that `androidTestImplementation` dependencies for `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, and `androidx.test:runner` are present.
  2. *CI Workflow Emulator Job & Artifact Upload Conformance Test (`TST-PRO-051.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains an emulator job executing `connectedDebugAndroidTest` (or `:app:connectedDebugAndroidTest`).
  • Assert that the workflow uploads instrumented test results via `actions/upload-artifact`.
  3. *AndroidTest Compilation & Smoke Test Existence Verification (`TST-PRO-051.3`)*:
  • Verify existence of `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt`.
  • Execute `./gradlew compileDebugAndroidTestKotlin` and assert successful compilation without errors.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-051.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.


### TST-PRO-052: **Architectural Resource and Timing Budgets Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-052`
- **Component**: `docs/architecture.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-6181`
- **Test Description**:
  1. *Architecture Section 4.9 Structural & Sectional Conformance Test (`TST-PRO-052.1`, `ArchitectureConformanceContractTest.kt`)*:
  • Read `docs/architecture.md`.
  • Assert that Section 4.9 ("Resource and Timing Budgets") is present.
  • Assert that all 4 critical subsystem domains (`TrackerService`, `BANALService`, `WorkoutSamplesDatabaseManager`, and Cockpit UI/Map) are documented.
  2. *Quantitative Threshold Verification Test (`TST-PRO-052.2`, `ArchitectureConformanceContractTest.kt`)*:
  • Assert that Section 4.9 specifies numeric bounds for CPU, heap memory, latency, battery drain, and disk growth.
  3. *Clean-Room Targeted Test Suite Execution (`TST-PRO-052.3`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` asserting 100% pass rate.


### TST-PRO-053: **System Context Specification and Code-Grounding Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-053`
- **Component**: `docs/system/system_context.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemContextContractTest.kt`
- **Jira**: `ATT-6158`
- **Test Description**:
  1. *System Context Structural & Diagram Verification (`TST-PRO-053.1`, `SystemContextContractTest.kt`)*:
  • Read `docs/system/system_context.md`.
  • Assert that the document exists and contains a valid Mermaid diagram (`flowchart` or `graph`).
  • Assert that all 5 interface domains (Sensors, Platform, Location/Maps, Cloud/Health, Decommissioned) are documented.
  2. *External Elements & Source Grounding Verification (`TST-PRO-053.2`, `SystemContextContractTest.kt`)*:
  • Assert that BLE GATT profiles defined in `docs/system/system_context.md` match UUIDs in `BluetoothConstants.java`.
  • Assert that ANT+ IPC services match package declarations in `AndroidManifest.xml`.
  • Assert that cloud SDK dependencies match entries in `app/build.gradle`.
  • Assert that Runkeeper and TrainingPeaks are explicitly tagged as decommissioned.
  3. *Clean-Room Targeted Test Suite Execution (`TST-PRO-053.3`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"` asserting 100% pass rate.


### TST-PRO-054: **System Requirements for Live Tracking Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-054`
- **Component**: `docs/system/sysreq_live_tracking.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`
- **Jira**: `ATT-6160`
- **Test Description**:
  1. *Specification Structure & SYSREQ Coverage Verification (`TST-PRO-054.1`, `SystemLiveTrackingRequirementsContractTest.kt`)*:
  • Read `docs/system/sysreq_live_tracking.md`.
  • Assert that the document exists and contains all 6 requirements (`SYSREQ-TRK-001` through `SYSREQ-TRK-006`).
  • Assert that each requirement contains non-empty sections for Rationale, Priority, Verification Method, and Traced Software Components.
  2. *Code Grounding & Timing Constants Verification (`TST-PRO-054.2`, `SystemLiveTrackingRequirementsContractTest.kt`)*:
  • Assert that BLE GATT profiles defined in `SYSREQ-TRK-001` match UUIDs in `BluetoothConstants.java`.
  • Assert that reconnection delays in `SYSREQ-TRK-002` match `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS` (1,000 ms) and `MAX_RECONNECT_DELAY_MS` (16,000 ms).
  • Assert that radar starvation watchdog matches `BTLERadarDevice.WATCHDOG_TIMEOUT_MS` (3,500 ms).
  • Assert that 1-Hz technical sampling tick matches `TrackerService` 1,000 ms period.
  3. *Clean-Room Targeted Test Suite Execution (`TST-PRO-054.3`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest"` asserting 100% pass rate.


### TST-PRO-055: **System Requirements for Data Exchange Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-055`
- **Component**: `docs/system/sysreq_data_exchange.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`
- **Jira**: `ATT-6161`
- **Test Description**:
  1. *Specification Structure & SYSREQ Coverage Verification (`TST-PRO-055.1`, `SystemDataExchangeRequirementsContractTest.kt`)*:
  • Read `docs/system/sysreq_data_exchange.md`.
  • Assert that the document exists and contains all 6 requirements (`SYSREQ-DEX-001` through `SYSREQ-DEX-006`).
  • Assert that each requirement contains non-empty sections for Rationale, Priority, Verification Method, and Traced Software Components.
  2. *Code Grounding & Data Exchange Verification (`TST-PRO-055.2`, `SystemDataExchangeRequirementsContractTest.kt`)*:
  • Assert that export formats in `SYSREQ-DEX-001` match `FileFormat` enum entries (`FIT`, `TCX`, `GPX`, `CSV`).
  • Assert that backup archive constants in `SYSREQ-DEX-005` match `BackupManager.BACKUP_FILENAME` (`"aTrainingTracker_backup.attbackup"`).
  • Assert that credential exclusions in `SYSREQ-DEX-006` match `auth_credentials_prefs.xml`.
  • Assert that Runkeeper and TrainingPeaks are explicitly demarcated as decommissioned.
  3. *Clean-Room Targeted Test Suite Execution (`TST-PRO-055.3`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest"` asserting 100% pass rate.


### TST-PRO-056: **System Architectural Design Contract Test Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-056`
- **Component**: `docs/system/system_architecture.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`
- **Jira**: `ATT-6164`, `ATT-6741`
- **Test Description**:
  1. *System Elements & Interfaces Coverage Verification (`TST-PRO-056.1`, `SystemArchitectureDesignContractTest.kt`)*:
  • Read `docs/system/system_architecture.md`.
  • Assert that the document exists and defines all 5 system elements (`SYS-EL-01` through `SYS-EL-05`) and 6 system interfaces (`SYS-IF-01` through `SYS-IF-06`).
  • Assert protocol, payload data schema, and timing characteristics are documented for each interface.
  2. *Dynamic Behavior & Mermaid Syntax Verification (`TST-PRO-056.2`, `SystemArchitectureDesignContractTest.kt`)*:
  • Assert that `docs/system/system_architecture.md` contains valid Mermaid `sequenceDiagram` fences for live 1-Hz telemetry acquisition and post-workout cloud export/backup.
  • Assert that Diagram 4.2 accurately models workout stop from `ControlTrackingScreen` / notification action delegating session finalization and export triggering to `TrackerService` (`mDbExecutor`).
  3. *100% Bidirectional System Requirements Allocation Verification (`TST-PRO-056.3`, `SystemArchitectureDesignContractTest.kt`)*:
  • Assert that the System Requirements Allocation Table allocates 100% of defined `SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006` requirements to system elements.
  • Assert exact bidirectional set equality (`declaredSysreqs == allocatedSysreqs`) ensuring zero orphan or undeclared `SYSREQ-*` IDs exist.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-056.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest"` asserting 100% pass rate.
  5. *Dynamic Production Code Grounding Reflection Verification (`TST-PRO-056.5`, `SystemArchitectureDesignContractTest.kt`)*:
  • Verify existence of referenced production classes via dynamic reflection (`Class.forName`): `RadarEdgeOverlayKt`, `RadarAlertManager`, `RadarAudioSynthesizer`, `NavigationAudioAlertManager`, `BikeBellAudioSynthesizer`, `SpeedAndLocationDevice_GoogleFused`.
  • Verify existence of referenced XML resource files via `File.exists()`: `app/src/main/res/xml/full_backup_content.xml`.
  • Verify referenced application process ID matches `com.atrainingtracker`.


### TST-TST-004: **Android Emulator Lifecycle Management, Physical-Phone Guardrail, and Two-Tier Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TST-004`
- **Component**: `tools/agent_emulator.py`, `tools/fake_gps.py`, `tools/simulate_kill.py`, `tools/replay_workout.py`, `tools/test_agent_emulator.py`
- **Jira**: `ATT-6506`
- **Test Description**:
  1. *ADB Device Table Parsing & Filtering Tests (`TST-TST-004.1`, tools/test_agent_emulator.py)*:
  • Assert parsing of `adb devices -l` output correctly partitions devices into emulators (`emulator-*`) and physical USB devices.
  • Assert multi-device resolution prioritizes `emulator-5554` when present.
  2. *Physical-Phone Protection & Agent Mode Guardrail Tests (`TST-TST-004.2`, tools/test_agent_emulator.py)*:
  • Assert `resolve_target_device` aborts with `RuntimeError` or `SystemExit` if targeting a physical USB serial when `--agent` or `ATT_AGENT_MODE=1` is set.
  • Assert explicit `--serial <SERIAL>` overrides default behavior when valid.
  3. *Emulator Lifecycle & Configuration Command Generation Tests (`TST-TST-004.3`, tools/test_agent_emulator.py)*:
  • Assert `ensure` constructs proper CLI arguments for `~/Android/Sdk/emulator/emulator -avd <AVD_NAME>`.
  • Assert boot verification queries `getprop sys.boot_completed`.
  • Assert window/transition/animator scale zeroing and keyguard dismiss commands.
  • Assert `configure-app` generates correct `pm grant` and `appops set` commands for `com.atrainingtracker.debug`.
  4. *Database Seeding & Extraction Pipeline Tests (`TST-TST-004.4`, tools/test_agent_emulator.py)*:
  • Assert `seed-db` and `sync-from-physical` construct valid `run-as com.atrainingtracker.debug` SQLite extraction and push commands.
  5. *Governance & Skills Two-Tier Policy Parity Tests (`TST-TST-004.5`, tools/test_agent_emulator.py)*:
  • Assert `.agents/rules/aspice_governance.md`, `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md`, and `docs/project_protocol.md` mandate `emulator-5554` for Stages 1–5 and physical device for Ceremony 2.
  6. *Python Standard Library Compliance Test (`TST-TST-004.6`, tools/test_agent_emulator.py)*:
  • Assert `tools/agent_emulator.py` imports only standard library modules complying with `TST-PRO-005.6`.





