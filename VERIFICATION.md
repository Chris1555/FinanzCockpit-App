# Verifikationsbericht – FinanzCockpit 2.0.1

Stand: 2. September 2026

## Automatische Prüfungen

- `testDebugUnitTest`: 20 Tests, 0 Fehler
- `lintDebug`: erfolgreich; 0 Fehler, 2 nicht blockierende Wartungshinweise
- `assembleRelease`: erfolgreich mit R8-Minifizierung und Ressourcen-Shrinking
- Release-Signatur: erfolgreich geprüft
- 16-KB-Zip-Ausrichtung: erfolgreich geprüft
- bekannte HTML-Ausgangsdaten: im Release enthalten

Die Tests decken Geldformatierung, Planberechnung, Plan-Ist-Vergleich,
Kategorie-Budgets, verschlüsselte Sicherungen, ältere Sicherungsformate und die
HTML-Ausgangsbasis ab.

## Binäre APK-Prüfung

- Datei: `FinanzCockpit-2.0.1-install.apk`
- Paket-ID: `de.finanzcockpit.mobile`
- Anzeigename: `FinanzCockpit 2`
- Versioncode: 2
- Version: 2.0.1
- Android: `minSdk 30`, `targetSdk 37`, `compileSdk 37`
- native Architekturen: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
- APK-Signatur v2: gültig
- Zertifikat SHA-256:
  `6bd156f3eeaa8dce6a6eb5ba8944890c2e4ddf7debed7ca0c298cb5392851828`
- APK SHA-256:
  `1253d3f1c306fab94d4a2a797b5e8935d10d0a7da9473a05b4e92bae2dc925cf`
- keine Internetberechtigung

## Signaturpaket

Gespeichert wurden:

- passwortgeschützter privater PKCS12-Schlüssel
- öffentliches X.509-Zertifikat
- SHA-256-Fingerabdruck und Datei-Prüfsummen
- Alias und beide benötigten Kennwörter
- Sicherheitshinweise
- zusätzliches ZIP für eine einfache externe Sicherung

ZIP SHA-256:

`68c0d7eb34f61fe44be5ff06fc41ea8cfc2a392116a9ab2553070d7799bfa857`

Der Generator überschreibt niemals einen vorhandenen Schlüssel. Der Bau prüft die
Signatur gegen den gespeicherten Fingerabdruck und bricht bei Abweichungen ab.

## Noch offen

Die APK wurde in dieser Umgebung nicht auf dem persönlichen Android-Gerät installiert.
Nach der Installation sollten Oberfläche, Biometrie, Ausgangsdaten und gegebenenfalls
die Wiederherstellung einer aktuellen Sicherung kontrolliert werden.
