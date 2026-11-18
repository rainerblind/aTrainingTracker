# Living Test Specification: Engineering Process, Stability & Architecture (Part 2 of 2)

**Document ID**: `DOC-TST-PRO-P2`  
**Version**: 1.0.6  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `process_stability_part2`  
**Prefixes**: `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`  
**Scope**: ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools.  
**Total Tests**: 86

---

### TST-PRO-063: **Problem Severity Classification & Defect Trend Metrics Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-063`
- **Component**: `tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6156`
- **Test Description**:
  1. *Bug Creation Severity Mandate (`TST-PRO-063.1`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Test creating a Bug without severity fails with exit code 1; test creating a Bug with `--severity=critical` attaches `severity-critical` label and succeeds.
  • Test non-bug issue types (Features, Improvements) can be created without severity.
  2. *Defect Trend Metrics Extraction (`TST-PRO-063.2`, tools/test_sprint_metrics.py & tools/hoechstmass/tools/test_sprint_metrics.py)*:
  • Process sprint issues containing bugs with severity labels; verify `parse_sprint_gate_metrics()` aggregates bug counts by severity, open vs closed, and calculates defect escape/resolution rates.
  3. *Metrics Markdown Reporting & Schema Validation (`TST-PRO-063.3`, tools/test_sprint_metrics.py & tools/test_verify_requirement_governance.py)*:
  • Verify `format_metrics_markdown()` includes the "Problem Trends & Defect Severity" section; verify `verify_sprint_metrics()` accepts `problem_trends` schema fields.
  4. *Counterpart Parity & Tooling Integration (`TST-PRO-063.4`, tools/hoechstmass/tools/test_hoechstmass_tools.py & tools/hoechstmass/tools/test_sprint_metrics.py)*:
  • Assert Höchstmaß mirror tools maintain 100% functional parity for bug severity enforcement and metrics extraction.

### TST-PRO-064: **Change-Request Impact Analysis Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-064`
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6157`
- **Test Description**:
  1. *Impact Analysis Section Validation (`TST-PRO-064.1`, tools/test_verify_requirement_governance.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Test `validate_impact_analysis_section()` against valid markdown containing all 4 dimensions (Requirements, Architecture, Tests, Risks) and assert True.
  • Test invalid markdown (missing section, missing dimension, empty or placeholder text) and assert False / failure diagnostics.
  2. *Governance CLI `--check-impact` Flag (`TST-PRO-064.2`, tools/test_verify_requirement_governance.py)*:
  • Test `verify_stage1_impact_analysis(analysis_file)` and CLI invocation `--check-impact [FILE]` verifying exit code 0 on valid deliverables and exit code 1 with actionable errors on invalid files.
  3. *Gate 1 Impact Analysis Verification (`TST-PRO-064.3`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Test `gate-verdict` on `[Analysis]` subtask with missing/incomplete impact analysis fails with error; test valid deliverable passes Gate 1.
  • Test non-analysis subtasks (Gates 2..5) proceed normally without triggering Stage 1 impact analysis check.
  4. *Counterpart Parity & Template Verification (`TST-PRO-064.4`, tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Assert both `.agents/skills/stage1-analysis/templates/analysis_template.md` and `tools/hoechstmass/templates/analysis_template.md` contain the standardized Change Impact Analysis section.
  • Assert Höchstmaß mirror tools maintain 100% functional parity with root tooling.

### TST-PRO-065: **Living Documentation Sharding Code-Span Pipe Splitting & Master Index Safety Guard Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-065`
- **Component**: `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`
- **Jira**: `ATT-6124`
- **Test Description**:
  1. *Code-Span Pipe Splitting & Pre-Fix Reproduction (`TST-PRO-065.1`, tools/test_shard_living_docs.py)*:
  • Test `split_markdown_table_row()` against single backticks (`...`), double backticks (``...``), escaped pipes (`\|`), and code snippets containing `||` (e.g. `REQ-EXP-021` snippet).
  • Assert reproduction: demonstrate that naive `row.split("|")` splits into 9 parts corrupting `Tests`, whereas `split_markdown_table_row()` preserves exact 7 columns with intact `Tests = TST-EXP-018`.
  2. *6-Column and 7-Column Requirement Parsing (`TST-PRO-065.2`, tools/test_shard_living_docs.py)*:
  • Parse synthetic requirements table with both 6-column and 7-column rows, verifying Title, Description, Rationale, Implementation, Tests, and Status are correctly populated without index shifts.
  3. *Master Index Overwrite Safety Guard (`TST-PRO-065.3`, tools/test_shard_living_docs.py)*:
  • Invoke `shard_living_docs.py` against a directory where `docs/requirements.md` is a Master Index; verify process exits with code 1 and existing shards are preserved.
  • Test `--force` bypass allowing overwrite when explicitly requested.
  4. *Clean Execution & Parity Verification (`TST-PRO-065.4`, tools/test_shard_living_docs.py)*:
  • Verify `--verify-only` succeeds on current repository living documentation shards.

### TST-PRO-066: **Living Documentation Unique Requirement & Test Identifier Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-066`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6120`, `ATT-6123`
- **Test Description**:
  1. *Duplicate Heading ID Detection & Detailed Reporting (`TST-PRO-066.1`, tools/test_verify_requirement_governance.py)*:
  • Create synthetic requirements and test shards in a temporary directory containing duplicated IDs.
  • Verify `verify_unique_ids()` detects each duplicated ID and formats error diagnostics reporting the exact identifier, filename, and line numbers of all occurrences.
  2. *Legacy Duplicate Baseline Tolerance & New Duplicate Rejection (`TST-PRO-066.2`, tools/test_verify_requirement_governance.py)*:
  • Verify that duplicate IDs present in `legacy_allowlist` produce warnings but return `success = True` in standard verification mode.
  • Verify that adding any single new duplicate outside the allowlist causes `verify_unique_ids()` to return `success = False` and triggers exit code 1.
  3. *Strict Uniqueness Enforcement Option (`TST-PRO-066.3`, tools/test_verify_requirement_governance.py)*:
  • Verify that when `fail_on_duplicates=True` (or CLI `--enforce-unique`), duplicates trigger `success = False` and exit code 1.
  4. *Clean Repository Governance Parity (`TST-PRO-066.4`, tools/test_verify_requirement_governance.py)*:
  • Verify that running `verify_unique_ids()` and `verify_unique_ids(fail_on_duplicates=True)` against the authentic repository passes cleanly with 0 duplicates and zero warnings.

### TST-PRO-067: **JUnit Test Results Archival & Evidence Integrity Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-067`
- **Component**: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `docs/engineering/test_results`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6137`
- **Test Description**:
  1. *CI Branch Build Workflow Archival Test (`TST-PRO-067.1`, ReleaseCiAndLintConfigContractTest.kt)*:
  • Inspect `.github/workflows/android-branch-build.yml`.
  • Assert presence of an artifact upload step configured with `if: always()` and step name indicating unit test report archival.
  • Assert artifact paths include `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/testDebugUnitTest/`.
  2. *Release Workflow Test Results Packaging & Attachment Test (`TST-PRO-067.2`, ReleaseCiAndLintConfigContractTest.kt)*:
  • Inspect `.github/workflows/android-release.yml`.
  • Assert presence of step packaging `junit-test-results-<tag>.zip` (or `.tar.gz`) from unit test report directories.
  • Assert that release assets attached via `softprops/action-gh-release@v2` include the versioned test results archive pattern.
  • Assert workflow artifact upload for release test results.
  3. *Test Results Symlink Retirement & Evidence Directory Contract Test (`TST-PRO-067.3`, ReleaseCiAndLintConfigContractTest.kt & tools/verify_requirement_governance.py)*:
  • Assert `docs/engineering/test_results` symlink to transient build output is retired.
  • Assert `docs/engineering/test_evidence/` is established as canonical directory in living docs and governance scripts.
  4. *Governance Tooling Integration (`TST-PRO-067.4`, tools/test_verify_requirement_governance.py)*:
  • Assert `verify_engineering_directory_structure()` in `tools/verify_requirement_governance.py` validates canonical `test_evidence/` layout and rejects obsolete symlink.
  5. *Workflow YAML Syntax Validation (`TST-PRO-067.5`, tools/test_verify_requirement_governance.py & ReleaseCiAndLintConfigContractTest.kt)*:
  • Parse `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` with YAML parser and assert zero syntax errors.

### TST-PRO-068: **Developer Sensor Simulator Self-Test Harness Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-068`
- **Component**: `docs/system/sys_integration_tests.md`, `tools/run_simulator_self_tests.py`, `tools/test_simulator_self_tests.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt`
- **Jira**: `ATT-6166`, `ATT-8157`
- **Test Description**:
  1. *System Integration Boundary & Gap Disclosure (`TST-PRO-068.1`, SystemIntegrationTestContractTest.kt)*:
  • Assert `docs/system/sys_integration_tests.md` exists, contains required metadata header, and explicitly discloses that Python simulators do not execute the Android APK or physical hardware.
  • Assert documentation covers external system interfaces (`SYS-IF-01` BLE, `SYS-IF-02` ANT+, `SYS-IF-03` GNSS, `SYS-IF-05` Cloud) and honest allocation across `INT-SWE-01..08`, `AppSmokeAndroidTest.kt`, and manual `SYS.5`.
  2. *System Requirements Bidirectional Traceability (`TST-PRO-068.2`, SystemIntegrationTestContractTest.kt)*:
  • Assert interface specifications trace to system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`) and system elements (`SYS-EL-01`..`05`).
  3. *Simulator Self-Test Runner Execution (`TST-PRO-068.3`, tools/test_simulator_self_tests.py)*:
  • Execute `tools/run_simulator_self_tests.py` against real simulator modules (`ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`) and verify JSON report schema via `--output`, explicit simulator-only metadata, and exit code 0.
  4. *Simulator Self-Test Unit Suite (`TST-PRO-068.4`, tools/test_simulator_self_tests.py)*:
  • Verify `unittest` suite for simulator handlers, targeted filtering, CLI `--output` arguments, and pure Python 3 stdlib compliance passes cleanly.

### TST-PRO-069: **System Verification Protocol & Hardware Reference Specification Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-069`
- **Component**: `docs/system/sys_verification_protocol.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt`, `docs/architecture.md`
- **Jira**: `ATT-6167`
- **Test Description**:
  1. *Protocol Structural Integrity & Metadata Header (`TST-PRO-069.1`, SystemVerificationProtocolContractTest.kt)*:
  • Assert `docs/system/sys_verification_protocol.md` exists and contains required metadata header referencing ASPICE SYS.5, REQ-PRO-069, and TST-PRO-069.
  • Assert protocol contains Mermaid workflow and system verification topology diagrams.
  2. *Hardware Reference & Environmental Conditions Matrices (`TST-PRO-069.2`, SystemVerificationProtocolContractTest.kt)*:
  • Assert protocol specifies hardware matrix with Google Pixel reference family, BLE HRM (Polar H10/Garmin HRM-Pro), Cycling Power (Favero Assioma/Stages/Wahoo), Garmin Varia Radar (RTL515), and Barometric Altimeter / GNSS.
  • Assert protocol specifies 5 environmental test matrices (Urban, Rural, Climb, Indoor, Longevity).
  3. *System Verification Cases & SYSREQ Traceability (`TST-PRO-069.3`, SystemVerificationProtocolContractTest.kt)*:
  • Assert protocol specifies formal test cases (`SYS-VRF-001` through `SYS-VRF-008`).
  • Assert bidirectional coverage of all system requirements (`SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006`) with zero dangling IDs.
  4. *Objective Pass/Fail Criteria & Release Record Template (`TST-PRO-069.4`, SystemVerificationProtocolContractTest.kt)*:
  • Assert protocol defines pass/fail thresholds for FIT/TCX integrity, Strava/Dropbox sync, RF reconnection, sample loss $\le 1\%$, and battery drain $\le 8\%$/hr.
  • Assert protocol includes release sign-off record template with signature fields for release governance.

### TST-PRO-070: **Living Documentation Bidirectional Reference Integrity Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-070`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6121`
- **Test Description**:
  1. *Dangling TST Detection in Requirements (`TST-PRO-070.1`, tools/test_verify_requirement_governance.py)*:
  • Assert that `find_dangling_references()` detects unresolvable `TST-*` IDs cited in `- **Tests**:` fields of REQs, reporting source file, line number, and target ID.
  2. *Dangling REQ Detection in Tests (`TST-PRO-070.2`, tools/test_verify_requirement_governance.py)*:
  • Assert that `find_dangling_references()` detects unresolvable `REQ-*` IDs cited in `- **Requirement(s)**:` fields of TSTs, reporting source file, line number, and target ID.
  3. *Legacy Allowlist Tolerance & New Reference Rejection (`TST-PRO-070.3`, tools/test_verify_requirement_governance.py)*:
  • Assert that `verify_dangling_references()` emits warnings and returns success (`True`) for known allowlisted legacy dangling links.
  • Assert that any net-new dangling link outside the allowlist fails verification (`False`) with error diagnostics.
  4. *Strict Enforcement Mode (`TST-PRO-070.4`, tools/test_verify_requirement_governance.py)*:
  • Assert that passing `--enforce-links` / `--fail-on-dangling` (or `fail_on_dangling=True`) returns failure (`False`) when any dangling reference is present.
  5. *Clean Repository Resolution (`TST-PRO-070.5`, tools/test_verify_requirement_governance.py)*:
  • Assert that verification passes cleanly when all cited IDs resolve to valid headings.

### TST-PRO-071: **Living Documentation Bidirectional REQ-TST Traceability & Code Coverage Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-071`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6122`
- **Test Description**:
  1. *Category 1 Calculation (REQs without TST) (`TST-PRO-071.1`, tools/test_verify_requirement_governance.py)*:
  • Assert that requirements with missing, empty, or 'None' `Tests` fields are correctly categorized and quantified.
  2. *Category 2 Calculation (TSTs without Test Code) (`TST-PRO-071.2`, tools/test_verify_requirement_governance.py)*:
  • Assert that TST headings lacking corresponding test code tags in test directories are detected and quantified.
  3. *Category 3 Calculation (Code Tags without Spec) (`TST-PRO-071.3`, tools/test_verify_requirement_governance.py)*:
  • Assert that code tags referencing test IDs not defined in living doc headings are detected and reported.
  4. *Category 4 Calculation (TSTs not Cited Back by REQs) (`TST-PRO-071.4`, tools/test_verify_requirement_governance.py)*:
  • Assert that TST specifications whose cited REQs do not include them in the `Tests` field are identified.
  5. *Report Output Formatting (`TST-PRO-071.5`, tools/test_verify_requirement_governance.py)*:
  • Assert that Markdown and JSON coverage reports format counts, totals, and percentages accurately.
  6. *Threshold Gate Ratcheting (`TST-PRO-071.6`, tools/test_verify_requirement_governance.py)*:
  • Assert that verifier fails with exit code 1 when coverage metrics fall below configured minimum thresholds, and passes when thresholds are met.

### TST-NFR-001: **Deterministic EKF Timing Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-001`
- **Component**: `UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured JVM EKF Timing & Delta-T Benchmark (`TST-NFR-001.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: JIT warmup completed; high-frequency synthetic IMU hardware timestamps with simulated thread arrival jitter.
  3. *Action*: Run `UnicycleOdometryEkf` / `KinematicAltitudeSlopeEkf` motion predict/update loops measuring elapsed step latency (`System.nanoTime`) and hardware-timestamp Delta-T deviation, emitting JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Delta-T jitter <= 2.0 ms and measured EKF cycle p95 latency <= 2.0 ms.

### TST-NFR-002: **Stationary Pinning Drift Prevention Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-002`
- **Component**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_ATTFused.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Stationary Replay Simulation (`TST-NFR-002.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Robolectric replay with stationary GNSS jitter within deadband + IMU rest state on `SpeedAndLocationDevice`.
  3. *Action*: Replay 30-minute (1,800-tick) stationary window followed by movement resumption, measuring horizontal drift (m/min), unpin latency (s), and replay runtime, emitting JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Accumulated horizontal drift < 0.5 m/min; coordinate unpinning <= 1.0 s upon movement resumption.

### TST-NFR-003: **Periodic Tick Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-003`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured `TrackerService` 1-Hz Tick Latency Integration Benchmark (`TST-NFR-003.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Active `TrackerService` Robolectric recording session after JIT warmup.
  3. *Action*: Measure >= 200 consecutive 1-Hz tick cycles via `System.nanoTime`, compute p95/p99/max execution duration, and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Tick p99 execution duration < 50 ms on 1000 ms cycle (>= 950 ms headroom).

### TST-NFR-004: **CPU Consumption Ceiling Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-004`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — `simpleperf` / `dumpsys cpuinfo` (`TST-NFR-004.1`).
  2. *Preconditions*: Physical Pixel reference device; active 30-minute background tracking session with screen off, GNSS 1 Hz, and 2 BLE sensors connected.
  3. *Action*: Execute `adb shell dumpsys cpuinfo` / Android Studio CPU Profiler capture across the 30-minute session and record attestation (device model, Android build, date, operator, measured % single-core CPU, PASS/FAIL). Never falsely satisfied by JVM unit tests.
  4. *Expected Result*: Attested average single-core CPU consumption < 5.0%.

### TST-NFR-005: **Dedicated Heap Footprint Ceiling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-005`
- **Component**: `TrackerService.java`, `LiveWorkoutSession.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Endurance Heap Footprint Benchmark (`TST-NFR-005.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Warmed JVM/Robolectric `TrackerService` / `LiveWorkoutSession` session with post-GC baseline heap recorded.
  3. *Action*: Replay 3,600+ 1-Hz multi-sensor telemetry ticks, invoke GC, measure retained heap delta (`MB`) and total heap (`MB`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Dedicated retained heap delta < 40 MB; total heap RAM <= 150 MB.

### TST-NFR-006: **Tracking Battery Drain Ceiling Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-006`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Battery Historian / `dumpsys batterystats` (`TST-NFR-006.1`).
  2. *Preconditions*: Physical Pixel reference device unplugged at >= 90% SoC; active GNSS + paired BLE/ANT+ sensors.
  3. *Action*: Reset `adb shell dumpsys batterystats --reset`, run 60-minute screen-off tracking and 60-minute screen-on cockpit map tracking (50% brightness), dump `batterystats`, and record attested discharge rates (%/h), device, build, date, operator, and verdict.
  4. *Expected Result*: Attested battery drain <= 5.0%/h screen off; <= 12.0%/h screen on with cockpit map.

### TST-NFR-007: **Sensor Dispatch Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-007`
- **Component**: `BANALService.java`, `ProxySensor.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Sensor Dispatch Latency Benchmark (`TST-NFR-007.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Warmed Robolectric `BANALService` / `ProxySensor` pipeline with active subscribers.
  3. *Action*: Dispatch >= 1,000 synthetic sensor events, measure timestamp delta (`System.nanoTime`) to subscriber delivery, and emit p99/max latency (ms) to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured p99 dispatch latency < 25 ms with zero dropped packets.

### TST-NFR-008: **Sensor Connection Failover Timeout Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-008`
- **Component**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `MyBTLEDevice.java`, `BANALService.java`, `ProxySensor.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Disconnect/Watchdog Failover Benchmark (`TST-NFR-008.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Active radar/BLE/proxy sensor state with `WATCHDOG_TIMEOUT_MS = 3500L` and secondary source or reconnect handler.
  3. *Action*: Simulate abrupt packet starvation, advance/observe watchdog & failover dispatch timing, measure elapsed failover/reconnect initiation latency (`ms`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured failover/reconnect initiation <= 3.5 s (`3500 ms`) observing §4.8 priority hierarchy.

### TST-NFR-009: **Tracking WakeLock Bounding Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-009`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6735`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured `ShadowPowerManager` WakeLock Lifecycle Test (`TST-NFR-009.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Robolectric `TrackerService` lifecycle driving `onCreate` -> `onStartCommand(START_NORMAL)` -> `PAUSED` -> `TRACKING` -> `ACTION_STOP` -> `onDestroy`.
  3. *Action*: Assert `ShadowPowerManager` WakeLock `isHeld()` for `aTrainingTracker:TrackingLock` across every transition, measure release latency (`ms`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: WakeLock held throughout active tracking and pause monitoring; released immediately (`0` leaked WakeLocks) upon `ACTION_STOP` / `onDestroy` finalization.

### TST-NFR-010: **Database Sample Write Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-010`
- **Component**: `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6735`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured SQLite WAL Write Latency Benchmark (`TST-NFR-010.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Real Robolectric SQLite database in WAL mode after warmup inserts on a background executor (`mDbExecutor` semantics).
  3. *Action*: Measure per-row `SQLiteDatabase.insertOrThrow` latency over >= 1,000 consecutive 1-Hz workout sample rows, verify non-main-thread execution, and emit p95/p99/max ms to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured p99 write latency < 30 ms per sample with zero operations on the Android main thread.

### TST-NFR-011: **Telemetry Storage Growth Rate Ceiling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-011`
- **Component**: `WorkoutSamplesDatabaseManager.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured SQLite Storage Progression Integration Test (`TST-NFR-011.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Real Robolectric SQLite workout samples table with WAL checkpoint baseline size measured.
  3. *Action*: Insert 3,600 consecutive 1-Hz multi-sensor rows (1-hour workout), run `PRAGMA wal_checkpoint(TRUNCATE)`, measure `.db` file byte delta (`KB/h`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured storage growth <= 360 KB/h (~100 bytes/sample).

### TST-NFR-012: **Cockpit Frame Render Budget Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-012`
- **Component**: `SensorGridScreen.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — AndroidX Macrobenchmark `FrameTimingMetric` / `dumpsys gfxinfo` (`TST-NFR-012.1`).
  2. *Preconditions*: Physical Pixel device rendering `SensorGridScreen` live 1-Hz telemetry cards for 60 seconds.
  3. *Action*: Collect `adb shell dumpsys gfxinfo com.atrainingtracker.trainingtracker framestats` (or Macrobenchmark `FrameTimingMetric`) and record attested p95 frame time (ms), device, build, date, operator, and verdict.
  4. *Expected Result*: Attested 95th-percentile frame render duration < 16.6 ms (sustaining >= 60 FPS).

### TST-NFR-013: **Map Polyline Update Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-013`
- **Component**: `MapLayers.kt`, `ATrainingTrackerMap.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Map Polyline Decimation Benchmark (`TST-NFR-013.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Warmed JVM benchmark with a 10,000-point synthetic GPS `LatLng` track.
  3. *Action*: Execute `IncrementalTrackDecimator` / `decimateLiveTrack` (`MapLayers.kt`) across incremental coordinate appends, measure elapsed `System.nanoTime` (ms), and emit p95/max ms to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured polyline geometry update latency < 30 ms.

### TST-NFR-014: **Cockpit Telemetry Emission Rate Throttling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-014`
- **Component**: `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Coroutine `TestScope` 1-Hz Emission Throttling Test (`TST-NFR-014.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: High-frequency (> 4 Hz) mock sensor updates feeding `BANALServiceRepository` / `TrackingTabsViewModel`.
  3. *Action*: Advance virtual time over 5.0 seconds (`5,000 ms`), count distinct `allFilteredSensorData` / `StateFlow` periodic emissions, verify rate <= 1.0 Hz, and emit measured emission count & interval to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: <= 5 distinct periodic UI emissions over 5.0 seconds (throttled to <= 1.0 Hz).

### TST-NFR-015: **Cold Start Startup Latency Budget Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-015`
- **Component**: `MainActivityWithNavigation.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Cold Start `am start -W` / Macrobenchmark `StartupMode.COLD` (`TST-NFR-015.1`).
  2. *Preconditions*: Physical Pixel device with app force-stopped before each of >= 5 cold launches.
  3. *Action*: Measure TTID (`TotalTime`) and TTFD (`reportFullyDrawn`) via `adb shell am start -W` / Macrobenchmark and record attested median/p95 ms, device, build, date, operator, and verdict.
  4. *Expected Result*: Attested TTID <= 1200 ms; TTFD <= 1500 ms.

### TST-NFR-016: **GNSS Horizontal Trajectory Accuracy Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-016`
- **Component**: `MySensorManager.java`, `SpeedAndLocationDevice_ATTFused.kt`, `docs/architecture.md` §4.8
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Open-Sky Field Track vs Surveyed Reference (`TST-NFR-016.1`).
  2. *Preconditions*: Open-sky field course with surveyed RTK/ground-truth polyline on physical Pixel device.
  3. *Action*: Record workout track, export GPX, compute orthogonal cross-track error against reference polyline, and record attested p95 error (m), RMSE (m), device, build, date, operator, and verdict.
  4. *Expected Result*: Attested 95th-percentile horizontal error <= 5.0 m; cross-track RMSE <= 3.0 m.

### TST-NFR-017: **Production Crash-Free Session Stability Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-017`
- **Component**: `TrainingApplication.java`, `docs/architecture.md`
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Play Console Android Vitals Crash Rate (`TST-NFR-017.1`).
  2. *Preconditions*: Production/staging release telemetry accumulated in Google Play Console Android Vitals over rolling 30-day window.
  3. *Action*: Export rolling 30-day crash-free session rate from Play Console / Crashlytics and record attested percentage, release version, date, operator, and verdict.
  4. *Expected Result*: Attested production crash-free session rate >= 99.5% (staging >= 99.0%).

### TST-NFR-018: **Application Not Responding (ANR) Rate Ceiling Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-018`
- **Component**: `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Play Console Android Vitals ANR Rate & StrictMode (`TST-NFR-018.1`).
  2. *Preconditions*: Production release monitored in Google Play Console Android Vitals plus on-device StrictMode session audit.
  3. *Action*: Export rolling 30-day user-perceived ANR rate from Play Console, inspect StrictMode/Perfetto logs, and record attested ANR rate (%), release version, date, operator, and verdict.
  4. *Expected Result*: Attested production ANR rate <= 0.05% (threshold 0.47%); zero main-thread blocking I/O violations.

### TST-PRO-072: **Release Manager System Verification Evidence Integration & Release Gating Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-072`
- **Component**: `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt`, `docs/architecture.md`
- **Jira**: `ATT-6169`, `ATT-8157`
- **Test Description**:
  1. *Skill System Evidence Gate Specification Contract Test (`TST-PRO-072.1`, ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Assert that `.agents/skills/release-manager/SKILL.md` exists, mandates unit/`INT-SWE-01..08` + `SYS.5` (`sys_verification_record.md`) + `verification_summary.md` release gates, and does NOT mandate `system_integration_results.json` or invalid `--output-json` as a blocking SYS.4 gate.
  2. *Release Tag Blocking Logic Contract Test (`TST-PRO-072.2`, ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Assert that the release checklist and step-by-step procedures explicitly block tag creation and Play Store distribution if `sys_verification_record.md` or `verification_summary.md` is missing, failing, or has unapproved deviations.
  3. *Release Template & Directory Structure Contract Test (`TST-PRO-072.3`, ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Assert that `docs/releases/` `README.md` and `TEMPLATE.md` specify `docs/releases/<tag>/` layout, `SYS.5` sign-off criteria, and non-gating simulator self-test status.
  4. *Contract Test Suite Execution & Governance Parity (`TST-PRO-072.4`, ReleaseManagerSystemEvidenceContractTest.kt & tools/verify_requirement_governance.py)*:
  • Assert `ReleaseManagerSystemEvidenceContractTest.kt` passes cleanly under Gradle and `python3 tools/verify_requirement_governance.py` confirms 100% living documentation parity.

### TST-PRO-073: **Evidence-Based Requirement Status Model and Master Index Parity Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-073`
- **Scope**: Contract Tests & Tooling Unit Tests
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6126`
- **Test Description**:
  1. *Headings Standardization (`TST-PRO-073.1`, AspiceSweAuditContractTest.kt)*:
     • Assert that all living requirement and test headings end with standard status tokens: `[Verified]`, `[Implemented]`, `[Planned]`, or `[Open]`.
  2. *4-Column Master Table Parity (`TST-PRO-073.2`, AspiceSweAuditContractTest.kt)*:
     • Assert that master tables in `docs/requirements.md` and `docs/tests.md` have 4 numeric columns (Total, Verified, Implemented, Planned/Open) matching shard counts with 100% mathematical parity.
  3. *Verifier 4-Column Sync & Parity (`TST-PRO-073.3`, tools/test_verify_requirement_governance.py)*:
     • Assert that `verify_master_table_parity()` and `recalculate_master_index_tables()` correctly calculate and output 4-column master table records.
  4. *Evidence Status Verification Rule (`TST-PRO-073.4`, tools/test_verify_requirement_governance.py)*:
     • Assert that requirements claiming `[Verified]` without an existing TST ID fail verification, while requirements citing existing TST IDs pass cleanly.

### TST-PRO-074: **Release Verification Summary Generator Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-074`
- **Component**: `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`
- **Jira**: `ATT-6138`, `ATT-8157`
- **Test Description**:
  1. *CLI Argument Parsing & Default Output Path Test (`TST-PRO-074.1`, test_generate_release_verification_summary.py)*:
  • Verify CLI accepts `--tag`, `--commit`, `--out`, and coverage threshold flags, correctly defaulting output to `docs/releases/<tag>/verification_summary.md`.
  2. *Unit Test Report Parsing & Archival Link Test (`TST-PRO-074.2`, test_generate_release_verification_summary.py)*:
  • Verify parsing of JUnit XML reports and versioned archive reference `build-evidence/junit-test-results-<tag>.zip`.
  3. *Coverage JSON Integration & Threshold Verification Test (`TST-PRO-074.3`, test_generate_release_verification_summary.py)*:
  • Verify extraction of bidirectional REQ-TST and TST-Code metrics from `--coverage-json` output and threshold breach detection.
  4. *SYS.5 Gate & Non-Blocking Legacy Simulator Summary Test (`TST-PRO-074.4`, test_generate_release_verification_summary.py)*:
  • Verify parsing of `sys_verification_record.md` flags missing/unsigned SYS.5 protocols under deviations requiring human approval, while missing `system_integration_results.json` raises zero blocking deviations and legacy files render honestly as non-blocking simulator/informational telemetry.
  5. *End-to-End Markdown Summary Generation & Governance Parity Test (`TST-PRO-074.5`, test_generate_release_verification_summary.py & tools/verify_requirement_governance.py)*:
  • Verify end-to-end generation of `verification_summary.md` and confirm 100% living documentation parity.

### TST-PRO-075: **Requirement Tests Field Format & Traceability Governance Verifier Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-075`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6125`
- **Test Description**:
  1. *Conforming Syntax Acceptance Test (`TST-PRO-075.1`, test_verify_requirement_governance.py)*:
  • Assert that single and multiple backticked test IDs (e.g. `` `TST-CON-001` ``, `` `TST-CON-001`, `TST-CON-002` ``) and valid placeholders (`None`, `Planned`, `Review`, `Manual inspection`) pass validation with 0 errors.
  2. *Corrupted Sentence Fragment Rejection Test (`TST-PRO-075.2`, test_verify_requirement_governance.py)*:
  • Assert that unclosed backticks, dangling boolean expressions (e.g. `code == 201 check...`), and trailing pipe metadata (e.g. `| **Tickets**: ATT-...`) are detected as violations.
  3. *Misplaced Source File Rejection Test (`TST-PRO-075.3`, test_verify_requirement_governance.py)*:
  • Assert that Kotlin/Java/XML filenames in `Tests:` fields are detected as violations.
  4. *Clean-Room Shard Audit & Governance Parity (`TST-PRO-075.4`, verify_requirement_governance.py)*:
  • Assert `tools/verify_requirement_governance.py` passes with exit code 0 across all 9 shards and master index tables match with 100% parity.

### TST-PRO-076: **System-to-Software Requirement Traceability & Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-076`
- **Scope**: Tooling Unit Tests, Contract Tests & Governance Integration
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`, `docs/project_governance.md`
- **Jira**: `ATT-6163`
- **Test Description**:
  1. *SYSREQ Citation Integrity Unit Test (`TST-PRO-076.1`, test_verify_requirement_governance.py)*:
  • Verify that `verify_system_software_traceability()` passes when all cited `SYSREQ-*` IDs exist in `docs/system/sysreq_*.md`, and fails with descriptive errors when an ungrounded or dangling `SYSREQ-XXX` is cited.
  2. *SYSREQ Coverage by Software Requirements Test (`TST-PRO-076.2`, test_verify_requirement_governance.py)*:
  • Verify that 100% of declared system requirements (`SYSREQ-TRK-001`..`006`, `SYSREQ-DEX-001`..`006`) are refined by software requirements, and flag missing coverage if any system requirement is unrefined.
  3. *Domain & Prefix Tailoring Governance Rule Test (`TST-PRO-076.3`, test_verify_requirement_governance.py)*:
  • Verify that software requirements belonging to tailored prefix families (`UI`, `UNT`, `PRO`, etc.) are recognized as software-derived (`[SWE-Derived]`) with valid architectural rationale.
  4. *CLI Integration & Living Documentation Parity Test (`TST-PRO-076.4`, tools/verify_requirement_governance.py & AspiceSweAuditContractTest.kt)*:
  • Verify end-to-end execution of `python3 tools/verify_requirement_governance.py --verify-sys-trace` and assert 100% master index parity across all living documentation shards.

### TST-PRO-077: **Requirement Attributes Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-077`
- **Scope**: Tooling Unit Tests, Contract Tests & Governance Integration
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `.agents/skills/stage2-req-test-spec/SKILL.md`, `templates/test_spec_template.md`, `docs/requirements.md`
- **Jira**: `ATT-6136`
- **Test Description**:
  1. *Requirement Attribute Presence & Domain Validation Unit Test (`TST-PRO-077.1`, test_verify_requirement_governance.py)*:
  • Verify that `verify_requirement_attributes()` passes when all three attributes (Priority, Type, Verification Method) are present and valid, and fails when any attribute is missing, empty, or has an invalid value.
  2. *Git Diff Enforcement & Legacy Tolerance Unit Test (`TST-PRO-077.2`, test_verify_requirement_governance.py)*:
  • Verify that diff-based checking enforces all three attributes on newly added or modified requirements while emitting warnings for untouched legacy requirements.
  3. *Strict CLI Enforcement Flag Test (`TST-PRO-077.3`, test_verify_requirement_governance.py)*:
  • Verify that `--enforce-attributes` / `--fail-on-missing-attributes` fails with exit code 1 if any requirement in the repository lacks valid attributes.
  4. *Template & Living Documentation Parity Audit (`TST-PRO-077.4`, tools/verify_requirement_governance.py)*:
  • Verify that Stage 2 skill template, deliverable template, and `docs/requirements.md` contain standardized attribute fields and living documentation master tables maintain 100% parity.

### TST-PRO-079: **Aftermath Reviews Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-079`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, `ZoneDistributionCardTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6128`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 (100% REQs with TST), 0 gaps in Category 2 for TST-REV (100% TSTs with code), 0 gaps in Category 3 for TST-REV, and 0 gaps in Category 4 for TST-REV (100% bidirectional citation).
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `aftermath_reviews`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 12 `REQ-REV-*` requirements define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardTest"` asserting 100% pass rate.

### TST-PRO-078: **Tracking Cockpit Verification & Bidirectional Traceability Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-078`
- **Scope**: Tooling Unit Tests, Contract Tests & Governance Integration
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`
- **Jira**: `ATT-6127`
- **Test Description**:
  1. *Tracking Cockpit Domain Coverage Test (`TST-PRO-078.1`, test_verify_requirement_governance.py)*:
  • Verify that `calculate_traceability_coverage()` across `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` yields $\ge 95\%$ Category 1 coverage and $\ge 95\%$ Category 4 bidirectional coverage with zero ungrounded [Verified] claims.
  2. *Zero Dangling Reference Verification Test (`TST-PRO-078.2`, verify_requirement_governance.py)*:
  • Verify that `find_dangling_references()` detects zero dangling references in `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` after removing `TST-FUSION-001`..`003` and `REQ-TRK-035` from legacy allowlists.
  3. *Living Documentation Master Table Parity Test (`TST-PRO-078.3`, AspiceSweAuditContractTest.kt)*:
  • Verify 100% mathematical parity across all 4 columns of `docs/requirements.md` and `docs/tests.md` via `verify_master_table_parity()` and `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.

### TST-PRO-080: **Aftermath Periods Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-080`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`, `PeriodBarGraphTest.kt`, `WorkoutShareBottomSheetContractTest.kt`, `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyCalculatorTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6129`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for aftermath_periods (100% REQs with TST), 0 gaps in Category 4 for aftermath_periods, and 0 Category 3 tags for SPT.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `aftermath_periods`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 41 requirements in `req_aftermath_periods.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "*PeriodBarGraph*" --tests "*SportingJourney*"` asserting 100% pass rate.

### TST-PRO-081: **Maps and Navigation Test Evidence Backfill & Bidirectional Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-081`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Scope**: Governance Verification, Coverage Analysis & Unit Tests
- **Component**: `docs/requirements/req_maps_navigation.md`, `docs/tests/tst_maps_navigation.md`, `WaypointBadgeMarkerTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6131`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for maps_navigation (100% REQs with TST), resolution of `TST-MAP-038` in Category 3, and 100% bidirectional citation for all MAP and NAV test specifications in Category 4.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references in `maps_navigation` (pruning `REQ-MAP-037` from legacy allowlist) and zero ungrounded Verified statuses.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 75 requirements in `req_maps_navigation.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.WaypointBadgeMarkerTest"` asserting 100% pass rate.

### TST-PRO-082: **Settings and Profiles Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-082`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_settings_profiles.md`, `docs/tests/tst_settings_profiles.md`, `WorkoutClusterCounterTest.kt`, `WorkoutClusterStatsTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6133`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for settings_profiles (100% REQs with TST), 0 gaps in Category 4 for settings_profiles, and 0 Category 3 tags for SET.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `settings_profiles`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 102 requirements in `req_settings_profiles.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "*Cluster*" --tests "*Setting*"` asserting 100% pass rate.

### TST-PRO-083: **Data Migration and Export Test Evidence Backfill & Bidirectional Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-083`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Scope**: Governance Verification, Coverage Analysis & Unit Tests
- **Component**: `docs/requirements/req_data_migration_export.md`, `docs/tests/tst_data_migration_export.md`, `WorkoutDeletionHelperTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6134`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for data_migration_export (100% REQs with TST), resolution of `REQ-DAT-010` ungrounded claim, and 100% bidirectional citation for all DAT, EXP, EXT, and MIG test specifications in Category 4.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references in `data_migration_export` and zero ungrounded Verified statuses (pruning `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS`).
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 130 requirements in `req_data_migration_export.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest"` asserting 100% pass rate.

### TST-PRO-084: **Store Presence Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-084`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_store_presence.md`, `docs/tests/tst_store_presence.md`, `PlayStoreListingContractTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6135`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for store_presence (100% REQs with TST), 0 gaps in Category 2 for store_presence (10/10 TSTs tagged in code), and 0 gaps in Category 4 for store_presence.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `store_presence`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 10 requirements in `req_store_presence.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.

### TST-PRO-085: **UI Design System Test Evidence Backfill & Bidirectional Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-085`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Scope**: Governance Verification, Coverage Analysis & Unit Tests
- **Component**: `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6132`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for ui_design_system (100% REQs with TST), 0 ungrounded claims, and 100% bidirectional citation for all UI and UNT test specifications in Category 4.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links`, `--check-evidence`, and `--enforce-unique` asserting zero dangling references and zero ungrounded Verified statuses in `ui_design_system` (pruning all UI items from legacy allowlists).
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 452 requirements in `req_ui_design_system.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.banalservice.sensor.formater.TimeFormatterTest"` asserting 100% pass rate.

### TST-PRO-086: **Process Stability Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-086`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6130`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for process_stability (100% REQs with TST), zero dangling references originating from this domain, and >= 95% Category 4 bidirectional parity.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `process_stability`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all requirements in `req_process_stability.md` and test specifications in `tst_process_stability.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `python3 -m unittest tools/test_verify_requirement_governance.py` asserting 100% pass rate across all governance verifier unit tests.

### TST-PRO-087: **Machine-Verified Bidirectional Traceability & Ratchet Baseline Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-087`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/traceability_baseline.json`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-7561`
- **Test Description**:
  1. *Bidirectional Orphan Verification*: Run `python3 tools/verify_requirement_governance.py` asserting all declared REQs have verifying TSTs and all TSTs declare valid REQs, or are recorded in `tools/traceability_baseline.json`.
  2. *Baseline Ratchet Monotonicity Test*: Execute unit tests in `tools/test_verify_requirement_governance.py` simulating addition of new unlinked REQs and baseline growth, verifying immediate CI failure.
  3. *Syntax Flexibility & Shorthand Normalization*: Verify parser supports `Verifies:` and legacy `Requirement(s):` syntax, and confirm normalized links for `TST-MIG-001`, `TST-UI-310`, and the 6 historical unreferenced requirements.
  4. *Targeted Unit Test Verification*: Execute `python3 -m unittest tools/test_verify_requirement_governance.py` asserting 100% pass rate.

---

### TST-PRO-088: **System Verification Protocol Sign-Off & Release Gating Verification Suite** [Planned]
- **Verifies**: `REQ-PRO-088`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/releases/V5.0.0/sys_verification_record.md`, `tools/run_system_integration_tests.py`, `.github/workflows/android-release.yml`, `tools/test_generate_release_verification_summary.py`, `tools/test_system_integration_tests.py`
- **Jira**: `ATT-7560`, `ATT-8141`
- **Test Description**:
  1. *SYS.5 Verification Record & Release Gating Contract*: Verify that `docs/releases/V5.0.0/sys_verification_record.md` exists, defines all 8 `SYS-VRF-001`..`008` cases, validates absence of synthetic placeholder serials (`*-REF-*`), and enforces human PO attestation prior to release qualification.
  2. *System Integration Runner & Wire Protocol Alignment*: Execute `tools/run_system_integration_tests.py` verifying all 6 cases (`SYS-INT-001`..`006`) pass cleanly including `SYS-INT-003`.
  3. *Release Verification Summary Gating*: Run `tools/generate_release_verification_summary.py --tag V5.0.0 --fail-on-deviations` asserting that unbacked or placeholder verification records correctly yield `BLOCKED` and missing human attestation prevents release candidate authorization.
  4. *Targeted Regression Test Execution*: Run unit tests in `tools/test_generate_release_verification_summary.py` and `tools/test_system_integration_tests.py` asserting 100% pass rate.


---

### TST-PRO-089: **Kover Verification Gate Execution and Multi-Package Rule Audit** [Verified]
- **Verifies**: `REQ-PRO-089`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/build.gradle`, `WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSectionReorderContractTest.kt`, `docs/tests.md`
- **Jira**: `ATT-7563`
- **Test Description**:
  1. *Test Suite Hygiene Verification*: Execute `./gradlew testDebugUnitTest` and assert all unit tests pass (100% pass rate).
  2. *Kover Global Rule Verification*: Execute `./gradlew koverVerifyDebug` and assert line coverage $\ge 40\%$ and branch coverage $\ge 35\%$.
  3. *Kover Subsystem Rule Verification*: Verify bounded rules for `tracker`, `filter`, and `devices`/`sensor` packages pass in `koverVerifyDebug`.
  4. *Negative Mutation Verification*: Artificially elevate bounds (e.g., line 99%) and assert build failure, verifying gate enforcement.
  5. *Documentation Parity*: Confirm `docs/tests.md` and `docs/coding_standards.md` reflect ratcheted figures.

---

### TST-PRO-090: **Unified CI & Release Quality Gates Contract Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-090`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `.github/workflows/android-release.yml`, `.github/workflows/android-branch-build.yml`
- **Jira**: `ATT-7564`
- **Test Description**:
  1. *Release Workflow Gate Presence & Ordering Contract*: Execute `ReleaseCiAndLintConfigContractTest.testReleaseWorkflow_runsLintDetektKoverAndGovernanceBeforeAssembly` verifying that `android-release.yml` executes `lintDebug`, `detekt`, `koverVerifyDebug`, and `verify_requirement_governance.py` prior to `assembleRelease :app:bundleRelease`.
  2. *Release Workflow Report Artifact Archiving Contract*: Execute `ReleaseCiAndLintConfigContractTest.testReleaseWorkflow_archivesQualityGateReports` verifying that `android-release.yml` uploads artifacts for lint, detekt, and kover reports with `if: always()`.
  3. *Branch Build Governance Step Contract*: Execute `ReleaseCiAndLintConfigContractTest.testBranchBuildWorkflow_runsGovernanceVerification` verifying that `android-branch-build.yml` executes `python3 tools/verify_requirement_governance.py`.
  4. *Governance Script Validation*: Run `python3 tools/verify_requirement_governance.py` asserting exit code 0 and zero governance violations across all shards.

---

### TST-PRO-091: **Independent ASPICE SUP.1 QA Auditing, Representative Sampling & Corrective Action Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-091`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `docs/engineering/qa_audits/`
- **Jira**: `ATT-7558`, `ATT-8152`, `ATT-8153`
- **Evidence**: Verified by `test_tst_pro_091_7_living_doc_shard_resolution`, `test_tst_pro_091_8_issue_description_ingestion`, `test_tst_pro_091_9_account_id_role_separation`, `test_tst_pro_091_10_seeded_random_sampling`, `test_tst_pro_091_11_dedicated_corrective_action_creation_and_deduplication`, and `test_tst_pro_091_13_ceremony2_retrospective_qa_tracking_protocol` in `tools/test_jira_accounts.py` (79/79 tests passed), `test_scenario_76_tst_pro_091_12_verifier_rejects_self_referential_corrective_actions` in `tools/test_verify_requirement_governance.py` (82/82 tests passed), and full Python test suite pass (181/181 passed).
- **Test Description**:
  1. *QA Auditor Role Independence & Coordinator Rejection*: Test in `test_jira_accounts.py` that `qa_audit` executes under `qa` / `agent2` and rejects `role="coordinator"` or `role="agent1"`.
  2. *Representative Sample Size Dynamic Scaling*: Test in `test_jira_accounts.py` that `qa_audit` samples $\ge 10\%$ when population exceeds 20 tickets (e.g. 25 tickets $\rightarrow \ge 3$ sampled).
  3. *Corrective Action Link Validation in Verifier*: Test in `test_verify_requirement_governance.py` that `verify_qa_audits` rejects `NON-COMPLIANT` reports lacking Jira issue keys in Section 4 and accepts reports with linked corrective actions.
  4. *Auditor Independence Verification in Verifier*: Test in `test_verify_requirement_governance.py` that `verify_qa_audits` rejects reports with `Auditor Role: coordinator`.
  5. *Counterpart Mirror Parity*: Verify parity in Höchstmaß mirror tooling (`test_hoechstmass_tools.py`).
  6. *Governance Script Validation*: Run `python3 tools/verify_requirement_governance.py` verifying exit code 0.
  7. *Living Document Shard Resolution (`TST-PRO-091.7`, tools/test_jira_accounts.py)*: Verify `qa_audit` resolves requirement references across all living requirement shards (`docs/requirements/req_*.md`) and passes compliant tickets.
  8. *Issue Description Ingestion (`TST-PRO-091.8`, tools/test_jira_accounts.py)*: Verify `fetch_all_sprint_issues` requests and parses the `description` field for requirement references.
  9. *AccountId Role Separation (`TST-PRO-091.9`, tools/test_jira_accounts.py)*: Verify `qa_audit` checks comment author `accountId` against `ROLE_ACCOUNT_IDS`, correctly evaluating independent reviews.
  10. *Seeded Random Sampling (`TST-PRO-091.10`, tools/test_jira_accounts.py)*: Verify `qa_audit` uses seeded pseudo-random sampling and records the random seed and sampled keys in the markdown header.
  11. *Dedicated Corrective Action Ticket Creation & Deduplication (`TST-PRO-091.11`, tools/test_jira_accounts.py)*: Verify `qa_audit` creates or links distinct dedicated corrective action tickets in raw Backlog (`add_to_sprint=False`, `rank_top=True`), deduplicated by non-conformance category, ensuring `ca_key != audited_key` in Section 4.
  12. *Self-Reference Rejection in Verifier (`TST-PRO-091.12`, tools/test_verify_requirement_governance.py)*: Verify `verify_qa_audits` rejects reports where corrective action links match the audited ticket key (`ca_key == audited_key`) for active/current sprints while grandfathering legacy historical audits prior to `2026-11-16_02_Spät`.
  13. *Ceremony 2 Retrospective QA Tracking & Multi-Sprint PO Escalation (`TST-PRO-091.13`, .agents/skills/sprint-planner/SKILL.md)*: Verify Ceremony 2 instructions require a dedicated 'QA Findings & Corrective Actions' section in the Retrospective and explicit PO escalation when an unclosed corrective action remains open for > 2 sprints.

### TST-PRO-092: **Process Metrics Credibility, Bug Severity Rubric & Downstream Escape Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-092`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/`, `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `docs/project_governance.md`
- **Jira**: `ATT-7559`
- **Test Description**:
  1. *Jira API Issue Field Query & Severity Extraction*: Test that `collect_sprint_metrics_for_id` requests `labels` and `description` and that `parse_sprint_gate_metrics` correctly detects `severity-critical` and `severity-major` labels without defaulting to Minor.
  2. *Atlassian Document Format (ADF) Comment Parsing*: Test that `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments` correctly extract text and parse PASS / REVISION / BOUNCE from ADF dict structures without throwing `AttributeError` or `TypeError`.
  3. *Segregation of Unverified Gate Subtasks*: Test that gate subtasks in `Erledigt` status without verdict comments increment `unverified` and do NOT increment `passed_first_time` or inflate FTPR.
  4. *Downstream Defect Escape Accounting*: Test that Stage 6 bounces are incorporated into upstream gate escape metrics, yielding non-zero escape probabilities for gates with downstream defects.
  5. *Plausibility Warning Thresholds*: Test that `parse_sprint_gate_metrics` generates a plausibility warning when FTPR is 100% across $\ge 20$ gate subtasks without rejections.
  6. *Bug Severity Rubric Enforcement*: Test in `test_jira_accounts.py` that `create_issue` enforces valid severities and flags crash/ANR origin bugs when submitted with sub-Major severity without justification.


---

### TST-PRO-093: **Governance Rules Normative Structure and Shard Size Capping Enforcement Test** [Verified]
- **Verifies**: `REQ-PRO-093`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`, `.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`
- **Jira**: `ATT-7565`
- **Test Description**:
  1. *Shard Size Verification Test (`tools/test_shard_living_docs.py`)*: Assert `check_shard_sizes()` flags temporary shards exceeding `max_lines` and passes for shards within limits.
  2. *Oversized Shard Splitting Test (`tools/test_shard_living_docs.py`)*: Verify `split_oversized_shard()` partitions an oversized shard into sub-shards with zero ID loss or format corruption.
  3. *Governance Contract Preservation*: Run `test_verify_requirement_governance.py`, `test_agent_emulator.py`, and `AspiceSweAuditContractTest.kt`, asserting all tests pass without regression.
  4. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% master table parity and zero dangling references.


---

### TST-PRO-094: **Legacy Requirement Attribute Backfill and Strict Verification Gate Suite** [Verified]
- **Verifies**: `REQ-PRO-094`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/`
- **Jira**: `ATT-7562`
- **Test Description**:
  1. *Missing Attribute Discovery*: Assert `find_missing_requirement_attributes()` returns 0 defective requirements.
  2. *Strict Verifier Execution*: Run `verify_requirement_attributes(enforce_attributes=True)` and assert pass with message `PASS: 100% of living requirements define valid Priority, Type, and Verification Method attributes.`
  3. *Negative Mutation Verification*: In `tools/test_verify_requirement_governance.py`, assert that any requirement without attributes fails verification.
  4. *Governance Parity Verification*: Execute `python3 tools/verify_requirement_governance.py` and confirm 100% parity with zero warnings.


---

### TST-PRO-095: **Weekly Dual-Vendor Code Review Governance and Automation Verification** [Verified]
- **Verifies**: `REQ-PRO-095`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/engineering/code_reviews/`
- **Jira**: `ATT-8213`
- **Test Description**:
  1. *Canonical Directory Registration Test (`tools/test_verify_requirement_governance.py`)*: Assert `code_reviews` is recognized in `ALLOWED_ENGINEERING_ENTRIES` and `verify_canonical_engineering_entries()` passes with `docs/engineering/code_reviews/` present.
  2. *Subsystem Coverage Matrix Parsing & Slice Selection Test (`tools/test_jira_accounts.py`, [Verified] ATT-8426)*: Assert helper function `get_next_review_subsystem()` correctly parses `coverage_matrix.md` with extended package mapping (`repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, `climbs/`) and selects unreviewed or oldest audited subsystem slices.
  3. *Weekly Review Ticket Creation & Deduplication Test (`tools/test_jira_accounts.py`, [Verified] ATT-8426)*: Mock Jira API and verify `ensure_weekly_review_ticket()` creates a ticket with generic "current top model" specification and `rank_top=True` under `ATT-232` when overdue and skips creation when an active review ticket already exists.
  4. *7-Day Cadence & Decoupled Release Gate Test (`tools/test_jira_accounts.py`, [Verified] ATT-8426)*: In `test_is_weekly_review_overdue`, verify that `is_weekly_review_overdue()` returns `False` when the latest review is $\le 7$ days old even when other subsystems remain `UNREVIEWED`, preventing release gating deadlock.
  5. *Governance Documentation, Model Mandate & Claude Non-Simulation Protocol Review (`[Verified] ATT-8426`)*: Verify generic model formulation and explicit Claude non-simulation handoff protocol across `docs/coding_standards.md`, `release_plan.md`, `coverage_matrix.md`, and `template_dual_vendor_code_review.md`.
  6. *Living Documentation Master Parity*: Run `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-096: **ASPICE 4.0 SUP.1 QA Governance, Audit Evidence Store, and Automation Verification** [Verified]
- **Verifies**: `REQ-PRO-096`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/management/qa_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/engineering/aspice_audits/`
- **Jira**: `ATT-8212`
- **Test Description**:
  1. *Canonical Directory Registration Test (`tools/test_verify_requirement_governance.py`)*: Assert `aspice_audits` is recognized in `ALLOWED_ENGINEERING_ENTRIES` and `verify_canonical_engineering_entries()` passes with `docs/engineering/aspice_audits/` present.
  2. *ASPICE Audit Currency & Overdue Check Test (`tools/test_jira_accounts.py`)*: Assert `is_weekly_aspice_audit_overdue()` correctly evaluates elapsed days against records in `docs/engineering/aspice_audits/`.
  3. *Weekly ASPICE Audit Ticket Creation & Deduplication Test (`tools/test_jira_accounts.py`)*: Mock Jira API and verify `ensure_weekly_aspice_audit_ticket()` creates a ticket with `rank_top=True` under `ATT-232` when overdue and skips creation when an active ticket already exists.
  4. *SUP.1 QA Plan Governance & Release Gate Review*: Verify alignment across `docs/management/qa_plan.md`, `cm_plan.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
  5. *Living Documentation Parity*: Run `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-097: **Capacity-Bound Bin-Packing, V5.0.0 Release Plan, and FixVersion Governance Verification** [Verified]
- **Verifies**: `REQ-PRO-097`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/management/release_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, `.agents/rules/aspice_governance.md`
- **Jira**: `ATT-8162`
- **Test Description**:
  1. *Multi-Lane Capacity Bin-Packing Test (`tools/test_jira_accounts.py`)*: Assert `select_greedy_bin_pack_tickets()` packs candidate tickets in strict `ORDER BY rank ASC` across disjoint Epics, respects `max_sp_per_lane` / capacity bounds, and preserves excess tickets of saturated Epics for subsequent sprints.
  2. *FixVersion Warning Suppression Test (`tools/test_jira_accounts.py`)*: Assert `show_issue()` does not emit FixVersion missing warnings when tickets are in non-terminal statuses (`Zu erledigen`, `In Bearbeitung`, `In Überprüfung`), but alerts when status is `Erledigt` and `fixVersions` is empty.
  3. *Release Plan Structure, Field Test Path & Pseudo-Sprint Review (`[Verified] ATT-8426`)*: Inspect `docs/management/release_plan.md` confirming full coverage of `V5.0.0` milestone epics, 12h continuous cadence, synchronization with `docs/releases/V5.0.0/sys_verification_record.md` (Section 3), and reference to pseudo-sprint `Bereit für PO Systemtest` (ID 454).
  4. *Governance & Lifecycle Parity Review*: Verify consistency across `.agents/rules/aspice_governance.md` (Rule 8.3 & Rule 85), `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
  5. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-098: **Human-Attested Field Evidence Extractor, Release Guarding and Governance Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-098`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/extract_pixel_field_evidence.py`, `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `docs/releases/TEMPLATE.md`, `docs/releases/V5.0.0/sys_verification_record.md`, `.agents/rules/aspice_governance.md`
- **Jira**: `ATT-8141`
- **Test Description**:
  1. *Placeholder Serial & Synthetic Data Rejection Test*: In `tools/test_generate_release_verification_summary.py`, verify that `collect_sys5_summary()` detects placeholder serials (`P8-REF-2024-001`, `P6-REF-2022-044`, `*-REF-*`) and treats them as blocking deviations.
  2. *Human PO Attestation Gating Test*: In `tools/test_generate_release_verification_summary.py`, assert that missing human PO attestation (missing signature, date, or build SHA) reports `BLOCKED` under `generate_release_verification_summary.py`.
  3. *Pixel Telemetry Extractor & Gap Coach Test (`TST-PRO-098.3`, [Verified] ATT-8427)*: In `tools/test_extract_pixel_field_evidence.py`, verify that `tools/extract_pixel_field_evidence.py` parses authentic `WorkoutSummaries` and `ExtremumValues` schema, extracts genuine sensor signals, reports `None` for unmeasured metrics, correctly parses TCX XML files without synthetic values, and outputs prioritized gap recommendations for unexercised sensor combinations.
  4. *V5.0.0 Protocol & Template Schema Audit*: Verify `docs/releases/TEMPLATE.md` and `docs/releases/V5.0.0/sys_verification_record.md` contain the Human PO Attestation block and zero synthetic assertions.
  5. *Agent Governance Rule Verification*: Assert that `.agents/rules/aspice_governance.md` mandates `human-execution` labeling and forbids autonomous agents from fabricating physical test evidence.
  6. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-099: **Test Result-Driven Verification Status & Evidence Gating Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-099`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `.agents/skills/stage5-verification/SKILL.md`
- **Jira**: `ATT-8143`
- **Test Description**:
  1. *JUnit XML Result Ingestion & TST Mapping Test (`tools/test_verify_requirement_governance.py`)*: Assert parser extracts testcases from JUnit XML files (`TEST-*.xml`), maps method names and code tags to `TST-XXX` IDs, and aggregates pass/fail/error counts.
  2. *Failing Test Result Evidence Gate Test (`tools/test_verify_requirement_governance.py`)*: Assert `verify_verified_test_evidence()` rejects `[Verified]` status with exit code 1 when a mapped test has `<failure>` or `<error>` in JUnit XML.
  3. *Unbacked TST Rejection & Ratchet Baseline Test (`tools/test_verify_requirement_governance.py`)*: Assert net-new `[Verified]` TST without passing test evidence fails, while legacy baseline items within `LEGACY_UNBACKED_VERIFIED_TSTS` pass with warning.
  4. *Stage 5 Evidence-Gated Promotion Test (`tools/test_jira_accounts.py`)*: Assert `auto_verify_living_docs_on_stage5()` checks test results before flipping status, promoting only items with passing evidence and keeping unbacked/failing items as `[Implemented]`.
  5. *Clean Worktree Default Verification Safety Test*: Assert default `verify_requirement_governance.py` passes cleanly in fresh worktree without requiring pre-existing build artifacts.
  6. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.

---

### TST-PRO-100: **Versioned Test and Coverage Evidence Contract & Gating Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-100`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/generate_test_evidence.py`, `tools/test_generate_test_evidence.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `docs/management/cm_plan.md`, `.agents/rules/aspice_governance.md`
- **Jira**: `ATT-8144`
- **Test Description**:
  1. *Test Evidence Generator Unit Suite (`TST-PRO-100.1`, tools/test_generate_test_evidence.py)*:
  • Assert parser extracts JUnit XML test results and calculates total, passed, failed, error, and skipped counts.
  • Assert parser correlates test cases to `TST-XXX` IDs and produces structured pass/fail mapping.
  • Assert parser parses Kover XML report and extracts global and per-package line and branch coverage percentages.
  • Assert generator produces valid, readable `docs/engineering/test_evidence/<target>.md` and `.json` files.
  2. *Governance Gating & Directory Structure Test (`TST-PRO-100.2`, tools/test_verify_requirement_governance.py)*:
  • Assert `verify_engineering_directory_structure()` allows `docs/engineering/test_evidence/` and rejects `docs/engineering/test_results` symlink.
  • Assert `verify_requirement_governance.py --check-test-evidence` validates presence, format, and non-empty status of sprint and release evidence files.
  3. *Release CI Contract & CM Plan Verification (`TST-PRO-100.3`, ReleaseCiAndLintConfigContractTest.kt)*:
  • Assert `ReleaseCiAndLintConfigContractTest.kt` verifies retirement of `docs/engineering/test_results` symlink and presence of `docs/engineering/test_evidence/`.
  • Assert `docs/management/cm_plan.md` registers `docs/engineering/test_evidence/` under controlled Configuration Items.
  4. *Living Documentation Parity*:
  • Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.

---

### TST-PRO-101: **Unbacked Status Triage, Test-Tag Reconciliation & Ratchet Reduction Suite (ATT-8145)** [Verified]
- **Requirement(s)**: `REQ-PRO-101`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassToolingContractTest.kt`, `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`
- **Jira**: `ATT-8145`
- **Test Description**:
  1. *Deterministic Unbacked Triage & CSV Ledger Test (`TST-PRO-101.1`, tools/test_verify_requirement_governance.py)*:
  • Verify `--triage-unbacked` classifies unbacked `[Verified]` TSTs across all shards into `unit-test-backed`, `UI/manual-only`, `obsolete/duplicate`, and `missing test code`, flags stale already-backed `LEGACY_UNBACKED_VERIFIED_TSTS` entries, and writes `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` cleanly under `ALLOWED_ENGINEERING_ENTRIES`.
  2. *Test-Tag Reconciliation & Evidence Verification Test (`TST-PRO-101.2`, tools/test_verify_requirement_governance.py & targeted Gradle unit tests)*:
  • Verify `extract_test_code_tags()` detects newly added `TST-*` tags across `*Test.kt`, `*Test.java`, and `tools/test_*.py` suites for `unit-test-backed` specs, and targeted Gradle / Python unit tests pass.
  3. *Truthful Downgrade, Orphan & Heading Suffix Compliance Test (`TST-PRO-101.3`, AspiceSweAuditContractTest.kt & verify_requirement_governance.py)*:
  • Verify downgraded `UI/manual-only` (incl. `TST-MIG-006`), `obsolete/duplicate`, `missing test code`, `14` `[Verified]` orphans, and unbacked REQs use standard `[Verified|Implemented|Planned|Open]` suffixes (`testLivingDocShards_allHeadingsStandardized`), and `--sync-indexes` maintains 100% master table parity (`testMasterTableParity_matchesDomainShards`).
  4. *Ratchet Reduction & Scenario 60/61 Fixture Test (`TST-PRO-101.4`, tools/test_verify_requirement_governance.py)*:
  • Verify `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet reduction passes `--check-verified-evidence` with exit code `0`, and `test_scenario_60` / `test_scenario_61` validate ratchet enforcement and clean-worktree safety using parameterized `baseline_allowlist` / reconciled fixtures.
  5. *Höchstmaß Mirror Parity Contract Test (`TST-PRO-101.5`, HoechstmassToolingContractTest.kt & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Verify `HoechstmassToolingContractTest` and `python3 -m unittest discover` in `tools/hoechstmass/tools/` pass with zero hardcoded `ATT` project/user IDs.

---

### TST-PRO-102: **Boundary-Safe SYSREQ Traceability & Bidirectional Coverage Verification Suite (ATT-8146)** [Verified]
- **Requirement(s)**: `REQ-PRO-102`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-8146`
- **Test Description**:
  1. *SYSREQ Boundary False-Positive Rejection Test (`TST-PRO-102.1`, tools/test_verify_requirement_governance.py)*:
  • Verify a document containing only `SYSREQ-DEX-001` or `SYSREQ-TRK-001` does NOT extract `REQ-DEX-001` / `REQ-TRK-001` into `sys_to_swe` or `_collect_test_file_index()`, while valid standalone `REQ-*` references still match cleanly.
  2. *Derived Prefix Exclusion Test (`TST-PRO-102.2`, tools/test_verify_requirement_governance.py)*:
  • Verify `REQ-PRO-*` (and other `SWE_DERIVED_PREFIXES`) mentioning `SYSREQ-*` are excluded from `sys_to_swe` and `swe_refinement_total_count`.
  3. *Bidirectional Coverage & Live CLI Invariant Verification Test (`TST-PRO-102.3`, [Verified] ATT-8437, tools/test_verify_requirement_governance.py)*:
  • Verify `report` exposes accurate `swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, and `swe_to_sys_map`; verify live `--verify-sys-trace --report-sys-coverage` prints `12/12` (`100.0%`) forward and reverse `System-Refinement` coverage with exit code `0`, and `test_scenario_68` asserts structural invariants: exact forward parity `covered_count == sysreq_count == 12`, lower-bound floors `swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`, and zero `REQ-DEX-*` / `REQ-PRO-*` pollution.

---

### TST-PRO-103: **Complete Bidirectional SYS-to-SWE Traceability & System-Refinement Classification Suite (ATT-8147)** [Verified]
- **Requirement(s)**: `REQ-PRO-103`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-8147`
- **Test Description**:
  1. *System Requirement(s) Attribute & Derived (SW-only) Parser Test (`TST-PRO-103.1`, tools/test_verify_requirement_governance.py)*:
  • Verify `verify_system_software_traceability()` parses `- **System Requirement(s)**:` for both `SYSREQ-*` links and `Derived (SW-only)` rationales, classifies `RAD`/`CLM` prefixes cleanly, and rejects unclassified `SYSTEM_REFINED_PREFIXES` REQs under `--check-reverse-sys-trace` / `fail_on_unclassified_swe=True`.
  2. *Live Repository 100% Bidirectional Classification & Invariant Test (`TST-PRO-103.2`, [Verified] ATT-8437, tools/test_verify_requirement_governance.py)*:
  • Verify `--check-reverse-sys-trace` and `--verify-sys-trace --report-sys-coverage` pass on the live repository with `12/12` (`100.0%`) forward coverage, and `test_scenario_70` asserts structural invariants: `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`, and `reverse_classified_pct == 100.0`.
  3. *System Contract & Fixture Compatibility Test (`TST-PRO-103.3`, System*ContractTest.kt & tools/test_verify_requirement_governance.py)*:
  • Verify `SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, `SystemVerificationProtocolContractTest`, `AspiceSweAuditContractTest`, and synthetic fixtures (`test_scenario_40..43`, `test_scenario_66..68`) all pass with exit code `0`.

---

### TST-PRO-104: **Code-to-Doc Orphan Guard, Shard Header Count Sync & REQ Description Governance Suite (ATT-8148)** [Verified]
- **Requirement(s)**: `REQ-PRO-104`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-8148`
- **Test Description**:
  1. *Code-to-Shard Orphan Detection & Typo Resolution Test (`TST-PRO-104.1`, tools/test_verify_requirement_governance.py)*:
  • Verify `verify_code_traceability_orphans()` scans `app/src/main`, `app/src/test`, `app/src/androidTest`, and `tools/`, flags undeclared `REQ-*` and `TST-*` tokens, passes cleanly with 0 orphans on the live repo after restoring missing specs and fixing `REQ-UI-314` and `REQ-MIG-053` typos, and validates synthetic orphan injection failure.
  2. *Living Doc Shard Header Synchronization & Audit Test (`TST-PRO-104.2`, tools/test_verify_requirement_governance.py)*:
  • Verify `recalculate_master_index_tables()` parses and writes exact header counts across all 18 shards in `docs/requirements/` and `docs/tests/`, and verify governance check rejects any shard with divergent `**Total Requirements**` or `**Total Tests**` headers.
  3. *Description Field Ratchet & Field Coverage Reporting Test (`TST-PRO-104.3`, tools/test_verify_requirement_governance.py)*:
  • Verify `--check-req-description` enforces `- **Description**:` on non-baselined requirements (including `REQ-PRO-104`), rejects new REQs lacking description, and verify `--report-req-field-coverage` outputs accurate percentages for Description, Acceptance Criteria, and Allocation fields.
  4. *Restored Requirement Governance & Evidence Cross-Check Test (`TST-PRO-104.4`, tools/test_verify_requirement_governance.py)*:
  • Verify all restored requirements in `SYSTEM_REFINED_PREFIXES` define valid `- **System Requirement(s)**:`, no restored test is marked `[Verified]` without test code evidence, and `--check-verified-evidence` and `--check-reverse-sys-trace` exit `0`.

---

### TST-PRO-105: **Explicit PO Approval & Changelog Provenance for Agent-Originated Change Requests Suite (ATT-8166)** [Verified]
- **Requirement(s)**: `REQ-PRO-105`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8166`
- **Test Description**:
  1. *Agent Backlog Placement & Direct Sprint Block Test (`TST-PRO-105.1`, tools/test_jira_accounts.py)*:
  • Verify `create_issue` creates issues in raw Backlog without sprint assignment and rejects `--add-to-sprint` when invoked under agent roles (`agent1`, `agent2`, `coordinator`).
  2. *Backlog-to-PO-Queue Two-Way Guard Test (`TST-PRO-105.2`, tools/test_jira_accounts.py)*:
  • Verify moving issues from raw Backlog into `Vom PO Freigegeben` (ID 447) or an active sprint is hard-rejected for all agent roles, while Coordinator `pack-sprint` (`447 -> active_sprint`) and `return-unstarted` (`active_sprint -> 447`) continue to function.
  3. *PO Changelog Provenance & QA Audit Dimension 6 Test (`TST-PRO-105.3`, tools/test_jira_accounts.py)*:
  • Verify `verify_po_approval_changelog()` parses Jira changelog history, passes issues moved to ID 447 or created by human PO `accountId`, rejects unapproved agent-originated issues, and asserts `qa_audit()` integrates Dimension 6 (`po_approval_pass`) while exempting ceremony tickets.
  4. *Historical Exception Disposition & Living Doc Parity Test (`TST-PRO-105.4`, Jira / tools/test_jira_accounts.py)*:
  • Verify `ATT-7902` disposition record confirms tests-only PO authorization, and verify all living doc shards pass governance checks with Description field ratchet compliance.

---

### TST-PRO-106: **Atomic REQ and TST ID Reservation Across Parallel Sprint Lanes Suite (ATT-8149)** [Verified]
- **Requirement(s)**: `REQ-PRO-106`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8149`
- **Test Description**:
  1. *Atomic Cross-Worktree ID Reservation Test (`TST-PRO-106.1`, tools/test_jira_accounts.py)*:
  • Verify `reserve_id()` locks `/tmp/att_id_reservation.lock`, persists reservations to `$(git rev-parse --git-common-dir)/att_id_reservations.json`, guarantees cross-worktree visibility across simulated parallel worktrees, and prevents duplicate allocations under concurrent threads/processes.
  2. *Monotonic Next-ID Calculation with Active Reservation Awareness Test (`TST-PRO-106.2`, tools/test_jira_accounts.py)*:
  • Verify next ID selection considers both living documentation files and active ledger reservations, correctly producing `max(committed, reserved) + 1` for single and multi-count allocations.
  3. *Gate 2 Reservation Provenance Verification Test (`TST-PRO-106.3`, tools/test_jira_accounts.py)*:
  • Verify `gate-verdict` on Stage 2 subtasks passes when all new REQ/TST IDs match the parent ticket reservation and rejects with a governance error when an unreserved ID is detected.
  4. *Reservation Hygiene, Expiration & Code Orphan Parity Test (`TST-PRO-106.4`, tools/test_jira_accounts.py)*:
  • Verify reservation release on ticket lifecycle completion, ledger cleanup, and verify `verify_requirement_governance.py --check-code-orphans` reports zero false positives.

---

### TST-PRO-107: **Separated Gate Review Authorship, Evidence Checklists & Zero-Revision Alerts Suite (ATT-8150)** [Verified]
- **Requirement(s)**: `REQ-PRO-107`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8150`
- **Test Description**:
  1. *Separated Authorship Enforcement Test (`TST-PRO-107.1`, tools/test_jira_accounts.py)*:
  • Verify `submit_stage` detects if the caller actor matches the Gate N reviewer on the same parent ticket, rejects self-review handoffs with an error, and permits valid alternating author submissions.
  2. *Evidence-Backed Gate 1–5 Checklist Validation Test (`TST-PRO-107.2`, tools/test_jira_accounts.py)*:
  • Verify `gate_verdict` accepts verdicts with structured checklists and verifiable evidence anchors (`file:line`, commit SHA, test execution hashes), rejects unstructured or anchorless text, and flags rapid reviews.
  3. *Multi-Sprint 100% FTPR Rubber-Stamping Alert Test (`TST-PRO-107.3`, tools/test_jira_accounts.py)*:
  • Verify `sprint_metrics` inspects historical sprint metrics, triggers a Rubber-Stamping Warning when consecutive sprints exhibit 100.0% FTPR, and verifies integration into retro comments.
  4. *Rule & Living Documentation Parity Test (`TST-PRO-107.4`, tools/test_jira_accounts.py)*:
  • Verify updated rules in `aspice_governance.md` and `jira-workflow/SKILL.md` conform to separated pair reviews and all living doc shards pass governance checks with zero code orphans.

---

### TST-PRO-108: **Technical Role Separation for Agent Credentials & Git Commit Identity Suite (ATT-8151)** [Verified]
- **Requirement(s)**: `REQ-PRO-108`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8151`
- **Test Description**:
  1. *Subagent Identity & Role Binding Enforcement Test (`TST-PRO-108.1`, tools/test_jira_accounts.py)*:
  • Verify that when `JIRA_ACTOR` is set in the environment (e.g. `agent1`), passing a mismatched `--as` flag (e.g. `--as agent2`) raises a SystemExit role mismatch error. Verify that matching `--as agent1` succeeds, and verify that when `JIRA_ACTOR` is unset, `--as coordinator` and manual role invocations continue to succeed without rejection.
  2. *Dedicated QA Role Credential Mapping & Account Independence Test (`TST-PRO-108.2`, tools/test_jira_accounts.py)*:
  • Verify that `resolve_account("qa")` resolves `JIRA_QA_USER` and `JIRA_QA_TOKEN`, verify that `get_config(role="qa")` isolates QA credentials, and verify validation rejects configurations where `agent2` and `qa` share identical account IDs.
  3. *Producing Agent Identity & Reviewer Git Commit Trailers Test (`TST-PRO-108.3`, tools/test_jira_accounts.py)*:
  • Verify that `_merge_ticket_impl` formats squash commit messages with standard git trailers (`Agent-Role:`, `Agent-Model:`, `Reviewed-by:`), correctly reading from environment or arguments and passing automated trailer validation.
  4. *Single-OS User Residual Risk Review Test (`TST-PRO-108.4`, docs/management/risk_register.md)*:
  • Verify that `docs/management/risk_register.md` contains an approved entry documenting the residual risk and defense-in-depth controls for co-located subagent execution under a single OS account.

---

### TST-PRO-109: **Stage Transition Precondition Enforcement, Impl-Plan Gate Validation & Bypass Prevention Suite (ATT-8154)** [Verified]
- **Requirement(s)**: `REQ-PRO-109`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8154`
- **Test Description**:
  1. *Predecessor Stage Precondition Enforcement in `submit-stage` Test (`TST-PRO-109.1`, tools/test_jira_accounts.py)*:
  • Verify that calling `submit_stage` with stage 4 on a parent ticket in `Analysis` raises SystemExit with non-zero exit code. Verify that Stage 1 allows transition from `zu erledigen` to `analysis`, and Stages 2..5 succeed when the parent is in the expected stage.
  2. *Stage 6 Gate 5 Prerequisite Enforcement in `final-review-verdict` Test (`TST-PRO-109.2`, tools/test_jira_accounts.py)*:
  • Verify that `final_review_verdict` raises SystemExit with non-zero exit code when called on a parent ticket whose status is not `Final Review (Agent)`. Verify that `--bounce-target` healing flow back to `Implementation` or `Analysis` continues to succeed.
  3. *Strict Subtask & Impl-Plan Summary Validation in `check-gate` Test (`TST-PRO-109.3`, tools/test_jira_accounts.py)*:
  • Verify that `check_gate` raises SystemExit with exit code 1 when passed a non-subtask issue key or a subtask with summary `[Analysis]` even if its status is `Erledigt`. Verify that it passes (exit code 0) only when the issue is a sub-task, its summary matches `[Impl-Plan]`, and its status is `Erledigt`.
  4. *Structured Pros/Cons & Recommendation Validation Test (`TST-PRO-109.4`, tools/test_jira_accounts.py)*:
  • Verify that `final_review_verdict` rejects plain keyword-stuffed unstructured text on `PASS`, and successfully accepts structured reports matching `stage6-final-review/SKILL.md` sections with bullets or structured options tables.
  5. *Sanctioned Coordinator Workflow Invariants Test (`TST-PRO-109.5`, tools/test_jira_accounts.py)*:
  • Verify that Coordinator operations (`pack_sprint`, `return_unstarted`, `carryover_wip`, `archive_po_acceptance`, `defer_to_systemtest_queue`), `ATT-8153` QA non-conformance ticket generation, and `ATT-8426` decoupled 7-day review currency checks remain 100% functional.

---

### TST-PRO-110: **Integration Verification Test Strategy & Evidence Integrity Suite (ATT-8155)** [Verified]
- **Requirement(s)**: `REQ-PRO-110`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `tools/generate_test_evidence.py`
- **Jira**: `ATT-8155`
- **Test Description**:
  1. *Architecture Integration Sequence Parity Test (`TST-PRO-110.1`, build/AspiceSweAuditContractTest.kt)*:
  • Verify that `AspiceSweAuditContractTest` asserts 100% bidirectional traceability between `INT-SWE-01..04`, `IF-SWE-01..04`, and executable integration test classes.
  2. *Robolectric Level 1 Execution & Fast Feedback Test (`TST-PRO-110.2`, integration/*.kt)*:
  • Verify that all four integration test suites execute under `./gradlew testDebugUnitTest` within fast turnaround bounds (<= 30s) without requiring emulator or physical hardware.
  3. *Machine-Readable Test Evidence & JUnit XML Archival Test (`TST-PRO-110.3`, tools/generate_test_evidence.py)*:
  • Verify that `tools/generate_test_evidence.py` and `tools/jira_util.py export-test-evidence` parse JUnit XML results for `INT-SWE-01..04` and archive versioned evidence under `docs/engineering/test_evidence/`.
  4. *Living Documentation & Shard Parity Test (`TST-PRO-110.4`, tools/verify_requirement_governance.py)*:
  • Verify that all living documentation shards pass governance checks with zero dangling references and zero code orphans.

---

### TST-PRO-111: **INT-SWE-05..08 Integration Verification & Strategy Parity Suite (ATT-8156)** [Verified]
- **Requirement(s)**: `REQ-PRO-111`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `docs/architecture/integration_strategy.md`
- **Jira**: `ATT-8156`
- **Test Description**:
  1. *Integration Test Suite & Spec Lock (`TST-PRO-111.1`, build/AspiceSweAuditContractTest.kt)*:
  • Verify `DomainCalculatorsIntegrationTest.kt`, `PersistenceSqliteIntegrationTest.kt`, `FileExportersIntegrationTest.kt`, and `CloudHealthConnectIntegrationTest.kt` exist on disk, use `RobolectricTestRunner` (`sdk = [34]`), and cite `TST-INT-013..016`.
  2. *Integration Strategy Factual Sync (`TST-PRO-111.2`, docs/architecture/integration_strategy.md)*:
  • Verify `integration_strategy.md` reflects authentic `sprint_455.md` / `ATT-8500` metrics (`6,540` tests, `1,119` suites, `29m 21s` Tier-2 / `2,635.73s` sum), Level 1 `INT-SWE-01..08` scope, and `emulator-5554` policy.
  3. *Machine-Readable Evidence & Governance (`TST-PRO-111.3`, tools/verify_requirement_governance.py)*:
  • Verify JUnit XML captures `TST-INT-013..016` and `TST-PRO-111`, and living documentation shards pass governance checks with zero dangling references or code orphans.

---

### TST-PRO-112: **Simulator Self-Test Scoping & Release Gate Honesty Test Suite (ATT-8157)** [Verified]
- **Requirement(s)**: `REQ-PRO-112`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt`, `tools/test_generate_release_verification_summary.py`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-8157`
- **Test Description**:
  1. *Simulator Self-Test Rename & Purity Test (`TST-PRO-112.1`, tools/test_simulator_self_tests.py & SystemIntegrationTestContractTest.kt)*:
  • Verify `tools/run_simulator_self_tests.py` (`SimulatorSelfTestRunner`) tests `ble_hr_simulator.py`, `ble_power_simulator.py`, and `ble_radar_simulator.py` without fake Android/constant-arithmetic claims, `--output` JSON export succeeds, legacy `run_system_integration_tests.py` is removed, and `SystemIntegrationTestContractTest.kt` passes.
  2. *Non-Blocking Release Gate Removal Test (`TST-PRO-112.2`, tools/test_generate_release_verification_summary.py & ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Verify `collect_sys4_summary()` in `tools/generate_release_verification_summary.py` raises zero deviations when `system_integration_results.json` is absent, does not falsely emit `[ PASS ]` SYS.4 app integration status, and `ReleaseManagerSystemEvidenceContractTest.kt` asserts `SKILL.md` / `docs/releases/` / `docs/architecture.md` gate strictly on unit/`INT-SWE-01..08`, `SYS.5`, and `verification_summary.md`.
  3. *Honest Documentation & V5.0.0 Reclassification Audit (`TST-PRO-112.3`, tools/verify_requirement_governance.py)*:
  • Verify `docs/architecture.md` (§6.2, §6.4), `docs/system/sys_integration_tests.md`, `docs/releases/V5.0.0/` (`verification_summary.md`, `system_integration_results.json`), and living docs explicitly state simulator-only scope, `INT-SWE-01..08` + `AppSmokeAndroidTest.kt` + manual `SYS.5` boundaries, and pass `verify_requirement_governance.py` with exit 0.

---

### TST-PRO-113: **Measured NFR Verification, Manual Protocol & Evidence Ingestion Suite (ATT-8158)** [Verified]
- **Requirement(s)**: `REQ-PRO-113`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/MeasuredNfrBenchmarkTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `docs/engineering/test_evidence/nfr_manual_protocol_template.md`, `tools/generate_test_evidence.py`, `tools/test_generate_test_evidence.py`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-8158`
- **Test Description**:
  1. *Measured JVM/Robolectric NFR Benchmark Execution (`TST-PRO-113.1`, MeasuredNfrBenchmarkTest.kt)*:
  • Verify that the 11 measurable NFR benchmarks (`TST-NFR-001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014`) run warmup + measured passes, assert documented budgets, and emit structured JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  2. *Manual Protocol Classification & Anti-False-Green Guard (`TST-PRO-113.2`, NonFunctionalRequirementsContractTest.kt)*:
  • Verify that `NonFunctionalRequirementsContractTest.kt` checks authentic benchmark/evidence artifacts and enforces `On-Device Manual` + non-`[Verified]` status for unattested hardware NFRs (`004, 006, 012, 015, 016, 017, 018`) instead of raw doc-string presence.
  3. *Test Evidence Ingestion & Governance Parity (`TST-PRO-113.3`, tools/test_generate_test_evidence.py & verify_requirement_governance.py)*:
  • Verify that `tools/generate_test_evidence.py` ingests `app/build/reports/nfr/nfr_measurements.json` and manual protocol status into `docs/engineering/test_evidence/<target>.{md,json}`, and `verify_requirement_governance.py` passes with exit 0.

---

### TST-PRO-114: **Living Detailed Design & Code-Parity Verification Suite (ATT-8159)** [Verified]
- **Requirement(s)**: `REQ-PRO-114`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`, `docs/architecture/detailed_design/tracker_service.md`, `docs/architecture/detailed_design/banal_service.md`, `docs/architecture/detailed_design/my_btle_device.md`, `docs/architecture.md`, `.agents/skills/stage3-impl-plan/SKILL.md`
- **Jira**: `ATT-8159`
- **Test Description**:
  1. *Detailed Design Structural & Mermaid Completeness Test (`TST-PRO-114.1`, DetailedDesignParityContractTest.kt)*:
  • Verify that `docs/architecture/detailed_design/tracker_service.md`, `banal_service.md`, and `my_btle_device.md` exist, each declare `stateDiagram-v2`, event/guard/transition tables, interface/threading/error-handling sections, and reference authentic `ATT-7902` characterisation and unit test classes.
  2. *Code-Parity Symbol & State Existence Verification (`TST-PRO-114.2`, DetailedDesignParityContractTest.kt)*:
  • Verify that every documented enum/state constant (`TrackingMode`, `AutoPauseState`, `TrackerService.StartType`, `MyBTLEDevice.State`), key lifecycle/concurrency field & method symbol, and cited test class resolves verbatim against `app/src/main/` and `app/src/test/` with zero drift.
  3. *SWE.2 Cross-Reference, Stage 3 Governance & Living Doc Parity (`TST-PRO-114.3`, DetailedDesignParityContractTest.kt & verify_requirement_governance.py)*:
  • Verify `docs/architecture.md` cross-references `docs/architecture/detailed_design/`, `.agents/skills/stage3-impl-plan/SKILL.md` mandates living detailed-design updates for the 3 core units in Stage 3 plans/reviews, and `verify_requirement_governance.py` exits `0`.

---

### TST-PRO-115: **Architecture & Interface Drift Remediation and JVM-Reflection Signature Contract Suite (ATT-8160)** [Verified]
- **Requirement(s)**: `REQ-PRO-115`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/InterfaceSignatureDriftContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `docs/architecture.md`, `docs/architecture/interfaces.md`
- **Jira**: `ATT-8160`
- **Test Description**:
  1. *JVM-Reflection Interface Signature Drift Verification (`TST-PRO-115.1`, InterfaceSignatureDriftContractTest.kt)*:
  • Parse operation/method signatures in `docs/architecture/interfaces.md` (`IF-SWE-01`..`IF-SWE-08`) and resolve provider classes via `Class.forName` without reading `app/src/main/` source files (`REQ-PRO-049`).
  • Verify via `declaredMethods` that every documented method/property exists with matching parameter count (arity), parameter types, return type, and nullability (`@Nullable`/`@NotNull` or Kotlin nullable signature parity), including `evaluateTick` (7 params), `calculateNormalizedPower` (nullable `Double?` return and `List<Double?>`), `PowerHrDynamicsCalculator.calculate` (5 params), `suggestCluster` (11 params), `detectClimbs` (6 params), `recordLiveSample` (8 params returning `Boolean`), `updateExtremaValue` (5/6 params), `syncWorkout` (3 params), `importRecentHrv` (4 params), and `BANALServiceComm` generic/raw return types.
  2. *Symbol Anchor, Allocation Prefix & ASPICE 4.0 BP Conformance (`TST-PRO-115.2`, ArchitectureConformanceContractTest.kt & AspiceSweAuditContractTest.kt)*:
  • Verify `docs/architecture.md` §4.9 references `TrackerService#acquireWakeLock` / `releaseWakeLock` (zero brittle `:line` citations), `ArchitectureConformanceContractTest.testArchitectureDoc_definesResourceAndTimingBudgets` enforces symbol anchors alongside `PARTIAL_WAKE_LOCK`, `mScheduler`, `mDbExecutor`, and `REQ-NFR-003`..`014`, Section 2 includes `SPT`, `HRV`, `RAD`, `WKO`, `CLM`, and SWE.2 BP headings align to ASPICE 4.0 (`BP1`–`BP5`).
  3. *Source-Reading Audit & Governance Parity (`TST-PRO-115.3`, SourceTextReadingAuditContractTest.kt & verify_requirement_governance.py)*:
  • Verify `SourceTextReadingAuditContractTest` remains 100% green (new test makes zero `src/main` file reads) and `verify_requirement_governance.py` exits `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-116: **Retrospective Architecture Decision Records (ADR-002..ADR-007) & Reflection Contract Suite (ATT-8161)** [Verified]
- **Requirement(s)**: `REQ-PRO-116`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AdrCompletenessContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `docs/architecture.md`, `docs/architecture/adr_002_foreground_tracking_service.md`, `docs/architecture/adr_003_banal_sensor_bus.md`, `docs/architecture/adr_004_sqlite_managers_vs_room.md`, `docs/architecture/adr_005_hybrid_preferences_datastore.md`, `docs/architecture/adr_006_incremental_compose_navigation.md`, `docs/architecture/adr_007_ble_ant_sensor_abstraction.md`
- **Jira**: `ATT-8161`
- **Test Description**:
  1. *ADR Structural, Alternatives & Honest PO Sign-Off Verification (`TST-PRO-116.1`, AdrCompletenessContractTest.kt)*:
  • Verify `docs/architecture/adr_002..007_*.md` all exist and contain `ADR-001` sections (`Context & Problem Statement`, `Decision Drivers & Evaluated Alternatives`, `Decision Outcome`, `Architectural Consequences & Trade-offs`), `>= 2` evaluated alternatives (`Rejected`/`Selected`), honest `Pending PO Sign-Off` status/decider fields, and zero phantom class tokens (`TrackPointsDatabaseManager`, `ANTDevice`, `BTLEDevice`).
  2. *JVM-Reflection Production Class Grounding & Index Parity (`TST-PRO-116.2`, AdrCompletenessContractTest.kt & AspiceSweAuditContractTest.kt)*:
  • Verify via `Class.forName` (zero `app/src/main/` file reading per `REQ-PRO-049`) that core classes cited in `ADR-002`..`ADR-007` (`TrackerService`, `BANALService`, `BANALServiceRepository`, `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `WorkoutRepository`, `SettingsDataStore`, `TuningPreferencesDataStore`, `MainActivityWithNavigation`, `MyDevice`, `MyRemoteDevice`, `MyANTDevice`, `MyBTLEDevice`, `PolarOnlineStreamingBridge`, `DeviceManager`) exist in compiled bytecode, and `docs/architecture.md` links `ADR-001`..`ADR-007`.
  3. *ADR-001 Preservation, Source-Reading & Governance Parity (`TST-PRO-116.3`, JavaCoreMigrationStrategyContractTest.kt, SourceTextReadingAuditContractTest.kt, verify_requirement_governance.py)*:
  • Verify `JavaCoreMigrationStrategyContractTest` stays green (`ADR-001` untouched), `SourceTextReadingAuditContractTest` flags zero `ATT-8161` reads, and `verify_requirement_governance.py` exits `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-117: **Autonomous Schicht Retro Write-Allowlist & Protected Governance Boundary Suite (ATT-8584)** [Verified]
- **Requirement(s)**: `REQ-PRO-117`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt`
- **Jira**: `ATT-8584`
- **Localization Parity**: N/A - no UI strings
- **Test Description**:
  1. *Python Unit Tests for `verify_retro_write_allowlist()` (`TST-PRO-117.1`, tools/test_verify_requirement_governance.py)*:
     • Execute `python3 -m unittest tools/test_verify_requirement_governance.py` covering: (a) allowlisted `chore(ceremony2)` file set (`retros/Sprint_Review_and_Retro_*.md`, `gates/*`, `qa_audits/*`, `reviews/*`, `test_evidence/*`, `metrics/process_metrics.jsonl`, `docs/management/risk_register.md`) passes; (b) unauthorized edits to protected paths (`.agents/rules/**`, `.agents/skills/**`, `docs/coding_standards.md`, `docs/design_guidelines.md`, `docs/project_governance.md`, `docs/project_protocol.md`, `docs/engineering/rule_history_archive.md`, `tools/**`) or `app/src/**` during `chore(ceremony2)` fail cleanly; (c) dedicated ticket-backed commits (`ATT-8584`), interactive PO-authorized commits (`Interactive-PO: true`), and pre-cutoff historical commits (`8aa881df`) pass without false positives.
  2. *Kotlin Contract & Parity Tests (`TST-PRO-117.2`, AspiceSweAuditContractTest.kt & HoechstmassCoreFrameworkContractTest.kt)*:
     • Execute `./gradlew :app:testDebugUnitTest --tests "*.AspiceSweAuditContractTest" --tests "*.HoechstmassCoreFrameworkContractTest"` verifying `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` codify the autonomous Retro write-allowlist, protected paths, and `Wochen Review & Retro` proposal routing while preserving `sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`, and `REQ-STP-010.2`.
  3. *CLI & Living-Doc Governance Parity (`TST-PRO-117.3`, verify_requirement_governance.py)*:
     • Execute `python3 tools/verify_requirement_governance.py` and `python3 tools/verify_requirement_governance.py --check-retro-allowlist` confirming exit code `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-118: **qa-audit Corrective Action Creation, Synthetic ATT-CA Guard & Heuristic Parity Suite (ATT-8559)** [Verified]
- **Requirement(s)**: `REQ-PRO-118`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `docs/engineering/qa_audits/sprint_2026-11-16_03_nacht.md`
- **Jira**: `ATT-8559`
- **Localization Parity**: N/A - no UI strings
- **Test Description**:
  1. *Mocked Cloud v3 CA Search & `10008` Creation (`TST-PRO-118.1`, tools/test_jira_accounts.py)*:
     • Execute mocked unit test (`Rule 5`: zero live Jira API calls) verifying `qa_audit()` queries CA duplicates via `get_search_endpoint` / `paged_jql_search` (`/rest/api/3/search/jql`), creates CA issues with `issuetype_id="10008"` (`Verbesserung`) under `ATT-8138`, and builds browse links from `config['JIRA_URL']`.
  2. *Fail-Loud CA Error Handling (`TST-PRO-118.2`, tools/test_jira_accounts.py)*:
     • Execute mocked unit test verifying Jira CA search/create exceptions propagate loudly instead of silently generating `ATT-CA-*` placeholder keys.
  3. *4-Augen Gate Verdict Recognition (`TST-PRO-118.3`, tools/test_jira_accounts.py)*:
     • Execute mocked unit test verifying `qa_audit()` passes `role_sep_pass` for `agent2` comments using `Gate 1 PASS` / `Gate 2 Audit PASS` headers as well as `GATE_VERDICT: PASS`, while failing `agent1`-authored verdicts.
  4. *Commit Hygiene & Wildcard Parity (`TST-PRO-118.4`, tools/test_jira_accounts.py)*:
     • Execute mocked unit test verifying `[ATT-XXXX]`, `fix(ATT-XXXX):`, `...(ATT-XXXX)`, and `Ticket: ATT-XXXX` satisfy `commit_pass`, and wildcard `REQ-FIL-*` in deliverables is excluded so `living_doc_pass` succeeds when real IDs resolve.
  5. *Verifier Synthetic `ATT-CA-*` Guard (`TST-PRO-118.5`, tools/test_verify_requirement_governance.py)*:
     • Execute unit tests verifying `_validate_single_qa_audit()` rejects `ATT-CA-*` placeholders on current/post-cutoff non-compliant reports, accepts real `ATT-\d+` keys, and cleanly handles grandfathered/re-audited reports so `--check-qa-audits` exits `0`.
  6. *Re-Audit & Full Governance Validation (`TST-PRO-118.6`, sprint_2026-11-16_03_nacht.md)*:
     • Re-run `qa-audit --as agent2` for Sprint `2026-11-16_03_Nacht`, verify `python3 tools/verify_requirement_governance.py` and `--check-qa-audits` exit `0`, and confirm `tools/test_jira_accounts.py` + `tools/test_verify_requirement_governance.py` pass 100% offline. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-119: **Immutable Annotated Baselines, Content Manifest & CM Plan Alignment Suite (ATT-8163)** [Verified]
- **Requirement(s)**: `REQ-PRO-119`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/baseline_manager.py`, `tools/test_baseline_manager.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/management/cm_plan.md`, `docs/releases/README.md`, `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/release-manager/SKILL.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-8163`
- **Test Description**:
  1. *Immutable Annotated Tagging & Collision Refusal (`TST-PRO-119.1`, tools/test_baseline_manager.py)*:
     • Execute offline unit tests against isolated temporary Git repositories verifying `baseline_manager.py tag` creates an annotated tag (`git cat-file -t <tag> == "tag"`), refuses with non-zero exit when the tag already exists (no `-f` overwrite), and refuses when a same-named branch ref (`refs/heads/<tag>`) exists.
  2. *Baseline Content Manifest Generation (`TST-PRO-119.2`, tools/test_baseline_manager.py)*:
     • Execute unit tests verifying `baseline_manager.py manifest` writes `docs/releases/<baseline>/manifest.md` containing baseline tag, resolved commit SHA, REQ/TST counts from living-doc indexes, linked `docs/engineering/test_evidence/` file & verdict, open risks/deviations, and governance status deterministically.
  3. *Dry-Run Branch/Tag Hygiene & PO Gate (`TST-PRO-119.3`, tools/test_baseline_manager.py)*:
     • Execute unit tests verifying `baseline_manager.py cleanup-audit` lists merged branches and lightweight tags in dry-run mode with zero ref mutations, refuses `--execute` without `--confirm-po`, and deletes only explicitly targeted merged local branches when both `--execute` and `--confirm-po` are supplied.
  4. *Ceremony 2 Manifest Allowlist Parity (`TST-PRO-119.4`, tools/test_verify_requirement_governance.py)*:
     • Execute unit tests verifying `verify_retro_write_allowlist()` accepts `docs/releases/<baseline>/manifest.md` in `chore(ceremony2)` commits while still rejecting protected skill/rule/tool edits (`REQ-PRO-117.2`).
  5. *CM Plan, Skills & Governance Contract Suite (`TST-PRO-119.5`, ReleaseCiAndLintConfigContractTest.kt & verify_requirement_governance.py)*:
     • Execute targeted JVM contract tests (`./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" --tests "com.atrainingtracker.trainingtracker.build.ReleaseManagerSystemEvidenceContractTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"`) asserting `--no-ff` merge policy, complete CI inventory (`gates/`, `qa_audits/`, `reviews/`, `releases/`), `SUP.8.BP7` status accounting, Revision History, `manifest.md` in `docs/releases/README.md`, zero `git tag -f` in `sprint-planner` skills, and `python3 tools/verify_requirement_governance.py` exit `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-120: **Work-Product Header, Human-Only Approval Guard & Governance Reconciliation Suite (ATT-8164)** [Verified]
- **Requirement(s)**: `REQ-PRO-120`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `tools/jira_util.py`, `docs/coding_standards.md`, `docs/management/`, `docs/architecture/`, `docs/system/`, `docs/requirements/`, `docs/tests/`, `.agents/rules/`, `tools/hoechstmass/`, `AspiceSweAuditContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`, `JavaCoreMigrationStrategyContractTest.kt`
- **Jira**: `ATT-8164`
- **Test Description**:
  1. *Standardized Header Coverage (`TST-PRO-120.1`, tools/test_verify_requirement_governance.py & AspiceSweAuditContractTest.kt)*:
     • Execute offline unit tests and Kotlin contract tests verifying all governed files (`docs/management/`, `docs/architecture/` + `detailed_design/`, `docs/system/`, `docs/requirements.md` + 9 shards, `docs/tests.md` + 9 shards, `.agents/rules/`, root governance docs) declare `Document ID` (`DOC-*`), semver `Version`, valid `Status`, `Approver`, and `Change History` table, and that zero `Approved` docs carry AI/Pending `Approver` values.
  2. *Human-Only Approval & Version-Bump Enforcement (`TST-PRO-120.2`, tools/test_verify_requirement_governance.py)*:
     • Execute offline unit tests using isolated temporary Git repositories (`Rule 5`: zero live Jira calls) verifying `verify_work_product_headers()` rejects: (a) missing header fields, (b) governed body edits without a monotonic semver `Version` bump, and (c) setting or retaining `Status: Approved` without `Human-Approved:` / `Interactive-PO:` trailer and non-agent `Approver`; and accepts (d) `Approved` -> `Reviewed` resets with version bump, (e) `Human-Approved:` commits, and (f) pre-cutoff historical commits.
  3. *Contradiction Reconciliation & `tools/hoechstmass/` Parity (`TST-PRO-120.3`, AspiceSweAuditContractTest.kt & HoechstmassCoreFrameworkContractTest.kt)*:
     • Execute Gradle contract tests verifying `9 Core Rules` across `.agents/rules/aspice_governance.md`, `docs/project_protocol.md`, `tools/jira_util.py`, and `tools/hoechstmass/.agents/rules/aspice_governance.md`; `Rules 1–85+` in `project_protocol.md`; `6-Stage` lifecycle phrasing; `max 3` / `2 active` lane concurrency reconciliation; and `Rule 3.4 / Rule 83` in `docs/management/risk_register.md` (with zero `app/src/main/` reads per `REQ-PRO-049.1`).
  4. *Full Contract & Governance Regression (`TST-PRO-120.4`)*:
     • Execute `python3 tools/test_verify_requirement_governance.py`, `python3 tools/verify_requirement_governance.py`, and `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"` and confirm 100% pass rate (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-121: **Problem Resolution Hardening, Bug Trend Metrics, Resolution/Fix Verification, Commit/Conflict Guards & Process-Escape Suite (ATT-8165)** [Verified]
- **Requirement(s)**: `REQ-PRO-121`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `.githooks/commit-msg`, `.githooks/pre-commit`, `.github/workflows/android-branch-build.yml`, `tools/test_sprint_metrics.py`, `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8165`
- **Test Description**:
  1. *Bug Metrics, Open Age & Multi-Sprint Trend Chart (`TST-PRO-121.1`, tools/test_sprint_metrics.py)*:
     • *Given* mocked Jira sprint issues including `Bug` (`10006`) tickets across `Critical`/`Major`/`Minor`/`Cosmetic` severities with `created`, `resolution`, `resolutiondate`, and `Final Review (Human)` vs `Erledigt` statuses plus multi-sprint `process_metrics.jsonl` history, *When* `parse_sprint_gate_metrics()` and `format_metrics_markdown()` execute, *Then* `open_bug_age_days` (`avg`, `max`, `gt_7d`), `fixed_awaiting_human`, and a multi-sprint Bug Trend table/ASCII chart render accurately while `verify_sprint_metrics()` accepts both legacy and extended JSONL lines.
  2. *Bug Resolution States & Fix-Verification Gate (`TST-PRO-121.2`, tools/test_jira_accounts.py)*:
     • *Given* offline mocked Jira REST API responses (including `/rest/api/3/resolution` returning only `id=10000` `Fertig`), *When* `resolve-bug` executes for `rejected`, `duplicate` (with `--duplicate-of`), `cannot-reproduce`, and `fixed`, *Then* standardized `resolution-*` labels and comments are recorded without failing on missing global Jira resolutions, `Erledigt` transitions remain blocked for agents (`REQ-PRO-016`), and advancing a `Bug` to `Final Review (Human)` without fix-verification evidence is rejected.
  3. *Commit Hygiene, Conflict-Marker CI Gate & Git Hooks (`TST-PRO-121.3`, tools/test_verify_requirement_governance.py)*:
     • *Given* isolated temporary Git repositories (`tempfile.TemporaryDirectory`), *When* `verify_conflict_markers()` scans tracked files with `<<<<<<<`/`=======`/`>>>>>>>` and `verify_commit_hygiene()` inspects post-cutoff non-merge commits on `develop` without `ATT-\d+`, *Then* both exit `1`, while clean tracked files, merge commits, and pre-cutoff commits (`<= cutoff_ts`) exit `0`; and `.githooks/commit-msg` and `.githooks/pre-commit` reject invalid commits/markers locally.
  4. *Deduplicated `process-escape` Bug Creation (`TST-PRO-121.4`, tools/test_jira_accounts.py)*:
     • *Given* offline HTTP mocks for Cloud v3 `paged_jql_search` and `create_issue`, *When* `create_process_escape_bug()` / `gate-verdict REVISION --file-escape-bug` / `file-process-escape` runs once for a new gate or Tier-2 failure and again for an already-open identical summary, *Then* the first creates a `Bug` (`10006`) labeled `process-escape` + `severity-*` and the second returns the existing key without creating a duplicate (`Rule 5`: zero live Jira mutations). (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-122: **Sprint Metrics Statistical Correctness, Mirror Parity & CL3–CL5 Claim Suspension Suite (ATT-8168)** [Verified]
- **Requirement(s)**: `REQ-PRO-122`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/sprint_metrics.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassCoreFrameworkContractTest.kt`
- **Jira**: `ATT-8168`
- **Test Description**:
  1. *SPC Percentage Bounds & `process_version` Reset (`TST-PRO-122.1`, tools/test_sprint_metrics.py)*:
     • *Given* `process_metrics.jsonl`-shaped histories with high percentage variance and mixed `process_version` eras, *When* `calculate_spc_limits()` runs, *Then* percentage `lcl`/`ucl` satisfy `0.0 <= lcl <= ucl <= 100.0` (binomial p-chart / clamped bounds) and limits reflect only records matching the active `process_version`.
  2. *Truthful Human Acceptance vs. Completion (`TST-PRO-122.2`, tools/test_sprint_metrics.py)*:
     • *Given* mocked sprint issues in `Erledigt`, `Final Review (Human)`, `Final Review (Agent)`, and `In Progress`, *When* `parse_sprint_gate_metrics()` executes, *Then* `human_acceptance_rate_percent` counts only terminal human-accepted tickets (`Erledigt`/`Done`/`Closed`) and differs from `completion_rate_percent` when tickets await final review.
  3. *No Fabricated `p = 0.02` Escape Floor (`TST-PRO-122.3`, tools/test_sprint_metrics.py)*:
     • *Given* sprint gates with `0` upstream revisions, *When* `parse_sprint_gate_metrics()` computes Swiss Cheese escape fields, *Then* no `0.02` synthetic floor is substituted and `theoretical_cumulative_escape_prob` does not report fabricated `10^-8`–`10^-11` probabilities.
  4. *Unmasked Gate 1–5 Plausibility Warning (`TST-PRO-122.4`, tools/test_sprint_metrics.py)*:
     • *Given* $\ge 20$ Gate 1–5 subtasks with `100.0%` Gate 1–5 FTPR (`0` Gate 1–5 revisions) alongside non-zero Gate 6 `revisions` and `bounces`, *When* `parse_sprint_gate_metrics()` runs, *Then* `plausibility_warnings` contains the `PA 4.1 Plausibility Warning`.
  5. *Höchstmaß Mirror Parity (`TST-PRO-122.5`, tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
     • *Given* equivalent test fixtures run against `tools/hoechstmass/tools/sprint_metrics.py`, *When* `calculate_spc_limits()` and `parse_sprint_gate_metrics()` execute, *Then* all `[0, 100]` SPC bounds, `process_version` resets, acceptance separation, zero-floor removal, and Gate 1–5 plausibility warnings match `tools/sprint_metrics.py`.
  6. *CL3–CL5 Suspension & Matrix Contract Regression (`TST-PRO-122.6`, HoechstmassCoreFrameworkContractTest.kt & verify_requirement_governance.py)*:
     • *Given* updated `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, `tools/hoechstmass/README.md`, and `sprint_metrics.py` docstrings, *When* `HoechstmassCoreFrameworkContractTest` and `python3 tools/verify_requirement_governance.py` execute, *Then* CL3–CL5 claims are explicitly `[Target / Not Yet Claimed]`, all `"Level 1..5"` / `"PA 1.1..5.2"` contract assertions pass, and governance exits `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-123: **CI Custom Safety Guards, Baseline Ratcheting & Java Analysis Evaluation Suite (ATT-8169)** [Verified]
- **Requirement(s)**: `REQ-PRO-123`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, `tools/lint_safe_enum.py`, `tools/check_static_analysis_baselines.py`, `tools/test_check_static_analysis_baselines.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `docs/management/qa_plan.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-8169`
- **Test Description**:
  1. *Custom Python Safety Guards Execution & CI Wiring (`TST-PRO-123.1`, tools/lint_*.py & workflows)*:
     • *Given* `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, and `tools/lint_safe_enum.py`, *When* executed on `HEAD` and inspected in `.github/workflows/android-branch-build.yml` + `android-release.yml`, *Then* all three exit `0` on `HEAD`, run prior to `:app:assembleDebug` / `:app:assembleRelease`, and fail CI on any non-zero violation exit.
  2. *Base-Ref Baseline Ratcheting & Sprint Metrics Parity (`TST-PRO-123.2`, tools/test_*.py, tools/test_sprint_metrics.py & test_hoechstmass_tools.py)*:
     • *Given* Detekt (`9,516` `<ID>`) and Lint (`238` `<issue>`) baseline XML fixtures compared against base ref / ceilings, *When* the ratchet helper and `sprint_metrics.py` (+ mirror) execute, *Then* equal or reduced baselines pass, any increase fails non-zero, and `sprint_metrics.py` + `tools/hoechstmass/tools/sprint_metrics.py` report current baseline counts and deltas.
  3. *CI & Lint Contract Test Regression (`TST-PRO-123.3`, ReleaseCiAndLintConfigContractTest.kt)*:
     • *Given* `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `app/detekt-baseline.xml`, and `app/lint-baseline.xml`, *When* `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"` executes, *Then* all existing pinned assertions plus new CI safety-linter and ratchet step assertions pass.
  4. *Java Static Analysis Evaluation & Governance Parity (`TST-PRO-123.4`, verify_requirement_governance.py)*:
     • *Given* the documented PMD vs. SpotBugs vs. Error Prone evaluation (referencing `ADR-001` for `120` `.java` files on Gradle `9.6.0` / AGP `9.4.0` / JDK `21`) and Detekt/Lint burn-down schedule, *When* `python3 tools/verify_requirement_governance.py` runs, *Then* all governance checks (headers, attributes, traceability, architecture lock) exit `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-124: **Secret Hygiene, Local Credential Permissions & CI Secret-Pattern Scan Suite (ATT-8170)** [Verified]
- **Requirement(s)**: `REQ-PRO-124`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/check_secrets_hygiene.py`, `tools/test_check_secrets_hygiene.py`, `.gitignore`, `docs/ci-secrets.md`, `docs/engineering/walkthroughs/ATT-4004_walkthrough.md`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-8170`
- **Test Description**:
  1. *Local Credential File `0600` Permission Enforcement (`TST-PRO-124.1`, tools/test_check_secrets_hygiene.py & local workspace)*:
     • *Given* isolated temporary credential fixtures (`.env.claude`, `.env.gemini`, `secrets.properties`, `release.properties`, `.env.jira*`) with `0644`/`0664` modes and live workstation credential files, *When* `tools/check_secrets_hygiene.py --check-local-perms` and `--fix-local-perms` execute, *Then* permissive files fail with non-zero exit code prior to remediation and exit `0` once tightened to `0600`, matching `docs/ci-secrets.md`.
  2. *Git Ignore & Tracked Leak Redaction (`TST-PRO-124.2`, .gitignore & ATT-4004_walkthrough.md)*:
     • *Given* updated `.gitignore` and `docs/engineering/walkthroughs/ATT-4004_walkthrough.md`, *When* inspected via `git check-ignore .jira_audit.log` and `tools/check_secrets_hygiene.py`, *Then* `.jira_audit.log` is ignored, `ATT-4004_walkthrough.md:L78-L81` contains redacted placeholders instead of real `BuildConfig.java` credentials, and an explicit PO key-rotation notice is logged.
  3. *Deterministic Offline Secret-Pattern CI Scan & Allowlist (`TST-PRO-124.3`, tools/test_check_secrets_hygiene.py)*:
     • *Given* offline `tempfile` fixtures containing synthetic real-looking credentials (`AIza...`, `AKIA...`, `ghp_...`, `ATATT3x...`, PEM private keys, non-placeholder `STRAVA_CLIENT_SECRET`) vs. allowlisted fixtures (`secrets.defaults.properties`, `app/build.gradle`, `ATT-4004_plan.md`, `StravaHelperTest.kt`, `tools/test_jira_accounts.py`), *When* `tools/check_secrets_hygiene.py` runs across both fixtures and live `git ls-files`, *Then* synthetic real secrets fail with exit code `1` while allowlisted placeholders/test fixtures and clean tracked files exit `0` (`Rule 5`: zero live API calls).
  4. *CI Workflow Contract & Governance Regression (`TST-PRO-124.4`, ReleaseCiAndLintConfigContractTest.kt & verify_requirement_governance.py)*:
     • *Given* `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml`, *When* `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"` (`REQ-PRO-049.1`: zero `app/src/main/` reads) and `python3 tools/verify_requirement_governance.py` execute, *Then* `check_secrets_hygiene.py` precedes assembly in both workflows, preserved pins (`REQ-SEC-042`, `REQ-STP-008`, `REQ-PRO-123`) pass, and governance exits `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-125: **Post-Stage-6 Atomic Sprint Integration, Branch-Diff Audit & BOUNCE Subtask Reuse Suite (ATT-8207)** [Verified]
- **Requirement(s)**: `REQ-PRO-125`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `.agents/rules/aspice_governance.md`, `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, `docs/management/cm_plan.md`
- **Jira**: `ATT-8207`
- **Test Description**:
  1. *Post-Stage-6 Atomic Squash-Merge & Rebase Under `flock` (`TST-PRO-125.1`, tools/test_flock_serialization.py)*:
     • *Given* an isolated temporary multi-worktree Git repository with `sprint/<SPRINT>` and `feature/<KEY>` (including concurrent sibling commits + `docs/requirements.md` / `docs/tests.md` index updates landed on `sprint/<SPRINT>` while `<KEY>` waited in Stage 6), *When* `parallel_sprint_lock.py --merge-ticket <KEY>` executes post-`PASS`, *Then* `feature/<KEY>` rebases cleanly without conflict, `--sync-indexes` reconciles counters, `sprint/<SPRINT>` receives strictly one squashed atomic commit with `REQ-PRO-108.3` trailers, and `feature/<KEY>` is deleted.
  2. *Zero-Build / Zero-Lock Stage 6 Branch-Diff Audit (`TST-PRO-125.2`, skills/rules/cm_plan review & grep)*:
     • *Given* updated `.agents/rules/aspice_governance.md` (`Rule 3.3`, `Rule 8.2`), `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, and `docs/management/cm_plan.md`, *When* reviewed, *Then* Gate 5 `PASS` keeps `feature/<KEY>` intact, Stage 6 audits `git diff sprint/<SPRINT_NAME>...feature/<KEY>` strictly read-only (zero `./gradlew` and zero `/tmp/att_build_merge.lock` calls during review), and `--merge-ticket` triggers strictly post-`PASS`.
  3. *Subtask Reuse & Reopening on `final-review-verdict BOUNCE` (`TST-PRO-125.3`, tools/test_jira_accounts.py)*:
     • *Given* mocked Jira REST state (`Rule 5`: zero live API calls) for a parent ticket in `Final Review (Agent)` with `Erledigt` `[Implementation]` and `[Test]` subtasks, *When* `final_review_verdict(..., "BOUNCE", ...)` executes across 1–2 bounces followed by `submit_stage(..., 4)` and `submit_stage(..., 5)`, *Then* existing `[Implementation]` and `[Test]` subtasks are reopened and reused with zero duplicate subtask creation, and `REQ-PRO-107.1` separated-authorship validation continues to hold across healing cycles.
  4. *`tools/hoechstmass/` Mirror Parity & Governance Verification (`TST-PRO-125.4`, tools/hoechstmass/tools/test_hoechstmass_tools.py & verify_requirement_governance.py)*:
     • *Given* updated `tools/hoechstmass/` tooling and skill mirrors, *When* `python3 -m unittest discover -s tools -p "test_*.py"`, `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"`, and `python3 tools/verify_requirement_governance.py` execute, *Then* all unit tests, `HoechstmassToolingContractTest` decoupling invariants, and governance checks exit `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-126: **SensorTypeUnitsContractTest Source-Reading Elimination & Governance Javadoc Verification Suite (ATT-8631)** [Verified]
- **Requirement(s)**: `REQ-PRO-126`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-8631`
- **Test Description**:
  1. *`SourceTextReadingAuditContractTest` `4/4` & `LEGITIMATE_GOVERNANCE_GUARDS` Integrity (`TST-PRO-126.1`, SourceTextReadingAuditContractTest.kt)*:
     • *Given* updated `SensorTypeUnitsContractTest.kt` containing zero `app/src/main/` references and zero read invocations (`readText`/`readLines`/`readString`/`useLines`/`bufferedReader`), *When* `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest` executes, *Then* all `4/4` tests pass (`0` unallowlisted readers, `LEGITIMATE_GOVERNANCE_GUARDS.size == 22`, `baseline.size == 105`, `0` stale entries).
  2. *`SensorTypeUnitsContractTest` JVM Reflection & Architecture Parity (`TST-PRO-126.2`, SensorTypeUnitsContractTest.kt)*:
     • *Given* `SensorType.values()` via JVM reflection, `docs/architecture/sensor_units.md`, and `docs/architecture.md`, *When* `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest` executes, *Then* `allSensorTypes_haveValidInternalUnit()` and `sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt()` pass `2/2` (`0` failures, `0` skipped).
  3. *Offline `verify_sensor_type_javadocs()` Python Unit Tests (`TST-PRO-126.3`, tools/test_verify_requirement_governance.py)*:
     • *Given* isolated `tempfile` fixtures containing synthetic `SensorType.java` enums (valid Javadoc + `"Internal unit:"` + matching unit, missing Javadoc block, missing `"Internal unit:"`, and mismatched unit vs constructor literal/`sensor_units.md`), *When* `python3 -m unittest tools/test_verify_requirement_governance.py` runs, *Then* all positive and negative unit tests pass offline (`Rule 5`).
  4. *Full Governance Gate Verification (`TST-PRO-126.4`, tools/verify_requirement_governance.py)*:
     • *Given* synchronized living documentation shards, master indexes, and live `SensorType.java`, *When* `python3 tools/verify_requirement_governance.py` and `--check-doc-headers --check-commit-hygiene --check-conflict-markers` execute, *Then* all governance checks including `verify_sensor_type_javadocs()` exit `0`. (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-127: **Symmetric REQ-TST Edge Parity, Tag Scanner Audit & Oversized Shard Splitting Suite (ATT-8206)** [Verified]
- **Requirement(s)**: `REQ-PRO-127`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/shard_living_docs.py`, `tools/test_verify_requirement_governance.py`, `tools/test_shard_living_docs.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`
- **Jira**: `ATT-8206`
- **Test Description**:
  1. *Symmetric `REQ <-> TST` Edge Parity & Baseline Ratchet (`TST-PRO-127.1`, `tools/test_verify_requirement_governance.py`)*:
     • *Given* `tempfile` shard fixtures with symmetric `REQ<->TST` citations vs asymmetric `REQ->TST` or `TST->REQ` links outside `traceability_baseline.json`, *When* `find_traceability_orphans()` / `verify_traceability_orphans()` execute, *Then* symmetric fixtures pass (`15` baseline orphans tolerated without regression) and asymmetric fixtures fail with deterministic mismatch diagnostics; live `--coverage` reports `0` Cat-4 gaps.
  2. *`androidTest/` & `tools/test_*.py` Tag Scanner Verification (`TST-PRO-127.2`, `tools/test_verify_requirement_governance.py`)*:
     • *Given* `tempfile` test trees under `app/src/androidTest/` and `tools/test_*.py`, *When* `extract_test_code_tags()` runs, *Then* valid `TST-*` tags across all 3 test roots are extracted under `L2954-2957` allowlist rules and `5` Cat-3 tags are audited cleanly.
  3. *Oversized Shard Split, Header Parity & Kotlin Contract Suite (`TST-PRO-127.3`, `tools/test_shard_living_docs.py` & Gradle contract tests)*:
     • *Given* all `5` split living-doc shards (`<= 4,000` lines each), synced `docs/requirements.md` / `docs/tests.md` (`RAD`, `HRV`, `WKO`, `CLM` prefixes), and updated `*_process_stability*.md` lookups, *When* `python3 -m unittest tools/test_shard_living_docs.py`, `python3 tools/shard_living_docs.py --check-size --strict`, and `./gradlew :app:testDebugUnitTest --tests ...AspiceSweAuditContractTest --tests ...NonFunctionalRequirementsContractTest --tests ...SystemRequirementsSpecificationContractTest --tests ...SystemArchitectureDesignContractTest` run, *Then* every test passes `100%` (`0` lost IDs, valid `DOC-*` + `Change History` on every sub-shard).
  4. *CI Workflow `--check-size --strict` & Governance Gate (`TST-PRO-127.4`, `tools/verify_requirement_governance.py` & `.github/workflows/`)*:
     • *Given* `.github/workflows/android-branch-build.yml` and `android-release.yml` wiring `shard_living_docs.py --check-size --strict` and `verify_requirement_governance.py --check-size`, *When* `ReleaseCiAndLintConfigContractTest` and `verify_requirement_governance.py` execute, *Then* all governance and CI config checks exit `0` (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-128: **Verdict Parsing, End-to-End FTPR, Risk & Bug Status Parity, and Stage Subtask Closure Guard Suite (ATT-8205)** [Verified]
- **Requirement(s)**: `REQ-PRO-128`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-8205`
- **Test Description**:
  1. *Extended Verdict Synonyms, ADF Extraction & Zero-Comment `unverified` Parity (`TST-PRO-128.1`, `tools/test_sprint_metrics.py` & `tools/test_jira_accounts.py`)*:
     • *Given* plain-text and Jira v3 ADF subtask comments containing `APPROVED`/`FREIGABE`/`REWORK`/`FAIL` plus zero-comment `Erledigt` subtasks and untagged comments, *When* `parse_verdicts_from_comments()` and `extract_sprint_gate_records()` execute, *Then* synonym verdicts map to `PASS`/`REVISION`, untagged non-reviewer comments are not defaulted to `agent2`, and zero-comment subtasks record `unverified` without fabricated `PASS` entries. Tag test docstrings with `TST-PRO-128` / `TST-PRO-128.1` for `verify_requirement_governance.py --check-verified-evidence`.
  2. *Additive `end_to_end_ftpr_percent` & Gate 1–5 `overall_ftpr_percent` Preservation (`TST-PRO-128.2`, `tools/test_sprint_metrics.py`)*:
     • *Given* sprint fixtures with `>= 20` 100%-FTPR Gate 1–5 subtasks and Stage 6 bounces/revisions, *When* `parse_sprint_gate_metrics()` and `format_metrics_markdown()` execute, *Then* `overall_ftpr_percent` remains 100.0% (emitting `PA 4.1 Plausibility Warning`) while `end_to_end_ftpr_percent` includes Gate 6 (`< 100.0%`). Tag tests with `TST-PRO-128.2`.
  3. *Multi-Signal Risk Distribution (`TST-PRO-128.3`, `tools/test_sprint_metrics.py`)*:
     • *Given* issues with `severity-*` labels, `[Highest]`/`[High]`/`[Low]` summary tags, Stage 1 `Risk Rating`, and `Medium` priority fallback, *When* `parse_sprint_gate_metrics()` runs, *Then* `risk_distribution` classifies each bucket accurately instead of flattening to `100% Medium`. Tag tests with `TST-PRO-128.3`.
  4. *Extended German/English Bug Statuses & Höchstmaß Parity (`TST-PRO-128.4`, `tools/test_sprint_metrics.py` & `tools/hoechstmass/tools/test_hoechstmass_tools.py`)*:
     • *Given* bugs in `abgelehnt`, `rejected`, `geschlossen`, `resolved`, and `Final Review (Human)` statuses, *When* `parse_sprint_gate_metrics()` runs in root and `tools/hoechstmass/tools/sprint_metrics.py`, *Then* `closed_bugs`, `fixed_awaiting_human`, and `resolution_rate_percent` match symmetrically. Tag tests with `TST-PRO-128.4`.
  5. *Zero-Comment Stage Subtask Closure Guard (`TST-PRO-128.5`, `tools/test_jira_accounts.py`)*:
     • *Given* mocked `transition_issue(..., "freigabe")` calls (`Rule 5`) for a `[Analysis]`..`[Test]` subtask without an `agent2` `PASS` comment vs a summary-less subtask (`ATT-9999` at `:312`), *When* executed, *Then* stage subtasks exit non-zero while summary-less subtasks succeed (`Localization Parity: N/A - no UI strings`). Tag tests with `TST-PRO-128.5`.

---

### TST-PRO-129: **ADR-001 Reconciliation, God-Class LoC Ratchet, WorkoutRepository SWE.3 Design & Living-Doc Layout Enforcement Suite (ATT-8203)** [Verified]
- **Requirement(s)**: `REQ-PRO-129`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/architecture/JavaCoreMigrationStrategyContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `docs/architecture/adr_001_java_core_services_migration.md`, `docs/architecture.md`, `docs/architecture/detailed_design/workout_repository.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `tools/shard_living_docs.py`
- **Jira**: `ATT-8203`
- **Test Description**:
  1. *ADR-001 `Keep`-vs-`Migrate` Consistency & `120` `.java` Count Verification (`TST-PRO-129.1`, `JavaCoreMigrationStrategyContractTest.kt`)*:
     • *Given* updated `docs/architecture/adr_001_java_core_services_migration.md` (§§1–8, `120` remaining `.java` `<= 149`, `29` migrated `.kt` with `ATT-7903`/`7905`/`7906` `Completed`, `V5.0.0`, `Status: Reviewed`) and `docs/architecture.md` (§5.3, §5.5), *When* `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest` executes, *Then* all decision-consistency assertions (`Keep`-vs-`Migrate` across §§2–8, `120` `.java` count reconciliation, `3` child tickets) pass `100%`. Tag test with `TST-PRO-129.1`.
  2. *God-Class `> 1,500` LoC Ratchet & `WorkoutRepository` SWE.3 Detailed Design Parity (`TST-PRO-129.2`, `ArchitectureConformanceContractTest.kt` & `DetailedDesignParityContractTest.kt`)*:
     • *Given* the `16` baselined `> 1,500` LoC production files (`WorkoutRepository.kt` `5,117` .. `ImportBackupTabsScreen.kt` `1,513`) and `docs/architecture/detailed_design/workout_repository.md` linked in `docs/architecture.md` §3.5 (`4` detailed-design docs), *When* `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `SourceTextReadingAuditContractTest`, and `AspiceSweAuditContractTest` execute, *Then* LoC ceiling ratchet checks (rejecting net-new `> 1,500` LoC files, upward growth, or stale lowered entries), `WorkoutRepository` Mermaid/JVM-reflection parity, `22` `LEGITIMATE_GOVERNANCE_GUARDS`, and `105` `app/source-reading-baseline.txt` all pass `100%`. Tag tests with `TST-PRO-129.2`.
  3. *Fail-Closed Requirements/Tests Directory Layout Enforcement (`TST-PRO-129.3`, `tools/test_verify_requirement_governance.py`)*:
     • *Given* isolated `tempfile` fixtures containing valid `req_*.md`/`tst_*.md` shards vs misplaced `SCRUM-123_plan.md` / non-`req_*.md` in `docs/requirements/` or non-`tst_*.md` in `docs/tests/`, plus the live repository, *When* `python3 -m unittest tools/test_verify_requirement_governance.py` runs (`Rule 5`), *Then* misplaced files fail closed deterministically while the live repository passes `0` misplaced files. Tag test docstrings with `TST-PRO-129.3`.
  4. *Living Doc Shard Split & Full Governance Parity (`TST-PRO-129.4`, `tools/shard_living_docs.py` & `tools/verify_requirement_governance.py`)*:
     • *Given* `docs/tests/tst_process_stability_part{1,2}.md` and synchronized `docs/requirements.md` / `docs/tests.md`, *When* `python3 tools/shard_living_docs.py --check-size --strict` and `python3 tools/verify_requirement_governance.py --repo-root .` execute, *Then* every shard is `<= 4,000` lines and governance exits `0` (`Localization Parity: N/A - no UI strings`).

---

### TST-PRO-130: **SWE.2 Heap/Cache/Map Budgets, Bounded HRV Threshold Cache, Subsystem Parity & IF-SWE-05 Upward-Import Ratchet Suite (ATT-8202)** [Verified]
- **Requirement(s)**: `REQ-PRO-130`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/architecture.md`, `docs/architecture/interfaces.md`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/ClusterWorkoutDataAdapter.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/ClusterWorkoutDataPort.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/InterfaceSignatureDriftContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryHrvConsensusTest.kt`
- **Jira**: `ATT-8202`
- **Test Description**:
  1. *SWE.2 Heap, Cache, Vertex & GoogleMap Budgets + Bounded `recentHrvThresholdsCache` (`TST-PRO-130.1`, `WorkoutRepository*Test` & Contract Tests)*:
     • *Given* `docs/architecture.md` §4.9 documenting quantitative bounds and lifecycle cleanup for `WorkoutRepository` (`MAX_OVERLAY_POINTS = 800`, lazy track loading, bounded `recentHrvThresholdsCache`), `PeriodsRepository`/`PeriodMapUtils` (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`, `<= 5` spatial anchors, `recentlyFinishedWorkoutIds <= 50`, `LruCache(64)`, `2_000`/`8_000` vertices, empty `workoutIdToPathMap`/`workoutIdToAllPolylinesMap`), `RadarStateRepository` (`<= 4` `RadarSource`), `WorkoutClusterRepository` (`extraBufferCapacity = 16`), and `GoogleMap` (`liteMode(true)` thumbnails + `beyondViewportPageCount = 0` GL isolation), *When* behavioral unit tests (`WorkoutRepository*Test`) insert beyond the cache cap or delete/refresh workouts and `ArchitectureConformanceContractTest`, `NonFunctionalRequirementsContractTest`, and `SystemArchitectureDesignContractTest` run, *Then* `recentHrvThresholdsCache` evicts deterministically without OOM, `WorkoutRepository.kt <= 5,117` LoC holds, and all §4.9 / `REQ-NFR-003`..`014` contract assertions pass `100%`. Tag tests with `TST-PRO-130.1`.
  2. *Swim/RadarLight/TPMS/Polar PFTP Coverage & Truthful `IF-SWE-05` Parity (`TST-PRO-130.2`, `InterfaceSignatureDriftContractTest.kt` & `AspiceSweAuditContractTest.kt`)*:
     • *Given* `docs/architecture.md` (§2, §5.1) and `docs/architecture/interfaces.md` documenting Swim, RadarLight, TPMS, Polar PFTP, and truthful `IF-SWE-05` boundaries (distinguishing `Context`/SQLite-backed `WorkoutClusterEngine` `<= 1,635` LoC from pure-JVM calculators), *When* `InterfaceSignatureDriftContractTest` and `AspiceSweAuditContractTest` execute, *Then* `100%` of documented signatures match compiled bytecode via JVM reflection with zero `src/main/` text reads. Tag tests with `TST-PRO-130.2`.
  3. *Full `.ui.*` Database Upward-Import Ratchet & Tripwire (`TST-PRO-130.3`, `ArchitectureConformanceContractTest.kt`)*:
     • *Given* `ArchitectureConformanceContractTest` scanning `trainingtracker/database/` and `banalservice/database/` using `\bcom\.atrainingtracker\..*\.ui\b` against the ratcheted legacy baseline (excluding decoupled `WorkoutClusterEngine -> WorkoutRepository`), *When* executed against the live tree and a negative tripwire importing `com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository`, *Then* the live tree passes with `0` unallowlisted upward imports, `0` stale baseline entries, `banalservice/database/` == `0`, and the tripwire detects `ui.aftermath` deterministically. Tag tests with `TST-PRO-130.3`.
  4. *Monolith Ceiling, Governance & Shard-Size Invariants (`TST-PRO-130.4`, `SourceTextReadingAuditContractTest` & `verify_requirement_governance.py`)*:
     • *Given* `WorkoutRepository.kt <= 5,117` LoC, `WorkoutClusterEngine.kt <= 1,635` LoC, `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `app/source-reading-baseline.txt == 105`, and all `28` living-doc shards `<= 4,000` lines, *When* `SourceTextReadingAuditContractTest`, `shard_living_docs.py --check-size --strict`, and `verify_requirement_governance.py` execute, *Then* all invariants pass and governance exits `0` (`Localization Parity: N/A - no UI strings`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Split oversized `tst_process_stability.md` into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`, `REQ-PRO-129.4`). |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Added `[Planned]` `TST-PRO-129` test specifications (`ATT-8203_test_spec.md`). |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Promoted `TST-PRO-129` to `[Implemented]` in Stage 4 (`JavaCoreMigrationStrategyContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `tools/test_verify_requirement_governance.py`). |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8203`) | `ATT-8203` | Promoted `TST-PRO-129` to `[Verified]` after Stage 5 verification (`35/35` Gradle contract tests across 6 suites, `TestLivingDocDirectoryLayout` `4/4` PASS, `28/28` shards `<= 4,000` lines, `TST-PRO-129` evidence `True`, governance `exit 0`). |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Added `[Planned]` `TST-PRO-130` test specifications (`ATT-8202_test_spec.md`: SWE.2 heap/cache/map budgets & bounded `recentHrvThresholdsCache`, Swim/RadarLight/TPMS/Polar PFTP & `IF-SWE-05` parity, full `.ui.*` upward-import ratchet, monolith & governance invariants). |
| `1.0.5` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Promoted `TST-PRO-130` to `[Implemented]` in Stage 4 (`WorkoutRepositoryHrvConsensusTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `WorkoutCluster*Test`). |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8202`) | `ATT-8202` | Promoted `TST-PRO-130` to `[Verified]` after Stage 5 verification (`47/47` targeted contract & component XML suites `242/242` PASS; `DeadCodeEliminationContractTest` `4/5` with pre-existing `EditDeviceDialog.kt` `internal @Preview`; `:app:lintDebug` `0` errors). |
