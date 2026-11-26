# Import-Presets

Fertige AuraCurve-Presets zum Importieren. Jedes Preset gibt es als Link (`*.link.txt`) und als JSON (`*.json`, gleiche Daten, lesbar).

## Importieren

- **Link tippen:** Ein `auracurve://`-Link öffnet die App direkt und importiert das Preset.
- **Link einfügen:** Link kopieren, dann im Equalizer auf **Paste link** tippen.

Importierte Presets landen als eigene Presets in der App und können dort weiter angepasst werden.

## Kleiner BT-Speaker

Für kleine Bluetooth-Speaker der Klasse JBL Go/Clip/Flip oder Bose SoundLink Micro. Solche Speaker haben 40–50-mm-Breitbänder, die zwischen 80 und 100 Hz abfallen, und einen eigenen DSP-Limiter.

| Bereich | Einstellung | Warum |
|---|---|---|
| unter 70 Hz | 0 dB, Subsonic-Filter 70 Hz | Bass, den der Treiber nicht spielt, löst nur den Limiter des Speakers aus. Dann „pumpt“ der ganze Mix. |
| 100–150 Hz | bis +4,5 dB (Peak 120 Hz) | Dort bewegt der Treiber noch Luft, das gibt Körper und Kick. |
| 450 Hz | −2,5 dB | Kleine Gehäuse klingen hier „boxig“ und nasal. |
| 2–3,5 kHz | +1 / +0,5 dB | Stimmen werden klarer, aber nur leicht: Bei hoher Lautstärke werden kleine Treiber hier schnell scharf. |
| 6–14 kHz | +1,5 bis +2,5 dB | Gleicht den Höhenabfall von Breitbändern aus und bringt Luft. |
| Dynamik | Kompressor −12 dB / 4:1, Limiter −1 dB | Schützt die Membran bei hoher Lautstärke. |
| Virtual Bass | 0,85 | Ergänzt per Obertönen den Bass, den der Speaker nicht spielt (nur im eingebauten Player). |
| Loudness | max. +8 dB | Klingt auch leise noch voll. |

Link:

```
auracurve://preset?v=1&d=hZLRa8IwEMb_lZHnKrVa5_qoGziGMDbfxh6u7dWGpcmWXMRN_N-XaBHaBnxLLr_7vtzxHRkvWcZYxCQ06E4vArlEfbfcjt6_Eb5QuzcCvUNaWb13yMeRVRp_LMrid_3Hsnk8jiO2Ay4fc5a5yynqEQ8dYjJOB8Qk6SDTEHLfQZKAzzTuIKNJgJmlXSYJWcXxzZmSHhPymqZ9naHXfKAT_k9v-AA0G0Dx6TNiDRRaLcGYdpK28mplUbcfb0trQE14xc7ihrBcI5Raqca_zMapo_PiSUIu0EWHtMVzZVtrNLUS5WX1yVk2L96AuPJ97ip4w8kFKjsy7PZTt9nvkjVIUAKBx3cotU9nLiySUlSPzDWcueWCniXLKhAGXaNQtpRozAYOS6UMec2F9zc2N0ryYmVJVZVf2iVTe67JgvBL2vCDH3-Rnv4B
```

## Soundbar ohne Sub

Für hochwertige Soundbars ohne Subwoofer der Klasse Sonos Arc/Beam, Bose Smart Soundbar oder Sennheiser Ambeo. Diese Bars kommen bis etwa 45–50 Hz runter, haben dort aber wenig Hub.

| Bereich | Einstellung | Warum |
|---|---|---|
| unter 40 Hz | ~0 dB, Subsonic-Filter 35 Hz | Ohne Sub läuft hier der Bass-Limiter der Bar an. Rumpeln, das sie nicht wiedergeben kann, wird entfernt. |
| 60–120 Hz | bis +3,8 dB (Peak ~70 Hz) | Die tiefste Oktave, die die Bar auch laut sauber spielt, plus Kick-Körper. |
| 200–300 Hz | bis −2,5 dB | Steht die Bar an der Wand oder im TV-Möbel, dröhnt es hier (Raumgrenzen-Effekt). Das bleibt so straff. |
| 1–6 kHz | nahezu linear | Gute Hochtöner und eine präsente, auf Dialoge abgestimmte Mitte sind schon da. |
| 10–16 kHz | +1,5 bis +2 dB | Luft, die abseits der Mittelachse sonst verloren geht. |
| Dynamik | Kompressor −10 dB / 3:1, Limiter −0,8 dB | Viel Dynamik, trotzdem sicher. |
| Virtual Bass | 0,45 | Füllt dezent unterhalb der Bar-Grenze auf (nur im eingebauten Player). |
| Loudness | max. +7 dB | Voll auch bei Filmabend-Lautstärke. |

Link:

```
auracurve://preset?v=1&d=jZJNawIxEIb_iuS8SlZdW_aoLdiDUGpvpYfJZtYNZBOaD7EV_3uTsJSmLtRbMvPMO5PMeyaCk5qQgijoMZz22ivOwEx0p3Cy9yykHJgDuo03x0C8nUlr8MOjaj63X6Re0BktyAGEemCkDpdL8YdYVr-JcoRYZcRihChp1mY-hqz-nWReZch0TKbKO03prBqb5t9eiyvmWmd1A1PO6U1vz6EyKL0XpIfG6DVYO0w5RJ69arphGUNoC2gc_mBJ3DrkWwRutO5jZplo1jwqYBKDb5zxmCKvnUHbacnTl5V0AF_ACZ0WWhApeuHQkPpMMK93WTGd3V9CMTrg4CDiB1QmWtMO1gyOZF5I96RI3YK0GHipPVdo7Q5Oa62ti1J3sa31zGolmo13um3TYpLZjsI4DzL-zU6c4quX1eUb
```

## Hinweise

- Beide Presets sind genre-neutral. Den Hörmodus „Bluetooth speaker“ bzw. „Soundbar“ dazu besser ausschalten, sonst wird doppelt korrigiert.
- Die Werte sind auf die Gerätegattung abgestimmt, nicht auf ein Messmodell. Dröhnt oder pumpt es, zuerst den Punch-Regler senken.
