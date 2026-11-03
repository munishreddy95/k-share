# K-Share branding and compatibility

K-Share is the application name across Android, desktop windows and web pages,
relay messages, short-link pages, browser extension, package descriptions, and user
documentation. The extension popup uses the K mark; its launcher assets already
use the K design; the desktop window, macOS dock, and browser-backed UI
now use that same icon. New received files use `Downloads/K-Share`.

Previously received files remain in their old folder. The rename does not migrate,
move, or delete user files. Existing installed Android apps keep their package
identity so a correctly signed update remains an update rather than a separate app.

The following implementation/deployment identifiers deliberately remain compatible:

- Android package/application ID and service actions: `com.fluxfileshare`.
- Nearby QR scheme and wire markers: `fluxnearby://` and `FLUX_*`.
- Existing `FLUX_RELAY_*` / `FLUX_DESKTOP_RELAY_*` configuration keys.
- Browser storage, cookie, IPC, and partial-transfer keys; internal QR/database globals.
- Docker service/network/volume identities used by existing deployments.

These are compatibility identifiers, not the app title. Renaming them without a
migration would break pairing, updates, saved access, deployment data, or resumed
transfers. Future protocol or storage migrations should be versioned and tested.
