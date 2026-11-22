# Test & Coverage Evidence: weekly_2026_KW47

- **Target**: `weekly_2026_KW47` (Type: `generic`)
- **Git Commit**: `a6f3c00036571d96406604e790adc0d47ee52f13`
- **Timestamp**: `2026-11-21T21:44:33.668745+00:00`
- **Overall Test Result**: **FAIL** (8 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 7,567
- **Passed**: 7,559
- **Failures**: 8
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 3973.95s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Measured
- **Global Line Coverage**: `86.38%`
- **Global Branch Coverage**: `64.96%`

### Critical Subsystem Coverage
| Subsystem | Line Coverage | Floor | Line Verdict | Branch Coverage | Floor | Branch Verdict |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Critical Tracking** | 88.72% | 55.0% | PASS | 70.42% | 45.0% | PASS |
| **Critical Filters** | 86.81% | 70.0% | PASS | 72.1% | 50.0% | PASS |
| **Critical Sensors & Protocols** | 85.93% | 60.0% | PASS | 63.83% | 50.0% | PASS |

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 1,207

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-001` | **PASS** | 1 | 0 |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 6 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-ARC-005` | **PASS** | 9 | 0 |
| `TST-ARC-006` | **PASS** | 5 | 0 |
| `TST-ARC-007` | **PASS** | 18 | 0 |
| `TST-ARC-008` | **PASS** | 20 | 0 |
| `TST-ARC-009` | **PASS** | 2 | 0 |
| `TST-ARC-010` | **PASS** | 16 | 0 |
| `TST-BLD-001` | **PASS** | 3 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-BUG-001` | **PASS** | 19 | 0 |
| `TST-BUG-002` | **PASS** | 8 | 0 |
| `TST-BUG-007` | **PASS** | 13 | 0 |
| `TST-BUG-008` | **PASS** | 4 | 0 |
| `TST-BUG-009` | **PASS** | 4 | 0 |
| `TST-BUG-010` | **PASS** | 4 | 0 |
| `TST-BUG-011` | **PASS** | 3 | 0 |
| `TST-CLM-008` | **PASS** | 20 | 0 |
| `TST-CLM-009` | **PASS** | 19 | 0 |
| `TST-CLM-010` | **PASS** | 9 | 0 |
| `TST-CLM-011` | **PASS** | 21 | 0 |
| `TST-CON-002` | **PASS** | 13 | 0 |
| `TST-CON-003` | **PASS** | 14 | 0 |
| `TST-CON-004` | **PASS** | 13 | 0 |
| `TST-CON-005` | **PASS** | 19 | 0 |
| `TST-CON-006` | **PASS** | 15 | 0 |
| `TST-CON-007` | **PASS** | 11 | 0 |
| `TST-CON-008` | **PASS** | 33 | 0 |
| `TST-CON-009` | **PASS** | 21 | 0 |
| `TST-CON-010` | **PASS** | 16 | 0 |
| `TST-CON-011` | **PASS** | 39 | 0 |
| `TST-CON-012` | **PASS** | 32 | 0 |
| `TST-CON-021` | **PASS** | 21 | 0 |
| `TST-CON-022` | **PASS** | 36 | 0 |
| `TST-CON-023` | **PASS** | 11 | 0 |
| `TST-CON-024` | **PASS** | 19 | 0 |
| `TST-CON-025` | **PASS** | 13 | 0 |
| `TST-CON-026` | **PASS** | 5 | 0 |
| `TST-CON-027` | **PASS** | 35 | 0 |
| `TST-CON-028` | **PASS** | 93 | 0 |
| `TST-CON-029` | **PASS** | 19 | 0 |
| `TST-CON-030` | **PASS** | 59 | 0 |
| `TST-CON-031` | **PASS** | 134 | 0 |
| `TST-CON-032` | **PASS** | 29 | 0 |
| `TST-CON-033` | **PASS** | 11 | 0 |
| `TST-CON-034` | **PASS** | 11 | 0 |
| `TST-CON-035` | **PASS** | 51 | 0 |

*(Remaining 1157 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: 11/11 passed (0 failed)

| REQ ID | TST ID | Metric | Measured Value | Budget | Verdict |
| :--- | :--- | :--- | :---: | :---: | :---: |
| `REQ-NFR-001` | `TST-NFR-001` | `ekf_cycle_p95_ms` | `0.7081 ms` | `<= 2.0 ms` | **PASS** |
| `REQ-NFR-002` | `TST-NFR-002` | `stationary_drift_m_per_min` | `0.0000 m/min` | `< 0.5 m/min` | **PASS** |
| `REQ-NFR-003` | `TST-NFR-003` | `tick_p99_ms` | `18.2006 ms` | `< 50.0 ms` | **PASS** |
| `REQ-NFR-005` | `TST-NFR-005` | `retained_heap_delta_mb` | `0.0000 MB` | `< 40.0 MB (total <= 150 MB)` | **PASS** |
| `REQ-NFR-007` | `TST-NFR-007` | `dispatch_p99_ms` | `0.0005 ms` | `< 25.0 ms` | **PASS** |
| `REQ-NFR-008` | `TST-NFR-008` | `watchdog_failover_ms` | `3500.0000 ms` | `<= 3500 ms` | **PASS** |
| `REQ-NFR-009` | `TST-NFR-009` | `wakelock_release_latency_ms` | `152.1930 ms` | `0 leaks (immediate release)` | **PASS** |
| `REQ-NFR-010` | `TST-NFR-010` | `db_write_p99_ms` | `0.4541 ms` | `< 30.0 ms` | **PASS** |
| `REQ-NFR-011` | `TST-NFR-011` | `storage_growth_kb_per_h` | `260.0000 KB/h` | `<= 360.0 KB/h` | **PASS** |
| `REQ-NFR-013` | `TST-NFR-013` | `polyline_update_p95_ms` | `0.0072 ms` | `< 30.0 ms` | **PASS** |
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
