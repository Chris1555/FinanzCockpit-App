## Zweck

<!-- Was wird geändert und welches konkrete Problem wird damit gelöst? -->

## Umfang und Abgrenzung

<!-- Welche Dateien/Funktionen sind betroffen? Was wurde bewusst nicht geändert? -->

## Sicherheits- und Datenbereiche

- [ ] Biometrie, Gerätesperre oder Keystore betroffen
- [ ] Datenbank, Verschlüsselung oder sensible App-Daten betroffen
- [ ] Backup oder Wiederherstellung betroffen
- [ ] Keiner dieser Bereiche ist betroffen

<!-- Sicherheitsauswirkungen, Schutzmaßnahmen und verbleibende Risiken beschreiben. -->

## Verifikation

- [ ] `testDebugUnitTest` erfolgreich
- [ ] `lintDebug` erfolgreich
- [ ] `assembleDebug` erfolgreich
- [ ] Relevanter Gerätetest erfolgreich
- [ ] Gerätetest nicht erforderlich; Begründung unten angegeben

<!-- Testergebnisse, Testgerät/Android-Version und gegebenenfalls Begründung ergänzen. -->

## Secrets, Release und Signierung

- [ ] Keine Secrets, Schlüssel, Zugangsdaten oder privaten Artefakte hinzugefügt
- [ ] Kein Release-Build und keine Release-Signierung ausgeführt
- [ ] Signaturmaterial und produktive Daten wurden nicht verwendet oder verändert

## Migrationen und Schemas

- [ ] Keine Datenbankmigration oder Schemaänderung
- [ ] Migration und Schemaänderung sind enthalten und getestet
- [ ] Nicht anwendbar

<!-- Bei Änderungen: Ausgangsversion, Zielversion, Schema und Testergebnis dokumentieren. -->

## Rollback

<!-- Wie lässt sich die Änderung sicher zurücknehmen? Welche Datenfolgen wären zu beachten? -->
