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

### Amendment 5 — the catalogue can be described in one go (2026-08-09)

**Decision.** The optional metadata feature gains a **scan**: one control in Settings that
looks up every film and series in the user's catalogue and fills the cache, rather than
filling it a poster at a time as somebody browses. One request per distinct title, paced,
resumable, and stoppable. It is off unless started, like the key it depends on.

**Rationale.** Search on the television (2026-08-09) can filter by genre, and the genre
filter is built from the metadata cache — so a viewer who has browsed four rows can filter by
the genres of four rows. The screen quotes its own coverage honestly, which turns out to be
the argument for this: a figure of 3% invites exactly one question, and until now the only
answer was "scroll past your entire catalogue".

**What it costs, stated plainly.** On the account this project is tested against it is tens
of thousands of requests against the user's own key, over the better part of an hour. That is
a great deal more than this app has ever asked of anything, so it is deliberate, visible,
started by hand, and never automatic. It also stops at the first refusal instead of pushing
through one.

**What it changes about how we ask.** Two things, both of which were latent defects rather
than new work:

1. `TmdbClient` returned `null` for every failure alike, and the cache wrote `null` down as
   "this title matches nothing" for a fortnight. Harmless a poster at a time; ruinous in
   bulk — a scan that met a rate limit half way would have recorded tens of thousands of
   false misses and left the search screen reporting a described catalogue with no genres in
   it. Failures are now typed, and only a genuine no-match is cached.
2. The token bucket that paces panel requests allowed the balance to stop at zero rather than
   go negative, so each wait accrued a token the next caller spent immediately: requests left
   in pairs, at **twice** the documented sustained rate. That guard exists because this
   project got a user's account blocked twice. It has been running at 5 requests a second
   while claiming 2.5 since the day it was written, and it is fixed here with a regression
   test that pins the rate rather than the concurrency.

**What does not change.** Every non-goal in §2 stands. No bundled key, no key of ours, no new
host: the scan asks the same service the detail screens already ask, with the same key the
user supplied, and asks for the *cheap* record — a search hit, whose genre ids are translated
by one call per catalogue — rather than the full one. Nothing is fetched for live channels.

### Amendment 6 — local profiles (2026-08-09)

**Decision.** The app asks who is watching. A profile owns **favourites and resume positions**
and nothing else; playlists, player settings, hidden categories and the metadata key stay
app-wide. Alongside the named profiles there is **Guest**, whose favourites and resume points
are deleted when the session ends. There is no PIN and nothing is hidden from anybody.

**Rationale.** `docs/PLAN.md` §6 parks "multiple profiles, parental PIN" in Phase 2, and this
is the first half of that item arriving early — deliberately without the second half. The
reason is that continue-watching is the feature most damaged by being shared: a household
using one television has been getting one list of half-watched films belonging to nobody in
particular, and it is precisely the row the app puts first on every catalogue screen.

**What is explicitly not in it.** No password, no PIN, no per-profile locking of categories or
of anything else. Those make this a parental-controls feature, which is a different promise
with different failure modes — a control that *appears* to restrict is worse than no control
— and it stays parked. Guest is not a weaker profile; it is a session, which is why its data
is deleted rather than merely hidden.

**How guest keeps its promise.** Guest is a row in `profiles` with the favourites and resume
tables carrying a foreign key onto it, so deleting the row deletes the data by cascade rather
than by a routine somebody has to remember to call. It is deleted when the guest leaves *and*
at every startup, because a television is switched off at the wall and a process the system
kills runs no tidy-up. A promise kept only on the happy path is not kept.

**What it costs on upgrade.** Schema 10 → 11 rebuilds both tables to put the profile in their
primary key, and **adopts everything already stored into a profile named "Default"** rather
than dropping it. Nobody upgrading loses a favourite. Backup keeps its existing format: a
file holds one person's favourites — whoever was watching when it was written, and whoever is
watching when it is read — because a format that carried profiles would have to answer what
happens when it lands on a device where those people do not exist.

### Amendment 7 — two criteria that describe an app we no longer have (2026-08-10)

**Ships in.** `1.0.0-beta.1`
**Release class.** Minor — nothing stored changes, no capability is withdrawn, and both halves
correct criteria rather than extend scope (§1).

**Decision.** Two acceptance criteria are restated. Neither changes what Quiblo is; both were
written accurately for a smaller version of it and have since been overtaken by work this
document already admitted.

#### AC-TV-03 — back walks a stack, and nothing on it is erased

The criterion read: *"Back from any screen returns to the category bar; back from the bar
exits the app."* It is now:

> **AC-TV-03** — Back pops exactly one step of the journey the viewer took, and never strands
> them. No step is discarded on the way. From a top-level screen, back exits.

**Rationale.** The old wording was written on 2026-08-03 for a frontend with two levels.
Amendment 4 added film and series detail screens the following day and did not re-read it, so
for a week the criterion has *demanded* the defect reported as `agile/012` #020: back from a
playing episode lands on the Series catalogue rather than on the series the viewer was
reading, and the episode they were on is gone. An app obeying AC-TV-03 was an app throwing
away a step every time back was pressed.

The property the criterion exists to protect — no screen that back cannot leave — is kept
word for word. What is dropped is "returns to the category bar", which was a description of a
two-level app rather than a promise to anybody.

**What this makes true in the code.** Navigation on the television stops being one current
screen that each new screen replaces, and becomes a stack that back pops. That is the
difference between the two readings, and it is why the fix cannot be a special case in the
player: a replace has no memory of what it replaced, so every screen would need to name its
own predecessor and they would disagree.

**What does not change.** Back from a top-level catalogue still walks to the first tab and
then leaves the app. A viewer can still never reach a screen that back will not leave, which
is the whole of what was ever being promised.

#### AC-NFR-04 — permissions a dependency contributes, named rather than denied

The criterion read: *"Permissions requested: INTERNET and network state only. No storage
permission (SAF instead), no location, no contacts."* It is now:

> **AC-NFR-04** — This project declares `INTERNET` and `ACCESS_NETWORK_STATE` and nothing
> else. The merged manifest of each application additionally contains `WAKE_LOCK`, contributed
> by Media3, and `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, contributed by
> androidx.core — **those two and no others.** No storage permission (SAF instead), no
> location, no contacts, no camera, no microphone. A third permission arriving from any source
> fails this criterion until it is examined and named here.

**Rationale.** The criterion as written has never passed and never could: an application does
not choose what its dependencies merge into its manifest. Measured on both merged release
manifests on 2026-08-10, each contains exactly four entries — the two we declare, plus:

- **`android.permission.WAKE_LOCK`**, from Media3. It is what stops the device sleeping during
  playback. Removing it trades a documentation problem for a real defect, and on a television
  in particular, where nobody touches the remote for two hours.
- **`dev.quiblo.player.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`** (and `dev.quiblo.tv.…` for
  the television), from androidx.core. A signature-level permission scoped to our own
  application id, used to stop other apps delivering broadcasts to receivers we register at
  runtime. It is not user-visible and grants nothing to anybody else.

**Checked against our own principles, because permitting something is not the same as
excusing it.** Neither addition touches a §4 invariant: neither opens a network path, so §4.5
("the app never phones home") is untouched — `WAKE_LOCK` keeps a screen awake and grants no
access to anything; neither reads or writes user data, so §4.6 (credentials never leave the
device) is untouched; and neither is a permission a reasonable person would be surprised by in
a media player. The spirit the criterion was written to defend — no storage, no location, no
contacts, nothing that reaches the user's life outside this app — holds exactly as before.

**Checked against the licence, for the same reason.** Both come from Apache-2.0 libraries,
which §3 records as compatible with GPLv3 and is the stated reason for choosing GPLv3 over
GPLv2. A merged manifest entry is not a licensing event at all — it is build output from
dependencies we already link — so nothing here changes the attribution position, and both
libraries are already carried in the third-party licence list `AC-LEGAL-03` covers.

**Why name them rather than simply permit them.** An open permission clause is not a
criterion. Naming these two makes a third arrival fail the build's own check instead of
passing unnoticed under a rule that forgives anything a dependency does — which is the failure
mode this project has met before under a different name: a guard that is technically satisfied
while the thing it guards against is happening.

**What does not change.** Every non-goal in §2 stands. No storage permission is added, SAF
remains how files are read and written, and nothing here permits a permission this project
declares itself.
