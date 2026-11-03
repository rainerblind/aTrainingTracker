# aTrainingTracker Online User Guide & Feature Showcase

Welcome to the official **aTrainingTracker** User Guide!

aTrainingTracker is an open-source, privacy-first athletic computer and telemetry tracking application for Android. It combines high-performance multi-protocol sensor connectivity (BLE + native ANT+), real-time barometric altitude calibration, offline vector maps, Strava Live Segments, automated *Lieblingsstrecken* route clustering, and full Coggan/Skiba sports science analytics into an intuitive, customizable cockpit.

---

## Select Language / Sprache wählen

<div style="display: flex; gap: 20px; margin: 24px 0;">
  <a href="guide/de/" style="font-size: 1.2em; font-weight: bold; text-decoration: none; padding: 12px 24px; background: #0288D1; color: white; border-radius: 8px;">🇩🇪 Deutsch (Benutzerhandbuch)</a>
  <a href="guide/en/" style="font-size: 1.2em; font-weight: bold; text-decoration: none; padding: 12px 24px; background: #0288D1; color: white; border-radius: 8px;">🇬🇧 English (User Guide)</a>
</div>

---

## Chapters Overview / Kapitelübersicht

| # | Deutsch | English |
|---|---------|---------|
| 1 | [Schnellstart & Erstes Training](guide/de/01-quick-start.md) | [Quick Start & First Ride/Run](guide/en/01-quick-start.md) |
| 2 | [Sensoren, Geräte & Dual-Protokoll-Hardware](guide/de/02-sensors-dual-protocol.md) | [Sensors, Devices & Dual-Protocol Hardware](guide/en/02-sensors-dual-protocol.md) |
| 3 | [Ausrüstung & Sensor-Matrix](guide/de/03-equipment-sensor-matrix.md) | [Equipment & Sensor Matrix](guide/en/03-equipment-sensor-matrix.md) |
| 4 | [Anpassbares Live-Cockpit & Tracking-Tabs](guide/de/04-live-cockpit-tabs.md) | [Customizable Live Cockpit & Tracking Tabs](guide/en/04-live-cockpit-tabs.md) |
| 5 | [Karten, GPX-Routen, Höhenprofile & Strava Live Segmente](guide/de/05-maps-routes-climbs-segments.md) | [Maps, GPX Routes, Climb Profiles & Strava Live Segments](guide/en/05-maps-routes-climbs-segments.md) |
| 6 | [Lieblingsstrecken & Routen-Clustering](guide/de/06-automatic-route-clustering.md) | [Route Clustering & Automated Intelligence](guide/en/06-automatic-route-clustering.md) |
| 7 | [Physiologie, Trainingszonen & Leistungsanalyse](guide/de/07-physiology-training-zones.md) | [Physiology, Training Zones & Performance Analytics](guide/en/07-physiology-training-zones.md) |
| 8 | [Workout-Auswertung, Perioden 2.0 & Jahresstatistiken](guide/de/08-workout-aftermath-periods.md) | [Workout Aftermath, Periods 2.0 & Lifetime Statistics](guide/en/08-workout-aftermath-periods.md) |
| 9 | [Datensouveränität, Export & Cloud-Synchronisation](guide/de/09-export-cloud-sync.md) | [Data Sovereignty, Export & Cloud Sync](guide/en/09-export-cloud-sync.md) |

---

## Key Highlights & Philosophy

- **100% Local Data Ownership**: Your workout records and GPS tracks reside securely on your own device in an open SQLite database. No mandatory accounts, no telemetry tracking, and zero advertising.
- **Dual-Protocol Hardware Arbitration**: Connect Bluetooth LE and native ANT+ sensors seamlessly with intelligent failover and battery telemetry.
- **Professional Analytics**: Calculate Normalized Power ($\text{NP}$), Intensity Factor ($\text{IF}$), Training Stress Score ($\text{TSS}$), Skiba Anaerobic Work Capacity balance ($W'_{\text{bal}}$), and longitudinal Performance Management ($\text{CTL}$, $\text{ATL}$, $\text{TSB}$).
