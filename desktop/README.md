# Flux File Share Desktop

Desktop companion for Flux File Share nearby transfers.

The desktop does not need a webcam. It shows QR codes, the Android app scans them, and files move directly over the local network.

## Modes

### Receive From Phone

1. Click **Receive From Phone**.
2. Keep the desktop and phone on the same Wi-Fi network.
3. On Android, open Nearby Share.
4. Choose Sender.
5. Scan the QR shown on desktop.
6. Desktop shows the receive folder and transfer list.
7. Select files on the phone.
8. Received files are saved to `Downloads/Flux File Share`.

### Send To Phone

1. Click **Send To Phone**.
2. On Android, open Nearby Share.
3. Choose Receiver.
4. Scan the QR shown on desktop.
5. Desktop shows the file picker.
6. Choose files on the desktop.
7. Files are saved by the Android receiver.

## Run

Browser-backed desktop companion:

```bash
cd desktop
npm run start:web
```

Electron app:

```bash
cd desktop
npm install
npm start
```

## Test

```bash
cd desktop
npm test
```

The protocol test simulates Android sender and receiver behavior, verifying desktop receive and desktop send flows.
