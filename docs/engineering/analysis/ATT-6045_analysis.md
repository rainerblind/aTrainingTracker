# Stage 1 Root Cause Analysis — ATT-6045

- **Jira Ticket**: `ATT-6045` (`2 SP`, Bug, Medium/P2, Epic `ATT-6033`, Related: `ATT-6036`)
- **Target Release**: `V5.0.0`
- **Baseline Branch / Tip**: `sprint/2026-11-20_03_Nacht` @ `b3ea47f6`

## 1. Forensic Root Cause Analysis (Codebase-Verified)
1. **Shared Mutable `SimpleDateFormat` Race in Parallel Cloud Imports (`GpxWorkoutParser.kt`, `TcxWorkoutParser.kt`)**:
   - `GpxWorkoutParser.kt:45` and `TcxWorkoutParser.kt:42` are Kotlin `object` singletons holding unsynchronized `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)` (`tcxTimeFormat`), invoked at `GpxWorkoutParser.kt:169` and `TcxWorkoutParser.kt:100,125`.
   - `LegacyImportEngine.kt:215-248` (`importFromDropbox`) and `:437` spawn 3 concurrent `Dispatchers.IO` workers calling `TcxWorkoutParser.parse` (`:560`) and `GpxWorkoutParser.parse` (`:623`) **outside** `importMutex` (`:745`). Concurrent calls corrupt `SimpleDateFormat`'s internal `Calendar`, throwing caught parse exceptions (persisting raw ISO strings) or silently corrupting sample/lap timestamps and `firstTime`.
2. **DST Spring-Forward +1h Shift, Truncation & Per-Sample Allocations**:
   - Both parsers slice `raw.substring(0, 19)`, throwing `StringIndexOutOfBoundsException` on short strings, discarding fractional seconds (`.SSS`) and explicit offsets (`+02:00`, `-05:00`), and parsing UTC strings in `TimeZone.getDefault()` via lenient `SimpleDateFormat`. During a local spring-forward DST gap (e.g., `2026-03-29T02:30:00Z` in `Europe/Berlin`), lenient parsing shifts `"02:30:00"` to `"03:30:00"` (`+1h`).
   - Additionally, `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)` is allocated per trackpoint/lap (`GpxWorkoutParser.kt:170,213`; `TcxWorkoutParser.kt:101,126`), while `LegacyImportEngine.kt:84` declares an unused `tcxTimeFormat` field.
3. **`BaseExporter.java` Column Index `0` Rejection (`BaseExporter.java:304,322`)**:
   - `myGetStringFromCursor` (`:304`) and `myGetBooleanFromCursor` (`:322`) guard `if (index <= 0)` instead of `if (index < 0)`, whereas sibling `dataValid` (`:279`) uses `if (index < 0)`. Any Cursor projection placing a requested column at index `0` is silently dropped as `null` / `false`.

## 2. Independent Peer Review & Verification (`4-Augen-Prinzip` @ `b3ea47f6`)
- Verified `GpxWorkoutParser.kt:45,167-179,211-222` (`306` LoC), `TcxWorkoutParser.kt:42,98-103,123-131` (`250` LoC), `LegacyImportEngine.kt:84,215-248,560,623,745` (`1,453` LoC), and `BaseExporter.java:277-332` (`357` LoC).
- Verified `LegacyImportEngine.kt:84` `tcxTimeFormat` has 0 references elsewhere; `parseTimeToMs` (`:905`) creates per-call instances and is separate.
- Verified `ExportStatusDatabaseManager.getDatabase()` (`:99`) is actively called by `DatabaseConnectionManager.kt:122` for backup/restore and MUST remain intact.
- Verified `StravaUploader.kt` no longer overrides `myGetStringFromCursor`/`myGetBooleanFromCursor` (migrated to `WorkoutData`); `BaseExporter.java:304,322` `< 0` fix is cleanly scoped and additive.
- Chesterton's Fence (`REQ-PRO-022`): No existing `REQ-MIG-*`/`REQ-DAT-*` needs alteration (`REQ-MIG-038` & `REQ-DAT-029` invariants stay untouched; net-new `REQ-MIG-*` will be reserved in Stage 2).

## 3. Scope Boundaries & Complexity Audit
- **In Scope**: Shared thread-safe `java.time` (`minSdk 26`) UTC-Wall-Clock parser/formatter (`yyyy-MM-dd HH:mm:ss` byte-identical outside DST gaps, zero DST shift, handling `Z`, numeric offsets `+HH:MM`, `.SSS`, bare local ISO, and invalid fallback; zero per-sample allocations) + `Duration` lap timing in `GpxWorkoutParser.kt` & `TcxWorkoutParser.kt`; remove dead `LegacyImportEngine.kt:84` `tcxTimeFormat`; fix `BaseExporter.java:304,322` `index < 0`; multi-thread/DST/offset/index-0 unit tests.
- **Out of Scope**: `FitWorkoutParser` local-time behavior (`REQ-MIG-038`); `ExportStatusDatabaseManager.getDatabase()` removal; `LegacyImportEngine.parseTimeToMs`; `RoutesDatabaseManager.deleteRoute`.
- **Complexity Check (`REQ-PRO-029`)**: `2 SP` (Standard) confirmed — 4 localized production files, `0` LoC ceiling pressure.

## 4. Change-Request Impact Analysis
- **Affected Requirements**: Net-new `REQ-MIG-*` / `TST-MIG-*` (reserved in Stage 2 in `docs/requirements/req_data_migration_export_part2.md` [`124` lines] & `docs/tests/tst_data_migration_export.md` [`2,292` lines]); complements `REQ-MIG-038`, `REQ-MIG-048`, `REQ-DAT-029`, `REQ-DAT-040` without modifying existing REQs. No `SYSREQ-*` (`SYS.2`) contract alters (`SYSREQ-DEX-001`, `SYSREQ-DEX-004` preserved).
- **Affected Architecture Elements**: `migration/parsers` (`GpxWorkoutParser`, `TcxWorkoutParser`), `migration/LegacyImportEngine`, `exporter/BaseExporter`. Zero `docs/architecture.md` or `docs/system/` changes required.
- **Verification Impact & Test Surface**: `GpxWorkoutParserTest.kt`, `TcxWorkoutParserTest.kt` (3-thread concurrent parse, DST spring-forward gap `2026-03-29T02:30:00Z`, `Z` / `+02:00` / `.SSS` / bare ISO / malformed fallback), `BaseExporterTest.kt` (column index `0` vs `-1` for `myGetStringFromCursor` & `myGetBooleanFromCursor`), plus `ArchitectureConformanceContractTest`. Zero UI/APK strings or Compose changes (`N/A - no new or modified UI strings`).
- **Risk Assessment & Invariants**: **LOW–MEDIUM Risk**. Preserve exact `"yyyy-MM-dd HH:mm:ss"` UTC-Wall-Clock output format outside DST gaps, raw-string fallback on invalid timestamps, `ExportStatusDatabaseManager.getDatabase()`, `BaseExporter` empty-string-to-`null` & `> 0` boolean semantics, and `ArchitectureConformanceContractTest` bounds.
