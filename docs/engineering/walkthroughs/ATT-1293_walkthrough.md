# Walkthrough: Visual Training Zone Gauge Tile in Cockpit (ATT-1293)

## Fulfilling REQ-UI-376 & TST-UI-339

A real-time horizontal segmented training zone gauge bar (`ZoneGaugeBar`) was engineered and integrated across `ZoneGaugeMath`, `ZoneDisplayOptions`, `SettingsDataStore`, `SensorFieldState`, `SensorFieldView`, `TrackingViewModel`, and `ZonesSettingsActivity`, conforming to ASPICE SWE.3, SWE.4, and SWE.5 standards.

---

### Implemented Architecture & Changes

#### 1. Pure Mathematical Engine & Intra-Zone Interpolation (`ZoneGaugeMath.kt`, `ZoneGaugeState.kt`)
- **`ZoneGaugeState.kt`**:
  - Encapsulates immutable UI state: `segmentColors: List<Color>`, `needleFraction: Float?`, and optional `activeZoneIndex: Int?`.
- **`ZoneGaugeMath.kt`**:
  - Implements $O(1)$, allocation-free pure calculation for 5-zone Heart Rate and 7-zone Coggan Cycling Power models.
  - Decoupled equal visual width partition ($\frac{1}{K}$ width per zone: $0.20$ per zone for HR, $1/7 \approx 0.142857$ per zone for Power).
  - Intra-zone interpolation:
    $$x_{\text{needle}} = \frac{(i - 1) + \text{fraction}_i}{K} \in [0.0, 1.0]$$
  - Open-ended top zone dynamic ceiling:
    - HR Zone 5: $U_5 = \max(H\!R_{\max}, Z_{4\text{max}} + 25\text{ bpm})$.
    - Power Zone 7: $U_7 = \min(2500\text{ W}, \max(Z_{6\text{max}} + 300\text{ W}, \text{FTP} \times 2.0))$, enforcing the $2500\text{ W}$ physical ceiling of `REQ-UI-373.1`.
    - Extreme efforts exceeding $U_{\text{top}}$ clamp cleanly to $x_{\text{needle}} = 1.0$.
  - Lower baseline clamping ($30\text{ bpm}$ for HR, $0\text{ W}$ for Power) maps to $x_{\text{needle}} = 0.0$.
  - Telemetry stale / null / non-finite inputs return `needleFraction = null`.

#### 2. User Preferences & DataStore Persistence (`ZoneDisplayOptions.kt`, `SettingsDataStore.kt`)
- Added `showGauge: Boolean = false` to `ZoneDisplayOptions.kt` for 100% backward compatibility.
- Added preference keys `hr_run_zone_show_gauge`, `hr_bike_zone_show_gauge`, `pwr_bike_zone_show_gauge` in `SettingsDataStore.kt`.
- Updated `getZoneDisplayOptionsFlow` and `saveZoneDisplayOptions` to persist `showGauge` independently across `HR_RUN`, `HR_BIKE`, and `PWR_BIKE`.

#### 3. Visual Component & AMOLED Contrast (`ZoneGaugeBar.kt`)
- Compact horizontal track (`6.dp` track height, `2.dp` needle overhang top & bottom = `10.dp` total height).
- Outer track and segment dividers stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ against True-Black AMOLED backgrounds (`#000000`) for dark segments (`TTColor.Zone6` and `TTColor.Zone7`).
- Indicator needle rendered in Pure White (`#FFFFFF`) with a 1dp dark shadow/border (`#1B1B1F`), achieving $> 12:1$ contrast against any zone segment color.
- When telemetry is stale or null (`state.needleFraction == null`), the track opacity dims to $0.35$ alpha and the needle is hidden.
- Optional active zone badge (`Z1`..`Z7`) rendered in the active zone color.
- Includes Compose `@Preview` for Light, Dark, and AMOLED modes.

#### 4. Cockpit Tile & ViewModel Integration (`SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`)
- `SensorFieldState`: Added `val zoneGaugeState: ZoneGaugeState? = null`.
- `SensorFieldView`: Renders `ZoneGaugeBar` directly below the value/units row when `zoneDisplayOptions.showGauge == true && zoneGaugeState != null`. Compact badge shown on view sizes $\ge \text{NORMAL}$.
- `TrackingViewModel`: Computes `zoneGaugeState` in `baseFields` and updates it during live telemetry ingestion for `HR` and `POWER`, reusing pure $O(1)$ math without triggering full-tile recompositions.

#### 5. Settings Screen & Interactive Live Preview (`ZonesSettingsActivity.kt`)
- Added "Zonen-Balken / Zone gauge bar" (`R.string.zone_display_gauge`) toggle row in the "Zonendarstellung / Zone Visualization" card.
- Live preview card computes and renders `previewGaugeState` for the selected preview chip (`Z1`..`Z7`), dynamically reflecting toggle changes in both Light and Dark/AMOLED modes.

#### 6. 9-Language Localization
- Added `zone_display_gauge` across all 9 application locales:
  - English (`values/`): `Zone gauge bar`
  - German (`values-de/`): `Zonen-Balken`
  - Spanish (`values-es/`): `Barra indicadora de zona`
  - French (`values-fr/`): `Jauge visuelle de zone`
  - Italian (`values-it/`): `Barra indicatore di zona`
  - Japanese (`values-ja/`): `ゾーンゲージバー`
  - Dutch (`values-nl/`): `Zonemeterbalk`
  - Polish (`values-pl/`): `Wskaźnik strefy`
  - Portuguese (`values-pt/`): `Barra de zona`

---

### Verification & Test Results (`TST-UI-339`)

1. **`ZoneGaugeMathTest.kt`**:
   - `testHeartRate5Zones_intraZoneNeedlePositioning`: PASSED (tested quintiles $0.1, 0.2, 0.4, 0.6, 0.8$).
   - `testHeartRate5Zones_openEndedZone5CeilingAndClamping`: PASSED (entry $0.80$, ceiling $1.00$, extreme $1.00$).
   - `testHeartRate5Zones_belowBaselineClamping`: PASSED (tested $20\text{ bpm}$ and $0\text{ bpm}$ clamp to $0.00$).
   - `testPower7Zones_intraZoneNeedlePositioning`: PASSED (tested septiles $1/7 \dots 6/7$).
   - `testPower7Zones_openEndedZone7DynamicCeilingAndClamping`: PASSED (entry $6/7$, ceiling $1.00$, sprint $1.00$, 2500W physical cap).
   - `testNullAndNonFiniteTelemetry_returnsNullNeedlePosition`: PASSED.
   - `testActiveZoneIndexCalculation`: PASSED across all 5 HR and 7 Power zones.
   - `testCalculateZoneGaugeState`: PASSED for HR, Power, and stale state.
2. **`ZoneDisplayOptionsTest.kt`**:
   - Tested default `showGauge = false`, custom assignment, copy mutation, and equality/hashCode parity: PASSED.
3. **`SettingsDataStoreZoneGaugeTest.kt`**:
   - Tested default `showGauge = false` across `HR_RUN`, `HR_BIKE`, `PWR_BIKE`: PASSED.
   - Tested independent profile persistence and Flow reactivity: PASSED.
4. **`SensorFieldZoneGaugeTest.kt`**:
   - Tested default absence, HR gauge state preservation, Power gauge state preservation, stale state, and copy mutation: PASSED.
5. **`ZoneDisplayLocalizationTest.kt`**:
   - Verified 100% presence and non-empty translation of `zone_display_gauge` across all 9 locales: PASSED.
