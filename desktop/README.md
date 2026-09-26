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
