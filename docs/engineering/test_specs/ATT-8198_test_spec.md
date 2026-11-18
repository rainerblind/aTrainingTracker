# Stage 2: Requirement & Test Specification - ATT-8198: Behavioral Compose UI Tests & Reflection-Only Ratchet

**Ticket**: [ATT-8198](https://rainerblind.atlassian.net/browse/ATT-8198) | **Sub-task**: [ATT-8983](https://rainerblind.atlassian.net/browse/ATT-8983) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät`  
**Requirement Mapping**: `REQ-PRO-132` (*Behavioral Compose UI Testing Mandate & Reflection-Only UI Test Ratchet*)  
**Test Spec ID**: `TST-PRO-132` (strengthens `TST-CON-031.5b`, `TST-TRK-062.5`) | **Branch**: `feature/ATT-8198`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-132`)

### 1.1 Problem Statement & Rationale
Out of `564` `trainingtracker/ui/` + `31` `banalservice/ui/` unit test files, `189` (`178` + `11`) rely on `Class.forName("...Kt")` JVM reflection without `createComposeRule()`, and `RadarSettingsSectionTest.kt` uses tautological `TuningConfig` assertions (including retired `RadarLightControlMode` controls) while claimed as a Compose UI test (`ATT-7991_walkthrough.md:29`). Mandating behavioral `createComposeRule()` UI tests and ratcheting out new reflection-only UI tests brings `SWE.4` to genuine `CL 2`.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **`SYS.2` / `SYS.3` Impact**: None (zero hardware, RF, HAL, REST, or external interface changes).

### 1.3 Functional & Architectural Requirements
1. **`REQ-PRO-132.1` (Reflection-Only UI Test Ratchet & Baseline)**: `UiReflectionOnlyTestAuditContractTest.kt` SHALL scan all `.kt`/`.java` files under `app/src/test/java/` whose relative path matches `(?:^|/)ui/` (`com/atrainingtracker/trainingtracker/ui/` [`178`] and `com/atrainingtracker/banalservice/ui/` [`11`]) and flag any file invoking multiline-aware `Class.forName("...Kt")` without `createComposeRule`. Existing legacy entries SHALL reside in `app/ui-reflection-baseline.txt` (`189` ceiling), enforcing `(detected - baseline).isEmpty()` and `(baseline - detected).isEmpty()`, with `0` reads of `app/src/main/`.
2. **`REQ-PRO-132.2` (Legitimate Reflection Guard Protection)**: The ratchet SHALL NOT flag `build/` reflection suites (`AndroidViewModelReflectionContractTest`, `InterfaceSignatureDriftContractTest`, `AdrCompletenessContractTest`), non-`*Kt` classpath/decoupling checks (`CoreDependencyAlignmentTest`, `WorkoutRepositoryDeleteTest`), or tests using `createComposeRule()` alongside signature checks (`RadarLightQuickSwitchSheetTest`, `SensorSourceDialogContractTest`).
3. **`REQ-PRO-132.3` (Standard & Skill Behavioral Compose Mandate)**: `docs/coding_standards.md` §5.4, `.agents/skills/stage4-implementation/SKILL.md`, and `.agents/skills/stage5-verification/SKILL.md` SHALL distinguish legitimate excision/ABI reflection (`build/` & non-`*Kt`) from prohibited reflection-only UI tests and mandate `createComposeRule()` behavioral assertions whenever `@Composable` UI is created or modified.
4. **`REQ-PRO-132.4` (`RadarSettingsSectionTest` Remediation)**: `RadarSettingsSectionTest.kt` SHALL retire stale `RadarLightControlMode` / `TuningConfig` tautologies (`:38-129`) and compose `RadarSettingsSection` via `@RunWith(RobolectricTestRunner::class)` + `createComposeRule()`, verifying conditional visibility, retired-control absence, and interactive callbacks/DataStore updates while preserving 9-locale XML checks (`:131-226`) with `0` `src/main/` reads.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* a new test under `trainingtracker/ui/` or `banalservice/ui/` calling `Class.forName("...Kt")` without `createComposeRule()`, *When* `UiReflectionOnlyTestAuditContractTest` runs, *Then* the build fails unless the file is on `app/ui-reflection-baseline.txt` (`<= 189`, `0` stale entries).
* **Criterion 2**: *Given* `RadarSettingsSectionTest.kt`, *When* `:app:testDebugUnitTest --tests "*.RadarSettingsSectionTest"` runs, *Then* `RadarSettingsSection` composes via `createComposeRule()`, verifying `FULL_SCREEN` clear-mode hiding, `radarSpatialAudioEnabled` conditional sub-controls, retired control absence, interactive callbacks, and preserved XML locale checks.
* **Criterion 3**: *Given* `build/` & non-`*Kt` reflection guards and `SourceTextReadingAuditContractTest`, *When* executed, *Then* `0` false positives occur (`22` guards, `105` source-reading baseline preserved).

### 1.5 System Invariants
Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline) preserved; `docs/coding_standards.md` §1.1–§1.5 & `AspiceSweAuditContractTest` skill markers preserved; `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-PRO-132`)

### Test Case 1: Reflection-Only UI Test Ratchet & Tripwire (`TST-PRO-132.1`)
* **Target**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/UiReflectionOnlyTestAuditContractTest.kt` + `app/ui-reflection-baseline.txt`
* **Action**: Scan `(?:^|/)ui/` for multiline `Class.forName("...Kt")` without `createComposeRule`; test positive live baseline (`189` ceiling, `0` stale, `0` unallowlisted) and negative synthetic tripwire (`com/atrainingtracker/trainingtracker/ui/fake/NewReflectionOnlyUiTest.kt`).
* **Expected**: Live repo passes `0` violations; tripwire fails deterministically.

### Test Case 2: Legitimate Reflection & Governance Guard Preservation (`TST-PRO-132.2`–`.3`)
* **Target**: `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`, `AndroidViewModelReflectionContractTest`, `InterfaceSignatureDriftContractTest`, `AdrCompletenessContractTest`, `CoreDependencyAlignmentTest`, `RadarLightQuickSwitchSheetTest`.
* **Expected**: All pass `100%`; `docs/coding_standards.md` §5.4 and Stage 4/5 skills contain behavioral `createComposeRule()` mandate with `0` broken markers.

### Test Case 3: `RadarSettingsSectionTest` Behavioral Compose UI Suite (`TST-PRO-132.4`)
* **Target**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/radar/RadarSettingsSectionTest.kt`
* **Action**: Compose `RadarSettingsSection` under Robolectric `createComposeRule()` with isolated `TuningPreferencesDataStore` & mocked static `TrainingApplication`/`RadarTrafficSimulator`/`RadarAlertManager`.
* **Expected**: `FULL_SCREEN` hides `radar_overlay_clear_mode` (`assertDoesNotExist()`); `RIGHT` displays it; `radarSpatialAudioEnabled` toggles `radar_traffic_orientation_title`/`radar_lane_change_tick_title`; retired `RadarLightControlMode` controls absent while `radar_brake_light_title` is displayed & clickable; `onSimulationToggled` & DataStore updates fire; preserved XML checks (`:131-226`) pass; `0` `src/main/` reads.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-132.1` | Contract Audit | `UiReflectionOnlyTestAuditContractTest` | `REQ-PRO-132.1` | Planned |
| `TST-PRO-132.2` | Contract Audit | `SourceTextReadingAuditContractTest` + `build/*` | `REQ-PRO-132.2` | Planned |
| `TST-PRO-132.3` | Governance Parity | `AspiceSweAuditContractTest` + §5.4 + Skills | `REQ-PRO-132.3` | Planned |
| `TST-PRO-132.4` | Robolectric Compose UI | `RadarSettingsSectionTest` | `REQ-PRO-132.4`, `TST-CON-031.5b`, `TST-TRK-062.5` | Planned |
