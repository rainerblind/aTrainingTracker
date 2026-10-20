# Historical Rule Evolution & Cross-Reference Archive (Rules 1–55)

This human-facing archive preserves the complete historical mapping of all 55 rules evolved across Sprint Retrospectives in **aTrainingTracker**, mapping each legacy rule number to its active home in the Document Triad or dedicated Skills:
1. **In-Sprint Core Governance**: [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md) (8 Core Rules)
2. **UI & Interaction Design Guidelines**: [`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md)
3. **Technical Architecture & Coding Standards**: [`docs/coding_standards.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/coding_standards.md)
4. **Ceremony & Release Skills**: [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md), [`.agents/skills/release-manager/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/release-manager/SKILL.md), and [`.agents/skills/stage1-analysis/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage1-analysis/SKILL.md)..[`.agents/skills/stage5-verification/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage5-verification/SKILL.md)

---

## Historical Mapping Cross-Reference Table (Legacy Rules 1–55)

| Legacy Rule # | Original Title / Topic | Consolidated Status | Active Document / Skill | Rationale / Mapping Notes |
| :---: | :--- | :---: | :--- | :--- |
| **Rule 1** | Obey the Rules: Mandatory Rules Refresh | **Active** | `aspice_governance.md` **Core Rule 1** | Explicit `view_file` at Ticket Start / Ceremony Start / Post-Compaction; automatic stage `SKILL.md` injection via `jira_util.py`. |
| **Rule 2** | Primacy of Jira Governance over IDE Hooks | **Active** | `aspice_governance.md` **Core Rule 3** | Human decision gates and sub-task direct completion. |
| **Rule 3** | Artifact Metadata Policy (`RequestFeedback: false`) | **Pruned** | *Obsolete* | Workaround for retired IDE test harness stop-hook. |
| **Rule 4** | Gate 3 Pre-Check Before Stage 4 Code Mod | **Active** | `aspice_governance.md` **Core Rule 4** | Non-negotiable `check-gate` before code modifications. |
| **Rule 5** | Strict Prohibition on Sprint Scope Alterations | **Relocated** | `sprint-planner/SKILL.md` | Agents never move backlog tickets into sprints autonomously. |
| **Rule 6** | Strict Separation of Ticket Creation vs Impl | **Active** | `aspice_governance.md` **Core Rule 5** | Stop immediately after creating a ticket. |
| **Rule 7** | Prohibition of "Lösungsversion" on Sub-Tasks | **Consolidated** | `aspice_governance.md` **Core Rule 8** | Enforced programmatically in `jira_util.py`. |
| **Rule 8** | Continuous In-Sprint Issue Logging in Retro | **Active** | `aspice_governance.md` **Core Rule 8** | Log in-sprint anomalies directly on the sprint's `Review & Retro` ticket. |
| **Rule 9** | Single-Ticket Focus During Review and Retro | **Relocated** | `sprint-planner/SKILL.md` | Ceremony 1 & 2 single-ticket focus. |
| **Rule 10** | Standardized Sub-Task Naming Scheme | **Consolidated** | `aspice_governance.md` **Core Rule 8** | Enforced automatically by `jira_util.py submit-stage` / `create-subtask`. |
| **Rule 11** | Prohibition of Code Mods During Sprint Review | **Relocated** | `sprint-planner/SKILL.md` | Zero code changes during Ceremony 2 review. |
| **Rule 12** | Retro & Governance Docs Before Merge | **Relocated** | `sprint-planner/SKILL.md` | Retro Before Merge protocol in Ceremony 2. |
| **Rule 13** | Unattended In-Sprint Escalation Protocol | **Active** | `aspice_governance.md` **Core Rule 6** | Autonomous escalation via Jira without blocking console. |
| **Rule 14** | Strict Ticket Rank Ordering Enforcement | **Relocated** | `sprint-planner/SKILL.md` | Respect `ORDER BY rank ASC` in Ceremonies 1 & 2. |
| **Rule 15** | Autonomous Full-Sprint Execution Mandate | **Active** | `aspice_governance.md` **Core Rule 2** | Execute full sprint without pausing between tickets. |
| **Rule 16** | Mandatory Device Deployment Before Review | **Relocated** | `sprint-planner/SKILL.md` | `./gradlew installDebug` before Ceremony 2 review. |
| **Rule 17** | Lean Defect Recording During Sprint Review | **Relocated** | `sprint-planner/SKILL.md` | Capture context & screenshot into top-of-backlog follow-up ticket. |
| **Rule 18** | Clean Ticket Summaries (No Category/Epic) | **Consolidated** | `aspice_governance.md` **Core Rule 8** | Enforced automatically by `jira_util.py create-issue`. |
| **Rule 19** | Fix Version Added When Finished, Not Started | **Consolidated** | `aspice_governance.md` **Core Rule 8** | Enforced programmatically in `jira_util.py set-fixversion`. |
| **Rule 20** | Database & DTO Mapping Symmetry | **Migrated** | `docs/coding_standards.md` **Sec 2.1** | Software Engineering: Symmetric `fromCursor` mappings. |
| **Rule 21** | Direct Platform Intents Over App Settings | **Migrated** | `docs/coding_standards.md` **Sec 3.1** | Android Platform: Direct system settings intents. |
| **Rule 22** | Human Prerequisite Detection in Planning | **Relocated** | `sprint-planner/SKILL.md` | Detect human prerequisites during Ceremony 1. |
| **Rule 23** | UI Design Consistency Baseline | **Migrated** | `docs/design_guidelines.md` **Sec 5** | UI & UX: Look like the rest of the app. |
| **Rule 24** | Avoid Redundant Diagnostic Builds | **Pruned** | *Obsolete* | Basic developer diligence. |
| **Rule 25** | Lean Defect Recording with Screencap Evidence | **Relocated** | `sprint-planner/SKILL.md` | Consolidated with Rule 17 in Ceremony 2. |
| **Rule 26** | Formal GitFlow Hotfix Workflow | **Relocated** | `release-manager/SKILL.md` **Phase 6** | Hotfix branching & dual GitFlow integration. |
| **Rule 27** | Dual Branch Integration for Hotfixes | **Relocated** | `release-manager/SKILL.md` **Phase 6** | Cherry-pick in-sprint hotfixes to active hotfix branch. |
| **Rule 28** | Deferred Review State for Field-Testing | **Relocated** | `sprint-planner/SKILL.md` | Outdoor field-testable tickets may remain in `Final Review (Human)` across sprint close. |
| **Rule 29** | Defensive FGS Permission Gating & Teardown | **Migrated** | `docs/coding_standards.md` **Sec 3.2** | Android Platform: Permission-gated FGS & finalization. |
| **Rule 30** | Developer Tool Host Pre-Flight Diagnostics | **Migrated** | `docs/coding_standards.md` **Sec 4.1** | Tooling: Pre-flight checks on host hardware utilities. |
| **Rule 31** | Explanatory Scale Direction in Settings Sliders | **Migrated** | `docs/design_guidelines.md` **Sec 3.1** | UI & UX: Hint text explaining slider direction. |
| **Rule 32** | Sub-Entity Coordinate Space Normalization | **Migrated** | `docs/design_guidelines.md` **Sec 2.1** | UI & Charts: Normalize sub-entity charts to `[0, length]`. |
| **Rule 33** | Unified Viewport Anchor Authority for Maps | **Migrated** | `docs/design_guidelines.md` **Sec 6.1** | UI & Maps: Single spatial authority for map viewports. |
| **Rule 34** | Chesterton's Fence for Empty Callback Lambdas | **Migrated** | `docs/coding_standards.md` **Sec 1.2** | Software Engineering: No silent callback suppression. |
| **Rule 35** | Clean Hardware Teardown on Entity Deletion | **Migrated** | `docs/coding_standards.md` **Sec 3.3** | Software Engineering: Disconnect hardware on delete. |
| **Rule 36** | Single Source of Truth & DRY — Shared Code | **Migrated** | `docs/coding_standards.md` **Sec 1.1** | Software Engineering: Mandatory canonical formatters. |
| **Rule 37** | True Geometric Primitives Over Polygon Auto-Tracing | **Migrated** | `docs/design_guidelines.md` **Sec 5.5** | UI & Icons: Construct vectors from geometric primitives. |
| **Rule 38** | No Wrapping in Dense Horizontal Chip Rows | **Migrated** | `docs/design_guidelines.md` **Sec 5.6** | UI & Layout: Use compact bordered/tinted pills over 7-chip M3 `FilterChip` rows. |
| **Rule 39** | Reactive Flow Subscription Over Static `.value` Reads | **Migrated** | `docs/coding_standards.md` **Sec 1.5** | Software Engineering: Subscribe live flows in `combine(...)`. |
| **Rule 40** | Growing Warm-Up Window for Rolling Telemetry | **Migrated** | `docs/coding_standards.md` **Sec 1.6** | Software Engineering: Emit rolling metrics from second 1 using growing $1..W-1$ window. |
| **Rule 41** | System Navigation Bar Inset Clearance | **Migrated** | `docs/design_guidelines.md` **Sec 5.6** | UI & Layout: Apply `navigationBarsPadding()` to scrollable screens. |
| **Rule 42** | `WorkoutDetails` Single-Line Grouped Metric Rows | **Migrated** | `docs/design_guidelines.md` **Sec 5.10** | UI & Layout: Single horizontal line of 2–3 equal-weight `MetricItem` columns (`AltitudeRow` pattern). |
| **Rule 43** | Single Unambiguous Configuration Surface | **Migrated** | `docs/design_guidelines.md` **Sec 3.2** | UI & Settings: Retire superseded overlapping settings controls in the same change. |
| **Rule 44** | Single Atomic Commit per Ticket ("One Commit per Ticket") | **Active** | `aspice_governance.md` **Core Rule 8** | Squash/commit each ticket as a single atomic commit on `sprint/<ID>`. |
| **Rule 45** | Synchronous Parent Stage Progression | **Active** | `aspice_governance.md` **Core Rule 3** | Enforced automatically by `jira_util.py gate-verdict` / `move freigabe`. |
| **Rule 46** | Subagent Delegation & Asymmetric `pro`/`flash` Zipper Principle | **Active** | `aspice_governance.md` **Core Rule 7** | Alternate `pro` and `flash` across Doing (`agent1`) and Gate Review (`agent2`) across Stages 1–5. |
| **Rule 47** | Modal Overlay Composition Retention | **Migrated** | `docs/design_guidelines.md` **Sec 5.8** | UI & Layout: Render modal bottom sheets alongside the host screen, never in an exclusive `if/else` branch (`ATT-4285`). |
| **Rule 48** | Mandatory Axis Scale Labels & Outlier-Resilient Y-Scaling | **Migrated** | `docs/design_guidelines.md` **Sec 2.6** | UI & Charts: Render explicit X/Y axis labels and clamp Y-axis scaling against outliers (`ATT-4287`). |
| **Rule 49** | Unambiguous Action Icons, No ASCII Prefixes, Centered Dividers | **Migrated** | `docs/design_guidelines.md` **Sec 5.5, 5.11** | UI & Icons: No `HorizontalRule` (`—`) next to `+`, no `"+ "` in strings with `Icons.Default.Add`, centered dividers (`ATT-4286`, `ATT-4288`). |
| **Rule 50** | Backend Filter Key Normalization & Post-Trim Empty Guard | **Migrated** | `docs/coding_standards.md` **Sec 1.7** | Software Engineering: Normalize sentinel IDs (`<= 0`) to `null` and check `isEmpty()` after `trimValues()` (`ATT-4282`). |
| **Rule 51** | Constructor State Seeding Parity with `resetSession()` | **Migrated** | `docs/coding_standards.md` **Sec 1.8** | Software Engineering: Seed baseline state in metric device constructors (`ATT-4283`). |
| **Rule 52** | Sensor-Presence Gating Before Publishing Virtual Metric Values | **Migrated** | `docs/coding_standards.md` **Sec 1.8** | Software Engineering: Gate `MySensor.newValue(...)` and derived `WorkoutDataMapper` summary rows on physical/source sensor presence (`ATT-4368`). |
| **Rule 53** | Audible Outdoor Safety Alerts (`USAGE_MEDIA`) & Cockpit Utility Availability | **Migrated** | `docs/coding_standards.md` **Sec 3.4**, `docs/design_guidelines.md` **Sec 5.12** | Android & UI: Route outdoor audio alerts to `USAGE_MEDIA`, keep utility buttons enabled across `READY`/`TRACKING`/`PAUSED`, avoid nesting `ElevatedButton(onClick)` inside `pointerInput` (`ATT-4371`). |
| **Rule 54** | Dialog Width Budgets, Multi-Chip `FlowRow` Wrapping & Long-Locale (`de`) Check | **Migrated** | `docs/design_guidelines.md` **Sec 5.13** | UI & Layout: Use `FlowRow` inside `AlertDialog` and verify segmented buttons against German (`de`) word lengths (`ATT-4372`). |
| **Rule 55** | Origin-Aware Cockpit Tab Navigation on `TrackingMode.TRACKING` Transitions | **Migrated** | `docs/coding_standards.md` **Sec 1.9** | Software Engineering: Only auto-navigate to the first Cockpit telemetry tab (`NavigateTo(0)`) on initial start (`READY -> TRACKING`) or manual resume from the Control tab (`Page 0`), never when Auto-Pause ends (`ATT-4373`). |
