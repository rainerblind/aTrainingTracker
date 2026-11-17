# Stage 1 Analysis - ATT-8156: Integration Verification for INT-SWE-05 to INT-SWE-08

**Ticket**: `ATT-8156` | **Epic**: `ATT-8138` | **Release**: `V5.0.0` | **Sprint**: `2026-11-17_01_Früh` | **SP**: 3 SP
**Mapping**: `REQ-PRO-111` (`REQ-PRO-111.1..4`), `TST-PRO-111`, `TST-INT-013..016` | **Branch**: `feature/ATT-8156`

---

## 1. Problem Domain & Forensic Root Cause (Finding M6)
ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7) noted `INT-SWE-01..08` lacked executable integration tests and traceable `TST-INT-*` specs. `ATT-8155` closed `INT-SWE-01..04` (`TST-INT-009..012`), leaving `INT-SWE-05..08` (`IF-SWE-05..08`) without boundary integration tests or `AspiceSweAuditContractTest` locks. Additionally, `docs/architecture/integration_strategy.md` still cites stale metrics (`3,660+` tests, `~2–3 minutes`), omits `INT-SWE-01..04`/`08` from Level 1, and states the local emulator is retired, conflicting with Rule 7.6 / `stage5-verification/SKILL.md`.

---

## 2. Technical Gap & Seam Analysis for INT-SWE-05..08
* **`INT-SWE-05` (`IF-SWE-05`: Services/Repos $\to$ Domain Calculators)**: Verify `AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, and `ClimbDetector` together using realistic synthetic time-series and elevation fixtures in Robolectric Level 1 JVM tests.
* **`INT-SWE-06` (`IF-SWE-06`: Services/Repos $\to$ SQLite Persistence)**: Verify real SQLite operations across `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `KnownLocationsDatabaseManager`, and `LapsDatabaseManager` (dynamic sample-table creation, 1-Hz sample + summary persistence, `onUpgrade` non-destructive schema parity).
* **`INT-SWE-07` (`IF-SWE-07`: Repository/ExportManager $\to$ File Exporters)**: Verify real streaming file generation (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`) driven from SQLite workout/lap records via `ExportManager` / `WorkoutRepository`, asserting `.tmp` atomic write cleanup, byte/XML output validity, and `ExportStatusDatabaseManager` state updates.
* **`INT-SWE-08` (`IF-SWE-08`: Repository/ExportManager $\to$ Cloud & Health Connect)**: Verify `StravaUploader` (OkHttp double for OAuth refresh + backoff retry), `DropboxUploader`, and `HealthConnectManager` / `HealthConnectHrvImporter` (fake `HealthConnectClient` + real `ExportStatusDatabaseManager` / `HrvReadinessRepository` flow).

---

## 3. Independent Peer Review & Verification (4-Augen & Side-Effect Audit)
* **Ticket Metric Correction (Criterion 2)**: `ATT-8156` cites `6,316 tests / ~21 min`; live `docs/engineering/test_evidence/sprint_455.md` (`c4795aee`) records **6,540** unit tests in **2,635.73s (~44 min)** across **1,126** test files (`app/src/test`). Update `integration_strategy.md` to authentic `sprint_455` metrics (`6,540+` tests, `~44 minutes`).
* **Emulator Policy Reconciliation**: `.github/workflows/android-branch-build.yml` runs CI `emulator-test` (`api-level: 34`), while local Stages 1–5 deploy exclusively to `emulator-5554` (`Pixel_8_Android_15` via `tools/agent_emulator.py`) per Rule 7.6 & `stage5-verification/SKILL.md` (physical `Pixel 10` reserved for Ceremony 2).
* **Governance & Side-Effect Check**: `tools/verify_requirement_governance.py` (`verify_architecture_and_system_specs`, L4061–4155) validates every backticked `.kt`/`.java`/`.xml` filename in modified `docs/architecture/` files against the repo; `AspiceSweAuditContractTest` locks section titles and `IF-SWE-01..08` / `INT-SWE-01..08` tokens. All additions to `integration_strategy.md` must remain 100% symbol-grounded with explicit Stage 1 justification.

---

## 4. Chesterton's Fence Archaeology & Invariant Preservation
* **ID Continuity**: Preserves `REQ-PRO-045`, `REQ-PRO-110`, legacy `TST-INT-002..008`, and `ATT-8155`'s `TST-INT-009..012`. Allocates contiguous `REQ-PRO-111` (`REQ-PRO-111.1..4`), `TST-PRO-111`, and `TST-INT-013..016`.
* **Runtime & Submodule Invariants**: Zero edits to `app/src/main/`; `ANT-Android-SDKs` remains untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 5. Scope Bounding & Complexity Assessment
* **In-Scope**: Define `REQ-PRO-111` + `TST-INT-013..016` + `TST-PRO-111`; implement 4 Robolectric integration test classes in `app/src/test/java/com/atrainingtracker/trainingtracker/integration/`; extend `AspiceSweAuditContractTest`; update `docs/architecture/integration_strategy.md`.
* **Out-of-Scope**: Production code edits (`app/src/main/`), live Strava/Dropbox/Health Connect calls, or physical device execution.
* **Complexity**: 3 SP (Complex — 4 subsystem boundary harnesses + governed architecture doc sync).

---

## 6. Change Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-111` (`REQ-PRO-111.1..4`) refining `REQ-PRO-045` in `docs/requirements/req_process_stability.md`; `docs/tests/tst_process_stability.md` (`TST-PRO-111`, `TST-INT-013..016`).
* **Affected Architecture Elements**: `docs/architecture/integration_strategy.md` (Section 3 Verification Levels & Section 5 Tier 2 metrics/emulator policy sync for `INT-SWE-01..08` / `IF-SWE-05..08`).
* **Verification Impact & Test Surface**: 4 new Robolectric JVM integration test suites under `app/src/test/java/com/atrainingtracker/trainingtracker/integration/` plus `AspiceSweAuditContractTest` contract lock.
* **Risk Assessment & Invariants**: LOW risk. Additive test suites and grounded architecture doc sync only; no schema, UI, or production runtime side effects.
