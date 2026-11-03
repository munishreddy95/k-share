# K-Share

K-Share shares the files you select between Android phones, computers, and browsers.
Use Nearby on your local network, open selected files through Local Web, or create
a Live Share link through a relay for remote visitors.

The repository includes an Android app, an Electron desktop companion, a standalone
browser extension, the sharing relay, and an optional short-link service.

## User documentation

- [Getting started](docs/users/GETTING_STARTED.md): choose a client and run it from source.
- [User guide](docs/users/USER_GUIDE.md): Nearby transfers, local links, live links, approvals, and stopping sharing.
- [Troubleshooting](docs/users/TROUBLESHOOTING.md): permissions, QR pairing, network access, playback, and reconnects.
- [Privacy and access](docs/users/PRIVACY.md): what is shared and how to control visitors.
- [Documentation home](docs/README.md): all user and operator guides.

## Choose how to share

| Mode | What it does | What you need |
| --- | --- | --- |
| Nearby | Copies selected files between devices using QR pairing | Devices on the same local network; Android scans the QR |
| Local Web | Lets a browser browse, play supported media, or download selected files | The sharing device and browser on the same reachable local network |
| Live Share | Lets remote visitors access selected files through a share link | Internet access, a configured relay, and approval where requested |
| Browser extension | Shares files selected in the browser without the desktop app | The browser running and access to the configured relay |

File discovery does not automatically share every discovered file. Local and Live
sharing have separate file selections. Share only what you intend visitors to access.

## Build the Android app

Requirements: JDK 17, Android SDK platform 36 and the associated Android build tools.
The app targets Android 8.0/API 26 or newer. From the repository folder:

```sh
./gradlew assembleDebug
```

On Windows, use `gradlew.bat assembleDebug`. With a test device connected through
Android Debug Bridge:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **K-Share** on the phone and follow [the User guide](docs/users/USER_GUIDE.md).
Release APKs need your own private signing configuration in ignored local files.
See [Contributing](CONTRIBUTING.md) for development and validation.

## Run the desktop app

Use Node.js 22.12 or newer. From the repository folder:

```sh
npm --prefix desktop ci
npm --prefix desktop start
```

The desktop offers **Receive**, **Send**, **Local Web**, and **Live Share**. New
Nearby transfers are saved to `Downloads/K-Share` by default. Files received under
the old name remain in their existing folder; they are not moved or deleted.

For the browser-backed companion, run `npm --prefix desktop run start:web`.
Its native file picker is unavailable; use Electron to select desktop files.
See [desktop setup](desktop/README.md) for the workflows.

## Load the browser extension

Open your browser’s extensions page, enable **Developer mode**, select
**Load unpacked**, and choose the `browser-extension` folder in this repository.
See [extension setup](browser-extension/README.md) for relay settings and limitations.
The source supports unpacked loading; this repository does not establish a store listing.

## Host the relay and short-link service

Live Share uses the relay in `relay/`. Shipped relay and short-link domains are
`example.com` placeholders; configure your own service before using Live Share.
Local/Nearby sharing does not need a relay.
The short-link service in `tiny-url/` is optional. For a self-hosted stack:

```sh
cp relay/.env.example relay/.env
cp tiny-url/.env.example tiny-url/.env
# Replace sample domains and token values in both files before deployment.
docker compose up -d --build
```

Follow [Self-hosting](docs/SELF_HOSTING.md) for HTTPS, proxying, matching service
tokens, Android build settings, and extension relay permissions. Existing config
and protocol identifiers remain compatible; see [Branding compatibility](docs/BRANDING.md).
The repository contains no production credentials. Keep environment files and
signing keys out of Git.

## Verification and contributions

Run `npm test` from the repository root with Node.js 22.12 or newer. The Node test
suites do not need the Electron download. See [Contributing](CONTRIBUTING.md) for
Android checks and [Release checks](docs/RELEASE_CHECKS.md) for current validation
and limitations. Report ordinary bugs through this repository’s GitHub issues;
report vulnerabilities using [SECURITY.md](SECURITY.md).

## License

Original K-Share contributions are available under the [MIT License](LICENSE).
Third-party components keep their own licenses; see [Third-party notices](THIRD_PARTY_NOTICES.md).
