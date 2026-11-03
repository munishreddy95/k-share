# Third-party notices

The MIT License at the repository root covers original K-Share contributions.
Third-party components keep their own copyright and license terms.

- Android depends on ZXing Android Embedded 4.3.0 and its transitive dependencies.
  ZXing components use Apache-2.0; preserve their notices when redistributing.
- Gradle wrapper scripts and tooling use Apache-2.0. Existing copyright and license
  headers in the wrapper scripts are retained.
- Electron uses MIT and includes Chromium, Node.js, and other components with their
  own notices. Preserve the license/notice bundle included with Electron when
  distributing the desktop application.
- Desktop npm dependency versions and declared licenses are recorded in
  `desktop/package-lock.json`. The relay and short-link service have no external npm dependencies.

The package manifests’ MIT fields do not relicense these dependencies. A native or
Electron binary distribution needs the relevant bundled third-party notices as
well as this project’s MIT notice. Do not replace upstream headers when renaming the app.
