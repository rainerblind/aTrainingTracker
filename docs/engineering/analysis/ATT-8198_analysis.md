# Stage 1 Analysis: ATT-8198 - Mandate Behavioral Compose UI Tests & Phase Out Reflection-Only UI Tests

**Ticket**: [ATT-8198](https://rainerblind.atlassian.net/browse/ATT-8198) | **Sub-task**: [ATT-8982](https://rainerblind.atlassian.net/browse/ATT-8982) (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät` | **Branch**: `feature/ATT-8198`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
Many `ui/` unit tests satisfy compilation/name existence via Java reflection (`Class.forName("...Kt")` + `declaredMethods`) or tautological data-class assertions without composing any `@Composable` tree (`createComposeRule()`). This prevents `SWE.4` from achieving genuine `CL 2` (`PA 1.1 = F`) behavioral UI verification.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: Replacing `File.readText()` UI source-greps under `REQ-TST-002` (`docs/coding_standards.md` §5.4:246) and `REQ-PRO-049` (`ATT-6564`..`ATT-6569`) steered authors toward `Class.forName("...Kt")` / `declaredMethods` stubs instead of `createComposeRule()`, while no governance contract test ratcheted `Class.forName("...Kt")` reflection-only UI tests.
* **Independent Peer Review & Verification (live `sprint/2026-11-18_02_Spät`)**:
  * **Ticket claim vs. live `createComposeRule()`**: Ticket cites `1` file (`RadarLightQuickSwitchSheetTest.kt:50`, now `:76`); live codebase has **31** files in `app/src/test/java/` (`26` in `ui/`, `0` in `app/src/androidTest/` out of `4` files).
  * **Live reflection baseline (`ui/` = `564` files; all `app/src/test/java/` = `1,155` files)**:
    * `Class.forName(...Kt")` (multiline-aware) in `ui/`: **`180`** files (**`178`** without `createComposeRule()`; `2` with both: `RadarLightQuickSwitchSheetTest.kt:266`, `SensorSourceDialogContractTest.kt:67`).
    * `Class.forName(...Kt")` + `declaredMethods` in `ui/`: **`156`** files (**`154`** without `createComposeRule()`).
    * `Class.forName` (`*Kt` outside `ui/`): `18` files (`banalservice/ui/`, `migration/`, `zones/`). Non-`*Kt` `ui/` `Class.forName` includes legitimate classpath/VM checks (`CoreDependencyAlignmentTest.kt`, `AdvancedTuningModularityTest.kt`, `WorkoutRepositoryDeleteTest.kt`).
  * **`app/source-reading-baseline.txt`**: `105` entries (`109` lines incl. comments), `SourceTextReadingAuditContractTest.kt:39-62` (`22` guards, ceiling `105`).
  * **`RadarSettingsSectionTest.kt` (`ui/settings/radar/`)**: `227` lines, **0** Compose/Robolectric imports; lines `38-57` & `71-108` tautologically assert `TuningConfig(...)` booleans/enums (even referencing retired `RadarLightControlMode` dropdown removed in `ATT-7991`, `RadarSettingsSection.kt:397-415`), yet `ATT-7991_walkthrough.md:29` logged it as `"Compose UI Test"`.
  * **Gradle Support (`app/build.gradle`)**: `unitTests.includeAndroidResources = true` (`:120`), `compose-bom:2026.09.00` (`:265`), `ui-test-manifest` (`:274`), `robolectric:4.14.1` (`:309`), `ui-test-junit4` (`:312`) are already configured.

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add a contract test in `app/src/test/.../build/` + monotonically shrinking baseline file banning new `Class.forName("...Kt")` reflection-only UI tests under `ui/` without `createComposeRule()` (analogous to `SourceTextReadingAuditContractTest.kt`).
  2. Update `docs/coding_standards.md` §5.4, `.agents/skills/stage4-implementation/SKILL.md`, and `.agents/skills/stage5-verification/SKILL.md` to mandate `createComposeRule()` behavioral UI tests whenever `@Composable` UI is added/modified.
  3. Remediate `RadarSettingsSectionTest.kt` to compose `RadarSettingsSection` (`RadarSettingsSection.kt:75`) via Robolectric `createComposeRule()`, verifying conditional visibility (`FULL_SCREEN` hides clear-mode `:128-146`; `radarSpatialAudioEnabled` reveals spatial sub-controls `:282-312`) and interactive callbacks/DataStore updates.
* **Out-of-Scope Non-Goals**: Refactoring the `178` existing legacy reflection-only `ui/` tests; touching `app/src/main/` production code; migrating `AndroidViewModelReflectionContractTest.kt` or `build/`/`LEGITIMATE_GOVERNANCE_GUARDS` reflection checks.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-132` (`TST-PRO-132`) refining `REQ-TST-002` (`docs/coding_standards.md` §5.4), `REQ-PRO-049` (`SourceTextReadingAuditContractTest.kt`), and remediating `TST-CON-031.5b` / `TST-TRK-062.4` (`RadarSettingsSectionTest.kt`).
* **Historical Origin & Commit Trace**: `REQ-TST-002` (`ATT-4028`) recommended `Class.forName`/`declaredMethods` to replace UI `File.readText()` greps; `REQ-PRO-049` (`ATT-6186`, `c91e62b6`) & `ATT-6564`–`ATT-6569` (`12f5f47c`, `1298a643`, `ef50449a`, `3b5a5b41`) phased out `src/main` reads but inadvertently normalized `Class.forName("...Kt")` UI stubs.
* **Root Reason for Existing Formulation**: At `ATT-4028`, Robolectric Compose test patterns were not yet standardized across the repo, so reflection stood in for UI verification while excision/decoupling checks protected refactoring.
* **Preservation of Core Invariants**: Preserve legitimate architectural/excision/classpath reflection guards (`AndroidViewModelReflectionContractTest`, `InterfaceSignatureDriftContractTest`, `AdrCompletenessContractTest`, `CoreDependencyAlignmentTest`, non-`*Kt` decoupling checks) and `SourceTextReadingAuditContractTest` (`22` guards, `105` baseline, `0` new `src/main` reads).

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-132` / `TST-PRO-132`; clarifies `REQ-TST-002` (`docs/coding_standards.md` §5.4); strengthens `TST-CON-031.5b` / `TST-TRK-062.4`. **`SYS.2` / `SYS.3` Impact**: None (no hardware, BLE/ANT+, HAL, REST API, or external data exchange changes).
* **Affected Architecture Elements**: `app/src/test/.../build/` (new contract test), `app/` (new baseline `.txt`), `app/src/test/.../ui/settings/radar/RadarSettingsSectionTest.kt`, `docs/coding_standards.md` §5.4, `.agents/skills/stage4-implementation/SKILL.md`, `.agents/skills/stage5-verification/SKILL.md`, `docs/requirements/req_process_stability_part2.md`, `docs/tests/tst_process_stability_part2.md`.
* **Verification Impact & Test Surface**: Targeted Robolectric Compose run (`RadarSettingsSectionTest`), new reflection-ratchet contract test, `SourceTextReadingAuditContractTest` (`0` new `src/main` reads), `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **MEDIUM** — touches CI contract ratchets, process skills, and Robolectric Compose wiring for `RadarSettingsSection` (`TuningPreferencesDataStore`, `TrainingApplication`, `RadarTrafficSimulator`). Mitigated by mocking/isolating DataStore & static bridges in Robolectric, using multiline-aware regex strictly on `ui/` `*Kt` reflection without `createComposeRule()`, and keeping `app/src/main/` untouched.

## 6. Architectural Strategy & High-Level Solution
1. Add `UiReflectionOnlyTestAuditContractTest` + `app/ui-reflection-baseline.txt` (`178` legacy `ui/` files matching `Class.forName(...Kt")` without `createComposeRule()`), enforcing `0` unallowlisted additions and `0` stale entries (analogous to `SourceTextReadingAuditContractTest.kt`).
2. Clarify `docs/coding_standards.md` §5.4 (distinguishing legitimate excision/ABI reflection from banned reflection-only UI tests) and update `stage4-implementation` & `stage5-verification` skills to require `createComposeRule()` behavioral assertions for new/modified `@Composable` UI.
3. Convert `RadarSettingsSectionTest.kt` to `@RunWith(RobolectricTestRunner::class)` + `createComposeRule()` testing real rendered semantics and callbacks of `RadarSettingsSection` while preserving the XML locale budget checks (`:131-226`) with `0` `src/main` source-code reads.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `0` edits to `app/src/main/`; `0` new `src/main` source-text reads (`REQ-PRO-049`); `SourceTextReadingAuditContractTest` (`22`/`105`) preserved; `N/A - no UI strings` (`values*/strings*.xml` untouched); existing `build/` & classpath reflection tests preserved; Human Decision Gate preserved.
* **Risk Rating**: **MEDIUM** (CI ratchet + governance skills + Robolectric Compose integration of `RadarSettingsSection`; zero production code risk).
