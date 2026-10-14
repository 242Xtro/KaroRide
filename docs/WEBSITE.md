# Static product website

`website/karoride/` contains the standalone DE/EN product page, privacy page,
local SVG illustrations, mobile navigation and responsive styling. No build step,
font CDN, analytics, payment widget or package installation is required.

Publish this directory as `/karoride/` on your own webspace. The Hellmold homepage
and other personal projects are intentionally excluded from this repository.
The full, private upload package also includes the two updated homepage files.

Configure public links/release metadata in `assets/config.js`. Only set
`repositoryUrl` after the actual repository exists. Add the official APK and
checksum to `downloads/` when deploying; binaries are excluded from source Git.

Photo slots: `images.hero` (camera/bike, portrait or landscape crop), `images.ride`
(wide cycling image). Screenshot slots: `screenshots.control`, `ride`, `field`,
`selection`. Paths must be relative/local, e.g. `assets/images/ride.webp`.
Use only your own or appropriately licensed images. Illustrations remain visible
until configured image files load successfully. No genuine hardware screenshots
are included yet.

Parallax is bounded, scroll-driven and disabled at 760px or less and for
`prefers-reduced-motion: reduce`. There is no scroll hijacking or continuous
animation loop. The dismissible Ko-fi message appears only after the hero and
before the main support section. Dismissal lasts for the current browser session.
Language uses `hellmold_lang`, matching the existing homepage. These storage
choices are described on the project privacy page.

The website is bilingual; the native app defaults to English with German selectable in its three-dot menu. The visual
camera illustration is labelled as such and is not a live device demo.
