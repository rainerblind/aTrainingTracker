# Stage 2 Requirement & Test Specification — ATT-6045

- **Jira Ticket**: `ATT-6045` (`2 SP`, Bug, Medium/P2, Epic `ATT-6033`, Related: `ATT-6036`)
- **Target Release**: `V5.0.0`
- **Living Docs**: `REQ-MIG-055` (`docs/requirements/req_data_migration_export_part2.md`) <-> `TST-MIG-055` (`docs/tests/tst_data_migration_export.md`) — `[Planned]`
- **System Requirement**: `SYSREQ-DEX-004` (`SYS.2`/`SYS.3` unchanged)
- **Localization**: `Localization Parity: N/A - no new or modified UI strings`

## 1. Formal Requirement (`REQ-MIG-055`)
1. **`REQ-MIG-055.1` — Thread- & DST-Safe `java.time` ISO-8601 Timestamp Parsing (`GpxWorkoutParser.kt`, `TcxWorkoutParser.kt`)**:
   - The system SHALL replace all mutable `SimpleDateFormat` singletons and per-sample/per-lap `SimpleDateFormat` allocations in `GpxWorkoutParser` and `TcxWorkoutParser` with shared immutable, thread-safe `java.time` (`DateTimeFormatter`, `OffsetDateTime`, `LocalDateTime`, `ZoneOffset.UTC`, `Duration`) helpers (`minSdk 26`).
   - Offset-aware ISO-8601 strings (`Z`, `+HH:MM`, `-HH:MM`, with or without fractional seconds `.SSS`) MUST convert via `.withOffsetSameInstant(ZoneOffset.UTC)` and bare offset-less ISO strings via `LocalDateTime`, formatting identically to `"yyyy-MM-dd HH:mm:ss"` (UTC-Wall-Clock) outside DST gaps with zero `+1h` DST shift, while preserving raw-string fallback on malformed inputs.
   - `GpxWorkoutParser` SHALL compute lap duration (`totalTimeSeconds`) from `"yyyy-MM-dd HH:mm:ss"` bounds via `java.time.Duration` in UTC.
2. **`REQ-MIG-055.2` — `BaseExporter` Column Index `0` & Dead-Code Cleanup (`BaseExporter.java`, `LegacyImportEngine.kt`)**:
   - `BaseExporter.myGetStringFromCursor` (`:304`) and `myGetBooleanFromCursor` (`:322`) SHALL check `if (index < 0)` (matching `dataValid` at `:279`), preserving `"" -> null` and `> 0` boolean semantics.
   - Unused `LegacyImportEngine.tcxTimeFormat` (`:84`) MUST be removed, while `ExportStatusDatabaseManager.getDatabase()` MUST remain intact for `DatabaseConnectionManager.getActiveDatabases()`.

## 2. Acceptance Criteria (`Given-When-Then`) & Invariants
- **AC1 (Concurrent & DST-Safe Parsing)**: *Given* 3+ concurrent worker threads calling `GpxWorkoutParser.parse` and `TcxWorkoutParser.parse` plus `Z`, `+02:00`/`-05:00`, `.SSS`, bare local ISO, malformed strings, and DST spring-forward/fall-back timestamps (`2026-03-29T02:30:00Z`, `2026-10-25T02:30:00Z`) under `Europe/Berlin`, *When* parsed, *Then* outputs are deterministic, byte-identical to `"yyyy-MM-dd HH:mm:ss"` UTC-Wall-Clock outside DST gaps with zero `+1h` DST distortion, and malformed strings fall back to raw text.
- **AC2 (`BaseExporter` Index `0` & Cleanup)**: *Given* a `Cursor` with requested columns at index `0` vs missing `-1`, *When* `myGetStringFromCursor` / `myGetBooleanFromCursor` run, *Then* index `0` reads cleanly (`"" -> null`, `> 0 -> true`), `-1` returns `null`/`false`, `LegacyImportEngine.tcxTimeFormat` is gone, and `ExportStatusDatabaseManager.getDatabase()` remains intact.
- **Invariants**: Exact `"yyyy-MM-dd HH:mm:ss"` UTC-Wall-Clock output outside DST gaps; `FitWorkoutParser` local-time behavior (`REQ-MIG-038`); raw-string fallback on invalid timestamps; `ExportStatusDatabaseManager.getDatabase()`; `BaseExporter` `"" -> null` & `> 0` semantics; `0` UI strings.

## 3. Test Specification (`TST-MIG-055`) & Traceability Matrix
| Test ID | Target Suite & Verbatim Coverage | Linked REQ |
| :--- | :--- | :--- |
| `TST-MIG-055.1` | `GpxWorkoutParserTest`, `TcxWorkoutParserTest`: 3-thread concurrent parse across repeated iterations verifying deterministic `firstTime`/`lastTime`/`parsedLaps` with zero exceptions | `REQ-MIG-055.1` |
| `TST-MIG-055.2` | `GpxWorkoutParserTest`, `TcxWorkoutParserTest`: `Europe/Berlin` DST spring-forward (`2026-03-29T02:30:00Z`) & fall-back (`2026-10-25T02:30:00Z`), `Z`, `+02:00`, `-05:00`, `.SSS`, bare ISO, lap duration, malformed fallback | `REQ-MIG-055.1` |
| `TST-MIG-055.3` | `BaseExporterTest`: `myGetStringFromCursor` & `myGetBooleanFromCursor` at index `0` (non-empty, `"" -> null`, `1 -> true`, `0 -> false`) vs `-1`; Tier 1 `--rerun` on `migration.*`, `exporter.*`, `ArchitectureConformanceContractTest` | `REQ-MIG-055.2` |
