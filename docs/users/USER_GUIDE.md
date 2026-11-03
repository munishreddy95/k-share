# K-Share user guide

[Documentation home](../README.md) · [Getting started](GETTING_STARTED.md) · [Troubleshooting](TROUBLESHOOTING.md)

## Choose a sharing mode

Nearby copies files to the receiving device. Local Web and Live Share let visitors
browse or download selected files while the sharing device remains online. Local
Web needs a reachable local network; Live Share needs internet access and a relay.

## Send files from phone to desktop

1. Put the phone and computer on the same local network.
2. In K-Share Desktop, choose **Receive**, check **Desktop IP**, and select **Start receiver**.
3. On Android, choose **Nearby → Desktop QR** and scan the desktop QR.
4. Select the files on the phone, then send them using the phone’s transfer controls.
5. Follow progress in the desktop’s **Received files** list.

New received files go to `Downloads/K-Share` by default. The desktop displays the
actual receive folder; **Open receive folder** opens it. Previously received files
in the old folder are left where they were.

Use **Cancel session** or **Stop receiver** to end the desktop session. A cancelled
transfer may leave partial data for resuming; a completed file is not revoked by
ending the session.

## Send files from desktop to phone

1. On the desktop, choose **Send**, then **Show QR**.
2. On the phone, choose **Nearby → Desktop QR** and scan that QR.
3. Select **Choose files** on the desktop and pick files to send.
4. Follow progress in **Sent files** and on the phone.

The phone saves completed transfers in `Downloads/K-Share`. You can also use the
phone’s Nearby **Sender** and **Receiver** flows to pair phones. Follow the QR
instructions shown for the selected role. For a stale session, refresh the QR and
scan again instead of using a QR captured earlier.

If switching desktop modes prompts **Switch sharing mode?**, choose **Stay here**
to preserve the current session or **Switch mode** to cancel it and proceed.

## Share Android files through a local browser link

1. On the phone, open **Web**, choose **Local**, and open the file library.
2. Grant needed media or document-folder access. Refresh the library if files are missing.
3. Use **Share** to choose files for this mode. Use **Shared** to review or remove selections.
4. Select **Start server** and copy the shown address or scan its Local QR.
5. Open that address from another device on the same reachable network.
6. If the visitor sees an access request, they enter the requested details; review
   the request on the phone and match its approval code before choosing **Approve**.

The browser shows the selected library. Visitors can search, play supported
video/audio, and download files. A file’s stream URL can be used in a compatible
external player. Playback depends on the receiving browser/player’s format support.

Use **Web visitors** to review/remove visitors. Select **Stop server** to stop local
sharing; confirm the stop dialog if shown. Removing a selection does not delete
the original file from your device.

## Share desktop files through Local Web

1. Choose desktop **Local Web**.
2. Check **Desktop IP**, select **Choose files**, and review the list.
3. Select **Start**, then open the displayed private URL or QR from the other device.
4. Review **Local sessions**. Select **Stop local share** when finished.

The desktop Local Web URL carries its access token. Anyone with that private URL
can use it while the share is running; this mode does not add the Live Share
approval step. Send it only to intended visitors.

## Create a Live Share link

On Android, choose **Web → Live**, select files specifically for Live Share, and
select **Start global link**. Review the selection dialog and choose **Create link**.
On desktop, choose **Live Share**, select **Choose files**, then **Create link**.

Wait for the relay to return a URL or QR. Share that URL privately with the intended
visitor. A short URL, when available, redirects to the full sharing URL.

Visitors may need to request access. Review **Pending visitors** or the phone’s
approval request, compare its code/details with the visitor, and choose **Approve**
or **Deny**. A supplied email or device name is not a verified identity. Review
connected visitors and remove anyone who should no longer have access.

Keep the sharing app and files available while visitors download. On desktop choose
**Stop live share**; on Android choose **Stop global link** and confirm when prompted.
An existing short URL cannot make files available after its underlying share stops.

## Share through the extension

1. In **Files**, select **Choose files** or **Add tab** and review the file list.
2. In **Link**, select **Create live link**, then **Copy link** or share its QR.
3. In **Access**, review requests, choose **Approve/Deny**, and manage connected visitors.
4. Select **Stop** in **Link** when finished.

**Add tab** shares a text file containing the tab title and URL. Selected files are
stored in extension browser storage; **Clear** removes the selection. The extension
cannot scan your whole computer or serve a LAN web address. Browser shutdown,
extension suspension, or storage limits may interrupt sharing.

## Finish safely

Stop each active sharing mode you started. Turning off a file filter or closing a
popup is not the same as stopping a share. Check that the app shows the link or
server as stopped. Visitors may keep files they have already downloaded; ending
sharing cannot delete those copies. See [Privacy and access](PRIVACY.md).
