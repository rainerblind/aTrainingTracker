# Test & Coverage Evidence: sprint_462

- **Target**: `sprint_462` (Type: `sprint`)
- **Git Commit**: `50a57b3e76951ac1a8076bf523c717576254b643`
- **Timestamp**: `2026-11-19T14:30:40.726392+00:00`
- **Overall Test Result**: **PASS** (0 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 687
- **Passed**: 687
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 771.49s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 199

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 4 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-ARC-005` | **PASS** | 9 | 0 |
| `TST-ARC-006` | **PASS** | 5 | 0 |
| `TST-ARC-008` | **PASS** | 5 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-CLM-008` | **PASS** | 20 | 0 |
| `TST-CLM-009` | **PASS** | 9 | 0 |
| `TST-CLM-010` | **PASS** | 5 | 0 |
| `TST-CLM-011` | **PASS** | 8 | 0 |
| `TST-CON-003` | **PASS** | 11 | 0 |
| `TST-CON-006` | **PASS** | 11 | 0 |
| `TST-CON-007` | **PASS** | 11 | 0 |
| `TST-CON-011` | **PASS** | 11 | 0 |
| `TST-CON-012` | **PASS** | 19 | 0 |
| `TST-CON-023` | **PASS** | 11 | 0 |
| `TST-CON-027` | **PASS** | 5 | 0 |
| `TST-CON-028` | **PASS** | 8 | 0 |
| `TST-CON-031` | **PASS** | 8 | 0 |
| `TST-CON-036` | **PASS** | 13 | 0 |
| `TST-CON-045` | **PASS** | 26 | 0 |
| `TST-CON-049` | **PASS** | 54 | 0 |
| `TST-CON-050` | **PASS** | 26 | 0 |
| `TST-CON-051` | **PASS** | 41 | 0 |
| `TST-CON-052` | **PASS** | 46 | 0 |
| `TST-DAT-014` | **PASS** | 1 | 0 |
| `TST-DAT-018` | **PASS** | 1 | 0 |
| `TST-FIL-002` | **PASS** | 9 | 0 |
| `TST-FIL-003` | **PASS** | 16 | 0 |
| `TST-FIL-018` | **PASS** | 14 | 0 |
| `TST-FIL-023` | **PASS** | 29 | 0 |
| `TST-FIL-027` | **PASS** | 29 | 0 |
| `TST-FIL-028` | **PASS** | 35 | 0 |
| `TST-FIL-029` | **PASS** | 47 | 0 |
| `TST-FIL-030` | **PASS** | 45 | 0 |
| `TST-INT-009` | **PASS** | 17 | 0 |
| `TST-INT-010` | **PASS** | 16 | 0 |
| `TST-INT-011` | **PASS** | 16 | 0 |
| `TST-INT-012` | **PASS** | 18 | 0 |
| `TST-INT-013` | **PASS** | 16 | 0 |
| `TST-INT-014` | **PASS** | 16 | 0 |
| `TST-INT-015` | **PASS** | 16 | 0 |
| `TST-INT-016` | **PASS** | 16 | 0 |
| `TST-LOC-001` | **PASS** | 10 | 0 |
| `TST-MAP-021` | **PASS** | 6 | 0 |
| `TST-MAP-022` | **PASS** | 1 | 0 |
| `TST-MAP-029` | **PASS** | 39 | 0 |
| `TST-MAP-047` | **PASS** | 8 | 0 |

*(Remaining 149 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: 11/11 passed (0 failed)

| REQ ID | TST ID | Metric | Measured Value | Budget | Verdict |
| :--- | :--- | :--- | :---: | :---: | :---: |
| `REQ-NFR-001` | `TST-NFR-001` | `ekf_cycle_p95_ms` | `0.7119 ms` | `<= 2.0 ms` | **PASS** |
| `REQ-NFR-002` | `TST-NFR-002` | `stationary_drift_m_per_min` | `0.0000 m/min` | `< 0.5 m/min` | **PASS** |
| `REQ-NFR-003` | `TST-NFR-003` | `tick_p99_ms` | `21.4127 ms` | `< 50.0 ms` | **PASS** |
| `REQ-NFR-005` | `TST-NFR-005` | `retained_heap_delta_mb` | `0.1128 MB` | `< 40.0 MB (total <= 150 MB)` | **PASS** |
| `REQ-NFR-007` | `TST-NFR-007` | `dispatch_p99_ms` | `0.0008 ms` | `< 25.0 ms` | **PASS** |
| `REQ-NFR-008` | `TST-NFR-008` | `watchdog_failover_ms` | `3500.0000 ms` | `<= 3500 ms` | **PASS** |
| `REQ-NFR-009` | `TST-NFR-009` | `wakelock_release_latency_ms` | `112.5505 ms` | `0 leaks (immediate release)` | **PASS** |
| `REQ-NFR-010` | `TST-NFR-010` | `db_write_p99_ms` | `0.3943 ms` | `< 30.0 ms` | **PASS** |
| `REQ-NFR-011` | `TST-NFR-011` | `storage_growth_kb_per_h` | `260.0000 KB/h` | `<= 360.0 KB/h` | **PASS** |
| `REQ-NFR-013` | `TST-NFR-013` | `polyline_update_p95_ms` | `0.0091 ms` | `< 30.0 ms` | **PASS** |
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
