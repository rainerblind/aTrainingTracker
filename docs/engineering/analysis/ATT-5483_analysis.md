# Stage 1 Analysis: ATT-5483 - Workout Details (Runden & Segmente Tab): Clicking a Lap Does Not Open LapEditBottomSheet

**Ticket**: [ATT-5483](https://rainerblind.atlassian.net/browse/ATT-5483)  
**Sub-task**: `[Analysis]` (creating via `submit-stage`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5483`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In Workout Details (`TrackOnMapScreen.kt`), clicking on a lap in the *"Runden & Segmente"* tab (`WorkoutDetailTabbedContent.kt` / `WorkoutDetailSectionType.LAPS`) does nothing visible: neither displaying the lap on the map nor opening `LapEditBottomSheet` to edit the lap's custom name, description, or boundary. Athletes cannot annotate laps or view individual lap segment maps from the Workout Details view.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Git Archaeology Trace**:
  1. `c2f96cae` (`ATT-511`, 2026-09-11): `LapEditBottomSheet` was introduced and wired exclusively inside `WorkoutSummary.kt` (`onLapClick = { lap -> activeEditingLap = lap }`), because the expandable summary card was then the sole host of `WorkoutLaps`.
  2. Legacy `TrackOnMapScreen.kt`: When `WorkoutLaps` was introduced into the single-page layout beneath the always-visible map, `onLapClick` was wired only to `selectedLapNr = if (selectedLapNr == lap.lapNr) null else lap.lapNr` for map highlighting, omitting `LapEditBottomSheet`.
  3. `ea5fa705` (`ATT-3920`, 2026-10-18) & `ATT-4165`: `ATT-3920` refactored Workout Details into multi-tab `WorkoutDetailTabbedContent.kt`. `WorkoutDetailSectionType.LAPS` was placed onto the *"Runden & Segmente"* tab (`MODE_A_CARD_SCROLL`), which has no map. The old `onLapClick` lambda was copied verbatim (`onSelectLapNr(...)`), leaving `LapEditBottomSheet` stranded in `WorkoutSummary.kt` while clicking a lap on the tabbed screen toggled state on the hidden Map tab with zero visible feedback.
* **Independent Peer Review & Verification**:
  * Confirmed in `WorkoutDetailTabbedContent.kt` L741: `onLapClick = { lap -> onSelectLapNr(...) }` hardcodes selection without opening an edit sheet or delegating `onLapClick`.
  * Confirmed in `TrackOnMapScreen.kt`: Hosts `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet`, but lacks `activeEditingLap` state and `LapEditBottomSheet` composition.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Wire `onLapClick: ((LapData) -> Unit)?` through `WorkoutDetailTabbedContent.kt` to `TrackOnMapScreen.kt`.
  * Host `LapEditBottomSheet` as an overlay in `TrackOnMapScreen.kt` (retaining composition alongside host screens per Rule 47 / `REQ-UI-149`).
  * Wire `onSaveLap` to `repository.updateLapDetails(workoutData.id, lapNr, name, description)` and `onMoveBoundaryClick` to `MoveLapBoundaryOnMapSheet`.
* **Out-of-Scope Non-Goals**:
  * Modifying `LapEditBottomSheet` internal layout, animations, or tag chips.
  * Altering `Laps.db` database schema or quantitative metric calculations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-142` (amended to explicitly mandate `LapEditBottomSheet` invocation in Workout Details `TrackOnMapScreen` / `WorkoutDetailTabbedContent` alongside `WorkoutSummary`); interacting with `REQ-UI-408` and `REQ-UI-450`.
* **Historical Origin & Commit Trace**: `c2f96cae` (`ATT-511`), `ea5fa705` (`ATT-3920`), `ATT-908`.
* **Root Reason for Existing Formulation**: Conceived when `WorkoutSummary.kt` was the primary workout inspection surface; subsequent tabbed detail refactoring (`ATT-3920`) inadvertently omitted sheet integration.
* **Preservation of Core Invariants**: Retains single-page map highlighting contract (`selectedLapNr`) while adding sheet overlay; preserves quantitative metric immutability.

---

## 5. Architectural Strategy & High-Level Solution
* **Modal Overlay Retention (Rule 47)**: Host `activeEditingLap?.let { LapEditBottomSheet(...) }` at the root of `TrackOnMapScreen.kt` alongside `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet`.
* **Callback Delegation**: In `WorkoutDetailTabbedContent.kt`, add `onLapClick: ((LapData) -> Unit)? = null` parameter; invoke `onLapClick(lap)` when tapped, while updating `selectedLapNr = lap.lapNr`.
* **Persistence & Actions**: Connect `onSaveLap` to `repository.updateLapDetails` within `coroutineScope.launch`, and route `onMoveBoundaryClick` to dismiss edit sheet and trigger `showMoveBoundarySheet = true`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Rule 47: Modal sheets must render as overlays alongside the host screen, never unmounting tabs.
  2. Quantitative lap metrics (`timeTotalS`, `distanceTotalM`, `speedAverageMps`) remain strictly immutable.
  3. Parent ticket Human Decision Gate strictly preserved.
* **Risk Rating**: **LOW** — Pure UI composition and callback wiring leveraging existing, verified `LapEditBottomSheet` and `WorkoutRepository.updateLapDetails` primitives.
