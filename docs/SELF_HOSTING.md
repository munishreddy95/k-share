# Self-hosting K-Share

[Documentation home](README.md) · [Security](../SECURITY.md)

The Android app, desktop app, and extension can share through the relay.
Live sharing needs a reachable relay with HTTPS/WebSocket proxying; local sharing
and Nearby do not require it. The sample hostnames in this repository describe
the existing deployment; replace them with domains you control for your own service.

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


## Compatibility and stored links

The existing Docker service, network, and volume names retain their legacy `flux`
identifiers so upgrades continue to use the same stored short links and network.
Do not remove volumes as part of a branding update. Back up the short-link data
volume before deployment changes. See [branding compatibility](BRANDING.md).

## Direct Node environment files

The services do not load `.env` automatically. With Node.js 22.12 or newer, use
`node --env-file=.env server.js` from the relevant service folder, or supply
environment variables through your process manager. Compose loads the files
explicitly through its `env_file` configuration.

## Extension relay changes

Changing the extension’s Advanced relay field alone does not permit a different
origin. Update both `host_permissions` and `content_security_policy.extension_pages`
in `browser-extension/manifest.json`, then reload the unpacked extension.
