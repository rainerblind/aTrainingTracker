# Stage 5: Walkthrough & Verification - ATT-5656: WorkoutShareBottomSheet 3-Tier Photo Preselection

**Ticket**: [ATT-5656](https://rainerblind.atlassian.net/browse/ATT-5656)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5656`  
**Requirement Mapping**: `REQ-UI-447` (`REQ-UI-447.6`) | **Test Mapping**: `TST-UI-447` (`TST-UI-447.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Resolved an observation where choosing "Foto mit Track & Daten" (Option B) or tapping "Foto auswählen" in `WorkoutShareBottomSheet.kt` launched Android's generic system photo picker directly without contextual preselection. Implemented a 3-tier cascade:
1. **Tier 1 (Attached Photos)**: Displays attached photos in a 3-column grid (`share_photos_attached_subtitle`) when `attachedPhotos.isNotEmpty()`, with Rule 73 stacked buttons to expand to Tier 2 or Tier 3.
2. **Tier 2 (Time-Window Photos)**: Queries `WorkoutPhotoManager.queryWorkoutTimePhotos` for photos captured during the workout ($\pm 5\text{ min}$) and displays them with capture timestamps (`HH:mm`), with buttons to return to Tier 1 or switch to Tier 3.
3. **Tier 3 (All Photos / System Picker)**: Launches `ActivityResultContracts.PickVisualMedia` when tiers are empty or when user requests all photos.
4. **Preview Re-selection**: Tapping "Foto auswählen" in composed preview cleanly re-enters the cascade.

---

## 2. Requirement & Acceptance Criteria Verification Matrix

| Acceptance Criterion | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- |
| **AC-1 (Attached Photos First)** | `WorkoutShareBottomSheetContractTest` & Compose verification | **PASSED** | `Verified` |
| **AC-2 (Time-Window Fallback)** | `WorkoutShareBottomSheetContractTest` (time-window query) | **PASSED** | `Verified` |
| **AC-3 (System Picker Fallback)** | `WorkoutShareBottomSheetContractTest` (`PickVisualMedia` fallback) | **PASSED** | `Verified` |
| **AC-4 (Preview Return)** | `WorkoutShareBottomSheetContractTest` (re-enter cascade from preview) | **PASSED** | `Verified` |
| **Rule 73 Stacked Buttons** | `WorkoutShareBottomSheetContractTest` (`fillMaxWidth`, `spacedBy(8.dp)`) | **PASSED** | `Verified` |
| **9-Language Parity** | `TranslationParityTest` (100% parity across 9 locales) | **PASSED** | `Verified` |
| **Living Documentation** | `verify_requirement_governance.py` (Chesterton's Fence) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~1s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.map.*"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 1s`, 32 actionable tasks (100% PASS).

### Living Documentation Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Modified/Altered existing requirement(s) detected: REQ-UI-447.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Invariants & Backward Compatibility
1. **Option A Snapshot Invariant**: Classic Map & Analytics composite snapshot sharing (`combineWorkoutAndShare`) remains 100% untouched.
2. **Preview Fast-Path**: Direct photo preview via `initialPhotoUri != null` is preserved.
3. **Rule 73 Button Hierarchy**: Full-width stacked buttons with 8dp spacing; zero horizontal action rows in `actions =` slot.
4. **Bounded Grid Height & Scroll Safety**: `LazyVerticalGrid` constrained to 120dp/240dp and parent `AppModalBottomSheet(scrollable = activePhotoTier == null)` preventing infinite measurement exceptions.
5. **Memory Safety**: Downsampled decoding (`maxDim = 240`) prevents OOM.
