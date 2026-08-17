# Competitors

Three open-source IPTV players, read against Quiblo on 2026-08-17.

This is not a feature table with our column ticked. It is an attempt to answer one question
honestly: **why would somebody choose Quiblo, and where would they be right not to?** Where a
competitor is ahead, that is written down as being ahead. The numbers are what the repositories
said on the date above and go stale — re-read them before quoting them.

| | **Quiblo** | **Extreme-InfiniTV** | **IPTVnator** | **ZUI IPTV Player** |
|---|---|---|---|---|
| Platforms | Android phone + Android TV | Windows, macOS, Linux, Android (phone/tablet/TV), web | Windows, macOS, Linux (Electron), PWA, Docker | LG webOS only |
| Stack | Kotlin, Compose, Media3 | Astro + Svelte + Rust (Tauri), bundled FFmpeg | Angular + Electron, Nx monorepo | React + Vite, hls.js / mpegts.js |
| Stars | — | ~98 | ~6,800 | ~6 |
| Licence | GPL-3.0 | GPL-3.0 (FFmpeg sidecar LGPL-2.1) | MIT | MIT |
| Distribution | GitHub releases (APK) | MS Store, Play, Snap, GitHub | GitHub, Docker, PWA | Sideloaded to a webOS TV |

## Where each of them is genuinely ahead

### IPTVnator — reach, and the desktop

Six thousand eight hundred stars is not a feature and cannot be out-shipped. It is also the most
complete of the three on paper: Stalker portals alongside M3U and Xtream, an EPG with a timeline
and a multi-channel grid, catch-up and archive, an offline download manager, external-player
handoff to MPV/VLC/IINA and embedded MPV rendering, a command palette, actor pages, nineteen
translations.

Quiblo has none of: Stalker portals, an EPG grid, catch-up, downloads, external-player handoff.

### Extreme-InfiniTV — one codebase on six platforms

Tauri gets it onto Windows, macOS, Linux, Android and the web from one source tree, and into
three app stores. It ships three embedded players (ArtPlayer, Video.js, Shaka) covering HLS,
MPEG-TS and DASH with ClearKey; Quiblo has one, Media3. It has a playlist editor with reordering
and M3U export, catch-up with seek, sixteen languages including RTL, and D-pad navigation for a
TV-first layout.

It is also closest to Quiblo in intent — a TV-first, no-telemetry, GPL player — which makes it
the one worth watching rather than the one worth dismissing.

### ZUI IPTV Player — the platform nobody else is on

Six stars and twenty-six commits, so it is not competition by size. It is worth reading for two
things Quiblo does not have: **cloud sync via QR code**, which is the least painful answer to
"type this URL on a television" anybody in this space has, and **parental PIN protection**, which
is a real family requirement Quiblo's profiles do not cover. It is also a demonstration that
webOS is reachable.

## Where Quiblo is already ahead

These are not on any of the three:

- **For You, and a recommender that looks at what you watched.** Thirteen signals per title —
  anime versus merely animated, language of production, description words, watch count, hour of
  day, whether it was searched for or taken off a shelf, favourites, thumbs — with each of your
  strongest titles proposing its own suggestions, and the row withheld entirely below five
  watched titles. Every one of the three offers a channel list, a poster grid and a search box.
  None offers an opinion.
- **Profiles with per-person taste.** Favourites and continue-watching are per profile; playlists
  and settings are shared; Guest keeps nothing.
- **Ambient light off the picture**, and a television UI designed against a real 50-inch panel
  rather than a resized desktop layout. Several of the fixes in this repository exist because a
  layout that measured correctly on an emulator was visibly wrong on a panel.
- **Background catalogue sync that merges rather than rebuilds**, so "recently added" means
  something on an M3U playlist that has no dates in it.
- **A repository written to be read.** The nine deleted features, the two empty releases, the
  amendments — the honest record is itself a differentiator in a category where most projects
  ship a README and a screenshot.

## Where Quiblo is behind, in the order it costs us

1. **Reach.** Android only, against two cross-platform projects. This is the single largest gap
   and it is already scoped as version 2 in
   [`agile/007`](../agile/007_Version_2_of_Quiblo_—_the_platforms_beyond_Android.md). Nothing in
   this document argues for doing it sooner than that plan says; it argues that it is the real
   deficit rather than a feature list.
2. **The guide.** Quiblo has now/next per channel, fetched on demand, and only where the source
   provides it — an Xtream account does, an M3U playlist does not, and no external XMLTV is read
   at all. All three competitors ship a fuller guide, and IPTVnator ships a grid. A time-grid is
   already listed as not-yet-implemented in the README; the part that is not listed is that an
   M3U user has no guide at all and no way to give us one.
3. **Catch-up / archive.** Two of the three have it. Xtream exposes it, so this is a feature
   Quiblo could have rather than a platform limitation.
4. **External player handoff.** Cheap, well understood, and the answer to every codec Media3
   cannot decode. Two of the three have it.
5. **Stalker portals.** A whole class of source Quiblo cannot open.
6. **Localisation.** Sixteen and nineteen languages against ours. Text direction is already
   handled, which is the hard half.

## What this suggests

**Do not compete on the feature list.** IPTVnator will win that, and Extreme-InfiniTV will win
the platform count. Both are further along on both axes and both got there first.

**Compete on being the best television.** The three gaps above that are cheap and TV-shaped —
catch-up, external-player handoff, and a real guide for the sources that can supply one — close
the distance where it is visible from a sofa. The recommender, the profiles and the ambient light
are things a viewer notices in the first five minutes and that no competitor has at all.

**The reach problem is a version-2 problem and stays one.** Rebuilding on a cross-platform stack
to match Extreme-InfiniTV would trade the thing Quiblo is best at — a native television app that
was fixed against real panels — for a thing two projects already do.

---

*Written in round `026`. Star counts and feature lists are as of 2026-08-17 and should be
re-read, not cited, after that.*
