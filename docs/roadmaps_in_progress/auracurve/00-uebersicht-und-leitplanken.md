# AuraCurve · Gesamtübersicht & Leitplanken

**Projekt:** AuraCurve (ehemals *HardBass EQ* / *Baschtls Gedröhne*)  
**Stand:** 15.11.2026 (überarbeitet nach Freigabe)  
**Bezug:** Gehört zu [`eq-review.md`](eq-review.md) (dort stehen die technischen Befunde mit Datei und Zeile).  
**Ablage:** `docs/roadmaps_in_progress/auracurve/`. Vorhanden sind Teil 00 bis 04 (02 mit Anschreiben-Entwurf `soundcloud-anschreiben-entwurf.md`) und der Review `eq-review.md`; 05 und 06 folgen. Die älteren Roadmaps in `docs/roadmaps_in_progress/` (siehe `../00-INDEX.md`) laufen unabhängig weiter.

---

## Marken-Steckbrief (Freigabe vom 15.11.2026)

| Element | Festlegung |
|---|---|
| **App-Name** | **AuraCurve** |
| **Slogan** | **Feel the bass. Rule the sound.** |
| **Fußnote** | Music player, bass enhancement, advanced EQ. |
| **Ersetzt** | „HardBass EQ“ (Repository, README, Dokumente) & „Baschtls Gedröhne“ (`app_name`) |
| **Optik / UI** | Alle Entwürfe (`screenshots/01` bis `04`) sind freigegeben und werden 1:1 umgesetzt. |
| **Play Store Banner** | Bild 06 mit Überschrift **„Bass you can see and steer.“**, AuraCurve darüber, Slogan & Fußnote darunter (ersetzt Bild 05). |

---

## Struktur der Teil-Roadmaps

1. **`01-marke-und-pr74.md`** – Markenprüfung, Rebranding & Abschluss Pull Request #74 *(Phase M & Phase 0)*
2. **`02-soundcloud-vorbereitung.md`** – Build-Varianten, Paketnamen & SoundCloud-Anfrage *(Phase 1)*
3. **`03-sicherheit-und-geraetetests.md`** – Token-Sicherheit, Keystore & Hardware-Testmatrix *(Phase 2 & Phase 3)*
4. **`04-ui-und-queue-feinschliff.md`** – Umsetzung der freigegebenen Optik, Lokalisierung & Queue-Optimierung *(Phase 4 & Phase 5)*
5. **`05-play-store-release.md`** – Google Play Console, Datenschutz, geschlossener Test & Rollout *(Phase 6)*
6. **`06-post-launch-vision.md`** – Weiterentwicklung nach dem Release *(Phase 7)*

---

## Goldene Regel für die Reihenfolge

> **Erst alles, was die Antwort von SoundCloud beeinflusst, dann alles, was von dieser Antwort abhängt.**  
> Die Anfrage bei SoundCloud hat die längste Wartezeit und entscheidet, wie der Player im Store aussehen darf. Deshalb steht sie vor dem großen UI-Umbau.
>
> **Ausnahme (15.11.2026):** Der Equalizer-Umbau (Roadmap 04, Teil A) hängt nicht von SoundCloud ab und kommt **vor** dem Video für die Anfrage, damit es die Kurve zeigt. Player-Bibliothek und Suche (Teil B) bleiben dahinter.

---

## Aufwands-Legende

- **S** = bis ein halber Tag
- **M** = ein bis drei Tage
- **L** = eine Woche und mehr
- **Wer:** *du* (Entscheidung/Gerätetest) vs. *Umsetzung* (Code/Assets)

---

## Was wir bewusst NICHT tun (Anti-Patterns)

- **Die heutige Version an SoundCloud schicken:** Belegt die Nutzung ohne Erlaubnis.
- **Mit dem SoundCloud-Teil in den Store gehen, bevor die Antwort da ist:** Eine Beschwerde kann die App und im Wiederholungsfall das Entwicklerkonto kosten.
- **Die Optik vor dem Gerätetest umbauen:** Entwürfe sind zwar freigegeben, aber sonst stapeln sich ungeprüfte Änderungen.
- **Umbenennen, bevor der Name geprüft ist:** Eine ältere Marke oder gleichnamige App würde die Arbeit doppelt kosten.
- **Mit „systemweit“ werben:** Android erlaubt das nur eingeschränkt; die Aussage zieht schlechte Bewertungen nach sich.
