# Android-Updates über GitHub Releases

Die App sucht beim Start und danach alle sechs Stunden nach einem neuen
veröffentlichten GitHub Release. Auch Betas werden berücksichtigt. Ein Release
ist für das In-App-Update nur geeignet, wenn es eine signierte APK und die dazu
gehörige `update.json` enthält. Der Release-Workflow erzeugt beide Dateien.

## Einmalig: Signierschlüssel hinterlegen

Die private Keystore-Datei **nicht** ins Repository einchecken. In den
Repository-Einstellungen unter **Secrets and variables → Actions** diese zwei
Secrets hinterlegen:

- `HARDBASS_RELEASE_KEYSTORE_BASE64`: Base64-Inhalt der Keystore-Datei
- `HARDBASS_RELEASE_PASSWORD`: Passwort für Keystore und Schlüssel

Der Schlüssel-Alias muss `hardbasseq` heißen. Die Keystore-Datei und das
Passwort zusätzlich außerhalb von GitHub sicher aufbewahren. Ohne diesen
Schlüssel lassen sich spätere APKs nicht als Updates über ältere Installationen
legen.

## Release veröffentlichen

1. Einen Tag wie `v0.2.0-beta.2` auf dem gewünschten Commit erstellen.
2. Für diesen Tag auf GitHub ein Release veröffentlichen.
3. Der Workflow **Signed Android release** baut eine signierte APK mit
   fortlaufendem `versionCode` und lädt sie sowie `update.json` zum Release.
4. Erst nach erfolgreichem Workflow die Release-APK auf dem Handy installieren.

Die App prüft beim Download die SHA-256-Prüfsumme, Paket-ID, Versionsnummer und
Signatur, bevor sie den Android-Installer öffnet. Android verlangt eine
Bestätigung für die Installation und gegebenenfalls einmalig die Freigabe für
„Unbekannte Apps installieren“ für HardBass EQ. Es findet keine stille
Installation statt.

**Wechsel von alten CI-Debug-APKs:** Diese wurden auf wechselnden GitHub-Runnern
mit verschiedenen Debug-Schlüsseln signiert. Der neue Release-Schlüssel kann
sie nicht direkt aktualisieren. Vor dem Wechsel lokale Daten sichern und die
alte App einmalig deinstallieren. Danach können neue Release-APKs mit derselben
Signatur als normale Updates installiert werden.
