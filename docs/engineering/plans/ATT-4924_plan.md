# Stage 3: Implementation Plan - ATT-4924: Auto-Pause Settings Cockpit UX & Header Refinement

**Ticket**: [ATT-4924](https://rainerblind.atlassian.net/browse/ATT-4924)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-SET-080` (`REQ-SET-080.2`, `REQ-SET-080.3`), `REQ-TRK-044` (`REQ-TRK-044.2`)  
**Test Mapping**: `TST-SET-080` (`TST-SET-080.4`, `TST-SET-080.5`, `TST-SET-080.6`)  
**Branch**: `feature/ATT-4924`  
**Author**: AI Agent 1 (Software Architect - `subagent_pro`)  
**Date**: 2026-11-02  

---

## 1. Architectural Design & Layer Boundaries (SWE.2)

```
Surface (fillMaxSize)
 └── Column
      ├── Surface (primaryContainer, fixed top): Title Row + Global Card + Sport Tab Row
      └── HorizontalPager (weight(1f)): AutoPauseSportTabContent (LazyColumn: contentPadding = 16.dp)
           └── Card: Enable Switch (tuning_auto_pause_enable_title) + Threshold & Debounce Sliders
```

### Layer Responsibilities
- **UI (`AutoPauseTabsScreen.kt`)**: Fixed non-collapsing top surface, single global card, per-sport controls with clear labels.
- **Design Governance (`docs/design_guidelines.md`)**: Distinguishes list screens from settings/form tab screens.
- **Localization (`strings.xml` in 9 locales)**: Provides dedicated enable title, bidirectional speed threshold copy, and all-sports global card summary.

---

## 2. UI Consistency (Design Guidelines Section 5)
- **Reference**: `AutoPauseTabsScreen.kt` / `AdvancedTuningScreen.kt`.
- **Reused Components**: `TuningToggleItem`, `TuningSliderItem`, `Card(RoundedCornerShape(12.dp), surfaceContainerLow)`, `PrimaryScrollableTabRow`.
- **Theme Tokens**: `primaryContainer`, `onPrimaryContainer`, `surfaceContainerLow`, `surfaceContainerHighest`, `16.dp` spacing.

---

## 3. Traceability & Requirements Mapping
* **`REQ-SET-080.2` / `TST-SET-080.4`**: Fixed header layout with zero `CollapsingAppBarNestedScrollConnection` / `appBarOffset`. Title row and tabs stay visible during content scroll.
* **`REQ-TRK-044.2` / `TST-TRK-044.2`**: Single global *Start in Auto-Paused* card placed above tabs row with summary stating it applies to all sports.
* **`REQ-SET-080.3` / `TST-SET-080.4`**: Dedicated switch label `tuning_auto_pause_enable_title` ("Enable Auto-Pause") replacing `sportTitle`. Bidirectional speed threshold strings.
* **`REQ-SET-080.5` / `TST-SET-080.5`**: 100% translation parity across all 9 canonical languages.

---

## 4. System Invariants & Preserved Behavior
1. **Zero DataStore Schema Changes**: Preference keys, default values, and ViewModel methods remain untouched.
2. **Fixed Header Invariant**: Navigation hamburger, title, reset action, and sport tabs remain visible at all times.
3. **True-Black AMOLED & Contrast**: Card containers (`surfaceContainerLow`) adhere to standard tokens.
4. **Code Lock (Rule 4)**: Production source code in `app/src/...` remains locked until Gate 3 passes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`docs/design_guidelines.md`)**: Clarify in Section 5.1 that settings/form tab screens use fixed headers.
2. **Step 2 (`strings.xml` in 9 locales)**: Add `tuning_auto_pause_enable_title`; reword `tuning_auto_pause_speed_threshold_title` / `_desc` (bidirectional); reword `tuning_auto_pause_start_in_auto_pause_desc` (all sports).
3. **Step 3 (`AutoPauseTabsScreen.kt`)**: Remove collapsing connection; implement fixed top Column (title row + global card + tab row) and `weight(1f)` `HorizontalPager`; in `AutoPauseSportTabContent`, remove duplicated global card and bind `tuning_auto_pause_enable_title`.
4. **Step 4 (Unit & Contract Tests)**: Author UI contract test in `AutoPauseTabsScreenContractTest.kt`; run `TranslationParityTest`.
5. **Step 5 (Verification)**: Run `./gradlew processDebugResources` and targeted unit test suite.

---

## 6. Verification & Rollback Plan
* **Targeted Verification Scope**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.autopause.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `./gradlew processDebugResources` && `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Revert changes on `feature/ATT-4924` via git restore.
