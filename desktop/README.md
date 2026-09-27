# HardBass EQ – Desktop (Windows / Linux)

Dieses Modul ist **kein** eigenständiger Equalizer, sondern ein kleines
Compose-Desktop-Tool, das die vorhandenen HardBass-EQ-Presets in die
Konfigurationsformate von zwei bereits etablierten, system-weit wirkenden
EQ-Engines exportiert:

- **Windows:** [Equalizer APO](https://sourceforge.net/projects/equalizerapo/)
- **Linux (Debian/Ubuntu, PipeWire):** [EasyEffects](https://github.com/wwmm/easyeffects)

Beide Tools müssen vorher separat installiert sein – dieses Modul erzeugt nur
die passenden Preset-Dateien und schreibt sie an den richtigen Ort.

## Voraussetzungen

### Windows

1. [Equalizer APO](https://sourceforge.net/projects/equalizerapo/) installieren
   und dabei das gewünschte Ausgabegerät als Kanal auswählen.
2. Einmal neu starten (von Equalizer APO selbst verlangt).

### Linux (Debian/Ubuntu)

```
sudo apt install easyeffects
```

EasyEffects nutzt PipeWire; auf reinem PulseAudio-Setup (ohne
`pipewire-pulse`) funktioniert es nicht.

## Benutzung

```
./gradlew :desktop:run
```

öffnet die App. Preset auswählen, optional Bass/Punch/Härte per Slider
anpassen, Ziel-Plattform wählen (wird automatisch erkannt, ist aber
umschaltbar) und den Pfad zum config-Ordner bzw. Preset-Ordner bestätigen
oder anpassen:

- **Windows:** Equalizer APO config-Ordner, standardmäßig
  `%ProgramFiles%\EqualizerAPO\config`. Es wird eine eigene Datei
  `HardBassEQ.txt` geschrieben und einmalig, idempotent per `Include:`-Zeile
  in `config.txt` eingebunden – deine eigene Konfiguration bleibt unangetastet.
  Equalizer APO übernimmt Änderungen an `config.txt`/den inkludierten
  Dateien automatisch, ohne Neustart.
- **Linux:** EasyEffects-Preset-Ordner, standardmäßig
  `~/.config/easyeffects/output/`. Nach dem Export in EasyEffects unter
  *Presets* das neue Preset (Name = Preset-Name aus HardBass EQ) auswählen –
  das ist ein manueller Schritt, es gibt keinen dokumentierten, stabilen Weg,
  es von außen live zu erzwingen.

### Aktuellen Klang vom Android-Handy übernehmen

1. In der Android-App **Desktop-Profil exportieren** wählen und
   `HardBassEQ-Desktop.json` speichern. Bei aktiver Wiedergabe enthält die
   Datei auch die tatsächlich angewandten EQ-Bänder einschließlich manueller
   Änderungen. Ohne aktive Audio-Session werden Preset, Korrektur und Makros
   gespeichert; der Desktop berechnet daraus die Bänder.
2. Die Datei auf den Ubuntu-PC übertragen. Im Desktop-Tool den Dateipfad
   unter **Profil vom Handy übernehmen** eintragen und **Datei laden** wählen.
3. **Linux / EasyEffects** auswählen und das Preset exportieren. Danach das
   Preset in EasyEffects unter *Presets* aktivieren.

Der Linux-Export enthält die EQ-Kurve, den angewandten Input-Gain, die
Kopfhörerkorrektur und – sofern eingeschaltet – einen dreibändigen
Multiband-Kompressor und Limiter. Makro-Regler können nach dem Import weiter
angepasst werden; bei mitgelieferten Hardware-Bändern wird nur die Änderung
gegenüber dem Handywert zusätzlich aufgetragen. Ein ausgeschalteter oder
überbrückter Handy-EQ erzeugt eine leere Effektkette.

Der Export ist auf das Presetformat von EasyEffects 7.1.6 (Ubuntu 24.04)
ausgelegt. Die Android- und EasyEffects-DSP-Algorithmen sind verschieden;
identische Parameter garantieren deshalb keinen bitgenau gleichen Klang.
Der EasyEffects-Limiter erlaubt nur bis zu 20 ms Release; der Android-Wert
von 50 ms wird daher auf 20 ms begrenzt. Die schmaleren Bassfilter reduzieren
die Überlagerung benachbarter parametrischer Bänder.
Equalizer APO unter Windows erhält weiterhin nur die EQ-Kurve.

## Experimentelle Windows-Dynamik (Mehrband-Kompressor/Limiter)

Equalizer APO selbst bringt keinen Kompressor/Limiter mit, kann aber laut
mehrfach bestätigten Nutzerberichten VST2-Plugins laden und deren Parameter
über Textzeilen in der Config setzen. Im Windows-Panel der Desktop-App gibt
es dafür eine Checkbox **"Experimentell: Kompressor/Limiter-Referenz
(ReaComp-VST)"** (standardmäßig aus), die zusätzlich eine
`HardBassEQ-Dynamics.txt` schreibt und separat per eigener `Include:`-Zeile
einbindet - unabhängig von der normalen EQ-Kurven-Datei, damit sie sich
jederzeit einzeln wieder rausnehmen lässt.

**Wichtig: diese Datei ändert erstmal nichts an deinem Klang.** Jede
`VST:`/`VSTPlugin:`-Zeile darin ist auskommentiert (`#`) - stattdessen stehen
dort als Klartext-Kommentar die aus deinem aktuellen Preset berechneten
Soll-Werte (Threshold/Ratio/Attack/Release für Kompressor und Limiter). Der
Grund: die genaue Equalizer-APO-VST-Automatisierungssyntax und die exakten
Parameter-Namen/Wertebereiche von [ReaComp](https://www.reaper.fm/reaplugs/)
(kostenlos, Teil der ReaPlugs VST FX Suite, keine Installation nötig) sind
für die jeweils installierte Version nicht offiziell dokumentiert - das
wurde ohne Zugriff auf einen echten Windows-Rechner mit Equalizer APO/ReaComp
erstellt und ist entsprechend unverifiziert. Eine falsche Zeile könnte
Equalizer APO dazu bringen, config.txt gar nicht mehr zu parsen (stumme
Systemaudioausgabe) - deshalb ist nichts davon von sich aus aktiv.

**So verifizierst/aktivierst du es:**

1. [ReaPlugs VST FX Suite](https://www.reaper.fm/reaplugs/) herunterladen,
   `reacomp.dll` in den Equalizer-APO-config-Ordner kopieren (Dateiname
   gegen die tatsächlich heruntergeladene Datei prüfen - Groß-/
   Kleinschreibung kann abweichen).
2. Equalizer APOs eigenen **Configuration Editor** öffnen, das Plugin dort
   über die GUI einmal manuell hinzufügen und die in `HardBassEQ-Dynamics.txt`
   als Kommentar angegebenen Werte (Threshold/Ratio/Attack/Release) von Hand
   eintragen.
3. Danach in der gespeicherten `config.txt` nachschauen, welche
   `VSTPlugin:`-Zeile/Parameter-Namen/Wertebereiche der Configuration Editor
   tatsächlich geschrieben hat.
4. Erst wenn Schritt 3 zeigt, dass unsere generierte Zeile (Syntax,
   Parameter-Namen, Wertskala) dazu passt, die entsprechende `#`-Zeile in
   `HardBassEQ-Dynamics.txt` einkommentieren - sonst lieber die von Hand über
   den Configuration Editor gefundene Syntax dauerhaft nutzen.

ReaComp ist ein Einzelband-Kompressor; der App-eigene 3-Band-MBC wird deshalb
nur als eine einzelne Breitband-Stufe angenähert (mit den Attack/Release-
Werten des mittleren Bands aus `AndroidAudioEngine.kt` als Mittelweg). Für
den Limiter gibt's in ReaComp keinen echten Brickwall-Modus - eine harte
Ratio (20:1) nähert das an.

## Installer selbst bauen

CI baut nur Kompilierung + Tests, keine fertigen Installer – `jpackage`
braucht dafür einen passenden Host (MSI nur unter Windows mit installiertem
[WiX Toolset](https://wixtoolset.org/), DEB nur unter Linux mit `dpkg`).
Lokal auf der jeweiligen Zielplattform:

```
# Windows
./gradlew :desktop:packageMsi

# Linux (Debian/Ubuntu)
./gradlew :desktop:packageDeb
```

Die fertigen Pakete landen unter `desktop/build/compose/binaries/`.
