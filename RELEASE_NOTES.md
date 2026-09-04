# FinanzCockpit 2.0.1

## Fehlerkorrekturen

- Der Zahlungsrhythmus wird beim Bearbeiten als eindeutige Auswahl angezeigt und
  zuverlässig von „Jährlich“ auf „Vierteljährlich“ oder einen anderen Rhythmus geändert.
- Die Änderung der Kostenart wird beim Verlassen des Editors nun als ungespeicherte
  Änderung erkannt.
- Der manuelle Sperrknopf öffnet nicht mehr sofort wieder den Entsperrdialog.
- Hinweise unter „Heute wichtig“ führen jetzt zum passenden Bereich.
- Die Nulllinie im Sechs-Monats-Verlauf wird passend zu den tatsächlichen Werten
  dargestellt.

## Datensicherheit und Bedienung

- Verschlüsselte Sicherungen werden nach der Passworteingabe zunächst geprüft und
  zusammengefasst. Vor dem Ersetzen der vorhandenen Daten ist eine zweite Bestätigung
  erforderlich.
- Kategorien mit einem zugeordneten Budget können nicht mehr versehentlich gelöscht
  werden.
- Der Finanzverlauf bleibt bei verborgenen Beträgen ebenfalls verborgen.
- Die Monatsnavigation der Buchungen besitzt eindeutige Beschriftungen für
  Bedienungshilfen.

## Qualität

- Versioncode auf 2 erhöht.
- 20 automatische Tests, Android-Lint, Release-Signatur und 16-KB-Zip-Ausrichtung
  erfolgreich geprüft.

# FinanzCockpit 2.0.0

## Neue Grundlage

- neue dauerhafte Paket-ID `de.finanzcockpit.mobile`
- neuer eigener Release-Schlüssel für diese und alle zukünftigen Versionen
- parallele Installation neben der bisherigen App möglich
- bekannte HTML-Ausgangsbasis mit 24 Planpositionen und 9 Kategorien enthalten
- verschlüsselte Sicherungen der bisherigen App können wiederhergestellt werden

## Design und Bedienung

- vier Bereiche: Übersicht, Planung, Buchungen und Mehr
- zentraler Plusknopf für Buchung, Planung und Budget
- vereinfachtes Cockpit mit den wichtigsten Monatswerten
- Analysekarte mit Plan-Ist-Vergleich und Sechs-Monats-Verlauf
- klare Budgetfarben und direkte Verknüpfung zu Buchungen
- größere Buchungszeilen, Kategoriesymbole und Wischgesten
- dezente Kartenanimationen und gezieltes haptisches Feedback
- Navy-/Türkis-Farbsystem sowie Hell-, Dunkel- und Systemdesign
- umfangreiche, übersichtlich gruppierte Einstellungen

## Datenschutz

- vollständig offline
- verschlüsselte lokale Datenbank
- Android-Keystore und Biometrie/Geräte-PIN
- passwortverschlüsselte Sicherungen
- keine Internetberechtigung
- Screenshots und Android-Cloud-Backup deaktiviert
