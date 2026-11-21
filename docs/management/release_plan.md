# V5.0.0 Product Release Plan

**Document ID**: `DOC-MGT-REL`  
**Version**: 1.1.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  
**Project**: aTrainingTracker (ATT)  
**Target Release**: `V5.0.0`  
**Configuration Item**: `docs/management/release_plan.md` (ASPICE 4.0 `MAN.3`, `SUP.8`, `SUP.10`)  
**Parent Epic**: `ATT-8138` (*ASPICE 4.0 Audit Remediation 2026-11*), `ATT-9494` (*ASPICE 4.0 Audit Remediation 2026-11-21*)  
**Associated Ticket**: `ATT-8162`, `ATT-8164`, `ATT-9494`  
**Baseline Date**: 2026-11-21  

---

## 1. Release Goals & Executive Scope
Release `V5.0.0` represents the production milestone for aTrainingTracker, transitioning from legacy development to strict ASPICE 4.0 CL2 process governance, multi-sensor ANT+/BLE telemetry stabilization, unified FitFileWriter v2 export, and verified on-device field reliability.

### 1.1 In-Scope Epics
1. **ATT-8138: ASPICE 4.0 Audit Remediation (Target CL2)**:
   - SUP.1 Quality Assurance Plan & dual-vendor weekly cross-audits (`ATT-8212`).
   - Weekly dual-vendor code & architecture reviews across current top models of Gemini and Claude (`ATT-8213`, `ATT-8426`).
   - Multi-lane greedy bin-packing and capacity-bound sprint cadence (`ATT-8162`).
   - Bidirectional requirement traceability and First-Time Pass Rate (FTPR) integrity.
2. **ATT-8141: Google Pixel 10 On-Device Physical Workout Field Verification**:
   - Continuous daily outdoor rides on Google Pixel 10 (`Pixel_10_Physical`).
   - Cumulative telemetry stress validation (GPS, ANT+ power meter, BLE heart rate, cadence).
   - Real-world battery consumption and offline SQLite database integrity.
3. **ATT-235: Advanced Sensor Telemetry & Cycling Dynamics**:
   - Pedal power balance, torque effectiveness, and pedal smoothness recording (`ATT-8196`).
   - Polar Verity Sense stored HR backfill and Aftermath retry (`ATT-7924`).
   - Sensor reconnect robustness across process lifecycle changes.
4. **ATT-5910: FitFileWriter V2 & Exporter Architecture**:
   - Standards-compliant FIT file generation with localized sport sub-types.
   - Exact binary layout verification and developer preview telemetry charts.

---

## 2. Sprint Cadence & Continuous Delivery Architecture
In accordance with `Rule 2.2` / `Rule 7.2` / `Rule 9.1` (`docs/management/schichtbetrieb.md`, `ATT-8030` evolved, `ATT-8162`), `V5.0.0` is constructed through three PO-aligned continuous **`Schicht` windows**:

### 2.1 Cadence Windows & Naming
* **Schicht Windows (Local Time)**:
  - **`01_Früh`**: `05:00 – 15:00` (10 h)
  - **`02_Spät`**: `15:00 – 22:00` (7 h)
  - **`03_Nacht`**: `22:00 – 05:00` (7 h)
* **Sprint Naming**: Sequential per calendar day and shift: `YYYY-MM-DD_0x_Schichtname` (e.g. `2026-11-20_03_Nacht`).

### 2.2 Capacity Envelope & Multi-Lane Bin-Packing
* **Source Queue**: Exclusively pulled from the PO-curated pseudo-sprint `Vom PO Freigegeben` (ID `447`) in strict `ORDER BY rank ASC`.
* **Disjoint Lane Allocation**: Up to 3 parallel Agent-Pair Lanes (`1..3`). Disjoint Epics are allocated per lane (`max_sp_per_lane` $\le 8$ SP per `Schicht` window) so lanes never collide on shared domains.
* **Direct Next-`Schicht` Rollover (`05:00` / `15:00` / `22:00`)**: At each `Schichtwechsel`, merges freeze on `sprint/<CLOSING_SCHICHT>`, completed Stage-6-verified items archive into `Bereit für PO Abnahme` (ID `453`), active WIP carries seamlessly (`carryover-wip`) into the next `Schicht`, and untouched tickets return to `Vom PO Freigegeben`.
* **Best-Effort USB Pixel 10 Deployment**: Following Lean Tier-2 checks and `--no-ff` merge to `develop`, `installDebug -PstampGitHash=true` deploys the commit-stamped build (`5.0.0-dev-<sha>`) when the Pixel 10 is connected (non-blocking if unplugged per `REQ-TST-004`).

---

## 3. Physical Field Verification Integration (ATT-8141)
In compliance with ASPICE 4.0 `SWE.6` (Software Qualification Testing) and Finding `K1`:
* Every pre-release candidate build merged into `develop` is automatically installed on the PO's Google Pixel 10.
* Real-world outdoor workout sessions verify real sensor pairing, GPS route tracking, UI readability under sunlight, and battery endurance.
* Authentic field verification evidence is recorded in `docs/releases/V5.0.0/sys_verification_record.md` (replacing deprecated `docs/engineering/field_tests/`), documenting actual device serial numbers, session timestamps, and human PO attestation prior to V5.0.0 release authorization.
* On-device field verification and human PO acceptance tickets are staged in protected pseudo-sprint `Bereit für PO Systemtest` (ID 454, `ATT-8423`) to ensure active development sprints remain uncluttered.

---

## 4. FixVersion & Configuration Management Governance (Rule 8.3)
* **In-Sprint Isolation**: During active sprints, tickets legitimately maintain `fixVersions` as `None`. Sub-tasks never receive a `fixVersions` value. Tooling (`tools/jira_util.py`) suppresses FixVersion warnings for all non-terminal ticket statuses.
* **Terminal Stamping**: The target release version `V5.0.0` is stamped on parent issues upon completion in Ceremony 2 or release creation by the release manager.

---

## 5. Release Quality Gate & Exit Criteria
A release build for `V5.0.0` may only be tagged and distributed when all following exit criteria are satisfied:
1. **Zero Open Blocker/Critical Defects**: Zero open issues with Priority `Critical` or `High`.
2. **ASPICE 4.0 Gate Records**: 100% of sprint tickets have completed Gates 1–5 and Stage 6 Cold Final Review with verified evidence.
3. **Weekly Dual-Vendor Currency**: Both weekly dual-vendor Code Review and ASPICE 4.0 Cross-Audit are current ($\le 7$ days), plus weekly Multi-OS & 9-Language emulator sweep (`REQ-PRO-135`).
4. **Living Documentation Master Parity**: `verify_requirement_governance.py` reports 100% parity across `docs/requirements.md` and `docs/tests.md`, with zero ungrounded requirements or dangling references.
5. **Tier 2 Full Regression Suite**: Clean execution of `./gradlew testDebugUnitTest :app:lintDebug :app:detekt assembleDebug`.
6. **Signed Release Artifacts**: APK and AAB built with production keystore, aligned with `docs/management/cm_plan.md`.

---

## 6. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-MGT-REL` work-product control header and Change History (`ATT-8162` baseline). |
| `1.1.0` | 2026-11-21 | Independent ASPICE Auditor (Gemini) | `ATT-9494` | Reconciled §2 cadence and §5 exit criteria with live 3-`Schicht` cadence (`05:00`/`15:00`/`22:00`, `max_sp 8`, `YYYY-MM-DD_0x_Schichtname`, `schichtbetrieb.md`) and Stage 6 Cold Final Review + `REQ-PRO-135` emulator sweep (`Finding m1`). |
