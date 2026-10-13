# Backlog Ticket Specification: ATT-3188

**Key:** ATT-3188  
**Summary:** Calculate training zone boundaries automatically from FTP and Heart Rate baselines  
**Type:** Improvement (`Verbesserung`)  
**Status:** Open / Backlog (`Zu erledigen`)  
**Epic:** ATT-111 (Aftermath: Compact Post-Workout Visual Analytics & Graphs)  
**Related Tickets:** [ATT-1290](https://rainerblind.atlassian.net/browse/ATT-1290) (Live NP & Intensity Factor), [ATT-1291](https://rainerblind.atlassian.net/browse/ATT-1291) (Real-time TSS & Mechanical Work)

---

### Problem Statement & Value Proposition
Configuring individual training zone boundaries manually requires athletes to calculate percentage cutoffs (e.g. 55%, 75%, 90%, 105%, 120%, 150% of FTP for cycling power; or 60%, 70%, 80%, 90% of HRmax / Karvonen reserve for heart rate) and adjust each min/max threshold card one by one. This friction often results in default or misconfigured zone boundaries, degrading the fidelity of cockpit zone coloring and Aftermath distribution analytics.

Furthermore, advanced power-based telemetry metrics such as Live Intensity Factor (IF) in ATT-1290 and real-time Training Stress Score (TSS) in ATT-1291 strictly require the athlete's Functional Threshold Power (FTP) as a persistent single source of truth (`IF = NP / FTP`; `TSS = [(s * NP * IF) / (FTP * 3600)] * 100`). If FTP is only treated as an ephemeral or optional calculator input, downstream metric engines cannot compute real-time training load.

Athletes need an intuitive, one-tap calculation assistant in Zone Settings that takes their benchmark metrics (FTP in Watts, or Max HR and optional Resting HR in bpm), permanently persists these physiological baselines in `SettingsDataStore`, and instantly populates the appropriate zone thresholds based on standard exercise physiology models, while still allowing full manual fine-tuning afterward.

---

### User Story
* **As an** endurance athlete (cyclist or runner)
* **I want** to automatically calculate my heart rate and cycling power zone boundaries from my benchmark physiology metrics (FTP or HRmax / Resting HR) and have my FTP persistently stored
* **So that** I can set up scientifically accurate training zones with minimal effort, and have my FTP available for live cockpit metrics like Intensity Factor (IF, ATT-1290) and Training Stress Score (TSS, ATT-1291).

---

### Scope & Functional Requirements

1. **Zone Settings Entry Point**:
   - Add a "Calculate Zones" / "Zone Calculator" action (icon button in `TopAppBar` or contextual button in the profile header) within `ZonesSettingsActivity` / `ZoneSettingsScreen`.
   - The calculation dialog adapts contextually to the currently selected tab (`HR_RUN`, `HR_BIKE`, or `PWR_BIKE`).

2. **Mandatory Persistent Storage of Athlete Baselines**:
   - Introduce persistent preferences in `SettingsDataStore.kt`:
     - `KEY_CYCLING_FTP` (`intPreferencesKey("cycling_ftp")`): Cycling FTP in Watts (e.g. default 0 or 200W).
     - `KEY_ATHLETE_HR_MAX` (`intPreferencesKey("athlete_hr_max")`): Maximum heart rate in bpm.
     - `KEY_ATHLETE_HR_REST` (`intPreferencesKey("athlete_hr_rest")`): Resting heart rate in bpm.
   - When the calculation dialog is opened, the input fields are pre-filled with the currently stored values from DataStore.
   - When the user taps "Apply" ("Übernehmen"), the dialog persists both the calculated zone boundaries AND the entered baseline values (FTP, HRmax, HRrest) to `SettingsDataStore`.
   - Expose these baselines as observable reactive flows (e.g. `cyclingFtpFlow: Flow<Int>` and synchronous getters for background services) to serve as the single source of truth for ATT-1290 (`IF = NP / FTP`) and ATT-1291 (live TSS).

3. **Cycling Power Calculation Model (Coggan 7-Zone)**:
   - Input: Functional Threshold Power (FTP) in Watts (valid range e.g. 50W–600W).
   - Calculates and populates Coggan power zones:
     - Zone 1 (Active Recovery): Upper bound = `round(FTP * 0.55)`
     - Zone 2 (Endurance): Upper bound = `round(FTP * 0.75)`
     - Zone 3 (Tempo): Upper bound = `round(FTP * 0.90)`
     - Zone 4 (Threshold): Upper bound = `round(FTP * 1.05)`
     - Zone 5 (VO2 Max): Upper bound = `round(FTP * 1.20)`
     - Zone 6 (Anaerobic Capacity): Upper bound = `round(FTP * 1.50)`
     - Zone 7 (Neuromuscular): Min bound = `Zone 6 upper bound + 1` (no upper limit)

4. **Heart Rate Calculation Models (5-Zone)**:
   - The user can choose between standard physiological approaches in the dialog:
     - **Model A: % of Maximum Heart Rate (% HRmax)**:
       - Inputs: Max Heart Rate (HRmax).
       - Zone 1 (Recovery): Upper bound = `round(HRmax * 0.60)`
       - Zone 2 (Aerobic / Endurance): Upper bound = `round(HRmax * 0.70)`
       - Zone 3 (Tempo): Upper bound = `round(HRmax * 0.80)`
       - Zone 4 (Threshold): Upper bound = `round(HRmax * 0.90)`
       - Zone 5 (Anaerobic / Max): Min bound = `Zone 4 upper bound + 1`
     - **Model B: Heart Rate Reserve (Karvonen Formula)**:
       - Inputs: Max Heart Rate (HRmax) and Resting Heart Rate (HRrest).
       - Formula: `Target HR = round(HRrest + % * (HRmax - HRrest))`
       - Zone 1: Upper bound = `round(HRrest + 0.60 * (HRmax - HRrest))`
       - Zone 2: Upper bound = `round(HRrest + 0.70 * (HRmax - HRrest))`
       - Zone 3: Upper bound = `round(HRrest + 0.80 * (HRmax - HRrest))`
       - Zone 4: Upper bound = `round(HRrest + 0.90 * (HRmax - HRrest))`
       - Zone 5: Min bound = `Zone 4 upper bound + 1`

5. **Calculation Dialog UI & UX**:
   - Material 3 `AlertDialog` or `ModalBottomSheet` containing:
     - Method selector (e.g. `FilterChip` or segmented buttons for HR: "% HRmax" vs. "Karvonen HRR").
     - Numerical `OutlinedTextField`s with input validation (e.g. FTP > 0, HRmax > HRrest).
     - Live preview of calculated zone boundary numbers before applying.
     - "Apply" button that populates the active tab's boundary cards and permanently persists the baseline metrics (FTP, HR baselines) and zone thresholds.
   - Athlete can still freely adjust individual slider/card values after automatic calculation has populated them.

6. **Localization Parity**:
   - Localize all calculator strings across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

### Acceptance Criteria
* **Given** the athlete is on the Cycling Power tab in Zone Settings
* **When** tapping "Calculate Zones" and entering an FTP of 300 Watts and tapping "Apply"
* **Then**:
  - The zone boundaries are populated with:
    - Z1 Max: 165W (55%)
    - Z2 Max: 225W (75%)
    - Z3 Max: 270W (90%)
    - Z4 Max: 315W (105%)
    - Z5 Max: 360W (120%)
    - Z6 Max: 450W (150%)
  - The value 300 Watts is permanently saved to `SettingsDataStore` under `cycling_ftp`.
* **Given** an FTP of 300 Watts has been saved
* **When** re-opening the Zone Calculator or querying `SettingsDataStore.cyclingFtpFlow`
* **Then** the stored value 300 Watts is returned, providing the benchmark for ATT-1290 (IF) and ATT-1291 (TSS).
* **Given** the athlete is on the Heart Rate tab (Run or Bike)
* **When** selecting "% HRmax", entering Max HR = 190 bpm, and applying
* **Then** the 5 zones populate with Z1 Max: 114 bpm (60%), Z2 Max: 133 bpm (70%), Z3 Max: 152 bpm (80%), Z4 Max: 171 bpm (90%), and 190 bpm is permanently saved to `SettingsDataStore` under `athlete_hr_max`.
* **Given** the athlete selects "Karvonen" with Max HR = 190 and Resting HR = 50 bpm and applies
* **Then** the zones populate with the calculated Heart Rate Reserve values, and both HRmax (190) and HRrest (50) are permanently saved to `SettingsDataStore`.
* **Given** automatically calculated zones are applied
* **When** the athlete manually edits a specific zone threshold card
* **Then** manual fine-tuning operates normally without locking, while the stored baseline FTP remains intact unless explicitly changed.

---

### Technical Notes & Touchpoints
- [SettingsDataStore.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt): Add persistent keys `cycling_ftp`, `athlete_hr_max`, `athlete_hr_rest` with reactive Flows and mutator functions.
- `ZonesSettingsActivity.kt` / `ZoneSettingsScreen`: TopAppBar calculation action, Compose dialog, pre-populating inputs from DataStore, and saving on Apply.
- `ZoneCalculationMath.kt`: Pure calculation engine for Coggan FTP brackets and Karvonen / %HRmax formulas.
- **Downstream Consumers**: Serves as prerequisite single source of truth for [ATT-1290](https://rainerblind.atlassian.net/browse/ATT-1290) (Normalized Power & Intensity Factor) and [ATT-1291](https://rainerblind.atlassian.net/browse/ATT-1291) (Training Stress Score).
- `res/values-*/strings.xml`: 9-locale parity for calculator dialog titles, labels, and helper text.
