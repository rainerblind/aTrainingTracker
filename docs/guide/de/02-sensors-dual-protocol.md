# Kapitel 2: Sensoren, Geräte & Dual-Protokoll-Hardware (BLE & ANT+)

<a id="sensor-pairing-bluetooth-ant"></a>
## 2.1 Kopplung von Bluetooth LE- und ANT+-Sensoren

aTrainingTracker verfügt über eine fortschrittliche Multi-Protokoll-Engine zur parallelen Verbindung von Bluetooth Low Energy (BLE) und nativen ANT+-Sensoren.

- **Unterstützte Profile**:
  - Herzfrequenzbrustgurte & optische Armbänder (BLE Heart Rate Service & ANT+ HRM)
  - Rad-Leistungsmesser (BLE Cycling Power & ANT+ PWR, einschließlich L/R-Balance)
  - Trittfrequenz- & Geschwindigkeitssensoren (BLE CSC & ANT+ BSC)
  - Running Dynamics & Laufsensoren (BLE RSC & ANT+ Stride)
- **Suchlauf & Kopplung**:
  Öffnen Sie *Geräte / Sensoren* im Menü. Der automatische Scan sucht gleichzeitig nach aktiven BLE- und ANT+-Geräten. Ein Fingertipp genügt, um ein Gerät zu koppeln und dem gewünschten Messkanal zuzuweisen.

![Geräte- & Sensorkopplung](../img/02_sensor_pairing.png)

---

<a id="barometric-altitude-calibration"></a>
## 2.2 Smartphone-Sensoren & Barometrische Höhenkalibrierung

Reine GPS-Höhenmessung unterliegt atmosphärischen und geometrischen Schwankungen ($\pm 15\text{--}30\text{ m}$). Wenn Ihr Smartphone ein Barometer besitzt, nutzt aTrainingTracker den hochpräzisen Drucksensor:

- **Bekannte Orte (*Known Locations*)**: Definieren Sie feste Referenzpunkte (z. B. Ihr Zuhause oder den Startpunkt Ihrer Stammrunde) mit exakter Meereshöhe. Sobald Sie das Training dort starten, kalibriert sich das Barometer absolut und driftfrei.
- **Automatische Kalibrierung**: Unterwegs gleicht die App langsame Wetter-Luftdruckänderungen kontinuierlich mit geglätteten GPS-Höhenwerten ab.

![Barometrische Höhenkalibrierung](../img/02_altitude_calibration.png)

---

<a id="priority-and-failover"></a>
## 2.3 Sensor-Priorität, Ausfallschutz & Batteriestatus

Wenn mehrere Sensoren für denselben Messwert verfügbar sind (z. B. Wattmessung via BLE und ANT+, oder Geschwindigkeit via Radsensor vs. GPS):

1. **Hardware-Sensor schlägt GPS**: Die exakte Raddrehung über einen Radsensor hat immer Vorrang vor GPS-Geschwindigkeit (Tunnel, dichte Wälder).
2. **Unterbrechungsfreier Ausfallschutz (*Failover*)**: Reißt ein Signal kurzzeitig ab, übernimmt nahtlos das alternative Protokoll oder das GPS, ohne dass Lücken in der Aufzeichnung entstehen.
3. **Batteriestand-Überwachung**: Für alle gekoppelten Sensoren wird der aktuelle Ladezustand angezeigt, sodass Sie rechtzeitig vor schwachen Knopfzellen gewarnt werden.

![Dual-Protokoll Status & Akku-Monitoring](../img/02_dual_protocol.png)

---

[Vorheriges Kapitel: Schnellstart](01-quick-start.md) | [Nächstes Kapitel: Ausrüstung & Sensor-Matrix](03-equipment-sensor-matrix.md)
