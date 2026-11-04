# Kapitel 5: Karten, GPX-Routen, Höhenprofile & Strava Live Segmente

<a id="gpx-import-and-map-navigation"></a>
## 5.1 GPX-Routen importieren & Streckenführung

Fahren oder laufen Sie geplante Strecken mit intuitiver Orientierung ab:

- **Einfacher Import**: Importieren Sie `.gpx`-Dateien direkt aus Downloads, Messengern oder Tourenportalen (Komoot, Strava, RideWithGPS).
- **Offline-Vektorkarten**: Speichern Sie OpenStreetMap-Kartenkacheln offline auf dem Gerät, um auch in abgelegenen Bergregionen ohne Mobilfunkempfang navigieren zu können.
- **Folgemodus (*Follow Track*)**: Die geplante Route wird in Royal-Blau über die Karte gelegt. Ihr aktueller Standort wird mit Fahrtrichtungspfeil dargestellt.

![Karten-Cockpit & Navigation](../img/05_map_cockpit.png)

---

<a id="interactive-climb-elevation-profile"></a>
## 5.2 Interaktives Höhenprofil & Steigungskategorien

Entlang der Route analysiert aTrainingTracker das Geländeprofil:

- **Dynamische Skalierung**: Das Höhenprofil passt seine vertikale Höhe automatisch an das tatsächliche Relief der Strecke an (von 70 dp im Flachland bis 200 dp im Hochgebirge).
- **Steigungs-Farbcodierung**: Flache Abschnitte erscheinen grün, moderate Anstiege gelb, steile Rampen ($> 10\%$) leuchtend rot.
- **Climb-Kategorien**: Signifikante Pässe und Steigungen werden automatisch nach offiziellen Radsport-Kriterien klassifiziert (*CAT 4*, *CAT 3*, *CAT 2*, *CAT 1*, *HC*).

![GPX-Routen & Höhenprofile](../img/05_gpx_routes.png)

---

<a id="strava-live-segments"></a>
## 5.3 Strava Live Segmente in Echtzeit

Messen Sie sich während der Fahrt mit Bestzeiten:

- **Automatische Annäherungswarnung**: Sobald Sie sich einem Strava-Segment nähern, blendet aTrainingTracker das Segment-Cockpit ein.
- **Echtzeit-Vergleich**: Verfolgen Sie Sekunde für Sekunde Ihren Vorsprung oder Rückstand auf Ihre persönliche Bestzeit (*PR*), den Streckenrekord (*KOM/QOM*) oder Ihr gesetztes Zeitziel.
- **Lineare Projektion**: Kontinuierliche Interpolation verhindert ruckelnde Distanzsprünge auf geraden Segmenten.

![Strava Live Segmente](../img/05_live_segments.png)

---

[Vorheriges Kapitel: Live-Cockpit](04-live-cockpit-tabs.md) | [Nächstes Kapitel: Lieblingsstrecken & Clustering](06-automatic-route-clustering.md)
