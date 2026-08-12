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
