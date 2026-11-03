# Getting started with K-Share

[Documentation home](../README.md) · [User guide](USER_GUIDE.md) · [Troubleshooting](TROUBLESHOOTING.md)

K-Share lets you choose files to copy to another device or expose through a browser
link. Choose the client that matches where your files are stored.

## Get the source

Clone the existing repository, or download its source through GitHub’s Code menu:

```sh
git clone https://github.com/munishreddy95/k-share.git
cd k-share
```

Commands below run from this repository folder unless a guide states otherwise.
GitHub access is required if the repository is private.

## Android

You need an Android 8.0 or newer device. This repository provides buildable source;
it does not establish a Play Store listing or signed release download.

To build from source, install JDK 17 and Android SDK platform 36, enable USB debugging
on your test device, and run these commands from the repository folder:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, replace `./gradlew` with `gradlew.bat`. If Gradle cannot find the SDK,
set `ANDROID_HOME` to its actual location, or put an `sdk.dir` setting in a local,
ignored `local.properties` file. Keep release signing files private.

Open **K-Share** and grant the permissions needed for the task you select:

- Media access lets K-Share discover your videos and audio.
- Camera access is used to scan pairing QR codes.
- Nearby/network permissions are used for device discovery and transfers.
- Notifications let the app show transfer/sharing status and access requests.
- Document folder grants let you scan folders you choose. The app may also offer
  All files access for broader document discovery; media scanning can work without it.

A scanned file is not automatically selected for web sharing. Review selections
under the relevant Local or Live mode before starting a link.

## Desktop

Install Node.js 22.12 or newer, open a terminal in the repository folder, then run:

```sh
npm --prefix desktop ci
npm --prefix desktop start
```

Keep the app open while transfers or shares are active. For Nearby, connect the
phone and computer to the same reachable local network. The phone scans the
desktop’s QR; the desktop does not need a camera.

The browser-backed companion starts with:

```sh
npm --prefix desktop run start:web
```

Open the UI address printed in the terminal. This mode cannot open the native
file picker. Use Electron for **Choose files** in Send, Local Web, or Live Share.

If Electron is missing, repeat `npm --prefix desktop ci` with normal install scripts
enabled. The `--ignore-scripts` option used in CI skips its application download.

## Browser extension

1. Open `chrome://extensions` in Chrome or `edge://extensions` in Edge.
2. Enable **Developer mode**.
3. Select **Load unpacked** and choose the repository’s `browser-extension` folder.
4. Open **K-Share**, select **Choose files**, and add only the files you intend to share.
5. In **Link**, choose **Create live link** and wait for the share URL.

The browser and extension must remain running to serve files. The extension’s
**Add tab** action creates a text file containing the current tab’s title and URL;
it does not copy the web page or capture the screen.

The default relay is configured in the extension. For another relay, an operator
must update its allowed origins as well as its relay field; see
[Self-hosting](../SELF_HOSTING.md#extension-relay-changes).

## First session

For a phone-to-desktop copy, choose desktop **Receive**, then **Start receiver**.
On Android, open **Nearby → Desktop QR** and scan the desktop QR. Select files on
the phone and follow its send flow. For browser access, follow the Local Web or
Live Share instructions in the [User guide](USER_GUIDE.md).
