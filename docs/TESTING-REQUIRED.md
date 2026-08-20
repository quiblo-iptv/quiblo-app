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

Last updated: **2026-08-20**.

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

**Superseded in part by A11.** The rule this section was written against read the first letter of
a title and stopped; round `019` Part B changed it to *any* letter outside a trailing bracketed
tag, which is what the two paragraphs below were describing the cost of. `4K | مسلسلات` is now
hidden by either script rather than read as Latin. The remaining limit still stands and is still
the design: **an Arabic film released under a transliterated Latin title is not hidden at all**,
because nothing in a playlist says it is Arabic. Report that as an observation, not a failure.

Everything else on this row — the browse and search behaviour, favourites keeping every title,
"Show everything again" — is unchanged and still owed a device. A11 is where the new rule's own
rows are.

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

### A8.3 — the empty states, which are different facts

**Superseded in part by `018`.** An M3U playlist no longer draws a blank tab: it falls back to
the end of its own list under a different heading. What remains here is the no-source case, and
the dated case with nothing inside the window.

| | |
| :---- | :---- |
| **What to look at** | The tab with no playlist configured at all |
| **Passes if** | It says to add a playlist under Settings |
| **Fails if** | It shows "Nothing here yet", which says nothing about what to do next |

### A8.4 — the upgrade, which is the part that can lose something

| | |
| :---- | :---- |
| **What to look at** | Install over an existing build without clearing data. Favourites, continue watching, the catalogue |
| **Passes if** | Everything is where it was, and on an Xtream account Recently Added is empty until the first refresh and fills after one |
| **Fails if** | Anything is missing, or a dated account's row appears full immediately — nothing backfills the dates and nothing should. An M3U playlist is the exception since `018`: its row is drawn from list order and is full straight away, headed "Latest in your playlist" |
| **Why it is here** | Schema 16. `AC-DATA-04`, and the most expensive place in this project to be wrong |

### A8.5 — the request count, which is the standing risk

| | |
| :---- | :---- |
| **What to look at** | Open the tab several times, then use the rest of the app |
| **Passes if** | Nothing slows down, no screen starts reporting the provider as unavailable, and series details still open |
| **Fails if** | The account starts being refused — see `docs/` on provider blocks. The dates ride inside lists the app already fetches, so this tab should cost **zero** additional requests, and a block traced to it means something is asking per row |

---

## A9 — the live guide, from [`017`](../agile/017_Recently_Added_and_the_Live_Guide_of_Quiblo.md) Part B

**Where:** the television. Reported as "EPG not working for live channels", on an Xtream panel.
The fetch and parse path was not the fault and is unchanged; what changed is *when* it is asked
and *what is said when it comes back empty*. **None of it has been seen against a real panel** —
`STOPPERS.md` S2.

### A9.1 — the list answers itself

| | |
| :---- | :---- |
| **What to look at** | Open Live and put the remote down. Do not press anything |
| **Passes if** | The top ten or so channels grow a programme line within a second or two, unprompted |
| **Fails if** | Every row is still blank after a few seconds — that is the reported defect, unfixed |
| **Why it is here** | The guide was fetched only for a row focus had rested on for 450ms, and nothing has focus when the screen opens |

### A9.2 — the request count, which is the reason it was not done this way before

| | |
| :---- | :---- |
| **What to look at** | Open Live, leave it, come back. Then hold Down and fly through the list. Then use the rest of the app |
| **Passes if** | Nothing slows, series details still open, and the account is not refused |
| **Fails if** | The provider starts refusing — the bound is ten per list per session, and a block traced here means the dedupe is not holding |
| **Why it is here** | This is the failure this project has already had **twice**. It outranks the feature |

### A9.3 — the two sentences

| | |
| :---- | :---- |
| **What to look at** | The bottom of the Live screen when no programmes appear |
| **Passes if** | A refusing provider produces the "not answering guide requests" line, and a provider with no listings produces the "no programme listings" line |
| **Fails if** | Either says nothing, or an M3U playlist shows one of them — a playlist has never carried listings and saying so every visit is nagging |
| **Why it is here** | Every one of these used to be a blank line, which reads as the app being broken and is what got reported |

### A9.4 — what has *not* been built, so nobody looks for it

**There is still no guide of any kind for an M3U playlist**, and no amount of this work changes
that: `M3uSource` implements no guide interface and nothing in the repository parses XMLTV. If a
guide is wanted for a playlist, that is `url-tvg`, gunzip, a parser and a cache — its own round.
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

## A9 — Round `018`: the metadata cache, the Recently Added window, and the detail line

**Where:** the television first, the phone for the two rows that say so. Nothing here has been
seen on a device. **The defect behind A9.1 was reported from two real televisions**, not from the
emulator, so a power cycle at the wall is the case to reproduce.

### A9.1 — an hour of scanning survives being switched off

| | |
| :---- | :---- |
| **What to look at** | Run a scan. Read "Held now: N titles looked up" in Settings. Switch the television off at the wall, switch it back on, open Settings again |
| **Passes if** | N is the same or larger, and starting a scan reports far less work to do than the first one did |
| **Fails if** | N drops, or the scan reports the whole catalogue again. **Those two are different faults** — a smaller N means rows were lost and the disk flush did not hold; an unchanged N with a full scan means something is counting them as unknown |
| **Why it is here** | This is the reported defect, and the number exists precisely so the two causes can be told apart. Nothing on the device could distinguish them while it was being diagnosed |

### A9.2 — the same, across an app update

| | |
| :---- | :---- |
| **What to look at** | Note N, install the new build over the old one without clearing data, read N again |
| **Passes if** | N is unchanged |
| **Fails if** | N is zero. If it is, check *how* the build was installed before believing the app: a differently signed build, or an install that uninstalls first, wipes everything the app owns and no code in this repository can prevent it |

### A9.3 — Recently Added, thirty days and no more

| | |
| :---- | :---- |
| **What to look at** | The row on the Xtream account, against what the provider says it added this month |
| **Passes if** | Everything in the row was added within thirty days, newest first |
| **Fails if** | It carries titles from last year, or it is empty on an account that has certainly added something this month |

### A9.4 — the fallback row on a playlist with no dates

| | |
| :---- | :---- |
| **What to look at** | The tab on an M3U playlist |
| **Passes if** | It is headed **"Latest in your playlist"**, holds films and series alternating, and they are the ones at the end of that playlist |
| **Fails if** | It is headed "Recently added" — the heading is the whole claim, and list order is not a date. Also fails if it is all films, which means the two kinds are not being taken separately |

### A9.5 — the kind badge, and the stillness behind it

| | |
| :---- | :---- |
| **What to look at** | Hold Right along the Recently Added row from the sofa |
| **Passes if** | Every tile says Movie or Series in its top-right corner, and the screen does not move vertically at all |
| **Fails if** | The catalogue twitches while the remote walks the row. That is #008 and the badge would be the cause — `TvBrowseScrollStabilityTest` covers it on the JVM, and this is the confirmation the JVM cannot give |

### A9.6 — the facts line on a film, a series, and an episode

| | |
| :---- | :---- |
| **What to look at** | A film's detail screen, a series' detail screen, and a season's episode list. Both apps |
| **Passes if** | The film shows a year and a length beside its score; the series shows a year and no length; episodes show their own lengths where the provider times them, and nothing where it does not |
| **Fails if** | A length reads `0m`, a year reads `1080`, or a series claims a running time. The first two are parsing accidents with a range check meant to stop them, and the third is a fact TMDB offers that this app deliberately does not use |

---

## A10 — Round `019` Part A: the keyboard and the category box

**Where:** the television, all of it. From
[`019`](../agile/019_Friction_on_the_Television_of_Quiblo.md) Part A. Neither change needs a
provider, so unlike most of this page these can be answered on the emulator — but the keyboard
half is exactly the sort of thing that behaved differently on the Haier before (#021), so the
panel is what settles it.

### A10.1 — walking past a field costs nothing

| | |
| :---- | :---- |
| **What to look at** | Open Settings and hold Down from the top, straight past the metadata key row, to the backup rows at the bottom |
| **Passes if** | No keyboard appears at any point. You arrive at the bottom in one movement |
| **Fails if** | A keyboard opens as focus lands on the key field, or the list jumps when it does |
| **Why it is here** | This is the report. It also re-tests #021 from the other side: no keyboard means no window resize means nothing for the list to chase |

### A10.2 — the field still works when it is wanted

| | |
| :---- | :---- |
| **What to look at** | Land on the metadata key, press centre, type a few characters, press the keyboard's Done. Then the same on the search box, the playlist form's four fields, and a new profile's name |
| **Passes if** | The keyboard opens on the press, the characters land in the field you pressed, and Done puts it away with focus still on that field |
| **Fails if** | Characters land in a different field — the failure this component was written for — or the keyboard cannot be dismissed, or focus is nowhere afterwards and the remote does nothing |
| **Why it is here** | Two stages means two ways for focus to be lost at the swap, and the app has been wrong about television focus four times |

### A10.3 — the playlist form and the profile name do not ambush you

| | |
| :---- | :---- |
| **What to look at** | Open Settings → playlists → add, and separately the add-profile screen |
| **Passes if** | The first field is highlighted and no keyboard is up |
| **Fails if** | A keyboard is already open when the screen appears |
| **Why it is here** | Both screens ask for focus on their first field as they open, which used to mean asking for a keyboard |

### A10.4 — the category box is passed in one press

| | |
| :---- | :---- |
| **What to look at** | Settings, on an account with a lot of categories. Move down through the Categories section to the Backup rows below it |
| **Passes if** | One press clears the box. It reads how many categories there are and how many are hidden |
| **Fails if** | Focus enters the list of categories and has to be walked through |

### A10.5 — and entered, and left

| | |
| :---- | :---- |
| **What to look at** | Press the box. Hide a category, rename one. Press Back. Then reopen it and hold Down past the last category |
| **Passes if** | It opens onto the first category's controls; Back closes it and leaves the box highlighted; holding Down walks out of the bottom and the box shuts behind you |
| **Fails if** | Back closes Settings instead of the box, or focus ends up on nothing, or the box stays open once focus has left it |
| **Why it is here** | Back is the way out, and it works by the box's handler being offered the key before the screen's. That precedence is worth seeing on a real remote |

---

## A11 — Round `019` Part B: hiding that hides

**Where:** the television, on an account with Arabic or another non-Latin script in it. From
[`019`](../agile/019_Friction_on_the_Television_of_Quiblo.md) Part B. The rule change is decided
in `core/common` and is covered on the JVM; what a device is needed for is what a *real
provider's* titles look like, which is the whole reason the old rule failed.

### A11.1 — the shape of the titles, before anything else

| | |
| :---- | :---- |
| **What to look at** | Browse Movies and Series with nothing hidden, and read the titles. Note how the provider writes its tags — leading, trailing, bracketed, piped |
| **Passes if** | You can say which of those shapes your account uses. This is a reading, not a test |
| **Why it is here** | The rule keeps a trailing bracketed tag and hides everything else. If this account tags some other way, the next two rows will say so and the rule needs revisiting rather than the code |

### A11.2 — hiding Arabic hides Arabic

| | |
| :---- | :---- |
| **What to look at** | Settings → hide Arabic. Then Movies, Series and Search |
| **Passes if** | Titles with Arabic anywhere in them are gone, including ones that begin with an English word |
| **Fails if** | Anything with Arabic letters in its name is still listed — that is the old first-letter rule, unchanged |

### A11.3 — and what it costs

| | |
| :---- | :---- |
| **What to look at** | With Arabic hidden, look for an English film your provider has tagged as dubbed or subtitled |
| **Passes if** | One tagged `[عربي]` or `(مترجم)` is still there; one tagged with a bare Arabic word on the end is gone |
| **Why it is here** | The second half is the accepted cost of the rule, written down so it is recognised as a decision rather than reported as a defect |

### A11.4 — a hidden category stays hidden when searched

| | |
| :---- | :---- |
| **What to look at** | Hide a category you know the contents of. Then search for something in it |
| **Passes if** | Nothing from that category comes back |
| **Fails if** | It does — which is the report |

### A11.5 — and comes back when asked for

| | |
| :---- | :---- |
| **What to look at** | Same search, then open Advanced and press Include hidden |
| **Passes if** | The hidden category's titles appear, and so do titles in a hidden writing system. Pressing it again puts them away. Leaving Search and coming back has it off |
| **Fails if** | It only brings back one of the two, or it is still on when the screen is reopened |

### A11.6 — a full page is still a full page

| | |
| :---- | :---- |
| **What to look at** | With a script hidden, search a term with a great many matches in the *other* script |
| **Passes if** | The row is as long as it would be with nothing hidden |
| **Fails if** | It is noticeably shorter — the filter is running after the cut again |

---

## A12 — Round `019` Part C: advanced search

**Where:** the television, on a scanned account — the genre filter reads the metadata cache, so
an unscanned catalogue has nothing to filter by and this section cannot be run at all. From
[`019`](../agile/019_Friction_on_the_Television_of_Quiblo.md) Part C.

### A12.1 — a genre fills both rows

| | |
| :---- | :---- |
| **What to look at** | Search → Advanced → pick a common genre such as Drama or Action, with the search box empty |
| **Passes if** | Both the films row and the series row have titles in them |
| **Fails if** | One of the two is empty. That is the report, and it is the whole of what changed |
| **Why it is here** | It needs a *large* catalogue. On a small one the old code passed too, which is part of why this took so long to be described accurately |

### A12.2 — and it is not one kind's leftovers

| | |
| :---- | :---- |
| **What to look at** | The same, on three or four different genres |
| **Passes if** | Neither row is consistently shorter than the other in a way the catalogue does not explain |
| **Fails if** | Series are always the short row — the cap is still being taken before the split somewhere |

### A12.3 — no live channels unless asked

| | |
| :---- | :---- |
| **What to look at** | Advanced search with a genre picked. Then Settings → Search → turn live channels on, and come back |
| **Passes if** | No live row at all by default; the live row appears once the setting is on |
| **Fails if** | Live channels are there with the setting off |

### A12.4 — off means not asked

| | |
| :---- | :---- |
| **What to look at** | With the setting off, use advanced search heavily for a few minutes |
| **Passes if** | Nothing slows and the account is not refused |
| **Why it is here** | Off is meant to mean the query is never made. This is the standing risk on every screen in this app and it outranks the feature |

---

## A13 — Round `020`: the For You tab

**Where:** the television. From [`020`](../agile/020_For_You_of_Quiblo.md). **The first row can be
seen on the emulator; the other two cannot be certified from this machine** — `STOPPERS.md` S2,
and the third additionally needs a scanned catalogue and a real watch history, which is days of
use rather than an afternoon.

### A13.1 — the tab, and the row that was already there

| | |
| :---- | :---- |
| **What to look at** | The bar. Then open the tab |
| **Passes if** | It reads **For You**, sits after Live and before Movies, and its first row is the Recently Added row exactly as it was — same titles, same order, same heading |
| **Fails if** | The tab moved, or the first row changed |

### A13.2 — the tab scrolls

| | |
| :---- | :---- |
| **What to look at** | Hold Down from the first row to the last |
| **Passes if** | The screen scrolls between rows and every row is reachable |
| **Fails if** | A row below the fold cannot be reached, or the screen jumps rather than scrolls |

### A13.3 — the screen does not shake

| | |
| :---- | :---- |
| **What to look at** | Hold Right along **each** of the three rows, from the top of the tab and from the bottom |
| **Passes if** | Nothing moves vertically. At all |
| **Fails if** | The rows twitch upward as a tile takes focus |
| **Why it is here** | This is #008, and the two new tile shapes are exactly the sort of thing that brings it back. `TvBrowseScrollStabilityTest` says they do not; a panel is what settles it |

### A13.4 — Now popular

| | |
| :---- | :---- |
| **What to look at** | With a Movie Database key configured. The second row |
| **Passes if** | At most five films and five series, each with a large number across the foot of its poster, and every one of them opens and plays from your own provider |
| **Fails if** | A tile opens nothing, or a number is drawn outside the poster, or the row holds titles the account does not carry |

### A13.5 — and what it costs

| | |
| :---- | :---- |
| **What to look at** | Open the tab, leave it, come back. Several times, over several days if you can |
| **Passes if** | The row is the same all week and the service does not start refusing |
| **Fails if** | The account is rate-limited, or the row changes between two openings on the same day |

### A13.6 — with no key, and with a key that is refused

| | |
| :---- | :---- |
| **What to look at** | Clear the Movie Database key. Reopen the tab |
| **Passes if** | The Now popular row is **gone**. Not empty, not spinning — absent |
| **Fails if** | An empty row with a heading is drawn |

### A13.7 — You may like

| | |
| :---- | :---- |
| **What to look at** | On a scanned catalogue, after watching several things on one profile |
| **Passes if** | The third row appears; each tile carries a line reading "Because you watched …" naming something you did watch; nothing already watched is in it; it is not five of the same genre |
| **Fails if** | The row names something you never watched, or suggests back what you just finished |

### A13.8 — and it belongs to one person

| | |
| :---- | :---- |
| **What to look at** | Switch to another profile and open the tab |
| **Passes if** | The suggestions differ, and a fresh profile has **no** third row at all |
| **Fails if** | Two profiles see the same suggestions — that is one person's viewing shown to another |

### A13.9 — the upgrade

| | |
| :---- | :---- |
| **What to look at** | Install over an existing build |
| **Passes if** | Every channel, favourite and resume point is where it was, and the tab works |
| **Why it is here** | Schema 18. The migration adds one empty table and touches nothing, which is exactly the sort of claim that should still be checked once |

---

## A14 — Round `021`: what 0.18.0 broke and what was always slow

**Where:** the television, on the real account. Every row here is about *how long something
takes* or *whether a row is there at all*, and neither can be answered on a small catalogue —
which is exactly how the defects in this round reached a release. From
[`021`](../agile/021_The_Catalogue_Under_Load_of_Quiblo.md).

### A14.1 — For You has all three rows

| | |
| :---- | :---- |
| **What to look at** | Kill the app, open it cold with a Movie Database key already configured, and go to For You |
| **Passes if** | Recently added, Now popular, and — on a profile that has watched something — You may like |
| **Fails if** | Only Recently added. That is the report, and the fix is about *timing*: the row was built before the encrypted key had been read |
| **Why it is here** | It must be a **cold** start. Opening the tab a second time in a warm process is the case that always worked |

### A14.2 — a key pasted while the tab is open

| | |
| :---- | :---- |
| **What to look at** | With no key configured, open For You, then go to Settings, paste a key, and come back |
| **Passes if** | Now popular fills in without the app being restarted |
| **Fails if** | The row stays absent until a relaunch |

### A14.3 — Movies and Series open

| | |
| :---- | :---- |
| **What to look at** | From the Live tab, press across to Movies, and time it against the same account in another player |
| **Passes if** | The first row of posters is there in about the time another player takes |
| **Fails if** | Seconds of spinner. Say how many — "still slow" cannot be acted on |
| **Why it is here** | Three separate changes claim this between them, and only a device can say which of them mattered |

### A14.4 — and they hold what they held

| | |
| :---- | :---- |
| **What to look at** | Walk right along two or three category rows, to the end of one |
| **Passes if** | Every row still has its titles, in the provider's order, under the provider's category names in the provider's category order |
| **Fails if** | A row is short where it was not, categories are alphabetical, or a row ends sooner than it used to |
| **Why it is here** | Each row is now capped at forty titles in the query. Forty is a deliberate choice, not a bug — but a row that was *shorter* than forty and got shorter still is |

### A14.5 — the channel list, and its guide

| | |
| :---- | :---- |
| **What to look at** | Open Live and do not touch the remote |
| **Passes if** | The list draws quickly, and the first several channels show what is on now without anything being focused |
| **Fails if** | Every row is blank until you rest on one. That is `017`'s defect returning, and the prefetch it names has moved to this screen |

### A14.6 — scrolling a long list

| | |
| :---- | :---- |
| **What to look at** | Hold right/down through a long category or channel list, past a few hundred items |
| **Passes if** | It keeps up, and nothing flickers or blanks as new pages arrive |
| **Fails if** | It stalls at regular intervals, or rows appear empty for a moment before filling |

### A14.7 — advanced search answers

| | |
| :---- | :---- |
| **What to look at** | Search → Advanced → press Action, with the search box empty. Then press two or three other genres |
| **Passes if** | Results in about a second, every time, including the second and third press |
| **Fails if** | A spinner that never resolves. That is the report |
| **Why it is here** | It needs a **scanned** account. The filter reads the metadata cache, and an unscanned catalogue has nothing to filter by |

### A14.8 — the upgrade, and what hiding does during it

| | |
| :---- | :---- |
| **What to look at** | Install over 0.18.0 with a writing system hidden in Settings, then open Movies straight away and again a minute later |
| **Passes if** | Titles in the hidden script are absent both times, and every channel, favourite and resume point survived |
| **Fails if** | Hidden titles appear for a while and then vanish, or the catalogue is empty, or nothing is hidden at all |
| **Why it is here** | Schema 19 adds three columns and fills them in the background afterwards. The whole design of that is that hiding keeps working while it runs, and this is the only way to see it |

### A14.9 — ambient keeps up

| | |
| :---- | :---- |
| **What to look at** | Play a 2.35:1 film and watch the black bars through a scene change |
| **Passes if** | The light in the bars moves with the scene rather than arriving after it |
| **Fails if** | It still lags visibly, or it flickers on every cut — the second is the risk of making it faster |

### A14.10 — and it has a switch

| | |
| :---- | :---- |
| **What to look at** | Settings → Playback → Ambient light. It should read On. Turn it off and play the same film |
| **Passes if** | The bars are plain black, and the setting is still off after a restart |
| **Fails if** | The switch reads Off when the screen opens, or turning it off changes nothing |

---

## A15 — Round `022`: the television, made to feel like one thing

**Where:** the television, except `A15.9` and `A15.10`, which need a phone. Six of these are about
how something *looks* or *feels*, which is the one class of claim a build cannot settle. From
[`022`](../agile/022_The_Television_Made_To_Feel_Like_One_Thing_of_Quiblo.md).

### A15.1 — the faces

| | |
| :---- | :---- |
| **What to look at** | Open the profile chooser on a profile that already had a generated picture |
| **Passes if** | It is a face on a coloured tile, and it is the *same* face every time the app opens |
| **Fails if** | Still four coloured shapes, or a different face on each launch — the second would mean the seed is being regenerated rather than read |

### A15.2 — and the chooser offers a dozen different ones

| | |
| :---- | :---- |
| **What to look at** | Add a profile, type a name, and look at the row of faces offered |
| **Passes if** | Twelve visibly different faces — different tiles, different shapes, mouths open and closed |
| **Fails if** | Several look the same. The arithmetic is pinned by tests, so this is really asking whether *twelve* is enough to look varied at a glance |

### A15.3 — the light behind the catalogue keeps up

| | |
| :---- | :---- |
| **What to look at** | Walk along a poster row one press at a time, then hold right through a whole row |
| **Passes if** | Stepping: the light changes about as fast as the poster does. Holding: one slow drift, no strobing |
| **Fails if** | It flashes while the D-pad is held. That is the risk this change takes, and the settle is what is supposed to prevent it |

### A15.4 — Search puts out the catalogue's light

| | |
| :---- | :---- |
| **What to look at** | Focus a colourful poster on Movies, then press up and left to Search |
| **Passes if** | The film's colours fade away rather than snapping off or staying |
| **Fails if** | The old colours are still there behind the search box |

### A15.5 — and lights itself

| | |
| :---- | :---- |
| **What to look at** | Sit on Search with an empty box for a minute |
| **Passes if** | Two soft pools of light moving slowly around, turning colour, going round in step with the highlight travelling round the search box |
| **Fails if** | It is brighter than a poster's light, or it is fast enough to be distracting, or the room and the box's highlight visibly disagree about where they are |

### A15.6 — the bar is always lit somewhere

| | |
| :---- | :---- |
| **What to look at** | Open Settings and come back. **Do it ten times.** Then open a film, come back, ten times |
| **Passes if** | Every single time, the bar is holding the remote — pressing right moves along it and the gear and face highlight when reached |
| **Fails if** | Even once the remote appears to do nothing and nothing is highlighted. This is a race, so once in ten is the whole defect |

### A15.7 — two backs close it

| | |
| :---- | :---- |
| **What to look at** | On Search, press back once, read the screen, then press back again |
| **Passes if** | "Press back again to close" along the bottom, then the app closes |
| **Fails if** | The first press closes it, or the second does not, or the line covers something |

### A15.8 — and it asks who is watching next time

| | |
| :---- | :---- |
| **What to look at** | Close it that way, then open it again from the launcher |
| **Passes if** | The "who is watching" chooser |
| **Fails if** | It resumes into the same profile. That is the reported half, and it is the half that survives a fix which only closes the activity |

### A15.9 — it installs on a phone

| | |
| :---- | :---- |
| **What to look at** | Install the television app on a phone and find it in the launcher |
| **Passes if** | It installs, appears in the launcher, opens, and turns when the phone turns |
| **Fails if** | The store or `adb install` refuses it, or it installs and cannot be found, or it stays landscape |

### A15.10 — and it can be driven by a finger

| | |
| :---- | :---- |
| **What to look at** | On the phone: tap the tabs, the gear, the profile face, a poster, a channel. Then open Settings and tap into the Movie Database key field |
| **Passes if** | Every one of them responds, and the keyboard does not cover the field being typed into |
| **Fails if** | The tabs do nothing, or the keyboard hides the field |
| **Why it is here** | Expected and accepted: everything is sized for a sofa and will look large, and the ambient light stays dark because it follows focus. Neither is a failure of this ticket |

### A15.11 — and the panel is exactly as it was

| | |
| :---- | :---- |
| **What to look at** | Back on the television: open Settings and focus the Movie Database key field |
| **Passes if** | The list does not move when the keyboard opens |
| **Fails if** | It shifts or shakes. `A15.9`'s manifest changes and the keyboard inset are the two edits that could have brought `#021` back |

## A16 — Round `023`: the shelves on For You

**Where:** the television, except `A16.7`, which is the phone. From
[`023`](../agile/023_The_Shelves_of_For_You_of_Quiblo.md). Three of these need a Movie Database key
and a scanned catalogue; two of those additionally need a provider that does *not* carry some of
this week's popular titles, which is most of them.

### A16.1 — two rows, ten each

| | |
| :---- | :---- |
| **What to look at** | Open For You with a Movie Database key set and a scanned catalogue |
| **Passes if** | **Popular films** and **Popular series** are separate rows, each numbered 1 upwards, no Movie/Series badge on either |
| **Fails if** | One mixed row, or the numbers restart partway along, or a row holds more than ten |

### A16.2 — the number reads from the sofa

| | |
| :---- | :---- |
| **What to look at** | Both popular rows, from a normal viewing distance |
| **Passes if** | The figure stands beside the poster, large enough to read across the room |
| **Fails if** | It is inside the artwork, or it is too small to read at three metres — the whole argument for a numbered row is that the number is the content |

### A16.3 — and walking the row does not move the screen

| | |
| :---- | :---- |
| **What to look at** | Hold right along a popular row, past position 9, and watch the rows above and below |
| **Passes if** | Nothing moves vertically, including across the 9 to 10 crossing |
| **Fails if** | The catalogue twitches. **This is defect #008 and it is the risk this round took**: the numeral now stands outside the poster, and only a fixed-width gutter keeps 1 and 10 the same width. `TvBrowseScrollStabilityTest` walks this shape, so a failure here means the test's model of the screen is wrong |

### A16.4 — a title the provider does not carry

| | |
| :---- | :---- |
| **What to look at** | Find a place in either popular row marked **Unavailable** |
| **Passes if** | The poster is dimmed, the number is still there and still in sequence, and pressing it writes **Not available yet** under the tile without opening anything or moving anything |
| **Fails if** | The tile plays, or a dialog appears, or the row changes height when the line appears |

### A16.5 — the rows are there before they are worked out

| | |
| :---- | :---- |
| **What to look at** | Leave For You, go to Movies, come back. Then close the app entirely and reopen it |
| **Passes if** | Both rows are on screen immediately, in the order they were in, and nothing flashes empty first |
| **Fails if** | An empty shelf, or a visible pause, or the rows arrive one at a time |

### A16.6 — Search's light reaches the corners

| | |
| :---- | :---- |
| **What to look at** | Sit on Search with an empty box and look at the edges of the screen and the area behind the tab bar |
| **Passes if** | The glow runs to all four corners and passes behind the bar, exactly as a poster's light does on Movies |
| **Fails if** | A dark margin down either side, a dark band under the bar, or a visible rectangle edge anywhere |

### A16.7 — categories in your own order, on the phone

| | |
| :---- | :---- |
| **What to look at** | Settings, the categories card. Move one category up twice, then open Live |
| **Passes if** | It is two places higher in the card, and the category picker on Live shows the same order |
| **Fails if** | The list scatters rather than shifting one place, or the two screens disagree |

### A16.8 — and on the television

| | |
| :---- | :---- |
| **What to look at** | Settings, open the category box, move one down with the remote. Leave settings and open Movies |
| **Passes if** | One press moves it one place, focus stays where it was, and the poster rows on Movies are in the new order |
| **Fails if** | Focus jumps to another row, or the box closes, or the rows do not follow |

### A16.9 — and the order survives a refresh

| | |
| :---- | :---- |
| **What to look at** | Move two categories, then refresh the source from Settings |
| **Passes if** | Both are still where you put them |
| **Fails if** | They are back in the provider's order. The order is stored against the provider's own name for the category, so this is the assertion that the name is what came back |

### A16.10 — Recently Added is fifteen

| | |
| :---- | :---- |
| **What to look at** | Count the first row on For You on an account with plenty added this month |
| **Passes if** | Fifteen at most |
| **Fails if** | More. Forty is what it was |

---

## A17 — Round `024`: the work that runs on its own, and two defects

**Where:** both apps. From [`024`](../agile/024_The_Work_That_Runs_On_Its_Own_of_Quiblo.md).
`A17.1` and `A17.2` are the two a viewer reported; the rest are about work nobody watches, which
is the hardest class of thing to certify and the reason these are written down at all.

### A17.1 — Resume is there when you come back

| | |
| :---- | :---- |
| **What to look at** | Play a film for a minute, press back, look at the button. Repeat five times on the television and five on the phone |
| **Passes if** | **Resume from 1:0x** every single time, immediately, with no flicker of "Play" first |
| **Fails if** | It says Play even once. **Five times each is the point**: this was a race, and a race that is lost one time in five looks fixed if you try it twice |

### A17.2 — and after the app is killed

| | |
| :---- | :---- |
| **What to look at** | Play something for two minutes. Kill the app from the recents list — do not press back. Reopen and go to the title |
| **Passes if** | It offers to resume from somewhere in the last ten seconds of what you watched |
| **Fails if** | It offers Play, or a position from when you started. This is the ten-second write, and killing the app is the only way to exercise it |

### A17.3 — a film's details are in the middle

| | |
| :---- | :---- |
| **What to look at** | Open a film with a short description on the television, then one with a long one |
| **Passes if** | The short one sits centred vertically; the long one starts at the top and scrolls as it always did |
| **Fails if** | The short one is still pressed against the top, or the long one has lost its top |

### A17.4 — the catalogue syncs on its own

| | |
| :---- | :---- |
| **What to look at** | Leave the app installed and untouched for five days, then open For You. **Owner: whoever can leave a device alone that long** |
| **Passes if** | Recently added holds titles the provider added while you were away |
| **Fails if** | The row is unchanged from five days ago. Check the provider actually added something before calling this a failure |

### A17.5 — and it does not renumber everything while it does

| | |
| :---- | :---- |
| **What to look at** | Before the wait: note three titles in Recently added and favourite one. After it: look again |
| **Passes if** | The three are still there, in the same order, with the same relative dates, and the favourite is still a favourite |
| **Fails if** | Recently added is suddenly the whole catalogue. That is the merge having become a rebuild, and it is the single failure this round's design exists to prevent |

### A17.6 — a title the provider dropped goes

| | |
| :---- | :---- |
| **What to look at** | Harder to arrange: needs a provider that removes something. Search for a title you know has gone after a sync |
| **Passes if** | It is not in the catalogue |
| **Fails if** | It is still listed and pressing it plays nothing |

### A17.7 — popular changes without being asked

| | |
| :---- | :---- |
| **What to look at** | With a Movie Database key set, note the popular rows. Leave it two days without opening For You, then open it |
| **Passes if** | The rows have moved on |
| **Fails if** | Identical after two days. TMDB's weekly lists do change, so identical rows mean the check did not run |

### A17.8 — and none of it happens without a key

| | |
| :---- | :---- |
| **What to look at** | On an installation with no Movie Database key, leave it several days and watch the battery figures for Quiblo |
| **Passes if** | No repeated wake-ups, and no popular row |
| **Fails if** | Regular activity with nothing to show for it. A worker that treats "no key" as a failure retries forever, which is the loop this asserts is absent |

---

## A18 — Round `025`: the suggestions row, rebuilt

**Where:** both apps, and mostly the television, where the row lives. From
[`025`](../agile/025_What_You_May_Like_of_Quiblo.md). **Every ticket here needs a real watch
history**, which is days of use rather than an afternoon — there is no way to compress a scorer
whose whole subject is what somebody actually watched.

### A18.1 — it says nothing until it knows something

| | |
| :---- | :---- |
| **What to look at** | A fresh profile. Watch one thing through, then open For You |
| **Passes if** | There is no **You may like** row at all — not an empty one, not a spinner |
| **Fails if** | A row appears. One watched title cannot produce four honest suggestions, and this is the whole of the "wait and learn" ask |

### A18.2 — and then it does

| | |
| :---- | :---- |
| **What to look at** | On the same profile, watch five different things most of the way through |
| **Passes if** | The row appears, and every tile says which of your own titles put it there |
| **Fails if** | Still nothing after five, or a tile with no reason under it |

### A18.3 — the complaint itself

| | |
| :---- | :---- |
| **What to look at** | On a profile whose viewing is mostly anime, read the whole row |
| **Passes if** | The suggestions are recognisably like what was watched — other anime, other shows of the same sort |
| **Fails if** | Superhero dramas, talk shows, or dubbed titles in a language nothing watched was in. **This is the reported defect** and it is the one judgement a test cannot make |

### A18.4 — a language you do not watch

| | |
| :---- | :---- |
| **What to look at** | A profile that watches only English or only Arabic |
| **Passes if** | The row is mostly in that language |
| **Fails if** | It is a mix with no pattern. Note this is separate from hiding a writing system, which is `A16`'s and is absolute |

### A18.5 — thumbs, on both apps

| | |
| :---- | :---- |
| **What to look at** | Open a film's details on the phone and on the television. Press the thumbs-up, then press it again |
| **Passes if** | It fills, then empties. The same on the series screen |
| **Fails if** | It cannot be taken back, or the two apps disagree about the same title |

### A18.6 — and a thumbs down is heard

| | |
| :---- | :---- |
| **What to look at** | Thumbs-down something the row was suggesting, or something like it. Leave For You and come back |
| **Passes if** | That title is gone from the row, and nothing else is suggested *because of* it |
| **Fails if** | It is still there. Note the row is remembered between opens (`A16.5`), so this may take one rebuild to show |

### A18.7 — a comfort film

| | |
| :---- | :---- |
| **What to look at** | Watch the same film three or four times over a week |
| **Passes if** | Suggestions like it move up the row |
| **Fails if** | Watching something repeatedly changes nothing at all |

### A18.8 — the row does not become one genre

| | |
| :---- | :---- |
| **What to look at** | Read the whole row on a profile with varied viewing |
| **Passes if** | Several tastes are represented, not four of the strongest one |
| **Fails if** | Twenty thrillers |

### A18.9 — nothing is sent anywhere

| | |
| :---- | :---- |
| **What to look at** | Watch several things, rate a few, then check the network — a router log, or a proxy |
| **Passes if** | Traffic goes only to your own provider and to The Movie Database |
| **Fails if** | Anything else at all. `FREEZE.md` §4.5, and the reason a scorer was chosen over a service |

---

---

## A19 — Round `026`: first launch, the timeline, and updating itself

**Where:** the television only. From
[`026`](../agile/026_First_Launch_The_Timeline_And_Updating_Itself_of_Quiblo.md). Three of these
need a **fresh install** — not a cleared cache, an uninstall — because consent is the one screen
that cannot be reached twice.

### A19.1 — the third page exists and can be got to

| | |
| :---- | :---- |
| **What to look at** | Uninstall, install, and press centre twice from the first screen |
| **Passes if** | Page three appears and offers **Add a playlist** and **Skip for later** |
| **Fails if** | The app enters at the end of the terms, or page three has one control |

### A19.2 — skipping actually skips

| | |
| :---- | :---- |
| **What to look at** | On page three, walk right to **Skip for later** and press it |
| **Passes if** | The app opens, with no source and no error. Settings → Playlists is empty and works |
| **Fails if** | It hangs, or the consent screen comes back on the next launch |

### A19.3 — adding a playlist from first launch

| | |
| :---- | :---- |
| **What to look at** | On page three press **Add a playlist**, type a real M3U URL, press Save |
| **Passes if** | It says it is loading, then says how many channels it loaded, and the app opens with them in it |
| **Fails if** | It enters the app while still loading, or enters with nothing and no message |

### A19.4 — and a playlist that will not load

| | |
| :---- | :---- |
| **What to look at** | The same, with a URL that does not resolve |
| **Passes if** | It says the source could not be loaded, and offers **Try again** and **Skip for later** |
| **Fails if** | It enters the app silently, or leaves the viewer on a screen with no way forward |

### A19.5 — the playlist screen is in the middle of the panel

| | |
| :---- | :---- |
| **What to look at** | Settings → Playlists and accounts, on the largest panel available |
| **Passes if** | The column is centred left-to-right, with equal space either side |
| **Fails if** | It hugs the left edge, or runs the full width of the panel |

### A19.6 — Save carries the light

| | |
| :---- | :---- |
| **What to look at** | Open the add form and walk focus down to **Save** |
| **Passes if** | The button's outline carries the travelling light Search and Play use, dimmer than Search's |
| **Fails if** | No light, or a light as bright as the search field's — it must not out-shout the field being typed into |

### A19.7 — the timeline takes focus

| | |
| :---- | :---- |
| **What to look at** | Play a film, press centre for the controls, press down once |
| **Passes if** | The progress bar itself is focused. Down again reaches subtitles/audio/picture fit |
| **Fails if** | Down goes straight past the bar, or the bar cannot be got off again with up or down |

### A19.8 — presses stack into one seek

| | |
| :---- | :---- |
| **What to look at** | On the focused bar, press right five or six times quickly, then stop |
| **Passes if** | The mark runs ahead of the picture as you press, and the film jumps **once**, about half a second after the last press |
| **Fails if** | It jumps on every press, or several times. **This is the whole feature** — one seek per run |

### A19.9 — a long run crosses the film

| | |
| :---- | :---- |
| **What to look at** | Hold right, or press it fifteen times, on a two-hour film |
| **Passes if** | The mark accelerates and reaches the far end in a few seconds |
| **Fails if** | Fifteen presses move it two and a half minutes. Note the first four presses are the interval from Settings on purpose |

### A19.10 — and the bar does not snap backwards

| | |
| :---- | :---- |
| **What to look at** | Immediately after a scrub lands, watch the bar for a second |
| **Passes if** | It stays where it was sent |
| **Fails if** | It flicks back to where the film was and then forward again |

### A19.11 — live has no timeline

| | |
| :---- | :---- |
| **What to look at** | Play a live channel and press down from play/pause |
| **Passes if** | It goes to the options row, as it always did. There is no bar and nothing to focus |
| **Fails if** | A bar appears, or down lands on nothing |

### A19.12 — check for updates, from behind

| | |
| :---- | :---- |
| **What to look at** | On a build older than the newest release, Settings → About → Updates → **Check now** |
| **Passes if** | It names the newer version. **Download** appears |
| **Fails if** | It says you are current, or says nothing |

### A19.13 — and from the front

| | |
| :---- | :---- |
| **What to look at** | The same on a build of the newest release |
| **Passes if** | It says you are on the newest release |
| **Fails if** | It offers the version you are already running |

### A19.14 — the download is verified before it is offered

| | |
| :---- | :---- |
| **What to look at** | Press **Download**, wait, then press **Install** |
| **Passes if** | It says the checksum matched and names the file, then the system installer opens |
| **Fails if** | It offers to install without mentioning the checksum, or the installer opens on a file that is still downloading |

### A19.15 — the television that will not install

| | |
| :---- | :---- |
| **What to look at** | A set with installs from unknown sources locked down |
| **Passes if** | Either the system's own permission prompt appears, or the row says the installer would not open and names the downloaded file |
| **Fails if** | Pressing Install does nothing at all |

### A19.16 — nothing is checked unprompted

| | |
| :---- | :---- |
| **What to look at** | Watch the network on a launch, on a browse, on a playback — without opening Settings |
| **Passes if** | Nothing goes to `api.github.com` |
| **Fails if** | Anything does. The check is a button and nothing else, and `tv_consent_terms_body` is the promise it keeps |

### A19.17 — offline says offline

| | |
| :---- | :---- |
| **What to look at** | Pull the network, then press **Check now** |
| **Passes if** | It says the television is not online |
| **Fails if** | It says you are up to date. Being unable to ask is not the same as being current |

---

## A20 — Round `027`: eight things found by using it

**Where:** the television only, against a real account with a TMDB key configured. From
[`027`](../agile/027_Bug_Round_of_Quiblo_—_Round_4.md). Every row below needs a viewer to do a
*second* thing — come back from somewhere, press twice, watch for a fortnight — which is why none
of them was found by opening a screen and looking at it.

### A20.1 — back lands where you left

| | |
| :---- | :---- |
| **What to look at** | Search a term, walk right to the fourth or fifth result, open it, then press Back |
| **Passes if** | The remote is on the tile that was opened, with the row scrolled where it was |
| **Fails if** | It is on the first result, or on the tab bar with the row back at its start |

### A20.2 — and from the player, and on the catalogue tabs

| | |
| :---- | :---- |
| **What to look at** | The same from Movies and from For You, and after actually *playing* something and backing out |
| **Passes if** | Each returns to the tile it was opened from |
| **Fails if** | Any one of them lands at the top. Series is the one that already worked — it is the comparison, not a case |

### A20.3 — walking the tab bar still works

| | |
| :---- | :---- |
| **What to look at** | With a tile focused deep in Movies, press Up to the bar and walk along it |
| **Passes if** | The bar keeps the remote for the whole walk |
| **Fails if** | The content snatches focus back mid-walk. That is the fault A20.1's fix must not introduce |

### A20.4 — the controls keep the remote

| | |
| :---- | :---- |
| **What to look at** | Play a film. Press Down to open the controls **immediately**, before the duration appears, then again a minute in. Walk the rows each time |
| **Passes if** | Something is always highlighted, and the arrows always move it |
| **Fails if** | The controls come up with nothing lit, or go dead after a button appears — the Back-then-Down workaround is the symptom |

### A20.5 — the timeline shows where you are aiming

| | |
| :---- | :---- |
| **What to look at** | On the controls, press Down to the bar and hold Left for a few seconds |
| **Passes if** | A circle on the line travels as you press, and is visibly larger while the bar has the remote |
| **Fails if** | Nothing on the bar moves until the seek lands, or the controls below the bar shift when it takes focus |

### A20.6 — a focused result keeps its whole title

| | |
| :---- | :---- |
| **What to look at** | Search a term, open **Advanced**, then walk onto a film and onto a series |
| **Passes if** | The title under the focused poster is whole, on every row |
| **Fails if** | Its bottom third is sliced off. This is #019's fault returning, and it returned once already |

### A20.7 — the two switches

| | |
| :---- | :---- |
| **What to look at** | Open **Advanced**. The switches sit on the field's own row, one press right of Advanced |
| **Passes if** | Both read as switches, the knob slides, and **Include TV channels** puts live results back into an advanced search |
| **Fails if** | Either is behind the suggestions in the chip strip, or is drawn like a genre, or changes nothing |

### A20.8 — a genre stays chosen

| | |
| :---- | :---- |
| **What to look at** | Choose a genre, then press the same chip again |
| **Passes if** | It stays chosen. **Clear** is what empties it |
| **Fails if** | The second press clears the filter |

### A20.9 — suggestions appear for somebody who stars things

| | |
| :---- | :---- |
| **What to look at** | On a profile with at least five favourites — watched or not — open **For You** and scroll to the bottom |
| **Passes if** | **You may like** is drawn, and each tile says which title caused it |
| **Fails if** | The row is absent. Note the catalogue must have been *described*: the coverage figure under Advanced search says how much of it has |

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
