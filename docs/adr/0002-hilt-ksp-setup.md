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

## Wichtiger Hinweis

Dieses ADR beschreibt die Umsetzung; ob sie tatsächlich fehlerfrei baut,
bestätigt erst der CI-Lauf (diese Sandbox hat keinen Android-SDK-Zugriff
für eine lokale Verifikation). Ergebnis wird in `docs/TEST_MATRIX.md`
nachgetragen.
