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

## Was NICHT übernommen wird

Die Android-App nutzt zusätzlich einen Multiband-Kompressor und Limiter
(siehe `roadmap.md`, Sessions 14–17) als Sicherheitsnetz gegen Clipping bei
angehobenen Bässen. Beide Desktop-Engines sind hier nur als reine
parametrische EQs angebunden – ohne Dynamikverarbeitung. Um trotzdem sicher
vor Clipping zu sein, wird der Preamp/Output-Gain hier **vollständig**
(nicht nur anteilig wie auf Android) um den positiven Spitzenpegel abgesenkt.
Das ist konservativer, aber ohne Kompressor/Limiter die richtige Wahl.

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
