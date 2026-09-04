# FinanzCockpit 2 für Android

FinanzCockpit 2 ist die neue, dauerhaft updatefähige Installationslinie der privaten
Finanz-App. Sie arbeitet vollständig offline und verwendet eine verschlüsselte lokale
SQLCipher-Datenbank.

## Neue Installationslinie

- Anzeigename: `FinanzCockpit 2`
- Paket-ID: `de.finanzcockpit.mobile`
- Version: `2.0.1`
- Versioncode: `2`
- Android: ab Android 11

Die neue Paket-ID erlaubt eine sichere parallele Installation neben der bisherigen
App. Dadurch kann der Datenbestand vor dem Entfernen der alten App kontrolliert werden.

Beim ersten Start übernimmt die Release-App einmalig die bekannte HTML-Ausgangsbasis
mit 24 Planpositionen und 9 Kategorien. Aktuellere Buchungen oder Änderungen vom Handy
können anschließend über eine verschlüsselte `.fcbak`-Sicherung der alten App
wiederhergestellt werden.

## Funktionen

- Navigation mit Übersicht, Planung, Buchungen und Mehr
- zentraler Plusknopf für Buchung, Planung und Budget
- ruhiges Cockpit mit frei verfügbarem Betrag, Monatsausgaben und „Heute wichtig“
- Plan-Ist-Analyse und Sechs-Monats-Verlauf
- Kategorie-Budgets mit Türkis-, Gelb- und Rotstatus
- Budgetkarten öffnen direkt die zugehörigen Buchungen
- große Buchungszeilen mit Kategoriesymbolen
- Wischen nach rechts zum Bearbeiten und nach links zum Löschen
- Rückgängig-Funktion nach dem Löschen
- Hell, Dunkel und Systemdesign
- Einstellungen für Startseite, Startmonat, Zahlenformat, Vibration, Animationen,
  Budgetwarnungen, Schnellbuchung, Datenschutz und Sicherungen
- verschlüsselte Sicherung und Wiederherstellung
- Import älterer JSON-Sicherungen
- kein Internetzugriff, keine Werbung und keine Analyse-SDKs

## Dauerhafte Releasesignatur

Privater Schlüssel und Zugangsdaten müssen getrennt vom Projekt an einem selbst
gewählten, geschützten Ort aufbewahrt werden. Der Signaturordner sollte auf mindestens
zwei getrennten sicheren Datenträgern gesichert werden. Sicherungskopien mit
vertraulichen Zugangsdaten müssen wie ein Passwort behandelt werden.

Zertifikat SHA-256:

`6bd156f3eeaa8dce6a6eb5ba8944890c2e4ddf7debed7ca0c298cb5392851828`

Der Schlüsselgenerator verweigert das Überschreiben bestehender Schlüssel. Der
Release-Bau bricht ab, wenn die dauerhafte Signatur fehlt, und kontrolliert nach dem
Bau automatisch den Zertifikat-Fingerabdruck.

## Künftige Updates bauen

Mit eingerichtetem JDK 17 und Android SDK muss der Signaturordner bei jedem Aufruf
ausdrücklich angegeben werden:

```text
powershell -ExecutionPolicy Bypass -File FINANZCOCKPIT_BAUEN.ps1 -SigningDirectory '<geschützter Signaturordner>'
```

Das Skript verwendet keinen Standardpfad für Signaturmaterial. Fehlt der Parameter,
bricht es ab, bevor es nach Zugangsdaten oder einem privaten Schlüssel sucht.

Das Skript führt Tests und Android-Lint aus, signiert das Release, prüft Zertifikat und
16-KB-Zip-Ausrichtung und kopiert anschließend die installierbare APK in den
`Android`-Ordner.

Die Signatur darf bei zukünftigen Versionen niemals gewechselt werden. Versionscodes
müssen jeweils erhöht werden.

Weitere Informationen stehen in [INSTALLATION.md](INSTALLATION.md),
[RELEASE_NOTES.md](RELEASE_NOTES.md), [VERIFICATION.md](VERIFICATION.md) und
[SECURITY.md](SECURITY.md).
