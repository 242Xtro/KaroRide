# Contributing

Please follow the [community guidelines](CODE_OF_CONDUCT.md). Contributions,
questions and bug reports in English or German are welcome.

Keep manual control, clear confirmed/unknown states and small ride-friendly views.
No automatic recording, camera setting changes or aggressive wake/reconnect loops.

Run the Java simulations and the appropriate Android build before proposing a change.
Test camera behavior on the exact model; label simulations separately from hardware.
The SDK-core JVM bridge is pinned to SDK 2.1.5 and must be reviewed on any SDK update.

Do not commit manufacturer SDK packages/source companions, private credentials,
signing keys, logs or device identifiers. New contributors need their own authorised
SDK access. Use your own signing key for development; only project-signed releases
can update an existing project-signed installation.

No cloud build with SDK credentials is configured. Issue templates accept DE/EN.
