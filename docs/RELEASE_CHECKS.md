# K-Share publication checks

Review date: November 3, 2026. Target repository: `munishreddy95/k-share`.

## Verified locally

| Check | Result |
| --- | --- |
| Android debug APK build | Pass: `assembleDebug` |
| Android lint | Pass: zero errors; 45 warnings remain |
| Built APK identity | Application label is K-Share; existing `com.fluxfileshare` package identity preserved |
| Desktop tests | Static UI, Nearby send/receive protocol, HTTP local/live share tests pass |
| Relay tests | Pass |
| Short-link service tests | Pass |
| Browser extension source checks | Pass |
| Visible branding and MIT metadata | Pass across application source and package manifests |
| Documentation links and headings | Pass |
| Desktop dependency audit | Zero reported advisories after compatible Electron/Undici updates |

The repository root `npm test` runs the branding/documentation checks and all Node
suites. These test actual transfer and HTTP flows against local test servers without
contacting the deployed production relay. CI is configured to repeat Node tests,
desktop auditing, and Android build/lint on pushes and pull requests.

The Gradle wrapper download initially timed out. Validation used the same official
Gradle 8.14.3 distribution downloaded separately and verified against its published
SHA-256 checksum. Android checks used JDK 17 and SDK platform 36. The wrapper remains
pinned to that same version; build validation did not change the toolchain target.

## Branding review

Updated the desktop window/application name and HTTP pages, Android download-folder
name and summary, relay offline/token errors and status messages, short-link page
titles, browser popup K mark, desktop window/dock/favicon, package names/descriptions, build project name, and
all current user guides. Launcher graphics already show the K design. The browser
icon’s SVG source is now named `k-share.svg`.

Old app titles are rejected by `npm run check:branding`. Protocol schemes, Android
application/service identities, environment variable names, storage keys, and
Docker identities remain compatible as documented in [BRANDING.md](BRANDING.md).
The rename leaves old received files in their existing folder.

## Publication boundaries

- This update publishes source, MIT licensing, and documentation, not a signed binary release.
- Physical-phone interoperability and manual desktop/extension sessions remain device-testing work.
- Passing lint does not mean all warnings were fixed; the existing warning set remains.
- The relay is part of the Live Share trust boundary; visitor details are not verified identities.
- Private vulnerability-reporting availability depends on GitHub repository settings.
- Generated APKs, environment/signing files, dependency folders, and build caches are excluded from Git.

Use [Contributing](../CONTRIBUTING.md) to reproduce checks and
[Self-hosting](SELF_HOSTING.md) to operate your own services.
