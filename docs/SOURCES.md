# Sources, versions and licenses

Actual build inputs are pinned, rather than claiming latest-version compatibility.

| Component | Version / commit | Use |
| --- | --- | --- |
| [karoo-ext](https://github.com/hammerheadnav/karoo-ext) | 1.1.9 / `f79f103cd0e1a9808db64474796b30d822cc0b17` | Unmodified public runtime; Apache-2.0, original copyright retained. Local build adapter. |
| Insta360 Camera SDK | 2.1.5 | Proprietary authorised binary dependency; not included in this source repository. |
| Temurin JDK | 17.0.20.1+1 | Project-local macOS aarch64 build tools. |
| Gradle / AGP / Kotlin | 8.13 / 8.13.2 / 2.3.20 | Locked build, dependency checksums included. |
| Android API / Build Tools | 36 / 36.0.0 | Compile only; minimum app API 31, target 34, arm64-v8a. |

Reviewed references, not incorporated application source/assets:

- [ClipRide](https://github.com/yrkan/clipride/tree/74dec2d9f41da06b8d73fbdcd42e7e2f9773ba4a), MIT, Copyright 2026 Yuri Kan: Karoo interaction reference. The KaroRide website uses its own layout, styles, copy and SVG illustrations. No ClipRide page code, images or GoPro adapter copied.
- [KarooInsta360Ctl](https://github.com/jpa5635/KarooInsta360Ctl/tree/623f65198d1183db8de9c663c3048960142c6c2d), Apache-2.0: evaluated Bluetooth adapter. No source copied.
- [insta360ctl](https://github.com/xaionaro-go/insta360ctl/tree/f94193ce03c5af0921a9992bfd1af6bd946150d0): no root license in reviewed snapshot; no source copied.
- [Camera integration docs](https://github.com/Insta360Develop/Insta360-Developer_Docs/tree/caa3700d0bf2a1b96317211f64431bcd0dfe11c0): API reference, not a complete SDK package.
- [Android SDK reference repository](https://github.com/Insta360Develop/Android-SDK/tree/d2f910c8a6a02f6d694e7d6be7a49b345b25e221): reviewed; no manufacturer Demo implementation copied.

Original snapshots were reviewed 12 October 2026; public release/reference pages
were checked again 13 October. Research archives, private SDK packages and local
hardware logs are deliberately excluded from the public source.

[SDK developer access](https://www.insta360.com/developer/home),
[SDK terms](https://www.insta360.com/support/supportcourse?post_id=20734),
[Karoo API](https://hammerheadnav.github.io/karoo-ext/).
The SDK license governs the manufacturer's binaries. KaroRide's Apache license
only covers its own code and the appropriately licensed karoo-ext component.
See LICENSE, NOTICE and licenses/ for preserved notices.

Published binaries require the manufacturer's distribution conditions and the
app's local SDK notice. This project does not include an SDK account, credentials,
manufacturer implementation source or the official release signing key.
