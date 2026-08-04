# Freeze Prompt — Quiblo

**Tagline:** a vibe-coded IPTV player. Free, open source, GPLv3. Bring your own playlist.
**Repo / org:** `quiblo-tv`
**Application ID:** `dev.quiblo.player`
**Status:** FROZEN for v1.0
**Date:** 2026-08-03
**Purpose:** This is the canonical description of the project. Any new contributor, or any AI agent asked to work on this codebase, is given this document first. Nothing outside this scope is built until v1.0 ships. Scope changes require an explicit amendment at the bottom of this file.

---

## 1. One-sentence definition

**Quiblo** is a free, open-source, GPLv3-licensed Android IPTV **client** that plays Live TV, VOD, and Series from playlists the user supplies themselves.

**Naming note:** the name was chosen deliberately to avoid the "VIPTV" / "VIP TV" namespace, which is saturated with paid subscription resellers. Do not reintroduce that string in the package ID, repo name, store listing, or documentation — the distance from that space is a legal and reputational asset, not an accident.

## 2. What this project is NOT

These are non-goals. Rejecting them is a decision, not an oversight.

- **Not a content service.** The project hosts, indexes, bundles, aggregates, and distributes zero streams. It ships with no default playlist, no channel directory, no "discover content" feature, and no built-in provider list.
- **Not a backend.** There is no server component, no user accounts, no telemetry, no cloud sync, no remote configuration.
- **Not a DRM client.** No Widevine, no ClearKey, no PlayReady in v1.
- **Not a TV app in v1** — *amended 2026-08-03, see Amendment 1, and extended by Amendment 4. Android TV and Google TV are now in v1, with the same screens as the phone.* LG webOS, Linux desktop, and Xbox remain phase 2 or later.
- **Not a downloader/recorder.** No recording, no catch-up, no timeshift in v1.

## 3. Locked decisions

| Area | Decision |
|---|---|
| License | **GPLv3** |
| Language | **Kotlin** |
| UI | **Jetpack Compose** (Material 3) |
| Player | **AndroidX Media3 / ExoPlayer** |
| Platform (v1) | **Android phones**, portrait-first, tablet-tolerant |
| minSdk | **30** (Android 11) |
| Sources | **M3U/M3U8** (remote URL + local file) and **Xtream Codes API** |
| EPG | **Xtream API only.** M3U playlists have no guide in v1. |
| Formats | **HLS, DASH, raw MPEG-TS, progressive MP4/MKV.** No DRM. *(DASH added by Amendment 2.)* |
| Content types | **Live TV, VOD, Series** |
| Storage | **Local only** (Room), with manual export/import to a file |
| Distribution | **GitHub Releases (APK)** |
| Network | Direct client-to-provider. No proxy, no relay, no intermediary. |

### Rationale for the non-obvious ones

- **GPLv3 over GPLv2:** the dependency tree (Media3, Compose, all AndroidX) is Apache-2.0, which is incompatible with GPLv2 but compatible with GPLv3. GPLv3 also carries an explicit patent grant and anti-tivoization terms, satisfying the requirement that the project remain open source through any downstream modification.
- **GPLv3 over AGPLv3:** the AGPL's network clause only triggers when modified code is run as a hosted service. This is a client. The clause would never fire and would falsely signal a server project.
- **Phones before TV:** identical data layer, far faster iteration, and none of the D-pad focus-management cost. TV inherits `:core:*` unchanged.
- **No DRM:** M3U and Xtream providers overwhelmingly serve unencrypted streams. DRM is substantial engineering work serving a near-empty use case here.

## 4. Architectural invariants

These must hold at every commit. A change that breaks one is a design regression regardless of whether tests pass.

1. **No UI code in `:core:*`.** No Compose import, no Android `Context` dependency beyond what Room and DataStore require. TV and desktop frontends must be able to consume `:core` untouched.
2. **The source layer is abstracted.** `MediaSource` is an interface. `M3uSource` and `XtreamSource` are implementations. Adding Stalker, XMLTV, or any future protocol means adding one implementation and zero changes to feature modules.
3. **EPG is source-agnostic.** Even though only Xtream supplies programme data in v1, the EPG storage and query layer accepts programmes from any provider. Adding XMLTV later must not require a schema migration.
4. **Playback is behind an interface.** Feature code never touches `ExoPlayer` directly. It talks to a `PlayerController`. This is the seam where DRM slots in later.
5. **The app never phones home.** The only outbound network traffic is to hosts the user explicitly configured. No analytics, no crash reporting SDK, no update check against a project-controlled server.
6. **Credentials never leave the device.** Xtream username/password are stored encrypted and are never written to logs, exports, or crash traces.

## 5. Glossary

- **M3U** — a plain-text playlist. Each entry carries a display name, optional attributes (`tvg-id`, `tvg-logo`, `group-title`), and a stream URL. Carries no programme schedule data.
- **Xtream Codes API** — a JSON HTTP API exposed by many IPTV panel providers. Authenticated via username/password against a base URL. Returns categorised live channels, VOD, series, and short-range EPG.
- **EPG** — Electronic Programme Guide. What is on now, what is on next.
- **Raw TS** — an unchunked, endless MPEG Transport Stream over HTTP. No adaptive bitrate, no seeking.
- **HLS** — chunked streaming with a `.m3u8` manifest. Supports adaptive bitrate.

## 6. Legal posture

The application is a general-purpose media player, in the same category as VLC or Jellyfin. It has no knowledge of what a user's playlist contains and exercises no editorial control over it. The README must state this plainly and must not link to, recommend, or describe how to obtain any playlist or provider.

## 7. Success condition for v1.0

A user installs the APK, adds either an M3U URL or Xtream credentials, browses categorised Live/VOD/Series content, marks favourites, plays a stream full-screen, and exports their configuration to a file — all offline-tolerant, with no account and no network call to any host they did not enter themselves.

Since Amendment 1, this must hold **on a phone and on a television**, with the television driven by a remote alone.

---

## Amendments

### Amendment 1 — Android TV and Google TV enter v1 (2026-08-03)

**Decision.** §2's "Not a TV app in v1" is withdrawn for Android TV and Google TV. A second
application, `:app-tv`, ships as part of v1.0. LG webOS, Tizen, Xbox and desktop are
unaffected and remain out of scope.

**Rationale.** The architectural invariants in §4 were written so a TV frontend would be
cheap, and that has now been demonstrated rather than assumed: every `:core:*` and
`:source:*` module is free of Compose, the ViewModels hold no UI types, and the existing
engine was confirmed running on the target television — a Google TV on Android 14 — before
this amendment was written. The TV app is a presentation layer and nothing more.

**What this costs.** v1.0 no longer ships when the phone app is ready. It ships when both
are, and the Definition of Done in `ACCEPTANCE.md` grows a television alongside the two
phones. The acceptance sweep roughly doubles. That is the trade being accepted, and it
should be re-read before anyone treats a green phone build as a release candidate.

**What does not change.** Every other non-goal in §2 stands — no backend, no accounts, no
DRM, no bundled content, no recording. The TV app is the same player with a different
frontend, not a different product. Plan: [`PLAN-TV.md`](PLAN-TV.md).

### Amendment 2 — DASH joins the supported formats (2026-08-04)

**Decision.** §3's format list gains DASH. `media3-exoplayer-dash` is on the player's
classpath alongside the HLS extractor.

**Rationale.** The parser is small and the omission was arbitrary rather than principled:
HLS, TS and progressive were chosen because that is what IPTV panels serve, and a provider
that happens to serve DASH would otherwise hit "format not supported" for no better reason
than a missing dependency.

**What this does not change.** Still no DRM. A DASH stream carrying Widevine or PlayReady
will fail at the licence step exactly as it did before, and that remains correct behaviour
for v1 rather than a bug to chase — see §2.

### Amendment 3 — the project is renamed to Quiblo (2026-08-04)

**Decision.** "Quiblo" replaces "Vibrato" everywhere: application ids `dev.quiblo.player` and
`dev.quiblo.tv`, the `dev.quiblo.*` namespace, the database, the launcher label and the
documentation.

**Rationale.** Vibrato is a musical term, but the first syllable carries an unfortunate
second reading, and a name a user is reluctant to say aloud is a bad name for a consumer
app regardless of its etymology. §1's reasoning about distancing the project from the
reseller namespace is unchanged and Quiblo satisfies it equally.

**Why now.** Android identifies an app by its application id, so changing it after a release
means existing installs cannot be upgraded — they see a different app. Nothing has been
released, which makes this the only free moment to do it.

**Consequences.** The database file is renamed with everything else. On any device carrying a
test build, the new build is a separate install with an empty database; sources must be added
again. That is a one-off cost of the rename and not a defect.

### Amendment 4 — the television gets the screens that make Amendment 1 true (2026-08-04)

**Decision.** `:app-tv` gains four things the frozen TV plan never included: a settings
screen, film and series detail screens, category selection in the Live list, and a
continue-watching row. v1.0.0 does not tag until they pass.

**Rationale.** Amendment 1 admitted the television into v1.0 on the argument that it is "the
same player with a different frontend, not a different product". A bug report on 2026-08-04
(`agile/001`) showed that claim did not hold. On a television a viewer could not open a film,
could not see a series' episodes, could not reach any setting — the gear was wired to
nothing *and* unreachable by remote — and could not pick a category in a list of 11,923
channels. That is not the same player with a different frontend; it is a frontend missing
most of the product.

So this amendment is less an expansion of scope than an admission that Amendment 1's scope
was never delivered. It is written down because `FREEZE.md` §1 requires scope to be explicit,
and because reasonable people could read these four screens as new work rather than as
completion — the honest thing is to date the decision either way.

**What this costs.** The acceptance sweep grows again: five new criteria (AC-TV-09…13) and
the television's own settings and detail screens to walk with a remote. v1.0.0 was already
gated on a sweep that had not been run; it is now gated on a slightly longer one.

**What does not change.** Every non-goal in §2 stands. No backend, no accounts, no DRM, no
bundled content, no recording. Nothing here adds a network call to a host the user did not
configure: the detail screens read the user's own panel and the optional metadata service
they supplied a key for, both of which already existed on the phone.

**One deliberate omission.** Theme mode and dynamic colour are *not* on the television, even
though §4 of this amendment says "the same settings". The television theme is always dark by
design (`QuibloTvTheme` documents why), and a television has no wallpaper for a dynamic
palette to be drawn from, so both controls would change nothing on screen. Shipping a control
that does nothing is the "hollow feature" shape this project has already had to delete nine
of; a documented absence is better than a switch that lies.
