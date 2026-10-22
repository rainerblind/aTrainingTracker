# Stage 3: Implementation Plan - ATT-4021: Zone editing races, loses drafts and lives in unreachable ZonesSettingsActivity

**Ticket**: [ATT-4021](https://rainerblind.atlassian.net/browse/ATT-4021)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: None  
**Target Release**: `V4.9.40` | **Active Sprint**: Product Backlog  
**Requirement Mapping**: `REQ-UI-428` (amending `REQ-UI-172`, `REQ-UI-397.2`)  
**Test Mapping**: `TST-UI-393` (`TST-UI-393.1`–`TST-UI-393.6`)  
**Branch**: `feature/ATT-4021`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background

Zone editing in `ZonesSettingsActivity.kt` launches unthrottled concurrent IO coroutines writing separate DataStore keys, racing and corrupting values on rapid typing; in-flight non-monotonic drafts are kept purely in `remember`, lost on rotation or tab switch; profile loading is duplicated 3x; `AthleteProfileScreen.kt` imports UI from an unstarted activity declared in `AndroidManifest.xml` with an unsafe Activity cast.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-428` (*Atomic Single-Transaction DataStore Zone Persistence, SavedStateHandle Draft Preservation, Deduplicated Profile Loading, and Zone UI Decoupling*).
* **Test Mapping**: `TST-UI-393` (`TST-UI-393.1`–`TST-UI-393.6` in `SettingsDataStoreZoneAtomicTest.kt`, contract tests & full suite).

---

## 3. System Invariants & Preserved Behavior

1. **Monotonicity Invariant**: Non-monotonic threshold combinations MUST NOT be saved to DataStore.
2. **Schema & Key Stability**: DataStore preference keys, defaults, and display options remain 100% backward compatible.
3. **Architectural Contract Compliance**: All 4 contract tests (`ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, `ZoneOnboardingBannerContractTest`, `AthleteProfileContractAndLocalizationTest`) MUST continue passing without modification.
4. **Draft Retention**: Drafts (even temporarily non-monotonic) MUST survive configuration changes and tab switches.
5. **Clean-Room Regression**: 100% pass rate across the full test suite (~3,660+ unit tests).

---

## 4. Proposed Architectural Changes

### Component 1: `SettingsDataStore.kt` Atomic Persistence & Unified Loading
* **Single-Transaction Persistence (`REQ-UI-428.1`)**: Provide `suspend fun saveZoneProfile(profile: ZoneProfileState)` executing a single `dataStore.edit {}` block writing all zone thresholds (Z1..Z4 or Z1..Z6) and `ZoneDisplayOptions` together atomically.
* **Unified Loader (`REQ-UI-428.3`)**: Consolidate power zone migration and flow reading into `suspend fun loadZoneProfile(type: ZoneType): ZoneProfileState` and `suspend fun loadAllZoneProfiles(): List<ZoneProfileState>`, replacing 3x duplicated logic.

### Component 2: `ZonesSettingsViewModel.kt` & Draft Preservation
* **Retained State & Debouncing (`REQ-UI-428.2`)**: Manage profile state and in-flight drafts via `SavedStateHandle`. Debounce/serialize save dispatches so rapid typing triggers a single atomic write once monotonic.

### Component 3: `ZonesSettingsActivity.kt`, `AthleteProfileScreen.kt` & `AndroidManifest.xml`
* **Safe Insets & Activity Decoupling (`REQ-UI-428.4`)**: Replace `(context as Activity).window` with safe context unwrapping (`context.findActivity()?.window`); connect `ZoneSettingsScreen` and `ZoneProfileTabContent` to unified loading and atomic saving; delete dead `<activity>` from `AndroidManifest.xml` while preserving `ZonesSettingsActivity.kt` file structure and contract markers.

### UI Consistency (Rule 23)
* No visual design changes; existing Material 3 container colors, `ZonePreviewPill`, and typography strictly preserved.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Atomic Persistence & Unified Loader in `SettingsDataStore.kt` (`REQ-UI-428.1`, `REQ-UI-428.3`)**: Implement `saveZoneProfile(profile: ZoneProfileState)` with a single `dataStore.edit {}` and `loadZoneProfile(type: ZoneType): ZoneProfileState`.
* **Step 2: Retained State & Debounced Serialization (`REQ-UI-428.2`)**: Create `ZonesSettingsViewModel` (or state holder) with `SavedStateHandle` draft preservation across rotations and debounced atomic commit.
* **Step 3: Connect UI & Replace Unsafe Window Cast (`REQ-UI-428.4`)**: In `ZonesSettingsActivity.kt`, route `ZoneSettingsScreen` and `ZoneProfileTabContent` through the unified loader and atomic save; replace `(context as Activity)` cast with safe unwrapping; retain all contract markers and file structure.
* **Step 4: Excise Dead Manifest Declaration (`REQ-UI-428.4`)**: Remove `<activity android:name=".trainingtracker.activities.ZonesSettingsActivity" ...>` from `app/src/main/AndroidManifest.xml`.
* **Step 5: Unit & Contract Tests (`TST-UI-393.1`–`TST-UI-393.4`)**: Author `SettingsDataStoreZoneAtomicTest.kt` verifying single-transaction writes, draft survival across recreation, and unified loading. Run existing contract tests. Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"`.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit/contract tests, `TranslationParityTest`, and `./gradlew testDebugUnitTest`.
* **Rollback**: Branch isolation on `feature/ATT-4021` enables immediate atomic revert if regressions occur.
