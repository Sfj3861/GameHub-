# GameHub

GameHub is an Android-first retro gaming hub with a custom visual identity, a local game-file library, a cloud-connected homebrew catalogue called **Juegos**, and a publisher screen.

## Current status: prototype 0.2.0

### Implemented in the Android app
- Kotlin + Jetpack Compose interface and custom purple/cyan launcher icon.
- Navigation for Inicio, Biblioteca, Juegos, Descargas, and Ajustes.
- Local file picker for adding files to the library list.
- **Publicar** form: game title, console, author, version, description, ROM file, server URL, publisher token, and rights confirmation.
- Multipart upload from Android to the cloud API over HTTPS.
- Catalogue loading from the cloud API.
- Download buttons using Android DownloadManager.

### Cloud API
The self-hosted API is in [server/](server/README.md). It accepts authenticated ROM uploads, records metadata and a SHA-256 hash, exposes a public catalogue, and serves published files for download. It is designed for a server with persistent storage and HTTPS.

**The cloud is not live yet.** You must deploy the API to a server, configure a private publisher token, and enter the HTTPS server URL and token in the app's Publicar screen. The catalogue URL is entered in Juegos. Do not put a publisher token into a public APK; use this publishing form only in a private/admin build until individual publisher accounts or short-lived credentials are implemented.

### Build Android APK

Requirements: JDK 17 and Gradle 8.9.

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions is configured to build a debug APK and upload it as a workflow artifact. A successful build has not yet been confirmed from this development session.

### Important limitations
- **Emulation is not integrated yet:** importing a file does not run it. The compatible engine/core still needs to be selected and integrated.
- The catalogue only supports the console labels listed in the server README.
- The server stores files on its persistent volume. Back up that volume and catalogue regularly.
- Only upload homebrew ROMs you created or are explicitly authorized to distribute. The rights checkbox is a declaration, not copyright verification.
- Before public launch, add publisher accounts, moderation/takedown workflow, upload quotas, malware/archive inspection, automated tests, and stronger production monitoring.

## Project structure
- `app/src/main/java/com/sfj3861/gamehub/MainActivity.kt` — app UI, publish upload, catalogue and download client.
- `app/src/main/res/drawable/ic_gamehub.xml` — custom vector icon.
- `server/server.js` — API server.
- `server/docker-compose.yml` and `server/Dockerfile` — self-hosting deployment.
- `.github/workflows/android.yml` — Android build workflow.
