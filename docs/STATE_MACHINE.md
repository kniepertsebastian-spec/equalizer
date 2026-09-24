# Zustandsautomat: `AudioEngineState`

Stand: Sprint-0-Punkt 3 (`roadmap-2026.md` §8, Punkt 3: „Zustandsautomat für
`LostControl` und `Retrying` spezifizieren"). Dieses Dokument ist eine
**Spezifikation**, keine Implementierung. Es legt den Ziel-Zustandsautomaten
für M1 fest (`roadmap-2026.md` §"M1 – Zuverlässiger Audio-Pfad", Aufgabe:
„Zustandsautomat für `Detached`, `Listening`, `Attaching`, `Active`,
`LostControl`, `Retrying` und `Unsupported` einführen.") und dient als
Grundlage für die M1-Umsetzung in `AudioEngineState.kt` und
`AndroidAudioEngine.kt`. Codeänderungen an diesen Dateien sind bewusst
**nicht** Teil dieses Dokuments/dieser Branch.

## 1. Ist-Zustand (Stand dieser Session)

`AudioEngineState.kt` definiert aktuell 7 Zustände:

```kotlin
sealed interface AudioEngineState {
    data object Detached : AudioEngineState
    data class Attaching(val sessionId: Int) : AudioEngineState
    data class Active(val sessionId: Int) : AudioEngineState
    data class Suspended(val reason: String) : AudioEngineState
    data class LostControl(val reason: String) : AudioEngineState
    data class Unsupported(val reason: String) : AudioEngineState
    data class Error(val message: String) : AudioEngineState
}
```

`AndroidAudioEngine.kt` setzt `_state.value` aktuell nur an 5 Stellen:

| Stelle | Zustand | Auslöser |
|---|---|---|
| `attachLocked()`, Anfang | `Attaching(sessionId)` | Neue Session vom `AudioSessionForegroundService` gemeldet |
| `attachLocked()`, nach Erfolg | `Active(sessionId)` | `Equalizer`/`DynamicsProcessing` erfolgreich erzeugt, gefolgt von `applyInternal()` |
| `attachLocked()`, catch-Block | `Error(...)` | Exception beim Erzeugen der Effekte; danach sofort `detachInternal()` |
| `detach()` | `Detached` | `AudioSessionForegroundService` meldet `session == null` |
| `applyInternal()`, catch-Block | `LostControl(...)` | Exception beim Anwenden von Settings auf eine bereits aktive Session |

Daraus folgt:

- **`Suspended` und `Unsupported` sind aktuell tote Zustände.** Sie werden in
  `EqualizerScreen.kt` (Status-Chip, Zeilen ~124–133) als UI-Fälle behandelt,
  aber von keiner Stelle im Audio-Layer jemals erzeugt (verifiziert per
  Grep über `app/src` nach `AudioEngineState\.(Suspended|Unsupported)`).
- **Es existiert kein automatisches Re-Attach/Retry.** Nach `LostControl`
  oder `Error` passiert nichts von selbst — die Engine wartet passiv auf die
  nächste Session-Broadcast-Meldung von `AudioSessionForegroundService`
  (`sessionRepository.activeSession`-Flow).
- **`Listening` und `Retrying` existieren nicht.** Der aktuelle `Detached`-
  Zustand deckt sowohl „Service läuft nicht/hört nicht zu" als auch „Service
  hört aktiv zu, aber keine Session gefunden" ab — das ist die Lücke, die
  Punkt 3 der Roadmap schließen soll.
- **`AudioSessionForegroundService` läuft praktisch immer.**
  `HardBassEqApplication.onCreate()` startet ihn unbedingt beim Prozessstart
  (`ContextCompat.startForegroundService(...)`, kein Bezug zu
  `masterEnabled`). `AudioSessionForegroundService.onCreate()` ruft sofort
  `sessionRepository.startListening()` und `routeRepository.startMonitoring()`
  auf und reicht `sessionRepository.activeSession` 1:1 an
  `audioEngine.attach(session)`/`audioEngine.detach()` durch. D. h. echtes
  „nicht zuhören" (`Detached` im Zielmodell, siehe unten) ist praktisch nur
  das kurze Fenster zwischen Prozessstart und dem Abschluss von
  `Service.onCreate()` — danach ist die App immer entweder aktiv am
  Zuhören oder an eine Session angebunden.

## 2. Ziel-Zustandsmenge (M1)

| Zustand | Payload | Bedeutung |
|---|---|---|
| `Detached` | – | Kein aktives Zuhören. Service noch nicht initialisiert (Kaltstart-Fenster) oder bewusst gestoppt. Kein Timer, kein Netzwerk-/Broadcast-Empfang. |
| `Listening` | – | Service läuft, `AudioSessionRepository`/`AudioRouteRepository` hören aktiv zu, aber aktuell ist keine Session angebunden. Entspricht dem, was der heutige Code fälschlich `Detached` nennt. |
| `Attaching` | `sessionId: Int` | Erster Attach-Versuch für eine neu gemeldete Session (Versuchszähler = 0, kein Backoff). |
| `Active` | `sessionId: Int` | Session angebunden **und** letzter `apply()`-Aufruf erfolgreich. Einzig zulässiger Zustand für die UI-Aussage „Aktiv". |
| `LostControl` | `sessionId: Int`, `reason: String` | Kurzlebiger Übergangszustand: `apply()` auf einer zuvor aktiven Session ist fehlgeschlagen. Löst sofort (selber Tick) das Planen des ersten Retry-Versuchs aus → Übergang nach `Retrying`. |
| `Retrying` | `sessionId: Int`, `attempt: Int`, `nextRetryAtMillis: Long` | Begrenzter Re-Attach-Versuch mit Backoff läuft (siehe §3). |
| `Unsupported` | `reason: String` | Gerät/Player stellt keinen geeigneten Effekt-Pfad bereit (z. B. `Equalizer`/`DynamicsProcessing` für diesen Typ nicht instanziierbar). Wird **nicht** automatisch erneut versucht — erst bei einer neuen, andersartigen Session-Meldung. |
| `Error` | `message: String` | Retry-Budget erschöpft oder nicht behebbarer Fehler. Erfordert eine explizite nächste Handlung (roadmap-2026.md M1-Aufgabe: „Fehler: konkrete nächste Handlung anbieten"). |

**Entscheidung: `Suspended` entfällt.** Es ist im M1-Aufgaben-Zitat nicht
enthalten, aktuell unerreichbar und deckt keinen Fall ab, der nicht bereits
durch `Detached`/`Listening` (kein Nutzerwunsch nach Wiedergabe) oder
`Unsupported` (Gerät kann nicht) abgedeckt wäre. Empfehlung für die
M1-Umsetzung: Zustand und den zugehörigen toten UI-Zweig in
`EqualizerScreen.kt` ersatzlos streichen.

**`Error` bleibt erhalten**, obwohl die Roadmap-Zeile in §"M1 – Aufgaben"
ihn nicht in der Zustandsaufzählung nennt — die UI-Statusliste im selben
Abschnitt fordert aber explizit einen „Fehler"-Status mit konkreter nächster
Handlung. `Error` ist dafür die naheliegende 1:1-Abbildung und existiert
bereits im Code; hier wird er als Ziel-Zustand bestätigt statt neu erfunden.

## 3. Übergangstabelle

| Von | Ereignis | Nach | Anmerkung |
|---|---|---|---|
| `Detached` | Service-`onCreate()` abgeschlossen, `startListening()`/`startMonitoring()` aufgerufen | `Listening` | Einmalig beim Prozess-/Service-Start. |
| `Listening` | `activeSession` liefert Session | `Attaching(sessionId)` | Versuchszähler wird auf 0 initialisiert. |
| `Attaching` | Effekte erfolgreich erzeugt + `applyInternal()` erfolgreich | `Active(sessionId)` | Regulärer Erfolgspfad, wie heute. |
| `Attaching` | Erzeugung der Effekte scheitert, Fehlerursache deutet auf fehlende Geräte-/Player-Fähigkeit hin (z. B. `Equalizer`/`DynamicsProcessing` nicht verfügbar für diesen Effekttyp) | `Unsupported(reason)` | Neu: heute landet das unspezifisch in `Error`. Abgrenzung zu `Retrying` unten. |
| `Attaching` | Erzeugung der Effekte scheitert aus anderem Grund (z. B. transiente `IllegalStateException`, Session bereits wieder weg) | `Retrying(sessionId, attempt=1, ...)` | Neu: heute landet das direkt in `Error` ohne Retry. |
| `Active` | `apply()` schlägt fehl | `LostControl(sessionId, reason)` | Wie heute (`applyInternal()`-catch), aber mit `sessionId` im Payload statt nur `reason`. |
| `LostControl` | (sofort, selber Tick) | `Retrying(sessionId, attempt=1, ...)` | Kein eigener Verweilzustand; dient nur der UI-Unterscheidung „gerade eben verloren" vs. „versuche es erneut". |
| `Retrying` | Backoff-Timer abgelaufen, Re-Attach erfolgreich | `Active(sessionId)` | Versuchszähler wird verworfen. |
| `Retrying` | Backoff-Timer abgelaufen, Re-Attach scheitert, `attempt < MAX_ATTEMPTS` | `Retrying(sessionId, attempt+1, ...)` | Nächstes Intervall gemäß §4. |
| `Retrying` | Backoff-Timer abgelaufen, Re-Attach scheitert, `attempt == MAX_ATTEMPTS` | `Error(message)` | Budget erschöpft, siehe §4. |
| `Retrying` | `activeSession` liefert eine **andere** `sessionId` (Player-Wechsel während des Wartens) | `Attaching(neueSessionId)` | Zähler wird verworfen; neue Session bekommt einen frischen Versuch, kein „vererbter" Backoff. |
| `Retrying`/`Attaching`/`Active`/`LostControl` | `activeSession` liefert `null` (Session sauber beendet) | `Listening` | Laufender Backoff-Timer wird abgebrochen. |
| `Error` | `activeSession` liefert eine neue Session (beliebige `sessionId`) | `Attaching(sessionId)` | Ein neuer Session-Broadcast ist automatisch ein neuer Versuch — kein manueller Reset nötig, da eine neue Session ohnehin einen frischen Kontext darstellt. |
| `Unsupported` | `activeSession` liefert eine neue Session | `Attaching(sessionId)` | Gleiche Logik: ein anderer Player/eine andere Session kann durchaus unterstützt sein, auch wenn der vorherige es nicht war. |
| beliebig | Service wird beendet (`onDestroy()`) | `Detached` | Nur relevant für Prozessende/Service-Neustart, nicht Teil des Alltagsbetriebs. |

Wichtig für die Abnahmekriterien aus `roadmap-2026.md` M1: In keinem Pfad
oben wird `Active` erreicht, ohne dass der vorangehende `apply()`-Aufruf
tatsächlich erfolgreich war — „Die UI behauptet nie „Aktiv", wenn `apply()`
fehlgeschlagen ist" ist damit strukturell erfüllt, nicht nur per Konvention.

## 4. Backoff-Policy für `Retrying`

Ziel (Roadmap-Wortlaut): „Begrenztes Re-Attach mit Backoff implementieren;
keine Endlosschleife und kein Akku-Spam."

- **`MAX_ATTEMPTS = 5`.** Danach → `Error`, keine weiteren automatischen
  Versuche mehr für diese Session.
- **Intervalle (exponentiell, gedeckelt):** `2s, 4s, 8s, 16s, 30s`. Versuch
  `n` wartet `intervals[n-1]` ab `Retrying`-Eintritt, bevor er ausgeführt
  wird. Der Deckel bei 30s verhindert, dass ein einzelner Retry-Zyklus
  unbegrenzt wächst.
- **Kein Jitter nötig:** Es gibt keinen zentralen Dienst, gegen den viele
  Client-Instanzen gleichzeitig retryen (jede Engine-Instanz reagiert nur
  auf lokale Broadcasts derselben App) — ein klassisches Thundering-Herd-
  Szenario entfällt.
- **Zähler-Reset:** Ausschließlich bei Wechsel auf eine andere `sessionId`
  oder bei erfolgreichem Re-Attach (`Active`). Ein Backoff-Zyklus für
  dieselbe `sessionId` läuft nie „von vorne" — das wäre der Akku-Spam-Fall,
  den die Roadmap ausdrücklich ausschließt.
- **Timer-Abbruch:** Jeder laufende Backoff-Timer muss abgebrochen werden,
  sobald `Listening` (Session weg) oder eine neue `sessionId` (Player-
  Wechsel) eintritt — sonst kann ein verspäteter Retry eine inzwischen
  überholte Session anfassen. Umsetzungsdetail für M1: der bestehende
  `Mutex`-serialisierte `attach()`/`detach()`/`apply()`-Pfad in
  `AndroidAudioEngine.kt` ist bereits die richtige Stelle dafür, da er schon
  seriellen Zugriff auf `_state` garantiert.
- **`Unsupported` wird nie automatisch retried.** Ein Capability-Problem
  löst sich nicht durch Warten; erneutes Probieren ist reine Akkuverschwendung.
  Nur eine neue Session (potenziell anderer Player/andere Capability) darf
  aus `Unsupported` herausführen (siehe Übergangstabelle).

## 5. UI-Status-Mapping

Die Roadmap fordert für M1 genau 4 UI-Status (Aktiv/Wartet/Nicht
unterstützt/Fehler). Abbildung der 8 Engine-Zustände:

| Engine-Zustand | UI-Status | Beispieltext (Chip) |
|---|---|---|
| `Active` | **Aktiv** | „Aktiv (Session #123)" — wie heute. |
| `Detached` | **Wartet** | „Startet…" (nur im kurzen Kaltstart-Fenster sichtbar). |
| `Listening` | **Wartet** | „Wartet auf Audio-Session" — heutiger `Detached`-Text, unverändert. |
| `Attaching` | **Wartet** | „Anbinden... (#123)" — wie heute. |
| `LostControl` | **Wartet** | Sehr kurzlebig (siehe §3); falls überhaupt sichtbar, z. B. „Verbindung verloren, versuche erneut…". |
| `Retrying` | **Wartet** | „Erneuter Versuch in {sekunden}s (Versuch {attempt}/5)" — macht die Abnahmekriterium-Anforderung „kein dauerhafter Kontrollverlust" für Nutzer sichtbar nachvollziehbar. |
| `Unsupported` | **Nicht unterstützt** | „Nicht unterstützt: {reason}" — heute ohne Grund im Text, sollte `reason` mit ausgeben. |
| `Error` | **Fehler** | „Fehler: {message}" — wie heute. Nächste Handlung (Roadmap-Anforderung) z. B. als zusätzlicher Button „Erneut versuchen", der den Versuchszähler manuell zurücksetzt und nach `Attaching` wechselt. |

`Detached`, `Listening`, `Attaching`, `LostControl` und `Retrying` teilen
sich alle den UI-Status „Wartet" — sie unterscheiden sich nur im
Chip-Text/Detail, nicht in Farbe/Kategorie. Das hält die UI-Statusmenge
exakt bei den geforderten 4 Kategorien, ohne die für Diagnose/Debugging
nützliche feinere Unterscheidung auf Engine-Ebene zu verlieren (die z. B.
`DiagnosticsRecorder` weiterhin pro Zustand mitschreibt, siehe
`AudioSessionForegroundService.onCreate()`, `audioEngine.state.collect`).

## 6. Bewusst nicht Teil dieser Spezifikation

- Die konkrete Kotlin-Implementierung in `AudioEngineState.kt` und
  `AndroidAudioEngine.kt` (M1-Umsetzung).
- Die vier Start-Reihenfolge-Tests auf echten Geräten
  (`roadmap-2026.md` §8, Punkt 4) — benötigt reale Hardware.
- Die eigentliche Re-Attach-Implementierung (`roadmap-2026.md` §8, Punkt 7)
  — laut Backlog-Reihenfolge bewusst erst nach Punkt 4.
- Die produkt-technische A/B/C-Entscheidung (Session-EQ vs. eigener Player,
  M1-Aufgabe „Technische Produktentscheidung dokumentieren") — das ist
  `docs/DECISIONS.md`, nicht dieses Dokument.
