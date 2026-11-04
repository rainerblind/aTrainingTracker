# Stage 3: Implementation Plan - ATT-5401: Guarantee Visible Action on 'Bei Google Play bewerten'

**Ticket**: [ATT-5401](https://rainerblind.atlassian.net/browse/ATT-5401)  
**Sub-task**: [ATT-5405](https://rainerblind.atlassian.net/browse/ATT-5405) (`[Impl-Plan]`)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-REV-003` (*First-Jubilee Celebration Dialog & Multi-Channel Engagement*)  
**Test Mapping**: `TST-REV-003` (*First-Jubilee Celebration Dialog Tier Gating & Intent Dispatch Contract Test*)  
**Branch**: `feature/ATT-5401`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
In `FirstJubileeCelebrationDialog.kt`, tapping the primary action *"Bei Google Play bewerten"* (`jubilee_action_review`) silently does nothing on debug/sideloaded builds or under Play Core review quota throttling. The action helper must reliably unwrap `Activity` from `ContextWrapper` and immediately fall back to `openPlayStorePage(context)` whenever In-App Review cannot render UI.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-REV-003` (`REQ-REV-003.1` Context unwrapping, `REQ-REV-003.2` Deterministic visible action, `REQ-REV-003.3` Dialog dismissal)
* **Test Mapping**: `TST-REV-003.DEBUG` (Play Store fallback launch), `TST-REV-003.UNWRAP` (`Context.findActivity()` unwrapping), `TST-REV-003.LOC` (localization parity), `TST-REV-003.REG` (clean-room regression)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Dead Clicks**: User tapping 'Bei Google Play bewerten' must always observe a visible action (In-App Review dialog or Play Store app/web page).
2. **Single-Prompt Invariant**: Dialog presentation count remains strictly bounded by `SettingsDataStore.has_shown_first_jubilee_prompt` (`REQ-REV-004`).
3. **Clean Architecture & Code Lock**: No production code modified until Gate 3 audit passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
### Component 1: `FirstJubileeCelebrationDialog.kt` (`FirstJubileeActionHelper`)
* Add `internal tailrec fun Context.findActivity(): Activity?` to unwrap `ContextWrapper` chains.
* In `FirstJubileeActionHelper.launchInAppReviewOrStore(context)`:
  * If `BuildConfig.DEBUG == true`: immediately call `openPlayStorePage(context)` so debug testers and sideloaded builds always see the Play Store.
  * In release builds: resolve `context.findActivity()`. If `null`, invoke `openPlayStorePage(context)`.
  * If `activity != null`, request review flow; measure elapsed time on completion: if elapsed < 300 ms (silent no-op suppression), invoke `openPlayStorePage(context)`.

### Component 2: `FirstJubileeCelebrationDialogTest.kt`
* Add `testContext_findActivity_unwrapsContextWrappers`: verify direct Activity, nested `ContextWrapper`s, and null for non-activity context.
* Add `testFirstJubileeActionHelper_debugBuildLaunchesPlayStoreFallback`: verify `market://details?id=com.atrainingtracker` intent is dispatched.

### UI Consistency (Rule 23)
* **Reference component**: Existing `FirstJubileeCelebrationDialog.kt`
* **Reused components**: Existing Material 3 `AlertDialog`, buttons, and icons
* **Theme tokens**: Existing color scheme and typography preserved
* **New one-off styles**: None

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Add `Context.findActivity()` and Review Fallback Logic
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/dialogs/FirstJubileeCelebrationDialog.kt`
* Add `findActivity()` helper; update `launchInAppReviewOrStore` with `BuildConfig.DEBUG` check and < 300 ms fallback.

### Step 2: Implement Unit and Contract Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/dialogs/FirstJubileeCelebrationDialogTest.kt`
* Add test cases for `findActivity` unwrapping and debug Play Store launch intent verification.

### Step 3: Targeted Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.dialogs.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests, translation parity test, and `verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-5401 -- .` restores pristine branch state.
