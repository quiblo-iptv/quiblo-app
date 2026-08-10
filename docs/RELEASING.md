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

**A merge to main is a release.** `.github/workflows/release-on-main.yml` runs on every push
to `main` and does three things in order, each gated on the one before it:

1. **Gate.** The same job a pull request has to pass, reused from `ci.yml` rather than
   copied: assemble, unit tests, detekt, parser coverage and Android Lint.
2. **Version.** Reads `versionName`/`versionCode` from `app/build.gradle.kts`, bumps the
   patch and the code, writes both into **both** build files, commits that to `main` as
   `chore(release): <version> [skip ci]` and tags it `v<version>`. It refuses to go on if
   the two application ids disagree about where they are starting from, or if the tag
   already exists. The push is made with `GITHUB_TOKEN`, which by design starts no further
   workflow run, so this cannot loop.
3. **Publish.** Calls `release.yml` with the new tag.

So the version to be released is decided by the workflow, not by you, and the numbers in the
build files are the record of what has already shipped. To release something other than the
next patch — a minor, a major, or a pre-release — set the version in both build files in the
pull request itself and the bump moves on from there. **It publishes one step past what it
finds**, so write the version *before* the one you want: `1.0.0-beta.0` to release
`1.0.0-beta.1`.

`versionName` accepts `X.Y.Z` and `X.Y.Z-<alpha|beta|rc>.N` and nothing else; anything else
stops the lane with a message rather than being truncated to three numbers. A pre-release
advances its own counter — `-beta.1` → `-beta.2` — whatever the commits contain, and is
published with `prerelease: true` so it never stands on the releases page as the current
version. Moving from beta to rc, or rc to final, is a deliberate edit in a pull request.
[`RELEASE-MANAGEMENT.md`](RELEASE-MANAGEMENT.md) §3–§6 says what each stage claims.

Nothing needs to be tagged by hand. Tagging `v*` still runs `release.yml` directly, and so
does running it from the Actions tab with a tag as input — both remain for a release that has
to be cut from somewhere other than the head of `main`. Those two paths run the full gate
first; the merge path does not, because it has just run it on the same tree.

`release.yml` builds, assembles both signed releases, enforces the 25 MB budget (AC-NFR-02)
on each, attaches all four files, and **publishes** the release:

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

## Checklist before merging to main

Since a merge publishes, this is a checklist for the pull request rather than for a tag.
Steps 1 and 2 of the old list are now the workflow's job — the gate proves the build, and the
bump is automatic — which leaves the ones no runner can do:

1. Both signed release APKs have been installed **over** a previous release and launched —
   R8 breaks reflection at runtime, not at build time, so a green build proves nothing
   about whether the app starts. `:app-tv` depends on the `:feature:*` modules for their
   ViewModels only, so it is the build most likely to have been shrunk too far.
2. Smoke test on a real device: add a source, play something, export and re-import. Then
   the same on the television, with the remote as the only input device.
3. `docs/ACCEPTANCE-SWEEP.md` §5 and §6 are clear, including the AC-TV-\* rows.
4. Confirm the size checks passed rather than assuming it.
5. **Open a screen the change touches, on a device or an emulator.** A green gate is not a
   running app: Koin resolves a module's dependencies only when something first asks for
   one, so a mis-wired `single { … }` compiles, passes detekt, passes lint, passes every
   unit test, and then takes the app down on the screen that needed it. That is exactly how
   `0.2.0` shipped a Live tab that crashed on being opened.
