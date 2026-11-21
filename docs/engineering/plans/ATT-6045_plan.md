# Stage 3 Implementation Plan — ATT-6045

- **Jira Ticket**: `ATT-6045` (`2 SP`, Bug, Medium/P2, Epic `ATT-6033`, Related: `ATT-6036`)
- **Target Release**: `V5.0.0`
- **Requirements / Tests**: `REQ-MIG-055` (`REQ-MIG-055.1`, `REQ-MIG-055.2`) <-> `TST-MIG-055` (`TST-MIG-055.1`..`TST-MIG-055.3`)
- **UI & Architecture Governance (`Rule 8.8`, `docs/design_guidelines.md`)**: Pure backend migration/exporter scope (`0` UI/Compose changes, `0` new strings). Neither `docs/architecture.md` (SWE.2) nor `docs/architecture/detailed_design/` (SWE.3) requires changes (`SYSREQ-DEX-004` unchanged).

## 1. Atomic Implementation Steps
1. **Shared `java.time` UTC Timestamp Helper (`WorkoutTimestampParser.kt`, new internal helper in `migration/parsers/`)** — `REQ-MIG-055.1`:
   - Define immutable, thread-safe `DB_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)` (`minSdk 26`).
   - Implement `formatIsoTimestampToUtcDbString(rawTime: String): String`: parse via `OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)` -> `.withOffsetSameInstant(ZoneOffset.UTC).format(DB_TIME_FORMATTER)` (handles `Z`, `+HH:MM`, `-HH:MM`, `.SSS`, DST gaps without `TimeZone.getDefault()`), falling back to `LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DB_TIME_FORMATTER)` for offset-less ISO strings, and returning raw `rawTime` on parse failure.
   - Implement `computeDurationSeconds(startDbTime: String, endDbTime: String): Double?` using `LocalDateTime.parse(..., DB_TIME_FORMATTER).toInstant(ZoneOffset.UTC)` and `Duration.between(t1, t2)`.
2. **Migrate `GpxWorkoutParser.kt` & `TcxWorkoutParser.kt`** — `REQ-MIG-055.1`:
   - Remove `SimpleDateFormat` imports, `tcxTimeFormat` singleton fields (`Gpx:45`, `Tcx:42`), and per-sample/per-lap `SimpleDateFormat` allocations (`Gpx:170,213`; `Tcx:101,126`).
   - Delegate trackpoint/lap time formatting (`Gpx:167-172`, `Tcx:98-103,123-128`) to `formatIsoTimestampToUtcDbString(...)` and GPX lap duration (`Gpx:211-221`) to `computeDurationSeconds(...)`.
3. **Remove Dead Field & Fix `BaseExporter` Column Index `0` Check** — `REQ-MIG-055.2`:
   - Delete unused `tcxTimeFormat` and `java.text.SimpleDateFormat` import if unused? *(Check `LegacyImportEngine.kt:915,998,1191` still use `SimpleDateFormat` — keep import intact, remove only line `84` `tcxTimeFormat`.)*
   - In `BaseExporter.java`, update `myGetStringFromCursor` (`:304`) and `myGetBooleanFromCursor` (`:322`) from `if (index <= 0)` to `if (index < 0)`, preserving `"" -> null`, `> 0` boolean semantics, and `ExportStatusDatabaseManager.getDatabase()`.
4. **Add Tagged Unit Tests (`TST-MIG-055.1`..`TST-MIG-055.3`)**:
   - `GpxWorkoutParserTest.kt` & `TcxWorkoutParserTest.kt`: 3+ thread concurrent parsing across repeated runs; `Europe/Berlin` DST spring-forward (`2026-03-29T02:30:00Z`) & fall-back (`2026-10-25T02:30:00Z`), `Z`, `+02:00`, `-05:00`, `.SSS`, bare local ISO, lap duration, and malformed fallback (tagged `TST-MIG-055`, `TST-MIG-055.1`, `TST-MIG-055.2`).
   - `BaseExporterTest.kt`: test `myGetStringFromCursor` & `myGetBooleanFromCursor` at column index `0` (non-empty string, `"" -> null`, `1 -> true`, `0 -> false`) and `-1` (`TST-MIG-055`, `TST-MIG-055.3`).
5. **Living Docs Promotion & Verification**:
   - Promote `REQ-MIG-055` & `TST-MIG-055` to `[Implemented]` in `docs/requirements/req_data_migration_export_part2.md` & `docs/tests/tst_data_migration_export.md`; sync indexes; run `verify_requirement_governance.py --base-ref sprint/2026-11-20_03_Nacht` and targeted Gradle `--rerun` across `migration.*`, `exporter.*`, and `ArchitectureConformanceContractTest`.

## 2. Invariants & Risk Mitigation
- Preserve `"yyyy-MM-dd HH:mm:ss"` UTC-Wall-Clock output outside DST gaps, raw fallback on invalid timestamps, `FitWorkoutParser` local-time behavior (`REQ-MIG-038`), `ExportStatusDatabaseManager.getDatabase()` (`DatabaseConnectionManager.kt:122`), and `ArchitectureConformanceContractTest` bounds. Zero production/test code edited before Gate 3 `PASS` (`Rule 4.1`).
