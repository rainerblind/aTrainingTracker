# Stage 2: Requirement & Test Specification - ATT-7759: Centralized Telemetry Color Palette & Global Graph Preferences

**Ticket**: [ATT-7759](https://issue-tracker/ATT-7759)  
**Parent Epic**: [ATT-5908](https://issue-tracker/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7759`  
**Requirement Mapping**: `REQ-UI-530` | **Test Spec ID**: `TST-UI-530`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-UI-530)

### 1.1 Problem Statement & Rationale
`TelemetryMetricGraph.kt` currently hardcodes raw `TTColor.Zone*` tokens and `TTColor.WPrimeBalGreen` that lack theme adaptation (`Zone1` fails contrast on light surfaces, `Zone5` is dim on dark/AMOLED backgrounds, and `WPrimeBalGreen` achieves only 2.53:1 contrast on white). Users also lack a global preference store to customize graph colors. A centralized 8-color Light/Dark palette with WCAG 2.1 compliance ($\ge 3.0:1$), canonical metric defaults, downgrade-safe preferences, and reactive graph integration resolves these issues (refining `REQ-UI-206`).

### 1.2 Functional & Architectural Requirements
1. **Centralized Fixed Telemetry Palette (`REQ-UI-530.1`)**: `Color.kt` SHALL define `TelemetryGraphColor` (8 Light/Dark tokens + `THEME_DEFAULT`), each achieving $\ge 3.0:1$ non-text contrast against Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`).
2. **Canonical Metric Defaults (`REQ-UI-530.2`)**: Canonical defaults SHALL be HR=Crimson, Power=Violet, W'bal=Emerald (Light `#059669`, Dark `#10B981`), DFA α1=Amber, rMSSD=Teal, Speed/Pace and cockpit sensors=`THEME_DEFAULT` (Blue).
3. **Global Preference Store & Safe Deserialization (`REQ-UI-530.3`)**: `TelemetryGraphColorPreferences` SHALL persist per-metric colors in `SharedPreferences`, supporting `getGraphColor`, `setGraphColor`, `resetGraphColor`, `resetAllGraphColors`, and downgrade-safe `safeEnumValueOf` fallback (`REQ-ARC-001`).
4. **TelemetryMetricGraph Integration (`REQ-UI-530.4`)**: `TelemetryMetricGraph.kt` SHALL resolve `accentColor` via `TelemetryGraphColorPreferences` + surface luminance (`TTColor.relativeLuminance(surface) < 0.5f`), preserving all geometry (`50.dp`/`25.dp`/`24.dp`), zone bands, scrubbing markers, and histograms.

### 1.3 Acceptance Criteria & Invariants
* *Given* default settings, *When* viewing `TelemetryMetricGraph`, *Then* HR, Power, W'bal, DFA α1, and rMSSD render in Crimson, Violet, Emerald, Amber, and Teal, while Speed/Pace render in primary Blue.
* *Given* any palette color in Light/Dark/AMOLED mode, *When* contrast is evaluated, *Then* stroke contrast is $\ge 3.0:1$ and zero `Color(0x...)` literals reside outside `Color.kt`.
* *Given* a metric color updated in preferences, *When* viewing Workout Detail, *Then* the chart reflects the updated color.
* *Invariants*: `TTColor.Zone1..7` and `TTColor.WPrimeBalGreen` unaltered; zero DB schema mutations; safe enum deserialization; 100% 9-language localization parity.

---

## 2. Test Specification (TST-UI-530)

### Test Case 1: Palette Contrast & WCAG Compliance (`TST-UI-530.1`)
* **Scope**: Unit Test (`TelemetryGraphColorContrastTest.kt`).
* **Assertions**: All 8 palette colors achieve $\ge 3.0:1$ contrast against `#FFFFFF`, `#16161A`, and `#000000`; Emerald Light (`#059669`) fixes `WPrimeBalGreen` contrast on white; `THEME_DEFAULT` resolves to `colorScheme.primary`.

### Test Case 2: Metric & Sensor Default Mappings (`TST-UI-530.2`)
* **Scope**: Unit Test (`TelemetryGraphColorPreferencesTest.kt`).
* **Assertions**: Canonical defaults verified for all `TelemetryMetricType` and `SensorType` families.

### Test Case 3: Preference Persistence & Safe Deserialization (`TST-UI-530.3`)
* **Scope**: Unit Test (`TelemetryGraphColorPreferencesTest.kt`).
* **Assertions**: `get`/`set`/`reset`/`resetAll` work deterministically; corrupted/unknown tokens safely fall back to default without exception.

### Test Case 4: TelemetryMetricGraph Accent Color Resolution (`TST-UI-530.4`)
* **Scope**: Unit Test (`TelemetryMetricGraphColorTest.kt` / `TelemetryMetricGraphTest.kt`).
* **Assertions**: `accentColor` resolves to theme-appropriate color token across Light, Dark, and AMOLED modes.

### Test Case 5: Architecture, Centralization & Governance Suite (`TST-UI-530.5`)
* **Scope**: Contract Tests & Linter.
* **Assertions**: `ThemeColorCentralizationAuditTest`, `AspiceSweAuditContractTest`, `SourceTextReadingAuditContractTest`, `SafeEnumDeserializationAuditTest`, and `verify_requirement_governance.py` pass cleanly.

---

## 3. Traceability Matrix & 9-Locale Audit

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-530.1]` | Unit | Palette contrast $\ge 3.0:1$ across Light/Dark/AMOLED | `REQ-UI-530.1` | Planned |
| `[TST-UI-530.2]` | Unit | Canonical metric & sensor default color mappings | `REQ-UI-530.2` | Planned |
| `[TST-UI-530.3]` | Unit | Preference persistence, reset & safe enum fallback | `REQ-UI-530.3` | Planned |
| `[TST-UI-530.4]` | Unit | `TelemetryMetricGraph` accent color resolution & theme adaptation | `REQ-UI-530.4` | Planned |
| `[TST-UI-530.5]` | Contract | Zero `Color(0x...)` outside `Color.kt`, architecture & governance | `REQ-UI-530`, `REQ-PRO-001` | Planned |

* **9-Language Localization Audit Note**: No new or altered user-facing string resources; 9-language localization parity is N/A.
