# Sprint Review & Retrospective — Sprint 2026-42.8

**Date**: 2026-10-18  
**Sprint**: `2026-42.8` (`sprint/2026-42.8`, Jira Sprint ID `375`)  
**Target Release**: `V4.9.40`

---

## 1. Sprint Review Summary

### 1.1 Reviewed & Accepted Tickets (`FixVersion: V4.9.40`)
1. **ATT-3929**: *Add 'Physiology & Training Models' section in Advanced Settings to toggle training science schools (Coggan NP/IF/TSS, PMC, Aerobic Decoupling EF/Pw:Hr, Critical Power CP/W'/W'bal)* — **Accepted**
2. **ATT-3930**: *Redesign WorkoutDetails power and physiology rows to follow the AltitudeRow + MetricItem pattern (Section 5.10) aligned with ATT-3929 schools* — **Accepted** *(Follow-up: `ATT-4042`)*
3. **ATT-3928**: *Move 'Profile & Zones' to Training drawer section and make 'My Stuff' collapsible by default* — **Accepted**
4. **ATT-3927**: *Fix TCX replay speed inflation and timeline progression for variable-interval GPS tracks in tools/replay_workout.py* — **Accepted**
5. **ATT-3926**: *Make inner dial of bsport_other.xml stopwatch icon transparent to match legacy grey/active rendering* — **Accepted**
6. **ATT-3920**: *Enable re-ordering and hiding tabs in the DetailedWorkoutView* — **Accepted** *(Follow-up: `ATT-4043`)*
7. **ATT-3938**: *Remember selected tab in Sensor Mapping (EquipmentSensorMatrixScreen) across navigation* — **Accepted**
8. **ATT-3961**: *Introduce global sensor priority and conflict resolution for duplicate sensor types* — **Accepted**
9. **ATT-3971**: *Detect climbs and enable Live Climb sheet on raw imported GPX/TCX routes without requiring MatchedTrack creation* — **Accepted**

### 1.2 Deferred Review State Across Sprint Close (Rule 16)
- **ATT-3921**: *Cockpit: Defaults for Bike Power should contain new Sensors (TSS, IF, ...)* — Remains in `Final Review (Human)` for live cockpit field verification.

### 1.3 Follow-Up Tickets Created at Top of Backlog
- **ATT-4043**: *Clean up WorkoutMasksAndCardsSection: remove redundant Details column, collapse Summary list by default, and split Zone Display Mode into Summary vs Details* (Epic `ATT-355`)
- **ATT-4042**: *Single-line Power row and clearer visual separation between domain rows in WorkoutDetails* (Epic `ATT-355`, with on-device screenshot `ATT-3930_review_feedback.png`)

---

## 2. Key Retrospective Learnings & Root Cause Analysis

### Learning 1: Single-Line Domain Rows & Clear Row Separation in `WorkoutDetails` (`ATT-3930` $\rightarrow$ `ATT-4042`)
- **Observation**: Even when using the `AltitudeRow` + `MetricItem` pattern (`docs/design_guidelines.md` Section 5.10), stacking two rows of 3 power metrics (`NP`, `IF`, `TSS` + `Work`, `W/kg`, `VI`) under a single domain header made `WorkoutDetails` feel dense and cramped, and consecutive domain rows lacked sufficient visual separation.
- **Root Cause**: `docs/design_guidelines.md` Section 5.10 previously permitted multi-row `Row` grids inside a single domain group (*"When a domain group has more than 3 metrics (e.g., 5–6 power metrics), arrange them into balanced multi-row `Row`s of 2–3 equal-weight `MetricItem` columns"*).
- **Countermeasure (`docs/design_guidelines.md` Section 5.10 / Rule 42 & `ATT-4042`)**:
  - Updated `docs/design_guidelines.md` Section 5.10 (and `.agents/rules/aspice_governance.md` Rule 42) to require **strictly a single horizontal row of 2–3 primary `MetricItem`s per domain row** and explicit visual separation (`HorizontalDivider` + vertical padding) between consecutive domain rows.
  - Created `ATT-4042` at the top of the backlog to refine the Power row to a single line (`NP`, `IF`, `TSS`) and increase separation between domain rows.

### Learning 2: Retiring Overlapping Legacy Settings Controls When Adding Dedicated Configuration Sheets (`ATT-3920` $\rightarrow$ `ATT-4043`)
- **Observation**: When introducing the dedicated *"Detail-Tabs konfigurieren"* (`WorkoutDetailTabsConfigSheet`) bottom sheet in `ATT-3920`, the legacy `Details` column inside the 2-column `WorkoutMasksAndCardsSection` (`Trainingsliste (Detail-Karten)`) matrix was kept in place for backward sync, leaving two redundant UI places in the same settings screen for configuring detail sections.
- **Root Cause**: Lack of an explicit UI guideline requiring superseded settings matrix controls to be retired when a dedicated configuration surface is introduced.
- **Countermeasure (`docs/design_guidelines.md` Section 3.2 / Rule 43 & `ATT-4043`)**:
  - Added **Section 3.2 (`Single Unambiguous Configuration Surface — Retiring Redundant Legacy Controls`)** to `docs/design_guidelines.md` and mapped it as **Rule 43** in `.agents/rules/aspice_governance.md`.
  - Created `ATT-4043` at the top of the backlog to remove the redundant `Details` column from `WorkoutMasksAndCardsSection`, collapse the Summary card list by default, and split `ZoneDisplayMode` into Summary vs. Details.

### Learning 3: First-Try Reliability for `tools/jira_util.py` CLI Invocations
- **Observation**: During ticket creation and attachment uploads in Ceremony 2, `tools/jira_util.py` occasionally failed on the first try (e.g. calling `attach` instead of `upload`, or passing `--type`/`--parent` flags or issue type names to `create-issue`).
- **Root Cause**:
  1. `.agents/skills/jira-workflow/SKILL.md` and `.agents/skills/sprint-planner/SKILL.md` omitted `create-issue`, `upload`, `set-fixversion`, `rank-top`, and `TYPE_ID` mappings (`10008` = Feature, `10009` = Bug, `10010` = Verbesserung) from their CLI command reference blocks.
  2. `tools/jira_util.py` only accepted positional numeric `TYPE_ID`s (`10008`, `10009`, `10010`) and `upload` (not `attach` or `--type`/`--parent` flags), and printed a bare `"Invalid command or arguments."` without usage instructions on error.
- **Countermeasure (`tools/jira_util.py`, `.agents/skills/jira-workflow/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`)**:
  - Enhanced `tools/jira_util.py` to accept `"attach"` as an alias for `"upload"`, resolve human-readable issue type names (`Feature`, `Bug`, `Verbesserung`, `Improvement`), accept both positional args and `--type`/`--parent`/`--epic` flags in `create-issue`, and print the full `USAGE_TEXT` whenever an invalid command or `--help` is passed.
  - Updated `.agents/skills/jira-workflow/SKILL.md` and `.agents/skills/sprint-planner/SKILL.md` with the complete CLI reference for all `tools/jira_util.py` commands.
