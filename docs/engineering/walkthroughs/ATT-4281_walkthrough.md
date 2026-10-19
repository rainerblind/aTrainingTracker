# Stage 5: Walkthrough & Verification - ATT-4281: Add electronic bike bell to Cockpit with button, double-tap gesture, and progressive loudness escalation

**Ticket**: [ATT-4281](https://rainerblind.atlassian.net/browse/ATT-4281)  
**Sub-task**: [ATT-4319](https://rainerblind.atlassian.net/browse/ATT-4319) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-421`  
**Test Mapping**: `TST-UI-384`  
**Branch**: `feature/ATT-4281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Executive Summary & Verification Overview

Ticket **ATT-4281** introduces a zero-latency, realistic electronic bicycle bell to the cycling Cockpit (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`). The solution provides:
1. **Floating Cockpit Bell Button (`🔔`)**: Displayed at the bottom overlay bar of the Cockpit when `showBikeBell == true` on the active tab (suppressed in `ScreenMode.CONFIGURATION`). It is arranged side-by-side with the `LapButton` when both are active, or centered when single, with dynamic 64.dp `SnackbarHost` bottom clearance. Single tap triggers the standard double-strike (*"Ding-Ding!"*) with haptic click and subtle icon shake; long press triggers a continuous striker roll (*"Brriiiing!"*) capped at 2.5s.
2. **Zero-Look Double-Tap Gesture**: Double-tapping anywhere on the Cockpit sensor grid in `ScreenMode.TRACKING` immediately triggers the bell and haptics without requiring eyes off the road, while cleanly preserving `BatterySaverController` screen wakefulness, vertical scrolling, and `HorizontalPager` tab swiping.
3. **Pure Kotlin In-Memory Digital Synthesis (`AudioTrack` `MODE_STATIC`)**: Real-time acoustic synthesis of metallic brass bell resonance ($2400\text{ Hz}$ fundamental, $3840\text{ Hz}$, $5280\text{ Hz}$, and $7600\text{ Hz}$ inharmonic overtones with exponential decay) pre-loaded in memory for zero-latency, allocation-free playback. Audio attributes use `USAGE_ASSISTANCE_SONIFICATION` without ducking or intercepting audio focus, coexisting smoothly with navigation prompts and music.
4. **Progressive Loudness Escalation**: Initial ring plays at polite outdoor volume (70% amplitude / 0.70f); repeat triggers within $\le 5\text{ seconds}$ or long-press gestures automatically escalate to 100% volume (1.0f); resets to polite volume after 5 seconds of silence.
5. **Database Migration & Configuration**: Upgraded `TrackingViewsDbHelper` to `DB_VERSION = 15` (`FlexibleGenericView.db` and `TrackingViews.db` registered in `DatabaseSchemaRegistry.kt`), adding `SHOW_BIKE_BELL INTEGER NOT NULL DEFAULT 0` with automatic default `1` for cycling tabs (`BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) and `0` for running/generic tabs, controllable via a `SpatialCockpitToggleCard` in `SensorGridScreen.kt` configuration mode.
6. **100% Localization Parity**: Strings `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` localized across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

Verification was conducted via clean-room regression testing (`./gradlew testDebugUnitTest`, 3,493 tests passed, 0 failures), targeted Robolectric unit/concurrency/lifecycle tests, and on-device deployment to physical Google Pixel 10 (`Pixel 10 - 17`, Android 16) with zero runtime crash loops or logcat errors.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-421.1` (Schema v15 & Cycling Defaults) | `TST-UI-384.1` | Real SQLite Migration Test (`TrackingViewsRealSqliteMigrationTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-421.1` (Repository DTO Symmetry) | `TST-UI-384.2` | Repository Unit Test (`TrackingViewsRepositoryTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-421.4` (PCM Synthesis & Concurrency) | `TST-UI-384.3` | Audio Synthesizer Test (`BikeBellAudioSynthesizerTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-421.5` (Volume Escalation & Lifecycle) | `TST-UI-384.4` | ViewModel Unit Test (`TrackingTabsViewModelTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-421.2` (Bottom Overlay & Mode Suppression) | `TST-UI-384.5` | Compose Layout Test (`TrackingTabsScreenLayoutTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-421.3` (Double-Tap & Pager Disambiguation) | `TST-UI-384.6` | Screen Contract Test (`SensorGridScreenContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-421.6` (9-Language Localization Parity) | `TST-UI-384.7` | Localization Resource Audit (all 9 `strings.xml`) | **PASSED** | `Verified` |
| `REQ-PRO-001` (Clean-Room Test Suite) | `TST-UI-384.8` | Full `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |
| `REQ-PRO-002` (Physical Device Deployment) | `SWE.6` | Pixel 10 On-Device Build & Run (`./gradlew installDebug`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 56s
32 actionable tasks: 1 executed, 31 up-to-date
3493 tests completed, 0 failed, 100% pass rate
```

### Targeted Unit & Integration Tests
```text
com.atrainingtracker.trainingtracker.database.TrackingViewsRealSqliteMigrationTest > testTrackingViewsDbHelper_v14ToV15Migration_addsShowBikeBellColumnAndSetsCyclingDefaults PASSED
com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest > testTrackingViewsRepository_dtoMappingAndShowBikeBellUpdate PASSED
com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest > testPcmSynthesis_conformsToBrassResonanceAcousticModel PASSED
com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest > testConcurrencyAndThreadSafety_simultaneousRingAndClose PASSED
com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizerTest > testAudioAttributes_configuredForSonificationWithoutExclusiveFocus PASSED
com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest > testBellVolumeEscalation_initialPoliteRepeatEscalatedSilenceResetAndLongPress PASSED
com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest > testOnCleared_releasesAudioSynthesizerResources PASSED
com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenLayoutTest > testBottomOverlayLayout_sideBySideWhenBothActiveAndCenteredWhenSingle PASSED
com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenLayoutTest > testConfigurationMode_suppressesBothLapAndBellButtons PASSED
com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenContractTest > testDoubleTapGesture_dispatchesBellRingInTrackingMode PASSED
com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenContractTest > testDoubleTapGesture_preservesHorizontalPagerSwipingAndScreenWakefulness PASSED
com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest > testDatabaseVersionIs15 PASSED
com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest > testOnCreate_seedsCanonicalDefaultTabConfigurationMatrix PASSED
com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest > testAddDefaultTab_backwardCompatibleOverload_delegatesWithClimbsAndHintsEnabled PASSED
com.atrainingtracker.trainingtracker.ui.tracking.BikePowerCockpitDefaultsAndGatingTest > testBikePowerDefaultTabs_standardPowerMapAndLapsLayouts PASSED
com.atrainingtracker.trainingtracker.ui.tracking.BikePowerCockpitDefaultsAndGatingTest > testEnsurePowerTabExistsForBikePower_selectsBeforeNonDestructiveInsert PASSED
```

### Compilation & Packaging (`./gradlew assembleDebug`)
```text
> Task :app:assembleDebug
BUILD SUCCESSFUL in 53s
43 actionable tasks: 5 executed, 38 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)

The application debug APK was installed and verified directly on an attached physical Google Pixel 10 running Android 16 (`Pixel 10 - 17`, serial `66020DLCR002FL`).
- **APK Installation**: `./gradlew installDebug` completed successfully (`Installed on 1 device`).
- **Activity Launch**: Launched `MainActivityWithNavigation` (`com.atrainingtracker.debug`).
- **Logcat Inspection**: Zero crashes, zero `NullPointerException`, zero unhandled exceptions, and zero audio device allocation errors.

### Visual Consistency (Rule 23)

The floating bell button conforms to Material 3 elevation and styling guidelines (`ElevatedButton`, height 72.dp), visually matching `LapButton.kt` and maintaining balanced symmetry in the Cockpit bottom overlay bar. In `ScreenMode.CONFIGURATION`, both buttons are suppressed with zero lingering artifacts, and `SnackbarHost` bottom clearance is automatically restored to 8.dp.

| Captured Physical Device Screen (`Pixel 10`) |
| :---: |
| ![device](img/ATT-4281_cockpit_device.png) |

* **Checked against `docs/design_guidelines.md` §5**: shapes [x] spacing [x] colors/themes [x] typography/icons [x] placement [x]
* **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate (3,493 tests completed, 0 failures).
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-421`) and `docs/tests.md` (`TST-UI-384`) updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-4319`) updated with walkthrough description and submitted for Gate 5 audit (`review_agent.py audit ATT-4319 --model pro`).
4. **Parent Ticket Final Review (Rule 3)**: Synchronously advancing parent ticket `ATT-4281` to `Final Review (Human)` assigned to `human`. AI agents are strictly forbidden from moving parent tickets to `Erledigt`.
5. **Single Atomic Commit (Rule 44)**: Integrated as a single atomic commit onto `sprint/2026-43.1` via `git merge --squash feature/ATT-4281`.
