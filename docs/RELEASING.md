<!--
  Vibrato — a free, open source IPTV player.
  Copyright (C) 2026 The Vibrato Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Releasing

## The signing key

**The keystore is the single irreplaceable artefact in this project.** Android identifies
an app by its signing key; lose it and no existing install can ever be upgraded again. The
only remedy is a new application id, which is a new app to every user.

Back it up somewhere that survives losing this machine, and keep the passwords with it but
not in the same file.

Create one once:

```bash
keytool -genkeypair -v \
  -keystore vibrato-release.jks \
  -alias vibrato \
  -keyalg RSA -keysize 4096 -validity 10000
```

`-validity 10000` is about 27 years. A key that expires during the app's life cannot sign
an upgrade, so this is deliberately long.

## Building a signed release locally

Create `keystore.properties` in the repository root — it is gitignored, along with `*.jks`
and `*.keystore`:

```properties
storeFile=/absolute/path/to/vibrato-release.jks
storePassword=…
keyAlias=vibrato
keyPassword=…
```

Then:

```bash
./gradlew :app:assembleRelease
```

Without those values the build still succeeds and produces `app-release-unsigned.apk`. It
is never signed with the debug key as a fallback: an APK silently signed with a throwaway
key cannot be upgraded over a real install, and the failure would only surface for users.

## Releasing through CI

Tagging `v*` runs `.github/workflows/release.yml`, which builds, tests, runs detekt and
lint, assembles a signed release, enforces the 25 MB budget (AC-NFR-02), attaches the APK
and its SHA-256, and opens the release as a **draft** for a human to publish.

Required repository secrets:

| Secret | What it holds |
|---|---|
| `VIBRATO_KEYSTORE_BASE64` | The `.jks`, base64-encoded (`base64 -w0 vibrato-release.jks`) |
| `VIBRATO_KEYSTORE_PASSWORD` | Keystore password |
| `VIBRATO_KEY_ALIAS` | Key alias |
| `VIBRATO_KEY_PASSWORD` | Key password |

The decoded keystore is written to the runner's temp directory, outside the workspace, and
deleted in an `always()` step so it is removed even when a build fails.

## Checklist before tagging

1. `./gradlew build` is green.
2. `versionCode` and `versionName` in `app/build.gradle.kts` are bumped. `versionCode`
   must increase on every published build.
3. The signed release APK has been installed **over** a previous release and launched —
   R8 breaks reflection at runtime, not at build time, so a green build proves nothing
   about whether the app starts.
4. Smoke test on a real device: add a source, play something, export and re-import.
5. Confirm the size check passed rather than assuming it.
