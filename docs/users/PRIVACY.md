# Privacy and access in K-Share

[Documentation home](../README.md) · [User guide](USER_GUIDE.md)

## Choose the files you expose

Discovery and sharing are separate steps. Review the **Shared** selection for the
mode you are using. Local and Live selections are separate. Folder permissions
allow the app to read the granted location; they do not mean every discovered file
must be published. Removing a shared selection does not delete the original file.

The extension stores explicitly selected file data in the browser’s extension
storage. Use **Clear** to remove selected files there. **Add tab** creates a text
file containing the active tab’s title and URL; check it for private information.

## Treat links and QR codes as private

Local and global links can carry access tokens. QR payloads can contain pairing
or session information. Share them only with the intended recipient and do not
post them in issues, screenshots, or public messages.

Android web flows and Live sharing can ask the owner to approve visitors. Desktop
Local Web uses its private token-bearing URL without a separate Live approval step.
A visitor’s email and device name are self-reported. Compare the displayed approval
code and confirm who they are through a channel you already trust.

## Understand the relay

Nearby and Local Web use device-to-device/local-network connections. Live Share
routes visitor requests and file responses through the configured relay. A short
URL points to the full sharing URL; it is not a second file-storage service.

Do not assume local HTTP or the Nearby transfer protocol encrypts traffic or
verifies people’s identities. Use trusted networks for local testing and HTTPS/WSS
for internet-facing deployments. The relay is part of the trust boundary; do not
promise end-to-end secrecy from it. Service operators should protect short-link
records and access tokens as sensitive data.

## Stop and revoke access

Review visitors and remove sessions you no longer recognize. Stop the Local or Live
share when done; do not rely on closing a popup. A download already completed on
another device cannot be revoked or deleted by stopping the share.

Avoid sharing private documents as a connectivity test. Review source and operator
configuration before using K-Share with sensitive data. See
[security reporting](../../SECURITY.md) for vulnerability handling.
