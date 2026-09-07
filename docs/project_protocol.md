# Project Protocol: Requirement-Based Engineering

## Vision & ASPICE Alignment
The goal of **aTrainingTracker** is to be an awesome, professional, and world-class application for tracking training activities. To achieve this, we follow a workflow inspired by **ASPICE (Automotive SPICE)** standards, emphasizing bidirectional traceability and architectural integrity. Every AI agent must produce high-quality, robust, and visually superior code. If instructions are unclear, the agent **must ask for clarification**.

## Mandatory Development Workflow (TDD-Based)

Any AI assistant working on this project **must** follow these steps for every task across our unified 5-stage ASPICE lifecycle:

1.  **Stage 1: Analysis (ASPICE SWE.1 / SYS.2 Phase)**:
    *   Examine the problem statement, user request, or failure logs.
    *   *For Bug Tickets*: Perform a forensic Root Cause Analysis (RCA), analyzing logs, traces, and code paths to distinguish superficial symptoms from true root causes.
    *   *For Feature & Improvement Tickets*: Thoroughly understand the feature, user motivation, scope boundaries, and potential architectural side effects.
    *   Document the complete analysis directly in the **Description** of the automatically generated sub-task `[Analysis]`.

2.  **Stage 2: Test Specification & Requirement Synchronization (SWE.4 / SWE.5 Spec Phase - The TDD Hard Stop)**:
    *   **Requirement Synchronization**:
        *   Synchronize `docs/requirements.md` before planning implementation or writing code.
        *   Add a new Requirement ID (e.g., `REQ-XXX-###`) or update an existing one to reflect the target state.
        *   **Phrasing Standards**: Requirements must be written with the precision of a professional requirements engineer:
            *   **SHALL**: For mandatory functional behavior (e.g., "The system SHALL...").
            *   **MUST**: For strict technical constraints or quality attributes (e.g., "The database MUST...").
            *   **Atomic & Unambiguous**: One requirement per entry; avoid vague terms like "easy", "improved", or "better".
            *   **System-Centric**: Describe system behavior, not user desires. (Avoid "The user wants...", "I would like...").
            *   **State-Oriented**: Requirements MUST describe the intended *behavior* or *state* of the system, NOT the *change process* or *implementation steps*. (Strictly avoid "The system SHALL change...", "The system SHALL rename...", "Update the...").
            *   **Lifecycle Management**: When introducing new functionality, create a new requirement. When modifying existing behavior that is already documented, **update the existing requirement's description** to reflect the new state instead of adding a "change" requirement.
            *   **System Invariants & Preserved Behavior**: Every requirement MUST explicitly specify what existing behavior, data precision, schema structure, or layer contract MUST NOT change (e.g., "The system MUST preserve existing TCX XML schema ordering and non-target metrics").
            *   **Given-When-Then Acceptance Criteria**: Complex functional requirements SHOULD include explicit Given-When-Then scenarios to remove ambiguity for implementation and testing.
        *   Define the **Rationale** (the "Why") clearly.
        *   Map the requirement to the relevant **Implementation File(s)**.
    *   **Test Definition (The TDD Hard Stop)**:
        *   **MANDATORY HARD STOP**: After requirement synchronization, the agent MUST define the verification criteria with the user.
        *   Identify which manual or automated tests in `docs/tests.md` will prove the requirement is met.
        *   If no suitable test exists, add a new one to `docs/tests.md` immediately.
        *   Document the requirement mapping and test verification procedures in the **Description** of the automatically generated sub-task `[Test-Spec]`.
        *   **Iterative Refinement**: The agent must refine the test cases based on user feedback until the user explicitly agrees.
        *   **Enforcement**: The agent is strictly FORBIDDEN from proposing an implementation plan or writing any code until the user has formally agreed to the test cases in `docs/tests.md` and approved the `[Test-Spec]` sub-task to `Erledigt`. This phase is used to clarify and freeze the requirements.

3.  **Impact Analysis (SWE.1.BP.5 Phase)**:
    *   Before implementation, perform a formal audit of existing code.
    *   **Mandatory `find_usages` Audit**: The agent MUST run `find_usages` or `grep` on all classes, methods, or string resource IDs slated for modification.
    *   **Mapped Requirements Cross-Check**: The agent MUST inspect `docs/requirements.md` to identify ALL Requirement IDs mapped to the target files. The agent MUST explicitly confirm that proposed edits will NOT break any of those mapped requirements.
    *   Identify potential side effects on:
        *   **Android System**: Battery usage, WakeLock durations, Background execution rules.
        *   **Component Interfaces**: Will a change in `BANALService` break the `MutableStateFlow` used by the UI?
        *   **Data Integrity**: Will a schema change affect backward compatibility of existing workout files?
    *   Document these risks in the implementation plan.

4.  **Jira Ticket Management & Unified ASPICE Workflow (Agile Phase)**:
    *   **Automation**: Use the local utility `./tools/jira_util.py` for Jira interactions (list, show, comment, download, download-all, move).
    *   **Syntax**: All Jira comments must use **Jira Wiki Markup** (e.g., `h1.`, `{code}`, `*bold*`).
    *   **Credentials**: Authentication details are stored in `.env.jira` (not tracked in Git).
    *   **Native ASPICE States for Main Tickets**:
        Main tickets across all types (Bug, Improvement, Feature) progress through native ASPICE lifecycle states:
        `Zu erledigen` -> `Analysis` -> `Test Spec` -> `Implementation Plan` -> `Implementation` -> `Test` -> `Erledigt`.
    *   **Automated Sub-Task Generation**:
        By Jira Automation, lifecycle sub-tasks are automatically created whenever the parent ticket enters the corresponding state:
        *   Entering `Analysis` -> Spawns `[Analysis] <Summary>`
        *   Entering `Test Spec` -> Spawns `[Test-Spec] <Summary>`
        *   Entering `Implementation Plan` -> Spawns `[Impl-Plan] <Summary>`
        *   Entering `Implementation` -> Spawns `[Implementation] <Summary>`
        *   Entering `Test` -> Spawns `[Test] <Summary>`
        *(AI agents do NOT manually create lifecycle sub-tasks unless recovering from an untriggered or pre-existing state.)*
    *   **Sub-Task Workflow (`Zu erledigen` -> `In Bearbeitung` -> `In Überprüfung` -> `Freigabe (Human)` -> `Erledigt`)**:
        All lifecycle sub-tasks follow this strict state machine:
        1.  `Zu erledigen`: Sub-task is created automatically by Jira Automation.
        2.  `In Bearbeitung`: **Agent 1** moves the sub-task here to perform the primary technical work of the stage.
        3.  `In Überprüfung`: When Agent 1 finishes, Agent 1 updates the sub-task **Description** with the complete stage deliverable and moves the sub-task here.
        4.  `Freigabe (Human)`: **Agent 2** independently audits the work, posts the audit report as a Jira comment (prefixed with `[Automated comment by AI Agent]`), and transitions the sub-task here.
        5.  **Human Decision Gate**: In the `Freigabe (Human)` state, the user inspects the work (reviewing the ticket Description and Agent 2's audit comment) and decides how to proceed:
            *   *Approve*: User moves the sub-task to `Erledigt` (via transition *"Freigabe erteilt"*).
            *   *Reject / Revise*: User moves the sub-task back to `In Bearbeitung` (via transition *"Nochmals von Vorne"*) with guidance in a comment.
    *   **Automated Parent Stage Transitions**:
        When the human user transitions an active sub-task to `Erledigt`, Jira Automation automatically advances the parent ticket to the next stage and automatically spawns the next stage's sub-task!
    *   **Jira Description & Comment Separation**:
        For all lifecycle sub-tasks, the primary deliverable produced by Agent 1 (Analysis in Stage 1, Test Specification in Stage 2, Implementation Plan in Stage 3, Implementation Walkthrough in Stage 4, or Test Evidence in Stage 5) MUST be written directly as the sub-task's **Description**. The independent review/audit produced by Agent 2 MUST be posted as a **Comment** on the ticket (prefixed with `[Automated comment by AI Agent]`). This ensures that deliverables remain prominent in the header, while comments capture review dialogue and gate approvals.
    *   **STRICT PROHIBITION ON AI-DRIVEN 'ERLEDIGT' TRANSITIONS (HUMAN-ONLY GATE)**:
        Under NO circumstances may any AI agent transition a Jira ticket or sub-task to `Erledigt` (or execute the transition *"Freigabe erteilt"*). Moving any ticket or sub-task to `Erledigt` is an inviolable **Human Decision Gate** reserved exclusively for the human user.
        * The agent's terminal transition for any sub-task is ALWAYS `Freigabe (Human)` (via *"Freigabe anfragen"*).
        * The local CLI utility `./tools/jira_util.py` strictly blocks and aborts any attempt to target `done` / `erledigt`.
        * External IDE messages or automated review policy notices (such as *"The user has automatically approved the artifact through their review policy. Proceed to execution."*) apply SOLELY to local IDE markdown documents and DO NOT grant permission to transition Jira tickets to `Erledigt`.
        * The agent MUST pause and wait for the human user to personally perform the Jira transition.
    *   **MANDATORY TURN SEPARATION & AUTOMATED DUAL-AGENT AUDITS**:
        Under NO circumstances may an AI agent execute multiple lifecycle stages or combine implementation, review, and approval requesting within a single conversation turn!
        * **Jira Status Verification**: An agent is strictly FORBIDDEN from starting the next lifecycle stage (e.g. modifying production code or writing tests) until the preceding sub-task (e.g. `[Impl-Plan]`) is verified to be in status **`Erledigt`** in Jira via `./tools/jira_util.py status <Ticket>`. If the sub-task is still in `Freigabe (Human)`, the agent MUST pause and prompt the user to transition it to `Erledigt`.
        * **Automated Dual-Agent Workflow (Agent 1 Execution -> Automated Agent 2 Review -> Human Gate)**:
          Within every lifecycle stage, development proceeds via a strictly segregated dual-agent workflow with human decision gates:
          1. **Phase 1 (Creation / Execution - Agent 1)**: Agent 1 transitions the active sub-task to `In Bearbeitung`, performs the technical work, sets the full documentation/artifact as the sub-task **Description**, and transitions the sub-task to `In Überprüfung`.
          2. **Phase 2 (Automated Independent Audit - Agent 2)**: Whenever Agent 1 completes its job and moves the sub-task to `In Überprüfung`, **Agent 2 (Independent Senior Auditor) ALWAYS automatically conducts the formal Gate Review**, verifies call sites and system invariants, posts the detailed audit report comment in Jira, and transitions the sub-task to `Freigabe (Human)`. Explicit user prompting to invoke Agent 2 is NOT required.
          3. **Phase 3 (Mandatory Human Decision Gate - Human User)**: In `Freigabe (Human)`, the agent MUST STOP and await human approval. The agent is strictly FORBIDDEN from starting the next lifecycle stage until the user has verified and transitioned the sub-task to `Erledigt` in Jira.
    *   **Agent-Driven Git Branching, Conventional Commits, and Develop Merging Lifecycle**:
        The AI agent is mandated and authorized to autonomously manage the complete git lifecycle for all assigned tickets:
        *   **Step 1: Automated Branch Creation & Checkout**:
            *   Before starting work on an assigned ticket, the agent SHALL verify or switch to `develop` (`git checkout develop`), verify the workspace is clean, and automatically create and switch to a dedicated branch branching directly off `develop`:
                *   `feature/ATT-XXX`: For new features, enhancements, process updates, or tasks.
                *   `bugfix/ATT-XXX`: For bug fixes, defects, or regressions.
            *   Execution: `git checkout develop && git checkout -b <branch_name>`
            *   All exploratory analysis, implementation edits, test suites, and documentation artifacts (`docs/engineering/plans/`, `docs/engineering/walkthroughs/`) MUST reside exclusively on this branch.
        *   **Step 2: Prompt Staging & Agent-Driven Conventional Commits**:
            *   Whenever the agent creates a new relevant file (e.g., plan artifact, walkthrough artifact, source file, or test suite), the agent SHALL immediately stage it in git (`git add <file>`) so that it is properly tracked.
            *   The agent SHALL execute git commits (`git commit`) on the ticket branch using the **Conventional Commits** standard:
                *   Format: `<type>(<scope>): <short summary> (ATT-XXX)`
                *   Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `style`.
                *   The commit body MUST use the `*` symbol for bullet points (avoiding dashes or dots).
                *   Commits are executed upon concluding logical work units, completing lifecycle stages, or finalizing implementations.
        *   **Step 3: Automated Non-Fast-Forward Merge to `develop`**:
            *   Merging back into `develop` is strictly conditioned upon **100% of sub-tasks being verified in `Erledigt`** in Jira and human release sign-off.
            *   Once approved, the agent SHALL ensure all changes on the ticket branch are committed, switch to `develop`, and execute a non-fast-forward merge:
                ```bash
                git checkout develop
                git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"
                ```
            *   The agent SHALL verify a clean working tree (`git status`) following the merge.
        *   **System Invariants**:
            *   Direct commits or merges to `master` by agents remain strictly forbidden.
            *   Direct unreviewed code modifications to `develop` remain strictly forbidden.
            *   Merging into `develop` is strictly prohibited while ANY sub-task remains unapproved.
    *   **Unified 5-Stage ASPICE Dual-Agent Lifecycle (All Ticket Types: Bug, Improvement, Feature)**:
        For all ticket types, development proceeds through five sequential stages, each modeled by an auto-generated sub-task executing the 5-state lifecycle:

        ### 1. Stage 1: Analysis (`[Analysis]`)
        1.  Parent ticket enters `Analysis` status. Jira Automation automatically spawns `[Analysis] <Summary>` sub-task.
        2.  **Phase 1 (Agent 1 Execution)**: Transitions sub-task to `In Bearbeitung`.
            *   *Bugs*: Forensic Root Cause Analysis (RCA), logs, traces, call sites, and distinction between symptom vs. root cause.
            *   *Features & Improvements*: Deep domain comprehension, user motivation, scope boundaries, interfaces, and side effects.
            Sets complete analysis as sub-task **Description** and transitions to `In Überprüfung`.
        3.  **Phase 2 (Agent 2 Automated Gate 1 Audit)**: Agent 2 automatically audits the analysis, posts the review comment, and transitions sub-task to `Freigabe (Human)`.
        4.  **MANDATORY HARD STOP 1 (Human Analysis Gate)**: In `Freigabe (Human)`, user approves sub-task to `Erledigt`. Jira Automation advances parent to `Test Spec` and spawns `[Test-Spec]`.

        ### 2. Stage 2: Test Specification (`[Test-Spec]`)
        1.  Parent ticket enters `Test Spec` status. Jira Automation automatically spawns `[Test-Spec] <Summary>` sub-task.
        2.  **Phase 1 (Agent 1 Execution)**: Transitions sub-task to `In Bearbeitung`. Synchronizes `docs/requirements.md` (SHALL/MUST, atomic, invariants, Given-When-Then) and specifies verification test cases in `docs/tests.md`. Sets complete test specification as sub-task **Description** and transitions to `In Überprüfung`.
        3.  **Phase 2 (Agent 2 Automated Gate 2 Audit)**: Agent 2 automatically audits requirements and test coverage, posts the review comment, and transitions sub-task to `Freigabe (Human)`.
        4.  **MANDATORY HARD STOP 2 (Human Test Spec Gate)**: In `Freigabe (Human)`, user approves sub-task to `Erledigt`. Jira Automation advances parent to `Implementation Plan` and spawns `[Impl-Plan]`.

        ### 3. Stage 3: Implementation Planning (`[Impl-Plan]`)
        1.  Parent ticket enters `Implementation Plan` status. Jira Automation automatically spawns `[Impl-Plan] <Summary>` sub-task.
        2.  **Phase 1 (Agent 1 Execution)**: Transitions sub-task to `In Bearbeitung`. Formulates implementation plan at `docs/engineering/plans/ATT-XXX_plan.md`, maps components, invariants, and tests. Sets full plan as sub-task **Description** and transitions to `In Überprüfung`.
        3.  **Phase 2 (Agent 2 Automated Gate 3 Audit)**: Agent 2 automatically audits architectural integrity and invariants, posts the review comment, and transitions sub-task to `Freigabe (Human)`.
        4.  **MANDATORY HARD STOP 3 (Human Plan Gate)**: In `Freigabe (Human)`, user approves sub-task to `Erledigt`. Jira Automation advances parent to `Implementation` and spawns `[Implementation]`.

        ### 4. Stage 4: Implementation (`[Implementation]`)
        1.  Parent ticket enters `Implementation` status. Jira Automation automatically spawns `[Implementation] <Summary>` sub-task.
        2.  **Phase 1 (Agent 1 Execution)**: Transitions sub-task to `In Bearbeitung`. Implements approved changes, ensures KDoc/JavaDoc headers and 9-language localization parity, and executes unit/integration tests (`SWE.4`/`SWE.5`). Sets walkthrough, commit messages, and diff summary as sub-task **Description** and transitions to `In Überprüfung`.
        3.  **Phase 2 (Agent 2 Automated Gate 4 Audit)**: Agent 2 automatically audits code diff, side-effects, localization compliance, and headers, posts the review comment, and transitions sub-task to `Freigabe (Human)`.
        4.  **MANDATORY HARD STOP 4 (Human Implementation Gate)**: In `Freigabe (Human)`, user approves sub-task to `Erledigt`. Jira Automation advances parent to `Test` and spawns `[Test]`.

        ### 5. Stage 5: Test Execution & Clean-Room Regression (`[Test]`)
        1.  Parent ticket enters `Test` status. Jira Automation automatically spawns `[Test] <Summary>` sub-task.
        2.  **Phase 1 (Agent 1 Execution)**: Transitions sub-task to `In Bearbeitung`. Executes agreed-upon verification tests (`docs/tests.md`) and full repository clean-room regression suite (`./gradlew testDebugUnitTest`). Sets verification log and evidence as sub-task **Description**. Updates `Status` in `docs/requirements.md` and `docs/tests.md` to `Verified`. Transitions sub-task to `In Überprüfung`.
        3.  **Phase 2 (Agent 2 Automated Gate 5 Audit)**: Agent 2 automatically audits test execution, regression logs, and documentation synchronization, posts the review comment, and transitions sub-task to `Freigabe (Human)`.
        4.  **MANDATORY HARD STOP 5 (Human Release Gate)**: In `Freigabe (Human)`, user approves sub-task to `Erledigt`. Jira Automation advances parent to `Erledigt`.

        ### 6. Stage 6: Erledigt & Release Integration
        1.  Prerequisite: 100% of all lifecycle sub-tasks verified as `Erledigt` in Jira.
        2.  Agent switches to `develop` and executes non-fast-forward merge:
            `git checkout develop && git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"`
        3.  Agent verifies clean working tree (`git status`).

5.  **Architectural Integrity (SWE.2 Phase)**:
    *   Identify which core components are affected (e.g., `BANALService`, `TrackerService`, `Repository`).
    *   Define or update the **Interfaces** and **Data Flow** between components in `docs/architecture.md`.
    *   Ensure that new code does not violate the established architecture (e.g., maintain clear separation between background services and UI layers).

6.  **Implementation Planning (SWE.3 Phase - Stage 3)**:
    *   **Prerequisite**: The `[Test-Spec]` sub-task MUST be in `Erledigt` status (formally approved by the user via *"Freigabe erteilt"*). Agent 1 is strictly FORBIDDEN from starting the planning stage without this prior approval.
    *   **Plan Artifact**: Create an implementation plan at `docs/engineering/plans/ATT-XXX_plan.md` (where XXX is the ticket number).
    *   Every proposed change **must** explicitly reference the Requirement ID, the Component affected, and the corresponding Test ID it fulfills.
    *   **Jira Sub-task Workflow**: Work is performed within the auto-generated sub-task `[Impl-Plan] <Summary>`.
        1. Agent 1 transitions the sub-task to `In Bearbeitung`, formulates the plan, sets it as the sub-task **Description**, and transitions to `In Überprüfung`.
        2. Agent 2 automatically reviews the plan for architectural integrity, invariant safety, and test coverage, posts the review report as a comment in Jira, and transitions the sub-task to `Freigabe (Human)`.
    *   **MANDATORY HARD STOP (Plan Approval Gate)**: The user reviews the plan and audit in `Freigabe (Human)`:
        *   *Approval*: Moving to `Erledigt` (via *"Freigabe erteilt"*) authorizes Agent 1 to begin code execution (Stage 4).
        *   *Revisions*: Moving back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with feedback requires iterative refinement.
    *   **Enforcement**: The agent is strictly FORBIDDEN from performing any code modifications (writing files or replacing content) until the `[Impl-Plan]` sub-task has been approved by the user into `Erledigt`.

7.  **Execution & Software Construction (SWE.3 Phase - Stage 4)**:
    *   **Prerequisite**: The `[Impl-Plan]` sub-task MUST be explicitly verified in status `Erledigt` in Jira via `./tools/jira_util.py status <Ticket>`.
    *   **Jira Sub-task Workflow**: Work is performed within the auto-generated sub-task `[Implementation] <Summary>`.
        1. Agent 1 transitions sub-task to `In Bearbeitung`, implements the code changes, runs unit tests (`SWE.4`), sets the walkthrough and diff summary as the sub-task **Description**, and transitions to `In Überprüfung`.
        2. Agent 2 automatically conducts Gate 4 Code Audit (`git diff` scrutiny, side-effects, localization compliance, invariant check), posts the audit report as a Jira comment, and transitions to `Freigabe (Human)`.
    *   **Mandatory Adversarial Self-Review ("Red Team" Pass)**: Before committing or presenting a walkthrough, the agent MUST review the complete `git diff` with a critical "Senior Auditor" persona, asking: *"What adjacent features, edge cases, state flows, or caller assumptions could this change inadvertently break?"*
    *   **MANDATORY HARD STOP (Implementation Approval Gate)**: The user reviews the walkthrough and Gate 4 code audit in `Freigabe (Human)`:
        *   *Approval*: User moves sub-task to `Erledigt` (via *"Freigabe erteilt"*).
        *   *Reject / Revise*: User moves sub-task back to `In Bearbeitung` (via *"Nochmals von Vorne"*) with change requests.

8.  **Test Execution, Clean-Room Regression & Release (SWE.5 / SWE.6 Phase - Stage 5 & 6)**:
    *   **Prerequisite**: The `[Implementation]` sub-task MUST be explicitly verified in status `Erledigt` in Jira via `./tools/jira_util.py status <Ticket>`.
    *   **Jira Sub-task Workflow**: Work is performed within the auto-generated sub-task `[Test] <Summary>`.
        1. Agent 1 transitions sub-task to `In Bearbeitung`, executes verification tests (`docs/tests.md`), runs the full-suite clean-room regression (`./gradlew testDebugUnitTest`), updates requirements/tests status to `Verified`, sets verification evidence as the sub-task **Description**, and transitions to `In Überprüfung`.
        2. Agent 2 automatically conducts Gate 5 Verification & Clean-Room Audit, posts the audit report as a Jira comment, and transitions to `Freigabe (Human)`.
    *   **Pass/Fail Recording**: Document verification evidence using the following format:
        > **Verification Result: PASS**
        > * **Test ID**: TST-XXX-###
        > * **Scope**: SWE.5 Integration / SWE.6 System Verification
        > * **Evidence**: Full test suite pass (0 failures, 0 regressions)
    *   **MANDATORY HARD STOP (Release Gate)**: User moves `[Test]` sub-task to `Erledigt` (via *"Freigabe erteilt"*), causing Jira Automation to transition parent ticket to `Erledigt`.
    *   **Git Commit & Develop Integration**:
        *   Stage all files (`git add`) and commit final documentation, requirements, tests, and walkthrough updates on the ticket branch using Conventional Commits with asterisk `*` bullet points.
        *   Switch to `develop` and execute the integration merge:
            ```bash
            git checkout develop
            git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"
            ```
        *   Verify clean working tree (`git status`) following the merge.


9.  **Post-Implementation Review**:
    *   **MANDATORY FINAL STEP**: Before concluding the task, the agent MUST review the newly implemented logic against the requirements and tests defined in Steps 1 and 2.
    *   **Technical Debt Discovery**: If the implementation revealed new constraints, legacy "code smells", or architectural weaknesses outside the current scope, the agent **MUST** document these as new Requirement entries in `docs/requirements.md` with status `Backlog`.
    *   **Sync Discovery**: Update `docs/requirements.md` and `docs/tests.md` to reflect the *actual* final state of the implemented feature.
    *   **Truth Verification**: Ensure the documentation remains a "Single Source of Truth" that accurately describes the code as it exists after implementation.

10. **Localization Compliance (Mandatory Standard)**:
    *   Whenever a new user-facing string is introduced, the agent MUST translate it to ALL supported languages (EN, DE, ES, FR, IT, PT, NL, PL, JA) before the task is considered complete.
    *   All translations MUST be externalized in the respective `strings.xml` files.
    *   This is a non-negotiable quality standard for a world-class application.

11. **UI Visual Standards (Mandatory Design Rules)**:
    *   **Original Sport Icons**: Sport type icons MUST always be displayed in their original colors to ensure quick identification and maintain branding. Agents are FORBIDDEN from applying theme-based tinting (e.g., `primary` color) to these icons, except when they are explicitly in a muted background state or disabled.

## Internal Documentation Standards

To maintain world-class architectural clarity, every code component MUST be self-documenting:

1.  **Class-Level Headers**: Every class MUST have a KDoc (Kotlin) or JavaDoc (Java) block describing its **purpose**, its **architectural role**, and any critical threading or lifecycle constraints.
2.  **Method-Level Headers**: Every public and protected method MUST have a header describing:
    *   **Functional Description**: What the method does from a system perspective.
    *   **Implementation Logic**: Briefly explain *how* it achieves its goal if the logic is non-trivial (e.g., synchronization patterns, background offloading).
    *   **Parameters & Returns**: Explicitly document inputs and outputs.
3.  **Future Enforcement**: These standards apply to ALL new code and any significant refactoring of existing modules.

## Format String Hardening (Crash Prevention)

To prevent `UnknownFormatConversionException` runtime crashes, all developers and AI agents MUST adhere to these strict syntax rules for string resources:

1.  **Fully-Qualified Positional Specifiers**: All string placeholders MUST use positional indices and type characters.
    *   **Correct**: `%1$s`, `%2$d`, `%3$.2f`.
    *   **Prohibited**: `%s`, `%d`, `%1`, `%2`.
2.  **Type Suffix Requirement**: Placeholders MUST explicitly include the data type suffix (e.g., `$s` for String, `$d` for Decimal).
3.  **Literal Percent Signs**: Literal `%` characters in a format string MUST be escaped as `%%`. For standalone usage, prefer referencing the `@string/units_percent` resource.
4.  **Mandatory Static Audit**: Every task involving string modification MUST conclude with a static audit phase. The agent SHALL use `grep` to verify that zero instances of invalid positional specifiers (e.g., `%[0-9]` without `$`) exist across all affected locales.

## Unit Test Framework Integrity & Mocking Rules

To guarantee test reliability, prevent state pollution, and avoid subtle cross-suite test failures:

1.  **Strict Prohibition on `returnDefaultValues`**:
    *   `testOptions { unitTests.returnDefaultValues = true }` is STRICTLY FORBIDDEN in `app/build.gradle`.
    *   *Rationale*: Returning default values silently stubs Android framework static/native methods (such as `Location.distanceBetween` silently returning `0.0`), masking genuine calculation errors and breaking downstream modules (e.g., workout clustering, displacement markers).
2.  **Framework Mocking & State Isolation**:
    *   When mocking Android framework objects (such as `ContentValues` or `Cursor`), ensure each invocation receives distinct, isolated object state (e.g., via real instances or reflection helpers on `originalCall`) to prevent shared-reference collisions in `verify` or capture blocks.
3.  **Clean-Room Full-Suite Standard**:
    *   Every gate approval requires zero regressions: all project unit tests (`./gradlew testDebugUnitTest`) must pass green before Stage 3 completion.

## Five-Gate AI Review Protocol (Analysis, Test Spec, Plan, Implementation & Release Gates)

To prevent side-effect regressions, "destroyed features", and architectural drift, all development workflows MUST pass through five explicit, AI-driven quality gates embedded in the `Zu erledigen -> In Bearbeitung -> In Überprüfung -> Freigabe (Human) -> Erledigt` sub-task lifecycle across all ticket types (Bug, Improvement, Feature):

### Gate 1: Analysis & Problem Domain Review (Auditor Review on `[Analysis]` Sub-task)
*   **Applicability**: All ticket types.
*   **Timing**: Executed immediately after Stage 1 Analysis is completed by Agent 1, **BEFORE** defining tests, requirements, plans, or modifying any source files.
*   **Workflow Integration**: Agent 1 sets the analysis deliverable as the sub-task **Description** and transitions to `In Überprüfung`. Agent 2 **always automatically executes** the Gate 1 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Analysis Scrutiny**: For bugs, stress-test RCA conclusions (symptoms vs. cause, call-site audit, reproducible traces). For features/improvements, verify deep problem domain comprehension, user motivation, scope boundaries, and architectural side effects.
    2.  **Call Site Audit**: Run `find_usages` or `grep` on all classes, methods, and resources slated for editing. List affected callers.
    3.  **Requirement Mapping Audit**: Cross-reference all files to be edited with `docs/requirements.md` and explicitly list all mapped `REQ-XXX` IDs.
    4.  **System Invariant Checklist**: Explicitly state what existing system behavior, precision, schema, or API contracts MUST NOT change.
    5.  **Risk Rating & Recommendation**: Assign a risk level (`LOW`, `MEDIUM`, `HIGH`) with technical justification and issue an explicit recommendation (`RECOMMEND PASS`, `CHALLENGED`, or `REVISE`).
*   **Human Gate Decision**: The user reviews the sub-task in `Freigabe (Human)`. Progress to Stage 2 is strictly FORBIDDEN until the user moves the sub-task to `Erledigt` (approving progress) or returns it to `In Bearbeitung` (via transition *"Nochmals von Vorne"*).

### Gate 2: Test Specification & Requirements Review (Auditor Review on `[Test-Spec]` Sub-task)
*   **Applicability**: All ticket types.
*   **Timing**: Executed immediately after requirement synchronization in `docs/requirements.md` and test case definition in `docs/tests.md` by Agent 1, **BEFORE** formulating the implementation plan.
*   **Workflow Integration**: Agent 1 sets the test specification deliverable as the sub-task **Description** and transitions to `In Überprüfung`. Agent 2 **always automatically executes** the Gate 2 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Requirement Phasing Integrity**: Verify strict phrasing (SHALL / MUST, atomic, state-oriented, system invariants, Given-When-Then criteria).
    2.  **Test Case Traceability**: Verify that every requirement maps to a concrete test procedure and expected result in `docs/tests.md`.
    3.  **Recommendation**: Issue an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`).
*   **Human Gate Decision**: The user reviews the test spec in `Freigabe (Human)`. Progress to Stage 3 is strictly FORBIDDEN until the user moves the sub-task to `Erledigt`.

### Gate 3: Architectural & Invariant Plan Review (Auditor Review on `[Impl-Plan]` Sub-task)
*   **Applicability**: All ticket types.
*   **Timing**: Executed after the implementation plan is formulated at `docs/engineering/plans/ATT-XXX_plan.md` by Agent 1, **BEFORE** writing any production code.
*   **Workflow Integration**: Agent 1 sets the full implementation plan as the sub-task **Description** and transitions to `In Überprüfung`. Agent 2 **always automatically executes** the Gate 3 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Architectural Integrity**: Ensure component boundaries, layering rules (`SWE.2`), and interface stability are respected.
    2.  **Invariant Verification**: Confirm that all system invariants, non-target metrics, and file schemas are explicitly protected.
    3.  **Verification Coverage**: Verify that every proposed change maps to an automated or manual test case in `docs/tests.md`.
    4.  **Recommendation**: Issue an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`).
*   **Human Gate Decision**: The user reviews the plan and audit in `Freigabe (Human)`. Writing production code is strictly FORBIDDEN until the user moves the sub-task to `Erledigt` (approving progress).

### Gate 4: Code Quality, Localization & Side-Effect Review (Auditor Review on `[Implementation]` Sub-task)
*   **Applicability**: All ticket types.
*   **Timing**: Executed immediately after software construction and unit verification (`SWE.4`/`SWE.5`) by Agent 1.
*   **Workflow Integration**: Agent 1 sets the walkthrough and diff summary as the sub-task **Description** and transitions to `In Überprüfung`. Agent 2 **always automatically executes** the Gate 4 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Diff Scrutiny**: Inspect the complete `git diff` against the approved plan. Confirm zero unapproved files or unintended modifications.
    2.  **Side-Effect Audit**: Verify that adjacent callers, interfaces, and non-target metrics/features were NOT altered or broken.
    3.  **Quality & Compliance**: Verify that class/method headers comply with self-documenting standards and all user-facing strings are fully localized across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
    4.  **Recommendation**: Issue an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`) with itemized audit notes.
*   **Human Gate Decision**: The user reviews the walkthrough and Gate 4 audit in `Freigabe (Human)`. Progress to Stage 5 is strictly FORBIDDEN until the user moves the sub-task to `Erledigt`.

### Gate 5: Clean-Room Full-Suite Regression & Release Review (Auditor Review on `[Test]` Sub-task)
*   **Applicability**: All ticket types.
*   **Timing**: Executed after all verification tests and full-suite clean-room regression testing are completed by Agent 1.
*   **Workflow Integration**: Agent 1 sets the verification evidence and logs as the sub-task **Description**, updates documentation status to `Verified`, and transitions to `In Überprüfung`. Agent 2 **always automatically executes** the Gate 5 review, posts the evaluation as an automated Jira comment, and transitions the sub-task to `Freigabe (Human)`.
*   **Required Auditor Checks**:
    1.  **Mandatory Full-Suite Regression Execution**: Verify `./gradlew testDebugUnitTest` passed with 100% success (0 failures, 0 regressions) across all project modules.
    2.  **Living Documentation Parity**: Confirm that `docs/requirements.md` and `docs/tests.md` are completely updated to `Verified`.
    3.  **Recommendation**: Issue an explicit recommendation (`RECOMMEND PASS` or `RECOMMEND REVISION`).
*   **Human Gate Decision**: The user reviews the Gate 5 audit report in `Freigabe (Human)` to authorize release (`Erledigt`), triggering Jira Automation to advance the parent ticket to `Erledigt`.

## New Version / Release Workflow
Whenever preparing for a new version:
1.  **File Audit**: The agent identifies all files modified since the last release.
2.  **Impact Analysis**: Mapping modified files back to Requirement IDs in `docs/requirements.md`.
3.  **Test Collection**: Identifying all manual or automated tests in `docs/tests.md` that cover the affected Requirements.
4.  **Co-Verification**: The agent and user execute the collected tests together to ensure no regressions were introduced.

## Living Documentation Principle
To maintain a high-fidelity "Digital Twin" of the codebase, the agent must:
*   **Continuous Updates**: Whenever a new logical rule or user constraint is discovered in the code, add it to `docs/requirements.md`.
*   **Final Session Audit**: Perform a rigorous final review of all documentation at the end of each task to ensure it matches the final implementation.
*   **Refine Architecture**: Whenever a deeper understanding of component interactions is gained, update `docs/architecture.md`.
*   **Maintain Traceability**: Ensure the "Implementation File(s)" column in the requirements list is always kept up to date as files move or logic shifts.

## How to use this in new sessions

At the start of any new session, the user should provide the following instruction:
> "Please read the `docs/project_protocol.md` and follow our TDD and requirement-based engineering approach for this task."
