# KaroRide 1.2.0-preview.1

KaroRide is a free extension for the Hammerhead Karoo 3 that controls your Insta360 Ace Pro or GO 3S from the handlebar.

[Website](https://hellmold.de/karoride/?lang=en) · [Download APK](https://github.com/242Xtro/KaroRide/releases/download/v1.2.0-preview.1/KaroRide-1.2.0-preview.1.apk) · [Deutsche Anleitung](https://github.com/242Xtro/KaroRide/blob/main/README.de.md) · [Ko-fi](https://ko-fi.com/karoride)

## What it does

Add KaroRide as a full ride page with large controls, or put CAMERA START / STOP next to your usual ride data. Battery, free storage and clip length are available. A recording is shown as running only after the camera confirms it. If Bluetooth drops, the state becomes Unknown and the last confirmed status stays marked.

One camera is controlled at a time. Stop or pause before switching. Pause ends the current clip; resume starts a new file. Video settings stay as they are and starting a ride never starts recording automatically.

The original Ace Pro and GO 3S in the Action Pod are the current preview models. Ace Pro wake-up needs Bluetooth Wakeup enabled and is confirmed through a connection and camera response. GO 3S wake-up remains in testing, and power-off is done on the camera after disconnecting in KaroRide. Outside-Pod use is not yet tested. X3 is next; X5 and Ace Pro 2 are planned. [Offer to test or report a problem](https://github.com/242Xtro/KaroRide/issues).

## Install and update

1. Open the website on your phone, download the APK and share it with Hammerhead Companion. Or share the APK download link from this release. Confirm Install on your paired Karoo. [Hammerhead's sideloading guide](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading).
2. Open Extensions, KaroRide, Open on the Karoo. Accept the SDK notice, allow Bluetooth and select your powered-on camera. Confirm on the camera if it asks.
3. Add CAMERA CONTROL as the only field on a new data page, or use CAMERA START / STOP alongside other fields. German names: KAMERA STEUERUNG and KAMERA START / STOPP.

Install an update over the previous project-signed APK without uninstalling first. Package de.markus.aceride, extension ID aceride, versionCode 9 and signing identity are retained. KaroRide starts in English. Choose German in the three-dot menu under Language / Sprache.

## Checks and limits

For this unchanged APK: 122 JVM simulations passed, Android release build passed, byte-identical offline rebuild passed. Lint: 0 errors, 18 warnings. Previous basic Ace Pro and GO 3S Action Pod controls were reported working by the user. The new native language picker and the full hardware test matrix still need device checks. Simulations and a build are not hardware tests.

APK: KaroRide-1.2.0-preview.1.apk, 27,787,346 bytes, Android API 31 or newer, arm64-v8a.

SHA-256: c8973c6097b3d06271db40eddf57bb94ce8b523a78c978f0b98d541705a746e2

The app works over Bluetooth without a phone on the ride. Local preferences stay on the Karoo and videos stay on the camera. Own code and karoo-ext use Apache-2.0; the manufacturer SDK stays proprietary. SDK packages, credentials and private signing keys are not included in the source repository. Independent project, no manufacturer partnership. Ko-fi support is voluntary and the app remains free.

## Deutsch

KaroRide steuert deine ursprüngliche Ace Pro oder GO 3S im Action Pod vom Karoo 3 aus. Als ganze Fahrtseite mit großen Tasten oder als KAMERA START / STOPP neben deinen Fahrdaten. Akku, freier Speicher und Cliplänge sind sichtbar. REC erscheint erst nach Kamerabestätigung; bei Funkverlust wird der Zustand Unbekannt und der letzte bestätigte Stand bleibt gekennzeichnet.

Eine Kamera zur Zeit steuern, vor einem Wechsel stoppen oder pausieren. Pause beendet den Clip; Weiter beginnt eine neue Datei. Videoeinstellungen bleiben bestehen, eine Fahrt startet keine Aufnahme automatisch. Aufwecken der Ace Pro braucht Bluetooth Wakeup. GO 3S: Aufwecken im Test, Ausschalten am Gerät nach dem Trennen. Außerhalb des Pods noch nicht getestet. X3 als Nächstes, X5 und Ace Pro 2 geplant. Tester und Fehlerberichte über Issues willkommen.

1. APK auf dem Handy herunterladen und über Teilen an Hammerhead Companion geben. Alternativ den APK-Downloadlink dieses Releases teilen. Installation am gekoppelten Karoo bestätigen.
2. Extensions, KaroRide, Öffnen. SDK-Hinweis bestätigen, Bluetooth erlauben, eingeschaltete Kamera auswählen und gegebenenfalls am Gerät bestätigen.
3. KAMERA STEUERUNG als einziges Feld auf einer neuen Datenseite nutzen oder KAMERA START / STOPP neben die üblichen Felder setzen.

Als Update über die bestehende Projekt-APK installieren, nicht vorher deinstallieren. Englisch ist Standard, Deutsch ist im Drei-Punkte-Menü unter Language / Sprache wählbar. Diese APK ist unverändert. 122 Simulationen, Release-Build und bytegleicher Offline-Nachbau bestanden; neue Sprachauswahl und vollständige Hardwarematrix noch am Gerät prüfen.

[Webseite](https://hellmold.de/karoride/) · [Deutsche Anleitung](https://github.com/242Xtro/KaroRide/blob/main/README.de.md) · [Ko-fi](https://ko-fi.com/karoride)
