# Test & Coverage Evidence: sprint_464

- **Target**: `sprint_464` (Type: `sprint`)
- **Git Commit**: `8f587ea585290402e189f7e73948332f5ffa1b07`
- **Timestamp**: `2026-11-20T04:19:35.120605+00:00`
- **Overall Test Result**: **PASS** (0 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 7
- **Passed**: 7
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 17.89s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 4

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-BUG-002` | **PASS** | 8 | 0 |
| `TST-PER-026` | **PASS** | 7 | 0 |
| `TST-PERF-004` | **PASS** | 8 | 0 |
| `TST-PERF-005` | **PASS** | 7 | 0 |

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: 11/11 passed (0 failed)

| REQ ID | TST ID | Metric | Measured Value | Budget | Verdict |
| :--- | :--- | :--- | :---: | :---: | :---: |
| `REQ-NFR-001` | `TST-NFR-001` | `ekf_cycle_p95_ms` | `0.5484 ms` | `<= 2.0 ms` | **PASS** |
| `REQ-NFR-002` | `TST-NFR-002` | `stationary_drift_m_per_min` | `0.0000 m/min` | `< 0.5 m/min` | **PASS** |
| `REQ-NFR-003` | `TST-NFR-003` | `tick_p99_ms` | `14.0372 ms` | `< 50.0 ms` | **PASS** |
| `REQ-NFR-005` | `TST-NFR-005` | `retained_heap_delta_mb` | `0.1739 MB` | `< 40.0 MB (total <= 150 MB)` | **PASS** |
| `REQ-NFR-007` | `TST-NFR-007` | `dispatch_p99_ms` | `0.0007 ms` | `< 25.0 ms` | **PASS** |
| `REQ-NFR-008` | `TST-NFR-008` | `watchdog_failover_ms` | `3500.0000 ms` | `<= 3500 ms` | **PASS** |
| `REQ-NFR-009` | `TST-NFR-009` | `wakelock_release_latency_ms` | `36.1398 ms` | `0 leaks (immediate release)` | **PASS** |
| `REQ-NFR-010` | `TST-NFR-010` | `db_write_p99_ms` | `0.3064 ms` | `< 30.0 ms` | **PASS** |
| `REQ-NFR-011` | `TST-NFR-011` | `storage_growth_kb_per_h` | `260.0000 KB/h` | `<= 360.0 KB/h` | **PASS** |
| `REQ-NFR-013` | `TST-NFR-013` | `polyline_update_p95_ms` | `0.0055 ms` | `< 30.0 ms` | **PASS** |
| `REQ-NFR-014` | `TST-NFR-014` | `emissions_in_5s` | `5.0000 emissions` | `<= 5 emissions / 5s (<= 1.0 Hz)` | **PASS** |

### Group B — On-Device Manual Protocols (`[MANUAL PROTOCOL — NOT EXECUTED IN JVM]`)
| REQ ID | TST ID | Budget | Method | Device / Build | Measured | Verdict |
| :--- | :--- | :---: | :--- | :--- | :---: | :---: |
| `REQ-NFR-004` | `TST-NFR-004` | `< 5.0% single-core` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
| `REQ-NFR-006` | `TST-NFR-006` | `<= 5.0%/h bg / <= 12.0%/h fg` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
| `REQ-NFR-012` | `TST-NFR-012` | `< 16.6 ms p95 (60 FPS)` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
| `REQ-NFR-015` | `TST-NFR-015` | `TTID <= 1200 ms / TTFD <= 1500 ms` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
| `REQ-NFR-016` | `TST-NFR-016` | `p95 <= 5.0 m / RMSE <= 3.0 m` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
| `REQ-NFR-017` | `TST-NFR-017` | `>= 99.5% prod / >= 99.0% staging` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
| `REQ-NFR-018` | `TST-NFR-018` | `<= 0.05% ANR / < 100 ms main thread` | On-Device Manual | `<TBD>` | `<NOT MEASURED>` | **PENDING MANUAL** |
