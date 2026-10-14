# Changelog

## Unreleased: documentation

- README structure, camera table, footnotes and questions follow the live [project website](https://hellmold.de/karoride/).
- Title image and social preview are rendered from the website in its current style. They remain illustrations, not device screenshots.
- Website source removed from this repository. The website is maintained on hellmold.de.
- App code, signing identity, licenses, SDK files and the existing release assets are unchanged.

## 1.2.0-preview.1

- English by default; choose English/Deutsch in the three-dot menu → Language / Sprache.
- Private persistent preference, live controls/ride fields/status messages and telemetry labels.
- Isolated resource contexts; no system locale, reconnect, recording command or activity recreation on language change.
- Full local language resources, no extra dependency or permission.
- 122/122 JVM simulations; Android build and device validation tracked separately.


## 1.1.1-preview.1 (2026-10-13)

- AceRide is now visibly named KaroRide across the launcher, extension and actions.
- Package, extension/field IDs and official signing identity retained for updates.
- Local first-use notice before the Insta360 SDK initialises; accepting does not start recording.
- Separate [DE/EN project website](https://hellmold.de/karoride/), screenshot placeholders and Ko-fi support link.
- Camera-control core unchanged: original Ace Pro and GO 3S, one selected camera.

110 JVM simulations pass. Offline release reproduced byte-for-byte. Device check
of the renamed build and notice remains pending. App labels currently German.

## Previous private versions

AceRide 1.1 added camera profiles and GO 3S. AceRide 1.0 added the compact start/stop
field and removed the runtime diagnostic UI. The private development history,
credentials, SDK archives and signing keys are not part of this public source.
