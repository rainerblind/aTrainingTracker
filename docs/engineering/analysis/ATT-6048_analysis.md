# Stage 1 Analysis: ATT-6048 - TuningPreferencesDataStore & SettingsDataStore: 4 Missing Auto-Pause Keys in saveTuningConfig and BoundedPref Refactoring

**Ticket**: [ATT-6048](https://issue-tracker/ATT-6048)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Branch**: `feature/ATT-6048` (from `sprint/2026-11-20_03_Nacht` @ `a73ca07f`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: In `TuningPreferencesDataStore.kt` (`2,410` LoC), `saveTuningConfig(config: TuningConfig)` (`:1478-1731`) omits 4 Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC`) despite their presence in `TuningConfig` (`:437, :440, :443, :446`), `ALL_KEYS` (`:729, :732, :735, :738`), `mapAutoPausePreferences` (`:855, :864-879`), and `update*` setters (`:2203-2349`). Furthermore, `.coerceIn(...)` bounds are duplicated across 3+ paths (`mapPreferencesToConfig`, `saveTuningConfig`, `update*`), and `SettingsDataStore.kt` hardcodes default `HR_RUN`/`HR_BIKE`/`PWR_BIKE` zone boundaries across 7 locations (`:73-75, :302-324, :333, :362, :782-811, :1108-1125, :1138-1157`).
* **Expected Behavior**: `saveTuningConfig` clamps and persists all 4 missing Auto-Pause keys (verified by a reflection-assisted round-trip unit test), standard numeric bounds delegate to declarative `BoundedPref` descriptors with dependent clamps intact, and `SettingsDataStore` derives zone defaults from centralized constants without behavior change.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: Organic growth across `TuningConfig`, `tuningConfigFlow`, `saveTuningConfig`, and `update*` required manual synchronization of every key and `.coerceIn` range; when `ATT-4839` / `ATT-4840` added resume debounce and start-in-auto-pause keys, `saveTuningConfig` was missed.
* **Independent Peer Review & Verification (Live `sprint/2026-11-20_03_Nacht` Audit)**:
  * **Bug Confirmed, Zero Current User Data Loss (P3)**: Only `AdvancedTuningScreen.kt:253` and `AdvancedTuningDialog.kt:235` call `saveTuningConfig(persistedConfig.copy(...))`, never touching Auto-Pause fields, while `AutoPauseTabsScreen` uses `update*` (`:2203-2349`) and reset uses `ALL_KEYS` (`:2398`).
  * **Schema Drift Since Epic Review (`67 -> 104` Fields)**: `TuningConfig` (`:384-490`) now has **104** constructor fields (not 67). Besides the 7 hardcoded `mapFollowMe*` fields (`REQ-MAP-050`, purged in `LEGACY_MAP_CAMERA_KEYS`) and 2 derived legacy Auto-Pause fields (`autoPauseSpeedThresholdKmh`, `autoPauseDebounceSec`, `:1262-1263`), `radarAutoLightControl` and `radarSmartLightAutomation` (`:1037-1046, :1706-1707`) are derived from `radarLightControlMode` whenever `KEY_RADAR_LIGHT_CONTROL_MODE` is written (and `:477` couples default `radarLightControlMode` to `radarLightAutoMode`). Round-trip tests must treat these derived/hardcoded fields accordingly (no naive independent override on derived fields).
  * **Testability & LoC Guardrails**: `TuningPreferencesDataStore` (`:535`) has a private constructor (`TuningPreferencesDataStoreLifecycleTest.kt:67` asserts non-public!) and resolves `context.dataStore` via `SettingsDataStore.kt:107` (returning a fresh `InMemoryPreferencesDataStore()` per access when `filesDir` is unmocked). `ArchitectureConformanceContractTest.kt:122` enforces a **2,410 LoC ceiling** on `TuningPreferencesDataStore.kt` (currently exact `2,410` LoC).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals (`3 SP`)**: (1) Clamp & persist the 4 missing Auto-Pause keys in `saveTuningConfig`; (2) Add reflection-backed `TuningConfig` round-trip + clamp/legacy-mirror characterization tests (`TST-SET-*`); (3) Introduce declarative `BoundedPref` descriptors in `TuningPreferencesDataStore.kt` (or same package) keeping `TuningPreferencesDataStore.kt <= 2,410` LoC; (4) Centralize `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` default zone constants in `SettingsDataStore.kt`.
* **Out-of-Scope Non-Goals**: No changes to `TuningConfig` public shape (`SportAutoPauseConfig` rejected as breaking ripple), no new `TrackerService.invalidateAutoPauseCache()` calls in `updateAutoPause{Bike,Run,Other}Enabled`, no UI/Compose/strings edits, and no change to `ALL_KEYS` outside verified scope (note: `KEY_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT` `:650` is absent from `ALL_KEYS` — log as follow-up observation only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-073` (`docs/requirements/req_settings_profiles.md:853`), `REQ-TRK-018`/`043`/`044` (`REQ-MAP-050`, `REQ-TRK-061.4`), targeting `TuningPreferencesDataStore.kt` and `SettingsDataStore.kt`.
* **Historical Origin & Commit Trace**: `REQ-SET-073` (`ATT-1304`) established clamped DataStore tuning; `ATT-4839`/`ATT-4840` added resume debounce & start-in-auto-pause; `ATT-8203` added the `2,410` LoC monolith ceiling; `ATT-7874` (`6dffe9e7`) locked private constructor scope.
* **Root Reason for Existing Formulation**: Dependent clamps (`mediumDim >= clampedFullDim`, `slopeSteep >= clampedSlopeFlat`, `dfaA1 T1/T2` gap), `navigationCueDismiss == 0 -> DEFAULT`, legacy key fallbacks/mirrors, `TrainingApplication` cluster sync, and selective `invalidateAutoPauseCache()` prevent unsafe configs and preserve backward compatibility.
* **Preservation of Core Invariants**: Refactoring via `BoundedPref` preserves 100% of dependent clamp ordering, `0 -> DEFAULT` semantics, legacy fallback/mirroring, `TrainingApplication` sync, `invalidateAutoPauseCache()` call sites, non-public `TuningPreferencesDataStore` constructor, and zero `runBlocking` in `SettingsDataStore.kt` (`DataStoreHotPathContractTest`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-SET-073` (adds explicit `saveTuningConfig` full-persistence contract & `BoundedPref`/zone-default centralization invariants). `SYS.2`/`SYS.3`: None (internal preferences persistence only; zero hardware/BLE/API boundary changes). Localization: N/A (`0` string changes).
* **Affected Architecture Elements**: `TuningPreferencesDataStore.kt` (`<= 2,410` LoC), `SettingsDataStore.kt`; zero `docs/architecture.md` subsystem changes (`Rule 8.8`).
* **Verification Impact & Test Surface**: Existing `settings/*` tests (`TuningConfigTest`, `TuningPreferencesDataStoreAutoPauseTest`, `TuningPreferencesDataStoreDfaA1Test`, `DataStoreHotPathContractTest`), `ArchitectureConformanceContractTest`, plus new round-trip & `BoundedPref`/zone-default tests.
* **Risk Assessment & Invariants**: **LOW–MEDIUM** (`3 SP`) — mitigated by round-trip characterization tests covering all 104 `TuningConfig` fields (accounting for derived/hardcoded fields), preserving `ArchitectureConformanceContractTest` (`<= 2,410` LoC), and `TuningPreferencesDataStoreLifecycleTest`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `TuningPreferencesDataStore.kt <= 2,410` LoC; non-public constructor; zero `runBlocking` added to `SettingsDataStore.kt`; 100% behavioral parity on dependent clamps, legacy mirrors, `TrainingApplication` sync, and `invalidateAutoPauseCache()`.
* **Risk Rating**: **LOW–MEDIUM** — localized to 2 settings DataStore files with strong unit/contract test coverage and zero UI/service signature breaks.
