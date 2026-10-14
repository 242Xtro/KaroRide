# Build / Bauen

Pinned toolchain: Temurin 17.0.20.1+1, Gradle 8.13, AGP 8.13.2, Kotlin 2.3.20,
Android compile API 36 / build tools 36.0.0. App min API 31, target API 34,
arm64-v8a. Camera SDK 2.1.5; vendored public karoo-ext 1.1.9 at commit
`f79f103cd0e1a9808db64474796b30d822cc0b17` with original license/copyrights.

The included installers are **optional macOS Apple Silicon project-local scripts**.
Review them and the toolchain lock before running. They download tools inside
`.toolchain/`; no Homebrew or global Java/Gradle configuration is changed.
Google's SDK license and the manufacturer's SDK terms must be accepted by you.
Do not run an installer without approving these downloads/licenses yourself.

After that approval:

```sh
python3 tools/install-toolchain.py
python3 tools/install-android-sdk.py
python3 tools/create-signing-key.py
```

Get authorised Android SDK access through https://www.insta360.com/developer/home.
Using the credentials supplied to you, create a private, untracked `secrets/sdk.properties`:

```properties
username=YOUR_OWN_APPROVED_SDK_USERNAME
password=YOUR_OWN_APPROVED_SDK_PASSWORD
```

Set the file to mode 0600. Never copy these values into chat, logs or a commit.
The signing script generates your own private key and local `signing.properties`;
it does not provide the project's release key. Keep that directory private.

```sh
sh tools/test-core.sh
sh tools/build.sh :core:simulationTest :app:assembleHardwareRelease :app:lintHardwareRelease
# After the first dependency resolution, verify an offline build:
sh tools/build.sh --offline --rerun-tasks :app:assembleHardwareRelease
```

Dependency locks/checksum metadata are included. Output:
`app/build/outputs/apk/hardware/release/app-hardware-release.apk`.
Java simulations alone can be run with JDK 17 and `JAVA_HOME` without Android SDK access.

No global debug key, firmware update, device installation, cloud build or automatic
GitHub release is performed by these commands. A successful build is not a Bluetooth test.

Deutsch: Werkzeuge und Caches liegen lokal im Projekt. Herstellerzugang selbst
beantragen; Zugangsdaten nie veröffentlichen. Ein eigener Community-Build hat
eine andere Signatur und ersetzt keine Projektinstallation als Update.
