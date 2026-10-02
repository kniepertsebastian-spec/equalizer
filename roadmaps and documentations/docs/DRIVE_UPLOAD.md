# Automatischer Debug-APK-Upload nach Google Drive

Bei jedem grünen CI-Build (`assembleDebug`) wird die Debug-APK automatisch
in einen freigegebenen Google-Drive-Ordner hochgeladen (Datei
`HardBassEQ-debug-latest.apk`, wird bei jedem Build überschrieben/aktualisiert,
kein wachsender Ordner voller Einzeldateien). So kann die App direkt aufs
Handy geladen werden, ohne über die GitHub-Actions-Artefakte zu gehen.

Der Upload läuft über einen Google-Cloud-Service-Account (kein persönliches
Google-Konto, kein OAuth-Login in der CI). Das erfordert eine einmalige
Einrichtung, die **nur ein Repo-Owner/Google-Konto-Inhaber** machen kann –
Claude Code hat keinen Zugriff auf die Google-Cloud-Console.

## Einmalige Einrichtung (ca. 10 Minuten)

### 1. Google-Cloud-Projekt + Drive API

1. https://console.cloud.google.com/ öffnen, ein Projekt anlegen (oder ein
   bestehendes wählen) – z. B. „hardbasseq-ci".
2. **APIs & Dienste → Bibliothek** → „Google Drive API" suchen → **Aktivieren**.

### 2. Service-Account anlegen

1. **APIs & Dienste → Anmeldedaten → Anmeldedaten erstellen → Dienstkonto**.
2. Name z. B. „hardbasseq-drive-upload", keine zusätzlichen Rollen nötig
   (der Zugriff kommt ausschließlich über die Drive-Ordnerfreigabe, nicht
   über IAM-Rollen).
3. Nach dem Anlegen: Dienstkonto öffnen → Tab **Schlüssel** → **Schlüssel
   hinzufügen → Neuer Schlüssel → JSON** → herunterladen.
   Diese JSON-Datei enthält den privaten Schlüssel – wie ein Passwort
   behandeln, nicht ins Repo committen.
4. Die **E-Mail-Adresse** des Dienstkontos notieren (steht in der JSON-Datei
   unter `client_email`, sieht etwa so aus:
   `hardbasseq-drive-upload@<projekt-id>.iam.gserviceaccount.com`).

### 3. Drive-Ordner für das Dienstkonto freigeben

1. Den Ziel-Ordner öffnen:
   https://drive.google.com/drive/folders/1xtR1iAIItwWrOco71gJG46bzRMHFnuhh
2. **Freigeben** → die Service-Account-E-Mail-Adresse aus Schritt 2.4
   eintragen → Rolle **Bearbeiter (Editor)** → Einladung senden.
   (Ohne diesen Schritt schlägt der Upload mit einem Berechtigungsfehler fehl,
   auch wenn der Schlüssel korrekt hinterlegt ist.)

### 4. JSON-Schlüssel als GitHub-Secret hinterlegen

1. Im Repo: **Settings → Secrets and variables → Actions → New repository
   secret**.
2. Name: `GDRIVE_SA_KEY`
3. Wert: kompletten Inhalt der heruntergeladenen JSON-Datei hineinkopieren
   (das ganze JSON-Objekt, nicht nur den `private_key`-Wert).
4. Speichern.

Danach die heruntergeladene JSON-Datei lokal löschen bzw. sicher aufbewahren.

## Danach

Sobald das Secret gesetzt ist, läuft der Upload automatisch bei jedem
`push`/`pull_request`-Build mit (Schritt „Upload debug APK to Google Drive"
in `.github/workflows/ci.yml`). Der Workflow-Schritt selbst läuft immer;
`scripts/upload_apk_to_drive.sh` prüft **innerhalb des Skripts**, ob
`GDRIVE_SA_KEY_JSON` gesetzt ist, und beendet sich sonst sofort mit Erfolg
(`exit 0`) – ohne gesetztes Secret bleibt der Rest der CI also grün.

**Wichtig (Lessons Learned):** Der ursprüngliche erste Versuch hat die
Bedingung stattdessen über `if: ${{ secrets.GDRIVE_SA_KEY != '' }}` auf
Schritt-Ebene geprüft. Das ist **ungültig** – der `secrets`-Kontext steht in
`if:`-Bedingungen nicht zur Verfügung, GitHub Actions lehnt die komplette
Workflow-Datei dann mit einem Parse-Fehler ab (0 Jobs, sofortiger roter
Status, kein Log). Das hat fünf Commits lang die komplette CI lahmgelegt,
bevor es aufgefallen ist. Deshalb: Secret-Prüfungen immer im Skript/`run:`-Body
machen, nie in `if:`.

Technischer Hintergrund: Der Workflow-Schritt ruft
`scripts/upload_apk_to_drive.sh` auf, das sich per JWT-Bearer-Flow
(RS256, Scope `drive.file`) direkt mit `curl`/`jq`/`openssl` gegen die
Google-Drive-API v3 authentisiert – keine zusätzlichen Build-Abhängigkeiten
nötig. Existiert `HardBassEQ-debug-latest.apk` im Zielordner bereits, wird
nur der Dateiinhalt aktualisiert (Beschreibung mit Branch/Commit/Run-ID),
sonst wird die Datei neu angelegt.

## Fehlerdiagnose

- **„Failed to obtain a Google Drive access token"**: Secret-Inhalt prüfen –
  muss das vollständige JSON-Objekt sein, nicht nur ein Teil davon.
- **403/„insufficientPermissions" beim Hochladen**: Ordner wurde nicht (oder
  mit der falschen E-Mail-Adresse) für das Dienstkonto freigegeben, siehe
  Schritt 3.
- Der Workflow-Schritt ist so gebaut, dass er den restlichen Build nicht
  gefährdet: Ohne Secret wird er komplett übersprungen.
