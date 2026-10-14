# KaroRide 1.2.0-preview.1 — Englisch und Deutsch

Englisch ist die Standardsprache, auch auf einem deutsch eingestellten Karoo.
Oben rechts über die drei Punkte öffnet sich das bisherige Menü. Der erste Eintrag
**Language / Sprache** bietet **English** und **Deutsch**. Auswahl antippen:
sie wird sofort wirksam und privat auf diesem Karoo gespeichert.

Die Änderung umfasst die sechs Fahrtasten, Kameraauswahl, Einrichtungs-/Status-/
SDK-Hinweise, bekannte Fehler, Akku-/Speicher-/Zeitbeschriftungen, Fahrtfelder und
die laufende Verbindungsbenachrichtigung. Deutsch zeigt Dezimalkomma, Englisch
Dezimalpunkt. Kameranamen und unbekannte Hersteller-Fehlercodes bleiben unverändert.

Kein Activity-Neustart für die Auswahl: keine erneute Ausführung eines alten
Start-Intents, kein Trennen/Verbinden/Aufwecken und keine Aufnahmeaktion.
Laufende Aufnahme, Pause und Kameraauswahl gehören weiterhin dem unveränderten
Controller. Nur Darstellung/Ressourcen werden aktualisiert. Neue Präferenz
`ui-settings/language` getrennt von Kameraprofilen und SDK-Zustimmung.
Keine neue Bibliothek, Berechtigung, Internetverbindung oder globale Spracheinstellung.
Englisch und Deutsch bleiben vollständig im APK; keine Sprachpaket-Downloads.

Karoo-eigene Feldauswahl-/Bonus-Action-Listen und Android-Berechtigungsdialoge
werden vom Karoo angezeigt und richten sich gegebenenfalls nach dessen Systemsprache.
Die Inhalte unserer laufenden Fahrtfelder nutzen die in KaroRide gewählte Sprache.
Feld-/Action-IDs, Paket `de.markus.aceride` und eigener Updateschlüssel bleiben erhalten.
Die bisherige APK 1.1.1 bleibt unverändert als Rückweg verfügbar.

## Prüfung und nächster Gerätetest

122 JVM-Simulationen: bisherige 110 plus 12 Sprach-/Formatierungsverträge.
225 vollständige Android-Stringschlüssel je Sprache, identische Formatparameter,
Zuordnung der bekannten Kernmeldungen, unveränderte Kamera-Engine/SDK-Verbindung
und Anzeige-only-Auswahl separat statisch geprüft. Android-Release, Lint,
Signatur/Berechtigungen, private Werte und Offline-Nachbau werden im aktuellen
APK-/Testbericht dokumentiert. Keine Android-UI- oder Bluetooth-Hardwareprüfung
von Codex behaupten; reine Text-/XML-/JVM-Prüfung davon unterscheiden.

Markus hat 1.1.1 installiert und das Menü gesehen. Er hat dieses neue Sprachupdate
noch nicht bestätigt. Nächster einzelner Schritt: die neue APK als Update installieren,
vorher nicht deinstallieren. Danach in Einzelschritten prüfen: Englisch beim ersten
Öffnen, Menü → Language / Sprache → Deutsch, Fahrtfeld-Anzeige, erneutes Öffnen/
Karoo-Neustart mit beibehaltener Auswahl, Sprachwechsel während einer manuellen
Aufnahme ohne Stop/Start/Verbindungswechsel. Kamera- und Pausendaten müssen erhalten bleiben.
