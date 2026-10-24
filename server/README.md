# GameHub cloud publishing API

Self-hosted API for publishing **homebrew ROMs that you own or are authorized to distribute**, listing them in the public catalogue, and downloading them.

## Deploy

Requires Docker Engine and Docker Compose on a server with persistent storage. Put the server behind HTTPS using a reverse proxy (Caddy, Nginx, or your hosting platform); do not expose publisher credentials over plain HTTP.

1. Copy `.env.example` to `.env` inside the server folder, then keep `.env` out of version control.
2. Set `PUBLISH_TOKEN` to a long random secret. Do not embed this token in a public APK.
3. Run `docker compose up -d --build`.
4. Check `https://your-host/health`.
5. Configure the Android app's API base URL and publisher token through a secure/private configuration. For production, prefer a server-side authenticated publisher account or short-lived tokens instead of a shared app-wide secret.

The persistent Docker volume `gamehub_data` stores ROM files and `catalog.json`. Back it up regularly. Configure HTTPS, storage limits, monitoring, and backups before inviting public uploads.

## API

- `GET /health` — health check.
- `GET /api/games` — public catalogue, metadata and download URLs.
- `POST /api/publish` — publisher-authenticated multipart upload. Fields: `title`, `console`, `author`, `version`, `description`, `rightsConfirmed=true`, and file field `rom`. Requires `Authorization: Bearer <PUBLISH_TOKEN>`.
- `GET /api/games/:id/download` — download a published file.

Supported console labels: Game Boy, Game Boy Color, Game Boy Advance, NES, SNES, Nintendo 64, Nintendo DS, Sega Master System, Mega Drive / Genesis, PlayStation, PSP, CHIP-8, Other.

Uploads are limited to 256 MiB by default and filtered by file extension, including `.ch8`, `.c8` and `.chip8` for CHIP-8. The server calculates SHA-256 and records it in catalogue metadata. This is not malware scanning or a proof of copyright ownership. The publisher must have rights to distribute each upload. Do not upload commercial ROMs or copyrighted game dumps without permission.

## Production hardening

Before a public launch, add individual publisher accounts, moderation/takedown workflow, upload quotas, malware/archive inspection, pagination, object storage/CDN for large files, audit logs, and automated tests. The shared bearer token is suitable only for a controlled private pilot; it should not be shipped inside a distributed APK.
