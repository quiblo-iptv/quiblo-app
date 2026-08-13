# Changelog

What changed in Quiblo, in the words of the people who changed it.

Every released version has a section here, and the release workflow publishes that section as the
release notes. **A generated list of commit subjects is not release notes.** Somebody arriving at
the releases page wants to know what they get by installing this version, and a link to a diff
answers a different question — one they did not ask and cannot read.

Write the entry when you build the thing, not at release time. The version headings are added by
the release lane; put new lines under `Unreleased` and they move up on their own.

Formatted after [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioned by
`docs/RELEASE-MANAGEMENT.md`.

## Unreleased

### Added

- **The television is not black any more.** Whatever has focus lights the screen behind it — the
  poster you are looking at tints the corners of the catalogue, and in the player the picture
  lights its own letterbox bars, so a film in 2.35:1 or a channel in 4:3 sits in a room rather
  than in a void. It is light added to the black, never a replacement for it: artwork with no
  usable colour in it leaves the screen exactly as it was.
- **Search shows the app's own mark above the name**, and **Advanced now sits beside the field**
  as well as under it — press right from the search box to reach it.
- **The search field has a slow travelling highlight** while the remote is somewhere else. It
  stops the moment the field takes focus, because the focus ring is the one moving thing on a
  television that must never be competed with.

## 0.13.1

### Fixed

- **"On now" no longer freezes at the moment you opened the screen.** The guide asked what the
  time was once, when the list was built, and then kept re-answering with it — so a channel list
  left open went on showing whatever was airing when you opened it.
- **Errors say what went wrong again in released builds.** Every typed playback and network error
  — timed out, unreachable, unsupported format — was being decided by a class name that the
  release build renames, so a shipped APK could only ever say "something went wrong". Debug
  builds were unaffected, which is why it went unseen.
- **A cancelled request is no longer reported as a provider failure.** Leaving a screen while it
  was loading produced an error about your playlist, and the work carried on in the background
  after it had been cancelled.
- **Skipping and browsing recover from the clock going backwards.** Both request limiters
  measured against a clock that a time correction can move backwards — routine on a television
  box with no battery-backed clock — and a backward jump stopped them letting anything through
  until it caught up.
- **An episode no longer restarts from the beginning when the screen is rebuilt.** The guard
  against reloading recognised films and channels and quietly exempted every episode, so
  returning to one lost your place in it and any subtitle file you had attached.
- **A subtitle file that is too large is refused instead of crashing.** The size limit was
  checked after the whole file had been read into memory, so the one case it exists for was the
  one case it could not prevent.
- **Searching for a title containing `%` or `_` finds that title.** They were being read as
  wildcards, so those searches matched far more than they should.
- **Restoring a backup is all-or-nothing.** A failure part-way through used to leave half an
  import behind.
- **The app recovers when the encrypted credential store will not open** — after a restore to a
  new device, say — instead of failing every playlist that needs a password with no way forward.
- **Films and series with the same name no longer share a detail screen.** Two titles a provider
  filed under one name could show each other's plot and artwork.
- **Xtream addresses with IPv6 or a username in them are accepted.**

### Changed

- **A large playlist is parsed off the screen's thread and read as it arrives** rather than held
  in memory whole. A 67,000-entry playlist was parsed on the frame you were looking at.
- **The next-episode banner no longer flickers back on after starting the next episode.** Pressing
  next, or letting the countdown run out, replaced the episode straight away while the player was
  still reporting the previous one as finished — so for the moment in between, the banner slid
  back in offering the episode that had just started. It never got far enough to do anything; it
  looked wrong, and it happened between every pair of episodes of a series watched through.

## 0.13.0

### Added

- **Next and previous episode, on the television.** A series now travels with the player, so the
  buttons either side of the transport move along it — in the order the episodes were made,
  whichever order the list happens to be sorted in. They stop at the first and last episode
  rather than wrapping round; a series is a thing that finishes.
- **The next episode starts on its own.** When one ends, a banner slides in at the top right
  counting down, with Stop and Play now under it. The count is set under Settings → Playback →
  "Start the next episode after", from three seconds to fifteen, or Off — off still offers the
  next episode and waits for you to choose it. Back cancels it on the way out.
- **The television player has real controls now.** Play and pause sit in the middle of the screen
  with the two skips either side of them and the episode steps outside those, and subtitles,
  audio and picture fit are a row underneath. Press down for them, down again to reach the second
  row, and the D-pad walks between them. Subtitles and audio open the same panel at their own
  heading rather than at the top of it.
- Every button says what it is to TalkBack, which an icon on its own does not.

### Changed

- The remote's own keys still do everything they did with nothing on screen — play, skip, change
  channel — so the fast way to pause has not moved. What changed is that the arrows belong to the
  controls while the controls are up, which is what makes them reachable at all.
- The controls stay up for six seconds rather than four, counted from the last press rather than
  from when they opened. They are something to navigate now, not something to read.
- Picture fit no longer has to be reached through the Menu key or a spare press of Up. Those still
  work; there is a button for it now, which is what that pairing was always standing in for.

## 0.12.0

### Added

- **Subtitles are drawn.** They were not before: the player selected a text track and showed
  nothing, because nothing on screen was drawing the cues. Every subtitle track the app has ever
  offered was invisible.
- **Subtitle size, colour and background, set from inside the player.** In Audio and subtitles,
  while a subtitle is showing, so the effect is visible as it is chosen rather than guessed at
  from a settings screen. Starts from the caption style set in Android's own accessibility
  settings, and "Match system" goes back to it.
- **A channel's whole programme guide, on a timeline.** Long-press a channel — with a finger on
  the phone, by holding the centre button on the television — and the listing is laid out against
  the clock: an hour behind, half a day ahead, each programme as wide as it is long. What is on
  now is marked, and the times are the television's own, whatever zone the panel keeps.
- The phone opens the strip scrolled to now and writes out whichever programme you tap, since a
  half-hour block has room for a title and not for a plot. The television walks the strip with the
  D-pad and shows the same detail above it, with no dialog: the channel list stays behind the
  panel and Back closes it.
- The full listing is asked of the provider only when a viewer asks for it, and once per channel
  per session. Browsing a list still fetches nothing but now and next.

## 0.11.0

### Added

- **Subtitle files, from a panel or from the device.** A film whose panel supplies subtitle files
  now offers them in the player's subtitle list. Any film can also be given one from the device:
  pick a `.srt`, `.vtt`, `.ass` or `.ttml`, and it joins the list beside whatever the stream
  already carried. The choice is remembered against the title, so it is still there next time.
- **Subtitle files are read in the encoding they were written in.** An Arabic `.srt` in
  windows-1256 — which is most of them — used to be a screen of symbols, because the player
  assumed UTF-8. Files are now read in the encoding the bytes actually indicate, and stored
  readable.
- A picked file is copied into the app rather than referenced, so it survives the picker's
  permission expiring and the file being moved or renamed later.

## 0.10.0

### Added

- **Hide titles written in a script you do not read.** Settings offers ten writing systems —
  Arabic, Chinese, Cyrillic, Greek, Hebrew, Japanese, Korean, Latin, Thai, Devanagari — and hiding
  one removes those titles from browse and from search. Nothing is hidden until you hide it, and
  favourites and what you have half-watched are never filtered: those are titles you picked by
  hand.

## 0.9.0

### Added

- **Right-to-left titles are laid out right-to-left.** A film or a channel whose name is written in
  Arabic or Hebrew now reads from the correct edge, on both the phone and the television, without
  turning the rest of the screen around it.

## 0.8.0

### Added

- **Forget a title from Continue watching, on the television.** Long-press a tile and it goes.
- **Search suggests as you type**, from what has already been loaded, so it costs no extra request
  to the panel.

### Fixed

- The category list in Settings no longer grows past the screen on either app.

## 0.7.0

Nothing user-facing. Published by the release lane on a merge that changed only documentation.

## 0.6.0

### Added

- **Read a long-running series the way you want to.** Seasons can be merged into one continuous
  list, and the order reversed so the newest episode is first. Remembered per person, per series.
- **A refresh button on both detail screens**, on both apps, for a title whose artwork or plot
  came back wrong or not at all.

---

Versions before 0.6.0 predate this file. Their releases are on the
[releases page](https://github.com/quiblo-iptv/quiblo-app/releases).
