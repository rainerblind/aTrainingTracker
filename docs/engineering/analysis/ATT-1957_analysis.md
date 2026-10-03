# Stage 1 Analysis: ATT-1957 - [Settings/UI] Advanced Settings Accordion Subsections Should Be Initially Collapsed

**Ticket**: [ATT-1957](https://rainerblind.atlassian.net/browse/ATT-1957)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Branch**: `feature/ATT-1957`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In sprint ticket ATT-1821 (`REQ-UI-222`), the 25+ configuration parameters of the Advanced / Expert Settings dialog (`AdvancedTuningDialog.kt`) were refactored into a modular, collapsible accordion architecture featuring 5 semantic subsections:
1. `COCKPIT_TYPOGRAPHY` (*Cockpit & Typografie*)
2. `BATTERY_SAVER` (*AMOLED & Akku-Sparmodus*)
3. `SENSORS_GPS` (*Sensoren, GPS & Filterung*)
4. `AFTERMATH_ANALYSIS` (*Aftermath & Profil-Analytik*)
5. `WORKOUT_MASKS_CARDS` (*Workout-Karten & Eingabemasken*)

During initial implementation in ATT-1821, the first section (`COCKPIT_TYPOGRAPHY`) was initialized as expanded by default (`rememberSaveable { mutableStateOf(setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)) }`). This was done primarily as a demonstration of the live HUD typography preview card.

However, in actual usage, athletes opening the Advanced Settings dialog are immediately confronted with the large, expanded Cockpit Typography section (including font family dropdown, weight chips, and the preview HUD card). This forces users who wish to inspect or adjust settings in the other 4 categories (e.g. Battery Saver dimming, GPS accuracy, slope damping, or Workout Card display masks) to scroll extensively past the entire typography section before reaching their target category.

The expected and much cleaner UX behavior is for all 5 accordion subsections to be initially collapsed (`expandedSections = emptySet<String>()`) when `AdvancedTuningDialog` opens. This presents a compact, scannable overview of all 5 categories—along with their live active-value summary subtitles—on a single screen, allowing athletes to tap and expand only the specific category they need.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `AdvancedTuningDialog.kt` reveals the exact mechanism:

1. **Expansion State Initialization in `AdvancedTuningDialog.kt`**:
   - In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt` (line 99):
     ```kotlin
     // Multi-section expansion state tracked across configuration changes via string identifiers
     var expandedSections by rememberSaveable { mutableStateOf(setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)) }
     ```
   - Because `setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)` is used as the initial state, the Cockpit & Typography section is unconditionally expanded whenever the dialog is first opened or recomposed fresh.

2. **Accordion Section Management**:
   - `isSectionExpanded(section: TuningSection)` checks `expandedSections.contains(section.name)`.
   - `toggleSection(section: TuningSection)` adds or removes `section.name` from `expandedSections`.
   - Initializing `expandedSections` with `emptySet<String>()` ensures that all 5 sections start collapsed, while preserving full independent expand/collapse toggling and state survival across screen rotations via `rememberSaveable`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Modify `expandedSections` initialization in `AdvancedTuningDialog.kt` from `setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)` to `emptySet<String>()`.
  2. Verify that upon opening `AdvancedTuningDialog`, all 5 accordion subsections (`COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `AFTERMATH_ANALYSIS`, `WORKOUT_MASKS_CARDS`) are collapsed, displaying their category icons, titles, and live active-value summary subtitles.
  3. Ensure that tapping any accordion card header expands its content cleanly via `AnimatedVisibility`, and state survives configuration changes (rotation).
  4. Update contract unit tests (`AdvancedTuningVisualContractTest.kt`) to verify initial collapsed state semantics.

* **Explicitly Out-of-Scope (To Prevent Scope Creep)**:
  - Modifying DataStore tuning keys or default preference values.
  - Altering the 5 category grouping definitions or string resources.
  - Changing the warning banner, reset button, or Save/Cancel actions.

---

## 4. Chesterton's Fence Requirement Archaeology (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-222` (*Settings/UI: Modular, Collapsible Subsection Accordion Architecture for Advanced Settings*), under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `e2303aa7`, ATT-1821 / ATT-1871).
3. *Root Reason for Existing Formulation*: In ATT-1821, the first section (`COCKPIT_TYPOGRAPHY`) was initialized as expanded by default (`setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)`) to immediately showcase the new typography HUD preview feature. However, this forced athletes wanting to adjust Battery Saver, GPS/Sensors, or Workout Cards to scroll through the entire cockpit preview every time they opened the dialog.
4. *Preservation of Core Invariants*: All 5 semantic sections, animated expand/collapse mechanics, live subtitles, warning card, factory reset, DataStore persistence, and zero tonal elevation remain 100% strictly preserved.

---

## 5. Proposed Architectural Design & Solution

1. **State Initialization Refinement**:
   - In `AdvancedTuningDialog.kt`:
     ```kotlin
     // Multi-section expansion state tracked across configuration changes via string identifiers
     // Initially all sections are collapsed (emptySet) providing a clean, compact overview (ATT-1957)
     var expandedSections by rememberSaveable { mutableStateOf(emptySet<String>()) }
     ```

2. **Automated Verification Strategy**:
   - Add unit/contract test in `AdvancedTuningVisualContractTest.kt` asserting that `expandedSections` is initialized with `emptySet()`.
   - Run targeted unit tests:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
     ```
   - Execute full regression suite in Stage 5.

---

## 6. Deliverable Sign-Off Criteria (Gate 1 Checklist)
- [x] Forensic root cause analysis accurately identifies the initial expanded section set in `AdvancedTuningDialog.kt`.
- [x] Chesterton's Fence Requirement Archaeology completed with all 4 mandatory fields.
- [x] Out-of-scope boundaries clearly defined.
- [x] Implementation approach preserves 100% backward compatibility and test stability.
