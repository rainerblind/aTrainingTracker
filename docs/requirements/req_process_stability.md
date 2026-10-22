# Living Requirements: Engineering Process, Stability & Architecture

**Domain**: `process_stability`  
**Prefixes**: `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`  
**Scope**: ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools.  
**Total Requirements**: 71

---

### REQ-LIV-001: Detect segments using "Gates" and cross-products [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `LiveSegmentsRepository.kt`
- **Rationale**: Reliable entry/exit detection regardless of jitter.
- **Description**: Detect segments using "Gates" and cross-products.

### REQ-LIV-002: Real-time progress on starred Strava segments [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `LiveSegmentsRepository.kt`
- **Rationale**: Motivate users during live efforts.
- **Description**: Real-time progress on starred Strava segments.

### REQ-LIV-003: Remaining distance/time vs PR [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `LiveSegmentsRepository.kt`
- **Rationale**: Immediate feedback on performance.
- **Description**: Remaining distance/time vs PR.

### REQ-LIV-004: Filter segments by bearing alignment (45°) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `LiveSegmentsRepository.kt`
- **Rationale**: Eliminate false alerts on parallel roads.
- **Description**: Filter segments by bearing alignment (45°).

### REQ-LIV-005: Active and Approaching Live Segments Numeric Distance Prioritization [Verified]
- **Tests**: `TST-LIV-001`
- **Implementation**: `LiveSegmentsRepository.kt`, `TrackingViewModel.kt`
- **Rationale**: The system SHALL prioritize and sort active and approaching Strava Live Segments during live tracking (`TrackingViewModel.kt`) strictly by raw numeric distance values in meters (`Double`), eliminating unit-boundary sorting inversions and erratic active segment flipping caused by lexicographical string comparisons (ATT-3324, refining `REQ-LIV-003`):
1. *Raw Distance Representation in `LiveSegmentData` (`LiveSegmentsRepository.kt`)*:
• The system SHALL define `val remainingDistance_raw: Double = Double.MAX_VALUE` in `LiveSegmentData`.
• The system SHALL define `val distanceToStart_raw: Double = Double.MAX_VALUE` in `LiveSegmentData`.
• Default initialization SHALL be `Double.MAX_VALUE` so that uninitialized or inactive segment states naturally sort to the end.
2. *State-Aware Raw Distance Metric Population (`LiveSegmentsRepository.kt`)*:
• For `LiveSegmentStatus.APPROACHING`: `distanceToStart_raw` SHALL be populated with the athlete's along-track geodesic distance to the virtual start gate (`distanceToStart`), and `remainingDistance_raw` SHALL be populated with `liveSegment.staticData.summary.distance_raw + distanceToStart`.
• For `LiveSegmentStatus.ON_SEGMENT` and `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: `distanceToStart_raw` SHALL evaluate to `0.0`, and `remainingDistance_raw` SHALL be populated with the remaining along-track distance to the virtual finish gate (`remainingDistance`).
• For `LiveSegmentStatus.FINISHED`: `distanceToStart_raw` SHALL evaluate to `0.0`, `remainingDistance_raw` SHALL evaluate to `0.0`, and `segmentOffset_raw` SHALL be populated with the athlete's distance beyond the finish gate (`distanceToEnd`).
• For `LiveSegmentStatus.FAR_FAR_AWAY`: `distanceToStart_raw` and `remainingDistance_raw` SHALL default to `Double.MAX_VALUE`.
3. *Strict Numeric Sorting Comparator in `TrackingViewModel.kt`*:
• In `activeLiveSegments`, secondary tie-breaking SHALL evaluate strictly against raw numeric `Double` metrics:
- For `LiveSegmentStatus.ON_SEGMENT` and `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: tie-break ascending by `it.liveData.remainingDistance_raw`.
- For `LiveSegmentStatus.APPROACHING`: tie-break ascending by `it.liveData.distanceToStart_raw`.
- For any other status: tie-break ascending by `it.liveData.segmentOffset_raw`.
• Formatted strings (`remainingDistance`, `distanceToStart`, `segmentOffset`) MUST NOT be used in the sorting comparator.
4. *System Invariants*:
• Segment progress calculation, gate crossing detection, orthogonal polyline projection (`REQ-GEO-001`), PR pacing comparison, and visual UI formatting via `DistanceFormatter` MUST NOT be altered.
• `LiveSegmentData` formatted string properties SHALL remain intact for Compose UI rendering.
• 100% full-suite unit test pass rate.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-LIV-003` (*Remaining distance/time vs PR*) and `TrackingViewModel.kt:161-175`.
2. *Historical Origin & Commit Trace*: Introduced during early Strava Live Segments implementation when `LiveSegmentData` was designed primarily as a presentation model for Compose UI cards.
3. *Root Reason for Existing Formulation*: In early development, `LiveSegmentData` was structured to bundle formatted strings (`distanceToStart`, `remainingDistance`) directly for UI rendering so composables wouldn't need formatting utilities. When writing `activeLiveSegments.sortedWith`, the author accessed `it.liveData.remainingDistance` for convenience, inadvertently triggering string ASCII comparison.
4. *Preservation of Core Invariants*: Formatted string presentation in UI cards (`SegmentHeader`, `LiveSegmentSheet`) remains 100% intact. `distanceToStart_raw` and `remainingDistance_raw` default to `Double.MAX_VALUE`, ensuring uninitialized segments sort cleanly to the end. Gate crossing, polyline orthogonal projection, PR delta math, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* two active live segments on the same trajectory (Segment A with 995 m remaining formatted as `"995 m"`, and Segment B with 2,400 m remaining formatted as `"2.40 km"`),
• *When* `activeLiveSegments` comparator evaluates priority,
• *Then* Segment A SHALL be prioritized ahead of Segment B (995.0 < 2400.0), with zero UI card flipping.
• *Given* two approaching live segments (Segment A at 950 m to start gate formatted as `"950 m"`, and Segment B at 1,200 m to start gate formatted as `"1.20 km"`),
• *When* `activeLiveSegments` comparator evaluates priority,
• *Then* Segment A SHALL be prioritized ahead of Segment B (950.0 < 1200.0).
• *Given* any `LiveSegmentData` emitted across `APPROACHING`, `ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, and `FINISHED`,
• *When* inspecting `remainingDistance_raw` and `distanceToStart_raw`,
• *Then* both properties contain exact unrounded double-precision meter distances.
• *Given* Segment A in `ON_SEGMENT_CLOSE_TO_FINISH` and Segment B in `ON_SEGMENT`,
• *When* `activeLiveSegments` evaluates priority,
• *Then* Segment A SHALL outrank Segment B based on primary status weight (4 > 3).

**Invariants**: Gate crossing geometry, polyline projection, PR comparison, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-PRO-001: Integrate Agile Workflow with Jira Cloud [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `project_protocol.md`, `ATT-103`
- **Rationale**: Ensure bidirectional traceability between tasks and code.
- **Description**: Integrate Agile Workflow with Jira Cloud.

### REQ-PRO-002: App Responsiveness [Verified]
- **Tests**: `TST-STR-002`
- **Implementation**: `TrackerService.java`, `ExportManager.java`
- **Rationale**: The system SHALL minimize lock contention to ensure the app remains responsive during heavy I/O or sensor load.

### REQ-PRO-006: Thread-Safe Sensor Management [Verified]
- **Tests**: `TST-STR-011`
- **Implementation**: `MySensorManager.java`, `MyDevice.java`
- **Rationale**: The sensor management system SHALL be thread-safe to prevent race conditions during registration and unregistration of hardware sensors across multiple threads.

### REQ-PRO-007: Traceable Test Execution [Verified]
- **Tests**: `TST-STR-012`
- **Implementation**: `jira_util.py`, `project_protocol.md`
- **Rationale**: The system SHALL integrate test verification into the Jira workflow by automatically creating sub-tasks for each test case defined in `docs/tests.md` that is relevant to the current ticket.

### REQ-PRO-010: Traceable Implementation Planning [Verified]
- **Tests**: `TST-PRO-003`
- **Implementation**: `project_protocol.md`
- **Rationale**: The development workflow SHALL include the creation of a dedicated Jira sub-task for every Implementation Plan to ensure high-visibility traceability and progress tracking.

### REQ-PRO-008: Automated Attachment Management [Verified]
- **Tests**: `TST-PRO-001`
- **Implementation**: `jira_util.py`
- **Rationale**: The development workflow SHALL support automated downloading of all attachments from a Jira ticket to a local documentation folder to facilitate analysis.

### REQ-PRO-009: Type-Aware Task Management [Verified]
- **Tests**: `TST-PRO-002`
- **Implementation**: `jira_util.py`
- **Rationale**: The development workflow SHALL provide visibility into Jira issue types (Bug, Task, Story) during listing and inspection to ensure appropriate processing logic.

### REQ-PRO-011: Mandatory Code Documentation [Verified]
- **Tests**: `TST-STR-015`
- **Implementation**: `project_protocol.md`, `WorkoutRepository.kt`
- **Rationale**: To ensure long-term maintainability and architectural transparency, all core system components SHALL utilize comprehensive internal documentation. Every class MUST feature a purpose-driven header, and every method MUST include a functional description detailing its logic and synchronization patterns.

### REQ-PRO-003: Pause Distance Isolation [Verified]
- **Tests**: `TST-UNT-008`, `TST-REG-004`
- **Implementation**: `MyAccumulatorSensor.java`, `ANTBikeSpeedDevice.java`, `BTLEBikeDevice.java`
- **Rationale**: The system SHALL prevent distance accumulation while paused to ensure workout statistics only reflect active movement.

### REQ-PRO-004: Pause Track Isolation [Verified]
- **Tests**: `TST-UI-039`
- **Implementation**: `BANALServiceRepository.kt`
- **Rationale**: The system SHALL prevent track and profile accumulation while paused, ensuring they strictly reflect active periods.

### REQ-PRO-005: Database Schema Integrity [Verified]
- **Tests**: `TST-UNT-011`
- **Implementation**: `TrackingViewsDatabaseManager.java`
- **Rationale**: The database initialization and migration logic must ensure that all required columns (e.g., ShowElevationProfile) exist for both fresh installations and upgrades.
- **Description**: Database Schema Integrity.

### REQ-NFR-001: Deterministic EKF Timing [Backlog]
- **Tests**: `TST-STR-010`
- **Implementation**: TBD
- **Rationale**: The estimation loop SHALL use hardware event timestamps and dynamic Delta-T calculation to ensure mathematical robustness against Android CPU jitter.
- **Description**: Deterministic EKF Timing.

### REQ-NFR-002: Stationary Pinning [Backlog]
- **Tests**: `TST-FUSION-004`
- **Implementation**: TBD
- **Rationale**: The system SHALL detect "Not Moving" events via IMU variance and coordinate clustering to prevent stationary "Drunkard's Walk" artifacts.
- **Description**: Stationary Pinning.

### REQ-PRO-012: Project Protocol Hardening [Verified]
- **Tests**: `TST-STR-017`
- **Implementation**: `project_protocol.md`
- **Rationale**: The system documentation MUST include strict rules for format string safety to prevent runtime crashes caused by invalid placeholders.

### REQ-PRO-013: System Invariant Protection & Impact Traceability [Verified]
- **Tests**: `TST-PRO-005`
- **Implementation**: `project_protocol.md`
- **Rationale**: The development workflow SHALL enforce explicit System Invariants ("What MUST NOT Change") for all requirements, MANDATE a pre-implementation `find_usages` cross-check of all requirements mapped to files slated for modification, and REQUIRE a pre-commit adversarial self-review to prevent regressions.

### REQ-PRO-014: Unified 5-Stage ASPICE Dual-Agent Quality Gates & Automated Jira Workflow [`TST-PRO-006`, `TST-PRO-008`]
- **Tests**: `docs/project_protocol.md`, `.cursorrules`, `tools/jira_util.py`
- **Implementation**: Ensure bidirectional ASPICE traceability, automated Jira lifecycle management, and independent AI quality audits without manual developer overhead.
- **Rationale**: The development workflow SHALL enforce explicit quality review gates embedded in the 5-state sub-task lifecycle (`Zu erledigen` -> `In Bearbeitung` -> `In Überprüfung` -> `Freigabe (Human)` -> `Erledigt`) across five sequential ASPICE stages for all ticket types (Bug, Improvement, Feature):
1. *Stage 1: Analysis (`[Analysis]`)*: Problem domain, user motivation, and side effects for features/improvements; root cause analysis (RCA) for bugs.
2. *Stage 2: Test Spec (`[Test-Spec]`)*: Requirement synchronization in `docs/requirements.md` and test specification in `docs/tests.md`.
3. *Stage 3: Implementation Plan (`[Impl-Plan]`)*: Architectural design, invariant protection, and test coverage mapping in `docs/engineering/plans/`.
4. *Stage 4: Implementation (`[Implementation]`)*: Software construction, self-documenting headers (KDoc/JavaDoc), and 9-language localization parity.
5. *Stage 5: Test (`[Test]`)*: Verification evidence, full clean-room regression testing (`./gradlew testDebugUnitTest`), and requirements/tests synchronization.
The workflow SHALL mandate that:
• *Automated Sub-Task Generation*: Sub-tasks are automatically created by Jira Automation upon the parent ticket entering each stage (agents SHALL NOT create lifecycle sub-tasks manually unless recovering from an untriggered state).
• *Description vs. Comment Discipline*: Agent 1 SHALL set the complete stage deliverable as the sub-task's **Description** and transition it to `In Überprüfung`. Agent 2 SHALL *always* conduct an independent gate audit upon sub-task entering `In Überprüfung`, post the audit report as a Jira comment (prefixed with `[Automated comment by AI Agent]`), and transition the sub-task to `Freigabe (Human)`.
• *Automated Parent Transitions*: Progression between stages SHALL occur autonomously once Agent 2's automated gate audit passes, transitioning the sub-task directly to `Erledigt` (via "freigabe"), whereupon Jira Automation automatically advances the parent ticket to the next stage and spawns the next sub-task. Sub-tasks do NOT require human approval.
• *Mandatory Lösungsversion (Fix Version/s)*: The workflow SHALL mandate that the `Lösungsversion` (*Fix Version/s*) is mandatory for all parent tickets across all issue types. The CLI tooling (`tools/jira_util.py`) SHALL display `Lösungsversion` on `show` and `list` and warn if missing. Agent 1 SHALL verify/set the `Lösungsversion` during Stage 5, and Agent 2 SHALL audit its presence during Gate 5 prior to human release approval and ticket closure.
**Acceptance Criteria**:
• *Given* an assigned Jira ticket (Bug, Improvement, or Feature),
• *When* the ticket enters any lifecycle stage,
• *Then* Jira Automation SHALL automatically create the corresponding sub-task.
• *Given* Agent 1 finishes the stage deliverable and sets it as the sub-task Description,
• *When* the sub-task enters `In Überprüfung`,
• *Then* Agent 2 SHALL automatically audit the deliverable, post the review comment, and transition to `Freigabe (Human)`.
• *Given* Agent 2 audit PASS moving the sub-task to `Erledigt`,
• *When* transitioned,
• *Then* Jira Automation SHALL advance the parent ticket to the next stage and generate the next sub-task.
• *Given* a parent ticket entering Stage 5/6 release verification,
• *When* evaluated,
• *Then* the parent ticket's `Lösungsversion` SHALL be populated with the active release version, verified by Agent 2 in Gate 5.
**Invariants**: Moving parent tickets to `Erledigt` remains an inviolable human-only gate; sub-tasks transition directly to `Erledigt` upon automated Gate audit PASS; direct merges to `develop` without 100% sub-task completion remain strictly forbidden.

### REQ-PRO-015: Agent-Driven Git Branching, Conventional Commits, and Develop Merging Protocol [`TST-PRO-007`]
- **Tests**: `docs/project_protocol.md`, `.cursorrules`
- **Implementation**: Ensure full git traceability, branch isolation, and non-fast-forward merge history without manual developer overhead.
- **Rationale**: The development workflow SHALL mandate agent-driven git lifecycle management for all assigned Jira tickets:
1. *Branch Creation*: Upon starting work on any ticket, the agent SHALL verify or switch to `develop` and automatically create/checkout a dedicated branch adhering to naming standards (`feature/ATT-XXX` or `bugfix/ATT-XXX`).
2. *Conventional Commits*: The agent SHALL stage files (`git add`) and execute git commits (`git commit`) using the Conventional Commits format (`feat(...)`, `fix(...)`, `docs(...)`, etc.) with ticket reference and asterisk `*` bullet points.
3. *Develop Integration*: Upon completion and verification of all sub-tasks and human release approval, the agent SHALL switch to `develop` and merge the ticket branch into `develop` using a non-fast-forward merge (`git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"`).
**Acceptance Criteria**:
• *Given* an assigned Jira ticket (`ATT-XXX`),
• *When* the agent begins development,
• *Then* the agent SHALL create and switch to branch `feature/ATT-XXX` or `bugfix/ATT-XXX` off `develop`.
• *Given* approved changes across implementation and documentation,
• *When* committing work,
• *Then* the agent SHALL execute `git commit` with a Conventional Commit message using `*` bullet points and ticket reference.
• *Given* 100% of sub-tasks in `Erledigt` and human sign-off,
• *When* concluding the ticket,
• *Then* the agent SHALL checkout `develop` and execute `git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"`.
**Invariants**: Direct commits to `master` remain strictly forbidden; unreviewed code MUST NOT be merged into `develop`; sub-tasks complete autonomously via automated audit PASS while parent ticket release approval remains strictly reserved for the human user.

### REQ-PRO-016: Inviolable ASPICE Human Decision Gates & Prevention of Premature Implementation Triggers [`TST-PRO-009`]
- **Tests**: `docs/project_protocol.md`, `.cursorrules`, `tools/jira_util.py`, `.agents/rules/aspice_governance.md`
- **Implementation**: Prevent premature implementation triggers and guarantee absolute compliance with ASPICE Human Decision Gates across all AI tooling environments.
- **Rationale**: The development workflow SHALL strictly enforce human decision gate authority across all ASPICE stages, eliminating premature code execution caused by IDE test harness auto-approvals or synthetic prompts:
1. *Artifact Metadata Policy (`RequestFeedback: false`)*: Whenever an AI agent creates or updates plan, walkthrough, or analysis artifacts in the IDE, the agent SHALL ALWAYS specify `ArtifactMetadata: { RequestFeedback: false, UserFacing: true, ... }`. AI agents are STRICTLY FORBIDDEN from specifying `RequestFeedback: true`. This disables the IDE's automated review policy hooks and suppresses any conflicting "Proceed" buttons in the IDE UI.
2. *Zero Governance Authority on Synthetic Harness Messages*: External IDE messages or automated review policy notices (such as *"The user has automatically approved the artifact through their review policy. Proceed to execution."*) apply SOLELY to local IDE markdown documents and hold ZERO governance authority. They grant NO permission to transition Jira tickets AND grant NO permission to edit, create, or modify any production code or test files. AI agents MUST explicitly discard such prompts and pause execution at the Jira Human Decision Gate.
3. *Living Documentation Primacy*: Implementation plans SHALL be authored directly in version-controlled living documentation at `docs/engineering/plans/ATT-XXX_plan.md` and mirrored into the Jira sub-task Description. Ephemeral IDE scratch artifacts hold no governance authority.
4. *Mandatory Pre-Implementation CLI Gate Verification*: In Stage 4 (Software Construction), before calling ANY file-modifying tool (`write_to_file`, `replace_file_content`, `multi_replace_file_content`) on any source code in `app/src/...`, the agent MUST execute `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>` and verify that the exit code is `0` (`GATE_PASSED: <KEY> is Erledigt`). If the sub-task is in any status other than `Erledigt`, code modification is STRICTLY BLOCKED until Stage 3 is completed and passes Gate 3 audit.
**Acceptance Criteria**:
• *Given* an AI agent generating an implementation plan or walkthrough artifact,
• *When* the artifact tool is invoked,
• *Then* `RequestFeedback` SHALL be set to `false`, preventing IDE review policy auto-approvals.
• *Given* an IDE environment emitting a synthetic auto-approval message,
• *When* received,
• *Then* the agent SHALL discard the message and pause execution, refusing to touch code until Jira status is verified.
• *Given* an agent entering Stage 4,
• *When* preparing to modify production code,
• *Then* the agent SHALL run `python3 tools/jira_util.py check-gate <Subtask>` and confirm exit code 0 before editing any file.
**Invariants**: The 5-stage ASPICE lifecycle, automated Jira sub-task generation, and dual-agent reviews MUST remain fully intact; moving parent tickets to `Erledigt` remains an inviolable human-only gate while sub-tasks complete autonomously via automated Gate audit PASS.

### REQ-PRO-017: Process Hardening: Sub-Task Self-Sufficiency, Transition Sequencing, Discovery-First, and Git Finalization [`TST-PRO-010`]
- **Tests**: `docs/project_protocol.md`, `tools/jira_util.py`, `.cursorrules`
- **Implementation**: Prevent empty review notifications, eliminate duplicate automated sub-tasks, ensure self-contained auditability, and ensure complete git integration upon ticket closure.
- **Rationale**: The development workflow SHALL enforce four operational process safeguards across all ASPICE lifecycle stages:
1. *Sub-Task Self-Sufficiency*: Every lifecycle sub-task MUST stand on its own as an independent audit record. Leaving sub-task Descriptions empty (`None`) or posting stub redirection comments (e.g. "see parent ticket") is strictly forbidden. Agent 1 SHALL write the full stage deliverable directly into the sub-task's Description via `tools/jira_util.py update-desc`. Independent Gate reviews posted by Agent 2 SHALL provide self-contained, concrete audit evidence directly in sub-task comments.
2. *Strict Documentation-Before-Transition Sequencing*: Agents SHALL execute `update-desc` and post the audit `comment` BEFORE executing `move` to transition sub-tasks to `In Überprüfung` or `Freigabe (Human)`. Transitioning a sub-task before its Description is populated is strictly forbidden.
3. *Automated Sub-Task Discovery (No Duplicate Creation)*: Agents SHALL inspect the parent ticket (`tools/jira_util.py show <Parent>`) to discover sub-tasks spawned by Jira Automation. Agents are strictly forbidden from eagerly invoking `create-subtask` unless verified that Jira automation failed to spawn the sub-task.
4. *Mandatory Post-Completion Git Finalization*: Upon 100% sub-task completion and human release approval, the agent SHALL autonomously checkout `develop`, execute a non-fast-forward merge (`git merge --no-ff <branch> -m "Merge branch '<branch>' into develop"`), verify a clean working tree (`git status`), and post an integration comment with the merge commit hash to the parent ticket before concluding.
**Acceptance Criteria**:
• *Given* an active lifecycle sub-task,
• *When* Agent 1 completes the stage deliverable,
• *Then* the complete deliverable SHALL be written to the sub-task Description before any state transition.
• *Given* Agent 2 conducting a gate review,
• *When* auditing the deliverable,
• *Then* the review comment SHALL be posted before transitioning to `Freigabe (Human)`.
• *Given* a parent ticket transitioning into a new ASPICE stage,
• *When* the agent begins work,
• *Then* the agent SHALL query `show <Parent>` to discover the automated sub-task rather than creating a duplicate.
• *Given* an assigned ticket with all sub-tasks in `Erledigt`,
• *When* the ticket is completed,
• *Then* the agent SHALL checkout `develop`, merge with `--no-ff`, verify `git status`, and post the merge commit hash to the parent ticket.
**Invariants**: Moving parent tickets to `Erledigt` remains an inviolable human-only gate; zero unmerged feature branches upon ticket completion.

### REQ-PRO-018: Autonomous Multi-Model Independent Review Agent & Automated Gate Audits [`TST-PRO-011`]
- **Tests**: `tools/review_agent.py`, `docs/project_protocol.md`, `.cursorrules`
- **Implementation**: Provide genuine architectural and cognitive independence for AI quality gate reviews, preventing self-confirmation bias while automating Jira audit posting and stage transitions.
- **Rationale**: The development workflow SHALL provide an autonomous, out-of-process review runner (`tools/review_agent.py`) capable of performing independent ASPICE quality gate audits (Gates 1 through 5) using external LLM providers (Google Gemini REST API via `.env.gemini` and Anthropic Claude Messages API via `.env.claude`):
1. *Architectural Independence & Memory Decoupling*: The review agent SHALL run in an independent Python process with zero shared conversational memory or prompt history with Agent 1, eliminating self-review confirmation bias.
2. *Gate Detection & Context Ingestion*: Given a sub-task key (`ATT-XXX`), the tool SHALL automatically determine the corresponding ASPICE stage and gate from the sub-task summary (`[Analysis]` -> Gate 1, `[Test-Spec]` -> Gate 2, `[Impl-Plan]` -> Gate 3, `[Implementation]` -> Gate 4, `[Test]` -> Gate 5). The tool SHALL gather the sub-task deliverable description, parent issue context, living documentation (`docs/requirements.md`, `docs/tests.md`), and active git diffs (for Stages 3 to 5).
3. *Deterministic Audit Protocol & Jira Wiki Formatting*: The review agent SHALL evaluate the ingested context strictly against the mandatory checklist defined in `docs/project_protocol.md` for that gate, assign a risk rating (`LOW`, `MEDIUM`, `HIGH`), formulate an explicit recommendation (`RECOMMEND PASS`, `CHALLENGED`, or `RECOMMEND REVISION`), format the report using Jira Wiki markup, post the audit report as a Jira comment prefixed with `[Automated comment by AI Agent (External Auditor)]`, and transition the sub-task to `Freigabe (Human)`.
4. *Standard Library Footprint & API Fallback*: The review runner SHALL be implemented exclusively using Python 3 standard library modules (`urllib.request`, `json`, `os`, `sys`, `subprocess`) with zero external pip dependencies. If the primary configured provider fails (e.g. quota or connection error), the tool SHALL support graceful fallback to the secondary provider.
**Acceptance Criteria (Given-When-Then)**:
• *Given* an active lifecycle sub-task in status `In Überprüfung` with deliverable populated in Description,
• *When* `python3 tools/review_agent.py audit <Subtask-Key>` is executed,
• *Then* the tool SHALL fetch context, invoke the external LLM API, post the audit report comment in Jira, and transition the sub-task to `Freigabe (Human)`.
• *Given* an attempt to execute an audit without network access or with invalid credentials,
• *Then* the tool SHALL log a clear diagnostic error and exit with non-zero exit code without leaving Jira tickets in an inconsistent state.
• *Given* the repository environment,
• *When* `python3 tools/review_agent.py` is invoked,
• *Then* it SHALL execute successfully without requiring external pip packages.
**Invariants**: Moving parent tickets to `Erledigt` remains strictly prohibited for the review runner; existing `tools/jira_util.py` functionality and 5-state lifecycle MUST NOT be degraded; API keys MUST NOT be logged or committed.

### REQ-PRO-019: Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling [`TST-PRO-012`]
- **Tests**: `tools/jira_util.py`, `tools/review_agent.py`, `docs/project_protocol.md`, `.cursorrules`
- **Implementation**: Provide reliable, role-specific authentication with atomic fallback and deterministic termination on missing credentials.
- **Rationale**: The development workflow and CLI tooling (`tools/jira_util.py`, `tools/review_agent.py`) SHALL support role-specific Jira user credentials across three distinct agent roles: Agent 1 (Implementer), Agent 2 (Auditor), and Coordinator (Orchestrator):
1. *Role Credentials Configuration*: `.env.jira` SHALL support optional role-specific environment variables (`JIRA_AGENT1_USER`, `JIRA_AGENT1_TOKEN`, `JIRA_AGENT2_USER`, `JIRA_AGENT2_TOKEN`, `JIRA_COORDINATOR_USER`, `JIRA_COORDINATOR_TOKEN`) alongside base default credentials (`JIRA_USER`, `JIRA_TOKEN`).
2. *Atomic Resolution & Pair Fallback*: When resolving credentials for any role, if either the username or the API token for that role is missing, the system MUST NOT mix partial credentials; instead, it SHALL atomically fall back to the base default pair (`JIRA_USER`, `JIRA_TOKEN`) and emit an advisory notice to `sys.stderr`.
3. *Terminal Failure on Missing Credentials*: If neither role-specific nor base default credentials exist (both username and token are unresolvable), the tooling MUST NOT attempt unauthenticated requests; it SHALL print a clean diagnostic error to `sys.stderr` and exit with non-zero exit code (1).
**Acceptance Criteria (Given-When-Then)**:
• *Given* `.env.jira` configured with valid `JIRA_AGENT1_USER` and `JIRA_AGENT1_TOKEN`,
• *When* `jira_util.py` is invoked with role `agent1`,
• *Then* Jira HTTP requests SHALL use Basic Auth with Agent 1's username and token.
• *Given* `.env.jira` where `JIRA_AGENT2_USER` is configured but `JIRA_AGENT2_TOKEN` is unset or empty,
• *When* `jira_util.py` resolves credentials for `agent2`,
• *Then* the system SHALL atomically fall back to `JIRA_USER` and `JIRA_TOKEN`, logging an advisory notice to stderr.
• *Given* `.env.jira` where neither role credentials nor default `JIRA_USER`/`JIRA_TOKEN` are defined,
• *When* any Jira command is invoked,
• *Then* the tool SHALL halt immediately with error code 1 and exit.
**Invariants**: Moving any issue to `Erledigt` remains strictly prohibited across all roles; zero external pip dependencies (Python 3 standard library only); APK runtime code and assets MUST NOT be modified.

### REQ-PRO-020: Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution [`TST-PRO-013`]
- **Tests**: `tools/jira_util.py`, `tools/review_agent.py`, `docs/project_protocol.md`, `.cursorrules`
- **Implementation**: Eliminate role impersonation between agents, enforce independent audit integrity, and formalize comment attribution.
- **Rationale**: The development workflow and tooling SHALL enforce strict role boundaries, anti-spoofing constraints, and distinct comment attribution across agent roles:
1. *Precedence Rule*: The active role SHALL be determined strictly by: `CLI Flag (--as <role>)` > `Environment Variable (JIRA_ACTOR)` > `Script Default` (`agent1` for `jira_util.py`, `agent2` for `review_agent.py`).
2. *Role Locking & Anti-Spoofing*: `tools/review_agent.py` MUST be permanently hardwired to role `agent2`. It SHALL NOT allow overriding the role to `agent1` or `coordinator`, ensuring Agent 1 cannot masquerade as the independent auditor.
3. *Actor Identity Attribution*: Comments posted to Jira SHALL include an actor-specific prefix identifying the posting role:
• Agent 1: `[Automated comment by AI Agent 1 (Implementer)]`
• Agent 2: `[Automated comment by AI Agent 2 (Auditor)]`
• Coordinator: `[Automated comment by AI Coordinator]`
• Default / Unspecified: `[Automated comment by AI Agent]`.
4. *Coordinator Governance Invariants*: The Coordinator persona SHALL be defined in `docs/project_protocol.md` and `.cursorrules` as an administrative/orchestration role. The Coordinator SHALL NOT override an Agent 2 `CHALLENGED` or `RECOMMEND REVISION` audit verdict, SHALL NOT bypass Gates 1 through 5, and MUST NOT transition parent tickets to `Erledigt`. Moving parent tickets to `Erledigt` remains an inviolable human-only gate.
**Acceptance Criteria (Given-When-Then)**:
• *Given* `tools/review_agent.py`,
• *When* executed,
• *Then* it SHALL authenticate and post comments strictly as `agent2` regardless of `JIRA_ACTOR` or CLI flags.
• *Given* `jira_util.py` executed with `--as coordinator`,
• *When* adding a comment,
• *Then* the coordinator credentials and `[Automated comment by AI Coordinator]` prefix SHALL be used.
• *Given* any agent role attempting to execute a transition targeting `Erledigt` or `Freigabe erteilt`,
• *Then* `tools/jira_util.py` SHALL block the transition and exit with error code 1.
**Invariants**: Gate 1-5 ASPICE audit requirements remain mandatory and independent; AI agents remain strictly forbidden from closing parent tickets to `Erledigt`.

### REQ-PRO-021: Jira Credential & Token Secret Masking in Tooling [`TST-PRO-014`]
- **Tests**: `tools/jira_util.py`, `tools/review_agent.py`
- **Implementation**: Prevent credential leakage into public logs, transcripts, or console streams.
- **Rationale**: The development workflow and CLI tooling (`tools/jira_util.py`, `tools/review_agent.py`) SHALL prevent the exposure of sensitive Jira credentials in terminal outputs and logs:
1. *Secret Masking*: The tooling SHALL NOT print `JIRA_TOKEN`, `JIRA_AGENT1_TOKEN`, `JIRA_AGENT2_TOKEN`, `JIRA_COORDINATOR_TOKEN`, or raw Basic Authorization headers to `sys.stdout` or `sys.stderr` under any log level, error condition, or trace output.
2. *Safe Advisory Logging*: When logging fallback notices (e.g. falling back to default credentials), advisory logs SHALL mention user IDs or role names only, never exposing token values.
**Acceptance Criteria (Given-When-Then)**:
• *Given* any execution of `jira_util.py` or `review_agent.py` under valid, invalid, or failing network conditions,
• *When* stdout and stderr are inspected,
• *Then* raw token strings SHALL NOT appear in the console output.
**Invariants**: Tooling MUST remain purely on Python 3 standard library; zero leakage of API secrets in CI or local logs.

### REQ-PRO-022: Requirement Archaeology & Chesterton's Fence Modification Hurdle [`TST-PRO-015`]
- **Tests**: `docs/project_protocol.md`, `.cursorrules`, `tools/review_agent.py`, `tools/verify_requirement_governance.py`
- **Implementation**: Prevent regressions and accidental destruction of hard-won architectural protections by enforcing the Chesterton's Fence principle prior to modifying established requirements.
- **Rationale**: The development workflow and governance tooling SHALL enforce an explicit, auditable archaeological investigation before any existing requirement in `docs/requirements.md` is modified, relaxed, or replaced:
1. *Mandatory Archaeological Investigation*: Prior to proposing or applying changes to an existing requirement, the agent/engineer SHALL inspect the requirement's historical git commits (`git log -S <REQ-ID> docs/requirements.md` with timeout and depth bounds), associated Jira tickets, and architectural rationale to understand why the requirement was originally formulated in its current form (Chesterton's Fence principle).
2. *Machine-Checkable Archaeology Audit Section*: Any deliverable (Stage 1 Analysis, Stage 2 Test Specification, and Pull Request / Implementation description) that modifies an existing requirement MUST include a dedicated section titled `### Requirement Archaeology & Chesterton's Fence Audit` containing four mandatory structured fields:
• `Original Requirement ID & Target`: The exact requirement ID slated for alteration (e.g. `REQ-TRK-002`).
• `Historical Origin & Commit Trace`: Git commit hash(es), ticket key(s), and original defect/motivation.
• `Root Reason for Existing Formulation`: Explicit answer to *Why was this fence built?* (e.g. what bug, crash, regression, or schema constraint originally necessitated this exact phrasing).
• `Preservation of Core Invariants`: Explicit justification of *Why is it safe to modify now?*, proving that original system invariants remain protected or explaining why the historical constraint is obsolete.
3. *Independent Auditor Gate Enforcement (Gate 2)*: In `tools/review_agent.py`, the Gate 2 audit checklist SHALL explicitly inspect git diffs of `docs/requirements.md`. If existing requirement lines are modified (identified via diff hunk parsing), Gate 2 SHALL verify the presence, completeness, and validity of the archaeology section. Gate 2 SHALL issue `CHALLENGED` or `RECOMMEND REVISION` on any deliverable altering existing requirements without this complete archaeological justification.
4. *Dedicated Governance Verification Script (`tools/verify_requirement_governance.py`)*: A standalone verification utility implemented purely in Python 3 standard library SHALL analyze git diffs or deliverable text:
• Differentiates modified vs new requirements: detects whether existing requirement IDs were modified via diff analysis against `develop`.
• Validates section syntax: uses multiline regex parsing to verify that all four mandatory field labels exist with non-empty content.
• Deterministic exit codes: returns exit code `0` on compliance, exit code `1` with schema diagnostics on failure.
5. *Legacy Grandfathering*: Existing unmodified requirements remain valid as-is without requiring retroactive archaeology logs.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a branch with git diff modifications to an existing requirement line in `docs/requirements.md`,
• *When* `python3 tools/verify_requirement_governance.py` or `review_agent.py` Gate 2 evaluates the deliverable containing all 4 non-empty archaeology fields,
• *Then* the validation SHALL succeed with exit code 0.
• *Given* a branch modifying an existing requirement in `docs/requirements.md` where any of the 4 mandatory fields is missing or blank,
• *When* evaluated by `tools/verify_requirement_governance.py` or `review_agent.py` Gate 2,
• *Then* the verification script SHALL return exit code 1 with itemized schema errors, and Agent 2 SHALL issue a `CHALLENGED` or `RECOMMEND REVISION` audit verdict.
• *Given* a deliverable introducing only net-new requirements (new row with a new ID not existing in base),
• *When* evaluated by `tools/verify_requirement_governance.py`,
• *Then* the script SHALL classify the change as net-new and exit with code 0 without demanding an archaeology section.

**Invariants**: Zero external pip dependencies; APK runtime code and assets MUST NOT be modified; human decision gate authority remains inviolable.

### REQ-PRO-023: Android Framework Test Stubbing Helpers & Developer Tooling Consistency [`TST-PRO-016`]
- **Tests**: `app/src/test/java/com/atrainingtracker/testutil/MockCursorFactory.kt`, `tools/jira_util.py`, `docs/project_protocol.md`, `.cursorrules`
- **Implementation**: Eliminate recurring MockK cursor boilerplate in database unit tests and streamline Jira issue lifecycle management.
- **Rationale**: The test infrastructure and developer tooling SHALL eliminate mocking boilerplate for Android SQLite framework types and automate metadata propagation across development workflows:
1. *Centralized JVM Cursor Test Fixture*: The test suite SHALL provide a reusable factory `com.atrainingtracker.testutil.MockCursorFactory` in `app/src/test/` to generate MockK `android.database.Cursor` instances backed by in-memory tabular data maps. The factory SHALL support multi-row traversal (`moveToFirst`, `moveToNext`, `position`, `count`, `isAfterLast`), case-insensitive or exact column index lookups (`getColumnIndex`, `getColumnIndexOrThrow`), nullability assertions (`isNull`), and strongly typed scalar extraction (`getInt`, `getLong`, `getDouble`, `getFloat`, `getString`, `getBlob`).
2. *Jira Tooling Metadata Propagation & Sprint Linking*: The CLI utility `tools/jira_util.py` SHALL support automatic inheritance and assignment of active sprint and version context on issue and sub-task creation:
• `create-subtask` SHALL accept an optional `--add-to-sprint` flag or automatically query active sprint status to associate newly created sub-tasks with the current sprint.
• `create-issue` SHALL accept `--fixversion` and `--add-to-sprint` options to eliminate manual two-step linking workflows.
3. *Developer Environment & Sandbox Boundaries Documentation*: `docs/project_protocol.md` and `.cursorrules` SHALL explicitly document filesystem boundaries for external Gradle Daemons (`~/.gradle`, `~/.android`) and network access requirements for external REST APIs, providing clear guidelines for when sandboxed commands require elevated user permissions.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a JVM unit test requiring a multi-row SQLite cursor with columns `["id", "name", "val"]`,
• *When* `MockCursorFactory.create(columns, rows)` is invoked,
• *Then* the returned `Cursor` SHALL correctly return column indices, navigate rows, return accurate types, and report `isNull` accurately without throwing `Method ... not mocked` or requiring custom `every { ... }` blocks.
• *Given* `tools/jira_util.py create-issue` or `create-subtask` invoked with `--add-to-sprint`,
• *When* executed with active sprint context,
• *Then* the newly created issue/sub-task SHALL be added to the active board sprint in Jira automatically.

**Invariants**: Zero APK runtime bytecode or asset modifications (100% test-only code and developer tooling); existing test suites continue to pass with zero regressions; pure Python 3 standard library for all tooling scripts.

### REQ-PRO-024: Modular Skill-Based Agile Architecture & Role-Specialized On-Demand Workflows [`TST-PRO-017`]
- **Tests**: `.agents/skills/`, `docs/project_protocol.md`, `.cursorrules`, `tools/jira_util.py`
- **Implementation**: Eliminate context window inflation and instruction drift, provide structured templates, enable rapid UI and backlog workflows, streamline subtask transitions, and formalize the 3-phase agile sprint lifecycle.
- **Rationale**: The development workflow and agent orchestration SHALL transition from a monolithic protocol document into an on-demand, modular skill-based architecture under `.agents/skills/`, complemented by role-specialized agents:
1. *Modular Skill Layout*: The workflow SHALL encapsulate procedural knowledge, quality gates, and domain practices into 9 discrete skills located under `.agents/skills/<skill_name>/` (`stage1-analysis`, `stage2-req-test-spec`, `stage3-impl-plan`, `stage4-implementation`, `stage5-verification`, `jira-workflow`, `ui-designer`, `brainstormer`, `sprint-planner`). Each skill directory SHALL contain a valid `SKILL.md` file with standardized YAML frontmatter (`name`, `description`) conforming to the Antigravity skill specification.
2. *Standardized Deliverable Templates*: Each stage skill SHALL package dedicated, directly usable markdown templates under its `templates/` directory (`templates/analysis_template.md`, `templates/test_spec_template.md`, `templates/plan_template.md`, `templates/walkthrough_template.md`, `templates/backlog_ticket_template.md`), eliminating prose-based template ambiguity.
3. *Role-Specialized Rapid Feedback Workflows*:
• `ui-designer`: Enables fast exploratory UI iteration utilizing Jetpack Compose `@Preview` composables, instant visual diffs, and immediate human visual verification loops without full APK compilation cycles or heavy stage ceremony.
• `brainstormer`: Enables Socratic discovery, problem definition, and automated backlog ticket formulation (`tools/jira_util.py create-issue`), allowing feature ideation and backlog grooming without interrupting active sprint tickets.
• `sprint-planner`: Facilitates the 3-phase Sprint lifecycle: conducts interactive ticket-by-ticket screening with the user at Sprint-Start (clarifying specifications and moving clear tickets to `Analysis`), establishes the Strategy A sprint integration branch (`sprint/<sprint_id>`), governs continuous in-sprint branch merges upon Stage 5 completion, and facilitates collaborative Sprint-End Review (evaluating tickets on the integrated sprint build, merging accepted tickets, or bouncing back to `Analysis`).
4. *Streamlined Subtask Lifecycle & Mandatory Parent Ticket Final Human Review*:
• *Subtasks*: Subtasks no longer require an intermediate Human review pause; transition `Freigabe` moves subtasks directly from `In Überprüfung` to `Erledigt` upon passing Agent 2 Gate audit.
• *Parent Tickets*: Standard Jira tickets (Bug, Feature, Improvement) mandate an explicit `Final Review (Human)` state following Stage 5 (`Test`). The AI agent transitions parent tickets to `Final Review (Human)` and assigns them to the human user upon concluding verification. The terminal transition from `Final Review (Human)` to `Erledigt` remains an inviolable Human Decision Gate reserved exclusively for the human user; AI agents remain strictly forbidden from transitioning parent tickets to `Erledigt`.
5. *Living Protocol Streamlining*: `docs/project_protocol.md` and `.cursorrules` SHALL be refactored into concise architectural indices, maintaining high-level process invariants while referencing `.agents/skills/` for execution specifics.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the `.agents/skills/` directory,
• *When* validated,
• *Then* all 9 skills SHALL exist and each contain a valid `SKILL.md` with YAML frontmatter `name` and `description`.
• *Given* an active subtask in `In Überprüfung` that has passed Gate audit,
• *When* `python3 tools/jira_util.py move <SUBTASK_KEY> freigabe` is executed,
• *Then* the subtask SHALL transition directly to `Erledigt`.
• *Given* an active parent ticket concluding Stage 5 verification in `Test`,
• *When* `python3 tools/jira_util.py move <KEY> "final review"` is executed,
• *Then* the parent ticket SHALL transition to `Final Review (Human)` and automatically assign to `human`.
• *Given* an AI agent attempting to transition a parent ticket to `Erledigt`,
• *When* `tools/jira_util.py` executes,
• *Then* the tool SHALL block the action and exit with error code 1.
• *Given* an active sprint start,
• *When* the `sprint-planner` skill is invoked,
• *Then* the agent SHALL walk through tickets in `Zu erledigen` with the user, resolve ambiguities, update descriptions, establish `sprint/<sprint_id>`, and transition clear tickets to `Analysis`.

**Invariants**: Zero APK runtime bytecode or asset modifications (100% process, documentation, and skill configuration); human decision gate on parent tickets remains inviolable; Gate 1 through Gate 5 quality checks remain mandatory.

### REQ-PRO-025: Automated Test Execution Acceleration, Worker Parallelism, and Resource-Safe Caching [`TST-PRO-018`]
- **Tests**: `app/build.gradle`, `gradle.properties`, `LocalizationTestCache.kt`
- **Implementation**: Dramatically reduce full test suite execution time from 7-9 minutes to under 3.5 minutes while bounding memory consumption, preventing MockK/ByteBuddy GC thrashing, and ensuring complete test isolation.
- **Rationale**: The build and test execution system SHALL accelerate automated unit test suite execution while guaranteeing host stability, bounded resource consumption, and zero cross-test state pollution:
1. *Multi-Worker Parallelism (`app/build.gradle`)*:
• The build system SHALL configure `testOptions.unitTests.all` with dynamic hardware-adaptive worker forking: `maxParallelForks = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)`.
• On an 8-core CPU system, exactly 4 parallel test worker processes (`GradleWorkerMain`) SHALL execute test classes concurrently.
2. *Periodic Worker Recycling (`forkEvery`)*:
• The build system SHALL configure `forkEvery = 80`. Each test worker process SHALL be terminated and replaced with a fresh JVM after executing 80 test classes.
• Periodic recycling SHALL completely flush the JVM Metaspace and heap, permanently eliminating MockK/ByteBuddy classloader retention and stop-the-world Full GC pauses.
3. *Defensive Heap Allocation Capping (`minHeapSize` / `maxHeapSize`)*:
• To prevent host OOM or swap thrashing, each worker fork SHALL be constrained to `minHeapSize = "256m"` and `maxHeapSize = "768m"`.
• Total concurrent heap allocation across all 4 worker forks SHALL be strictly capped at $\le 3.072\text{ GB}$, leaving over 75% of physical memory available for the host OS and Gradle daemon.
4. *Optimized JIT Compiler Flags (`jvmArgs`)*:
• Test worker JVMs SHALL configure `-XX:+TieredCompilation`, `-XX:TieredStopAtLevel=1` (prioritizing fast C1 JIT compilation over heavyweight C2 optimizations, optimal for short-lived unit test forks), and `-Dfile.encoding=UTF-8`.
5. *Decoupled Task Execution & Local Build Caching (`gradle.properties`)*:
• `gradle.properties` SHALL declare `org.gradle.parallel=true` (enabling parallel execution of decoupled project tasks) and `org.gradle.caching=true` (enabling Gradle's local build cache for deterministically cacheable task outputs).
6. *Thread-Safe In-Memory Localization DOM Cache (`LocalizationTestCache.kt`)*:
• The test infrastructure SHALL provide a shared, thread-safe in-memory cache helper `LocalizationTestCache` backed by `ConcurrentHashMap<String, Map<String, String>>`.
• Strings for each locale SHALL be parsed from disk at most once per worker JVM via atomic `computeIfAbsent`, returning unmodifiable string maps (`Collections.unmodifiableMap`) to guarantee zero cross-test mutation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-PRO-025`), establishing formal build and test infrastructure standards under Process Epic `ATT-232` (*Process & Engineering Workflow*).
2. *Historical Origin & Commit Trace*: `gradle.properties` historically had `# org.gradle.parallel=true` commented out due to early Gradle 2.x/3.x incubator instability. In modern Gradle and AGP, parallel task execution and the build cache are mature production-grade features. `testOptions` was never configured in `app/build.gradle`, defaulting to legacy single-worker execution.
3. *Root Reason for Existing Formulation*: Single-worker sequential execution was the zero-configuration default. As the test suite expanded from ~50 classes to 436 classes, cumulative execution time scaled linearly to 7–9 minutes.
4. *Preservation of Core Invariants*: Process-Level Test Isolation: Gradle's `maxParallelForks` creates separate operating system JVM processes (`GradleWorkerMain`), guaranteeing that static fields, global mocks (`mockkStatic`), and thread-locals remain 100% isolated between workers. In-memory Room/SQLite instances are worker-local. Zero Flakiness: All 436 test classes MUST pass cleanly with 100% determinism.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an 8-core CPU workstation,
• *When* `./gradlew testDebugUnitTest` is executed,
• *Then* Gradle SHALL spawn 4 parallel test worker processes (`maxParallelForks = 4`).
• *Given* the complete 436-class test suite,
• *When* `./gradlew testDebugUnitTest` is executed,
• *Then* total execution duration SHALL decrease by at least 40% compared to baseline single-worker sequential execution (targeting $\le 4\text{ minutes}$).
• *Given* parallel multi-worker test execution,
• *When* running the complete test suite,
• *Then* 100% of test classes and test cases SHALL pass with zero race conditions, flakiness, or test cross-contamination.
• *Given* 4 concurrent test workers running simultaneously,
• *When* measuring worker JVM memory allocations,
• *Then* each worker SHALL NOT exceed 768 MB heap, capping aggregate worker memory at $\le 3.1\text{ GB}$.
• *Given* concurrent access to `LocalizationTestCache`,
• *When* multiple test classes request strings for the same locale,
• *Then* `strings.xml` SHALL be parsed from disk at most once per worker JVM, and returned maps SHALL be strictly immutable.

**Invariants**: Test isolation preserved; zero flaky tests; memory consumption bounded to safe host limits.

### REQ-PRO-026: Document Triad Architecture: Separation of ASPICE Governance, UI Design Guidelines, and Technical Coding Standards [`TST-PRO-019`]
- **Tests**: `.agents/rules/aspice_governance.md`, `docs/design_guidelines.md`, `docs/coding_standards.md`, `docs/project_protocol.md`
- **Implementation**: Decouple process governance from UI design heuristics and software construction standards, establishing a clean Document Triad with importance-tiered governance, consolidated rules, and full retrospective traceability.
- **Rationale**: The development workflow, AI agent governance, and living engineering documentation SHALL decouple process governance from visual design heuristics and software construction standards by establishing a canonical Document Triad (ATT-3323):
1. *ASPICE Process Governance Streamlining (`.agents/rules/aspice_governance.md`)*:
• The process rulebook SHALL strictly govern agile lifecycle stages, quality review gates, Jira transitions, human decision boundaries, and sprint ceremonies.
• Remaining process rules SHALL be restructured into four descending importance tiers:
- *Tier 1: Core Operating Invariants & Human Boundaries* (Top of file): Rule 1 (*Obey the Rules & Mandatory Triad Refresh*), Rule 2 (*Autonomous Full-Sprint Execution*), Rule 3 (*Human Decision Gates & Terminal States*), Rule 4 (*Gate 3 Pre-Check Before Production Code*), Rule 5 (*Strict Separation of Ticket Creation vs. Implementation*), Rule 6 (*Autonomous In-Sprint Escalation Protocol*).
- *Tier 2: Sprint Ceremonies & Scope Governance*: Rule 7 (*Strict Sprint Scope & Backlog Rank Authority*), Rule 8 (*Zero Code Changes During Sprint Review*), Rule 9 (*Lean Defect Recording with Immediate Evidence Capture* — consolidating legacy Rules 17 & 25), Rule 10 (*Mandatory Device Deployment Before Review*), Rule 11 (*Retrospective & Governance Documentation Before Merge*).
- *Tier 3: Jira Standards & Versioning*: Rule 12 (*Standardized Sub-Task Naming Scheme*), Rule 13 (*Clean Ticket Summaries*), Rule 14 (*Fix Version Governance* — consolidating legacy Rules 7 & 19).
- *Tier 4: Specialized Workflows*: Rule 15 (*Formal GitFlow Hotfix & Dual-Branch Protocol* — consolidating legacy Rules 26 & 27), Rule 16 (*Human Prerequisites & Deferred Field-Testing* — consolidating legacy Rules 22 & 28).
• Obsolete rules SHALL be permanently pruned: legacy Rule 3 (*Artifact Metadata RequestFeedback: false* workaround for retired harness) and legacy Rule 24 (*signingReport avoidance*).
• A permanent Historical Mapping Cross-Reference Table SHALL be appended to `.agents/rules/aspice_governance.md`, mapping legacy rule numbers (Rule 1 through 36) to their new rule number or document destination for retrospective traceability.
2. *UI Style Guide & Interaction Guidelines Consolidation (`docs/design_guidelines.md`)*:
• All visual, aesthetic, and layout rules SHALL be housed authoritatively in `docs/design_guidelines.md`, consolidating: Rule 23 (*UI Design Consistency & Reference Screen Matching* in Section 5), Rule 31 (*Explanatory Scale Direction in Settings Sliders* in Section 3.1), Rule 32 (*Coordinate Space Normalization for Sub-Entity Inspection Charts* in Section 2.1), Rule 33 (*Unified Viewport Anchor Authority for Map Tracking* in Section 6.1), and Jetpack Compose multi-state `@Preview` loops and 9-language localization formatting.
3. *Technical Architecture & Coding Standards (`docs/coding_standards.md`)*:
• The repository SHALL establish `docs/coding_standards.md` as the authoritative living document for SWE.1/SWE.2 software construction standards, architectural invariants, and test framework rules:
- *Rule 36: Single Source of Truth & DRY (Don't Repeat Yourself) — Shared Code Over Duplication*: Mandatory reuse of shared utilities and composables; canonical formatters (`DistanceFormatter`, `AltitudeFormatter`, `SpeedFormatter`, `PaceFormatter`, Compose `LocalMetricFormatter`) and unit math (`MyHelper.mps2userUnit()`, `UserUnit2mps()`) MUST be used for all metric presentation (inline string formatting like `String.format("%.1f km", ...)` and manual unit conversions are strictly forbidden); proactive elimination of divergent logic.
- *Rule 20: Database & DTO Mapping Symmetry*: Symmetric single-item and batch `fromCursor` mappings.
- *Rule 21: Specific Direct Platform Intents Over Generic App Settings*.
- *Rule 29: Defensive Service Lifecycle Finalization & FGS Permission Gating*: Permission-gated FGS types and unconditional SQLite session finalization within guaranteed `try/finally` blocks.
- *Rule 30: Developer Tool Host Pre-Flight Diagnostics*.
- *Rule 34: Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests*.
- *Rule 35: Clean Hardware Teardown and Orphan Defense on Persistence Deletion*.
- *Test Framework Invariants*: Strict prohibition of `returnDefaultValues = true`; phased execution distinguishing Stage 4 unit tests (5–15s) from Stage 5 clean-room regression (`./gradlew testDebugUnitTest`).
4. *Retrospective Routing Governance for Future Retros (Rule 11)*:
• Future retrospective improvements SHALL be routed systematically according to domain: process/gate/ceremony learnings to `.agents/rules/aspice_governance.md`, visual/UI/theme learnings to `docs/design_guidelines.md`, and technical/coding/architectural learnings to `docs/coding_standards.md`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-PRO-026`), establishing formal Document Triad architecture across `.agents/rules/aspice_governance.md`, `docs/design_guidelines.md`, and `docs/coding_standards.md`.
2. *Historical Origin & Commit Trace*: Sprint 2026-39.1 (`ATT-728`) established `.agents/rules/aspice_governance.md`. Sprints 2026-40.1 through 2026-42.1 appended retrospective rules linearly, resulting in 36 rules across 28 KB.
3. *Root Reason for Existing Formulation*: Appending rules directly to `aspice_governance.md` was rapid during retrospectives but caused cognitive clutter, duplication, and domain entangling.
4. *Preservation of Core Invariants*: Inviolable Human Decision Gates on parent tickets, autonomous full-sprint execution, Gate 3 programmatic pre-check (`check-gate`), historical traceability, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an AI agent beginning a ticket and performing Rule 1 refresh,
• *When* inspecting `.agents/rules/aspice_governance.md`,
• *Then* the file SHALL be organized into 4 importance tiers with the most critical operating invariants (Rules 1–6) at the top of the file.
• *Given* the revised governance rulebook,
• *When* searching for legacy duplicate rules (17+25, 7+19, 26+27, 22+28),
• *Then* each pair SHALL be unified into a single coherent rule, and obsolete rules (3, 24) SHALL be absent.
• *Given* an engineer looking up a legacy rule number from an earlier retro (e.g. "Rule 15", "Rule 23", "Rule 36"),
• *When* consulting the Historical Mapping Cross-Reference Table in `aspice_governance.md`,
• *Then* the table SHALL uniquely map every legacy rule 1–36 to its new rule number or target document.
• *Given* the repository documentation,
• *When* inspecting `docs/coding_standards.md`,
• *Then* it SHALL contain Rule 36 DRY invariants (mandatory formatter and unit math reuse), DTO mapping symmetry, direct platform intents, FGS permission gating, and test framework rules.
• *Given* the repository documentation,
• *When* inspecting `docs/design_guidelines.md`,
• *Then* it SHALL contain UI visual consistency baseline (Rule 23), slider scale hints (Rule 31), coordinate normalization (Rule 32), map viewport authority (Rule 33), and Compose `@Preview` / 9-language localization formatting.

**Invariants**: Human Decision Gate for parent tickets remains inviolable; 100% clean-room test pass rate; zero regression in governance enforcement scripts.

### REQ-STB-001: Resilient WorkManager Initialization & Startup Protection [`TST-STB-001`]
- **Tests**: `AndroidManifest.xml`, `TrainingApplication.java`, `ExportManager.java`, `BackupWorker.kt`
- **Implementation**: Prevent fatal startup crashes on fragmented Android 14 environments while preserving background task functionality on compliant devices.
- **Rationale**: The system SHALL prevent application startup crashes caused by platform-level `JobScheduler` incompatibilities (such as missing `JobScheduler.forNamespace` on non-standard Android 14 builds):
1. *Decoupled Auto-Initialization*: The system SHALL remove automatic execution of `WorkManagerInitializer` from `androidx.startup.InitializationProvider` via manifest configuration (`tools:node="remove"`).
2. *Controlled Resilient Startup*: The system SHALL manually initialize `WorkManager` within `TrainingApplication.onCreate()` guarded by a `try ... catch (Throwable)` block, ensuring application launch succeeds unconditionally even if framework scheduler creation fails.
3. *Caller Resilience*: All downstream callers of `WorkManager.getInstance()` SHALL handle uninitialized or failing WorkManager state gracefully without throwing unhandled exceptions.
**Acceptance Criteria**:
• *Given* an Android environment reporting API 34+ where `JobScheduler.forNamespace` is absent or throws `NoSuchMethodError`,
• *When* the application is launched,
• *Then* the application SHALL start cleanly without crashing, logging the initialization failure.
• *Given* a standard compliant Android device,
• *When* the application is launched,
• *Then* `WorkManager` SHALL be successfully initialized, and background workers (export, backup) SHALL execute normally.
**Invariants**: Other `androidx.startup` components MUST NOT be disabled; background export and backup execution on standard devices MUST NOT be degraded.

### REQ-STB-002: Resilient TrackerService Foreground Service Lifecycle & Crash Protection [`TrackerService.java`, `AndroidManifest.xml`]
- **Tests**: Ensure workout tracking resilience via START_STICKY while preventing fatal SecurityException crashes on Android 14+ when restarted in the background without background location permissions.
- **Implementation**: IllegalStateException)` block, safely logging any unexpected platform launch restriction exceptions and calling `stopSelf()` rather than crashing the application process.
**Acceptance Criteria**:
• *Given* an active workout on Android 14+ where the user has granted `ACCESS_BACKGROUND_LOCATION`,
• *When* the OS kills `TrackerService` and recreates it with `intent == null`,
• *Then* `TrackerService` SHALL successfully call `startForeground` with location type and continue tracking.
• *Given* an active workout on Android 14+ where the user has ONLY granted foreground location,
• *When* the OS kills `TrackerService` and recreates it with `intent == null`,
• *Then* `TrackerService` SHALL NOT crash with `SecurityException`, but SHALL safely stop itself, preserve workout data, and notify the user.
• *Given* any unexpected `SecurityException` during `startForeground`,
• *When* `startForeground` is executed,
• *Then* the exception SHALL be caught, logged, and handled cleanly without process death.
**Invariants**: `START_STICKY` MUST be retained; normal foreground workout tracking initiated by the user MUST continue to operate with location foreground service notification intact.
- **Rationale**: The system SHALL ensure the workout tracking service (`TrackerService`) operates with resilient lifecycle management and crash immunity across Android 14+ (API 34+) background launch restrictions:
1. *START_STICKY Preservation*: The system SHALL retain `Service.START_STICKY` as the return value of `TrackerService.onStartCommand` to allow automatic background recreation and continuous tracking when the process is killed during active workouts.
2. *Background Restart Precondition Guard*: When `onStartCommand` is invoked with `intent == null` (system recreation after kill):
• *If `ACCESS_BACKGROUND_LOCATION` is granted*: The service SHALL proceed to promote to a location foreground service (`FOREGROUND_SERVICE_TYPE_LOCATION`) and continue tracking.
• *If `ACCESS_BACKGROUND_LOCATION` is NOT granted*: The service SHALL NOT attempt an illegal background promotion to location FGS; instead, it SHALL safely preserve workout metrics to SQLite, post a user-facing notification informing the user that tracking was paused, and call `stopSelf()`.
3. *Exception Shielding*: All invocations of `startForeground(...)` in `TrackerService` SHALL be encapsulated in a `try ... catch (SecurityException

### REQ-STB-003: Interrupted Workout Resumption & Unfinished Workout Recovery [`TST-STB-003`]
- **Tests**: `TrackerService.java`, `MainActivityWithNavigation.java`, `ControlTrackingViewModel.kt`, `WorkoutSummariesDatabaseManager.java`, `TrainingApplication.java`
- **Implementation**: Ensure interrupted workouts can be seamlessly resumed from notifications or recovered on app launch without data loss or crashes.
- **Rationale**: The system SHALL allow resuming ongoing workouts interrupted by system termination or permission restrictions:
1. *Interrupted Notification Intent*: The notification posted by `TrackerService.showTrackingInterruptedNotification()` SHALL include an explicit action/extra (`EXTRA_RESUME_INTERRUPTED_WORKOUT = true`, and destination `SELECTED_FRAGMENT = START_OR_TRACKING`) in its `PendingIntent`.
2. *Automatic Foreground Resumption*: When the user taps the interrupted tracking notification, `MainActivityWithNavigation.handleIntent()` SHALL detect `EXTRA_RESUME_INTERRUPTED_WORKOUT`, set `TrainingApplication.setResumeFromCrash(true)`, dismiss the interrupted notification, and immediately resume tracking via `TrackerService.StartType.RESUME_BY_USER`, ensuring Android 14+ while-in-use foreground location permissions succeed without `SecurityException` and preserving all previous metrics.
3. *Unfinished Workout Detection & UI Restoration*: When `MainActivityWithNavigation` initializes while an unfinalized workout exists in SQLite (`FINISHED == 0`), the system SHALL detect the unfinished workout state, prevent silent loss/overwrite of the workout data, and present `StartOrResumeDialog` offering the user the choice to resume the workout or start fresh.
**Acceptance Criteria**:
• *Given* an active workout that was interrupted and displays the "Aufzeichnung unterbrochen" notification,
• *When* the user taps the notification,
• *Then* `MainActivityWithNavigation` SHALL launch into the foreground and immediately resume tracking with all prior metrics, sensors, and elapsed time intact.
• *Given* an unfinalized workout record in SQLite (`FINISHED == 0`),
• *When* the user launches the application normally,
• *Then* the application SHALL prompt the user with `StartOrResumeDialog` to resume or start fresh rather than silently defaulting to an empty ready state.
**Invariants**: Normal workout start (`START_NORMAL`) MUST operate unchanged; `START_STICKY` lifecycle and exception shielding in `TrackerService` MUST NOT be altered; no data loss for interrupted workout sessions.

### REQ-STB-004: Resilient Location Provider Initialization & Device Hardware Fault Tolerance [`SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_Network.java`, `DeviceManager.java`, `MainActivityWithNavigation.java`]
- **Tests**: Prevent fatal service creation crashes on devices without GPS hardware or providers while preserving location tracking on standard hardware.
- **Implementation**: SecurityException)` block. If the provider is unavailable or registration fails, the device SHALL call `LocationUnavailable()`, log a warning, and proceed cleanly without throwing runtime exceptions.
2. *Safe Provider Verification & Registration in SpeedAndLocationDevice_Network*: The system SHALL enforce identical defensive provider existence checks and exception shielding for `LocationManager.NETWORK_PROVIDER` in `SpeedAndLocationDevice_Network`.
3. *DeviceManager Instantiation Isolation*: In `DeviceManager.<init>` and `pairingChanged()`, the system SHALL verify provider availability before instantiating GPS and Network location devices, and encapsulate sensor creation in localized try-catch blocks to prevent any single hardware sensor failure from aborting `BANALService.onCreate()`.
4. *Safe Provider Queries in Navigation UI*: `MainActivityWithNavigation` SHALL verify that `LocationManager.GPS_PROVIDER` exists before calling `isProviderEnabled()`, preventing false "GPS disabled" alerts on GPS-less hardware.
**Acceptance Criteria**:
• *Given* an Android device or emulator where `LocationManager.GPS_PROVIDER` does not exist or throws `IllegalArgumentException: provider "gps" does not exist`,
• *When* `BANALService` is created and `DeviceManager` initializes,
• *Then* `BANALService` SHALL initialize successfully without crashing, and other paired sensors (ANT+, BLE, pressure) SHALL operate normally.
• *Given* a compliant device with GPS hardware,
• *When* `SpeedAndLocationDevice_GPS` is initialized,
• *Then* location updates SHALL be registered and received normally.
**Invariants**: GPS tracking frequency, accuracy, and lifecycle on GPS-capable devices MUST NOT be altered; other device sensors (ANT+, BLE, Google Fused) MUST remain fully operational.
- **Rationale**: The system SHALL prevent application startup crashes caused by missing or unsupported platform location providers (such as missing `gps` or `network` providers on tablets, emulators, or specialized Android ROMs without GPS hardware):
1. *Safe Provider Verification & Registration in SpeedAndLocationDevice_GPS*: Prior to requesting location updates, the system SHALL verify `mLocationManager != null` and `mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null`. All invocations of `LocationManager.requestLocationUpdates()` SHALL be encapsulated in a `try ... catch (IllegalArgumentException

### REQ-STB-005: Equipment Database Boundary Validation & Unassigned Equipment Query Optimization [`TST-STB-005`]
- **Tests**: `EquipmentDbHelper.java`, `WorkoutDataMapper.kt`, `EquipmentDataProvider.kt`, `StravaUploader.kt`
- **Implementation**: Eliminate Logcat error flooding and redundant database contention when loading workout summaries without assigned equipment.
- **Rationale**: The system SHALL enforce strict boundary validation and caller-level short-circuiting for equipment lookups to eliminate Logcat error log flooding and redundant SQLite queries:
1. *Precondition Guards in EquipmentDbHelper*: `EquipmentDbHelper.getEquipmentNameFromId(equipmentId)`, `getStravaIdFromId(equipmentId)`, and `getDeviceIdsForEquipment(equipmentId)` SHALL immediately validate that `equipmentId > 0`. If `equipmentId <= 0` (such as `0` resulting from SQLite cursor mapping of SQL `NULL`, or legacy `-1`), the methods SHALL return `null` (or an empty list) immediately without opening the database, executing SQLite queries, or logging `ERROR` messages.
2. *Caller-Level Short-Circuiting*: Mappers and data providers (`WorkoutDataMapper`, `EquipmentDataProvider`, `StravaUploader`, `EquipmentAndSportTypeDiscoveryManager`, `SportTypeViewModel`) SHALL verify `equipmentId > 0` before querying `EquipmentDbHelper`, avoiding unnecessary object instantiation and helper method calls.
3. *Diagnostic Error Integrity*: For legitimate corrupted positive IDs (`equipmentId > 0` absent from `Equipment.db`), `EquipmentDbHelper` SHALL retain its diagnostic error logging.
**Acceptance Criteria**:
• *Given* workout summaries with unassigned equipment (`EQUIPMENT_ID` is `NULL`, `0`, or `-1`),
• *When* loading or scrolling workout lists, aftermath cards, or period summaries,
• *Then* `EquipmentDbHelper` SHALL NOT emit `ERROR: in getEquipmentFromId: no name for id: 0` or `-1`, and no SQLite queries SHALL be executed for unassigned equipment.
• *Given* a workout with a valid assigned equipment ID (`equipmentId > 0`),
• *When* mapped,
• *Then* the equipment name and Strava gear ID SHALL resolve accurately.
**Invariants**: Valid equipment resolution and Strava upload gear synchronization MUST NOT be altered; positive non-existent IDs MUST continue to be logged as diagnostic errors.

### REQ-STB-006: Navigation Drawer Header Background Resource Availability & Crash Immunity [`TST-STB-006`]
- **Tests**: `menu_header_background.jpg`, `AppNavigationDrawer.kt`
- **Implementation**: Prevent fatal production startup and navigation drawer crashes caused by missing density-specific drawable resources under Android App Bundle density splitting.
- **Rationale**: The system SHALL guarantee that the navigation drawer header background image (`menu_header_background`) is unconditionally accessible across all Android device screen densities, dynamic display scaling settings, and Android App Bundle (.aab) split configurations, and ensure the drawer header UI renders with crash immunity:
1. *Base APK Resource Availability (`drawable-nodpi`)*:
• The system SHALL provide a universal fallback for `menu_header_background.jpg` in `app/src/main/res/drawable-nodpi/` (and/or `app/src/main/res/drawable/`).
• This ensures that when the application is packaged and distributed as an Android App Bundle (.aab), the resource is included directly in the base APK and is NEVER stripped by `bundletool` density splitting, preventing `Resources$NotFoundException` when a device operates on an un-split or non-standard screen density (e.g. `tvdpi`, `ldpi`, or non-standard display scaling).
2. *Defensive Resource Resolution & Fallback Rendering*: In `AppNavigationDrawer.kt`, `DrawerHeader()` SHALL resolve the background painter safely. If `painterResource` throws `Resources.NotFoundException` or any `Throwable` (e.g. due to runtime resource corruption or OS configuration anomalies), the system SHALL catch the error, log a warning (`Log.w(TAG, "Failed to load drawer header background", e)`), and gracefully fall back to a styled background surface (e.g. Material 3 primary container color or gradient) rather than allowing an unhandled exception to crash the application process.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android environment with non-standard or altered display scaling where density split APKs are absent,
• *When* the navigation drawer is opened or `DrawerHeader()` is composed,
• *Then* `menu_header_background` SHALL resolve successfully from the base APK without `Resources$NotFoundException`.
• *Given* an environment where the resource ID is intentionally unresolvable or corrupted,
• *When* `DrawerHeader()` is composed,
• *Then* the Composable SHALL render gracefully with the fallback background and the application SHALL NOT crash.
• *Given* the resource directories,
• *When* inspected,
• *Then* `app/src/main/res/drawable-nodpi/menu_header_background.jpg` SHALL exist.

**Invariants**: Existing branding, dimensions, landscape aspect ratio, `logo_512` branding, and drawer navigation items MUST NOT be degraded.

### REQ-STB-007: AndroidX Core Compatibility & Android 14 (API 34) Insets Crash Immunity [`TST-STB-007`]
- **Tests**: `app/build.gradle`
- **Implementation**: Prevent fatal production crashes (`NoSuchMethodError`) during Compose window attachment on Android 14 devices where framework API methods were omitted in OEM firmware.
- **Rationale**: The system SHALL guarantee that application startup and Jetpack Compose window insets initialization execute with complete crash immunity across all Android 14 (API 34) firmware builds and OEM implementations:
1. *AndroidX Core Runtime Alignment*: The application build configuration SHALL enforce dependency resolution of `androidx.core:core` and `androidx.core:core-ktx` at stable version `1.15.0` (via explicit dependency declaration and Gradle `resolutionStrategy.force`), preventing transitive upgrades to defect-ridden `1.16.0–1.19.0` versions containing unguarded calls to `WindowInsets.Type.systemOverlays()` and `AccessibilityEvent.setAccessibilityDataSensitive()`.
2. *System Insets & Padding Stability*: All Compose screens and dialogs utilizing system insets modifiers (e.g., `safeDrawingPadding()`, `statusBarsPadding()`, `navigationBarsPadding()`, `Scaffold`, `ModalBottomSheet`) SHALL initialize and attach to the window without encountering `java.lang.NoSuchMethodError`.
3. *Traceability & Future Upgrade Gate*: The build configuration MUST document the upstream Google Issue Tracker references (`Issue 558456603` / `Issue 551348854`) and establish that unpinning requires verification of official runtime method presence guards in `androidx.core 1.19.1+` / `1.20.0+` (tracked via `ATT-1091`).
4. *Invariants*: Edge-to-edge rendering, status bar transparency, navigation bar contrast, and existing Material 3 styling MUST remain fully functional without visual degradation across all supported API levels (API 26 to API 37+).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 14 (API 34) runtime environment lacking `WindowInsets.Type.systemOverlays()`,
• *When* the application launches and Compose hierarchies attach to the window,
• *Then* `WindowInsetsCompat` SHALL resolve insets without invoking missing platform methods and the application SHALL NOT encounter `NoSuchMethodError`.
• *Given* the project's dependency graph,
• *When* `./gradlew :app:dependencies` is executed,
• *Then* `androidx.core:core` and `androidx.core:core-ktx` SHALL resolve strictly to `1.15.0`.
• *Given* the test suite,
• *When* automated unit tests are executed,
• *Then* 100% of tests SHALL pass with zero regressions.

### REQ-STB-008: Resilient Permission-Gated Location Provider Access & Startup Crash Immunity [`TST-STB-008`]
- **Tests**: `MainActivityWithNavigation.kt`, `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_Network.java`
- **Implementation**: Prevent fatal startup crashes (`SecurityException`) during application launch when location permissions are ungranted or revoked, and eliminate premature GPS disabled alerts.
- **Rationale**: The system SHALL guarantee that application startup in `MainActivityWithNavigation.onCreate()` and location sensor initialization execute with complete crash immunity across all Android API versions and OEM builds when location permissions are missing or revoked:
1. *Precondition Permission Gating in UI*: In `MainActivityWithNavigation`, `LocationManager.getProvider(LocationManager.GPS_PROVIDER)` and `LocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)` SHALL ONLY be queried when `ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED`. If permission is absent or revoked, the query SHALL be bypassed without throwing exceptions or showing premature "GPS disabled" alerts.
2. *Defensive Exception Shielding*: All invocations of `LocationManager.getProvider()` and `isProviderEnabled()` across `MainActivityWithNavigation.kt`, `SpeedAndLocationDevice_GPS.java`, and `SpeedAndLocationDevice_Network.java` SHALL be enclosed in localized exception handling catching `SecurityException` (IPC binder permission rejections) and `IllegalArgumentException` (invalid/non-existent provider names), logging diagnostics without terminating the application process.
3. *Null-Safety Contracts*: Callers of `getProvider()` SHALL verify the returned `LocationProvider` reference is non-null before invoking subsequent provider operations.
4. *Graceful Fallback & State Preservation*: When permissions are missing or provider queries fail, user preferences (`TrainingApplication.trackLocation()`) SHALL remain uncorrupted, zero background polling loops shall be leaked, and `onRequestPermissionsResult()` SHALL safely re-evaluate GPS provider status once permissions are granted.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an application cold start where `ACCESS_FINE_LOCATION` has NOT been granted or was revoked by the user,
• *When* `MainActivityWithNavigation.onCreate()` executes,
• *Then* `MainActivityWithNavigation` SHALL NOT invoke unguarded `LocationManager.getProvider()` calls and SHALL NOT crash with `SecurityException`.
• *Given* an unpermissioned state,
• *When* `onCreate()` executes,
• *Then* `GPSDisabledDialog` SHALL NOT be displayed to the user.
• *Given* an environment where `LocationManager.getProvider()` throws `SecurityException` or `IllegalArgumentException`,
• *When* `checkGpsEnabledIfPermitted()` executes,
• *Then* the exception SHALL be caught and logged, and the activity lifecycle SHALL continue unimpeded.
• *Given* a user granting `ACCESS_FINE_LOCATION` via the runtime permission dialog,
• *When* `onRequestPermissionsResult()` is received,
• *Then* `checkGpsEnabledIfPermitted()` SHALL evaluate the hardware GPS provider and prompt `GPSDisabledDialog` if and only if the provider is disabled on the device.

**Invariants**: Live location tracking during active workouts with granted permissions MUST NOT be degraded; existing paired sensor discovery (ANT+, BLE, Google Fused) MUST remain fully operational.

### REQ-STB-009: Asynchronous Workout Initialization & Main-Thread ANR Immunity in TrackerService [`TST-STB-009`]
- **Tests**: `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `LiveWorkoutSession.java`
- **Implementation**: Prevent fatal main-thread ANRs during workout startup caused by blocking SQLite DDL operations and disk latency.
- **Rationale**: The system SHALL guarantee that workout tracking startup and SQLite schema initialization execute with complete ANR and crash immunity across all Android devices, large database configurations, and disk I/O latency conditions:
1. *Main-Thread Database Decoupling*: `TrackerService.onStartCommand` SHALL NOT execute synchronous database DDL statements (`CREATE TABLE`, `DROP TABLE`), database opening, or multi-row SQLite inserts on the Android main UI thread.
2. *Asynchronous Executor Dispatch*: During `START_NORMAL` initialization, `createNewWorkout()`, `WorkoutSamplesDatabaseManager.createNewTable()`, and initial `notifyTrackingStarted()` SHALL be dispatched to `TrackerService`'s dedicated single-thread FIFO database executor (`mDbExecutor`).
3. *CompletableFuture Synchronization & Sample Buffering*: `TrackerService` SHALL maintain a synchronization primitive (`CompletableFuture<Void> mTableInitializationFuture`). Any early sensor samples or write tasks submitted prior to table creation completion SHALL await the future on the background executor (up to 5s timeout) rather than being dropped or failing with `SQLiteException: no such table`. Sensor sampling SHALL NEVER commence persistence before table creation commits to disk.
4. *Broadcast Delay until Schema Commitment*: `notifyTrackingStarted(workoutId)` SHALL be dispatched only AFTER `mTableInitializationFuture` completes successfully, ensuring downstream UI observers and broadcast receivers never query an uninitialized table.
5. *Defensive Downstream Error Handling*: If database or table initialization throws `SQLException` or `IllegalStateException`, `mTableInitializationFuture` SHALL complete exceptionally, cancel `mTrackerHandle`, post `showTrackingInterruptedNotification()`, and gracefully invoke `performStopSelf()` rather than swallowing errors or allowing telemetry corruption.
6. *Foreground Service Startup SLA & WakeLock Safety*: `performStartForeground()` in `TrackerService.onStartCommand` SHALL execute immediately on the main thread without awaiting database I/O, satisfying Android 14+ 5-second FGS startup constraints, with CPU WakeLock acquired to ensure uninterrupted background commit.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android environment with slow flash storage or a large `WorkoutSamples.db` containing hundreds of historical tables,
• *When* `TrackerService.onStartCommand` is invoked with `START_NORMAL`,
• *Then* `onStartCommand` SHALL return immediately without blocking on SQLite schema DDL or disk reads (`pread`), eliminating ANR risks.
• *Given* early sensor ticks arriving while table creation is queued on `mDbExecutor`,
• *When* samples are processed for persistence,
• *Then* the background task SHALL await `mTableInitializationFuture` and write all samples without `SQLiteException` or data loss.
• *Given* an environment where table creation throws `SQLException`,
• *When* initialization fails on `mDbExecutor`,
• *Then* `TrackerService` SHALL complete exceptionally, log the error, notify the user, and stop itself gracefully.

**Invariants**: No sensor sample collection or persistence shall commence until the underlying workout table creation transaction is fully committed to disk; workout sampling rate (1s), live session metrics, map points, elevation streams, and Android 14+ FGS location permissions (`REQ-STB-002`) MUST remain fully intact.

### REQ-STB-010: Static Map Lite-Mode Enforcement in Compose Lazy Lists & RenderThread Deadlock Immunity [`TST-STB-010`]
- **Tests**: `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`
- **Implementation**: Prevent fatal native RenderThread deadlocks and ANRs during activity stop/pause transitions by enforcing Lite Mode for list map previews.
- **Rationale**: The system SHALL guarantee that map thumbnails embedded within scrollable Compose lists (`LazyColumn`, `LazyRow`) operate in Google Maps Lite Mode to eliminate native rendering thread resource contention and prevent fatal UI deadlocks:
1. *Lite Mode Enforcement in List Previews*: All static map previews embedded inside scrolling list items (`PathPreviewMap.kt` used in workout and route summaries, and `WorkoutClusterComponents.kt` used in `ClusterCard` and `ClusterWorkoutCard`) SHALL configure `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
2. *Render Pipeline Decoupling*: Map previews in Lite Mode SHALL render as static bitmap snapshots on background worker threads, completely eliminating native `GLSurfaceView`/`TextureView` allocations, continuous HWUI render thread loops, and native `setFrameCompleteCallback` registrations on `RenderThread`.
3. *Deadlock Immunity on Lifecycle Transitions*: When the application is paused, stopped, or backgrounded, `HardwareRenderer.setStopped(true)` (which runs on the main thread inside an ART `@FastNative` method) SHALL execute without contending with active frame callbacks on the `RenderThread`, completely eliminating the circular lock dependency causing `art::ConditionVariable::WaitHoldingLocks` ANRs.
4. *Visual Asset & Interaction Parity*: In Lite Mode, polylines, start/end/apex markers, spatial bounds clamping (`CameraUpdateFactory.newLatLngBounds`), and on-click navigation (`onMapClick`) SHALL remain fully functional and visually identical.
5. *Preservation of Interactive Maps*: Full-screen interactive maps (`ATrainingTrackerMap.kt`, `LapEditBottomSheet.kt`, `ManualClusterScreen.kt`) SHALL retain standard interactive mode.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an application screen displaying a scrollable list of workouts, routes, or route clusters,
• *When* the user scrolls rapidly through the list or backgrounds the app during rendering,
• *Then* `HardwareRenderer.setStopped(true)` SHALL complete immediately on the main thread without deadlocking on `RenderThread`, eliminating Crashlytics issue `1b9b9a1021c548bb3c8e6b665f963fe1`.
• *Given* `PathPreviewMap` and `WorkoutClusterComponents`,
• *When* composed,
• *Then* `GoogleMap` SHALL initialize with `liteMode(true)`, and route polylines and spatial markers SHALL render accurately.

**Invariants**: Interactive maps with gestures enabled (live workout tracking, lap editing, manual cluster creation) MUST retain full vector rendering; route geometry, colors, and bounds padding MUST NOT be degraded.

### REQ-STB-011: Resilient SavedStateHandle Fallbacks & Crash Immunity in DevicesTabbedViewModel [`TST-STB-011`]
- **Tests**: `DevicesTabbedViewModel.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`
- **Implementation**: Prevent fatal `NullPointerException` crashes and indefinite progress stalls when accessing the Sensors screen from Compose navigation.
- **Rationale**: The system SHALL guarantee that `DevicesTabbedViewModel` initializes with complete crash immunity across all navigation paradigms (Compose NavHost, Fragment transactions, or process recreation) when `SavedStateHandle` arguments are missing or malformed:
1. *Defensive Protocol Parsing & Defaulting*: `protocol` SHALL safely parse `savedStateHandle[BANALService.PROTOCOL]`. If null, empty, or unparseable, it SHALL default to `Protocol.ALL` and persist the default to `savedStateHandle`.
2. *Defensive DeviceType Parsing & Defaulting*: In `init`, `deviceType` SHALL safely parse `savedStateHandle[BANALService.DEVICE_TYPE]`. If absent or unparseable, it SHALL default to `DeviceType.ALL`, initialize `_uiState` to `UiState.DisplayingTabs(DeviceType.ALL)`, and persist the default to `savedStateHandle`.
3. *Dynamic Pairing Filter Configuration*: The ViewModel SHALL provide `updateFilters(protocol, deviceType)` to reconfigure protocol and device type filters when pairing sessions are launched dynamically.
4. *Invariants*: BLE/ANT+ discovery and database persistence contracts MUST NOT be degraded.

### REQ-STB-012: Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance [Verified]
- **Tests**: `TST-STB-012`
- **Implementation**: `MainActivityWithNavigation.kt`, `StartOrResumeDialog.kt`, `ProcessExitReasonHelper.kt`
- **Rationale**: The system SHALL diagnose unexpected background tracking process terminations when an unfinalized workout exists in SQLite (`hasUnfinishedWorkout() == true`), evaluate `ApplicationExitInfo` on Android 11+ (API 30+) and `PowerManager.isIgnoringBatteryOptimizations()`, present a modernized, empathetic recovery dialog (`StartOrResumeDialog`) with dynamic rationale titles reflecting the concrete cause (Battery Kill, Low Memory, Permission Revocation), display constructive, empathetic athletic-partnership copy across progressive escalation stages without sarcasm, maintain an idempotent session-bound counter immune to screen rotation, prioritize direct system intents (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) per Rule 21, and reset the escalation counter upon exemption while strictly preserving workout resumption contracts (`REQ-STB-003`).

### REQ-STB-014: Resilient Foreground Service Startup Delegation & Background Launch Exception Shielding in TrainingApplication [`TrainingApplication.java`, `TrackerService.java`]
- **Tests**: Prevent fatal production crashes (`IllegalStateException` / `SecurityException`) when `startTracking()` is invoked while the process is in background state, ensuring smooth foreground delegation and graceful failure recovery.
- **Implementation**: SecurityException)` block. Any runtime restriction thrown by the Android framework (such as `IllegalStateException` due to background start restrictions or `ForegroundServiceStartNotAllowedException` on Android 12+ / API 31+, or `SecurityException` due to permission state) SHALL be caught and logged cleanly without terminating the application process.
3. *Failure State Recovery & Athlete Notification*: If starting the service throws an `IllegalStateException` or `SecurityException`, the system SHALL reset `cTrackingMode = TrackingMode.READY`, notify registered tracking state observers via `notifyTrackingStateChanged()`, and post `TrackerService.showTrackingInterruptedNotification(this)` to alert the athlete that tracking could not start immediately and provide a high-priority notification to tap and resume once the app is explicitly brought to the foreground.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-STB-002` (*Resilient TrackerService Foreground Service Lifecycle*) and `REQ-STB-003` (*Interrupted Workout Resumption*).
2. *Historical Origin & Commit Trace*: Production crash issue `e8da0510ece240b5986dce3f7efb8ebb` in release `4.9.38.1 (263)`, ticket `ATT-2661`, sprint `2026-41.3`, target release `V4.9.39`, Epic `ATT-235` (*No crashs*).
3. *Root Reason for Existing Formulation*: `TrainingApplication.java:1443` still invoked pre-Android 8.0 `startService(intent)`. When `REQUEST_START_TRACKING` was received while the calling process was in background state (`RCVR idle`), Android threw `IllegalStateException: Not allowed to start service ...: app is in background`. Previous tickets (ATT-622, ATT-635) guarded `TrackerService.onStartCommand()` internally, but never updated the service invocation at the application layer or shielded `startService()` against background launch rejections.
4. *Preservation of Core Invariants*: `START_STICKY` service lifecycle (`REQ-STB-002`), `StartType` contracts (`START_NORMAL`, `RESUME_BY_USER`), paired sensor discovery, accumulator reset, and Pebble watchapp coordination MUST NOT be altered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an application in the foreground initiating workout tracking,
• *When* `startTracking()` executes,
• *Then* it SHALL invoke `ContextCompat.startForegroundService()` with the configured `TrackerService` intent and transition `cTrackingMode` to `TrackingMode.TRACKING`.
• *Given* an application process in the background (`RCVR idle`) where Android rejects service startup with `IllegalStateException`,
• *When* `startTracking()` executes,
• *Then* it SHALL catch the exception without crashing, reset `cTrackingMode` to `READY`, notify observers, and post `TrackerService.showTrackingInterruptedNotification()`.
• *Given* an environment where launching the foreground service throws `SecurityException`,
• *When* `startTracking()` executes,
• *Then* it SHALL catch the exception, log diagnostics, and prevent process termination.

**Invariants**: Normal foreground tracking, `START_STICKY` lifecycle, and clean-room regression pass rate MUST remain fully intact.
- **Rationale**: The system SHALL ensure resilient workout tracking service launch and crash immunity across all Android API versions when `startTracking()` is invoked (ATT-2661):
1. *Foreground Service Startup Delegation*: In `TrainingApplication.startTracking()`, the system SHALL launch `TrackerService` using `ContextCompat.startForegroundService(this, intent)` rather than `startService(intent)`, signaling to the Android platform that the service will promote itself to the foreground via `startForeground()`.
2. *Defensive Launch Exception Shielding*: The invocation of `ContextCompat.startForegroundService(this, intent)` SHALL be encapsulated in a `try ... catch (IllegalStateException

### REQ-STB-015: Simulated Process Exit Reason Injection Channel & Native PyQt6 Desktop Kill Simulator Architecture [`TST-STB-015`]
- **Tests**: `ProcessExitReasonHelper.kt`, `tools/simulate_kill.py`
- **Implementation**: Support simulated exit reason injection and PyQt6 desktop GUI for process kill simulator.
- **Rationale**: The system SHALL provide a debug-only simulated process exit reason injection channel in `ProcessExitReasonHelper.kt` and a native PyQt6 desktop GUI in `tools/simulate_kill.py` to enable deterministic desk verification of Low Memory Kills (LMK), Battery Kills, and Permission Revocations without requiring physical device RAM exhaustion (ATT-3377, harmonizing with `REQ-STB-012` and `ATT-2469`):
1. *Debug Exit Reason Injection Channel (`ProcessExitReasonHelper.kt`)*:
• In debug builds (`BuildConfig.DEBUG`), `ProcessExitReasonHelper.resolveKillReason()` SHALL inspect SharedPreferences for debug key `pref_debug_simulated_kill_reason` (`PREF_DEBUG_SIMULATED_KILL_REASON = "pref_debug_simulated_kill_reason"`).
• Supported simulated values SHALL map directly: `"LOW_MEMORY"` -> `KillReason.LOW_MEMORY`, `"PERMISSION_REVOKED"` -> `KillReason.PERMISSION_REVOKED`, `"BATTERY_KILL"` -> `KillReason.BATTERY_KILL`, and `"GENERIC"` -> `KillReason.GENERIC_UNFINISHED`.
• Idempotent Single-Use Consumption: When present in debug builds, the system SHALL consume and immediately clear `pref_debug_simulated_kill_reason` from SharedPreferences (`prefs.edit().remove(PREF_DEBUG_SIMULATED_KILL_REASON).apply()`), preventing spurious re-triggering upon subsequent activity recreation or screen rotations.
• Zero Release Footprint: In release builds (`BuildConfig.DEBUG == false`), the injection channel SHALL be strictly bypassed/stripped; release execution SHALL rely exclusively on system `ApplicationExitInfo` and `PowerManager`.
2. *Simulator Tool Injection Support (`tools/simulate_kill.py`)*:
• `KillSimulatorController` SHALL provide `inject_simulated_kill_reason(reason: str)` writing `pref_debug_simulated_kill_reason` to target package SharedPreferences via ADB `run-as`.
• `kill_lmk_simulation()` SHALL inject `"LOW_MEMORY"` before terminating the process, guaranteeing that subsequent app launch displays the Low Memory recovery dialog (`Icons.Default.Memory` and `R.string.unfinished_workout_title_memory`).
• `kill_battery_simulation()` and `kill_permission_simulation()` SHALL support injecting their corresponding simulated reasons.
3. *Native PyQt6 Desktop GUI Architecture (`tools/simulate_kill.py`)*:
• `simulate_kill.py --gui` SHALL launch a native PyQt6 desktop application matching the obsidian dark theme architecture of `tools/replay_workout.py`.
• The GUI SHALL display high-glanceability status cards: Target Package, Device Connection, Process PIDs, Battery Whitelist Status, Permission Status, Escalation Level, and Latest OS Exit Record.
• The GUI SHALL provide one-click action triggers: Low Memory Kill (with injection), Battery Kill (Stages 1–3), Permission Kill, SIGKILL, Whitelist Toggle, Permissions Toggle, App Launch, and Clean Reset.
• Graceful Fallback: If PyQt6 is not installed on the host environment (`HAS_PYQT6 == False`), the tool SHALL display an advisory notice and fall back gracefully to the interactive terminal menu (`TerminalKeyReader`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new developer tooling requirement (`REQ-STB-015`), harmonizing with `REQ-STB-012` (*Forensic Process Kill Diagnosis*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-3377`, Sprint `2026-42.1`. Follows desk testing limitations discovered in Sprint `2026-40.16` (`ATT-2079`) and `ATT-2469`, where AOSP in-kernel Low Memory Killer (`lmkd`) mechanics prevented synthesizing genuine LMK records from user-space ADB.
3. *Root Reason for Existing Formulation*: `ProcessExitReasonHelper` originally relied exclusively on platform `ApplicationExitInfo`. Because user-space kills produce `REASON_SIGNALED`, unwhitelisted desk test devices always fell back to `BATTERY_KILL`, making LMK dialog verification impossible at the desk without physical RAM exhaustion.
4. *Preservation of Core Invariants*: Production release builds (`BuildConfig.DEBUG == false`) rely strictly on Android OS exit reasons with zero exposure to debug preferences; dialog callbacks, resumption state machines, and battery settings intents remain 100% intact; 100% clean-room test pass rate preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a debug build of aTrainingTracker on a connected device,
• *When* executing `python3 tools/simulate_kill.py --kill lmk` and launching the app with an unfinalized workout,
• *Then* `StartOrResumeDialog` SHALL render the Low Memory dialog with the memory chip icon (`Icons.Default.Memory`) and title `R.string.unfinished_workout_title_memory` ("Training unterbrochen: Speicherengpass").
• *Given* an injected simulated exit reason in SharedPreferences,
• *When* `resolveKillReason()` executes,
• *Then* the key SHALL be removed from SharedPreferences immediately, ensuring subsequent normal launches do not re-evaluate the simulated reason.
• *Given* a release build configuration (`BuildConfig.DEBUG == false`),
• *When* inspecting bytecode or testing release behavior,
• *Then* the debug injection code SHALL be bypassed.
• *Given* a workstation with `PyQt6` installed,
• *When* running `python3 tools/simulate_kill.py --gui`,
• *Then* a native desktop window matching the obsidian dark theme of `tools/replay_workout.py` SHALL open, displaying real-time status cards and quick action triggers.

**Invariants**: Zero release footprint, recovery dialog contracts preserved, 100% unit test pass rate.

### REQ-TOOL-001: Standalone External ADB GPX Replay & Mock GPS Simulation Tool for Desk Verification [`TST-TOOL-001`]
- **Tests**: `tools/fake_gps.py`
- **Implementation**: External CLI GPX replay and fake GPS simulation tool for desk verification via ADB.
- **Rationale**: The project SHALL provide an external CLI developer testing tool (`tools/fake_gps.py`) executing outside the Android application that streams simulated GPS coordinates from any GPX route into a connected physical Android device (e.g. Pixel 10) or emulator via ADB `cmd location providers` at 1 Hz for desk testing and sprint review verification without modifying the production application source code or requiring third-party mock location apps (ATT-2467):
1. *External CLI Architecture (`tools/fake_gps.py`)*:
• The tool SHALL be implemented as a standalone Python 3 utility under `tools/fake_gps.py` requiring zero modifications to the Android application source code (`app/`).
• The tool SHALL synchronize across all active system location providers (`gps`, `network`, `fused`), registering test providers and injecting synchronous updates into all three to prevent the Android operating system and Google Play Services from oscillating between simulated coordinates and live desk Wi-Fi fixes.
• Single-Instance Process Guard: The tool SHALL automatically identify and terminate any previously running background instance (`pgrep -f fake_gps.py`), preventing coordinate collision and duplicate route injection.
• Automatic device discovery: The tool SHALL automatically detect connected devices via `adb devices` or accept `--device <serial>`.
2. *GPX Parsing & Geodesic Interpolation Engine*:
• The tool SHALL parse standard GPX files (`--gpx <path>`) extracting `<trkpt>` trackpoints (latitude, longitude, elevation, timestamp).
• In the absence of an external file, the tool SHALL offer built-in sample routes (`--sample munich`, `--sample fork`, `--sample straight`) including diverging fork routes specifically tailored for ATT-1955 verification.
• The engine SHALL calculate cumulative geodesic distances along the route using the Haversine formula.
• Movement progression SHALL advance along polyline segments with linear geodesic interpolation and continuous forward travel bearing ($\theta \in [0^\circ, 360^\circ)$) calculated strictly based on the geodesic difference between the previous and current emitted location fixes.
3. *Interactive Real-Time Terminal Dashboard & Hotkey Controls*:
• The tool SHALL present an interactive live terminal dashboard displaying: route name, status (`[PLAYING]`, `[PAUSED]`, `[STOPPED]`), current Lat/Lon, effective speed (km/h), speed multiplier, distance covered (km), elapsed time, bearing heading, off-route status, and a progress bar.
• Hotkey controls (non-blocking single-keypress):
- `[Space]`: Toggle Play / Pause.
- `[s]`: Stop simulation and rewind to start.
- `[1]`, `[2]`, `[5]`, `[0]`: Set speed multiplier ($1\times, 2\times, 5\times, 10\times$).
- `[+]` / `[-]`: Increment / decrement base speed by 5 km/h.
- `[d]`: Toggle deliberate off-route deviation (offsets coordinates $75\text{ m}$ perpendicular to forward bearing to trigger off-route warnings).
- `[r]`: Rejoin route immediately.
- `[<]` / `[>]`: Scrub backward / forward by 10% of route distance.
- `[q]` / `Ctrl+C`: Stop simulation, remove test provider, and exit cleanly.
4. *Safe Lifecycle & Hardware GPS Restoration Guard*:
• The tool SHALL register robust exit handlers (`atexit`, `SIGINT`, `SIGTERM`).
• Upon exit or interruption, the tool SHALL execute `adb shell cmd location providers remove-test-provider` across all registered providers (`gps`, `network`, `fused`), ensuring the physical device immediately and seamlessly restores normal hardware GNSS location services without manual intervention.
5. *Preservation of System Invariants*:
• Zero mock code compiled into production release APKs.
• 100% unit test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-TOOL-001`).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2467`, Epic `ATT-2466` (*Developer Testing Tools*). Resolves review blockage where navigation tickets could not be tested at the desk.
3. *Root Reason for Existing Formulation*: Testing turn-by-turn navigation, active chevrons, and fork alerts previously required outdoor bike rides. Product Owner explicitly requested a external tool without modifying the app code.
4. *Preservation of Core Invariants*: Production code isolation, clean hardware GPS restoration across all providers, single-instance safety, and 100% unit test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected Android device (Pixel 10) running aTrainingTracker,
• *When* `python3 tools/fake_gps.py --sample munich` is executed,
• *Then* the tool configures the system test providers (`gps`, `network`, `fused`) and streams simulated GPS fixes at 1 Hz,
• *And* the app receives moving GPS fixes as if the athlete were riding outdoors, updating map chevrons and navigation cues without jumping to Wi-Fi desk fixes.
• *Given* an active simulation,
• *When* the user presses `[5]`,
• *Then* playback advances at $5\times$ speed ($125\text{ km/h}$).
• *Given* an active simulation,
• *When* the user presses `[d]`,
• *Then* injected coordinates diverge $75\text{ m}$ perpendicular from the route, triggering off-route detection in the app.
• *Given* an active simulation,
• *When* the user presses `[q]` or Ctrl+C,
• *Then* all test providers are cleanly removed from the device via ADB and normal hardware GPS resumes.

**Invariants**: Clean hardware GPS restoration across all providers, single-instance protection, and 100% unit test pass rate MUST NOT be broken.

### REQ-TOOL-002: Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation [`TST-TOOL-002`]
- **Tests**: `tools/replay_workout.py`
- **Implementation**: Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS simulation.
- **Rationale**: The project SHALL provide an external workout replay tool (`tools/replay_workout.py`) executing outside the Android application that parses recorded Garmin/Strava `.tcx` XML activities and broadcasts simulated BLE Cycling Power (`0x1818`), BLE Heart Rate (`0x180D`), and ADB mock GPS locations (`gps`, `network`, `fused`) concurrently in clock synchronization for desk testing and sprint review verification without modifying the production application source code (ATT-2969, amended by `REQ-TOOL-003`):
1. *TCX Parsing & Telemetry Extraction Engine*:
• The tool SHALL parse standard `.tcx` XML documents containing `<Trackpoint>` entries.
• For each trackpoint, the parser SHALL extract `Time`, `Position` (`LatitudeDegrees`, `LongitudeDegrees`), `AltitudeMeters`, `DistanceMeters`, `HeartRateBpm`, `Cadence`, and `Extensions` TPX `Watts` / `Speed`.
• The engine SHALL compute cumulative distance, track duration, and continuous forward travel bearing ($\\theta \\in [0^\\circ, 360^\\circ)$).
• The tool SHALL provide a built-in sample workout (`tools/sample_workout.tcx`) when no file argument is provided.
2. *Synchronized Playback Engine & Interactive Scrubbing*:
• The playback engine SHALL advance through the workout timeline with wall-clock pacing (1 Hz base rate).
• Supported playback multipliers: $1\\times, 2\\times, 5\\times, 10\\times$.
• Interactive scrubbing: The user SHALL be able to seek to any point in the timeline ($0.0 \\dots 1.0$), immediately updating current position, altitude, speed, heart rate, cadence, and power in lockstep.
• Transport states: `PLAYING`, `PAUSED`, `STOPPED`.
3. *Simultaneous BLE GATT Peripheral Broadcasting & Dual Advertising*:
• The tool SHALL register a complete BlueZ D-Bus GATT peripheral application (`org.bluez.GattManager1.RegisterApplication`) exposing Cycling Power (`0x1818`), Heart Rate (`0x180D`), and Device Information (`0x180A`).
• The tool SHALL register BlueZ `LEAdvertisement1` advertising objects via `org.bluez.LEAdvertisingManager1.RegisterAdvertisement` with standard 16-bit UUIDs (`['1818']` for `ATT-Pwr` and `['180d']` for `ATT-HRM`).
• The tool SHALL run the GLib main loop in a dedicated background worker thread using an isolated `GLib.MainContext.new()` and `push_thread_default()` to ensure thread-safe D-Bus method and property handling without colliding with PyQt6.
• The tool SHALL provide a `--no-ble` flag to bypass BLE GATT server initialization when Bluetooth hardware or permissions are absent.
4. *Android 14+ ADB Mock GPS Location Injection*:
• The tool SHALL inject simulated GPS fixes via ADB strictly adhering to Android 14+ `LocationShellCommand` syntax: `cmd location providers set-test-provider-location <p> --location <lat,lon> --accuracy 2.5 --time <ms>` across `gps`, `network`, and `fused`.
• Unsupported options (`--altitude`, `--speed`, `--bearing`) that trigger `IllegalArgumentException` on Android 14+ SHALL be omitted from `cmd location` calls.
• Clean lifecycle guard: The tool SHALL remove test providers upon exit (`remove-test-provider`) via `atexit` and `signal` handlers.
• The tool SHALL provide a `--no-gps` flag to bypass ADB location injection when no device is attached.
5. *Rule 30 Host Pre-Flight Self-Diagnostics*:
• The tool SHALL execute pre-flight self-diagnostics at startup verifying host Bluetooth daemon, adapter power, LE advertising capability, ADB device presence, and shell mock location permission (`android:mock_location`).
• The tool SHALL output actionable diagnostics in both CLI terminal and GUI status indicators.
6. *Dual User Interface (PyQt6 Desktop GUI & Headless CLI)*:
• The tool SHALL offer a modern dark/obsidian PyQt6 desktop GUI dashboard with transport controls, interactive timeline slider, digital telemetry cards (Power, Cadence, HR, Speed, Distance, Altitude), status indicators, and file picker dialog.
• The tool SHALL offer a headless CLI mode (`--headless` / `--cli`) for CI environments with non-blocking keybindings.
7. *Desktop Process Lifecycle & Thread Isolation Architecture*:
• The tool SHALL instantiate `QApplication(sys.argv)` as the root operation prior to initializing `ReplayEngine`, `BleReplayServer`, or GUI windows when running in GUI mode.
• The tool SHALL allocate an isolated GLib main context (`GLib.MainContext.new()`) for the D-Bus worker thread to eliminate thread contention on the default context and prevent GTK/GDK assertion failures.
• The tool SHALL decouple ADB GPS coordinate injection to an asynchronous worker queue to guarantee responsive 60fps GUI event dispatching.
• The tool SHALL expand user home directories (`os.path.expanduser`) on input file paths.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-TOOL-002` (*Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation*, Sprint 2026-41.6, ATT-2969).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.6 (`ATT-2969`).
3. *Root Reason for Existing Formulation*: Testing realistic sensor correlation previously required running three disconnected terminal scripts. PO requested a unified desktop replay tool with modern GUI. Initial implementation suffered from inverted initialization order, default GLib context hijacking, and synchronous ADB subprocess stalls on Linux desktop.
4. *Preservation of Core Invariants*: Production code isolation, clean hardware GPS restoration, and 100% unit test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `.tcx` file with trackpoints containing GPS coordinates, altitude, heart rate, cadence, and power,
• *When* parsed by `TcxParser`,
• *Then* all trackpoints are extracted with valid numerical values, correct bearings, and cumulative distances.
• *Given* an active workout replay session,
• *When* seeking to any percentage of the workout or selecting 5x speed,
• *Then* the telemetry metrics update instantly to match the selected trackpoint and playback continues at the selected speed.
• *Given* an active playback session with BLE and ADB enabled on a Linux host with connected Android device,
• *When* playback advances,
• *Then* BLE Cycling Power, BLE Heart Rate, and mock GPS coordinates are emitted simultaneously matching the current trackpoint, and discoverable by the mobile app.
• *Given* a user launching the tool on desktop with GUI,
• *When* `tools/replay_workout.py [file.tcx]` is executed,
• *Then* `QApplication` initializes cleanly without GTK assertion failures or CPU spin locks, opening the GUI dashboard smoothly.
• *Given* a path starting with tilde (e.g. `~/Dropbox/workout.tcx`),
• *When* supplied as argument or opened in file dialog,
• *Then* the path is expanded and loaded successfully.
• *Given* a terminal environment without a display,
• *When* launched with `--headless`,
• *Then* the tool runs in terminal mode without attempting to initialize PyQt6 or open a window.

**Invariants**: Clean hardware GPS restoration across all providers, graceful fallback without Bluetooth/ADB hardware, and 100% unit test pass rate MUST NOT be broken.

### REQ-TOOL-003: BLE Battery Service Simulation & PyQt6 Runtime Battery Level Controls for TCX Replay Tool [`TST-TOOL-003`]
- **Tests**: `tools/replay_workout.py`, `tools/test_replay_workout.py`
- **Implementation**: BLE Battery Service (0x180F) simulation and PyQt6 runtime battery controls for TCX replay tool.
- **Rationale**: The TCX workout replay tool (`tools/replay_workout.py`) SHALL simulate the standard Bluetooth SIG Battery Service (UUID `0x180F`) and Battery Level Characteristic (UUID `0x2A19`) across simulated peripherals and provide interactive PyQt6 runtime controls and CLI parameters for testing battery-dependent features (ATT-3178):
1. *Bluetooth SIG Battery Service (`0x180F`) & Characteristic (`0x2A19`)*:
• The tool SHALL register standard Bluetooth SIG Battery Service (`0000180f-0000-1000-8000-00805f9b34fb`) instances on the BlueZ D-Bus GATT application.
• Each Battery Service SHALL expose a Battery Level Characteristic (`00002a19-0000-1000-8000-00805f9b34fb`) supporting `['read', 'notify']`.
• Value payload SHALL be a single unsigned 8-bit integer (`uint8`, 1 byte) clamped to $[0, 100]$.
• Reading the characteristic (`ReadValue`) SHALL return the current battery percentage.
• The characteristic SHALL support notifications (`StartNotify` / `StopNotify`). Whenever the battery level is modified, the characteristic SHALL emit `PropertiesChanged` to dispatch GATT notifications to connected Bluetooth LE clients.
2. *Dual Peripheral Battery Simulation*:
• The tool SHALL maintain independent battery state variables for Heart Rate (`ATT-HRM`) and Cycling Power (`ATT-Pwr`).
• Peripheral advertisement objects (`ad_hrm` and `ad_pwr`) SHALL include `'180f'` in their `ServiceUUIDs` lists (`['180d', '180f']` and `['1818', '180f']`).
• `BleReplayServer` SHALL provide thread-safe methods `set_hr_battery(level: int)` and `set_power_battery(level: int)`.
3. *PyQt6 Runtime Battery Control Widgets (`ReplayGuiWindow`)*:
• The GUI dashboard SHALL incorporate a dedicated "SENSOR BATTERY SIMULATION" panel.
• The panel SHALL feature two distinct control columns: Heart Rate Battery (`ATT-HRM`) and Cycling Power Battery (`ATT-Pwr`).
• Each column SHALL contain:
* A status label displaying the current battery percentage (e.g. `HR Battery: 100%`).
* A continuous slider (`QSlider`, range 0 to 100) allowing smooth adjustment.
* Rapid preset buttons: `100%` (Full), `50%` (Nominal), `15%` (Low), `5%` (Critical).
• Real-time notification dispatch: Modifying the slider or clicking a preset button SHALL immediately update the label, tint the percentage display based on severity ($\le 20\%$ warning amber `#FFA726`, $\le 5\%$ critical red `#CF6679`), and invoke the corresponding BLE server battery setter to emit GATT notifications in real time.
4. *Command-Line & Headless Support*:
• The CLI parser SHALL support `--hr-battery <0-100>` (default 100) and `--power-battery <0-100>` (default 100).
• In headless CLI mode, initial battery levels SHALL be displayed on startup.
5. *Preservation of System Invariants*:
• Production application code isolation: Zero mock or test code in `app/src/main`.
• Clean fallback: Seamless mock execution when BlueZ or PyQt6 is unavailable.
• 100% unit test pass rate across `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-TOOL-003`), extending TCX replay capabilities defined in `REQ-TOOL-002` (`tools/replay_workout.py`).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.9 (`ATT-3178`). Follows Sprint 2026-40.16 retro (`ATT-2389`), where low sensor battery tickets (`ATT-2192`, `ATT-3167`) could not be verified at the desk due to lack of battery simulation.
3. *Root Reason for Existing Formulation*: `REQ-TOOL-002` initially prioritized GPS, Power, Cadence, and Heart Rate data streams. Battery Service simulation was postponed.
4. *Preservation of Core Invariants*: Production code isolation, thread safety via isolated `GLib.MainContext`, and fallback behavior strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `tools/replay_workout.py` running with BLE enabled,
• *When* aTrainingTracker connects to `ATT-HRM` or `ATT-Pwr`,
• *Then* the app discovers the Battery Service (UUID `0x180F`) and successfully reads the configured battery level (`0x2A19`).
• *Given* an active connected workout replay session,
• *When* the user adjusts the HR or Power battery slider in the GUI (e.g. from 80% to 15%),
• *Then* `PropertiesChanged` is emitted on D-Bus, transmitting a GATT notification with value 15% to connected Android clients.
• *Given* the CLI launched with `--hr-battery 15 --power-battery 5`,
• *When* the tool starts up,
• *Then* `BleReplayServer` initializes with HR battery at 15% and Power battery at 5%.

**Invariants**: Production code isolation, thread safety via isolated GLib main context, and 100% unit test pass rate MUST NOT be broken.

### REQ-TOOL-004: TCX Replay Per-Device Kill, Disconnection & Reconnection Simulation [`TST-TOOL-004`]
- **Tests**: `tools/replay_workout.py`, `tools/test_replay_workout.py`
- **Implementation**: TCX replay per-device kill, disconnection, and reconnection simulation across BLE HRM, Power, and mock GPS.
- **Rationale**: The TCX workout replay tool (`tools/replay_workout.py`) SHALL provide independent runtime kill/disconnect and revive/reconnect controls for each simulated device (Heart Rate Monitor `ATT-HRM`, Cycling Power Meter `ATT-Pwr`, and Mock GPS location stream) to test sensor dropouts, signal loss, and automatic reconnection recovery (ATT-3179):
1. *Per-Device BLE Disconnect & Revive Lifecycle (`BleReplayServer`)*:
• The server SHALL maintain independent kill state flags: `is_hr_killed: bool` and `is_power_killed: bool`.
• `kill_hr()` SHALL unregister `ad_hrm` advertisement (`ad_manager.UnregisterAdvertisement`), suppress HR measurement and HR battery GATT notifications, and invoke `Disconnect()` on connected client device objects via BlueZ D-Bus ObjectManager.
• `revive_hr()` SHALL re-register `ad_hrm` advertisement (`ad_manager.RegisterAdvertisement`), and resume HR measurement and HR battery GATT notifications at the active TCX timestamp.
• `kill_power()` SHALL unregister `ad_pwr` advertisement (`ad_manager.UnregisterAdvertisement`), suppress Cycling Power measurement and Power battery GATT notifications, and invoke `Disconnect()` on connected client device objects via BlueZ D-Bus ObjectManager.
• `revive_power()` SHALL re-register `ad_pwr` advertisement (`ad_manager.RegisterAdvertisement`), and resume CP measurement and Power battery GATT notifications at the active TCX timestamp.
2. *GPS Signal Loss Simulation (`AdbGpsInjector`)*:
• The injector SHALL maintain an independent kill state flag: `is_killed: bool`.
• `kill()` SHALL suppress enqueuing coordinates in `inject_location()` while keeping test providers active in Android system, simulating satellite loss (tunnels/urban canyons).
• `revive()` SHALL resume coordinate injection immediately at the current TCX point.
3. *Master TCX Timeline Invariant (Time-Skip Model)*:
• The master TCX playback timeline SHALL advance globally regardless of individual device kill states.
• Reviving a device SHALL immediately synchronize telemetry to the active playback timestamp.
4. *PyQt6 GUI Connectivity Controls (`ReplayGuiWindow`)*:
• The GUI dashboard SHALL provide interactive toggle buttons (`[Kill HR]` / `[Revive HR]`, `[Kill Power]` / `[Revive Power]`, `[Kill GPS]` / `[Revive GPS]`).
• The dashboard SHALL render real-time status badges (Active/Broadcasting green vs. Killed/Suppressed red).
5. *Headless CLI & Scripting Support*:
• The headless CLI runner (`run_headless_cli`) SHALL provide non-blocking hotkeys: `'h'` (HR toggle), `'p'` (Power toggle), `'g'` (GPS toggle).
• Live connectivity status badges SHALL be displayed in the terminal ticker bar.
• The CLI parser SHALL support automated timestamp flags: `--kill-hr-at <sec>`, `--revive-hr-at <sec>`, `--kill-power-at <sec>`, `--revive-power-at <sec>`, `--kill-gps-at <sec>`, `--revive-gps-at <sec>`.
6. *Preservation of System Invariants*:
• Production application code isolation: Zero mock or test code in `app/src/main`.
• Clean fallback: Seamless mock execution when BlueZ or PyQt6 is unavailable.
• 100% unit test pass rate across `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-TOOL-004`), extending TCX replay capabilities defined in `REQ-TOOL-002` and `REQ-TOOL-003` (`tools/replay_workout.py`).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.9 (`ATT-3179`). Follows Sprint 2026-40.16 retro (`ATT-2389`), where testing sensor reconnection recovery and GPS tunnel behavior required physical sensor manipulation or manual script restarts.
3. *Root Reason for Existing Formulation*: Prior tool iterations focused on initial streaming synchronization; runtime dropout simulation was deferred.
4. *Preservation of Core Invariants*: Production code isolation, thread safety via isolated `GLib.MainContext`, and clean ADB test provider cleanup strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `tools/replay_workout.py` running with BLE and GPS active,
• *When* `kill_hr()` is triggered in GUI or CLI,
• *Then* HR advertising ceases, HR GATT notifications are silenced, and connected clients receive disconnect notifications.
• *When* `revive_hr()` is triggered in GUI or CLI,
• *Then* HR advertising resumes, live HR GATT notifications resume matching the current TCX timestamp, and the client reconnects automatically.
• *Given* replay running with GPS active,
• *When* `kill()` is triggered on `AdbGpsInjector`,
• *Then* coordinate injection stops while test providers remain active.
• *When* `revive()` is triggered on `AdbGpsInjector`,
• *Then* coordinate injection resumes at the current TCX coordinates.
• *Given* automated CLI flags `--kill-hr-at 10 --revive-hr-at 20`,
• *When* playback reaches 10s and 20s,
• *Then* HR is automatically killed at 10s and revived at 20s.

**Invariants**: Production code isolation, thread safety via isolated GLib main context, and 100% unit test pass rate MUST NOT be broken.

### REQ-ARC-001: Safe Enum Deserialization, Observability & Crash Immunity Across External Boundaries [`TST-ARC-001`]
- **Tests**: `EnumUtils.kt`, `DeviceManager.java`, `WorkoutSummariesDatabaseManager.java`, `PeriodSummariesDatabaseManager.kt`
- **Implementation**: Safe enum deserialization and crash immunity across external boundaries.
- **Rationale**: The system SHALL guarantee crash immunity against `NullPointerException` and `IllegalArgumentException` during enum deserialization from external untrusted sources (SQLite cursors, IPC Intents/Bundles, JSON payloads, and SharedPreferences), while enforcing ASPICE data correctness and telemetry observability (ATT-3562, parent epic ATT-235):
1. *Canonical Deserialization Engine (`REQ-ARC-001.1`)*:
• The system SHALL provide a centralized utility `com.atrainingtracker.util.EnumUtils` offering `safeValueOf(enumClass, name, defaultValue, tag)` and `safeValueOfOrNull(enumClass, name, tag)`.
• Kotlin callers SHALL be provided with inline reified extension functions `safeEnumValueOf(name, default, tag)` and `safeEnumValueOfOrNull(name, tag)`.
• The utility SHALL accept `null`, empty string, leading/trailing whitespace strings, and unrecognized string tokens without throwing any exception.
2. *Criticality-Tiers Handling Strategy (`REQ-ARC-001.2`)*:
• *Tier A (Critical Hardware & Lifecycle State)*: In `DeviceManager` (`Protocol`, `DeviceType`) and `TrackerService` (`StartType`), unparseable or null enum strings SHALL trigger an explicit high-severity error log (`Log.e`) and a graceful operation abort / early exit, preventing corrupted radio dispatch or undefined lifecycle states.
• *Tier B (Database Multi-Record Queries)*: In database query cursor iteration (`WorkoutSummariesDatabaseManager`, `ExportStatusDatabaseManager`, `PebbleDatabaseManager`), rows containing unparseable or null enum strings SHALL trigger a diagnostic warning log (`Log.w`) and be skipped (`continue;`), preventing corruption of user statistics with synthetic ghost records.
• *Tier C (UI Configuration & Preferences)*: In preference loaders, dialog arguments, and view configurations (`PeriodSummariesDatabaseManager`, `EquipmentDbHelper`, `TrainingApplication`, `TuningPreferencesDataStore`), invalid or missing enum strings SHALL trigger a diagnostic warning log (`Log.w`) and fallback safely to canonical defaults (`PeriodType.WEEK`, `BSportType.UNKNOWN`, `ViewSize.NORMAL`, `MyUnits.METRIC`).
3. *Observability & Telemetry Invariant (`REQ-ARC-001.3`)*:
• All safe deserialization fallbacks and skipped rows SHALL emit structured diagnostic logs containing the caller TAG, the target enum class name, and the invalid string token for telemetry traceability.
4. *Architectural Guard & Regression Prevention (`REQ-ARC-001.4`)*:
• The codebase SHALL enforce safe deserialization via automated static audit tests (`SafeEnumDeserializationAuditTest.kt`) ensuring no direct unguarded `Enum.valueOf(cursor.getString(...))` or `Enum.valueOf(intent.getStringExtra(...))` calls exist in production code.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-ARC-001`. Fixes regression vectors exposed in ATT-2179 and ATT-3562 across database, service, and UI layers.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3562`). `Enum.valueOf` directly invoked on SQLite column values, intent extras, and preferences without null or validation guards.
3. *Root Reason for Existing Formulation*: Legacy code assumed non-null database schemas and trusted IPC payloads. Schema evolution, NULL database fields, and unexpected extras caused uncaught NPE and IAE crashes.
4. *Preservation of Core Invariants*: SQLite schemas and database table definitions remain 100% backward compatible. Hardware safety is preserved through Tier A validation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a null, empty, or whitespace string input,
• *When* `EnumUtils.safeValueOf` is called with a default value,
• *Then* the default value is returned without exception and a warning is logged.
• *Given* an unrecognized string token,
• *When* `EnumUtils.safeValueOfOrNull` is called,
• *Then* `null` is returned without exception and a warning is logged.
• *Given* a database cursor containing an unrecognized `SensorType` or `ExtremaType`,
• *When* `WorkoutSummariesDatabaseManager` fetches extrema records,
• *Then* the corrupted row is safely skipped with a warning log, and valid rows are returned.
• *Given* an intent with missing or invalid `Protocol` or `DeviceType`,
• *When* `DeviceManager` receives the intent,
• *Then* the search operation aborts safely with an error log without initiating invalid radio scans.

**Invariants**: Zero uncaught exceptions on external enum strings, hardware driver safety preserved, 100% clean-room test pass rate.

### REQ-STB-016: Guarded Service Launches and Idempotent BroadcastReceiver Teardown Across Lifecycle Boundaries [`TST-STB-016`]
- **Tests**: `LifecycleUtils.kt`, `MainActivityWithNavigation.kt`, `BANALServiceRepository.kt`, `TrainingApplication.java`, `TrackerService.java`, `DeviceManager.java`
- **Implementation**: Guarded service launches and idempotent BroadcastReceiver teardown across lifecycle boundaries.
- **Rationale**: The system SHALL guarantee complete crash immunity against Android OS lifecycle exceptions (`IllegalStateException`, `BackgroundServiceStartNotAllowedException`, `ForegroundServiceStartNotAllowedException`, `SecurityException`, and `IllegalArgumentException: Receiver not registered`) during service launches and `BroadcastReceiver` teardown across activities, fragments, services, repositories, viewmodels, and hardware sensor devices (ATT-3563, parent epic ATT-235):
1. *Canonical Lifecycle Safety Utility (`LifecycleUtils.kt`)*:
• The system SHALL provide centralized utility `com.atrainingtracker.util.LifecycleUtils` offering `@JvmStatic` methods `safeStartService(context: Context?, intent: Intent?, tag: String = "LifecycleUtils"): ComponentName?` and `safeUnregisterReceiver(context: Context?, receiver: BroadcastReceiver?, tag: String = "LifecycleUtils"): Boolean`.
• Kotlin callers SHALL be provided with extension functions `Context?.safeStartService(...)` and `Context?.safeUnregisterReceiver(...)`.
• `safeStartService` SHALL accept null context or intent returning null, invoke `context.startService(intent)`, and catch `IllegalStateException`, `SecurityException`, and `Throwable`, logging a structured warning (`Log.w`) without re-throwing.
• `safeUnregisterReceiver` SHALL accept null context or receiver returning false, invoke `context.unregisterReceiver(receiver)`, catch `IllegalArgumentException` and `IllegalStateException`, and log a structured warning (`Log.w`) containing the caller tag and receiver runtime class name, returning false without crashing.
2. *Service Launch Guarding (`MainActivityWithNavigation`, `BANALServiceRepository`, `TrainingApplication`)*:
• All direct unshielded `Context.startService(...)` calls across `MainActivityWithNavigation.kt` (`BANALService`), `BANALServiceRepository.kt`, and `TrainingApplication.java` (Pebble services) SHALL be migrated to `LifecycleUtils.safeStartService`.
• Callers SHALL safely tolerate `null` return values without throwing downstream `NullPointerException`.
3. *Comprehensive Receiver Teardown Guarding (~35 Call Sites)*:
• All unshielded `unregisterReceiver(...)` calls across `MainActivityWithNavigation.kt`, `TrackerService.java`, `TrainingApplication.java`, `BANALService.java`, `DeviceManager.java`, `MyDevice.java`, `BatteryDevice.java`, `MyRemoteDevice.java`, `AltitudeFromPressureDevice.java`, `FilterManager.java`, `EquipmentFragment.kt`, `ConfigViewsFragment.java`, `DevicesTabbedViewModel.kt`, `ExportStatusRepository.kt`, `SensorGridScreen.kt`, and Pebble services SHALL be migrated to `LifecycleUtils.safeUnregisterReceiver`.
• Teardown operations SHALL be completely idempotent, tolerating unregister calls when receivers were never registered or were already unregistered.
4. *Static Architectural Guard & CI/CD Contract (`REQ-STB-016.4`)*:
• The codebase SHALL enforce lifecycle safety via an automated JUnit architecture audit test (`LifecycleServiceReceiverSafetyAuditTest.kt`) executed as part of `./gradlew testDebugUnitTest`, verifying zero unshielded `unregisterReceiver(` or `startService(` calls exist in `app/src/main` outside `LifecycleUtils`.
• Standalone CLI linter `tools/lint_lifecycle_safety.py` SHALL verify zero violations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-STB-016`. Fixes production crash reports in ATT-622, ATT-2661, and ATT-3563.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3563`). Legacy code relied on symmetric lifecycle callbacks and unconstrained pre-Oreo background service start rules.
3. *Root Reason for Existing Formulation*: Android API 26+ background execution limits and lifecycle race conditions throw `IllegalStateException` on service starts and `IllegalArgumentException` on unregistering unregistered receivers.
4. *Preservation of Core Invariants*: Bounded service operations via `bindService(..., BIND_AUTO_CREATE)` remain fully functional; receiver unregistration postcondition (receiver is unregistered) is preserved; zero crashes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an application in the background when `BANALService` or Pebble services are requested,
• *When* `safeStartService` executes,
• *Then* `null` is returned, a warning is logged, and no `IllegalStateException` is thrown.
• *Given* an Activity or Service being destroyed where a `BroadcastReceiver` was not registered,
• *When* `safeUnregisterReceiver` executes,
• *Then* `false` is returned, a warning is logged, and no `IllegalArgumentException` crashes the process.
• *Given* the automated test suite,
• *When* `LifecycleServiceReceiverSafetyAuditTest` runs,
• *Then* zero unshielded calls exist in `app/src/main`.

**Invariants**: Zero unhandled lifecycle exceptions, idempotent receiver unregistration, 100% clean-room test pass rate.

### REQ-TOOL-005: Bluetooth Low Energy Cycling Power Simulator Pedal Power Balance Simulation [`TST-TOOL-005`]
- **Tests**: `tools/ble_power_simulator.py`, `tools/test_ble_power_simulator.py`
- **Implementation**: BLE Cycling Power Simulator Pedal Power Balance Simulation.
- **Rationale**: The BLE Cycling Power simulator tool (`tools/ble_power_simulator.py`) SHALL simulate Bluetooth SIG Cycling Power Service (UUID `0x1818`) dual-sided pedal power balance telemetry, matching the parsing logic in `BTLEBikePowerDevice.java` and supporting interactive live testing of the cockpit balance widget (`REQ-UI-377`) (ATT-3678):
1. *Cycling Power Feature Characteristic (`UUID 0x2A65`)*:
• The feature characteristic SHALL set bit 0 (`0x00000001`, Pedal Power Balance Supported) concurrently with bit 3 (`0x00000008`, Crank Revolution Data Supported), broadcasting raw 32-bit feature mask `[0x09, 0x00, 0x00, 0x00]`.
2. *Cycling Power Measurement Characteristic (`UUID 0x2A63`)*:
• The measurement characteristic SHALL expose configurable attribute `pedal_balance` (default `50.0%`, range `0.0..100.0%`, or `None` when disabled).
• When `pedal_balance` is enabled (`is not None`):
- The 16-bit `flags` field SHALL set bit 0 (`0x0001`, Pedal Power Balance Present) in addition to bit 5 (`0x0020`, Crank Revolution Data Present).
- The 1-byte raw balance field (UINT8 in $1/2\%$ resolution: `raw_balance = int(round(pedal_balance * 2))`, clamped to `[0, 200]`) SHALL be inserted immediately after Instantaneous Power (offset 4) and before Crank Revolution Data.
• When `pedal_balance` is disabled (`None`):
- Bit 0 in `flags` SHALL remain unset (`0x0000`).
- The raw balance byte SHALL NOT be inserted into the measurement payload, falling back to legacy 8-byte layout.
3. *Interactive CLI Control (`keyboard_input_loop`)*:
• The interactive CLI loop SHALL support commands `b <value>` (setting balance percentage in $[0.0, 100.0]\%$) and `b off` (disabling balance transmission).
• The status summary printout SHALL report active balance percentage, left/right split, or disabled status.
4. *Preservation of System Invariants*:
• Production code isolation: Zero mock or simulator code in `app/src/main`.
• 100% clean-room unit test coverage in `tools/test_ble_power_simulator.py`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-TOOL-005`), extending BLE simulation capabilities defined in `tools/ble_power_simulator.py`. Complements `REQ-UI-377` (*Pedal Power Balance Real-Time Visual Split Indicator*).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.5 (`ATT-3678`). Follows Sprint 2026-42.4 review of `ATT-1295` where live on-device testing was blocked due to lack of balance simulation.
3. *Root Reason for Existing Formulation*: Simulator initially supported only basic power and cadence telemetry.
4. *Preservation of Core Invariants*: Full backward compatibility for power and cadence generation, production code isolation, and 100% unit test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `tools/ble_power_simulator.py` running with default settings,
• *When* reading the Cycling Power Feature characteristic (0x2A65),
• *Then* bit 0 is set (0x09) indicating pedal power balance support.
• *Given* `pedal_balance = 50.0`,
• *When* generating measurement payload,
• *Then* flags bit 0 is set, payload length is 9 bytes, and byte at offset 4 has value 100 (50.0%).
• *Given* `b 54` entered in CLI loop,
• *When* generating measurement payload,
• *Then* byte at offset 4 has value 108 (54.0% Right / 46.0% Left, Mild Asymmetry Amber tier).
• *Given* `b off` entered in CLI loop,
• *When* generating measurement payload,
• *Then* flags bit 0 is clear, payload length is 8 bytes, and no balance byte is present.

**Invariants**: Production code isolation, backward compatibility of power/cadence simulation, and 100% test pass rate MUST NOT be broken.

### REQ-TOOL-006: Variable-Interval TCX Telemetry Interpolation, Time-Based Playback Stepping & Workout-Relative ADB GPS Timestamp Synchronization in `tools/replay_workout.py` [`TST-TOOL-006`]
- **Tests**: `tools/replay_workout.py`, `tools/test_replay_workout.py`
- **Implementation**: Fix TCX replay speed inflation and timeline progression for variable-interval GPS tracks in `tools/replay_workout.py`.
- **Rationale**: The TCX workout replay tool (`tools/replay_workout.py`) SHALL preserve $1\text{ Hz}$ physiological telemetry across variable-interval GPS `<Trackpoint>` entries, compute fallback speed from distance/time deltas with zero-division / `NaN` / `Inf` guards when `<Speed>` extensions are absent, advance `ReplayEngine` by workout elapsed time (`time_sec`) rather than raw array index, and stamp ADB mock GPS fixes with Replay Session Epoch workout-relative timestamps so Android `SpeedAndLocationDevice.java` calculates accurate speed regardless of GPS recording interval or replay multiplier (ATT-3927, amending `REQ-TOOL-002`):
1. *Variable-Interval TCX Trackpoint Preservation, Linear Coordinate Interpolation & Safe Fallback Speed (`REQ-TOOL-006.1`)*:
• In `TcxParser.parse_file()`, whenever a `.tcx` file contains at least one valid `<Position>` (`<LatitudeDegrees>`, `<LongitudeDegrees>`), the parser SHALL retain intermediate `<Trackpoint>` elements that have a valid `<Time>` even when `<Position>` is omitted on those intermediate points, preserving their $1\text{ Hz}$ `hr_bpm`, `cadence_rpm`, `power_watts`, `ele`, and `distance_m` telemetry.
• For `<Trackpoint>` elements missing `<Position>`, `TcxParser.parse_file()` SHALL linearly interpolate `lat` and `lon` (and `ele` when `<AltitudeMeters>` is missing) with respect to workout elapsed time `time_sec` between the bounding known GPS fixes (and carry the first/last known fix at leading/trailing boundaries).
• After computing cumulative `distance_m` and `step_d`, for every point $i \ge 1$ where `<Speed>` was not provided in `<Extensions>` (or `speed_mps <= 0.0`): if $\Delta t = \text{points}[i].\text{time\_sec} - \text{points}[i-1].\text{time\_sec} \ge 0.001$, `TcxParser` SHALL compute `raw_speed = step_d / dt` and set `points[i].speed_mps = raw_speed if math.isfinite(raw_speed) else 0.0`; if $\Delta t < 0.001$ (duplicate or malformed timestamps), `points[i].speed_mps` SHALL be set to `0.0` (preventing `ZeroDivisionError`, `NaN`, or `Inf`). If `points[0].speed_mps <= 0.0` and `len(points) > 1`, `points[0].speed_mps` SHALL be initialized to the first subsequent finite non-zero `speed_mps` (or `0.0`).
2. *Elapsed-Time Playback Stepping in `ReplayEngine` (`REQ-TOOL-006.2`)*:
• `ReplayEngine` SHALL maintain `self.elapsed_time_sec: float` initialized to `self.points[0].time_sec` (or `0.0`) and synchronized with `self.points[self.current_index].time_sec` on `seek_ratio()`, `seek_time()`, and `stop()`.
• `ReplayEngine.step(dt_sec: float = 1.0)` SHALL advance `self.elapsed_time_sec += dt_sec * self.speed_multiplier` and advance `self.current_index` to the highest index $k \le \text{len(points)} - 1$ where `self.points[k].time_sec <= self.elapsed_time_sec`, cleanly clamping to `len(self.points) - 1` and transitioning to `PAUSED` at the final point without index-out-of-bounds errors.
• In `run_headless_cli`, the loop SHALL tick at a fixed $1.0\text{ s}$ base interval (`sleep_t = max(0.01, 1.0 - elapsed)`) so `speed_multiplier` is applied once in `engine.step()` rather than squared.
3. *Replay Session Epoch Workout-Relative ADB GPS Timestamps in `AdbGpsInjector` (`REQ-TOOL-006.3`)*:
• `AdbGpsInjector` SHALL maintain a Replay Session Epoch `self.base_epoch_ms: int = int(time.time() * 1000)` and expose `reset_epoch(self, time_sec: float = 0.0)` which sets `self.base_epoch_ms = int(time.time() * 1000) - int(round(time_sec * 1000.0))` (invoked on `stop()`, `load_file()`, or explicit seek so `base_epoch_ms` maintains the exact relative `time_sec` offset from the new current position without artificial `+1000ms` stuttering).
• `AdbGpsInjector.inject_location(self, lat: float, lon: float, ele: float = 0.0, speed_mps: float = 0.0, bearing_deg: float = 0.0, time_sec: Optional[float] = None)` SHALL compute `timestamp_ms = self.base_epoch_ms + int(round(time_sec * 1000.0))` when `time_sec is not None` (falling back to `int(time.time() * 1000)` when `time_sec is None`), preserving exact $\Delta t_{\text{workout}} = (t_i - t_{i-1}) \times 1000\text{ ms}$ across all playback multipliers.
• Both `ReplayGuiWindow._emit_telemetry` and `run_headless_cli` SHALL pass `time_sec=pt.time_sec` to `gps_injector.inject_location(...)`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TOOL-006`, amending `REQ-TOOL-002` (`ATT-2969`) Clauses 1, 2, and 4. Targets `tools/replay_workout.py` and `tools/test_replay_workout.py`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2969`, commit `1523f683`), Sprint 2026-42.0 (`ATT-3178`, `ATT-3179`).
3. *Root Reason for Existing Formulation (Why was this fence built?)*: `tools/replay_workout.py` was originally built against synthetic $1\text{ Hz}$ TCX files where every `<Trackpoint>` contained `<Position>` and `<Speed>` at uniform $1.0\text{ s}$ intervals.
4. *Preservation of Core Invariants (Why is it safe to modify now?)*: Interpolating missing `<Position>` coordinates by `time_sec`, deriving fallback speed from `step_d / dt` (guarded by `dt >= 0.001` and `math.isfinite`), advancing `ReplayEngine` by `elapsed_time_sec`, and stamping ADB `--time` with `base_epoch_ms + int(round(time_sec * 1000))` fixes speed inflation and timeline jumps on variable-interval TCX files while remaining 100% identical for uniform $1\text{ Hz}$ TCX files and leaving production Android code (`SpeedAndLocationDevice.java`) untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a variable-interval `.tcx` file where `<Trackpoint>` elements appear at $t = 0, 1, 2, 3, 4, 5\text{ s}$ with HR/Power at every second, `<Position>` only at $t = 0\text{ s}$ and $t = 5\text{ s}$, and no `<Speed>` extension,
• *When* parsed by `TcxParser.parse_file()`,
• *Then* all 6 trackpoints ($t = 0..5\text{ s}$) are retained with their $1\text{ Hz}$ HR/Power values, intermediate `lat`/`lon` coordinates at $t = 1..4\text{ s}$ are linearly interpolated by time, and `speed_mps` is derived from `step_d / dt > 0` (with `dt < 0.001` safely yielding `0.0`).
• *Given* `ReplayEngine` loaded with trackpoints at $t = 0, 5, 10, 15\text{ s}$ at $1\times$ speed,
• *When* `engine.step()` ($1.0\text{ s}$) is invoked 4 times ($t_{\text{elapsed}} = 4.0\text{ s}$), 5 times ($t_{\text{elapsed}} = 5.0\text{ s}$), and up to $t_{\text{elapsed}} \ge 15.0\text{ s}$,
• *Then* `current_index` remains `0` at $4.0\text{ s}$, advances to `1` ($t = 5.0\text{ s}$) on the 5th step, and transitions to `PAUSED` at `current_index == len(points) - 1` without index-out-of-bounds errors.
• *Given* `AdbGpsInjector.inject_location(..., time_sec=t)` called at $t = 0.0\text{ s}$ and $t = 5.0\text{ s}$ regardless of replay speed multiplier ($1\times, 2\times, 5\times, 10\times$),
• *When* inspecting the queued timestamps,
• *Then* `timestamp_ms(5.0) - timestamp_ms(0.0) == 5000`, so Android's `SpeedAndLocationDevice.java` computes the exact workout speed without inflation.

**Invariants**: Zero production code modifications in `app/src/main`; full backward compatibility with `REQ-TOOL-002`, `REQ-TOOL-003`, `REQ-TOOL-004`; 100% clean-room test pass rate MUST NOT be broken.

### REQ-BLD-001: R8 Minification, Resource Shrinking and DEX Code Obfuscation with Safe ProGuard Keep Rules [`TST-BLD-001`]
- **Tests**: `app/build.gradle`, `app/proguard-rules.pro`, `ProGuardRulesContractTest.kt`
- **Implementation**: Enable R8 minification, resource shrinking, and DEX code obfuscation in release builds with safe ProGuard keep rules to satisfy Google Play threshold (>= 25%).
- **Rationale**: The build system SHALL configure `app/build.gradle` and `app/proguard-rules.pro` to enable R8 minification, resource shrinking, dead-code optimization, and DEX code obfuscation in release builds conforming to Google Play vitals threshold ($\ge 25\%$) while guaranteeing 100% runtime reflection and data persistence safety (ATT-3770):
1. *R8 Minification and Resource Shrinking in Release Build Variant (`REQ-BLD-001.1`)*:
• In `app/build.gradle`, `buildTypes.release` SHALL configure `minifyEnabled = true` and `shrinkResources = true`.
• R8 optimization SHALL use standard rules `getDefaultProguardFile('proguard-android-optimize.txt')` and application rules `'proguard-rules.pro'`.
2. *Removal of Blanket Compose Keep Rule (`REQ-BLD-001.2`)*:
• The blanket rule `-keep class androidx.compose.** { *; }` SHALL be removed from `app/proguard-rules.pro`, delegating Compose keep semantics to official AAR consumer rules bundled in Jetpack Compose libraries and unlocking tree-shaking of unused `material-icons-extended` classes.
3. *Domain Enum Preservation (`REQ-BLD-001.3`)*:
• All enum classes in package `com.atrainingtracker.**` SHALL retain their constant members, values array, and `valueOf(String)` / `safeValueOf(String)` resolution methods to protect SQLite string persistence and SharedPreferences/DataStore deserialization against runtime `IllegalArgumentException`.
4. *Hardware SDK, FIT & Cloud Service Interoperability (`REQ-BLD-001.4`)*:
• Public and reflectively accessed classes in Garmin FIT SDK (`com.garmin.fit.**`), ANT+ Plugin library (`com.dsi.ant.**`), PebbleKit (`com.getpebble.android.kit.**`), Dropbox SDK (`com.dropbox.core.**`), and GPX parser (`io.ticofab.androidgpxparser.**`) SHALL be preserved.
5. *Serialization, ViewModels & Crash Reporting Traceability (`REQ-BLD-001.5`)*:
• Kotlinx serialization descriptors and AndroidX ViewModel reflection constructors SHALL be preserved.
• Stacktrace deobfuscation line numbers and source files SHALL be preserved via `-keepattributes SourceFile,LineNumberTable` and `-renamesourcefileattribute SourceFile`.
6. *Google Play Vitals Obfuscation Threshold Compliance (`REQ-BLD-001.6`)*:
• Release build DEX optimization SHALL achieve $\ge 25\%$ obfuscated classes, generating a valid `mapping.txt` bundled into the release AAB/APK.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-BLD-001` (*R8 Minification, Resource Shrinking and DEX Code Obfuscation with Safe ProGuard Keep Rules*). Targets `app/build.gradle`, `app/proguard-rules.pro`, and `ProGuardRulesContractTest.kt`.
2. *Historical Origin & Commit Trace*: Commit `f1de722dd6d88a193444371fdd9e8f55de9820c5` (May 11, 2026) disabled `minifyEnabled` as an emergency precaution because comprehensive keep rules were not yet configured.
3. *Root Reason for Existing Formulation*: Emergency deactivation avoided potential reflection/SDK crashes, but resulted in 1% obfuscation, violating Google Play's mandatory 25% threshold.
4. *Preservation of Core Invariants*: Full reflection safety for SQLite enum resolution, ANT+/BLE hardware connectivity, Garmin FIT export, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `app/build.gradle` and `app/proguard-rules.pro`,
• *When* `./gradlew :app:assembleRelease` is executed,
• *Then* the build succeeds, `mapping.txt` is generated at `app/build/outputs/mapping/release/mapping.txt`, and release DEX obfuscation is $\ge 25\%$.
• *Given* `app/proguard-rules.pro`,
• *When* inspected for Compose and keep rules in `ProGuardRulesContractTest`,
• *Then* blanket `-keep class androidx.compose.**` is absent, and domain enum, FIT, ANT+, Pebble, Dropbox, and serialization keep rules are present.
• *Given* the complete unit test suite,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without regression.

**Invariants**: Debug build speed preserved (`minifyEnabled false` in debug); SQLite enum persistence intact; 100% clean-room test pass rate MUST NOT be broken.

### REQ-BLD-002: Release CI Automated Test & Lint Gating with Baseline Hygiene [`TST-BLD-002`]
- **Tests**: `.github/workflows/android-release.yml`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `app/lint-baseline.xml`, `ReleaseWorkflowContractTest.kt`
- **Implementation**: Gate release CI artifact generation on `:app:testDebugUnitTest`, document lint suppressions in `app/build.gradle`, eliminate orphaned `StravaSegmentsIntentService` in manifest, and synchronize `lint-baseline.xml`.
- **Rationale**: The build system and CI pipeline SHALL gate release APK/AAB artifact generation and distribution on passing unit tests (`:app:testDebugUnitTest`), maintain release lint checks (`checkReleaseBuilds = true`), document justified lint suppressions, eliminate orphaned manifest declarations, and synchronize the lint baseline (ATT-4025, extending `REQ-BLD-001` and aligning with `REQ-LOC-001`):
1. *Release CI Automated Unit Test Gating (`REQ-BLD-002.1`)*:
• `.github/workflows/android-release.yml` SHALL execute `./gradlew :app:testDebugUnitTest` prior to `:app:assembleRelease` and `:app:bundleRelease`.
• If any unit test in `:app:testDebugUnitTest` fails, the release workflow SHALL fail immediately, aborting build artifact assembly and preventing release APK/AAB upload to GitHub Releases.
2. *Release Lint Execution & Suppressions Justification (`REQ-BLD-002.2`)*:
• `app/build.gradle` SHALL maintain `checkReleaseBuilds = true` to run `lintVitalRelease` during release assembly.
• `app/build.gradle` SHALL document clear technical rationales for active suppressions: `DuplicatePlatformClasses` (legacy Apache HTTP/XML classpath overlaps), `MissingTranslation` (guarded strictly by `TranslationParityTest` under `REQ-LOC-001` in `testDebugUnitTest`), and `Range` (historical annotations in legacy calculation routines targeted for incremental burn-down).
3. *Manifest Component Hygiene (`REQ-BLD-002.3`)*:
• `app/src/main/AndroidManifest.xml` SHALL NOT declare non-existent component classes. Orphaned declarations (such as `StravaSegmentsIntentService`, removed from source) SHALL be excised, resolving `MissingClass` lint errors.
4. *Lint Baseline Synchronization & Burn-Down Hygiene (`REQ-BLD-002.4`)*:
• `app/lint-baseline.xml` SHALL be synchronized with the active Android Gradle Plugin release variant to eliminate orphaned entries and establish a deterministic baseline for incremental code-quality burn-down.
5. *CI Workflow Contract Verification (`REQ-BLD-002.5`)*:
• `ReleaseWorkflowContractTest.kt` SHALL verify via automated contract test that `.github/workflows/android-release.yml` includes `:app:testDebugUnitTest` prior to `assembleRelease` / `bundleRelease`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-BLD-002` (*Release CI Automated Test & Lint Gating with Baseline Hygiene*), extending `REQ-BLD-001` (*R8 Release Builds*) and aligning with `REQ-LOC-001` (*Translation Parity*). Targets `.github/workflows/android-release.yml`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, `app/lint-baseline.xml`, and `ReleaseWorkflowContractTest.kt`.
2. *Historical Origin & Commit Trace*: Commit `192635e1` (Guy Sheffer, Jul 2026) added release workflow without test step; commit `2bc9ae1d` (Gerhard Olsson, Jul 2024) suppressed `MissingTranslation` and `Range` during Android 14 SDK migration.
3. *Root Reason for Existing Formulation*: CI release workflow focused strictly on packaging and signing artifacts; `MissingTranslation` and `Range` were disabled to avoid blocking builds on hundreds of legacy layout and string issues.
4. *Preservation of Core Invariants*: Release APK/AAB signing and asset attachments remain fully operational; 100% clean-room test pass rate; translation integrity preserved via `TranslationParityTest`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `.github/workflows/android-release.yml`,
• *When* evaluated by `ReleaseWorkflowContractTest`,
• *Then* `:app:testDebugUnitTest` is configured to run before `:app:assembleRelease :app:bundleRelease`.
• *Given* a unit test failure during release CI execution,
• *When* the workflow runs,
• *Then* the build halts immediately and release assets are not published.
• *Given* `app/build.gradle`,
• *When* inspected,
• *Then* `checkReleaseBuilds = true` is active, and technical rationales for `DuplicatePlatformClasses`, `MissingTranslation`, and `Range` are documented.
• *Given* `app/src/main/AndroidManifest.xml`,
• *When* audited for component classes,
• *Then* orphaned `StravaSegmentsIntentService` is absent and all declared services/receivers exist in source.
• *Given* the complete unit test suite,
• *When* `./gradlew testDebugUnitTest` runs,
• *Then* 100% of unit tests and `TranslationParityTest` pass without regression.

**Invariants**: Release APK/AAB signing and publishing intact; 100% clean-room test pass rate; translation integrity preserved via `TranslationParityTest`.

### REQ-SEC-041: Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation [`TST-SEC-038`]
- **Tests**: `StravaHelper.kt`, `WorkoutRepository.kt`, `StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java`, `TrainingApplication.java`, `LogcatPrivacySecurityTest.kt`
- **Implementation**: Prevent Strava OAuth credentials, GPS polylines, and raw HTTP payloads from appearing in logcat, bind debug flags to BuildConfig.DEBUG, and programmatically invalidate legacy tokens on V4.9.40 upgrade.
- **Rationale**: `StravaHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`), `WorkoutRepository` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`), `StravaUploader` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`), `StravaEquipmentSynchronizeThread` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSynchronizeThread.java`), `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`), and 11 production classes hardcoding debug constants SHALL eliminate all credential and GPS polyline disclosures from Android logcat, bind debug logging strictly to `BuildConfig.DEBUG`, ensure complete absence of external logging interception, programmatically invalidate legacy Strava credentials on upgrade to V4.9.40 to guarantee revocation of historically exposed tokens, and enforce zero-leak logging via static CI/CD unit tests (ATT-3983, extending `REQ-EXT-009`):
1. *OAuth Credential Sanitization (`REQ-SEC-041.1`)*:
• In `StravaHelper.kt`, `storeJSONData` and token refresh handlers SHALL NOT log raw JSON responses, `access_token` strings, `refresh_token` strings, or full athlete profile JSON payloads.
• Diagnostic logging SHALL be restricted strictly to non-sensitive booleans and metadata (e.g. `hasAccessToken`, `athleteId`, HTTP status codes).
2. *Location & GPS Polyline PII Sanitization (`REQ-SEC-041.2`)*:
• In `WorkoutRepository.kt`, `updateWorkoutInList` SHALL NOT log `updatedWorkout.mapPolyline` or raw GPS coordinate tracks.
• Logging SHALL report only boolean presence (`hasPolyline`) and polyline character length.
3. *HTTP Payload Sanitization in Upload and Sync (`REQ-SEC-041.3`)*:
• In `StravaUploader.kt:254` and `StravaEquipmentSynchronizeThread.java:138`, unconditional logging of raw HTTP response bodies SHALL be removed or replaced with upload IDs and status codes.
4. *Standardization of Build-Scoped Debug Flags (`REQ-SEC-041.4`)*:
• All production classes hardcoding `DEBUG = true` (including `ConfigViewFragment`, `StravaHelper`, `ExportStatusDatabaseManager`, `ExportManager`, `LiveSegmentsRepository`, `SegmentsRepository`, `ExportNotificationManager`, `ControlTrackingViewModel`, `EquipmentDbHelper`, `SportTypeEquipmentLinkManager`, `PebbleService`) SHALL bind `DEBUG` to `BuildConfig.DEBUG` (or `TrainingApplication.getDebug(false)`), allowing R8 to dead-code strip diagnostic logs in release builds.
5. *Mandatory Programmatic Credential Invalidation on V4.9.40 Upgrade (`REQ-SEC-041.5`)*:
• To neutralize any historical refresh tokens leaked into user device logcat buffers prior to V4.9.40, `TrainingApplication` / `StravaHelper` SHALL check a one-time migration key `strava_token_security_invalidated_v4_9_40`. If not set and an active Strava connection exists, the system SHALL asynchronously revoke the existing refresh token with Strava via `StravaDataPurgeManager.purgeAllStravaData(context, alsoRevokeRemote = true)` and set the migration key, requiring athletes to re-authenticate with a fresh, uncompromised token.
6. *Static Lint Guard Test in Build Pipeline (`REQ-SEC-041.6`)*:
• `LogcatPrivacySecurityTest.kt` SHALL execute as a mandatory unit test in `./gradlew testDebugUnitTest`, asserting zero occurrences of `DEBUG = true` outside comments in `app/src/main/` and static regex verification prohibiting sensitive payload variables in `Log.*` invocations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEC-041`, extending `REQ-EXT-009` (`ATT-1078`). Targets `StravaHelper.kt`, `WorkoutRepository.kt`, `StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java`, `TrainingApplication.java`, and `LogcatPrivacySecurityTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3983`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. *Root Reason for Existing Formulation*: Verbose debugging payloads and hardcoded `DEBUG = true` were left over from initial prototyping in 2026-10.
4. *Preservation of Core Invariants*: Strava OAuth flow and sync state machine idempotent transitions remain intact; R8 release stripping enabled; 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete connecting to Strava or refreshing a token,
• *When* inspecting logcat in debug or release builds,
• *Then* zero access tokens, refresh tokens, or raw response JSON payloads are printed.
• *Given* workout caching or export status updates in `WorkoutRepository`,
• *When* inspecting logcat,
• *Then* zero encoded GPS polylines or coordinate arrays are printed.
• *Given* an existing installation with active Strava credentials upgrading to V4.9.40,
• *When* `TrainingApplication` launches,
• *Then* the legacy refresh token is programmatically revoked remotely and cleared locally, ensuring compromised tokens cannot be reused.
• *Given* the source codebase,
• *When* `LogcatPrivacySecurityTest` runs,
• *Then* zero `DEBUG = true` declarations exist in `app/src/main/`.

**Invariants**: OAuth flow idempotency, R8 dead-code stripping, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-SEC-042: BuildConfig Secret Generation Restriction, Keystore Credential Exclusion, Codebase Decoupling, and Default Properties Fallback [Restrict BuildConfig secret generation to legitimate active keys, decouple codebase from dead secrets, exclude release keystore credentials, provide defaults fallback, and enforce zero credential leakage via reflection contract test.]
- **Tests**: password).*`; and (c) all application code compiles cleanly without missing symbol errors.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEC-042`, expanding security hardening initiated in `REQ-SEC-041`. Targets `app/build.gradle`, `secrets.defaults.properties`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, and `BuildConfigSecretsContractTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4004`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `secrets-gradle-plugin:2.0.1` was integrated in commit `0ec8fc29` (ATT-3551) to externalize API keys from source control.
3. *Root Reason for Existing Formulation*: `secrets-gradle-plugin` by default scans `<buildType>.properties`. When `release.properties` was created to hold release signing keys for Gradle `signingConfigs.release`, the plugin automatically converted all properties in `release.properties` (`keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`) into public static fields in `BuildConfig.java`. Furthermore, historical service keys (Runkeeper, TrainingPeaks, Dropbox app secret) were retained in `secrets.properties` and injected into the build even though their corresponding integrations were decommissioned or switched to PKCE.
4. *Preservation of Core Invariants*: Legitimate integrations (Google Maps rendering, Strava OAuth token exchange via `StravaHelper`, Dropbox backup/export authorization via `DropboxClientFactory` with `DROPBOX_APP_KEY`) MUST continue to function with zero disruption. Release APK signing MUST continue to operate without modification.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a compiled APK or `BuildConfig` class in any build variant (debug or release),
• *When* inspecting `BuildConfig.java` or dexed classes,
• *Then* no keystore credentials (`keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`) or dead client secrets (`DROPBOX_APP_SECRET`, `RUNKEEPER_*`, `TRAININGPEAKS_*`) are present.
• *Given* the application source code in `app/src/main`,
• *When* compiling debug or release variants,
• *Then* compilation succeeds with zero errors, confirming no code references pruned BuildConfig fields.
• *Given* a fresh clone of the repository without a local `secrets.properties` file,
• *When* `./gradlew assembleDebug` or `./gradlew testDebugUnitTest` is executed,
• *Then* the build succeeds without error using fallback defaults from `secrets.defaults.properties`.
• *Given* an active Strava or Dropbox workflow,
• *When* authorizing Strava or uploading to Dropbox,
• *Then* `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `DROPBOX_APP_KEY` remain accessible and functional.

**Invariants**: Legitimate API integration functionality, release signing capability, and 100% clean-room test pass rate MUST NOT be broken.
- **Implementation**: store
- **Rationale**: The build configuration (`app/build.gradle`), version-controlled default properties file (`secrets.defaults.properties`), and continuous integration workflows SHALL configure `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` to exclude all keystore and signing credentials, prune decommissioned and dead third-party client secrets from compilation into `BuildConfig.java` and APK DEX classes, guarantee that application source code does not reference pruned secrets, supply safe build-time defaults strictly limited to required keys for clean clones and CI environments, and enforce zero credential exposure via static reflection contract unit tests (ATT-4004, extending `REQ-SEC-041`):
1. *Keystore and Signing Property Exclusion (`REQ-SEC-042.1`)*:
• The `secrets` configuration block in `app/build.gradle` SHALL include `ignoreList.add("key.*")` (or explicit properties `keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`).
• Keystore paths, key aliases, and signing passwords read from `release.properties` (or any `<buildType>.properties`) SHALL NEVER be compiled into `BuildConfig.java`, generated as resource strings, or bundled into APK DEX files.
2. *Pruning Decommissioned and Unused Client Secrets & Codebase Decoupling (`REQ-SEC-042.2`)*:
• The build configuration SHALL configure `secrets.ignoreList` to exclude decommissioned third-party service credentials (specifically `RUNKEEPER.*`, `TRAININGPEAKS.*`) and unused SDK secrets (`DROPBOX_APP_SECRET`).
• The application source code (`app/src/main`) SHALL NOT reference `BuildConfig.DROPBOX_APP_SECRET`, `BuildConfig.RUNKEEPER_*`, or `BuildConfig.TRAININGPEAKS_*`. (Dropbox integration in `DropboxSettingsDialog.kt` strictly relies on PKCE via `BuildConfig.DROPBOX_APP_KEY`, requiring zero app secret).
• The system SHALL guarantee that only active, legitimate keys (`MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `DROPBOX_APP_KEY`) are generated into `BuildConfig` or injected into `AndroidManifest.xml`.
3. *Tracked Default Properties Fallback File (`REQ-SEC-042.3`)*:
• The project root SHALL provide a version-controlled default properties file `secrets.defaults.properties` defining non-empty placeholder fallback values strictly limited to the four required keys: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `DROPBOX_APP_KEY`.
• `secrets.defaults.properties` SHALL strictly omit `DROPBOX_APP_SECRET`, `RUNKEEPER_*`, and `TRAININGPEAKS_*` to eliminate deceptive dead configuration entries.
• `app/build.gradle` SHALL bind `defaultPropertiesFileName = "secrets.defaults.properties"` in the `secrets` block so that clean checkouts and CI environments build successfully without failing when local `secrets.properties` is absent.
4. *CI Workflow and Secrets Sanitization (`REQ-SEC-042.4`)*:
• Continuous integration workflows (`.github/workflows/android-branch-build.yml` and `android-release.yml`) and documentation (`docs/ci-secrets.md`) SHALL remove obsolete `RUNKEEPER_*`, `TRAININGPEAKS_*`, and `DROPBOX_APP_SECRET` environment variables and secrets, preventing dead secret injection during automated builds.
5. *Reflection Contract Unit Test & Compilation Guard (`REQ-SEC-042.5`)*:
• The test suite SHALL include an automated unit test (`BuildConfigSecretsContractTest.kt`) that inspects `BuildConfig::class.java.declaredFields` via reflection in `./gradlew testDebugUnitTest` to assert: (a) required fields exist: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`; (b) forbidden fields do NOT exist: `RUNKEEPER_*`, `TRAININGPEAKS_*`, `DROPBOX_APP_SECRET`, and any field matching `(?i).*key(alias

### REQ-STB-017: MainActivity Launch Intent Lifecycle Sanitization, Transient Extra Consumption & Safe Resumption Guards [`TST-STB-017`]
- **Tests**: `MainActivityWithNavigation.kt`
- **Implementation**: MainActivity launch intent lifecycle sanitization, transient extra consumption, and defensive resumption guards.
- **Rationale**: The system SHALL guarantee that launch intent extras delivered to `MainActivityWithNavigation` are processed strictly on initial activity creation (`savedInstanceState == null`) or explicit new intent delivery (`onNewIntent`), transient intent extras are consumed upon processing, and tracking resumption via `chooseResume()` is defensively guarded against invalid state transitions and process resurrection (ATT-4001, amending `REQ-STB-003`, `REQ-UI-124`, and `REQ-UI-345`):
1. *Activity Recreation Intent Isolation (`MainActivityWithNavigation.kt`)*:
• In `MainActivityWithNavigation.onCreate()`, `handleIntent(intent)` SHALL be invoked if and only if `savedInstanceState == null`.
• On activity recreation (configuration changes such as rotation, dark mode toggle, or multi-window resize, and process death resurrection where `savedInstanceState != null`), `savedInstanceState` SHALL retain full authority over active screen restoration (`mSelectedFragmentId`), and stale launch intent extras from `ActivityManagerService` SHALL NOT be re-evaluated.
• Intent delivery while the activity is alive SHALL route through `onNewIntent(intent)`, which SHALL call `setIntent(intent)` and invoke `handleIntent(intent)`.
2. *Transient Extra Consumption & Intent Synchronization (`MainActivityWithNavigation.kt`)*:
• In `handleIntent(intent)`, once intent extras are processed, they SHALL be immediately consumed and stripped from the active intent:
- Handling `EXTRA_RESUME_INTERRUPTED_WORKOUT` SHALL remove `EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, and `EXTRA_DRAWER_ITEM_ID`.
- Handling `EXTRA_DRAWER_ITEM_ID` SHALL remove `EXTRA_DRAWER_ITEM_ID`.
- Handling `SELECTED_FRAGMENT` SHALL remove `SELECTED_FRAGMENT`.
- Handling `EXTRA_FILTER_CRITICAL_BATTERY` SHALL remove `EXTRA_FILTER_CRITICAL_BATTERY`.
• At the conclusion of `handleIntent(intent)`, `MainActivityWithNavigation` SHALL invoke `setIntent(intent)` to ensure the sanitized intent state is synchronized with the activity.
3. *Defensive Tracking Resumption Guards (`MainActivityWithNavigation.kt`)*:
• In `MainActivityWithNavigation.chooseResume()`, before setting `TrainingApplication.setResumeFromCrash(true)` or broadcasting `TrainingApplication.REQUEST_START_TRACKING`:
- The system SHALL verify `!TrainingApplication.isTracking()`. If tracking is already active, `chooseResume()` SHALL log a warning and return immediately without broadcasting `REQUEST_START_TRACKING`.
- The system SHALL verify `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`. If no unfinalized workout exists in SQLite, `chooseResume()` SHALL log a warning and return immediately without broadcasting `REQUEST_START_TRACKING`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-STB-017` under Stability Epic `ATT-235`, amending `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*), `REQ-UI-124` (*MainActivity Kotlin Migration*), and `REQ-UI-345` (*Workout Recovery Dialog & Start Interception Guard*). Targets `MainActivityWithNavigation.kt`.
2. *Historical Origin & Commit Trace*: Independent whole-app code review (branch `feature/ATT-3921` @ `0234bfc0`). Legacy Java implementation called `handleIntent(getIntent())` unconditionally in `onCreate()`; preserved during Kotlin migration in commit `0668dda6` (`ATT-657`/`ATT-658`). `hasUnfinishedWorkout()` was introduced in `fe4e24f1` (`ATT-3165` / `REQ-UI-345`).
3. *Root Reason for Existing Formulation*: Historical `handleIntent` assumed launch intents represented immediate one-time user actions without considering Android framework task intent persistence across activity recreations (`savedInstanceState != null`) and process resurrecting from Recent Tasks. Extras were treated as read-only rather than consumed event payloads.
4. *Preservation of Core Invariants*: Legitimate cold start from interrupted workout notifications (`REQ-STB-003.2`), low-battery notifications, dynamic drawer deep-links, `StartOrResumeDialog` prompting on normal cold starts (`REQ-STB-003.3`), and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an interrupted tracking notification tapped on cold start (`savedInstanceState == null`),
• *When* `onCreate()` executes,
• *Then* `handleIntent()` consumes the extras, cancels the notification, navigates to `drawer_start_tracking`, and initiates resume.
• *Given* an activity launched from a notification and subsequently recreated with `savedInstanceState != null` (e.g. dark mode toggle or process recreation),
• *When* `onCreate()` executes,
• *Then* `handleIntent()` is bypassed, the restored screen in `savedInstanceState` is preserved, and tracking is not auto-resumed.
• *Given* `chooseResume()` invoked while tracking is already active (`isTracking() == true`),
• *When* `chooseResume()` executes,
• *Then* `REQUEST_START_TRACKING` is NOT broadcast.
• *Given* `chooseResume()` invoked when no unfinished workout exists in SQLite (`hasUnfinishedWorkout() == false`),
• *When* `chooseResume()` executes,
• *Then* `REQUEST_START_TRACKING` is NOT broadcast.
• *Given* a new intent delivered while the activity is running,
• *When* `onNewIntent(intent)` executes,
• *Then* `handleIntent(intent)` processes and strips the extras, updating `setIntent(intent)`.

**Invariants**: Normal cold start workout recovery dialog, notification intent routing, `onNewIntent` dispatch, and 100% clean-room test suite pass rate MUST NOT be broken.

### REQ-STB-018: WorkoutRepository Concurrency Invariants, Conflated Dirty-Flag Reloads, Stale Snapshot Defense & Exception Shielding [`TST-STB-018`]
- **Tests**: `WorkoutRepository.kt`
- **Implementation**: Enforce WorkoutRepository concurrency invariants: conflated per-workout dirty-flag reloads, atomic loading guard, stale-snapshot & deletion-resurrection defense, pure CAS updates, and defensive deletion reporting with guaranteed Idle progress reset.
- **Rationale**: The system SHALL guarantee thread safety, state synchronization, and failure isolation within `WorkoutRepository` across background reloads, progressive history streaming, memory caching, and session deletion (ATT-4002, Implemented in: ATT-4002, Status: Planned, amending `REQ-STB-013`):
1. *Conflated Per-Workout Reload Pipeline (`reloadWorkoutData` - `REQ-STB-018.1`)*:
• `WorkoutRepository` SHALL implement a conflated, thread-safe reload pipeline for individual sessions (`reloadWorkoutData(workoutId)`).
• When a reload request arrives for a `workoutId` that is already actively reloading, the repository SHALL mark the workout ID as dirty (`dirtyReloadIds.add(workoutId)`) rather than dropping the request.
• The active reload loop SHALL re-evaluate the dirty set upon completing a fetch pass. If marked dirty, it SHALL perform a sequential re-query of SQLite, guaranteeing that the final in-memory state converges to the latest database state.
• Session finalization transitions (`freshWorkoutData.finished == true`) SHALL deterministically trigger downstream lifecycle observers (`PeriodsRepository.onWorkoutFinished`, `WorkoutClusterEngine.onWorkoutFinished`), guaranteeing that tracking completion events (`TRACKING_FINISHED_INTENT`) arriving during active reloads are never dropped.
2. *Thread-Safe Progressive History Streaming & Stale Snapshot Defense (`loadAllWorkouts` - `REQ-STB-018.2`)*:
• `WorkoutRepository.loadAllWorkouts()` SHALL guard against concurrent invocations using an atomic flag (`AtomicBoolean` or `Mutex`), preventing check-then-act race conditions between parallel coroutines.
• Progressive and terminal emissions of loaded batches into `_allWorkouts` SHALL merge safely with active in-memory state without resurrecting workouts deleted in memory (`deleteWorkout`) during the cursor scan.
• Following the cursor iteration loop, `loadAllWorkouts()` SHALL execute an unconditional terminal emission to publish all accumulated sessions, guaranteeing that remaining partial batches are never lost when intermediate rows throw extraction exceptions.
• Step 3 snapshot-to-domain mapping (`mapper.fromSnapshot`) and export status resolution SHALL be encapsulated in defensive try/catch blocks, safely skipping corrupted rows without crashing the stream.
3. *Pure Functional `MutableStateFlow.update` CAS Invariants (`REQ-STB-018.3`)*:
• All transformation blocks passed to `_allWorkouts.update` SHALL be strictly pure functions with zero side effects.
• Launching coroutines (`launch(Dispatchers.IO)`), executing blocking database queries, or mutating captured external closure variables inside CAS retry blocks is strictly PROHIBITED.
• In `updateWorkoutInMemory(workoutId, block)`, if the session is absent from `_allWorkouts.value`, any background database retrieval MUST occur asynchronously outside the `_allWorkouts.update` lambda.
• In `saveWorkout`, snapshotting the previous state model for surgical notifications MUST occur outside `_allWorkouts.update`.
4. *Accurate Deletion Reporting & Guaranteed Progress Teardown (`deleteWorkout` - `REQ-STB-018.4`)*:
• `WorkoutRepository.deleteWorkout(id)` SHALL post `deleteFinishedEvent.postValue(Pair(id, true))` if and only if database deletion succeeds.
• If database deletion fails (`deletionHelper.deleteWorkout(id) == false`) or encounters an unhandled runtime/SQLite exception, `deleteFinishedEvent` SHALL post `Pair(id, false)`.
• The entire body of `deleteWorkout(id)` SHALL be enclosed in a `try ... finally` block guaranteeing that `_deletionProgress.postValue(DeletionProgress.Idle)` executes under all exit paths, preventing stuck progress dialogs.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-STB-018` under Epic `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*), amending `REQ-STB-013` (*Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection*). Targets `WorkoutRepository.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (`ATT-2309`, commit `3e710f3c`) and Sprint 2026-38 (`ATT-318` / `ATT-669`, commit `b7e305e2`). Discovered during independent whole-app architecture review (`feature/ATT-3921` @ `0234bfc0`), tracked in `ATT-4002`.
3. *Root Reason for Existing Formulation*: `reloadWorkoutData` used `reloadingWorkoutIds.add(workoutId)` to throttle rapid broadcast bursts from spawning parallel database queries on `Dispatchers.IO`, dropping subsequent reloads without scheduling a retry. `loadAllWorkouts` used `_allWorkouts.value = allLoadedWorkouts.toList()` to pump batches progressively to the UI, overwriting concurrent deletions and edits. `updateWorkoutInMemory` launched background database fetching inside `_allWorkouts.update` assuming a simple lock rather than a CAS retry loop. `deleteWorkout` duplicated `postValue(Pair(id, true))` on both branches without `try ... finally`.
4. *Preservation of Core Invariants*: Monotonic forward-only cursor traversal (`c.moveToNext()`), vectorized batch queries (`extrema`, `stravaData`, `clusterNames`, `laps`), single source of truth in `_allWorkouts`, query deduplication under burst broadcasts, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout session undergoing active `reloadWorkoutData(workoutId)`,
• *When* a subsequent reload notification (`TRACKING_FINISHED_INTENT`) arrives before the active reload completes,
• *Then* the workout ID is flagged dirty, a sequential re-query occurs, the final in-memory state reflects `finished == true`, and `PeriodsRepository.onWorkoutFinished` / `WorkoutClusterEngine.onWorkoutFinished` are invoked.
• *Given* `loadAllWorkouts()` actively streaming history from SQLite,
• *When* a workout is deleted via `deleteWorkout(id)` during cursor iteration,
• *Then* subsequent progressive emissions and the final terminal emission do NOT resurrect the deleted workout in `allWorkouts.value`.
• *Given* a database where an intermediate row throws an extraction exception during cursor reading,
• *When* `loadAllWorkouts()` executes,
• *Then* the exception is caught and logged, remaining rows continue to stream, and an unconditional terminal emission guarantees all valid rows including the final partial batch are published to `allWorkouts`.
• *Given* `loadAllWorkouts()` actively executing,
• *When* a concurrent invocation occurs,
• *Then* the second invocation is atomically rejected without spawning parallel cursor traversals.
• *Given* concurrent updates to `_allWorkouts` triggering CAS retries,
• *When* `updateWorkoutInMemory()` or `saveWorkout()` executes,
• *Then* zero coroutines are launched, zero I/O is performed, and zero external closure variables are mutated inside the `_allWorkouts.update` block.
• *Given* a workout deletion that fails in the database or throws an exception,
• *When* `deleteWorkout(id)` executes,
• *Then* `deleteFinishedEvent` posts `Pair(id, false)`, and `_deletionProgress` is guaranteed to reset to `DeletionProgress.Idle`.

**Invariants**: Monotonic forward-only cursor traversal (`REQ-STB-013`), single source of truth in `_allWorkouts`, lifecycle transition guarantee, pure CAS retry invariant, progress dialog cleanliness, clean-room 100% test pass rate.

### REQ-STB-019: MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Execution [`TST-STB-019`]
- **Tests**: `MainActivityWithNavigation.kt`, `TrainingApplication.java`
- **Implementation**: Enforce MainActivity service unbinding and runnable cancellation, lifecycle coroutine scoping, in-ride battery state retention across rotations, off-main-thread DB access, and Play Services dialog leak defense.
- **Rationale**: The system SHALL guarantee complete service binding lifecycle safety, structured coroutine lifecycle scoping, persistent in-ride battery alert deduplication across configuration changes, safe dialog lifecycle management, and non-blocking asynchronous execution of SQLite queries across `MainActivityWithNavigation` and `TrainingApplication` (ATT-4020, Implemented in: ATT-4020, Status: Verified, amending `REQ-UI-356`):
1. *Unconditional Service Unbinding & Handler Cleanup (`MainActivityWithNavigation.kt`, `REQ-STB-019.1`)*:
• In `MainActivityWithNavigation.onDestroy()`, `disconnectFromBANALService()` SHALL be invoked unconditionally (eliminating the `!isChangingConfigurations` bypass), ensuring `mBanalConnection` is never leaked to `ActivityManagerService` across activity recreations or finishes.
• In `MainActivityWithNavigation.onDestroy()`, any scheduled delayed disconnect runnable (`mDisconnectFromBANALServiceRunnable`) SHALL be explicitly removed from `mHandler` via `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)`, preventing destroyed activity instances from being retained in the Android main looper message queue.
2. *Structured Coroutine Scoping via lifecycleScope (`MainActivityWithNavigation.kt`, `REQ-STB-019.2`)*:
• In `MainActivityWithNavigation`, unmanaged `CoroutineScope(Dispatchers.IO).launch` calls in `checkSensorBatteryAfterWorkout`, `checkSensorBatteryOnStart`, and `startInRideBatteryMonitoring` SHALL be replaced with `lifecycleScope.launch(Dispatchers.IO)` (or structured lifecycle-bound job management), guaranteeing that asynchronous jobs automatically cancel upon activity destruction and never retain outer activity references.
3. *In-Ride Battery Alert State Retention & Screen-Off Tracking Collection (`MainActivityWithNavigation.kt`, `REQ-STB-019.3`)*:
• In-ride battery alert deduplication state (`mInRideAlertedSensorIds`, `mInRideCriticalAlertedSensorIds`) SHALL be retained across activity configuration changes (e.g. via retained state holder, ViewModel, or repository cache) rather than resetting on activity recreation, preventing duplicate warning or critical alert snackbars upon screen rotation.
• In-ride battery monitoring collector (`startInRideBatteryMonitoring`) SHALL remain active during active tracking (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`) even when `MainActivityWithNavigation` is paused (`onPause()`) during screen-off operation, stopping only upon explicit workout completion (`TrackingMode.STOPPED` / `onDestroy`).
4. *Off-Main-Thread SQLite Database Execution (`MainActivityWithNavigation.kt`, `TrainingApplication.java`, `REQ-STB-019.4`)*:
• `TrackingViewsDatabaseManager.getAllFilterData(devicesDatabaseManager)` invoked inside `mBanalConnection.onServiceConnected()` SHALL execute asynchronously on a background dispatcher (`Dispatchers.IO`), eliminating main-thread SQLite queries during service connection.
• `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` in `checkUnfinishedWorkout()` (invoked from `onResume()`) and in `chooseResume()` SHALL execute asynchronously on `Dispatchers.IO`, updating UI and displaying `StartOrResumeDialog` safely on the main thread.
• Dialog actions `chooseStart()` and `chooseResume()` calling `discardOrFinishUnfinishedWorkout()` and `hasUnfinishedWorkout()` SHALL dispatch SQLite write/read operations to `Dispatchers.IO`.
• `TrainingApplication.trackLocation()` (invoked from `checkGpsEnabledIfPermitted()` in `MainActivityWithNavigation.onCreate()`) SHALL be dispatched off the main thread or execute asynchronously, ensuring zero synchronous database queries block Activity startup.
5. *Google Play Services Dialog Window Leak Prevention (`MainActivityWithNavigation.kt`, `REQ-STB-019.5`)*:
• Any `Dialog` returned by `GooglePlayServicesUtil.getErrorDialog()` displayed during `onCreate()` SHALL be tracked and safely dismissed in `onDestroy()` (or managed via a `DialogFragment`), preventing Android `WindowLeaked` exceptions upon activity rotation or termination.
6. *Preservation of System Invariants (`REQ-STB-019.6`)*:
• `MainActivityWithNavigation` MUST continue to implement `BANALService.GetBanalServiceInterface` and provide `getBanalServiceComm()`.
• Battery threshold invariants (20% warning, 10% critical, `REQ-UI-356`) and single-alert session deduplication remain strictly preserved.
• Zero SQLite schema migrations.
• 100% clean-room unit test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-STB-019` under Stability Epic `ATT-235`, amending `REQ-UI-356` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars*). Targets `MainActivityWithNavigation.kt` and `TrainingApplication.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.1 (`ATT-3313`, `ATT-3451`) implemented in-ride battery alert snackbars; legacy service binding predates `BANALServiceRepository`. Discovered during whole-app architecture review (`feature/ATT-3921` @ `0234bfc0`), tracked in `ATT-4020`.
3. *Root Reason for Existing Formulation*: In-ride battery alerting was placed directly in `MainActivityWithNavigation` for convenient HUD snackbar access; `isChangingConfigurations` guard attempted to optimize service rebinding across rotations but violated Android's requirement that destroying activities unbind their registered connections. Database queries were executed synchronously on the main thread for legacy convenience.
4. *Preservation of Core Invariants*: 20% warning / 10% critical thresholds, session deduplication, `GetBanalServiceInterface` contract, non-blocking UI responsiveness, and 100% clean-room test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `MainActivityWithNavigation` undergoing activity recreation due to screen rotation (`isChangingConfigurations == true`),
• *When* `onDestroy()` executes,
• *Then* `disconnectFromBANALService()` unbinds the service and `mHandler.removeCallbacks` cancels the delayed disconnect runnable with zero leaked connections.
• *Given* an active tracking session with a sensor reporting 15% battery triggering a warning snackbar,
• *When* the athlete rotates the device (configuration change),
• *Then* deduplication state is preserved and duplicate alert snackbars are NOT re-emitted.
• *Given* an active workout tracking session with the screen turned off (`onPause()` called),
• *When* a sensor battery drops to critical level ($\le 10\%$),
• *Then* battery monitoring remains active and records the critical drop.
• *Given* `MainActivityWithNavigation` cold start, resume, and service connection,
• *When* SQLite queries (`getAllFilterData`, `hasUnfinishedWorkout`, `trackLocation`) execute,
• *Then* all database operations run off the main thread on `Dispatchers.IO`.
• *Given* a Google Play Services error dialog shown on screen,
• *When* the activity is destroyed or rotated,
• *Then* the dialog is safely dismissed without `WindowLeaked` exceptions.

**Invariants**: Clean-room 100% test pass rate, no main-thread SQLite queries, no leaked service connections or windows, and no duplicate in-ride alerts.

### REQ-TST-001: Deterministic JVM Test State Isolation, Production Catch Workaround Excision, Strict Test Assertions, and Exporter Unit Test Coverage []
- **Tests**: x.isNotEmpty()`) in place of deterministic JUnit assertions.
• In `BatteryRemainingTimeFormatterTest` and `BatteryDeviceTest`, assertions SHALL strictly verify expected deterministic output strings (`assertEquals("Charging", result)` or specific battery values) matching test fixtures.
3. *Excision of Test-Only Catch Workarounds in Production Code (`REQ-TST-001.3`)*:
• Production code in `TrainingApplication.java` (`setManualPauseLock`, `setAutoPaused`), `TrackerService.java` (`performStopForeground`, `runOnMainThread`, `handleTrackingAborted`, `notifyTrackingStarted`, `sampleAndWriteToDb`), `WorkoutClusterEngine.kt` (`ensureStartingLocationAnchor`), and `AutoPauseManager.kt` (`try { Log.w(...) } catch (Throwable)`) SHALL NOT catch and swallow `Throwable` solely to tolerate unmocked Android dependencies during JVM test runs.
• Unit test harnesses invoking these components SHALL supply appropriate MockK mocks or Robolectric Android shadows (e.g. for Android `Log`, `Context`, `Handler`, or `Intent`), enabling production methods to safely assume genuine runtime contracts.
4. *Exporter & Cloud Uploader Unit Test Coverage (`REQ-TST-001.4`)*:
• `CSVFileWriter.java` and `DropboxUploader.java` SHALL be accompanied by dedicated unit test suites (`CSVFileWriterTest.kt` and `DropboxUploaderTest.kt`) covering header formatting, sample row serialization, coordinate and sensor decimal precision, token handling, error states, and cancellation responsiveness.
5. *Preservation of System Invariants (`REQ-TST-001.5`)*:
• 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.
• Zero alteration of production runtime behavior, database schemas, or calculation formulas.
• Rule 4 strict code lock: production code modifications occur in Stage 4 following Gate 3 approval.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TST-001` under Epic `ATT-2466` (*Developer Testing Tools*), upholding `docs/coding_standards.md` §5.1. Targets test suites in `app/src/test/` and production classes `TrainingApplication.java`, `TrackerService.java`, `WorkoutClusterEngine.kt`, `AutoPauseManager.kt`, `CSVFileWriter.java`, and `DropboxUploader.java`.
2. *Historical Origin & Commit Trace*: Independent whole-app code review (`0234bfc0`, `feature/ATT-3921`) and follow-ups in `ATT-3980`, `ATT-3984`, and `ATT-4006`.
3. *Root Reason for Existing Formulation*: Developers inserted inline `catch (Throwable ignored)` in production methods as quick fixes when unit tests ran against naked Android classes without `Log`, `Context`, or `Handler` mocks. `Locale.setDefault` was added to formatters without `@After` cleanup.
4. *Preservation of Core Invariants*: 100% clean-room test pass rate across all unit tests, deterministic execution in any locale/timezone, and zero alteration of runtime production behavior.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the full test suite executing in any arbitrary order or host locale/timezone,
• *When* `./gradlew testDebugUnitTest` runs,
• *Then* `Locale.getDefault()` and `TimeZone.getDefault()` remain unpolluted after test execution, and all tests pass with 100% determinism.
• *Given* `BatteryRemainingTimeFormatterTest` and `BatteryDeviceTest`,
• *When* tests execute,
• *Then* assertions strictly evaluate expected values via `assertEquals` without tautological `
- **Rationale**: The automated test infrastructure and production architecture SHALL guarantee deterministic JVM test isolation by restoring mutated global JVM state (`Locale` and `TimeZone`) in test `@After` teardown blocks, eliminate weak non-asserting or tautological assertions in test suites, excise artificial test-only `catch (Throwable)` blocks from production code by equipping unit test suites with proper MockK / Robolectric scaffolding, and establish comprehensive unit test coverage for `CSVFileWriter` and `DropboxUploader` (ATT-4024, Parent Epic ATT-2466, upholding `docs/coding_standards.md` §5.1):
1. *Global JVM State Isolation & Lifecycle Restoration (`REQ-TST-001.1`)*:
• Any test class that mutates global JVM default locale (`Locale.setDefault(...)`) or default timezone (`TimeZone.setDefault(...)`, `ZoneId.of(...)`)—including `IntensityFactorFormatterTest`, `BatteryRemainingTimeFormatterTest`, `BatteryDeviceTest`, `AdvancedTuningAccordionTest`, and `PeriodsAggregationTest`—SHALL capture the baseline JVM locale and timezone before mutation and guarantee their restoration in an `@After` lifecycle method (or dedicated JUnit `@Rule`), ensuring zero cross-test state leakage across test execution order.
2. *Strict & Deterministic Test Assertions (`REQ-TST-001.2`)*:
• Test suites SHALL NOT use Kotlin bare `assert` keywords or tautological conditional checks (`x == "Charging"

### REQ-BLD-003: Manifest Component Security, Intent Filter Scoping & Permission Configuration Hygiene [`TST-BLD-003`]
- **Tests**: `app/src/main/AndroidManifest.xml`, `ManifestSecurityContractTest.kt`
- **Implementation**: Enforce internal encapsulation for ConfigPebbleViewsActivity (exported="false"), harden GpxImportActivity intent filters without host="*" or file scheme, update ACCESS_BACKGROUND_LOCATION comment, and retain largeHeap and build invariants.
- **Rationale**: The application manifest (`app/src/main/AndroidManifest.xml`) SHALL enforce strict component encapsulation, harden external intent filter resolution, maintain Android 14+ background location foreground service recovery requirements, and preserve high-density memory and stability invariants (ATT-4027, extending `REQ-BLD-002` and aligning with `REQ-TRK-015`, `REQ-PER-012`, and `REQ-UI-164`):
1. *Internal Component Encapsulation (`REQ-BLD-003.1`)*:
• `ConfigPebbleViewsActivity` in `app/src/main/AndroidManifest.xml` SHALL be declared with `android:exported="false"`, eliminating unauthenticated external intent invocation surface for internal Pebble view configuration.
2. *GPX Intent Filter Hardening & Path Scoping (`REQ-BLD-003.2`)*:
• `GpxImportActivity` intent filters in `AndroidManifest.xml` SHALL remove invalid and redundant `android:host="*"` attributes and obsolete `android:scheme="file"` tags (blocked on API 24+).
• For generic `application/octet-stream` MIME type data matching in `VIEW` intents, the filter SHALL constrain matching to GPX file extensions (`android:pathPattern=".*\\.gpx"`, `android:pathSuffix=".gpx"`), preventing the application from appearing in system "Open with" choosers for arbitrary non-GPX binary files.
3. *Background Location Permission Documentation Parity (`REQ-BLD-003.3`)*:
• `app/src/main/AndroidManifest.xml` SHALL retain active `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />` required by Android 14+ (`Build.VERSION_CODES.UPSIDE_DOWN_CAKE`) for background `START_STICKY` FGS recovery (`REQ-TRK-015`).
• The misleading legacy manifest comment claiming background location is not necessary SHALL be replaced with accurate documentation explaining the Android 14+ FGS promotion contract.
4. *Preservation of Build & Memory Invariants (`REQ-BLD-003.4`)*:
• The application SHALL retain `android:largeHeap="true"` (`REQ-PER-012`) to support high-density period heatmaps and multi-session GIS vector tracks without OOM, retain local patched core library `local-repo/androidx.core:1.15.0-patched` (`REQ-UI-164`) against defective OEM ROM accessibility crashes, and preserve AGP 9 build property flags until AGP 10.
5. *Automated Manifest Security Contract Verification (`REQ-BLD-003.5`)*:
• `ManifestSecurityContractTest.kt` SHALL verify via automated contract test that `ConfigPebbleViewsActivity` is not exported (`android:exported="false"`), `GpxImportActivity` contains no `host="*"` or `file` scheme, `ACCESS_BACKGROUND_LOCATION` is declared, and `largeHeap="true"` is retained.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-BLD-003` (*Manifest Component Security, Intent Filter Scoping & Permission Configuration Hygiene*), extending `REQ-BLD-002` and aligning with `REQ-TRK-015`, `REQ-PER-012`, and `REQ-UI-164`. Targets `app/src/main/AndroidManifest.xml` and `ManifestSecurityContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent whole-app code review (branch `feature/ATT-3921` @ `0234bfc0`, Oct 2026). Stale background location comment traces to early Android 10 migration; `ConfigPebbleViewsActivity` `android:exported="true"` was added during Android 12 exported-attribute enforcement; `GpxImportActivity` filters originated from early intent filter recipes.
3. *Root Reason for Existing Formulation*: Background location was thought unnecessary because tracking used a visible foreground service notification, prior to Android 14 background restart restrictions. `ConfigPebbleViewsActivity` was exported to satisfy AGP manifest merger without realizing it was internal-only. `file://` and `host="*"` were legacy boilerplate.
4. *Preservation of Core Invariants*: Full Android 14+ `START_STICKY` tracking recovery (`REQ-TRK-015`), GPX route import via content URIs, GIS large heap memory allocation (`REQ-PER-012`), OEM accessibility fix (`REQ-UI-164`), and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `app/src/main/AndroidManifest.xml`,
• *When* audited by `ManifestSecurityContractTest`,
• *Then* `ConfigPebbleViewsActivity` has `android:exported="false"`, `GpxImportActivity` contains no `android:host="*"` or `android:scheme="file"`, and `android:largeHeap="true"` and `ACCESS_BACKGROUND_LOCATION` are present.
• *Given* an external app sharing or opening a `.gpx` file via `content://` URI,
• *When* the intent is resolved,
• *Then* `GpxImportActivity` resolves as an available handler.
• *Given* an arbitrary binary file with MIME type `application/octet-stream` without a `.gpx` extension,
• *When* an `ACTION_VIEW` intent is resolved,
• *Then* `GpxImportActivity` does NOT appear as a matching handler.
• *Given* `TrackerService` restarting on Android 14+ via `START_STICKY`,
• *When* promoting to `FOREGROUND_SERVICE_TYPE_LOCATION`,
• *Then* `ACCESS_BACKGROUND_LOCATION` satisfies system requirements preventing SecurityException crashes.

**Invariants**: FGS Android 14+ location recovery (`REQ-TRK-015`), large heap budget (`REQ-PER-012`), OEM accessibility patch (`REQ-UI-164`), GPX content URI import compatibility, and 100% clean-room test pass rate.

### REQ-TST-002: Deprecation and Modernization of Fragile UI Source-Grep Contract Tests in Favor of Behavioral, Model, and Structural Test Verification [`TST-TST-002`]
- **Tests**: `ReturnNavigationHudContractTest.kt`, `RouteTabbedCleanLayoutTest.kt`, `TurnDirectionIconContractTest.kt`
- **Implementation**: Modernize fragile source-grep contract tests (ReturnNavigationHud, RouteTabbedCleanLayout, TurnDirectionIcon) to use domain models, reflection, and XML DOM evaluation while preserving architectural guards.
- **Rationale**: The automated testing infrastructure SHALL deprecate and eliminate fragile source-code string greps (`File.readText().contains(...)`) asserting on literal Compose tokens, internal formatting substrings, omitted class references, or raw SVG path chunks, replacing them with resilient domain state model assertions, programmatic reflection/ABI checks, and XML DOM vertex parsing, while preserving vital system architectural governance guards (ATT-4028, Parent Epic ATT-2466, updating `docs/coding_standards.md` §5.1):
1. *Deprecation of Source-Code Substring Greps for UI Styling & Tokens (`REQ-TST-002.1`)*:
• Unit test suites SHALL NOT assert on literal Compose design tokens (such as `"RoundedCornerShape(12.dp)"`, `"TTColor.RouteSelected"`), raw emoji literals, or internal layout composition substrings via `File.readText().contains(...)`.
• In `ReturnNavigationHudContractTest`, UI and styling contracts SHALL be verified through semantic domain state models (`ReturnNavigationState`) and behavioral formatters, eliminating brittle source-reading assertions.
2. *Programmatic & Structural Layout Contract Verification (`REQ-TST-002.2`)*:
• Unit test suites verifying architectural cleanup, dead code excision, or component decoupling (e.g. `RouteTabbedCleanLayoutTest`) SHALL NOT grep source code strings for omitted class names (`GatewayFilterChipsRow`, `GatewayDirection`, `RouteCorridorClassifier`) or internal method parameters (`"targetBounds = null"`).
• Excision and decoupling SHALL be verified via programmatic reflection (checking absence/presence of declared fields, methods, and classes), compile-time ABI contracts, or file existence checks.
3. *Geometric & DOM-Based Vector Drawable Verification (`REQ-TST-002.3`)*:
• Vector drawable contract test suites (e.g. `TurnDirectionIconContractTest`) SHALL NOT grep raw SVG path command substrings (`"V20"`, `"h-2"`, `"M6,20"`, `"V20z"`).
• Vector drawables SHALL be verified via XML DOM parsing (`DocumentBuilderFactory`), XML attribute validation, and geometric vertex milestone evaluation (`evaluateAbsoluteMilestones`), asserting grounded baseline coordinates and stem widths mathematically.
4. *Preservation of System Architectural Governance Guards (`REQ-TST-002.4`)*:
• Non-UI system architectural governance and security guard tests—including `TryWithResourcesCursorAuditTest` (resource leak prevention), `ManifestSecurityContractTest` / `ManifestAndBuildConfigContractTest` (manifest security), `ProGuardRulesContractTest` (obfuscation), `TranslationParityTest` (localization), and Rule 34 empty-lambda guards—SHALL remain preserved and distinct from prohibited source-text UI grep.
5. *Preservation of System Invariants (`REQ-TST-002.5`)*:
• 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.
• Zero alteration to production runtime UI layouts, visual appearance, or business logic.
• Rule 4 strict code lock: production code modifications occur in Stage 4 following Gate 3 approval.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TST-002` under Epic `ATT-2466` (*Developer Testing Tools*), updating `docs/coding_standards.md` §5.1. Targets `ReturnNavigationHudContractTest.kt`, `RouteTabbedCleanLayoutTest.kt`, and `TurnDirectionIconContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent whole-app code review (branch `feature/ATT-3921` @ `0234bfc0`, Oct 2026), `ATT-2564` / `ATT-4015` (ReturnNavigation HUD), and `ATT-1400` / `ATT-3678` (Turn direction icons).
3. *Root Reason for Existing Formulation*: Reading source files as plain strings was introduced as a quick surrogate for Compose UI testing without requiring Android instrumentation or Robolectric test runners. Over time, these tests froze whitespace, formatting, and literal shape calls, causing false-positive failures during innocent refactors.
4. *Preservation of Core Invariants*: Component behavior, visual styling, icon symmetry, and 100% clean-room test pass rate remain strictly verified through resilient domain and reflection checks.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ReturnNavigationHudContractTest`,
• *When* executed,
• *Then* tests verify state model properties, formatting, and metrics behavior without reading `ReturnNavigationHud.kt` as raw text.
• *Given* `RouteTabbedCleanLayoutTest`,
• *When* executed,
• *Then* tests verify excision and clean presentation via reflection and file existence without string grep on `RouteTabbedScreen.kt`, `RouteList.kt`, or `RouteItem.kt`.
• *Given* `TurnDirectionIconContractTest`,
• *When* executed,
• *Then* vector drawable grounding and stem dimensions are verified via XML DOM and geometric milestones without substring searches for SVG path commands.
• *Given* architectural governance tests (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`),
• *When* executed,
• *Then* all guards pass with zero regressions.

**Invariants**: Zero alteration to production UI/logic, architectural safety guards preserved, 100% clean-room test pass rate.

### REQ-ARC-002: Exception Handling Hygiene, Fatal Error Propagation, Diagnostic Logging & Thread Interrupt Preservation [`TST-ARC-002`]
- **Tests**: `FitFileWriter.java`, `PeriodsRepository.kt`, `StravaUploader.kt`, `MyBanalDebugHelper.java`, `ForkNavigationRepository.kt`, `WorkoutClusterDatabaseManager.kt`, `MigrationEngine.kt`, `ExceptionHandlingHygieneTest.kt`
- **Implementation**: Enforce exception handling hygiene across FitFileWriter, PeriodsRepository, StravaUploader, MyBanalDebugHelper, ForkNavigationRepository, and WorkoutClusterDatabaseManager: eliminate silent catches, replace catch(Throwable) with catch(Exception), preserve thread interrupts, and remove printStackTrace().
- **Rationale**: The application architecture and source code SHALL enforce disciplined exception handling across main export, repository, database, and navigation components (`FitFileWriter`, `PeriodsRepository`, `StravaUploader`, `MyBanalDebugHelper`, `ForkNavigationRepository`, `WorkoutClusterDatabaseManager`, `MigrationEngine`), eliminating silent/empty catch blocks, strictly avoiding `catch (Throwable)` on recoverable logic to ensure fatal JVM errors and coroutine cancellation propagate naturally, logging non-fatal fallback conditions via `Log.w`/`Log.e`, preserving thread interrupt state on `InterruptedException`, and eliminating unsafe force-unwraps (ATT-4030, parent epic ATT-431, extending `REQ-ARC-001` and `REQ-TST-001`):
1. *Silent/Empty Catch Elimination & Diagnostic Warning Logging (`REQ-ARC-002.1`)*:
• In `FitFileWriter.java`, empty catch blocks around timestamp and distance conversions SHALL be replaced with diagnostic `Log.w(TAG, ...)` logging before applying fallback sentinels.
• In `StravaUploader.kt`, silent `catch (_: Exception)` blocks in `parseTimeToEpochSeconds` SHALL narrow to `DateTimeParseException` / `ParseException` and emit `Log.w` diagnostics before returning `null`.
• In `WorkoutClusterDatabaseManager.kt`, silent `catch (_: Exception)` in JSON deserialization SHALL narrow to `JSONException` and emit `Log.w` before returning safe fallback `emptyList()`.
• In `MigrationEngine.kt` and `BackupManager.kt`, resource cleanup and close catches SHALL log diagnostic warnings instead of swallowing errors silently.
2. *Prohibition of `catch (Throwable)` on Recoverable Logic & Fatal Error Escalation (`REQ-ARC-002.2`)*:
• In `FitFileWriter.java`, `PeriodsRepository.kt`, and `MigrationEngine.kt`, all instances of `catch (Throwable)` around standard operational or cleanup blocks SHALL be replaced with `catch (Exception)`.
• Fatal JVM errors (`OutOfMemoryError`, `VirtualMachineError`, `LinkageError`) and coroutine cancellation signals MUST NOT be caught or suppressed by standard recovery blocks.
3. *Thread Interrupt Preservation on Backoff Sleep (`REQ-ARC-002.3`)*:
• In `StravaUploader.kt` (`sleepStrategy`), when `InterruptedException` is caught during exponential backoff sleep, the method SHALL re-assert thread interruption via `Thread.currentThread().interrupt()`, preserving cooperative thread cancellation semantics across background executors.
4. *Standard Android Logging & Unsafe Force-Unwrap Elimination (`REQ-ARC-002.4`)*:
• In `MyBanalDebugHelper.java`, legacy `e.printStackTrace()` calls SHALL be replaced with standard Android logging `Log.e(TAG, "...", e)`.
• In `ForkNavigationRepository.kt`, unsafe force-unwraps (`!!`) on mutable state `lastSearchPos` SHALL be replaced with thread-safe local variable bindings (`val pos = lastSearchPos; if (pos != null)`).
5. *Behavioral Unit Testing & Exception Hygiene Verification (`REQ-ARC-002.5`)*:
• Automated unit tests in `ExceptionHandlingHygieneTest.kt` SHALL verify: (a) parse error fallback behavior in `StravaUploader.parseTimeToEpochSeconds`, (b) fallback behavior on malformed JSON in `WorkoutClusterDatabaseManager.deserializePreviewPaths`, (c) `InterruptedException` interrupt flag preservation during backoff sleep, (d) safe null handling in `ForkNavigationRepository`, and (e) architectural policy verification asserting zero `e.printStackTrace()` calls in audited production source files.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-ARC-002` under Epic `ATT-431` (*Clean Code*), extending `REQ-ARC-001` (*Safe Enum Deserialization*) and `REQ-TST-001` (*Deterministic JVM Test State Isolation*). Targets `FitFileWriter.java`, `PeriodsRepository.kt`, `StravaUploader.kt`, `MyBanalDebugHelper.java`, `ForkNavigationRepository.kt`, `WorkoutClusterDatabaseManager.kt`, `MigrationEngine.kt`, and `ExceptionHandlingHygieneTest.kt`.
2. *Historical Origin & Commit Trace*: Whole-app independent code review (`feature/ATT-3921` @ `0234bfc0`, Oct 2026). Legacy export utilities and repositories accumulated blanket catch blocks and silent swallows during incremental feature additions.
3. *Root Reason for Existing Formulation*: Blanket catches and silent swallows were added as defensive stopgaps to prevent background tasks or export jobs from terminating on unexpected inputs, but created blind spots for data parsing corruptions and fatal JVM memory faults.
4. *Preservation of Core Invariants*: Fallback sentinels (returning `emptyList()`, `null`, `emptyMap()`, or default timestamps) are strictly preserved so export engines, navigation, and period views remain 100% resilient and do not crash on malformed inputs. Clean-room test suite pass rate remains 100%.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an invalid or corrupt date string passed to `StravaUploader.parseTimeToEpochSeconds`,
• *When* parsed,
• *Then* `Log.w` is logged, `null` is returned without throwing unhandled exceptions, and no silent catch suppresses root causes.
• *Given* a thread interrupted during `StravaUploader` backoff sleep,
• *When* `InterruptedException` is handled,
• *Then* `Thread.currentThread().isInterrupted` remains `true`.
• *Given* malformed JSON in `WorkoutClusterDatabaseManager.deserializePreviewPaths`,
• *When* deserialized,
• *Then* a warning is logged and `emptyList()` is safely returned.
• *Given* `ForkNavigationRepository` when `lastSearchPos` is null,
• *When* queried,
• *Then* execution completes gracefully without `NullPointerException` or force-unwraps (`!!`).
• *Given* audited main production sources,
• *When* audited by `ExceptionHandlingHygieneTest`,
• *Then* zero `e.printStackTrace()` calls exist and zero `catch (Throwable)` blocks mask fatal errors in audited classes.

**Invariants**: Graceful fallback degradation (no crashes on corrupt data), thread interrupt preservation, and 100% clean-room test pass rate.

### REQ-ARC-003: God Class Splitting, Dead Code Elimination, Thread-Safe Visibility & API Modernization [`TST-ARC-003`]
- **Tests**: `TrainingApplication.java`, `MainActivityWithNavigation.kt`, `AntStartupCheckContractTest.kt`, `DeadCodeEliminationContractTest.kt`
- **Implementation**: Eliminate dead methods and legacy dialog classes, ensure volatile visibility on cResumeFromCrash, modernize lock-screen window APIs, and scope Compose previews to private.
- **Rationale**: The application architecture and source code SHALL eliminate confirmed dead methods, unused state variables, and unreferenced legacy dialog/view classes, enforce volatile memory visibility on static cross-thread state in `TrainingApplication.java`, modernize deprecated window lock flags in `MainActivityWithNavigation.kt`, scope top-level Compose `@Preview` composables to `private` visibility, and verify all excisions via programmatic reflection and file existence contracts (ATT-4031, parent epic ATT-431, extending `REQ-ARC-001` and `REQ-ARC-002`):
1. *Dead Method and Unused State Excision (`REQ-ARC-003.1`)*:
• In `TrainingApplication.java`, dead methods `@Deprecated todo()`, `setIsSegmentListUpdating()`, `isSegmentListUpdating()`, and unused field `mSegmentListUpdating` SHALL be excised.
• In `MainActivityWithNavigation.kt`, uncalled dead methods `checkBatteryOptimizations()` and `showInstallANTShitDialog()` SHALL be excised.
2. *Dead Legacy Class Elimination & Lint Cleanup (`REQ-ARC-003.2`)*:
• Unreferenced legacy Java classes `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, and `InstallANTShitDialog.java` SHALL be deleted from the repository.
• Stale lint issue entries referencing `MultiSelectionSpinner` in `lint-baseline.xml` SHALL be purged.
3. *Thread-Safe Visibility & Concurrency Hardening (`REQ-ARC-003.3`)*:
• In `TrainingApplication.java`, static mutable recovery state `cResumeFromCrash` SHALL be declared `volatile` (`public static volatile boolean cResumeFromCrash`), guaranteeing cross-thread memory visibility between background service dispatchers and UI resume dialogs.
4. *Window Lock API Modernization (`REQ-ARC-003.4`)*:
• In `MainActivityWithNavigation.kt`, deprecated `FLAG_SHOW_WHEN_LOCKED` window flag usage SHALL be modernized using `setShowWhenLocked(true/false)` for API 27+ (`Build.VERSION_CODES.O_MR1`), with legacy fallback to `addFlags`/`clearFlags` for API 24-26.
5. *Compose UI Preview Visibility Scoping (`REQ-ARC-003.5`)*:
• Top-level `@Preview` composable functions across Compose screens SHALL be restricted to `private` visibility, preventing internal test preview code from leaking into public production bytecode.
6. *Dead Code & Visibility Contract Test Verification (`REQ-ARC-003.6`)*:
• Automated contract tests in `DeadCodeEliminationContractTest.kt` SHALL verify via reflection (`Class.forName`, `Modifier.isVolatile`, `declaredFields`, `declaredMethods`) and file existence that excised classes are absent, dead methods are removed, and `cResumeFromCrash` is volatile.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-ARC-003` under Epic `ATT-431` (*Clean Code*), extending `REQ-ARC-001` (*Safe Enum Deserialization*) and `REQ-ARC-002` (*Exception Handling Hygiene*). Targets `TrainingApplication.java`, `MainActivityWithNavigation.kt`, `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, `InstallANTShitDialog.java`, `AntStartupCheckContractTest.kt`, and `DeadCodeEliminationContractTest.kt`.
2. *Historical Origin & Commit Trace*: Whole-app independent code review (`feature/ATT-3921` @ `0234bfc0`, Oct 2026). Dead dialogs originated from early ANT+ service and Eclipse migrations (2014-2017); `AntStartupCheckContractTest` boundary historically anchored to `checkBatteryOptimizations` from commit `420130df`.
3. *Root Reason for Existing Formulation*: Dead classes and methods were retained when screens were migrated to Jetpack Compose to avoid breaking uninspected call graphs or test boundaries.
4. *Preservation of Core Invariants*: Battery optimization prompts remain active in modern Compose onboarding (`PermissionOnboardingSheet.kt`); `AntStartupCheckContractTest` boundary is retargeted to `observeNavigationEvents` preserving 100% cold-start assertions; lock screen behavior is preserved via API 27+ `setShowWhenLocked`; 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* excised classes `SelectSportTypeDialog`, `MultiSelectionSpinner`, `DeviceIdAndNameArrayAdapter`, and `InstallANTShitDialog`,
• *When* probed via reflection and file system,
• *Then* `Class.forName` throws `ClassNotFoundException` and files do not exist on disk.
• *Given* `TrainingApplication`,
• *When* inspected via reflection,
• *Then* methods `todo()`, `setIsSegmentListUpdating()`, and `isSegmentListUpdating()` are absent, and field `cResumeFromCrash` has `Modifier.isVolatile == true`.
• *Given* `MainActivityWithNavigation`,
• *When* inspected,
• *Then* `checkBatteryOptimizations` and `showInstallANTShitDialog` are absent, and window lock configuration invokes `setShowWhenLocked` on API 27+.
• *Given* top-level `@Preview` composables in Compose UI files,
• *When* inspected,
• *Then* all have `private` visibility.
• *Given* `AntStartupCheckContractTest` and the full unit test suite,
• *When* executed,
• *Then* 100% pass rate is maintained.

**Invariants**: Cold-start permission checking, lock screen visibility behavior, and 100% clean-room test pass rate.

### REQ-TST-003: Deterministic Test State Isolation, Scaffolding Parity, Coroutine Teardown & Clean-Room Suite Execution [`TST-TST-003`]
- **Tests**: `HardcodedStringsAndUnitsContractTest.kt`, `AltitudeAwareClusterMatchingTest.kt`, `DevicesTabbedViewModelTest.kt`, `BackupRestoreViewModelLocalRestoreTest.kt`, `BackupRestoreViewModel.kt`, `TrackingTabsViewModelInteropTest.kt`
- **Implementation**: Deterministic full-suite unit test isolation, scaffolding parity, ViewModel dispatcher injection, and coroutine lifecycle teardown hygiene.
- **Rationale**: The automated test infrastructure and ViewModel architectures SHALL eliminate contract collisions with excised dead code, guarantee complete static dependency mocking across clustering engines, enforce isolated preference DataStore injection with stubbed application paths, guarantee deterministic ViewModel background dispatcher execution via `@JvmOverloads` injection, ensure rigorous ViewModel coroutine scope lifecycle teardown in test `@After` blocks to prevent uncaught coroutine exception leakage across JVM test boundaries, and maintain a 100% clean-room test pass rate across the full 3,840+ unit test suite under `./gradlew testDebugUnitTest` (ATT-4669, Parent Epic ATT-2466, extending `REQ-TST-001` and `REQ-BLD-002`):
1. *Contract Test Alignment with Dead Code Excision (`REQ-TST-003.1`)*:
• In `HardcodedStringsAndUnitsContractTest.kt`, the obsolete test asserting that `TrainingApplication.todo()` is marked `@Deprecated` SHALL be removed, aligning with dead-code excision in `REQ-ARC-003.1` and `DeadCodeEliminationContractTest`.
2. *Static Dependency Scaffolding Parity (`REQ-TST-003.2`)*:
• In `AltitudeAwareClusterMatchingTest.kt`, unit tests exercising `WorkoutClusterEngine.learnFromWorkout()` (which calls `ensureStartingLocationAnchor()`) SHALL supply static MockK mocks for `KnownLocationsDatabaseManager` and execute clean static teardown, preventing unmocked Android Location / database initialization failures following production catch-workaround excision (`REQ-TST-001.3`).
3. *Isolated DataStore Injection & Context Mocking (`REQ-TST-003.3`)*:
• In `DevicesTabbedViewModelTest.kt`, all test instantiations of `DevicesTabbedViewModel` SHALL inject `settingsDataStore = mockSettingsDataStore`, and `mockApplication.filesDir` SHALL be stubbed to return a valid directory, preventing un-stubbed application preference storage crashes during test suite execution.
4. *Deterministic ViewModel Dispatcher Injection (`REQ-TST-003.4`)*:
• In `BackupRestoreViewModel.kt`, asynchronous operations (`restoreFromLocalBackup`) SHALL execute on a configurable `CoroutineDispatcher` injected via `@JvmOverloads constructor(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`.
• In `BackupRestoreViewModelLocalRestoreTest.kt`, test cases SHALL inject `testDispatcher`, eliminating background thread races and guaranteeing deterministic synchronization with `advanceUntilIdle()`.
5. *ViewModel Coroutine Scope Lifecycle Teardown (`REQ-TST-003.5`)*:
• In `TrackingTabsViewModelInteropTest.kt` (and test classes instantiating ViewModels with infinite flow collection in `init`), all test-instantiated ViewModels SHALL be tracked and have their `viewModelScope.cancel()` invoked in `@After` teardown prior to `Dispatchers.resetMain()` and `unmockkAll()`, strictly preventing uncaught background coroutine exceptions from leaking into subsequent test executions (`UncaughtExceptionsBeforeTest`).
6. *Clean-Room Full Suite Regression Execution (`REQ-TST-003.6`)*:
• The full unit test suite under `./gradlew testDebugUnitTest` SHALL achieve a 100% clean-room pass rate across all 3,840+ unit tests with zero test failures or flakiness.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TST-003` under Epic `ATT-2466` (*Developer Testing Tools*), extending `REQ-TST-001` and `REQ-BLD-002`. Targets `HardcodedStringsAndUnitsContractTest.kt`, `AltitudeAwareClusterMatchingTest.kt`, `DevicesTabbedViewModelTest.kt`, `BackupRestoreViewModelLocalRestoreTest.kt`, `BackupRestoreViewModel.kt`, and `TrackingTabsViewModelInteropTest.kt`.
2. *Historical Origin & Commit Trace*: Discovered during Sprint `2026-43.7` Tier 2 integration gating (`feature/ATT-4669`). Traces to `ATT-4024` (commit `040854ad`, catch excision), `ATT-4026` (commit `8529df98`, contract test), and `ATT-4031` (commit `f04b501c`, dead code removal).
3. *Root Reason for Existing Formulation*: Tests accumulated missing mocks when production exception catches were excised; contract tests were not pruned when dead methods were eliminated; ViewModels hardcoded `Dispatchers.IO` and omitted `viewModelScope.cancel()` in `@After`.
4. *Preservation of Core Invariants*: Production clustering and backup logic preserved with zero alteration; thread safety and 100% clean-room test pass rate strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `HardcodedStringsAndUnitsContractTest`,
• *When* executed,
• *Then* tests succeed without asserting on excised `todo()` method.
• *Given* `AltitudeAwareClusterMatchingTest.testLearnFromWorkout_UpdatesAltitudeExtremaCentroidsInDatabase`,
• *When* executed,
• *Then* `KnownLocationsDatabaseManager` is mocked and test passes deterministically.
• *Given* `DevicesTabbedViewModelTest`,
• *When* tests instantiate `DevicesTabbedViewModel`,
• *Then* `mockSettingsDataStore` is injected and tests execute without `PreferenceDataStoreFile` NPEs.
• *Given* `BackupRestoreViewModel.restoreFromLocalBackup`,
• *When* invoked with injected `testDispatcher`,
• *Then* operations complete synchronously with `advanceUntilIdle()` and error/success states assert reliably.
• *Given* `TrackingTabsViewModelInteropTest`,
• *When* `@After` teardown runs,
• *Then* all instantiated ViewModels have their `viewModelScope` cancelled, preventing `UncaughtExceptionsBeforeTest` errors in subsequent tests.
• *Given* the entire unit test suite,
• *When* `./gradlew testDebugUnitTest` executes,
• *Then* all 3,840+ tests pass with 0 failures.

**Invariants**: Zero alteration to production runtime business logic, thread safety preserved, 100% clean-room test pass rate.

### REQ-ARC-004: Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene [`TST-ARC-004`]
- **Tests**: `WorkoutClustersViewModel.kt`, `AndroidViewModelReflectionContractTest.kt`, `docs/coding_standards.md`
- **Implementation**: Annotate WorkoutClustersViewModel constructor with @JvmOverloads to provide 1-arg constructor for AndroidViewModelFactory reflection, add AndroidViewModel reflection contract test, and maintain coding standards.
- **Rationale**: Whenever an `AndroidViewModel`, `ViewModel`, or `Fragment` class instantiated via default Android framework reflection (`ViewModelProvider.AndroidViewModelFactory`, `viewModel(activity)`) declares default constructor parameters (e.g., `CoroutineDispatcher = Dispatchers.IO`), the primary constructor SHALL be annotated with `@JvmOverloads constructor` to generate single-argument bytecode constructors matching framework reflection expectations, preventing runtime `NoSuchMethodException` failures (ATT-4667, Parent Epic ATT-431, extending `REQ-ARC-003` and aligning with `docs/coding_standards.md` §3.5):
1. *WorkoutClustersViewModel Reflection Compatibility (`REQ-ARC-004.1`)*:
• In `WorkoutClustersViewModel.kt`, the primary constructor SHALL declare `@JvmOverloads constructor(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`, ensuring that `ViewModelProvider.AndroidViewModelFactory` resolves `(Application.class)` constructor via Java reflection.
2. *Automated Reflection Contract Verification (`REQ-ARC-004.2`)*:
• `AndroidViewModelReflectionContractTest.kt` SHALL reflectively inspect all `AndroidViewModel` subclasses instantiated via default framework factory, asserting that `clazz.getConstructor(Application::class.java)` resolves and has public visibility.
3. *Coding Standards Parity (`REQ-ARC-004.3`)*:
• `docs/coding_standards.md` §3.5 SHALL document the mandatory `@JvmOverloads constructor` rule for reflective Android framework classes with default parameters.
4. *Preservation of Core Invariants (`REQ-ARC-004.4`)*:
• The default `Dispatchers.IO` coroutine dispatcher injection for background equipment mapping (`REQ-DAT-033.3`) SHALL remain intact.
• Existing clustering algorithms, database access, and UI StateFlows SHALL remain unchanged.
• 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-ARC-004` under Epic `ATT-431` (*Clean Code*), extending `REQ-ARC-003`. Targets `WorkoutClustersViewModel.kt`, `AndroidViewModelReflectionContractTest.kt`, and `docs/coding_standards.md` §3.5.
2. *Historical Origin & Commit Trace*: Discovered during Sprint `2026-43.7` (`feature/ATT-4667`). Traces to `ATT-4003` (commit `6073cf43`), introducing `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` to offload sport equipment mapping (`REQ-DAT-033.3`).
3. *Root Reason for Existing Formulation*: `ioDispatcher` was added to `WorkoutClustersViewModel` for coroutine dispatching without realizing Kotlin does not emit single-arg Java bytecode constructors for default arguments without `@JvmOverloads`, causing runtime reflection failure.
4. *Preservation of Core Invariants*: Full clustering functionality, background IO dispatching, route linking, and 100% clean-room test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutClustersViewModel`,
• *When* instantiated via `ViewModelProvider.AndroidViewModelFactory` or `getConstructor(Application.class)`,
• *Then* the 1-arg constructor resolves and instantiates cleanly without `NoSuchMethodException`.
• *Given* `AndroidViewModelReflectionContractTest`,
• *When* executed,
• *Then* all default-instantiated `AndroidViewModel` subclasses expose a valid, public 1-arg `(Application.class)` constructor.
• *Given* the full test suite,
• *When* `./gradlew testDebugUnitTest` runs,
• *Then* 100% tests pass with zero failures.

**Invariants**: Zero alteration to clustering algorithms or StateFlows, coroutine dispatcher injection preserved, 100% clean-room test pass rate.

### REQ-PRO-027: Agile Agentic ASPICE (AAA) Governance, Constructive Resistance, Hypothesis Verification, Stage 5 Resource Gate & Ceremony 2 Completeness Sweep [`TST-PRO-020`]
- **Tests**: `docs/project_protocol.md`, `.agents/rules/aspice_governance.md`, `.agents/skills/stage1-analysis/SKILL.md`, `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`
- **Implementation**: Codify Agile Agentic ASPICE (AAA) philosophy, constructive resistance, Stage 1 hypothesis audit, Stage 5 AAPT2 checks, and Ceremony 2 completeness sweep.
- **Rationale**: The engineering workflow, AI agent orchestration, and living standards SHALL enshrine Agile Agentic ASPICE (AAA) and its three Automotive SPICE pillars (ATT-3534, Parent Epic ATT-232, refining `REQ-PRO-026` and `REQ-PRO-024`):
1. *Three Automotive SPICE Pillars (`docs/project_protocol.md`, `.agents/rules/aspice_governance.md`)*:
• Pillar 1: Intentional Engineering Tripwires (*Stolpersteine*) — Deliberate quality gates (Gates 1–5), bidirectional requirement/test traceability, Chesterton's Fence archaeology, and independent `pro`/`flash` cross-auditing prevent autonomous agent shortcuts.
• Pillar 2: Artifact-Driven Infinite Context — Stage deliverables (`docs/engineering/`), living specifications (`docs/requirements.md`, `docs/tests.md`), and walkthroughs (`docs/engineering/walkthroughs/`) maintain persistent external memory across sessions.
• Pillar 3: Constructive Resistance & Documented Deviations (*Pflicht zum Widerspruch & Dokumentierte Abweichungserlaubnis*) — Agents MUST NOT initiate shortcuts. When a human requests an exception from standard process, the agent SHALL first offer constructive resistance (explaining the guardrail purpose and associated risk). If the human insists, the exception, justification, and modifications SHALL be cleanly documented in Jira and deliverable/walkthrough.
2. *Stage 1 Hypothesis Verification (`.agents/skills/stage1-analysis/SKILL.md`)*:
• When a ticket contains a pre-formulated problem domain, root-cause analysis, or technical proposal, the Stage 1 agent SHALL treat it as a hypothesis to independently verify against live code on the active sprint branch, documenting findings in a dedicated "Independent Peer Review & Verification" section.
3. *Fast Stage 5 Resource Gate (`.agents/skills/stage5-verification/SKILL.md`)*:
• Stage 5 Tier 1 verification SHALL execute `./gradlew processDebugResources` to rapidly catch 9-language XML/AAPT2 compilation errors before Gate 5 sign-off.
4. *Ceremony 2 Critical Co-Reviewer Completeness Sweep (`.agents/skills/sprint-planner/SKILL.md`)*:
• Ceremony 2 Step 3 SHALL inspect the actual git commit (`git show`) for `<KEY>` and execute a codebase completeness sweep (`grep`/`rg`) across `app/src/main` on refactoring/cleanup/migration tickets before presenting the Recommendation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-027` under Epic `ATT-232` (*Process*), refining `REQ-PRO-026` (*Document Triad Architecture*) and `REQ-PRO-024` (*Modular Skill Architecture*).
2. *Historical Origin & Commit Trace*: `REQ-PRO-026` in `ATT-3323` (commit `6925d864`), `REQ-PRO-024` in `ATT-3676` (commit `7a12b84`).
3. *Root Reason for Existing Formulation*: Process rules established the Document Triad and skills but left AAA philosophical foundations, constructive resistance, fast AAPT2 checks, and hypothesis verification implicit.
4. *Preservation of Core Invariants*: 8 Core Rules structure, Persistent `pro`/`flash` Zipper Principle, Human Decision Gate on parent tickets, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `docs/project_protocol.md` and `.agents/rules/aspice_governance.md`,
• *When* inspected,
• *Then* both documents define Agile Agentic ASPICE (AAA) and the 3 pillars (Tripwires, Infinite Context, Constructive Resistance).
• *Given* a ticket with a prior root-cause analysis,
• *When* Stage 1 is executed,
• *Then* the analysis deliverable includes an independent code audit against the active sprint branch.
• *Given* Stage 5 verification,
• *When* executed,
• *Then* `./gradlew processDebugResources` is mandated.
• *Given* Ceremony 2 ticket review,
• *When* inspecting refactoring tickets,
• *Then* a codebase completeness sweep across `app/src/main` is mandated.

**Invariants**: Human Decision Gate remains inviolable; 100% clean-room test pass rate; zero governance regressions.

### REQ-PRO-028: Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Governance [`TST-PRO-021`]
- **Tests**: `docs/requirements.md`, `docs/tests.md`, `docs/requirements/`, `docs/tests/`, `tools/verify_requirement_governance.py`, `tools/jira_util.py`
- **Implementation**: Restructure living documentation into domain-sharded multi-line markdown sections and upgrade governance tooling to support dual-format verification.
- **Rationale**: The system living specification architecture and governance tooling SHALL restructure monolithic single-file tables into modular domain-sharded documents, introduce multi-line Markdown heading sections, and support dual-format verification in governance tools (ATT-4594, Parent Epic ATT-232, refining `REQ-PRO-026` and `REQ-PRO-027`):
1. *Domain Directory Sharding (`REQ-PRO-028.1`)*:
• The living documentation SHALL be sharded into 7 domain-specific files under `docs/requirements/` and `docs/tests/` (`req_tracking_cockpit.md`, `req_aftermath_periods.md`, `req_maps_navigation.md`, `req_data_migration_export.md`, `req_settings_profiles.md`, `req_ui_design_system.md`, `req_process_stability.md`).
• `docs/requirements.md` and `docs/tests.md` SHALL serve as lightweight master indices referencing all domain shards.
2. *Multi-Line Heading & Bullet Section Format (`REQ-PRO-028.2`)*:
• Monolithic single-line pipe-delimited table rows SHALL be replaced with structured multi-line Markdown heading sections (`### REQ-XXX: ... [Status]` and `### TST-XXX: ... [Status]`), eliminating single-line bloat and enabling token-efficient granular modifications.
3. *Tooling & Verification Parity (`REQ-PRO-028.3`)*:
• `tools/verify_requirement_governance.py` and `tools/jira_util.py` (`auto_verify_living_docs_on_stage5`) SHALL support both single-file tables and domain-sharded multi-line heading sections, scanning `docs/requirements/` and `docs/tests/` and reliably detecting status transitions (`Planned` -> `Verified`).
4. *100% Migration Parity & ID Preservation (`REQ-PRO-028.4`)*:
• Automated migration tooling (`tools/shard_living_docs.py`) SHALL guarantee 100% bidirectional traceability preservation across all 762 `REQ-` and 764 `TST-` IDs without omission.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-PRO-028` under Epic `ATT-232` (*Process*), refining `REQ-PRO-026` (*Document Triad Architecture*) and `REQ-PRO-027` (*Agile Agentic ASPICE Governance*). Targets `docs/requirements.md`, `docs/tests.md`, `docs/requirements/`, `docs/tests/`, `tools/verify_requirement_governance.py`, and `tools/jira_util.py`.
2. *Historical Origin & Commit Trace*: `REQ-PRO-026` in `ATT-3323` (commit `6925d864`), `REQ-PRO-027` in `ATT-3534` (commit `39b3df08`).
3. *Root Reason for Existing Formulation*: Early project phases used single-file Markdown tables for simplicity, which accumulated across 43 sprints to 4.3 MB, causing severe tool truncation and token bloat.
4. *Preservation of Core Invariants*: 100% bidirectional traceability, automated requirement governance with exit code 0, and non-destructive documentation migration remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `docs/requirements/` and `docs/tests/`,
• *When* inspected,
• *Then* 7 domain-sharded markdown files exist in each directory, and master indices provide links and counts.
• *Given* living specification entries in domain shards,
• *When* inspected,
• *Then* entries use multi-line heading format `### REQ-XXX: ... [Status]`.
• *Given* `tools/verify_requirement_governance.py` and `tools/jira_util.py`,
• *When* executed,
• *Then* both verify multi-line heading status and domain shards with exit code 0.
• *Given* the migration tool `tools/shard_living_docs.py`,
• *When* executed,
• *Then* all 762 REQ IDs and 764 TST IDs are preserved with 100% parity.

**Invariants**: Bidirectional traceability, zero loss of normative requirements, 100% clean-room test pass rate.
