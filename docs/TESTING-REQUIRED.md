<!--
  Quiblo — a free, open source IPTV player.
  Copyright (C) 2026 The Quiblo Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

# Testing required

**What has been built and not yet seen work.**

This page is not the acceptance sweep. [`SWEEP-PLAN.md`](SWEEP-PLAN.md) covers the run of every
criterion before `1.0.0`; [`ACCEPTANCE-SWEEP.md`](ACCEPTANCE-SWEEP.md) is where its results go.
This is the shorter, rougher list: **code that is written, merged and green, and that nobody has
watched behave.**

It exists because this project has twice signed a fix off on a measurement and had the panel
reject it — `012` #015 and #021, both on 2026-08-11, both with a real mechanism found, measured
and corrected, and both with the reported symptom still there afterwards. The lesson written
down at the bottom of `012` is the one this page is built around:

> The exit criteria are written against the symptom, not against the mechanism.

So every row below says **what to look at**, not what was changed.

Last updated: **2026-08-12**.

---

## How to read a row

| Column | Means |
| :---- | :---- |
| **Where** | The device it has to be seen on. A phone item checked on a television proves nothing and the reverse is worse |
| **What to look at** | The symptom. If you find yourself checking that the code does the thing, you are reading the wrong column |
| **Fails if** | The specific wrong outcome, so a near-miss is not read as a pass |

**Anything not on this page and not in `ACCEPTANCE-SWEEP.md` has not been claimed as working.**

---

## A — The pass-one work, merged 2026-08-12

Five items from [`015`](../agile/015_Pass_One_of_Quiblo_—_the_screens_012_leaves_open.md) and the
`1.0.0` defect from [`016`](../agile/016_Grouping_of_Quiblo_—_what_014_needs_that_the_tree_does_not_have.md).
None of it has run anywhere but the JVM.

### A1 — #024, the metadata cache key

**Where:** either app, with a TMDB key configured and a catalogue that has two versions of one
film in it. **This is the only `1.0.0` item on this page.**

| | |
| :---- | :---- |
| **What to look at** | Two films with the same name and different years — *Dune*, *The Thing*, *Suspiria*, whatever the account carries. Open both detail screens |
| **Passes if** | Each shows its own plot, poster, rating and cast |
| **Fails if** | They show the same ones. That is the defect, unfixed |

**And the upgrade, which is the part with a cost:** install over an existing build that already
had a metadata cache. The first browse afterwards is **as slow as a first install**, because
`MIGRATION_11_12` drops the cache rather than carrying rows across that may hold the wrong
film's details. On a scanned catalogue the search screen's coverage figure reads **0%** until a
scan is run again. Both are intended. Report them only if they do not recover.

**Watch for the one thing the JVM cannot check:** a title the provider gave no year for is now
its own cache row, separate from the same title with a year. On a real catalogue that means
*slightly more* lookups than before. If a scan of a large account is now noticeably longer, say
so — the trade was made deliberately and can be revisited, but only against a real number.

### A2 — INC-E4, the corner radius

**Where:** both apps. **Every screen.**

| | |
| :---- | :---- |
| **What to look at** | Cards, dialogs, chips, text fields, the player's controls. Corners are rounder — 20dp where Material's default was 12, 28 where it was 16 |
| **Passes if** | It looks intentional at three metres and at arm's length, and nothing is clipped by its own new radius |
| **Fails if** | Any surface has content cut at a corner, or the television looks rounder than the phone |

This is the cheapest visible change in the pass and it touches everything, which is why it wants
a look rather than a test. **If it is simply not liked, say so** — the whole scale is three
numbers in one file.

### A3 — INC-F3, long-press to forget something

**Where:** phone, on the Continue watching row. The television half is **not built** —
`STOPPERS.md` **S12**.

| | |
| :---- | :---- |
| **What to look at** | Long-press a tile. A one-action menu appears: **Remove from watch history**. Take it |
| **Passes if** | The tile goes, and it is still gone after a force-stop and reopen |
| **Fails if** | The row reappears, or a **series** tile is replaced by the previous episode of the same series rather than removed |

That second failure is the one worth pressing on. A series tile stands for the whole programme,
and removing one episode of it would look almost right.

### A4 — INC-F0, profile avatars

**Where:** both apps, at the chooser that appears at every launch.

| | |
| :---- | :---- |
| **What to look at** | Each profile has a picture in its circle. Create a profile on the **phone**, pick a face from the row above the name field, and see it on the tile afterwards |
| **Passes if** | The face chosen is the face shown, on both apps, after a force-stop |
| **Fails if** | A profile created before this build shows a face nobody chose, rather than its initial on a colour |

**The upgrade case is the one that matters, and it is `AC-PROF-05` in miniature.** A profile
that existed before this build must come through with **no** face and draw its initial. See
`STOPPERS.md` S9 for the staged `0.2.0` artefacts, which is the only way to test an upgrade.

**Also confirm the colour is stable:** the same profile name draws the same colour on the phone
and on the television. It is derived from the name rather than stored, so if the two devices
disagree, that is a real fault and not a rendering difference.

### A5 — INC-F8, the phone settings screen

**Where:** phone, Settings, on an account with **many** categories — an Xtream account carries
several hundred and that is the case this changes.

| | |
| :---- | :---- |
| **What to look at** | Drag anywhere on the screen |
| **Passes if** | One drag from the bottom reaches the last category, **and** one drag reaches the cards above the category section. There is one scrolling surface |
| **Fails if** | A drag moves one part of the screen while another stays put, or the category list ends before the account's categories do |

**A second thing to look at, which the test cannot judge:** the category rows no longer sit in a
card of their own — the heading keeps its card and the rows are plain, matching the television.
Say whether the section still reads as a section.

### INC-F9 — a section reads in the direction its text is written

Merged 2026-08-12. The rule has ten unit tests and the control was watched on a tablet, but a
detector being right is not the same as a screen looking right.

| | |
| :---- | :---- |
| **What to look at** | A film or series whose plot is in Arabic, on both apps. The title too, if the provider names it in Arabic |
| **Passes if** | The plot aligns right and reads right-to-left, while the rest of the screen — buttons, back arrow, episode list — stays exactly where it was |
| **Fails if** | The whole screen mirrors, or an English title inside an Arabic app aligns wrongly, or a title that is only a number and a season code moves anything |

**The one to watch for:** a title like `Dune 2 مترجم` must stay left-to-right. It is the case a
contains-check gets wrong and the reason the rule reads only the first strong character.

### INC-F14 — hiding a script the viewer does not read

Merged 2026-08-12. **The setting was watched working on a tablet; the filtering was not.** The
emulator has no playlist, so what is proven is that the card renders, both apps store the choice,
and "2 hidden / Show everything again" appears. What a hidden script does to 67,000 rows has only
been proven in unit tests.

| | |
| :---- | :---- |
| **What to look at** | Settings → Writing systems on an account with Arabic and Latin content. Hide Arabic, then browse and search |
| **Passes if** | Arabic-titled rows disappear from browse and from search, favourites keep every one of them, and "Show everything again" brings them all back |
| **Fails if** | A catalogue screen goes blank, the list takes visibly longer to draw than before, or a favourite disappears |

**Two limits that are the design, not defects.** A category named `4K | مسلسلات` reads as Latin,
because `K` is a Latin letter — hiding Latin loses it. And an Arabic film released under a
transliterated Latin title is not hidden at all, because nothing in a playlist says it is Arabic.
Report these as observations, not as failures.

### INC-F10 — subtitle files, the panel's and the viewer's own

Merged 2026-08-12. **Nothing here has been seen on a device.** Every part of it is covered by
unit tests — the format sniffing, the encoding detection, the copy-and-remember, the menu shape —
and none of those is a subtitle appearing over a film.

| | |
| :---- | :---- |
| **What to look at** | Play any film. Open Audio and subtitles. The list ends with "Add a subtitle file…" |
| **Passes if** | Picking a `.srt` restarts the film at the position it was at, the file's name appears in the Subtitles list, choosing it shows the lines over the picture, and it is still listed the next time that title is played |
| **Fails if** | The menu has no such entry, the picker does not open, playback restarts from the beginning, the file is listed but shows nothing, or the entry is gone next time |

**Take an Arabic `.srt` that is not UTF-8.** That is the case this was built for and the one a
UTF-8 file cannot exercise: encoded in windows-1256, it used to render as a line of symbols. It
should read as Arabic. A file already in UTF-8 proves nothing about this.

**Three things that are the design, not defects.** Attaching restarts the stream, because the
engine takes subtitle files as part of the media item — a second of buffering is expected. A
subtitle a panel supplies is only fetched for a film, never for a live channel or an episode.
And a television with no document picker installed says so rather than offering an entry that
does nothing; on such a device the whole feature is unreachable, which is worth reporting as a
fact about the device rather than a fault in the app.

**The panel half is untested against a real panel.** No account available here supplies subtitle
files, so what a populated `subtitles` field does has only been proven against synthetic payloads
in the shapes panels are known to send.

### INC-F11 — subtitle appearance, and the fact that subtitles now appear at all

Merged 2026-08-12. **Nothing here has been seen on a device**, and one part of it has never
worked on any device: until this merge nothing in either app drew subtitle cues, so every
subtitle track the app has offered since the player was written was invisible. `AC-PLAY-04`'s
subtitle half and `012` #023 both need re-running because of it.

| | |
| :---- | :---- |
| **What to look at** | Play something with a subtitle track and turn it on |
| **Passes if** | Lines appear over the picture, and Audio and subtitles then grows Subtitle size, Subtitle colour and Subtitle background |
| **Fails if** | Turning a subtitle on still changes nothing, or the appearance rows are offered with nothing showing |

| | |
| :---- | :---- |
| **What to look at** | With a subtitle showing, change size, then colour, then background |
| **Passes if** | Each takes effect on the line currently on screen, and all three survive leaving the player and coming back |
| **Fails if** | A change needs a restart to appear, or is forgotten between films |

**Check "Match system" against a device that has a system caption style set.** Android settings →
Accessibility → Caption preferences. Enlarge captions there, then open the player with Match
system selected: the size should follow. This is the half that cannot be checked on a device
nobody has configured, and it is the half that matters to somebody who needs large captions.

**One thing that is the design.** Match system forgets the explicit choices rather than hiding
them behind a flag, so turning it off returns the defaults and not what was set before.

---

## B — Owed from `012`, still owed

`012` built twelve defect fixes on 2026-08-10. **Two were swept and rejected. Ten have never
been seen on a device at all.** Nothing on this page changes that, and the full list with what
each owes is in [`012`](../agile/012_Bug_Round_of_Quiblo_—_Round_3.md) under "What closes this
round". `STOPPERS.md` **S1** carries the running order for a sitting with the remote.

Two are not "owed a device" — they have had one and failed it:

| # | State |
| :---- | :---- |
| **#015** | Rejected 2026-08-11, rebuilt the same day, published in `v0.2.7`, **owed the panel again** |
| **#021** | Rejected 2026-08-11 and **unfixed**. Its next step is a video capture, not another argued mechanism. Do not re-try `adjustNothing` |

---

## C — Deferred measurements, in writing

Things nobody can check yet, recorded so they stop being re-asked.

| What | Needs | Why it is not guessed |
| :---- | :---- | :---- |
| `012` #018 — is the genre-chip wait actually shorter? | A scanned 67k-title account | The coverage figure beside the chips comes from the same read. An optimisation that leaves it stale trades a slow screen for a lying one |
| A1's scan length after #024 | The same | The extra lookups are real and small in theory. Theory is what this project keeps being wrong about |
| `AC-PLAY-04` — two audio tracks, switchable | A file that carries two | Built in `012` #023 and the criterion has **never been run**. `STOPPERS.md` S3 |

---

## What this page is not

- **Not a substitute for the sweep.** Everything here also has to pass its acceptance criterion,
  and several of these rows *are* criteria — `AC-PROF-05`, `AC-META`, `AC-NFR-09`.
- **Not a list of what might be wrong.** Every row is code we believe is right. The point of
  writing them down is that believing is what got #015 and #021 signed off.
- **Not permanent.** A row leaves this page when a device has answered it, in either direction.
  A row that has been here through two sweeps is a row nobody is going to check, and it should
  become a stopper with an owner instead.
