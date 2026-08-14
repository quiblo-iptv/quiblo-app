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

Last updated: **2026-08-13**.

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

**Where:** both apps, on the Continue watching row. The television half was built later the same
day — `STOPPERS.md` **S12**, closed — and holds the centre button to arm the tile rather than
opening a menu.

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

### INC-F4 — the full guide, on a timeline

Merged 2026-08-12. **Nothing here has been seen on a device**, and it cannot be seen without a
working panel: the listing comes from `get_simple_data_table`, which the M3U side has no answer
for and no test account has ever been asked for.

| | |
| :---- | :---- |
| **What to look at** | Long-press a live channel on the phone |
| **Passes if** | The sheet still opens with Now and Next, and a Full guide strip under it opens scrolled to the programme playing |
| **Fails if** | The strip opens at the far left, is empty against a panel that has listings, or the sheet no longer scrolls |

| | |
| :---- | :---- |
| **What to look at** | Hold the centre button on a channel on the television |
| **Passes if** | A strip appears across the bottom with focus on the programme on now, left and right walk it, the header names what is focused, and Back closes it |
| **Fails if** | The remote looks dead when the panel opens, focus lands on the first block instead of the one playing, or Back leaves the app instead of closing the panel |

| | |
| :---- | :---- |
| **What to look at** | The times on the strip against the clock on the wall |
| **Passes if** | The ruler ticks on the hour and the now-marker sits where the current time is |
| **Fails if** | Everything is a whole number of hours out — that is a zone applied twice, and a now/next label hides it where a timeline cannot |

**The gesture itself is what is unproven on the television.** Holding the centre button is this
app's only long-press, and the one other control that uses it — arming a continue-watching tile,
`S12` — has never been seen on a remote either. If the panel never opens, check that before
looking at anything in the guide code.

**Watch the request count.** Opening the panel is one call per channel per session and browsing
the list is still none. A panel that starts refusing the account after a few long-presses means
the dedupe is not holding, and that is the failure this project has already had twice.

---

## A6 — the television player, rebuilt around focusable controls (`v0.13.0`)

**Where:** the television, on a series with at least three episodes. **Nothing of this has run
anywhere but the JVM**, and it is the largest change the player has had since it was written:
the screen went from having no focusable control on it to having eight.

The JVM cover is real and it is narrow. `TvPlayerControlsReachableTest` walks every button with
a D-pad at the panel's own geometry, and `TvPlayerKeyMapTest` holds the rule that lets it —
while the controls are up the key map answers no arrow, so focus traversal can have them. What
neither can see is the panel: whether focus lands where it should when the controls appear at
all, whether it comes back to the screen when they time out, and whether a real remote's key
repeat outruns any of it.

### A6.1 — the controls appear, and the remote can reach all of them

| | |
| :---- | :---- |
| **What to look at** | Press **down** during an episode. Play/pause is focused. Press **left** and **right** along the row: rewind, forward, and the two episode steps outside them. Press **down** again: the row underneath — subtitles, audio, picture fit |
| **Passes if** | Every button takes focus in turn, and the white fill makes it obvious which one has it from across the room |
| **Fails if** | A button is drawn and cannot be focused, **or** pressing down a second time does nothing, **or** focus vanishes entirely at any point. The last is AC-TV-02 and is the worst of the three — a remote with nowhere to be is indistinguishable from a frozen app |

**Then let them time out.** They hide after six seconds without a press. **Press left
immediately afterwards.** The film should seek, not move focus — that is the screen taking
focus back, and if it did not, the remote is now dead until something else is pressed.

### A6.2 — next and previous episode

| | |
| :---- | :---- |
| **What to look at** | The two outer buttons. From the middle of a season, both are there; on the **first** episode there is no previous button and on the **last** there is no next |
| **Passes if** | Each press starts the neighbouring episode from the beginning, and **back** returns to the series list once — not once per episode stepped through |
| **Fails if** | It plays the wrong episode. Check this against a season listed **newest first** as well: the run is built in broadcast order deliberately, and getting it wrong walks the series backwards |

### A6.3 — the countdown, which is the one thing that acts on nobody's press

**Watch a whole episode to its end**, or seek to the last few seconds of one.

| | |
| :---- | :---- |
| **What to look at** | A banner slides in at the top right counting down. **Stop** and **Play now** underneath, focus on Play now |
| **Passes if** | The count falls a second at a time and the next episode starts at zero. Pressing **Stop** ends it and nothing starts. Pressing **back** leaves the player and nothing starts after that either |
| **Fails if** | It fires more than once, **or** it fires after Stop, **or** it appears at the end of a **film** or after a stream **fails** — a failure has its own Try again screen and skipping past it would quietly lose the half nobody watched |

**And with the setting off** (Settings → Playback → "Start the next episode after" → Off): the
banner still appears and nothing ever starts on its own. That is the intended behaviour, not a
bug — what is off is the counting, not the offer.

### A6.4 — what the change might have broken

Both of these worked before and are the things a rewritten key map takes away silently.

| | |
| :---- | :---- |
| **Live** | Zapping still works with up/down and the channel keys, and the player draws **no** seek or episode buttons on a channel |
| **The track panel** | The subtitles button opens it at Subtitles and the audio button at Audio, rather than both at the top. AC-PLAY-04's audio half is still unrun for want of a two-track stream — `STOPPERS.md` S3 |

---

## A7 — the television's own furniture, `v0.15.0` and the branch after it

**Where:** the television. **All of this has been walked on the emulator with the D-pad and
screenshotted**, which is new — see the premise note at the top of
[`STOPPERS.md`](STOPPERS.md). That retires "can it be reached" and not "is it right", so every
row below is a question about how the screen reads and not about whether the control exists.

### A7.1 — the launcher tile

| | |
| :---- | :---- |
| **What to look at** | The Apps row of the launcher, from the sofa |
| **Passes if** | The tile reads as Quiblo before the label under it does. No bar down either side, and the name is legible at the size the launcher draws it |
| **Why it is here** | It was the square app icon dropped into a 16:9 frame, which the launcher letterboxed. It is now drawn at 320x180 with the name beside the mark — a piece of artwork nobody has seen anywhere but a 1080p window |

### A7.2 — the gear and the face at the end of the bar

| | |
| :---- | :---- |
| **What to look at** | Walk right along the tab bar past the last tab. Gear, then the face. Then left, back through both |
| **Passes if** | The two icons read as one group rather than as two stray controls, the highlight is obvious on each from across the room, and the face is recognisably the profile's own |
| **Why it is here** | The spacing was wrong by 28dp and only looked wrong; `TvBarKeysTest` was green throughout |

### A7.3 — the resting search screen

| | |
| :---- | :---- |
| **What to look at** | Open Search and do not type. The mark, the name, the field and Advanced |
| **Passes if** | The block sits on what reads as the middle of the panel from three metres, and Advanced reads as belonging to the field above it |
| **Why it is here** | This screen's position has been wrong four times, each time for a different reason, and twice it measured as correct while looking wrong. It is now measured against a line 46% down the panel rather than half way — **the lift is the part most likely to be wrong on a large screen**, in either direction |

### A7.4 — picking a face for a new profile

| | |
| :---- | :---- |
| **What to look at** | Add a profile. Type a name, then walk the row of faces with the D-pad |
| **Passes if** | The faces are told apart from the sofa, the chosen one is obviously chosen, and the on-screen keyboard does not bury the row it belongs to |
| **Why it is here** | The keyboard covers the row while the name field has focus. That is normal on a television and it is also the first thing anyone will hit, so it wants judging rather than assuming |

---

## A8 — Recently Added, from [`017`](../agile/017_Recently_Added_and_the_Live_Guide_of_Quiblo.md)

**Where:** the television. **Nothing here has been seen on a device**, and half of it cannot be
without a working panel — `STOPPERS.md` S2. The dates come from `get_vod_streams` and
`get_series`, which no test account has ever been asked for.

### A8.1 — the tab is where it was asked to be

| | |
| :---- | :---- |
| **What to look at** | Walk the tab bar left and right from Search |
| **Passes if** | Recently Added sits after Live and before Movies, its label is legible from the sofa, and Back from it returns to Search like every other tab |
| **Fails if** | It sits anywhere else, or the bar's Left and Right skip it — the bar is index arithmetic and a new position repoints all of it |

### A8.2 — the row against the account

| | |
| :---- | :---- |
| **What to look at** | The row on an Xtream account, next to what the provider says is new |
| **Passes if** | Films and series are interleaved, newest first, and the first few are titles the provider genuinely added most recently |
| **Fails if** | It is alphabetical, or grouped by kind, or the newest titles are at the far right — that is a sort applied after the cap rather than before it |
| **Why it is here** | The order is the only claim this tab makes, and the only thing that can check it is somebody who knows what their provider added this week |

### A8.3 — the two empty states, which are different facts

| | |
| :---- | :---- |
| **What to look at** | The tab on an M3U playlist, and then on an account with no playlist at all |
| **Passes if** | The M3U case says the playlist carries no dates and names Xtream as what does; the no-source case says to add a playlist under Settings |
| **Fails if** | Either shows "Nothing here yet", or the M3U case shows a list — a playlist has no dates, so anything drawn there is ordered by nothing |

### A8.4 — the upgrade, which is the part that can lose something

| | |
| :---- | :---- |
| **What to look at** | Install over an existing build without clearing data. Favourites, continue watching, the catalogue |
| **Passes if** | Everything is where it was, Recently Added is empty until the first refresh, and it fills after one |
| **Fails if** | Anything is missing, or the row appears full immediately — nothing backfills the dates and nothing should |
| **Why it is here** | Schema 16. `AC-DATA-04`, and the most expensive place in this project to be wrong |

### A8.5 — the request count, which is the standing risk

| | |
| :---- | :---- |
| **What to look at** | Open the tab several times, then use the rest of the app |
| **Passes if** | Nothing slows down, no screen starts reporting the provider as unavailable, and series details still open |
| **Fails if** | The account starts being refused — see `docs/` on provider blocks. The dates ride inside lists the app already fetches, so this tab should cost **zero** additional requests, and a block traced to it means something is asking per row |

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
