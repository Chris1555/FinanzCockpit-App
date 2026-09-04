# Installation und Datenübernahme

## FinanzCockpit 2 installieren

1. Die bisherige FinanzCockpit-App nicht deinstallieren.
2. In der bisherigen App unter **Mehr → Daten** eine verschlüsselte Sicherung erstellen,
   sofern dort aktuellere Daten als in der HTML-Ausgangsbasis vorhanden sind.
3. `FinanzCockpit-2.0.1-install.apk` auf das Handy kopieren und öffnen.
4. Die Installation aus der verwendeten Dateien-App einmalig erlauben.
5. `FinanzCockpit 2` starten und mit Biometrie oder Geräte-PIN entsperren.
6. Die enthaltenen Ausgangsdaten prüfen.
7. Für aktuellere Handydaten unter **Mehr → Daten → Sicherung wiederherstellen** die
   zuvor erstellte `.fcbak`-Datei auswählen und das Sicherungspasswort eingeben.
8. Planungen, Buchungen, Kategorien und Budgets mit der alten App vergleichen.
9. Die alte App erst entfernen, wenn die Datenübernahme vollständig geprüft wurde.

FinanzCockpit 2 besitzt einen eigenen App-Speicher. Installation und Prüfung verändern
die alte App und deren Daten nicht.

## Signatur unbedingt sichern

Das getrennt vom Projekt aufbewahrte Signaturpaket ermöglicht alle zukünftigen
Updates. Ohne diese Dateien wäre später erneut nur eine Neuinstallation möglich.

Das ZIP enthält den privaten Schlüssel und seine Zugangsdaten. Es darf nicht öffentlich
hochgeladen oder per unverschlüsselter E-Mail versendet werden. Empfohlen sind zwei
getrennte verschlüsselte USB-Datenträger oder ein seriös verschlüsselter Tresor. Der
Ablageort wird nicht dokumentiert und muss dem Bauskript für jeden Release-Build
ausdrücklich mit `-SigningDirectory` übergeben werden.
