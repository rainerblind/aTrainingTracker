# Sprint Review & Retrospective: Sprint 2026-41.2

* **Ticket**: [ATT-2634](https://atrainingtracker.atlassian.net/browse/ATT-2634) (*-- Review & Retro --*)
* **Sprint**: `2026-41.2`
* **Branch**: `sprint/2026-41.2` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-08

---

## 1. Executive Summary & Review Outcome

Sprint **2026-41.2** focused on cockpit navigation UX, cloud export resilience, and visual styling harmony:
- User-tunable in-ride HUD transparency and auto-dismiss timing in Expert Settings ([ATT-2632](https://atrainingtracker.atlassian.net/browse/ATT-2632)).
- Google Drive workout export authorization and hierarchy resolution ([ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)).
- Visual harmonization of bike and shoe icon tinting in the equipment sensor matrix ([ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630)).

The sprint also incorporated the production hotfix release **V4.9.38.3** ([ATT-2633](https://atrainingtracker.atlassian.net/browse/ATT-2633)), resolving production crashes on Android 14 API 34 devices ([ATT-2584](https://atrainingtracker.atlassian.net/browse/ATT-2584)) and universal vector drawables in Compose ([ATT-2619](https://atrainingtracker.atlassian.net/browse/ATT-2619)).

In accordance with **Rule 16 (Install Before Review)**, the Joint Review began with deploying the integrated sprint build to the attached physical Google Pixel 10 (`66020DLCR002FL`). The sprint-native tickets were reviewed individually in rank order (**Rule 9 & Rule 14**).

---

### 1.1 Evaluated Sprint Tickets

| Ticket | Summary | Review Result | Status |
| :--- | :--- | :--- | :--- |
| **[ATT-2632](https://atrainingtracker.atlassian.net/browse/ATT-2632)** | Configure navigation cue overlay transparency and dismiss duration in expert settings | **Accepted (i.O.)**: Transparency slider (20%–100%) and auto-dismiss chips verified and accepted. | `Erledigt` |
| **[ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631)** | Google Drive workout export fails with Failed to resolve Google Drive folder hierarchy | **Rejected (n.i.O.)**: Export still fails on physical device. An interactive debugging session is needed to capture live tokens and responses. | Bounced to `Analysis` |
| **[ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630)** | Unify bike and shoe icon tinting in equipment sensor matrix | **In-Progress Work Preserved**: Stages 1–4 completed; Stage 5 verification paused upon user request. Changes safely committed on `feature/ATT-2630` (`5f5114aa`). | `In Bearbeitung` (Carry-over) |

---

## 2. Retrospective: Observations, Root Causes & Process Hardening

### 2.1 Action Item 1: Formalized Hotfix Workflow & Governance (Rule 26)
* **Observation / User Mandate**: Product Owner Rainer Blind noted in ATT-2634:
  > *"Define Workflow / Skill for Hotfixes."*
  During Sprint 2026-41.2, emergency hotfixes ATT-2584 and ATT-2619 required rapid construction, immediate testing, tag generation (`V4.9.38.3__266`), and release build deployment without disrupting the ongoing sprint branch cadence.
* **Root Cause**: The project possessed standard ASPICE stage skills for sprint feature development and release management (`release-manager`), but lacked an explicit, standardized governance rule and workflow definition for out-of-band hotfix branches.
* **Process Hardening (Rule 26 & GitFlow Invariant)**:
  1. **Hotfix Branching**: Hotfixes branch directly from `master` (or the latest release tag): `hotfix/V<VERSION>__<BUILD>`.
  2. **Streamlined ASPICE Execution**: Hotfixes strictly execute Stages 1–5 (Analysis, Test-Spec, Impl-Plan, Implementation, Verification), authoring targeted deliverables in `docs/engineering/`.
  3. **Release & Dual Integration**:
     - Hotfix is verified and tagged on `master`.
     - GitFlow hotfix finish merges back into `master` and back-merges into `develop`.
     - If an active sprint branch (`sprint/<ID>`) is in flight, the hotfix changes MUST be immediately rebased or merged into `sprint/<ID>` to ensure ongoing sprint tickets build upon the hotfix baseline without merge conflicts.

---

### 2.2 Action Item 2: Interactive Cloud & External Account Testing Protocol (ATT-2631)
* **Observation**: In ATT-2631, all offline unit tests (`GoogleDriveClientTest`, `GoogleDriveUploaderTest`) passed 100% using HTTP response mocks. However, during on-device inspection in the Sprint Review, the live Google Drive export still failed.
* **Root Cause**: Cloud integrations depend on external Google Play Services OAuth2 token negotiation, runtime account selection, and live Google Drive API v3 endpoints. Synthetic mock tests verify parsing and retry control flow, but cannot validate live token validity, Google Drive API server quotas, or account-specific folder permission states.
* **Process Hardening**:
  - For tickets involving external cloud services (Google Drive, Dropbox, Strava), Stage 5 verification must incorporate an on-device live check or an explicit interactive verification step with device logcat inspection (`adb logcat -s GoogleDrive... TrainingTracker`) before claiming verified resolution.
  - ATT-2631 has been transitioned back to `Analysis` with an action item to conduct an interactive debugging session with the user.

---

### 2.3 Action Item 3: Style Guide Update for Semantic Category Icon Tinting (ATT-2630)
* **Observation**: In `EquipmentSensorMatrixScreen.kt`, active bike icons were tinted with `MaterialTheme.colorScheme.primary` while active shoe icons were tinted with `MaterialTheme.colorScheme.secondary`, causing visual mismatch across matrix rows and between tabs in `DevicesTabbedScreen`.
* **Root Cause**: When the matrix was initially built as a single combined screen, `secondary` was applied ad-hoc to shoes for visual contrast against bikes. With tabbed sport isolation (`REQ-UI-284`), this became visually disharmonious.
* **Process Hardening (`docs/design_guidelines.md` §5.5 & Rule 23)**:
  - Added explicit guidelines to Section 5.5 of `docs/design_guidelines.md`:
    > *"When rendering paired or symmetric category items across lists, tables, or tabbed views (e.g. equipment categories like bikes and shoes): All active entities across categories MUST use the same unified brand color token (`MaterialTheme.colorScheme.primary`). Do NOT assign disparate color roles (e.g. `primary` to bikes and `secondary` to shoes) to differentiate categories; visual distinction is provided by the vector drawables themselves (`ic_equipment_bike` vs `ic_equipment_shoe`) and section/tab titles."*

---

### 2.4 Action Item 4: Precise Sprint ID Filtering in Review & Planning Ceremonies
* **Observation**: At the start of Ceremony 2, querying `sprint in openSprints()` returned tickets from Sprint 358 ("Human Review") alongside active Sprint 359 ("2026-41.2").
* **Root Cause**: Multiple sprints can be concurrently active in Jira boards (e.g. a standing "Human Review" board sprint alongside the active iteration sprint).
* **Process Hardening (`sprint-planner/SKILL.md`)**:
  - In `sprint-planner/SKILL.md`, replaced generic `sprint in openSprints()` with explicit sprint ID or exact sprint name matching:
    ```bash
    python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Final Review (Human)' ORDER BY rank ASC"
    ```
  - Eliminates cross-sprint ticket pollution and ensures review focus strictly on the current sprint.

---

## 3. Sprint Closure & Integration Notice

* **Accepted Tickets**: [ATT-2632](https://atrainingtracker.atlassian.net/browse/ATT-2632) (`Erledigt`).
* **Returned to Backlog**: [ATT-2631](https://atrainingtracker.atlassian.net/browse/ATT-2631) (`Analysis` for interactive session).
* **In-Progress Scope**: [ATT-2630](https://atrainingtracker.atlassian.net/browse/ATT-2630) (clean WIP on `feature/ATT-2630`).
* **Governance Hardening**: Updated `docs/design_guidelines.md` (§5.5), `.agents/rules/aspice_governance.md` (Rule 26), and `sprint-planner/SKILL.md`.
