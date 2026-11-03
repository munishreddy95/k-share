# Troubleshooting K-Share

[Documentation home](../README.md) · [Getting started](GETTING_STARTED.md) · [User guide](USER_GUIDE.md)

## Files are missing from the library

Check Android media permissions, refresh the scan, and confirm the file is in a
category the app scans. For documents, grant access to the specific folder or use
the document-discovery permission option. Files listed in the library still need
to be selected for the relevant Local or Live sharing mode.

## A local link will not open

Confirm the server/share is running and that both devices are on the same network.
Use the actual address shown by K-Share, including the port and any access token.
`localhost` on a visitor’s device refers to that device, not the sharing phone or
computer. Guest Wi-Fi, VPNs, and client isolation can prevent device-to-device access.

On desktop, choose the network interface that the phone can reach under **Desktop IP**.
Check firewall permission for the local server without disabling your firewall.
Restart sharing and use its new address if the IP changed. Local links do not
become internet links when forwarded to someone on another network; use Live Share.

## QR scanning fails or reports a different session

Grant camera permission and scan the correct QR for the flow. For desktop transfers,
use the phone’s **Nearby → Desktop QR** flow. Refresh the desktop QR and scan again
if the session has been restarted. A screenshot of an old QR can contain an expired
session token. Verify that the phone can reach the selected Desktop IP.

## A Nearby transfer pauses or fails

Keep both apps running and devices connected to the same network. Check free space
and write permissions on the receiving device. Follow an offered resume action
using the same file/session conditions; do not assume every failed transfer can
resume. If pairing no longer matches, cancel, create a new session, and rescan.
Already completed files remain in the receive folder.

## Where are my received files after the rename?

New transfers use `Downloads/K-Share` by default. Older files remain in the previous
`Downloads/Flux File Share` folder. The rename does not move or delete them. On
desktop, check the displayed **Receive folder** and **Open receive folder**.

## The desktop file picker does nothing

The browser-backed companion displays “File picker works in the desktop app.”
Run `npm --prefix desktop ci` and `npm --prefix desktop start` to use Electron’s
native picker. Install with normal scripts enabled so Electron is downloaded.
The browser extension has its own independent file picker and does not need Electron.

## Live Share stays offline or says the relay is not configured

Check internet connectivity and keep the sharing app running. The Android build
needs relay host, port, TLS, public-base, and agent-path settings. Desktop uses its
configured/default relay. Ask your operator to check the relay and HTTPS/WebSocket
proxy; see [Self-hosting](../SELF_HOSTING.md).

The short-link service is optional. A failing short link and an offline underlying
share are different problems; ask the sender for a newly created share URL. Never
post complete private sharing URLs in a public bug report.

## Visitors cannot enter or were removed

Check pending requests and compare approval codes before approving. A denied,
expired, or removed session needs a fresh request. Copy the full private link,
including its token. Visitor email/name fields are supplied by the visitor and are
not proof of their identity.

## Browser playback fails

Try downloading the selected file or using its stream URL in a compatible player.
A browser may not support a file’s container or codec even though downloading works.
Keep the sharing device online while seeking or streaming. Do not assume every
video format can play inline.

## The extension disconnects or cannot use another relay

Keep the browser running; a service worker stopped by the browser cannot serve
files. Reopen the popup, check its status, and recreate the link if needed. Large
files may hit browser storage limits. Changing **Advanced relay** alone does not
change extension permissions; the operator must update the manifest’s allowed
origins and reload it. See [extension setup](../../browser-extension/README.md).

## Report a bug

Use [GitHub issues](https://github.com/munishreddy95/k-share/issues). Include the
client, operating system/browser, app version or commit, steps, visible error, and
whether devices share one network. Remove credentials, active QR payloads, private
URLs, email addresses, personal file paths, and file content from screenshots/logs.
For vulnerabilities follow [SECURITY.md](../../SECURITY.md) instead of posting details publicly.
