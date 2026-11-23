# Checkliste: Klangqualität bei sehr kräftigem Bass (System-EQ)

Roadmap 13, P13-4. AuraCurve arbeitet auf dem Signal, das andere Apps und das Gerät liefern. Was
davor oder danach schon verbogen wurde, kann der EQ nicht mehr reparieren – diese Punkte vor
einem Hörtest (oder bei „klingt matschig / pumpt“) prüfen.

## Spotify (und andere Streaming-Apps)

| Einstellung | Empfehlung | Warum |
| --- | --- | --- |
| Lautstärke normalisieren | **Aus** (wenn an: nicht „Laut“) | „Laut“ legt einen eigenen Limiter/Kompressor davor – der Bass pumpt doppelt. |
| Audioqualität (WLAN/Mobil/Download) | **Sehr hoch (320 kbps)** | Bei niedriger Bitrate fehlen Höhen und Transienten, die der EQ nicht zurückholt. |
| Spotify-Equalizer | **Aus** | Zwei EQs übereinander summieren Anhebungen und fressen Headroom. |

## Kopfhörer-Hersteller-App (Sony, Samsung, Nothing, JBL, Bose …)

- EQ auf **Flat / Aus**.
- **Clear Bass, Bass Boost, Dolby Atmos, 360 Audio, „Adaptive Sound“** aus – sie verstärken den
  Bass zusätzlich *hinter* AuraCurve, also dort, wo kein Limiter mehr greift.

## Bluetooth-Codec

- In den Entwickleroptionen bzw. der Hersteller-App prüfen: **LDAC** oder **aptX Adaptive / HD**
  statt **SBC**. SBC verliert bei dichtem Bass + Höhen hörbar Auflösung (Hi-Hats „zischeln“).
- LDAC auf „Beste Qualität (990 kbps)“ oder „Adaptiv“, nicht „Verbindung bevorzugt“.

## In AuraCurve

- Diagnose-Bericht prüfen: `Has Multiband Compressor: true`, `MBC Bands: 5` und – sofern das Gerät
  es zulässt – `Post-EQ: 34-band`. Mit `MBC Bands: 4` oder `Post-EQ: not in use` läuft der
  Rückfallpfad (Gerät lehnt die größere Konfiguration ab).
- Für +8 bis +15 dB Bass das Klangziel **„Clear bass“** probieren (Mud-Dip um 250 Hz, Zügelung um
  4,5 kHz, etwas „Air“ ab 10 kHz).
- **Subsonic-Filter** an: 25–30 Hz für Kopfhörer, 35–45 Hz für Auto und Bluetooth-Box (die
  Kontext-Modi setzen das bereits).
