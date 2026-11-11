# Stage 1 Analysis: ATT-6569 - Phase out source-text-reading tests in banalservice.ui and migration packages

**Ticket**: [ATT-6569](https://rainerblind.atlassian.net/browse/ATT-6569)  
**Sub-task**: `[Analysis]` [ATT-6970](https://rainerblind.atlassian.net/browse/ATT-6970)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6569`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment of 2026-11-08 (baseline `develop @ sprint/2026-45.11`), Remediation Item **C-12** (SWE.4 BP) identified extensive legacy unit tests that read production Kotlin/Java source code (`.readText()`, `readLines()`, `readString()`, `useLines()`, `bufferedReader()`) targeting `src/main` to assert token presence or regex patterns rather than exercising runtime bytecode and behavioral contracts.

This pattern violates `docs/coding_standards.md` §5.4 (*Contract Test Standards & Prohibition of UI Literal Source-Grep*), which mandates:
> "Unit and contract tests **MUST NOT** read source code files (`File.readText()`) to assert on Compose design tokens, raw string/emoji literals, internal call parameters, or raw SVG path commands... Text-grep assertions freeze non-functional formatting, whitespace, and private implementation details, triggering false-positive test failures during innocent refactors while verifying zero runtime behavior."

Ticket **ATT-6569** executes the remediation for two major subsystems:
1. `com.atrainingtracker.banalservice.ui` (14 legacy tests)
2. `com.atrainingtracker.trainingtracker.migration` (10 legacy tests)

Total: **24 target test classes** to be completely decoupled from source-reading, migrating to behavioral, programmatic reflection, and `LocalizationTestCache`-backed assertions. Consequently, `app/source-reading-baseline.txt` will be ratcheted down from **275** to **251** legacy tests, and `SourceTextReadingAuditContractTest.kt` bounds updated accordingly.

---

## 2. Inventory & Audit of the 24 Target Tests

An exhaustive audit of `app/source-reading-baseline.txt` on branch `sprint/2026-46.2` (HEAD `0d437b9c`) identified exactly 24 files across the targeted packages:

### A. Subsystem `banalservice/ui` (14 Tests)
1. `com/atrainingtracker/banalservice/ui/devices/DeviceDiscoveryLocalizationTest.kt` (reads `src/main/res/*/strings.xml`)
2. `com/atrainingtracker/banalservice/ui/devices/DeviceStatusRowContractTest.kt` (reads `DeviceStatusRow.kt`)
3. `com/atrainingtracker/banalservice/ui/devices/DeviceTypeSelectionDialogTest.kt` (reads `DeviceTypeSelectionDialog.kt`)
4. `com/atrainingtracker/banalservice/ui/devices/PairingLocalizationTest.kt` (reads `src/main/res/*/strings.xml`)
5. `com/atrainingtracker/banalservice/ui/devices/ant/AntServicesStatusContractTest.kt` (reads `AntServicesStatusCard.kt`, `AntServicesStatusSheet.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`)
6. `com/atrainingtracker/banalservice/ui/devices/ant/AntStartupCheckContractTest.kt` (reads `AntStartupCheckViewModel.kt`, `AntStartupCheckNotification.kt`)
7. `com/atrainingtracker/banalservice/ui/devices/ant/AntStatusLocalizationTest.kt` (reads `src/main/res/*/strings.xml`)
8. `com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreenContractTest.kt` (reads `DeviceListScreen.kt`)
9. `com/atrainingtracker/banalservice/ui/devices/devicelist/SwapOrUpgradeDialogContractTest.kt` (reads `SwapOrUpgradeDialog.kt`, `strings.xml`)
10. `com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedPermissionContractTest.kt` (reads `DevicesTabbedScreen.kt`, `AndroidManifest.xml`)
11. `com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenContractTest.kt` (reads `DevicesTabbedScreen.kt`)
12. `com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenPromptContractTest.kt` (reads `DevicesTabbedScreen.kt`)
13. `com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialogContractTest.kt` (reads `EditSportTypeDialog.kt`)
14. `com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreenContractTest.kt` (reads `SportTypesTabsScreen.kt`)

### B. Subsystem `migration` (10 Tests)
1. `com/atrainingtracker/trainingtracker/migration/BackupCloudProviderUnificationTest.kt` (reads `BackupTabContent.kt`, `ImportBackupTabsScreen.kt`, `strings.xml`)
2. `com/atrainingtracker/trainingtracker/migration/BackupManagerTest.kt` (reads `BackupManager.kt`)
3. `com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt` (reads `BackupWorker.kt`)
4. `com/atrainingtracker/trainingtracker/migration/ImportBackupMapResilienceTest.kt` (reads `GpxSaxHandler.kt`, `TcxSaxHandler.kt`, `FitSaxHandler.kt`)
5. `com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt` (reads `ImportBackupTabsScreen.kt`, `strings.xml`)
6. `com/atrainingtracker/trainingtracker/migration/ImportFormatFilePickerContractTest.kt` (reads `ImportBackupTabsScreen.kt`)
7. `com/atrainingtracker/trainingtracker/migration/ImportFormatFilePickerLocalizationTest.kt` (reads `src/main/res/*/strings.xml`)
8. `com/atrainingtracker/trainingtracker/migration/ImportFormatLocalizationTest.kt` (reads `src/main/res/*/strings.xml`)
9. `com/atrainingtracker/trainingtracker/migration/ImportNavigationLocalizationTest.kt` (reads `src/main/res/*/strings.xml`)
10. `com/atrainingtracker/trainingtracker/migration/RestoreCardContractTest.kt` (reads `RestoreTabContent.kt`, `BackupTabContent.kt`, `ImportBackupTabsScreen.kt`, `strings.xml`)

---

## 3. Categorization & Behavioral Replacement Strategy

The 24 test classes fall into three concrete operational patterns:

### Pattern 1: XML Localization String Verification (7 Tests)
- **Files**: `DeviceDiscoveryLocalizationTest`, `PairingLocalizationTest`, `AntStatusLocalizationTest`, `ImportFormatFilePickerLocalizationTest`, `ImportFormatLocalizationTest`, `ImportNavigationLocalizationTest`, plus localization methods in `BackupCloudProviderUnificationTest` and `RestoreCardContractTest`.
- **Existing Anti-Pattern**: Tests invoke `findFile("src/main/res/$locale/strings.xml").readText()` and parse raw XML lines via regex. This violates `SourceTextReadingAuditContractTest` scanning.
- **Replacement Strategy**: Migrate all XML reading to `LocalizationTestCache.getStrings(locale)` or `LocalizationTestCache.getRawContent(locale)`.
  - `LocalizationTestCache` is already an approved, thread-safe in-memory cache and an explicit member of `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS` (`REQ-PRO-025`, `TST-PRO-018`).
  - Unit tests query parsed key-value string maps directly (`strings[key]`), asserting key existence, non-emptiness, and format specifiers (`%s`, `%1$s`) behaviorally without performing direct file I/O or referencing `src/main`.

### Pattern 2: Component Structure, Reflection, and Parameter Contract Tests (13 Tests)
- **Files**: `DeviceStatusRowContractTest`, `DeviceTypeSelectionDialogTest`, `SwapOrUpgradeDialogContractTest`, `AntServicesStatusContractTest`, `AntStartupCheckContractTest`, `DeviceListScreenContractTest`, `DevicesTabbedScreenContractTest`, `DevicesTabbedScreenPromptContractTest`, `EditSportTypeDialogContractTest`, `SportTypesTabsScreenContractTest`, `ImportFormatCardsContractTest`, `ImportFormatFilePickerContractTest`, `RestoreCardContractTest`.
- **Existing Anti-Pattern**: Tests read `.kt` source files to verify function declarations, parameter defaults, composable calls, or theme references.
- **Replacement Strategy**:
  - Replace `.readText()` grep with Java/Kotlin reflection (`Class.forName(...)`, `declaredMethods`, `declaredFields`, parameter types).
  - Top-level Kotlin Composable functions (e.g., `DeviceStatusRow`, `DeviceTypeSelectionDialog`) compile to static methods on generated `*Kt` classes (e.g., `DeviceStatusRowKt.class`). Reflection cleanly asserts method existence, argument signatures, and annotations (`@Composable`).
  - Extract pure business logic / string resolution helpers where appropriate (e.g. `resolveCloudButtonTextRes`, `resolveAutomatedBackupSummaryRes` in `BackupTabContent.kt`), testing them with pure unit assertions.
  - Verify UI contracts and dialog callbacks using Robolectric / unit models and state assertions.

### Pattern 3: Lifecycle, Runtime Policy, and Concurrency Guards (4 Tests)
- **Files**: `DevicesTabbedPermissionContractTest`, `BackupManagerTest`, `BackupWorkerTest`, `ImportBackupMapResilienceTest`.
- **Existing Anti-Pattern**: Tests grep source for strings like `"StandardCopyOption.ATOMIC_MOVE"`, `"ExistingPeriodicWorkPolicy.UPDATE"`, `"Manifest.permission.BLUETOOTH_SCAN"`.
- **Replacement Strategy**:
  - `DevicesTabbedPermissionContractTest`: Retain and strengthen the state-machine simulation tests (`testScanPermissionStateSimulation_*` and `testPairingFlow_permissionElevationPreservesSelectedPairingProtocol`). Remove the brittle source-text file reads on `DevicesTabbedScreen.kt` and `AndroidManifest.xml` (the manifest is already comprehensively guarded by `ManifestAndBuildConfigContractTest`).
  - `BackupManagerTest`: Test atomic backup behavior directly using temporary folders and file assertions rather than source inspection.
  - `BackupWorkerTest`: Test Worker configuration and scheduling via `WorkManager` test harness / `TestListenableWorkerBuilder` rather than string matching.
  - `ImportBackupMapResilienceTest`: Test parser SAX handlers with malformed/legacy XML/FIT stream inputs directly.

---

## 4. Ratcheting Mechanism & Baseline Debt Reduction

* **Current Baseline Debt**: 275 tests in `app/source-reading-baseline.txt`.
* **Tests Remediated**: 24 tests.
* **New Ratcheted Baseline Debt**: `275 - 24 = 251` tests.
* **Audit Updates in `SourceTextReadingAuditContractTest.kt`**:
  - `baseline.size <= 251`
  - `assertEquals(251, baseline.size)`
  - Tripwire `(baseline - detectedReaders).isEmpty()` guarantees that none of the 24 remediated tests retain any hidden `src/main` reads, and no stale entries remain in the baseline.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), `docs/coding_standards.md` §5.4.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-12 (2026-11-08); initial audit infrastructure in ATT-6186 (`c91e62b6`), governance guard stabilization in ATT-6731 (`88ce1d51`).
* **Root Reason for Existing Formulation**: Tests were historically written as quick "contract checks" asserting that specific strings or methods were added to source files during refactor tickets. While well-intentioned, these tests created massive maintenance debt and false security.
* **Preservation of Core Invariants**:
  1. No legitimate governance guard from `LEGITIMATE_GOVERNANCE_GUARDS` is removed or modified.
  2. All functional assertions (localization parity across 9 languages, dialog callbacks, permission flow preservation, backup safety) are fully maintained or strengthened by the behavioral replacements.
  3. The ratcheting ceiling strictly decreases without regressions.

---

## 6. User Scope Grounding (ATT-1250) & Complexity Guardrails

* **In-Scope**:
  - Refactoring the 14 `banalservice/ui` tests to remove all `readText()` / `src/main` reads.
  - Refactoring the 10 `migration` tests to remove all `readText()` / `src/main` reads.
  - Extracting stateless resolution helpers in `BackupTabContent.kt` where necessary to enable pure unit testing.
  - Removing all 24 entries from `app/source-reading-baseline.txt`.
  - Updating ratcheted ceiling and exact count in `SourceTextReadingAuditContractTest.kt` from 275 to 251.
  - Verifying 100% pass rate with `./gradlew testDebugUnitTest --tests "*SourceTextReadingAuditContractTest*"`.
* **Out-of-Scope (Deferred to Future Tickets)**:
  - Remaining 251 legacy source-reading tests in `ui.map`, `ui.tracking`, `ui.aftermath`, `ui.settings`, `ui.routes`, etc. (covered by tickets ATT-6564..ATT-6568).
  - Any production functional changes unrelated to testability refactoring.

---

## 7. System Invariants & Risk Assessment

* **Invariants**:
  1. Zero regression across the entire unit test suite.
  2. 9-language localization parity across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) verified via `LocalizationTestCache`.
  3. Ratchet ceiling strictly decrements to 251.
* **Risk Rating**: **LOW** (Scope strictly bounded to test suites, string caching, and localized helper extraction; zero change to tracking/recording/persistence core).
