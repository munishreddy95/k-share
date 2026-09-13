# Flux File Share

Flux File Share is an Android app that lets a user discover files on their phone and expose only the files they choose over the local network. Other devices on the same network can open the phone's `http://ip:port/` address in a browser, view the shared file list, watch video files inline, or download files.

The app can also create an optional global Web Share link through a deployed Flux relay. The phone keeps the local web server private, opens an outbound relay connection, and people with the private global link can reach the selected shared files from outside the local network.

The repository also includes a desktop companion receiver in `desktop/`. The desktop app shows a QR code, the Android app scans it from Nearby Share Sender mode, and selected phone files are copied directly to `Downloads/Flux File Share` on the desktop. This avoids requiring a webcam on desktops or laptops.

## Features

- Automatic video and audio discovery after Android media permission is granted, plus a refresh button for rescanning on demand. Discovered files are not shared automatically.
- Document folder picker that scans user-approved folders for common documents. Granted folders are rescanned on refresh. Scanned documents are discovered, not shared automatically.
- Persisted read permissions for selected document URIs where providers allow it.
- Foreground service keeps the local HTTP server alive while sharing.
- Browser web UI served directly from the phone with tabs for videos, audio, documents, and all files.
- Browser video/audio player opens in-page and minimizes to a floating pop-up player without stopping playback.
- Native dark UI with icon controls, tooltips, and a persisted app text-size setting.
- Download route for every shared file.
- Watch/listen route for browser-playable videos and audio with HTTP byte-range support for seeking.
- Stream URL route for opening formats like MKV in VLC or another native network player.
- LAN Web Share has no external runtime server dependency.
- Optional Global Web Share relay for sharing selected phone files through a public domain or wildcard subdomain.

## Build

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a build that can create global Web Share links, point the Android app at a deployed relay:

```bash
./gradlew assembleRelease \
  -PFLUX_RELAY_HOST=share.example.com \
  -PFLUX_RELAY_PORT=443 \
  -PFLUX_RELAY_PUBLIC_BASE=https://{slug}.share.example.com \
  -PFLUX_RELAY_TLS=true \
  -PFLUX_RELAY_AGENT_PATH=/agent
```

Use `-PFLUX_RELAY_TLS=false` only for local development against a non-TLS relay.

## Install On A Connected Android Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. Open Flux File Share on the phone.
2. Grant media permission when prompted, or tap `Refresh files` / `Add documents folder`.
3. Use the `Share` filter to choose files to expose, or the `Shared` filter to remove exposed files.
4. Tap `Start`.
5. Open the shown address, for example `http://192.168.1.34:8080/`, from another device on the same local network.

Some mobile networks, guest Wi-Fi networks, VPNs, and hotspot configurations block peer-to-peer local traffic. If the page does not open from another device, confirm both devices are on the same Wi-Fi network and that client isolation is disabled.

## Global Web Share Relay

The relay lives in `relay/` and uses no third-party npm packages.

```bash
cd relay
npm test
PUBLIC_PORT=4444 AGENT_PATH=/agent PUBLIC_BASE=https://{slug}.share.example.com BASE_DOMAIN=share.example.com npm start
```

Production deployment should put HTTPS in front of the relay HTTP port. Browser traffic and Android relay traffic both go through Nginx on `443`; Android connects to the `/agent` WebSocket path. Configure DNS as either a wildcard subdomain such as `*.share.example.com` or use path mode such as `https://share.example.com/<session>/`.

Global links include a private token. Anyone with that link can access the currently shared files until the user stops the global link, so only explicitly selected files are exposed.

### Docker Relay And Tiny-URL Deployment

The root Docker Compose stack runs both the relay and tiny-url service on a private Docker network. Nginx still proxies public traffic from the VPS to `127.0.0.1`; the relay calls tiny-url internally at `http://flux-tiny-url:4100/api/links`.

```bash
cp relay/.env.example relay/.env
cp tiny-url/.env.example tiny-url/.env
```

Edit `relay/.env`:

```text
PUBLIC_BASE=https://{slug}.share.fv.munishkumarm.com
BASE_DOMAIN=share.fv.munishkumarm.com
AGENT_PATH=/agent
TINY_URL_API_URL=http://flux-tiny-url:4100/api/links
TINY_URL_CREATE_TOKEN=<same token as tiny-url CREATE_TOKEN>
```

Edit `tiny-url/.env`:

```text
PUBLIC_BASE=https://tiny.munishkumarm.com
CREATE_TOKEN=<same token as relay TINY_URL_CREATE_TOKEN>
ALLOW_PUBLIC_CREATE=false
ENABLE_CREATE_UI=false
CREATE_ALLOWED_IPS=
TRUST_PROXY=true
```

Then deploy:

```bash
docker compose up -d --build
```

If the relay is not running in Docker and is started directly on the VPS, use `http://127.0.0.1:4100/api/links` for `TINY_URL_API_URL` instead.

The default `docker-compose.yml` maps:

```text
127.0.0.1:4444 -> relay HTTP and /agent WebSocket for Nginx proxying
127.0.0.1:4100 -> tiny-url HTTP for Nginx short-link redirects
```

Your existing Nginx server block needs WebSocket upgrade headers:

```nginx
location / {
    proxy_pass http://127.0.0.1:4444;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering off;
}
```

If your Nginx config does not already define `$connection_upgrade`, add this in the `http` block:

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}
```

## Desktop Companion

```bash
cd desktop
npm run start:web
```

For the Electron app shell:

```bash
cd desktop
npm install
npm start
```

Desktop details are in `desktop/README.md`.
