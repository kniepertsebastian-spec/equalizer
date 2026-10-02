# ADR 0002: Hilt/KSP tatsächlich eingerichtet – verwendete Versionen

Status: Akzeptiert (Build-Ergebnis: siehe `docs/TEST_MATRIX.md` nach dem
nächsten CI-Lauf)
Datum: 18. September 2026
Kontext: M1 – Projektfundament, Folge-Schritt zu ADR 0001

## Entscheidung

Der in ADR 0001 skizzierte Workaround wurde umgesetzt:

| Komponente | Version | Begründung |
|---|---|---|
| Kotlin (`kotlin-android` + `kotlin.plugin.compose`) | `2.3.20` | Neueste Kotlin-Version, die KSP `2.3.12` laut dessen eigener Build-Konfiguration unterstützt. Downgrade von zuvor `2.4.20`. |
| `android.builtInKotlin` | `false` (in `gradle.properties`) | KSP unterstützt AGP 9s eingebautes Kotlin noch nicht. |
| `android.newDsl` | `false` (in `gradle.properties`) | **Zweiter, separater Schalter** – `builtInKotlin=false` allein reicht nicht: AGP 9s "new DSL" ist per Default aktiv und lässt `org.jetbrains.kotlin.android` unabhängig davon mit `ClassCastException` fehlschlagen. Erst durch einen echten CI-Fehlschlag entdeckt (siehe „Korrektur" unten), nicht vorab in der Recherche gefunden. |
| KSP (`com.google.devtools.ksp`) | `2.3.12` | Aktuellste KSP-Version zum Zeitpunkt der Recherche. |
| Hilt (`com.google.dagger.hilt.android`, `hilt-android`, `hilt-android-compiler`) | `2.59.2` | Erste Hilt-Version, deren Gradle-Plugin AGP 9 unterstützt. |
| `androidx.hilt:hilt-navigation-compose` | `1.4.0` | Für `hiltViewModel()` in Compose Navigation. |

## Umsetzung

- `HardBassEqApplication` (`@HiltAndroidApp`), in `AndroidManifest.xml` als
  `android:name` eingetragen.
- `MainActivity`: `@AndroidEntryPoint`, `SessionAttachSpikeController` per
  Feldinjektion (`@Inject lateinit var`).
- `AndroidAudioEffectRepository`: `@Inject constructor()`, gebunden über
  `AudioModule` (`@Binds` auf `AudioEffectRepository`).
- `SessionAttachSpikeController`: `@Singleton`, `@Inject constructor(@ApplicationContext ...)`
  – Lebenszyklus wechselt damit von Activity-gebunden (`remember { ... }`)
  zu Application-Singleton. Für den M0-Spike-Code unkritisch (keine
  Ressourcen werden über Rotation hinweg fälschlich offengehalten, da
  `stop()` weiterhin beim Verlassen der Compose-Sektion aufgerufen wird).
- `MainViewModel`: `@HiltViewModel`, injiziert über `hiltViewModel()` in
  `AppNavHost` statt manueller `MainViewModelFactory` (entfernt). Der
  `CoroutineDispatcher` für Hintergrundarbeit wird über einen eigenen
  Qualifier (`@DefaultDispatcher`, `DispatcherModule`) bereitgestellt, da
  Dagger-generierte Factories keine Kotlin-Default-Parameterwerte nutzen
  können – jeder Konstruktorparameter braucht eine explizite Bindung.

## Noch nicht Teil dieses Schritts

Room, DataStore-Nutzung und Kotlin-Serialization-Typen bleiben bewusst
außen vor (roadmap: "implementieren" statt "einrichten" ist erst M4 fällig,
siehe ADR 0001). Nur die reine Gradle-Abhängigkeitsverdrahtung für diese
drei folgt ggf. als separater, kleiner Schritt, sobald dieser
Hilt/KSP-Umbau in CI bestätigt grün ist – nicht im selben Commit, um die
Fehlersuche bei einem CI-Rotlauf nicht zu verkomplizieren.

## Korrektur nach erstem CI-Lauf

Der erste Versuch (nur `android.builtInKotlin=false`) schlug fehl:

```
The 'org.jetbrains.kotlin.android' plugin is not compatible with AGP's 9.0
new DSL (`android.newDsl=true` is enabled by default).
Solution: Set `android.builtInKotlin=true` ... or set `android.newDsl=false`
in `gradle.properties` to temporarily bypass this issue.
```

Behoben durch zusätzliches Setzen von `android.newDsl=false`. Das war in
keiner der vorherigen Recherche-Quellen für ADR 0001 explizit genannt –
zwei unabhängige, separat abschaltbare AGP-9-Verhaltensänderungen
(built-in Kotlin, new DSL) kollidieren beide mit `kotlin-android`, nicht
nur eine.

## Wichtiger Hinweis

### Korrektur nach zweitem CI-Lauf (19. September 2026)

Auch Commit `fe0db2d` war noch rot: [CI-Lauf 35400854917](https://github.com/kniepertsebastian-spec/equalizer/actions/runs/35400854917)
erreichte KSP, scheiterte anschließend jedoch in `compileDebugKotlin`:

```text
Inconsistent JVM-target compatibility detected for tasks
'compileDebugJavaWithJavac' (17) and 'compileDebugKotlin' (21).
```

Bei der Rückkehr zum separaten Kotlin-Android-Plugin fehlte die explizite
Kotlin-Zielkonfiguration. `kotlin.compilerOptions.jvmTarget` wird nun auf
`JvmTarget.JVM_17` gesetzt, passend zu Java `sourceCompatibility` und
`targetCompatibility`. Das JDK für Gradle bleibt 21. Die Zielprüfung wird
nicht unterdrückt. Diese Konfiguration gilt für alle Kotlin-Kompilationen
im App-Modul, einschließlich Unit-Tests.

Quelle: [Kotlin compilerOptions für Android](https://kotlinlang.org/docs/gradle-compiler-options.html#migrate-away-from-android-kotlinoptions).
Aktueller Validierungsstand: `docs/TEST_MATRIX.md`.

Dieses ADR beschreibt die Umsetzung; ob sie tatsächlich fehlerfrei baut,
bestätigt erst der CI-Lauf (diese Sandbox hat keinen Android-SDK-Zugriff
für eine lokale Verifikation). Ergebnis wird in `docs/TEST_MATRIX.md`
nachgetragen.
