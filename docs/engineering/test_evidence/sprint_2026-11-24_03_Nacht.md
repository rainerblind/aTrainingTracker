# Test & Coverage Evidence: sprint_2026-11-24_03_Nacht

- **Target**: `sprint_2026-11-24_03_Nacht` (Type: `sprint`)
- **Git Commit**: `aa2fb662d3be97e25a92fe7e000dc0976fa0fcf7`
- **Timestamp**: `2026-11-25T04:32:51.504687+00:00`
- **Overall Test Result**: **FAIL** (3 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 1,379
- **Passed**: 1,376
- **Failures**: 3
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 1751.51s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 337

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 6 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-ARC-008` | **PASS** | 14 | 0 |
| `TST-ARC-009` | **PASS** | 3 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-BUG-001` | **PASS** | 20 | 0 |
| `TST-CON-003` | **PASS** | 16 | 0 |
| `TST-CON-005` | **PASS** | 19 | 0 |
| `TST-CON-009` | **PASS** | 22 | 0 |
| `TST-CON-011` | **PASS** | 38 | 0 |
| `TST-CON-012` | **PASS** | 25 | 0 |
| `TST-CON-021` | **PASS** | 22 | 0 |
| `TST-CON-022` | **PASS** | 26 | 0 |
| `TST-CON-023` | **PASS** | 11 | 0 |
| `TST-CON-024` | **PASS** | 19 | 0 |
| `TST-CON-026` | **PASS** | 5 | 0 |
| `TST-CON-027` | **PASS** | 20 | 0 |
| `TST-CON-028` | **PASS** | 99 | 0 |
| `TST-CON-030` | **PASS** | 68 | 0 |
| `TST-CON-031` | **PASS** | 90 | 0 |
| `TST-CON-036` | **PASS** | 19 | 0 |
| `TST-CON-040` | **PASS** | 68 | 0 |
| `TST-CON-041` | **PASS** | 68 | 0 |
| `TST-CON-042` | **PASS** | 68 | 0 |
| `TST-CON-044` | **PASS** | 68 | 0 |
| `TST-CON-045` | **PASS** | 58 | 0 |
| `TST-CON-046` | **PASS** | 68 | 0 |
| `TST-CON-047` | **PASS** | 13 | 0 |
| `TST-CON-048` | **PASS** | 5 | 0 |
| `TST-CON-049` | **PASS** | 193 | 0 |
| `TST-CON-050` | **PASS** | 77 | 0 |
| `TST-CON-051` | **PASS** | 46 | 0 |
| `TST-CON-052` | **PASS** | 77 | 0 |
| `TST-CON-053` | **PASS** | 78 | 0 |
| `TST-CON-054` | **PASS** | 146 | 0 |
| `TST-CON-055` | **PASS** | 90 | 0 |
| `TST-CON-057` | **PASS** | 34 | 0 |
| `TST-CON-060` | **PASS** | 141 | 0 |
| `TST-CON-061` | **PASS** | 73 | 0 |
| `TST-CON-062` | **PASS** | 39 | 0 |
| `TST-CON-063` | **PASS** | 5 | 0 |
| `TST-CON-064` | **PASS** | 32 | 0 |
| `TST-DAT-002` | **PASS** | 18 | 0 |
| `TST-DAT-020` | **PASS** | 10 | 0 |
| `TST-DAT-021` | **PASS** | 18 | 0 |
| `TST-DAT-029` | **PASS** | 4 | 0 |
| `TST-DAT-032` | **PASS** | 4 | 0 |
| `TST-DAT-035` | **PASS** | 6 | 0 |

*(Remaining 287 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: Not Measured (NFR measurement report not found at /tmp/wt_schicht_2026-11-24_03_Nacht/app/build/reports/nfr/nfr_measurements.json)

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
