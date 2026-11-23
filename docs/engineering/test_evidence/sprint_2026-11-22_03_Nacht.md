# Test & Coverage Evidence: sprint_2026-11-22_03_Nacht

- **Target**: `sprint_2026-11-22_03_Nacht` (Type: `sprint`)
- **Git Commit**: `9eee040735dace4e3f5c926df877843dcca68b7b`
- **Timestamp**: `2026-11-23T04:13:37.619168+00:00`
- **Overall Test Result**: **FAIL** (4 failure(s), 0 error(s))

---

## 1. Unit Test Metrics
- **Total Tests Executed**: 1,157
- **Passed**: 1,153
- **Failures**: 4
- **Errors**: 0
- **Skipped**: 0
- **Execution Duration**: 1117.97s

---

## 2. Code Coverage Metrics (Kover)
- **Status**: Not Measured (Kover skipped for regular Schicht (executed in weekly Code Review / ASPICE Audit and releases))

---

## 3. Evaluated TST Specifications
- **Total Correlated TSTs**: 296

| TST ID | Status | Passing Cases | Failing Cases |
| :--- | :---: | :---: | :---: |
| `TST-ARC-002` | **PASS** | 8 | 0 |
| `TST-ARC-003` | **PASS** | 6 | 0 |
| `TST-ARC-004` | **PASS** | 2 | 0 |
| `TST-BLD-002` | **PASS** | 33 | 0 |
| `TST-BLD-003` | **PASS** | 7 | 0 |
| `TST-BUG-002` | **PASS** | 7 | 0 |
| `TST-CON-028` | **PASS** | 64 | 0 |
| `TST-CON-030` | **PASS** | 64 | 0 |
| `TST-CON-031` | **PASS** | 64 | 0 |
| `TST-CON-040` | **PASS** | 64 | 0 |
| `TST-CON-041` | **PASS** | 64 | 0 |
| `TST-CON-042` | **PASS** | 64 | 0 |
| `TST-CON-044` | **PASS** | 64 | 0 |
| `TST-CON-046` | **PASS** | 64 | 0 |
| `TST-CON-055` | **PASS** | 71 | 0 |
| `TST-DAT-005` | **PASS** | 2 | 0 |
| `TST-DAT-006` | **PASS** | 7 | 0 |
| `TST-DAT-007` | **PASS** | 9 | 0 |
| `TST-DAT-012` | **PASS** | 2 | 0 |
| `TST-DAT-014` | **PASS** | 16 | 0 |
| `TST-DAT-026` | **PASS** | 8 | 0 |
| `TST-DAT-027` | **PASS** | 3 | 0 |
| `TST-DAT-031` | **PASS** | 5 | 0 |
| `TST-EXP-007` | **PASS** | 5 | 0 |
| `TST-EXP-008` | **PASS** | 11 | 0 |
| `TST-EXP-011` | **PASS** | 8 | 0 |
| `TST-EXP-043` | **PASS** | 6 | 0 |
| `TST-EXP-045` | **PASS** | 77 | 0 |
| `TST-EXP-046` | **PASS** | 15 | 0 |
| `TST-EXP-047` | **PASS** | 91 | 0 |
| `TST-EXT-005` | **PASS** | 5 | 0 |
| `TST-EXT-007` | **PASS** | 4 | 0 |
| `TST-FIL-016` | **PASS** | 43 | 0 |
| `TST-FIL-017` | **PASS** | 24 | 0 |
| `TST-FIL-019` | **PASS** | 14 | 0 |
| `TST-FIL-020` | **PASS** | 26 | 0 |
| `TST-FIL-022` | **PASS** | 43 | 0 |
| `TST-FIL-023` | **PASS** | 106 | 0 |
| `TST-FIL-025` | **PASS** | 35 | 0 |
| `TST-FIL-026` | **PASS** | 43 | 0 |
| `TST-FIL-027` | **PASS** | 87 | 0 |
| `TST-FIL-028` | **PASS** | 108 | 0 |
| `TST-FIL-029` | **PASS** | 57 | 0 |
| `TST-FIL-030` | **PASS** | 9 | 0 |
| `TST-FIL-031` | **PASS** | 10 | 0 |
| `TST-FIL-032` | **PASS** | 35 | 0 |
| `TST-FIL-033` | **PASS** | 10 | 0 |
| `TST-FIL-034` | **PASS** | 58 | 0 |
| `TST-FIL-035` | **PASS** | 16 | 0 |
| `TST-FIL-036` | **PASS** | 91 | 0 |

*(Remaining 246 TST specifications recorded in companion JSON)*

---

## 4. Non-Functional Requirements (NFR) Verification Summary
- **Group A (JVM/Robolectric Measured)**: Not Measured (NFR measurement report not found at /tmp/wt_schicht_2026-11-22_03_Nacht/app/build/reports/nfr/nfr_measurements.json)

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
