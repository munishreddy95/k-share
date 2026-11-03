# K-Share Short Links

Small standalone Node.js URL shortener for K-Share public links. All `example.com`
domains below are placeholders; replace them with your own deployment addresses.

It creates tiny URLs like:

```text
https://tiny.example.com/a8kp2rq
```

and redirects them to the actual K-Share global share URL:

```text
https://abc123.share.example.com/
```

## Features

- No external npm dependencies.
- Lowercase random or custom slugs.
- Optional expiry per link.
- Token-protected create/list/delete APIs.
- Relay-only mode: public users can open short links, but cannot create them.
- Atomic JSON persistence.
- Basic create-rate limiting.
- Docker and Docker Compose ready.
- Nginx-friendly: app listens on local HTTP, TLS stays in Nginx/Certbot.

## Local Run

Use Node.js 22.12 or newer for the environment-file command below.

```bash
cp .env.example .env
# Edit .env before starting; Node does not load it automatically.
node --env-file=.env server.js
```

For local testing without a token, set:

```env
ALLOW_PUBLIC_CREATE=true
CREATE_TOKEN=
ENABLE_CREATE_UI=true
```

For production, keep `ALLOW_PUBLIC_CREATE=false` and `ENABLE_CREATE_UI=false`.
The relay should call `POST /api/links` with `Authorization: Bearer <CREATE_TOKEN>`.
Custom slugs are normalized to lowercase before storing, so `Phone-Share` becomes `phone-share`.

## API

Create a link:

```bash
curl -s -X POST http://127.0.0.1:4100/api/links \
  -H 'content-type: application/json' \
  -H 'authorization: Bearer change-this-long-random-token' \
  -d '{"url":"https://abc123.share.example.com/","slug":"phone-share"}'
```

Response:

```json
{
  "slug": "phone-share",
  "url": "https://abc123.share.example.com/",
  "shortUrl": "https://tiny.example.com/phone-share",
  "createdAt": "2026-07-25T10:00:00.000Z",
  "expiresAt": null,
  "visits": 0,
  "lastVisitAt": null
}
```

Create an expiring link:

```bash
curl -s -X POST http://127.0.0.1:4100/api/links \
  -H 'content-type: application/json' \
  -H 'authorization: Bearer change-this-long-random-token' \
  -d '{"url":"https://abc123.share.example.com/","ttlSeconds":86400}'
```

List links:

```bash
curl -s http://127.0.0.1:4100/api/links \
  -H 'authorization: Bearer change-this-long-random-token'
```

Delete a link:

```bash
curl -s -X DELETE http://127.0.0.1:4100/api/links/phone-share \
  -H 'authorization: Bearer change-this-long-random-token'
```

Health check:

```bash
curl -s http://127.0.0.1:4100/healthz
```

## Docker

```bash
cp .env.example .env
docker compose up -d --build
```

The compose file binds to `127.0.0.1:4100` and attaches the service to the private `flux-fileshare` Docker network. When the relay also runs in Docker, it should call `http://flux-tiny-url:4100/api/links`.

## Nginx Example

```nginx
server {
    listen 80;
    server_name tiny.example.com;

    location / {
        proxy_pass http://127.0.0.1:4100;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_buffering off;
        proxy_request_buffering off;
    }
}
```

Certbot can add SSL to this server block.

For production, block public create/list/delete calls in Nginx. Relay should use the private Docker URL, not public `/api/`:

```nginx
location ^~ /api/ {
    return 404;
}
```
