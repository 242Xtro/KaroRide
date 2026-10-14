# Preview 1.2.0-preview.1

Install the project APK from the release assets, rather than building it yourself
unless you want to develop KaroRide. Source downloads do not contain an APK.

| Release property | Value |
|---|---|
| File | `KaroRide-1.2.0-preview.1.apk` |
| Bytes | 27,787,346 |
| SHA-256 | `c8973c6097b3d06271db40eddf57bb94ce8b523a78c978f0b98d541705a746e2` |
| Signing certificate SHA-256 | `c296c01df80912f846b19a1d11a6be8ffe90e7ba16faa6f9c88222deb50526e4` |
| Android package / versionCode | `de.markus.aceride` / `9` |
| Extension ID | `aceride` (retained for updates) |
| Minimum Android / ABI | API 31 / arm64-v8a |
| Camera SDK | Insta360 Camera SDK 2.1.5 |

Verify the downloaded APK using its companion checksum, for example on macOS:

```sh
shasum -a 256 -c KaroRide-1.2.0-preview.1.apk.sha256
```

Install over the previous project-signed AceRide/KaroRide APK. Do not uninstall
first if you want to retain camera profiles and local preferences. Community
builds with a different key cannot update the project-signed app.

English is the default. Open the three-dot menu → Language / Sprache to choose
German. The choice is stored on the Karoo; it does not change the camera or the
system language. No extra language download is needed.

The release build, 122 JVM simulations and byte-identical offline rebuild passed;
lint reported 0 errors and 18 warnings. Previous basic original Ace Pro and GO 3S
Action Pod controls were reported working by the user. The new native language
picker and the complete hardware matrix remain pending. These are different
kinds of evidence; see [VALIDATION.json](VALIDATION.json).

No automatic updates or cloud build is configured. Future project-signed APKs
can be installed in the same way. Uninstalling removes local app data, never
videos stored on the camera. GO 3S power-off remains a device operation.
