# Sicherheitsmodell

## Schutzmaßnahmen

- **Datenbank:** Room arbeitet über SQLCipher 4.18.0. Der Inhalt liegt nicht als Klartext-SQLite-Datei vor.
- **Geräteschlüssel:** Eine zufällige 256-Bit-Datenbankpassphrase wird mit AES-256-GCM unter einem nicht exportierbaren Android-Keystore-Schlüssel verpackt.
- **Benutzerauthentifizierung:** Jede Entsperrung verlangt `BIOMETRIC_STRONG` oder `DEVICE_CREDENTIAL`. Der Keystore-Schlüssel ist nur im entsperrten Gerätezustand nutzbar.
- **Sofortige Sperre:** `onStop()` leert den UI-Zustand, verwirft den Datenbankzugriff und schließt die Datenbank. Bildschirmfotos und die Vorschau im App-Wechsler werden mit `FLAG_SECURE` blockiert.
- **Netzwerk:** Das Manifest enthält keine Internetberechtigung. Klartextverkehr ist zusätzlich deaktiviert.
- **System-Backups:** Cloud-Backup und Geräteübertragung sind für Datenbank, Dateien und Schlüsselmaterial deaktiviert.
- **Dateisicherung:** Export ist nur als authentifizierter AES-256-GCM-Container möglich. Die Schlüsselableitung verwendet PBKDF2-HMAC-SHA-256 mit 600.000 Iterationen, zufälligem Salt und mindestens zwölf Passwortzeichen.
- **Import:** Dateigröße, Schema, Datensatzanzahl, IDs, Feldlängen, Datums- und Betragsformate werden begrenzt und geprüft. Ein Klartext-Import muss ausdrücklich bestätigt werden.
- **Speicherbereinigung:** Temporäre Passwörter, Schlüssel- und Importpuffer werden soweit auf JVM/Android praktikabel überschrieben.

## Bewusste Grenzen

- FinanzCockpit 2 enthält die vom Benutzer gewünschten HTML-Ausgangspositionen als APK-Asset. Diese Ausgangswerte sind vor der ersten Übernahme nicht geheim und können aus der APK extrahiert werden. Laufende Änderungen und neue Buchungen werden nicht in die APK geschrieben und bleiben in der verschlüsselten Datenbank.
- Ein kompromittiertes, gerootetes oder bereits entsperrtes Gerät kann den Schutz des Android-Betriebssystems unterlaufen.
- `FLAG_SECURE` verhindert gewöhnliche Screenshots, aber keine Aufnahme mit einer externen Kamera.
- Ein vergessenes Sicherungspasswort kann nicht wiederhergestellt werden.
- Wird der Android-Keystore-Schlüssel durch Geräte-Reset, Sicherheitsänderungen oder Hardwarefehler ungültig, ist eine zuvor erstellte verschlüsselte Sicherung erforderlich.
- Der Quellcode und die automatischen Prüfungen ersetzen kein unabhängiges professionelles Sicherheitsaudit.

## Veröffentlichung

Keine Keystore-Datei und kein Kennwort gehört in das Repository. Das dauerhafte Signaturpaket muss getrennt vom Projekt an einem geschützten Ort verwaltet werden. Für einen Release-Build muss dieser Ort dem Bauskript ausdrücklich mit `-SigningDirectory` übergeben werden; das Skript sucht nicht an einem Standardpfad danach. Für Google Play sollte Play App Signing verwendet und der Upload-Schlüssel außerhalb des Projekts verwaltet werden. Release-Artefakte müssen vor Veröffentlichung auf realen Geräten getestet werden.
