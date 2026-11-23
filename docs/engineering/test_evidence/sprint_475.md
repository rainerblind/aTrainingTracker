# Test & Coverage Evidence: sprint_475

- **Target**: `sprint_475` (Type: `sprint`)
- **Git Commit**: `e0f528b26284f408903314b80ce7d31e077d520b`
- **Timestamp**: `2026-11-23T21:32:27.008234+00:00`
- **Overall Test Result**: **FAIL** (4 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 2,916
- **Passed**: 2,912
- **Failures**: 4
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 2119.24s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 561

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-001` | **PASS** | 1 | 0 |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 6 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-ARC-005` | **PASS** | 9 | 0 |
| `TST-ARC-006` | **PASS** | 5 | 0 |
| `TST-ARC-007` | **PASS** | 19 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-BUG-001` | **PASS** | 20 | 0 |
| `TST-BUG-007` | **PASS** | 13 | 0 |
| `TST-BUG-008` | **PASS** | 4 | 0 |
| `TST-BUG-009` | **PASS** | 4 | 0 |
| `TST-BUG-010` | **PASS** | 4 | 0 |
| `TST-BUG-011` | **PASS** | 3 | 0 |
| `TST-CON-002` | **PASS** | 13 | 0 |
| `TST-CON-003` | **PASS** | 16 | 0 |
| `TST-CON-004` | **PASS** | 13 | 0 |
| `TST-CON-005` | **PASS** | 19 | 0 |
| `TST-CON-006` | **PASS** | 15 | 0 |
| `TST-CON-007` | **PASS** | 11 | 0 |
| `TST-CON-008` | **PASS** | 17 | 0 |
| `TST-CON-009` | **PASS** | 21 | 0 |
| `TST-CON-010` | **PASS** | 3 | 0 |
| `TST-CON-011` | **PASS** | 37 | 0 |
| `TST-CON-012` | **PASS** | 32 | 0 |
| `TST-CON-021` | **PASS** | 21 | 0 |
| `TST-CON-022` | **PASS** | 29 | 0 |
| `TST-CON-023` | **PASS** | 11 | 0 |
| `TST-CON-024` | **PASS** | 19 | 0 |
| `TST-CON-025` | **PASS** | 13 | 0 |
| `TST-CON-026` | **PASS** | 5 | 0 |
| `TST-CON-027` | **PASS** | 35 | 0 |
| `TST-CON-028` | **PASS** | 100 | 0 |
| `TST-CON-029` | **PASS** | 19 | 0 |
| `TST-CON-030` | **PASS** | 67 | 0 |
| `TST-CON-031` | **PASS** | 111 | 0 |
| `TST-CON-032` | **PASS** | 29 | 0 |
| `TST-CON-033` | **PASS** | 11 | 0 |
| `TST-CON-034` | **PASS** | 11 | 0 |
| `TST-CON-035` | **PASS** | 51 | 0 |
| `TST-CON-036` | **PASS** | 16 | 0 |
| `TST-CON-037` | **PASS** | 14 | 0 |
| `TST-CON-038` | **PASS** | 43 | 0 |
| `TST-CON-039` | **PASS** | 22 | 0 |
| `TST-CON-040` | **PASS** | 93 | 0 |
| `TST-CON-041` | **PASS** | 93 | 0 |
| `TST-CON-042` | **PASS** | 100 | 0 |
| `TST-CON-043` | **PASS** | 3 | 0 |
| `TST-CON-044` | **PASS** | 86 | 0 |

*(Remaining 511 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: Not Measured (NFR measurement report not found at /tmp/wt_schicht_2026-11-23_02_Spät/app/build/reports/nfr/nfr_measurements.json)

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
