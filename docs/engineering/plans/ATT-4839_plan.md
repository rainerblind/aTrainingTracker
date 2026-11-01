# Stage 3: Implementation Plan - ATT-4839: [Verbesserung] Different times for detection of auto pause and leaving auto pause

**Ticket**: [ATT-4839](https://rainerblind.atlassian.net/browse/ATT-4839)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Design & Layer Boundaries (SWE.2)

```
[AutoPauseTabsScreen (UI Slider)]
            │ (events)
            ▼
[AutoPauseViewModel (updateAutoPause*ResumeDebounceSec)]
            │ (suspend update / StateFlow)
            ▼
[TuningPreferencesDataStore (Jetpack DataStore)]
            │ (toAutoPauseConfig)
            ▼
[AutoPauseConfig (sport-specific resumeDebounceSec)]
            │ (1-Hz evaluateTick)
            ▼
[AutoPauseManager (resumeDebounceCounter >= sportResumeDebounceSec)]
```

### Layer Responsibilities
- **UI (`AutoPauseTabsScreen.kt`)**: Renders a *Resume Detection Time* `TuningSliderItem` on each sport tab (Bike: 1..20s; Run: 1..20s; Other: 1..60s).
- **ViewModel (`AutoPauseViewModel.kt`)**: Dispatches async preference updates to `TuningPreferencesDataStore`.
- **DataStore (`TuningPreferencesDataStore.kt`)**: Persists integer keys `KEY_AUTO_PAUSE_*_RESUME_DEBOUNCE_SEC` and resets them in `resetAutoPausePreferences(sportType)`.
- **Domain Engine (`AutoPauseManager.kt`)**: Removes `+0.5` speed hysteresis offset deadband. Maintains `resumeDebounceCounter: Int`. Increments counter when fresh valid speed $\ge$ threshold; resets to 0 when below threshold or stale/corrupt. Resumes to `TRACKING` only when counter reaches `resumeDebounceSec`.

---

## 2. UI Consistency (Design Guidelines Section 5)

- **Reference Screen**: `AutoPauseTabsScreen.kt` (existing tab layout from ATT-4836).
- **Reused Components**: `TuningSliderItem` from `ui.settings.tuning.TuningFormControls.kt`.
- **Theme Tokens**: Standard spacing `16.dp`, `MaterialTheme.colorScheme.surface`, `onSurface`, and `primary`.

---

## 3. Atomic Implementation Steps

1. **Step 1: Defaults & Persistence Layer**:
   - Add default constants in `TuningPreferencesDefaults.kt`.
   - Add fields to `TuningConfig.kt`.
   - Add preferences keys, update methods, and reset handling in `TuningPreferencesDataStore.kt`.
2. **Step 2: Domain Engine (`AutoPauseManager.kt`)**:
   - Add `bikeResumeDebounceSec`, `runResumeDebounceSec`, `otherResumeDebounceSec`, and `getResumeDebounceSecForSport` to `AutoPauseConfig`.
   - Add `resumeDebounceCounter: Int` in `AutoPauseManager`.
   - Update `evaluateTick` in `AUTO_PAUSED`: remove `+0.5` hysteresis offset, check $v \ge v_{\text{threshold}}$, confirm over consecutive ticks, reset counter on drop or stale.
   - Reset `resumeDebounceCounter` on `reset()`, `setState()`, and `onManualResume()`.
3. **Step 3: ViewModel & UI**:
   - Add update methods in `AutoPauseViewModel.kt`.
   - Add Resume Detection Time sliders in `AutoPauseTabsScreen.kt`.
4. **Step 4: Localization Parity (9 Languages)**:
   - Add `auto_pause_resume_debounce_title`, `auto_pause_resume_debounce_summary`, and `auto_pause_resume_debounce_default_hint` to all 9 `strings.xml` files.
5. **Step 5: Unit Tests**:
   - Update and author tests in `AutoPauseManagerTest.kt` (`TST-TRK-043.1`, `TST-TRK-043.2`, `TST-TRK-043.3`).
   - Author tests in `AutoPauseViewModelTest.kt` (`TST-TRK-043.4`).
   - Run `TranslationParityTest` and AAPT2 resource build.

---

## 4. Invariants & Verification Checklist

1. **Code Lock**: Strictly verify Gate 3 status before editing production code.
2. **Zero Regressions**: Existing `AutoPauseManagerTest` and tracking tests must pass 100%.
3. **Manual Pause Sovereignty**: `manualPauseLocked` must remain absolute blocker.
