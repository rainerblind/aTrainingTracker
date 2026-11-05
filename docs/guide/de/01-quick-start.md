# Kapitel 1: Schnellstart & Erstes Training

<a id="quick-start-overview"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 1.1 Der Start-Tab & Sportart-Auswahl

Wenn du **aTrainingTracker** öffnest, startest du direkt im ersten Tab **Start**:

- **Sensor-Statusleiste (ganz oben)**: Zeigt dir auf einen Blick, welche internen und externen Sensoren bereits aktiv sind (Trainingszeit, GPS, Höhe, Speicherkarte sowie – sobald verbunden – Geschwindigkeit, Trittfrequenz, Herzfrequenz und Leistung).
- **Bekannte Orte**: Startest du an einem gespeicherten Referenzpunkt (z. B. *„Zu Hause erkannt"*), kalibriert die App den barometrischen Höhenmesser automatisch auf die hinterlegte Meereshöhe.
- **Sensorsuche (`Suchen`)**: Sobald du gekoppelte ANT+- oder Bluetooth-LE-Sensoren besitzt, sucht die App beim Öffnen automatisch danach. Mit **Suchen** kannst du jederzeit einen neuen Suchlauf starten.
- **Aufzeichnung starten**: Ein Tipp auf das große blaue **Play-Symbol** startet dein Training.
  - *Hinweis zu Berechtigungen:* Erst beim allerersten Start fragt die App schrittweise die nötigen Freigaben ab: **Standort** (für GPS), **Benachrichtigungen** (für die laufende Statusanzeige), **Geräte in der Nähe / Bluetooth** (nur falls BLE-Sensoren gekoppelt sind), **Hintergrund-Standort („Immer zulassen")** sowie die Ausnahme von der **Akku-Optimierung**, damit Android lange Touren bei gesperrtem Display nicht abbricht.
- **Sportart (`Laufen`, `Radeln`, `Andere`)**: Solange nur GPS oder ein Brustgurt aktiv ist, steht die Auswahl auf *Andere*. Sobald sich ein Rad-Sensor (Speed, Trittfrequenz oder Wattmesser) verbindet, schaltet die App automatisch auf **Radeln**; bei einem Laufsensor auf **Laufen**. Du kannst die Sportart jederzeit auch manuell antippen.
- **Route wählen (unten)**: Lade optional eine GPX-Strecke aus einer Datei oder deinem Verlauf für die Karten- und Höhenprofil-Navigation.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/01_control_tracking.png"><img src="../img/01_control_tracking.png" width="240" alt="Start-Tab & Sportart-Auswahl" /></a>
  <br/><sub><em>Der Start-Tab vor Trainingsbeginn</em></sub>
</td>
</tr>
</table>

---

<a id="live-cockpit-overview"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 1.2 Das Live-Cockpit & Direktstart

Wischst du nach links (oder tippst oben auf **Standard**, **Karte** oder **Runden**), wechselst du in die Live-Datenansichten:

- **Direktstart aus dem Cockpit**: Falls du dich schon im Cockpit befindest, bevor die Aufzeichnung läuft, siehst du oben das blaue Banner **Nicht gestartet**. Mit einem Tipp auf **Start** im Banner kannst du die Aufzeichnung direkt hier starten, ohne zurück zum *Start*-Tab wechseln zu müssen.
- **Übersichtliche Datenkacheln**: Zeigt dir Live-Werte wie aktive Fahrzeit, Uhrzeit, Geschwindigkeit, Distanz, Herzfrequenz (inklusive farbigem Trainingszonen-Balken), Trittfrequenz, aktuelle Höhe und gesammelte Höhenmeter (*Hoch*).
- **Integrierte Fahrradklingel**: Über den orangenen **Klingel-Button** unten kannst du Fußgänger oder andere Radfahrer jederzeit per Smartphone-Lautsprecher warnen (kurz antippen für einzelnes Klingeln, gedrückt halten für Dauerklingeln).

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/01_cockpit_standard.png"><img src="../img/01_cockpit_standard.png" width="240" alt="Standard Live-Cockpit" /></a>
  <br/><sub><em>Live-Cockpit mit Direktstart-Banner & Klingel</em></sub>
</td>
</tr>
</table>

---

<a id="starting-and-pausing-workouts"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 1.3 Auto-Pause, Manuelle Pause & Beenden

Während der Fahrt kümmert sich **aTrainingTracker** automatisch um Ampelstopps und Pausen:

- **Automatische Pause (`Auto-Pause`)**: Sobald du anhältst und deine Geschwindigkeit unter die eingestellte Schwelle fällt, wechselt die App auf **Auto-Pause** und hält die aktive Trainingszeit an. Im Cockpit erscheint ein gelbes Banner – möchtest du im Stand trotzdem weiter aufzeichnen, tippe einfach auf **Jetzt fortsetzen**.
- **Manuell pausieren**: Um das Training selbst zu unterbrechen, wechsle auf den ersten Tab ganz links (der während der Fahrt **Tracking** heißt) und tippe auf das große **Pause**-Symbol.
- **Fortsetzen oder Beenden (`Stopp`)**: Sobald das Training pausiert ist, heißt der erste Tab **Pause** und zeigt zwei große Symbole nebeneinander:
  - **Fortsetzen (Play)**: Nimmt die Aufzeichnung wieder auf.
  - **Stopp (Quadrat)**: Beendet das Training und speichert die Einheit sofort in der lokalen Datenbank (und startet je nach Einstellung den automatischen Export).
- **Live-Segmente im Blick**: Näherst du dich unterwegs einem markierten Strava-Segment (im Bild unten: *„Zum Bäcker – Anpirschen"*), blendet sich am unteren Rand automatisch die Live-Segment-Vorschau mit Countdown und Höhenprofil ein.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/01_auto_paused.png"><img src="../img/01_auto_paused.png" width="240" alt="Auto-Pause & Live-Segment" /></a>
  <br/><sub><em>Auto-Pause-Banner & Live-Segment im Cockpit</em></sub>
</td>
</tr>
</table>

---

[Nächstes Kapitel: Sensoren & Hardware](02-sensors-dual-protocol.md) | [Zurück zur Übersicht](index.md)
