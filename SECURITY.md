# Security policy

K-Share is a developing file-sharing project. No guaranteed security response time
or supported production release schedule is established.

## Report a vulnerability

Use the repository’s private reporting page if GitHub private vulnerability reporting
is enabled: https://github.com/munishreddy95/k-share/security/advisories/new.
If that route is unavailable, open an issue asking the maintainer for a private
reporting channel without including exploit details, active links, tokens, or files.
Do not post a vulnerability’s sensitive details publicly while arranging contact.

## Deployment boundaries

- Share only explicitly selected files and review visitor requests.
- Treat complete sharing URLs, QR payloads, access cookies, service API tokens, and
  signing keys as private. Do not commit environment/signing files.
- Visitor names and email addresses are self-reported, not identity verification.
- Local HTTP and Nearby transfers must not be assumed confidential against network observers.
- Live sharing trusts the relay; use HTTPS/WSS and protect proxy/service configuration.
- Keep relay API tokens strong, matching between services, and separate from share links.
- Preserve short-link data backups and review access when updating deployments.

User-facing guidance is in [Privacy and access](docs/users/PRIVACY.md).
