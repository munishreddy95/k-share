# K-Share Browser Extension

A standalone extension for K-Share Live sharing. It does not require the desktop app.

[Getting started](../docs/users/GETTING_STARTED.md) · [User guide](../docs/users/USER_GUIDE.md) · [Privacy](../docs/users/PRIVACY.md)

## Load from source

1. Open `chrome://extensions` in Chrome or `edge://extensions` in Edge.
2. Enable **Developer mode** and choose **Load unpacked**.
3. Select the `browser-extension` folder inside your K-Share checkout.
4. Open K-Share from the extensions menu.

The repository supplies unpacked extension source, not a verified store listing.

## Share and approve visitors

1. Under **Files**, choose files or use **Add tab**. Add tab makes a text file with
   the active tab’s title and URL; it does not capture the page.
2. Under **Link**, select **Create live link**, then **Copy link** or share its QR.
3. Under **Access**, compare visitor details/codes and choose **Approve** or **Deny**.
4. Review connected visitors, remove unwanted sessions, and choose **Stop** under
   Link when finished. Closing the popup is not the stop action.

Files you select are stored in browser extension storage. Use **Clear** to remove
the selection. The extension cannot scan the whole computer or run a LAN server.
Browser shutdown or service-worker suspension can interrupt sharing, and large files
can hit browser storage limits. Files already downloaded by a visitor cannot be revoked.

## Relay configuration

The default relay is `wss://share.fv.munishkumarm.com/agent`. Its availability is an
operator concern; the extension is not a file-hosting service by itself.

The popup’s **Advanced relay** field controls the target, but the manifest currently
permits the configured deployment origin. To use a different relay, update both
`host_permissions` and `content_security_policy.extension_pages` in `manifest.json`,
then reload the unpacked extension. See [Self-hosting](../docs/SELF_HOSTING.md).

## Check source

From the repository root:

```sh
node browser-extension/test/extension-static.test.js
```

The original extension code is covered by the repository’s [MIT License](../LICENSE).
