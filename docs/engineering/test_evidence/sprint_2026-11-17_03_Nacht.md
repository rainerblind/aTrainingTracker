# Test & Coverage Evidence: sprint_2026-11-17_03_Nacht

- **Target**: `sprint_2026-11-17_03_Nacht` (Type: `sprint`)
- **Git Commit**: `a3508e6b2d23930c1895131f2b11519c55cd4ffb`
- **Timestamp**: `2026-11-18T04:36:47.237713+00:00`
- **Overall Test Result**: **PASS** (0 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 633
- **Passed**: 633
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 385.07s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 154

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 4 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-CON-012` | **PASS** | 2 | 0 |
| `TST-CON-022` | **PASS** | 7 | 0 |
| `TST-CON-031` | **PASS** | 7 | 0 |
| `TST-CON-045` | **PASS** | 14 | 0 |
| `TST-CON-048` | **PASS** | 4 | 0 |
| `TST-FIL-004` | **PASS** | 23 | 0 |
| `TST-FIL-016` | **PASS** | 13 | 0 |
| `TST-FIL-017` | **PASS** | 16 | 0 |
| `TST-FIL-019` | **PASS** | 14 | 0 |
| `TST-FIL-021` | **PASS** | 7 | 0 |
| `TST-FIL-022` | **PASS** | 13 | 0 |
| `TST-FIL-023` | **PASS** | 28 | 0 |
| `TST-FIL-024` | **PASS** | 23 | 0 |
| `TST-FIL-025` | **PASS** | 29 | 0 |
| `TST-FUSION-001` | **PASS** | 23 | 0 |
| `TST-FUSION-002` | **PASS** | 3 | 0 |
| `TST-FUSION-003` | **PASS** | 6 | 0 |
| `TST-INT-009` | **PASS** | 17 | 0 |
| `TST-INT-010` | **PASS** | 16 | 0 |
| `TST-INT-011` | **PASS** | 16 | 0 |
| `TST-INT-012` | **PASS** | 18 | 0 |
| `TST-INT-013` | **PASS** | 16 | 0 |
| `TST-INT-014` | **PASS** | 16 | 0 |
| `TST-INT-015` | **PASS** | 16 | 0 |
| `TST-INT-016` | **PASS** | 16 | 0 |
| `TST-LOC-001` | **PASS** | 10 | 0 |
| `TST-MAP-028` | **PASS** | 1 | 0 |
| `TST-MAP-059` | **PASS** | 5 | 0 |
| `TST-MIG-023` | **PASS** | 3 | 0 |
| `TST-MIG-024` | **PASS** | 3 | 0 |
| `TST-NFR-001` | **PASS** | 11 | 0 |
| `TST-NFR-002` | **PASS** | 11 | 0 |
| `TST-NFR-003` | **PASS** | 11 | 0 |
| `TST-NFR-005` | **PASS** | 11 | 0 |
| `TST-NFR-007` | **PASS** | 11 | 0 |
| `TST-NFR-008` | **PASS** | 11 | 0 |
| `TST-NFR-009` | **PASS** | 11 | 0 |
| `TST-NFR-010` | **PASS** | 11 | 0 |
| `TST-NFR-011` | **PASS** | 11 | 0 |
| `TST-NFR-013` | **PASS** | 11 | 0 |
| `TST-NFR-014` | **PASS** | 11 | 0 |
| `TST-PER-040` | **PASS** | 11 | 0 |
| `TST-PER-045` | **PASS** | 18 | 0 |
| `TST-PRI-003` | **PASS** | 3 | 0 |
| `TST-PRO-033` | **PASS** | 8 | 0 |

*(Remaining 104 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: 11/11 passed (0 failed)

| REQ ID | TST ID | Metric | Measured Value | Budget | Verdict |
| :--- | :--- | :--- | :---: | :---: | :---: |
| `REQ-NFR-001` | `TST-NFR-001` | `ekf_cycle_p95_ms` | `0.3872 ms` | `<= 2.0 ms` | **PASS** |
| `REQ-NFR-002` | `TST-NFR-002` | `stationary_drift_m_per_min` | `0.0000 m/min` | `< 0.5 m/min` | **PASS** |
| `REQ-NFR-003` | `TST-NFR-003` | `tick_p99_ms` | `15.2401 ms` | `< 50.0 ms` | **PASS** |
| `REQ-NFR-005` | `TST-NFR-005` | `retained_heap_delta_mb` | `0.2093 MB` | `< 40.0 MB (total <= 150 MB)` | **PASS** |
| `REQ-NFR-007` | `TST-NFR-007` | `dispatch_p99_ms` | `0.0005 ms` | `< 25.0 ms` | **PASS** |
| `REQ-NFR-008` | `TST-NFR-008` | `watchdog_failover_ms` | `3500.0000 ms` | `<= 3500 ms` | **PASS** |
| `REQ-NFR-009` | `TST-NFR-009` | `wakelock_release_latency_ms` | `125.1498 ms` | `0 leaks (immediate release)` | **PASS** |
| `REQ-NFR-010` | `TST-NFR-010` | `db_write_p99_ms` | `0.2629 ms` | `< 30.0 ms` | **PASS** |
| `REQ-NFR-011` | `TST-NFR-011` | `storage_growth_kb_per_h` | `260.0000 KB/h` | `<= 360.0 KB/h` | **PASS** |
| `REQ-NFR-013` | `TST-NFR-013` | `polyline_update_p95_ms` | `0.0047 ms` | `< 30.0 ms` | **PASS** |
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
