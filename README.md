# GameHub

GameHub is an Android-first retro gaming hub with a custom visual identity, a local game-file library, and a planned homebrew catalogue called **Juegos**.

## Current status: early prototype (0.1.0)

The first Android project scaffold is now in place.

### Included
- Kotlin + Jetpack Compose Android application.
- Custom purple/cyan GameHub launcher icon.
- Navigation for Inicio, Biblioteca, Juegos, Descargas, and Ajustes.
- Local file picker to add game files to the in-app library list.
- Starter dark theme and responsive Compose layouts.
- GitHub Actions workflow to build and upload a debug APK artifact.

### Not implemented yet
- **Emulation engine:** importing a file currently adds it to the library; it does not run it. A compatible engine/core still needs to be selected and integrated.
- **Store backend:** the Juegos screen is an honest placeholder. It does not yet download or install content.
- Persistent library metadata, save states, controller mapping, and actual download management.
- Release signing and a tested APK release.

## Build

Requirements: JDK 17 and Gradle 8.9.

From the repository root:

```bash
gradle assembleDebug
```

The debug APK should be generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow runs the same build on pushes and pull requests to `main`. Once it succeeds, download the `gamehub-debug-apk` artifact from that workflow run.

## Project structure

- `app/src/main/java/com/sfj3861/gamehub/MainActivity.kt` — Compose UI and local file import.
- `app/src/main/res/drawable/ic_gamehub.xml` — custom vector app icon.
- `app/src/main/AndroidManifest.xml` — Android app entry point.
- `.github/workflows/android.yml` — CI build workflow.

## Planned milestones

1. Verify the Android scaffold builds and installs.
2. Select and integrate an open-source emulation engine while complying with its licence.
3. Add persistent library entries, game details, and controller support.
4. Connect the Juegos catalogue to a versioned metadata feed.
5. Add verified downloads, compatibility checks, and error recovery.
6. Test on real Android devices and prepare a signed release.

Only content that is authorized for distribution should be listed in the future catalogue. Engine integration will depend on the target systems, licence obligations, and device compatibility.
