# K-Share Desktop

The desktop companion offers **Receive**, **Send**, **Local Web**, and **Live Share**.
The phone scans desktop QR codes; the desktop does not need a webcam.

[Getting started](../docs/users/GETTING_STARTED.md) · [User guide](../docs/users/USER_GUIDE.md) · [Troubleshooting](../docs/users/TROUBLESHOOTING.md)

## Run

From this `desktop/` folder with Node.js 22.12 or newer:

```sh
npm ci
npm start
```

For a browser-backed receiver/preview, run `npm run start:web` and open the printed
UI address. Native **Choose files** works in Electron, not browser-backed mode.
Do not use `--ignore-scripts` when you need the Electron application downloaded.

## Nearby receive and send

For **Receive**, select the correct **Desktop IP**, choose **Start receiver**, then
scan its QR using Android **Nearby → Desktop QR**. Select files on the phone and
send them. The **Received files** list shows progress. New files default to
`Downloads/K-Share`; **Open receive folder** opens the actual folder.

For **Send**, choose **Show QR**, scan it from Android’s Desktop QR flow, then
**Choose files** on the desktop. Follow the **Sent files** list. Keep both devices
on the same reachable local network. **Cancel session** ends the current pairing.

## Browser shares

**Local Web** has its own file list and private local URL. Select files, choose
**Start**, and share the full URL or QR on your local network. Use **Stop local share**
when finished. Desktop Local Web does not add Live Share’s visitor-approval step.

**Live Share** has a separate file list. Select files, choose **Create link**, and
approve intended visitors under **Pending visitors**. Keep the app online and use
**Stop live share** when done. See [Self-hosting](../docs/SELF_HOSTING.md) for relay
settings. The shipped relay hostname is an example placeholder; configure your
own relay before creating a Live Share link. See
[Privacy](../docs/users/PRIVACY.md) for access limits.

## Tests

```sh
npm test
```

These tests exercise static UI bindings, Nearby protocol send/receive, and HTTP
local/live sharing against a local test relay. They do not replace physical-device
or desktop window testing. Code is MIT-licensed; see [LICENSE](../LICENSE).
