# Stage 2: Requirement & Test Specification - ATT-2308: Milestone Workout Auto-Naming and First Jubilee Celebration

**Ticket**: [ATT-2308](https://rainerblind.atlassian.net/browse/ATT-2308)  
**Sub-task**: [ATT-5336](https://rainerblind.atlassian.net/browse/ATT-5336) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-REV-001`..`REQ-REV-005`  
**Test Spec ID**: `TST-REV-001`..`TST-REV-006`, `TST-LOC-001`  
**Branch**: `feature/ATT-2308`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-REV-001..005)

### 1.1 Scope & Functional Clauses
1. **Sport-Specific Milestone Counting (`REQ-REV-001`)**: `WorkoutSummariesDatabaseManager.getFinishedWorkoutCountForSport(sportId)` SHALL query finished workouts per sport. Milestones are detected when `count == 100 || count == 250 || (count >= 500 && count % 500 == 0)`.
2. **Dynamic Auto-Naming & Export Parity (`REQ-REV-002`)**: Milestones SHALL augment the workout title (`[Count]. [SportName]: [Name]`) persisted to `WorkoutSummaries.WORKOUT_NAME` prior to cloud/file export.
3. **First-Jubilee Celebration Dialog (`REQ-REV-003`)**: Reaching the first milestone ever across any sport SHALL present `FirstJubileeCelebrationDialog` with Google Play In-App Review, direct feedback email to Rainer, and Free-tier supporter note (`NavRoutes.SUPPORTER`). Pro/Pioneer athletes omit the supporter note.
4. **Single-Prompt Invariant (`REQ-REV-004`)**: `SettingsDataStore.has_shown_first_jubilee_prompt` SHALL be persisted as `true` on first presentation. Subsequent milestones update titles without dialogs. Zero review spam.
5. **Debug Preview Trigger (`REQ-REV-005`)**: `SupporterDebugSection` SHALL expose a preview button under `BuildConfig.DEBUG`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirements `REQ-REV-001`..`REQ-REV-005`. Targets `WorkoutSummariesDatabaseManager.java`, `MilestoneAutoNamingManager.kt`, `FirstJubileeCelebrationDialog.kt`, `SettingsDataStore.kt`, `SupporterDebugSection.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-2308`, parent Epic `ATT-176`).
* **Root Reason for Existing Formulation**: Novel feature extending sequential cluster naming (`REQ-SET-066`) and supporter tiers (`REQ-SET-087`) to celebrate personal milestones.
* **Preservation of Core Invariants**: Single-prompt invariant, export title symmetry, zero review spam, thread-safe background IO execution.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (Sport Counter)**: *Given* 99 rides and 99 runs, *When* ride 100 finishes, *Then* ride counter hits milestone 100 while runs remain 99.
* **AC-2 (Title Augmentation)**: *Given* a 100th ride, *When* auto-named, *Then* the title matches `"100. Radfahrt: [ClusterName]"`.
* **AC-3 (First-Jubilee Dialog)**: *Given* the first milestone ever and `!has_shown_first_jubilee_prompt`, *When* saved, *Then* celebration dialog opens with In-App Review, email feedback, and Free-tier Pro appeal.
* **AC-4 (Single-Prompt Invariant)**: *Given* `has_shown_first_jubilee_prompt = true`, *When* subsequent milestones occur, *Then* titles augment without displaying the dialog.

---

## 2. Test Specification (TST-REV-001..006, TST-LOC-001)
* **TST-REV-001 (Sport Milestone Counter)**: SQLite counter query & threshold progression unit test (`WorkoutSummariesDatabaseManagerTest.kt`).
* **TST-REV-002 (Title Augmentation & Export)**: Auto-naming format & pre-export persistence contract test (`MilestoneAutoNamingManagerTest.kt`).
* **TST-REV-003 (Celebration Dialog & Tiers)**: ReviewManager launch, email intent, and Free vs. Pro tier UI gating test (`FirstJubileeCelebrationDialogTest.kt`).
* **TST-REV-004 (Single-Prompt Invariant)**: `SettingsDataStore` prompt persistence & zero-spam suppression test (`MilestoneAutoNamingManagerTest.kt`).
* **TST-REV-005 / TST-LOC-001 (9-Locale Parity)**: Parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (`TranslationParityTest.kt`).
* **TST-REV-006 (Debug Preview)**: `SupporterDebugSection` `BuildConfig.DEBUG` button contract test (`SupporterDebugSectionTest.kt`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-001` | Unit | `WorkoutSummariesDatabaseManagerTest.kt` | `REQ-REV-001` | Planned |
| `TST-REV-002` | Contract | `MilestoneAutoNamingManagerTest.kt` | `REQ-REV-002` | Planned |
| `TST-REV-003` | UI / Logic | `FirstJubileeCelebrationDialogTest.kt` | `REQ-REV-003` | Planned |
| `TST-REV-004` | Integration | `MilestoneAutoNamingManagerTest.kt` | `REQ-REV-004` | Planned |
| `TST-REV-005` | Localization | `TranslationParityTest.kt` | `REQ-REV-002`, `REQ-REV-003` | Planned |
| `TST-REV-006` | UI / Debug | `SupporterDebugSectionTest.kt` | `REQ-REV-005` | Planned |
