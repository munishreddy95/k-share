# Contributing to K-Share

Keep changes focused and include the behavior and validation in your pull request.
Contributions are offered under the project’s MIT License; contribute only code
and assets you have the right to license.

## Setup and checks

Use Node.js 22.12 or newer for the desktop toolchain. Run `npm test` from the root
for the branding/docs checks and the existing desktop, relay, short-link, and extension suites.
Node tests use local ports and do not need an Electron binary or external live service.
Use `npm --prefix desktop ci` to install the actual desktop app.

For Android use JDK 17 and SDK platform 36, then run:

```sh
./gradlew assembleDebug lintDebug
```

Point `ANDROID_HOME` or an ignored `local.properties` at your SDK. Do not commit
release keystores, signing settings, `.env` files, generated builds, or received files.

## Branding and compatibility

Use K-Share for user-visible names. Read [Branding compatibility](docs/BRANDING.md)
before renaming installed-app, protocol, configuration, storage, or deployment identifiers.
Those require migration planning. User docs should use current UI labels and describe
which operations need a local network, relay, browser permission, or approval.

## Reporting

Use GitHub issues for reproducible ordinary bugs. Include the client, platform,
steps, and sanitized error. Follow [SECURITY.md](SECURITY.md) for vulnerabilities.
