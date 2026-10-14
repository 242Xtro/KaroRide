# KaroRide

![KaroRide — Fokus auf die Fahrt. Die Kamera in Reichweite.](docs/images/karoride-banner.svg)

**Deine Ace Pro oder GO 3S direkt am Hammerhead Karoo 3 steuern.**

**Preview `1.2.0-preview.1`** · [Download & Versionshinweise](https://github.com/242Xtro/KaroRide/releases/tag/v1.2.0-preview.1) · [English](README.md) · [Kaffee ausgeben](https://ko-fi.com/karoride)

Große Fahrtasten, Start/Stopp-Datenfeld, Kameraauswahl und klare Statusanzeige.
Jeweils eine ausgewählte Kamera wird gesteuert. Pause beendet den Clip; Fortsetzen
startet eine neue Datei. Fahrtstart, Wiederverbindung und Karoo-Neustart starten
keine Aufnahme automatisch. Videoeinstellungen bleiben bestehen.

| Auf deiner Fahrtseite | Auf einen Blick | Einmal einrichten |
|---|---|---|
| Große Tasten für Start, Stopp, Pause und Fortsetzen | Aufnahme, Verbindung, Akku, freier Speicher, Cliplänge | Kameraprofile speichern; nach Stopp umschalten |
| Ganze Kameraseite oder kompaktes Start/Stopp-Feld | Bestätigter Kamerastatus; unbekannte Zustände klar gekennzeichnet | Englisch als Standard, Deutsch wählbar; nach Einrichtung offline |

Das Titelbild ist eine Designillustration. Echte Geräte-Screenshots ergänzen wir
später. Die eigene Projektwebseite auf hellmold.de ist in Vorbereitung.

## Kameras und Grenzen

- **Ursprüngliche Ace Pro, erste Generation:** manuelle Steuerung, Akku, freier
  Speicher und Dauer; SDK-Adapter für Aufwecken/Ausschalten. Bluetooth Wakeup muss
  an der Kamera aktiviert sein. Aufwecken zählt erst nach Verbindung und Statusantwort.
- **GO 3S:** manuelle Bedienung und Kamerawechsel; Grundbedienung im Action Pod vom
  Nutzer bestätigt. Ausschalten wird vom verwendeten SDK nicht unterstützt:
  Verbindung zuerst trennen, danach am Gerät ausschalten.

Der Nutzer bestätigt bisherige Grundbedienung, Wechsel nach Stopp sowie Datenfeld
und eigene Fahrtseite. Vollständige Einzeltests einschließlich GO 3S außerhalb
des Pods, längerer Ausschaltzeiten und Funkverlust-/Neustart-/Sensorfällen bleiben
offen. Die neue Sprachauswahl in 1.2.0 und ihre Speicherung brauchen noch eine Geräteprüfung.
Die App startet auf Englisch; über die drei Punkte → Language / Sprache ist Deutsch wählbar. Auswahl lokal gespeichert; Webseite und Dokumentation DE/EN.

**Geplant, noch nicht enthalten:** X3 als nächster Ausbau, anschließend X5 und
Ace Pro 2 mit Gerätetestern. Keine Terminzusage.

## Einrichtung

1. **KaroRide-1.2.0-preview.1.apk** und die `.sha256`-Datei beim
   [Preview-Release](https://github.com/242Xtro/KaroRide/releases/tag/v1.2.0-preview.1) herunterladen.
2. APK auf dem Smartphone über „Teilen“ an Hammerhead Companion geben und
   Installation am Karoo bestätigen. [Offizielle Anleitung](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).
3. Extensions → KaroRide → Open: SDK-Hinweis bestätigen, notwendige Bluetooth-Rechte
   erlauben, eingeschaltete Kamera suchen und auswählen. Autorisierung gegebenenfalls am Gerät bestätigen.
4. Im Fahrprofil eine Datenseite mit ausschließlich **CAMERA CONTROL / KAMERA
   STEUERUNG** anlegen, oder **CAMERA START / STOP / KAMERA START / STOPP** neben
   die üblichen Daten setzen. Karoo-Auswahlmenüs können der Systemsprache folgen.

Vor der Fahrt App öffnen und verbinden. Bei Funkverlust kann die Kamera weiter
aufnehmen. Die Anzeige wird unbekannt; nach erneuter Verbindung zuerst echten Zustand abgleichen.

## AceRide wird KaroRide

Paketkennung `de.markus.aceride`, Extension-ID `aceride`, Feld-IDs und eigener
Updateschlüssel bleiben erhalten. Projekt-APK als Update installieren,
vorher nicht deinstallieren. versionCode 9; bestehende Kameraauswahl bleibt
normalerweise erhalten. SDK-Hinweis einmal bestätigen. Videos bleiben auf der Kamera.

[Build-Anleitung](docs/BUILD.md) · [Datenschutz](docs/PRIVACY.md) · [Prüfsummen & Updates](docs/RELEASE.md) · [Prüfungen](docs/VALIDATION.json)

122/122 JVM-Simulationen bestanden, Android-Release gebaut, vollständiger Offline-
Nachbau bytegleich. Lint: 0 Fehler, 18 dokumentierte Warnungen. Keine von Codex ausgeführten Hardwaretests.
Das verwendete Hersteller-SDK erfordert eigenen genehmigten Entwicklerzugang.
Zugangsdaten und Signierungsschlüssel liegen nur lokal und gehören nicht ins Repository.

Die App bleibt kostenlos. [Ein Kaffee auf Ko-fi](https://ko-fi.com/karoride)
unterstützt die Weiterentwicklung — oder auch mal einen neuen Schlauch.
Alles freiwillig, ohne Supportvertrag oder Terminzusage. Für Fehler bitte Schritte/Version/Modell angeben,
keine Kamera-IDs, Passwörter oder privaten Protokolle veröffentlichen.

Eigener Code und Hammerhead-Runtime: Apache-2.0, [LICENSE](LICENSE)/[NOTICE](NOTICE).
Hersteller-SDK: proprietär; die eigene Lizenz gilt nicht für dessen Binaries.
Unabhängiges Projekt von Markus Hellmold, keine offizielle Hersteller-App.
