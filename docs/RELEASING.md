<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
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
  -keystore quiblo-release.jks \
  -alias quiblo \
  -keyalg RSA -keysize 4096 -validity 10000
```

`-validity 10000` is about 27 years. A key that expires during the app's life cannot sign
an upgrade, so this is deliberately long.

## Building a signed release locally

Create `keystore.properties` in the repository root — it is gitignored, along with `*.jks`
and `*.keystore`:

```properties
storeFile=/absolute/path/to/quiblo-release.jks
storePassword=…
keyAlias=quiblo
keyPassword=…
```

Then:

```bash
./gradlew :app:assembleRelease :app-tv:assembleRelease
```

**A release is two APKs.** `:app` is the phone app (`dev.quiblo.player`), `:app-tv` is the
television app (`dev.quiblo.tv`). They are separate application ids, not flavours: two
installs, two databases, and a user may well have both. Both are signed with the same key —
losing it loses the upgrade path for both.

Without those values the build still succeeds and produces `app-release-unsigned.apk`. It
is never signed with the debug key as a fallback: an APK silently signed with a throwaway
key cannot be upgraded over a real install, and the failure would only surface for users.

## Releasing through CI

Tagging `v*` runs `.github/workflows/release.yml`, which builds, tests, runs detekt and
lint, assembles both signed releases, enforces the 25 MB budget (AC-NFR-02) on each,
attaches all four files, and opens the release as a **draft** for a human to publish:

| Asset | What it is |
|---|---|
| `quiblo-<tag>.apk` | Phone and tablet, `dev.quiblo.player` |
| `quiblo-tv-<tag>.apk` | Android TV / Google TV, `dev.quiblo.tv` |
| `….apk.sha256` | One per APK |

The names differ because the artefacts are not interchangeable, and the failure is quiet:
the phone APK installs happily on a television and then never appears in its launcher,
because only `:app-tv` declares `LEANBACK_LAUNCHER`.

Required repository secrets:

| Secret | What it holds |
|---|---|
| `QUIBLO_KEYSTORE_BASE64` | The `.jks`, base64-encoded (`base64 -w0 quiblo-release.jks`) |
| `QUIBLO_KEYSTORE_PASSWORD` | Keystore password |
| `QUIBLO_KEY_ALIAS` | Key alias |
| `QUIBLO_KEY_PASSWORD` | Key password |

The decoded keystore is written to the runner's temp directory, outside the workspace, and
deleted in an `always()` step so it is removed even when a build fails.

## Checklist before tagging

1. `./gradlew build` is green.
2. `versionCode` and `versionName` are bumped in **both** `app/build.gradle.kts` and
   `app-tv/build.gradle.kts`. `versionCode` must increase on every published build, and
   each application id has its own sequence.
3. Both signed release APKs have been installed **over** a previous release and launched —
   R8 breaks reflection at runtime, not at build time, so a green build proves nothing
   about whether the app starts. `:app-tv` depends on the `:feature:*` modules for their
   ViewModels only, so it is the build most likely to have been shrunk too far.
4. Smoke test on a real device: add a source, play something, export and re-import. Then
   the same on the television, with the remote as the only input device.
5. `docs/ACCEPTANCE-SWEEP.md` §5 and §6 are clear, including the AC-TV-\* rows.
6. Confirm the size checks passed rather than assuming it.
