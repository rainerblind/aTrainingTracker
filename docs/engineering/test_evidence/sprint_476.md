# Test & Coverage Evidence: sprint_476

- **Target**: `sprint_476` (Type: `sprint`)
- **Git Commit**: `90a05230fcf872c35c8d4ae053ab628c6a7350ec`
- **Timestamp**: `2026-11-24T04:01:27.188871+00:00`
- **Overall Test Result**: **FAIL** (4 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 3,429
- **Passed**: 3,425
- **Failures**: 4
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 2069.57s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 688

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 6 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-ARC-009` | **PASS** | 3 | 0 |
| `TST-ARC-010` | **PASS** | 16 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-BUG-002` | **PASS** | 7 | 0 |
| `TST-CLM-009` | **PASS** | 10 | 0 |
| `TST-CLM-011` | **PASS** | 13 | 0 |
| `TST-CON-008` | **PASS** | 11 | 0 |
| `TST-CON-010` | **PASS** | 3 | 0 |
| `TST-CON-012` | **PASS** | 3 | 0 |
| `TST-CON-022` | **PASS** | 10 | 0 |
| `TST-CON-025` | **PASS** | 19 | 0 |
| `TST-CON-027` | **PASS** | 11 | 0 |
| `TST-CON-032` | **PASS** | 29 | 0 |
| `TST-CON-033` | **PASS** | 11 | 0 |
| `TST-CON-037` | **PASS** | 14 | 0 |
| `TST-CON-038` | **PASS** | 43 | 0 |
| `TST-CON-039` | **PASS** | 22 | 0 |
| `TST-CON-040` | **PASS** | 22 | 0 |
| `TST-CON-041` | **PASS** | 22 | 0 |
| `TST-CON-042` | **PASS** | 22 | 0 |
| `TST-CON-043` | **PASS** | 3 | 0 |
| `TST-CON-044` | **PASS** | 22 | 0 |
| `TST-CON-046` | **PASS** | 22 | 0 |
| `TST-CON-056` | **PASS** | 5 | 0 |
| `TST-CON-058` | **PASS** | 15 | 0 |
| `TST-DAT-001` | **PASS** | 18 | 0 |
| `TST-DAT-003` | **PASS** | 7 | 0 |
| `TST-DAT-004` | **PASS** | 3 | 0 |
| `TST-DAT-005` | **PASS** | 2 | 0 |
| `TST-DAT-006` | **PASS** | 10 | 0 |
| `TST-DAT-007` | **PASS** | 15 | 0 |
| `TST-DAT-008` | **PASS** | 18 | 0 |
| `TST-DAT-009` | **PASS** | 18 | 0 |
| `TST-DAT-010` | **PASS** | 18 | 0 |
| `TST-DAT-011` | **PASS** | 18 | 0 |
| `TST-DAT-012` | **PASS** | 21 | 0 |
| `TST-DAT-013` | **PASS** | 16 | 0 |
| `TST-DAT-014` | **PASS** | 16 | 0 |
| `TST-DAT-016` | **PASS** | 11 | 0 |
| `TST-DAT-019` | **PASS** | 13 | 0 |
| `TST-DAT-020` | **PASS** | 17 | 0 |
| `TST-DAT-022` | **PASS** | 5 | 0 |
| `TST-DAT-023` | **PASS** | 16 | 0 |
| `TST-DAT-024` | **PASS** | 26 | 0 |
| `TST-DAT-026` | **PASS** | 12 | 0 |
| `TST-DAT-027` | **PASS** | 3 | 0 |

*(Remaining 638 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: Not Measured (NFR measurement report not found at /tmp/wt_schicht_2026-11-23_03_Nacht/app/build/reports/nfr/nfr_measurements.json)

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
