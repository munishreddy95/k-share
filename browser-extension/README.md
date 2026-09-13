# K-Share Browser Extension

Standalone browser extension for K-Share Live. It does not require the desktop app.

## What It Can Do

- User selects files from the extension popup.
- Extension stores those files in browser extension storage.
- Extension connects to the K-Share relay as a Live Share agent.
- Relay returns a share link and QR.
- Visitors request approval with email/device details.
- Chrome notification and extension badge alert the user when access is requested.
- Extension popup shows Approve/Deny and connected visitors.
- Approved visitors can browse, watch, and download selected files.

## What It Cannot Do Alone

- It cannot silently scan the whole computer.
- It cannot run a LAN web server from the browser.
- It cannot keep sharing if the browser/extension service worker is stopped by the browser.

For large/background transfers, the desktop app is still the stronger companion. This extension is intentionally independent and browser-safe.

## Load In Chrome Or Edge

1. Open `chrome://extensions`.
2. Enable `Developer mode`.
3. Click `Load unpacked`.
4. Select `/Users/munishkumar/Documents/flux-fileshare/browser-extension`.

Default relay:

```text
wss://share.fv.munishkumarm.com/agent
```

The relay can be changed from the popup Advanced section, but the extension CSP currently allows the production K-Share relay only.
