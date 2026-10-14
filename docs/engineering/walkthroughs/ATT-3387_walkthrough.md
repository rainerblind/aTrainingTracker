# Stage 5: Walkthrough & Verification - ATT-3387: Show first-time swipe and tab coachmark on cockpit screen to guide stopping tracking

**Ticket**: [ATT-3387](https://rainerblind.atlassian.net/browse/ATT-3387)  
**Sub-task**: [ATT-3584](https://rainerblind.atlassian.net/browse/ATT-3584) (`[Test]`)  
**Parent Epic**: [ATT-1735](https://rainerblind.atlassian.net/browse/ATT-1735) (*Tracking UI Modernization*)  
**Target Release**: `V4.9.40` (Assigned at Sprint Close per Rule 14)  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-367`  
**Test Mapping**: `TST-UI-330`  
**Branch**: `feature/ATT-3387`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Verification Overview

Implemented the first-time non-blocking onboarding coachmark banner (`StopTrackingCoachmarkBanner.kt`) on the cockpit tracking screen (`TrackingTabsScreen.kt`) to guide first-time users on swiping right or tapping the leftmost `Control` (`Steuerung`) tab (`Page 0`) to pause or stop recording:
1. **Persistence & Automatic Factory Reset (`TuningPreferencesDataStore.kt`)**:
   - Added `TuningPreferencesDefaults.DEFAULT_HAS_SEEN_STOP_TRACKING_HINT = false` and `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` registered in `ALL_KEYS`, exposing `hasSeenStopTrackingHintFlow: Flow<Boolean>` and `suspend fun setHasSeenStopTrackingHint(seen: Boolean)`.
   - Because `KEY_HAS_SEEN_STOP_TRACKING_HINT` is included in `ALL_KEYS`, invoking `tuningDataStore.resetToDefaults()` automatically resets the coachmark to `false` without adding lines to `ui/settings/tuning/` files (`< 400` line modularity invariant preserved).
2. **ViewModel Configuration-Change Survival & Synchronous Optimistic Dismissal (`TrackingTabsViewModel.kt`)**:
   - Added `hasRenderedCoachmarkInSession: StateFlow<Boolean>` (`markCoachmarkRenderedInSession()`) and `isCoachmarkDismissPending: StateFlow<Boolean>` (`markCoachmarkDismissPending()`, `resetCoachmarkSessionState()`).
   - Pure companion object predicates `shouldShowStopTrackingCoachmark` and `shouldAutoAcknowledgeStopTrackingCoachmark` handle the initial `null` DataStore loading state, prevent premature auto-acknowledgement during the `READY -> TRACKING` transition (`settledPage == 0` before rendering on `Page >= 1`), and immediately hide the banner synchronously on user interaction before the async DataStore write completes.
3. **Zero-Jump Constant-Weight Box Overlay & 4-Inch Screen Ergonomics (`StopTrackingCoachmarkBanner.kt`, `TrackingTabsScreen.kt`)**:
   - Wrapped `HorizontalPager` and `AnimatedVisibility` (`StopTrackingCoachmarkBanner`) inside `Box(modifier = Modifier.weight(1f).fillMaxWidth())` at `Alignment.TopCenter` (`zIndex = 2f`), guaranteeing zero layout shift or resize of `HorizontalPager`.
   - Enforced `heightIn(max = 120.dp)`, `maxLines = 2`, `TextOverflow.Ellipsis`, weighted text layout (`Modifier.weight(1f)`), `tonalElevation = 0.dp`, and `48.dp` minimum touch targets (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`).
4. **9-Language Localization Parity (`strings.xml`)**:
   - Added `coachmark_stop_tracking_hint`, `coachmark_stop_tracking_show_tab`, and `coachmark_stop_tracking_dismiss` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-367` | `TST-UI-330.1` | `StopTrackingCoachmarkTest` (Visibility, `null` Loading state, `isDismissPending` optimistic dismissal, & race-free auto-acknowledgement predicates) | **PASSED** | `Verified` |
| `REQ-UI-367` | `TST-UI-330.2` | `StopTrackingCoachmarkTest` (ViewModel configuration-change survival & `TuningPreferencesDataStore` `ALL_KEYS` factory reset contract) | **PASSED** | `Verified` |
| `REQ-UI-367` | `TST-UI-330.3` | `StopTrackingCoachmarkTest` & `TrackingTabsScreenLayoutTest` (Zero-jump `Box(Modifier.weight(1f))` overlay, `heightIn(max = 120.dp)`, `maxLines = 2` 4-inch screen clearance, 48dp touch targets) | **PASSED** | `Verified` |
| `REQ-UI-367` | `TST-UI-330.4` | `StopTrackingCoachmarkTest` & `TranslationParityTest` (9-Language Localization Audit across EN, DE, ES, FR, IT, JA, NL, PL, PT) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-330.5` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "*StopTrackingCoachmarkTest*" --tests "*TrackingTabsScreenLayoutTest*" --tests "*TrackingTabsViewModelTest*" --tests "*TranslationParityTest*" --tests "*AdvancedTuningModularityTest*"
BUILD SUCCESSFUL in 1m 51s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23)
- Verified `StopTrackingCoachmarkBanner` against `TrackingTabsScreen.kt` HUD banners and `TurnPromptBanner.kt`:
  - **Shapes**: `RoundedCornerShape(12.dp)` matching canonical card/banner corner radius in `docs/design_guidelines.md`.
  - **Colors/Themes**: `MaterialTheme.colorScheme.secondaryContainer`, `MaterialTheme.colorScheme.onSecondaryContainer`, `BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))`, `tonalElevation = 0.dp` (preserving AMOLED contrast).
  - **Spacing & Small-Screen Clearance**: `padding(horizontal = 12.dp, vertical = 6.dp)`, `heightIn(max = 120.dp)`, `maxLines = 2`, `TextOverflow.Ellipsis`, `Column(modifier = Modifier.weight(1f))`, and `defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`.
  - **Placement**: Top-aligned overlay (`Alignment.TopCenter`, `zIndex = 2f`) inside the constant-weight `HorizontalPager` container `Box(modifier = Modifier.weight(1f).fillMaxWidth())`.
- *Checked against `docs/design_guidelines.md` §5*: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
- *Deviations & justification*: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: `REQ-UI-367` in `docs/requirements.md` and `TST-UI-330` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-3584` transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-3387` integrated into `sprint/2026-42.4` and transitioned to `Final Review (Human)` for final release sign-off.
