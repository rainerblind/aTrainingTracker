# Stage 1 Analysis: ATT-4021 - Zone editing races, loses drafts and lives in unreachable ZonesSettingsActivity

**Ticket**: [ATT-4021](https://rainerblind.atlassian.net/browse/ATT-4021)  
**Parent Epic**: None | **Target Release**: `V4.9.40` | **Active Sprint**: Product Backlog | **Branch**: `feature/ATT-4021`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

Forensic code inspection confirms all 5 reviewer findings in `ZonesSettingsActivity.kt` and `AthleteProfileScreen.kt`:
1. **Concurrency Race on Rapid Typing**: In `ZonesSettingsActivity.kt` lines 148–160 (`saveProfile`) and 1006–1018 (`saveSingleProfile`), every keystroke where `isProfileMonotonic` holds launches a new `scope.launch(Dispatchers.IO)` writing 4–6 keys sequentially via separate `dataStore.saveHrZoneMax` calls (5–7 distinct `dataStore.edit {}` disk operations). Lacking debouncing or serialized dispatch, concurrent IO coroutines interleave and race, allowing stale intermediate values to overwrite later edits on disk.
2. **Draft Loss on Configuration Change**: Edits that are temporarily non-monotonic during typing are held purely in ephemeral `remember { mutableStateOf(...) }` (`allProfilesData` / `profileState`). Rotation or tab switches destroy the remember cache and trigger a reload from DataStore, silently dropping uncommitted user input.
3. **3x Copy-Pasted Loading Logic**: Zone loading and migration logic (`checkAndMigratePowerZones()`, `getZoneXMaxFlow().first()`, `getZoneDisplayOptionsFlow().first()`) is duplicated across `reloadProfiles` (lines 163–178), `LaunchedEffect(Unit)` (lines 182–198), and `loadProfile` (lines 988–1000).
4. **Dead Activity & Inverted Dependency**: `ZonesSettingsActivity` is declared in `AndroidManifest.xml` (line 145) with hardcoded `android:label="Training Zones"`, but is never started via `Intent` (navigation uses Compose `NavRoutes.TRAINING_ZONES_PATTERN`). Yet `AthleteProfileScreen.kt` (line 98) imports `ZoneProfileTabContent` from `activities/ZonesSettingsActivity.kt`, creating an inverted UI dependency.
5. **Unsafe Activity Cast**: Line 125 uses `(context as android.app.Activity).window` in `SideEffect`, risking `ClassCastException` in preview or non-Activity contexts.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Zone State & Persistence** | `ZonesSettingsActivity.kt` / `ZoneSettingsViewModel.kt` | Encapsulate zone state, deduplicate loading, debounce/serialize atomic DataStore edits (`dataStore.edit {}`), and preserve drafts across configuration changes. |
| **Athlete Profile Integration** | `AthleteProfileScreen.kt` | Consume unified zone UI/ViewModel without inverted Activity dependency. |
| **Manifest Hygiene** | `AndroidManifest.xml` | Remove unstarted `<activity android:name=".ZonesSettingsActivity">` or localize label. |
| **Compose Route** | `ATrainingTrackerApp.kt` | Host `ZoneSettingsScreen` backed by scoped ViewModel/state. |
| **Contract Tests** | `zones/ZonesSettingsHeaderContractTest.kt`, `ZonesSettingsPreviewPillsContractTest.kt`, `ZoneOnboardingBannerContractTest.kt`, `athleteprofile/AthleteProfileContractAndLocalizationTest.kt` | Ensure file paths, composable signatures, and contract assertions remain satisfied. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Debounce and serialize zone persistence into a single atomic `dataStore.edit {}` block per profile.
  2. Preserve in-flight drafts across screen rotation and configuration changes via ViewModel / `SavedStateHandle`.
  3. Consolidate 3x copy-pasted DataStore loading logic into a single reusable helper/repository method.
  4. Decouple `ZoneProfileTabContent` from `ZonesSettingsActivity` while strictly maintaining contract test compatibility.
  5. Remove dead manifest activity entry and eliminate unsafe `(context as Activity)` window cast.
* **Out-of-Scope Non-Goals**:
  1. No changes to DataStore preference key names, defaults, or schema contracts.
  2. No modification of `ZoneCalculatorDialog` calculation formulas or athlete metric database tables.
  3. No changes to `SensorFieldView` cockpit rendering or `ZoneGaugeMath`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-UI-172` (*Configurable Zone Visualization Options & Theme-Aware Live Preview*) and `REQ-UI-397.2` (*Unified Tabbed Profile & Zones Screen*), establishing `REQ-UI-428` (*Debounced Atomic Zone Persistence, SavedStateHandle Draft Preservation, and Deduplicated Zone UI Architecture*).
* **Historical Origin & Commit Trace**: `ATT-1265` / `ATT-1390` (`e3966ebf`) introduced `ZoneSettingsScreen`; `ATT-3184` (`0da639ba`) added 7 power zones; `ATT-3851` (`d1b5f9bf`) colocated `ZoneProfileTabContent` in `ZonesSettingsActivity.kt` to share UI with `AthleteProfileScreen`.
* **Root Reason for Existing Formulation**: `ZoneProfileTabContent` was appended directly into `ZonesSettingsActivity.kt` during drawer restructuring (`ATT-3851`) to avoid introducing a new ViewModel file late in the sprint. Direct unthrottled IO launches were used as quick inline callbacks.
* **Preservation of Core Invariants**: Monotonicity validation, `ZoneDisplayOptions` toggles, live theme-aware preview, `ZoneCalculatorDialog` workflow, and all 4 architectural contract tests remain 100% preserved.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Monotonicity enforcement: non-monotonic values MUST NOT be persisted to DataStore.
  2. 100% backward compatibility of DataStore keys (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`).
  3. Architectural contract tests (`TST-UI-347`, `TST-UI-348`, `TST-UI-337`, `TST-UI-360`) MUST continue to pass.
  4. Zero loss of user-entered drafts during screen rotation or navigation tab switches.
* **Risk Rating**: **MEDIUM** — Central athletic settings touched; architectural contract tests assert source file structure.
