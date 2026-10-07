# Stage 3: Implementation Plan - ATT-2619: [Bug] ResearchButtonKt.ResearchButton

**Ticket**: [ATT-2619](https://atrainingtracker.atlassian.net/browse/ATT-2619)  
**Sub-task**: [ATT-2642](https://atrainingtracker.atlassian.net/browse/ATT-2642) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Android Modernization & Production Stability*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: Hotfix Release  
**Requirement Mapping**: `REQ-UI-254` (*ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback*)  
**Test Mapping**: `TST-UI-213`  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Problem Description & Background

In production release `4.9.38.2 (265)`, Crashlytics recorded a fatal crash (`Issue 355c60b8489f67ef630dc85e12708945`):
`android.content.res.Resources$NotFoundException: Resource ID #0x7f08017c` at `ResearchButton.kt:48` inside `ControlTrackingScreen.kt:110`.

The crash occurred because `ResearchButton` loaded `R.drawable.research_icon` through Jetpack Compose's `painterResource()`. In the project resources, `research_icon.png` was placed only in density-specific folders (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) without a universal fallback in `app/src/main/res/drawable/`. On devices receiving density-specific split APKs or non-standard display configurations, runtime resource resolution failed fatally.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-254` (*ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback*)
* **Test Mapping**: `TST-UI-213` (*ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Visual Hierarchy & State Fidelity**: `ResearchButton` retains 48dp dimension, primary color when active, 40% gray when disabled, click interaction dispatching, and localized label `R.string.research`.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.
5. **Programmatic Pre-Check**: Before modifying production code in Stage 4, `python3 tools/jira_util.py check-gate ATT-2642` MUST exit with code 0.

---

## 4. Proposed Architectural Changes

### Component 1: `ResearchButton.kt` (Jetpack Compose UI)
* Replace `painter = painterResource(id = R.drawable.research_icon)` with `imageVector = Icons.Default.Refresh`.
* Supply `contentDescription = stringResource(id = R.string.research)` for screen reader accessibility.
* Bypasses `Resources.getValue()` and `ResourceIdCache` entirely, eliminating all possibility of `Resources$NotFoundException` at runtime.

### Component 2: `app/src/main/res/drawable/research_icon.xml` (Universal Vector Asset)
* Add a vector drawable `research_icon.xml` to `res/drawable/`.
* Guarantees that any legacy or external Android platform callers referencing `R.drawable.research_icon` (such as `TrainingApplication.java` notification action builders) resolve cleanly on all display configurations.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Universal Vector Fallback Asset Creation
* **File**: `app/src/main/res/drawable/research_icon.xml`
* **Changes**: Define Android vector drawable with 24dp size and standard refresh path data.

### Step 2: Modernize `ResearchButton.kt` to Vector ImageVector
* **File**: `app/src/main/res/drawable/research_icon.xml`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ResearchButton.kt`
* **Changes**:
  - Import `androidx.compose.material.icons.Icons` and `androidx.compose.material.icons.filled.Refresh`.
  - Use `imageVector = Icons.Default.Refresh`.
  - Supply `contentDescription = stringResource(id = R.string.research)`.

### Step 3: Implement Unit and Resource Fallback Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ResearchButtonTest.kt`
* **Changes**:
  - Test composing `ResearchButton` in enabled state.
  - Test composing `ResearchButton` in disabled state.
  - Test resolving `R.drawable.research_icon` through `ContextCompat.getDrawable()`.

### Step 4: Execute Targeted Unit Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ResearchButtonTest"`
* **Expected Result**: 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted tests run in Stage 4.
  2. Full clean-room regression test `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Branch isolation (`hotfix/V4.9.38.3__266`) ensures changes can be reverted via git if necessary.
