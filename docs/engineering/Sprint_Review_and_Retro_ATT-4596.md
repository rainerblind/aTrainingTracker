# Sprint Review & Retrospective: Sprint `2026-43.6` (`ATT-4596`)

**Ticket**: [ATT-4596](https://rainerblind.atlassian.net/browse/ATT-4596) Review & Retro - Sprint 2026-43.6  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.40` | **Sprint**: `2026-43.6` (ID `386`) | **Branch**: `sprint/2026-43.6` $\rightarrow$ `develop`  
**Date**: 2026-10-22  

---

## 1. Sprint Review Summary (14 Tickets Reviewed in Rank Order)

| Rank | Ticket | Summary | Verdict | Fix Version | Follow-Up / Notes |
| :---: | :--- | :--- | :---: | :---: | :--- |
| 1 | [ATT-4592](https://rainerblind.atlassian.net/browse/ATT-4592) | Fix backup restore SecurityException on Android /data/user/0 symlink | **n.i.O. (Bounced to `Analysis`)** | — | Symlink fix works, but `DatabaseSchemaRegistry.kt` has stale hardcoded DB schema versions (`1`, `25`, `2`, `10`) causing `MigrationEngine` to reject valid backups. Follow-up UI ticket: `ATT-4664`. |
| 2 | [ATT-4588](https://rainerblind.atlassian.net/browse/ATT-4588) | Fix Dropbox OAuth Activity unwrapping and credential persistence race | **Accepted (`i.O.`)** | `V4.9.40` | Confirmed working on Pixel 10. Follow-up symmetry tickets: `ATT-4665`, `ATT-4666`. |
| 3 | [ATT-4587](https://rainerblind.atlassian.net/browse/ATT-4587) | Insert newline before '(Dropbox & Google Drive)' on unified manual cloud backup button | **Accepted (`i.O.`)** | `V4.9.40` | Confirmed working on Pixel 10. |
| 4 | [ATT-4015](https://rainerblind.atlassian.net/browse/ATT-4015) | Route progress tracking projects in raw degrees and jumps on out-and-back routes | **Accepted (`i.O.`)** | `V4.9.40` | All production callers wired to pass stateful `lastSegmentIndex`. |
| 5 | [ATT-4021](https://rainerblind.atlassian.net/browse/ATT-4021) | Zone editing races, loses drafts and lives in unreachable ZonesSettingsActivity | **Accepted (`i.O.`)** | `V4.9.40` | Verified single-activity navigation and atomic batch zone saves. |
| 6 | [ATT-4024](https://rainerblind.atlassian.net/browse/ATT-4024) | Close test coverage gaps and fix flaky test hygiene | **Accepted (`i.O.`)** | `V4.9.40` | Follow-up filed for `WorkoutClustersViewModel` `@JvmOverloads` (`ATT-4667`). |
| 7 | [ATT-4025](https://rainerblind.atlassian.net/browse/ATT-4025) | Run unit tests and lint in release CI and reduce lint baseline | **Accepted (`i.O.`)** | `V4.9.40` | Added `./gradlew :app:testDebugUnitTest` gate to `android-release.yml`. |
| 8 | [ATT-4026](https://rainerblind.atlassian.net/browse/ATT-4026) | Move hardcoded user-visible strings, units and content descriptions to resources | **Accepted (`i.O.`)** | `V4.9.40` | Extracted 28 strings across 9 locales and added imperial unit support in Crop & Filter screens. |
| 9 | [ATT-4027](https://rainerblind.atlassian.net/browse/ATT-4027) | Review manifest permissions, intent filters and build configuration debt | **Accepted (`i.O.`)** | `V4.9.40` | Encapsulated `ConfigPebbleViewsActivity` and scoped `GpxImportActivity` `.gpx` filters. |
| 10 | [ATT-4028](https://rainerblind.atlassian.net/browse/ATT-4028) | Replace source-grep contract tests with lint rules or behavioural tests | **Accepted (`i.O.`)** | `V4.9.40` | Replaced fragile UI source-grep tests with behavioral/reflection/DOM tests. |
| 11 | [ATT-4029](https://rainerblind.atlassian.net/browse/ATT-4029) | Fix untranslated strings and redundant non-translatable keys in locales | **Accepted (`i.O.`)** | `V4.9.40` | Purged 56 `ExtraTranslation` keys and translated remaining DE/NL/PL/backup strings. |
| 12 | [ATT-4030](https://rainerblind.atlassian.net/browse/ATT-4030) | Improve exception handling hygiene across main sources | **Accepted (`i.O.`)** | `V4.9.40` | Replaced silent catches with `Log.w`, narrowed `Throwable` to `Exception`, restored thread interrupts. |
| 13 | [ATT-4031](https://rainerblind.atlassian.net/browse/ATT-4031) | Split god classes and remove dead code | **Accepted (`i.O.`)** | `V4.9.40` | Deleted 4 dead legacy Java classes and scoped Compose `@Preview` functions to `private`. |
| 14 | [ATT-4032](https://rainerblind.atlassian.net/browse/ATT-4032) | Fix low-severity tracking robustness issues | **Accepted (`i.O.`)** | `V4.9.40` | Cached device/sport names on 1 Hz loop, guarded notification after stop, monotonic timer clock. |

*Note*: Ticket 15 (`ATT-4033`) remained in `Test Spec` when Sprint Review was initiated early and will carry forward cleanly.

---

## 2. Retrospective Root-Cause Analysis & Permanent Countermeasures (`Rule 62`)

### 2.1 Background Tier 2 Unit Test Execution at Ceremony 2 Start (`sprint-planner/SKILL.md`)
* **Observation**: Running `./gradlew testDebugUnitTest` (~4m 45s) at Step 4 of Ceremony 2 forced the human user to wait nearly 5 minutes after reviewing the last ticket.
* **Countermeasure**: Updated `sprint-planner/SKILL.md` Ceremony 2 Step 1 so `./gradlew testDebugUnitTest && python3 tools/verify_requirement_governance.py` is launched immediately in the background at the very start of the Sprint Review alongside `./gradlew installDebug`. By the time the first 1–2 tickets are reviewed, the full suite result is already available. Follow-up ticket `ATT-4669` was filed for the 5 full-suite test failures caught by the Tier 2 gate.

### 2.2 Epic-Scoped Persistent Subagent Pairs: Error/Timeout Replacement Policy (`aspice_governance.md`)
* **Observation**: Reusing `(subagent_pro, subagent_flash)` per Parent Epic (`Rule 61`) worked very well across 14 tickets, eliminating cold-start overhead and preserving deep domain context. Only on the 5th ticket of the largest Epic (`ATT-431` on `ATT-4033`) did `subagent_flash` encounter a transient `context deadline exceeded` timeout.
* **Countermeasure**: Preserved the unrestricted per-Epic persistent subagent pair architecture (`Rule 61`), and codified an explicit **Subagent Error/Timeout Replacement Policy** in `.agents/rules/aspice_governance.md` Core Rule 7: if a persistent subagent enters an `errored` state or hits a `context deadline exceeded` timeout, terminate that specific subagent (`manage_subagents(Action="kill")`), spawn a fresh replacement of the same model tier for the Epic pair, and dispatch the current stage prompt to the replacement.

### 2.3 Single Source of Truth for `DatabaseSchemaRegistry` (`docs/coding_standards.md`)
* **Observation**: `ATT-4592` fixed `SecureZipExtractor`'s symlink check, but on-device restore failed immediately after extraction because `DatabaseSchemaRegistry.kt` hardcoded integer literals (`1`, `25`, `2`, `10`) that became stale when `ATT-4008` bumped the corresponding `SQLiteOpenHelper.DB_VERSION` constants.
* **Countermeasure**: Codified in `docs/coding_standards.md` §2.2 that `DatabaseSchemaRegistry` and any backup/restore validation logic **MUST** reference `<DbHelper>.DB_VERSION` constants directly rather than duplicating integer literals.

### 2.4 `@JvmOverloads constructor` Mandate for Reflective Framework Classes & Debug Package ADB Launch (`docs/coding_standards.md`, `stage5-verification/SKILL.md`)
* **Observation**: Adding default parameters to `WorkoutClustersViewModel(application: Application, ioDispatcher: ...)` without `@JvmOverloads constructor` passed Kotlin unit tests but crashed at runtime via `AndroidViewModelFactory` reflection (`ATT-4667`). Additionally, during Stage 5 of `ATT-4588`, `subagent_flash` launched `com.atrainingtracker` (Release) instead of `com.atrainingtracker.debug` (Debug).
* **Countermeasure**:
  1. Added §3.5 in `docs/coding_standards.md` mandating `@JvmOverloads constructor` whenever default parameters are added to an `AndroidViewModel`, `ViewModel`, or `Fragment` instantiated by framework reflection.
  2. Updated `stage5-verification/SKILL.md` to require launching `com.atrainingtracker.debug` explicitly (`adb shell am start -n com.atrainingtracker.debug/...`) and touching every modified route/screen on-device.

### 2.5 Multi-Provider UI & Capability Symmetry (`docs/design_guidelines.md`)
* **Observation**: Reviewing `ATT-4588` on-device revealed that `DropboxSettingsDialog` and `GoogleDriveSettingsDialog` had diverged in layout and feature granularity (`ATT-4664`, `ATT-4665`, `ATT-4666`).
* **Countermeasure**: Added §3.4 in `docs/design_guidelines.md` mandating that parallel cloud/sync providers (Dropbox, Google Drive, etc.) share a single parameterized composable layout and symmetric capability toggles.
