# KaroRide

![Illustration der Projektseite mit Kamerasteuerung am Karoo](docs/images/karoride-banner-v2.png)

KaroRide ist eine kostenlose Extension für den Hammerhead Karoo 3, mit der du deine Insta360 Ace Pro oder GO 3S direkt am Lenker steuerst.

[Website](https://hellmold.de/karoride/) · [Download 1.2.0-preview.1](https://github.com/242Xtro/KaroRide/releases/tag/v1.2.0-preview.1) · [English](README.md) · [Ko-fi](https://ko-fi.com/karoride)

Das Titelbild ist eine gerenderte Illustration der Webseite, kein Geräte-Screenshot. Fotos von echten Fahrten und Screenshots folgen.

## Was es kann

Leg KaroRide als ganze Datenseite an, und du hast große Tasten für Wecken, Start, Pause, Stopp und Ausschalten, dazu Akku, freien Speicher und Cliplänge auf einen Blick.

Lieber deine gewohnte Seite? Setz das Start/Stopp-Feld zwischen Tempo und Leistung und steuere die Kamera, ohne die Seite zu wechseln.

Eine Aufnahme gilt erst als laufend, wenn die Kamera sie bestätigt. Reißt Bluetooth ab, zeigt KaroRide **Unbekannt** und behält den letzten bestätigten Stand, statt sich einen auszudenken.

## Kameras

| Funktion | Insta360 Ace Pro, 1. Generation | Insta360 GO 3S, im Action Pod getestet |
|---|---|---|
| Aufnahme starten und stoppen | Ja | Ja |
| Pausieren und fortsetzen<sup>3</sup> | Ja | Ja |
| Akku, freier Speicher, Cliplänge | Ja | Ja |
| Kamera vom Karoo aus wecken | Ja<sup>1</sup> | Im Test |
| Kamera vom Karoo aus ausschalten | Ja | Nein<sup>2</sup> |

1. Ace Pro: In den Kameraeinstellungen **Bluetooth Wakeup** einschalten. KaroRide meldet die Kamera erst als wach, wenn sie verbunden ist und geantwortet hat.
2. GO 3S: Das Insta360-SDK hat für sie keinen Ausschaltbefehl. In KaroRide trennen, dann an der Kamera ausschalten. Der Betrieb außerhalb des Action Pods ist noch nicht getestet.
3. Pause beendet den aktuellen Clip, Weiter startet einen neuen. Das ist bei beiden Kameras gleich.

Als Nächstes kommt die **X3**. **X5** und **Ace Pro 2** sind geplant und in dieser Preview noch nicht unterstützt. Wenn du eine davon hast und beim Testen helfen magst, [schreib ein Issue auf GitHub](https://github.com/242Xtro/KaroRide/issues).

## Installation

1. **APK auf den Karoo bringen.** Öffne die [Webseite](https://hellmold.de/karoride/) auf dem Handy und lade die APK herunter. Tipp auf Teilen und wähl die Hammerhead-Companion-App. Oder teile den [APK-Downloadlink des GitHub-Release](https://github.com/242Xtro/KaroRide/releases/download/v1.2.0-preview.1/KaroRide-1.2.0-preview.1.apk) mit der Companion-App. Bestätige Installieren auf deinem gekoppelten Karoo. [Sideloading-Anleitung von Hammerhead](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).
2. **Kamera koppeln.** Am Karoo Extensions, KaroRide, Öffnen. SDK-Hinweis bestätigen und Bluetooth erlauben. Kamera einschalten, in der Liste auswählen und an der Kamera bestätigen, falls sie fragt.
3. **Ins Fahrprofil einbauen.** Eine Datenseite mit **KAMERA STEUERUNG** als einzigem Feld anlegen. Oder **KAMERA START / STOPP** neben deine gewohnten Daten setzen. Auf Englisch heißen die Felder **CAMERA CONTROL** und **CAMERA START / STOP**.

Später aktualisieren: Neue APK einfach über die alte installieren. Nicht vorher deinstallieren, dann bleiben deine Einstellungen erhalten. Verwende weiterhin die Projekt-APK. Community-Builds mit anderem Schlüssel können sie nicht als Update ersetzen. [Prüfsumme und Update-Hinweise](docs/RELEASE.md).

## Fragen

<details>
<summary>Brauche ich unterwegs mein Handy?</summary>

Nein. Der Karoo spricht direkt per Bluetooth mit der Kamera. Das Handy brauchst du nur einmal, um die APK auf den Karoo zu bringen.

</details>

<details>
<summary>Was passiert, wenn die Verbindung abreißt?</summary>

Die Kamera nimmt unter Umständen weiter auf. KaroRide zeigt den Zustand als Unbekannt und markiert den letzten bestätigten Stand. Nach dem Wiederverbinden liest es den echten Zustand von der Kamera.

</details>

<details>
<summary>Bleibt eine Pause in derselben Videodatei?</summary>

Nein. Pause beendet den Clip, Weiter beginnt eine neue Videodatei. KaroRide merkt sich je Kamera, dass du pausiert hast.

</details>

<details>
<summary>Kann ich zwei Kameras gleichzeitig steuern?</summary>

KaroRide steuert jeweils eine ausgewählte Kamera. Eine laufende Aufnahme vor dem Wechsel stoppen oder pausieren. Das ist eine Regel der App, keine Grenze der Kameras.

</details>

<details>
<summary>Warum kann ich die GO 3S nicht ausschalten?</summary>

Das verwendete SDK hat dafür keinen Ausschaltbefehl. Erst in KaroRide trennen, dann die Kamera am Gerät ausschalten. Ein dunkles Pod-Display heißt nicht automatisch, dass sie aus ist.

</details>

<details>
<summary>Ändert KaroRide meine Videoeinstellungen?</summary>

Nein. Auflösung, Bildrate und Stabilisierung bleiben, wie sie sind. Einen passenden Videomodus stellst du an der Kamera ein; KaroRide wechselt ihn nie stillschweigend.

</details>

<details>
<summary>Ist die App englisch oder deutsch?</summary>

Beides. KaroRide startet auf Englisch. Im Drei-Punkte-Menü unter Language / Sprache stellst du auf Deutsch um, die Fahrtfelder wechseln mit.

</details>

<details>
<summary>Ist das eine offizielle App?</summary>

Nein. KaroRide ist ein unabhängiges Projekt. Es nutzt das Insta360 Camera SDK und Hammerheads karoo-ext, ohne Partnerschaft mit einer der beiden Firmen.

</details>

## Build

[Build-Anleitung](docs/BUILD.md) · [Quellen und Versionen](docs/SOURCES.md) · [Prüfergebnisse](docs/VALIDATION.json)

Werkzeug- und Abhängigkeitsversionen sind festgelegt. Entwickler benötigen eigenen genehmigten Insta360-SDK-Zugang und einen eigenen privaten Signierungsschlüssel. SDK-Pakete, Zugangsdaten und unser Updateschlüssel sind nicht im Repository enthalten. Kein Cloud-Build ist eingerichtet.

Für diese unveränderte APK wurden 122 JVM-Simulationen, der Android-Release-Build und ein bytegleicher Offline-Nachbau bestanden. Lint: 0 Fehler und 18 Warnungen. Bisherige Grundbedienung der Ace Pro und GO 3S im Action Pod wurde vom Nutzer bestätigt. Die neue Sprachauswahl von 1.2.0 und die vollständige Hardware-Testmatrix brauchen noch Geräteprüfungen. Ein Build oder eine Simulation ist kein Bluetooth-Kompatibilitätsnachweis.

## Datenschutz

Kameraprofile, Pausenabsicht und Sprachwahl bleiben im privaten App-Speicher. Videos bleiben auf der Kamera. Die App nutzt Bluetooth und einen Connected-Device-Foreground-Service, ohne Internet-, Standort-, Medien-, Speicher- oder Benachrichtigungszugriffsrecht. [App-Datenschutz](docs/PRIVACY.md) · [Webseiten-Datenschutz](https://hellmold.de/karoride/datenschutz.html?lang=de).

Unterstützung über [Ko-fi](https://ko-fi.com/karoride) ist freiwillig. KaroRide bleibt kostenlos. Der Zahlungsanbieter öffnet sich erst beim Linkklick.

## Lizenz

Eigener Code und die enthaltene Hammerhead-Runtime stehen unter Apache-2.0. Siehe [LICENSE](LICENSE), [NOTICE](NOTICE) und [Quellennachweise](docs/SOURCES.md). Das proprietäre Insta360-SDK unterliegt weiterhin den [SDK-Bedingungen](https://www.insta360.com/support/supportcourse?post_id=20734). Die Apache-Lizenz gewährt keine Rechte an diesen Binaries.

KaroRide ist ein unabhängiges Projekt von Markus Hellmold, ohne Partnerschaft mit Hammerhead oder Insta360. Produktnamen benennen kompatible Geräte. Es wird kein fremder App-Signierungsschlüssel verwendet. Die Software wird ohne Gewähr bereitgestellt.
