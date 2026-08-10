**Bug Round of Quiblo — Round 3**

Eleven faults reported from `docs/INC_AGILE.md`, found by using the television and phone
builds against the real account rather than by reading the code — plus a twelfth found while
triaging them, which the intake had filed as a feature.

**Created:** 2026-08-10, against commit `b7bcba4` on `main`.
**Ships as:** part of `1.0.0-beta.1`. This is defect work, not scope — no `FREEZE.md`
amendment is required for any of it, with the single exception called out under **#020**.

**Executed before [`013`](013%20Increment%20Round%20of%20Quiblo%20—%20the%20catalogue%20a%20viewer%20actually%20uses.md)
and [`014`](014%20One%20Entry%20Per%20Title%20of%20Quiblo%20—%20duplicates,%20qualities%20and%20languages.md),
in full.** The reason is in "Why this comes first" below.

---

| Bug | Platform | Criterion | Wave | Description |
| :---- | :---- | :---- | :---- | :---- |
| #012 | TV | AC-TV-02 | 3 | The first tile of the continue-watching row is clipped by its own container |
| #013 | TV | AC-PLAY-01 | 3 | Switching shows leaves the previous stream's last frame on screen until the new one loads |
| #014 | TV | AC-TV-11/12 | 2 | "Add to favourites" is trimmed, and there is no remove-from-history control |
| #015 | TV | AC-TV-11/12 | 1 | The detail screen opens already scrolled: artwork cropped from the top, title off screen |
| #016 | Both | AC-PROF-01 | 1 | The app does not ask who is watching; the chooser is square-avatared and off-centre |
| #017 | Both | AC-NFR-08 | 2 | Favourites are titled `VOD` and `SERIES` — enum names, on screen |
| #018 | TV | — | 3 | Advanced search takes a visible pause to produce its genres, with nothing shown meanwhile |
| #019 | TV | AC-TV-14 | 2 | Advanced search: no Series heading, cropped titles when focused, films called "Films" |
| #020 | TV | **AC-TV-03** | 1 | Back from a title returns to the catalogue rather than to the title's own screen |
| #021 | TV | AC-TV-10 | 1 | Opening the API-key field makes the settings screen shake |
| #022 | TV | AC-TV-09 | 2 | The API-key field is not aligned with the controls above and below it |
| #023 | Both | AC-PLAY-04 | 2 | Audio tracks cannot be selected from either player, though the engine exposes them |

## Why this document exists

**Five of these are failures of criteria that `ACCEPTANCE-SWEEP.md` §7 records as "Not
run".** AC-TV-03, 10, 11, 12 and 14 have never been swept, and the first person to drive
those screens with a remote found faults in all of them. That is the sweep beginning to
happen informally, and its early result is that the television is not yet sweepable.

**One of them is not a defect.** #020 describes behaviour that AC-TV-03 currently *demands*.
The criterion and the viewer disagree, and the disagreement has to be settled in writing
before a line is changed, because a fix either way makes one of the two wrong.

**Two have precise mechanisms already**, found while triaging rather than while fixing, and
both are one-line changes: #012 and #015. They are in wave 3 and wave 1 respectively, which
is a statement about consequence rather than about effort.

## Why this comes first

Everything in `013` and `014` lands on the screens listed above. Autocomplete goes in the
search field that #019 mislabels; the merge-seasons and sort controls go on the detail screen
that #015 opens cropped; avatars go in the chooser that #016 never shows. **Building a
feature onto a broken screen means fixing the screen twice** — once now and once around the
new control — and the second fix is the expensive one because it has more to not break.

`006` gate 5 makes the same argument for the sweep: it must run on a finished tree, or it
runs twice. These eleven are the newest entries on that gate's list.

## Decisions taken

| Question | Chosen |
| :---- | :---- |
| Sequencing against features | All eleven close before any item in `013` or `014` starts |
| Sequencing within the round | By consequence, not by cost — see the waves below |
| Who is watching (#016) | **Ask at every launch.** The stored choice becomes session state |
| Back behaviour (#020) | Blocked on an acceptance decision. Recommendation below, to be dated as an amendment |
| Naming (#017, #019) | One vocabulary across both apps: **Live**, **Movies**, **Series**. No enum names, no caps, no "Films", no "VOD" |

---

## Wave 1 — the ones that lose the viewer

### #020 — back does not return to where the viewer was

**Reported:** pressing back from a film or an episode lands on the Movies or Series home
screen instead of on the title's own detail screen, and a series forgets which episode was
being watched.

**This contradicts an acceptance criterion, and that is the finding.** AC-TV-03 reads: *Back
from any screen returns to the category bar; back from the bar exits the app.* Written for a
frontend that had no detail screens — Amendment 4 added those the following day — it now
describes a back button that throws away a step every time it is pressed. Amendment 4 added
AC-TV-11 and 12 without re-reading AC-TV-03 against them.

**Recommendation, to be decided before the fix is written.** Restate AC-TV-03 as: *Back pops
exactly one step of the journey the viewer took, and never strands them. From a top-level
screen, back exits.* The property AC-TV-03 exists to protect — no screen that back cannot
leave — is kept; the "returns to the category bar" clause was a description of a two-level
app and is not worth preserving now that the app is three levels deep. Record it as a dated
amendment in the same pass as Amendment 7 (`006` gate 2), because it changes what "done"
means rather than how something is built.

**The episode cursor needs no new storage.** The last episode a viewer started for a given
series is already recorded, per profile, in watch history — the same record that draws the
continue-watching row. Restoring the cursor is a query, not a table. Anything stored
separately would be a second source of truth for one fact and would drift from the row on
the home screen.

**Exit criterion.** From a playing episode, back reaches that series' detail screen with
focus on the episode just watched, and a second back reaches Series. The same holds for a
film in two presses. Nothing on any screen becomes unreachable by back, and no screen
requires two presses to leave.

### #016 — the app never asks who is watching

**Reported:** the chooser appears once and never again. It should ask at every launch, offer
create-profile and Guest, use circular avatars, and sit in the middle of the screen.

**Mechanism, confirmed.** `ProfileRepository.activeProfile` is `null` only when the stored id
matches no row, and `ProfileStore` keeps that id across process death. So the chooser is
shown exactly once per install, plus whenever a guest session is cleaned up at startup —
which is why guest appears to behave correctly and named profiles appear not to. Nothing here
is broken; the app is doing what it was built to do, and what it was built to do is wrong for
a television in a household, which is the case profiles were introduced for.

**Fix.** Clear the stored active id at startup, in the same place `endGuestSessions()` already
runs, so the choice is session state on both apps. No "remember me": a switch that defaults to
skipping the question reintroduces the bug for whoever leaves it on, and the question costs
one D-pad press.

**Build the chooser once.** `INC-F0` in `013` puts avatars on this screen and a profile
control beside the settings gear. The circular avatar asked for here *is* that feature's
groundwork, so give the tile an artwork slot now and fill it there rather than laying out the
screen twice.

**Exit criterion.** AC-PROF-01 passes after a force-stop and reopen, not only on a fresh
install. The chooser is centred, its avatars are circular, and creating a profile or choosing
Guest from it takes no more presses than it does today.

### #015 — the detail screen opens already scrolled

**Reported:** on a series especially, the poster is cut off at the top and the title is not on
screen at all, because focus lands on the first button and drags the scroll down with it.

**Mechanism, confirmed.** `TvMovieScreen.kt:110` requests focus on the first action button;
that button is inside the `verticalScroll` column started at line 119. A focus request brings
its target into view, and Compose satisfies that by scrolling the column — before the viewer
has pressed anything. The report's own explanation is correct.

**Fix.** Two candidates, and the first is preferred:

1. **Do not let the initial focus scroll anything.** Request focus on the first action without
   the bring-into-view that accompanies it, so the screen opens at its top with the button
   focused. Focus and scroll position are separate facts and only the second is wrong here.
2. Bound the scrolling region so the header cannot leave the screen, as the report suggests.
   This is a layout change with a knock-on for long plots and multi-season series, and it is
   the fallback if (1) turns out to fight the focus machinery.

Round 2 already established that this panel has 444dp of usable height after overscan. Any fix
is judged at that geometry, not at the emulator's.

**Exit criterion.** Opening any film or series shows its title and the top of its artwork with
no input, with focus already on the first action, on the 960x540dp panel.

### #021 — the settings screen shakes when the API-key field is opened

**Reported:** opening the API-key entry makes the screen shake continuously. The report names
a focus race, which matches what the symptom looks like.

**This is the #008 family, and it is the fourth appearance.** Every member of it has the same
shape: something reports bounds that change every frame, and a scrollable container chases
them. In #008 it was a focusable inside an animating scale. Here the suspect is the IME
opening and resizing the window while a focus request or a bring-into-view is still resolving,
so the field is scrolled toward a target that moves as it is approached.

**Do not argue this one — measure it.** `TvBrowseScrollStabilityTest` is the pattern:
Robolectric, the panel's real geometry, the scroll position read frame by frame, and an
assertion of the property (*opening the keyboard must not move the settings list after the
field is on screen*). Four wrong answers were argued rather than measured last time this
species appeared, and the harness that ended it exists and can be pointed at another screen.

**Exit criterion.** With the on-screen keyboard up, the settings list reports the same scroll
offset on every frame for a full second, in the harness and on the Haier. AC-TV-10 is re-run
after it, since the same field is what that criterion tests.

---

## Wave 2 — the words on the screen

These are cheap, they are all visible within seconds of opening a screen, and they are the
first thing a stranger installing the beta will judge. Grouped because they want one decision
made once: **the app has one vocabulary — Live, Movies, Series — and every screen uses it.**

### #017 — favourites are labelled with enum names

**Mechanism, confirmed.** `TvPosterRows.kt:257` uses `channel.kind.name` as the row title when
showing favourites. `ChannelKind.VOD` prints as `VOD`, `SERIES` as `SERIES`. It is a domain
enum reaching the screen unaccompanied by a string resource, which also makes it untranslatable
— AC-NFR-08 forbids user-facing strings outside `strings.xml`, and this is one.

**Fix.** A `stringResource` per kind, shared by both apps, and a rule that no `kind.name`
reaches a composable. Worth a lint or a test: this is the third label defect in this round and
the mechanism behind two of them is a domain value used as display text.

### #019 — advanced search says the wrong things and crops the right ones

Three faults in one screen, one of which is confirmed: `app-tv/src/main/res/values/strings.xml:67`
declares `tv_search_movies` as **"Films"**. Everywhere else in the app the same catalogue is
"Movies". The remaining two — a missing Series heading, and series titles cropped when focused —
are reported and undiagnosed; the crop is likely the same headroom problem as #012 and should be
checked against that fix before being investigated separately.

### #014 — a trimmed button and a missing one

"Add to favourites" does not fit its button, and there is no way to remove an entry from watch
history from the detail screen. The label is a sizing fix; the history control is a small piece
of new work on a screen wave 1 is already touching, which is why it sits here rather than in
`013`. `INC-F3` in `013` adds the same action by long-press on the continue-watching row —
**build the repository call once and give both screens the same one.**

### #022 — the API-key field is not aligned with its neighbours

The field sits out of line with the controls above and below it in the right-hand column. Cosmetic,
adjacent to #021, and worth doing in the same pass because both are the same widget in the same
screen — but it is not the same defect and closing #021 does not close it.

### #023 — audio tracks are unreachable, and that is an unmet criterion

**Found while triaging, not reported.** `docs/INC_AGILE.md` files multi-audio support as feature
12, on the assumption that it is new work. It is not.

`Media3PlayerController` has exposed `audioTracks`, `textTracks`, `selectAudioTrack` and
`selectTextTrack` since the player was built, and reads them off `onTracksChanged` for whatever
the container declares. What is missing is the way in: the phone cycles *text* tracks from one
button (`PlayerScreen.kt:293`) and offers nothing at all for audio, and the television player
offers neither.

**AC-PLAY-04 says a viewer can switch between multiple audio or subtitle tracks.** It has not
been run. It does not pass. That makes this `1.0.0` work rather than increment work, and it is a
defect of a shape this project has met before in reverse: not a control that does nothing, but a
capability with no control.

**Fix.** A track menu in both players — audio and subtitles in one list, with the current
selection marked and "off" available for subtitles. On the television it opens from the same
controls overlay AC-TV-06 governs, so it must not pause playback. `013` INC-F11 later styles
subtitles from that same menu, and `014` adds a version switcher to it, so put it where both can
extend it.

**Exit criterion.** On a file carrying two audio tracks, both players list both, name them by
language where the container declares one, and switch between them without restarting playback.
AC-PLAY-04 is then run for the first time, on both apps.

---

## Wave 3 — clipping, stale frames, and waiting

### #012 — the first continue-watching tile is clipped

**Mechanism, confirmed, and the fix is one line.** Round 2 fixed the identical fault in the
poster rows by moving the room a focused card grows into *inside* each poster
(`TvPosterRows.kt:299–304`: "No content padding… Both used to live here; both now live inside
the poster"). `TvContinueWatchingRow.kt:99` still carries
`contentPadding = PaddingValues(vertical = FOCUS_GROWTH)` — **vertical only**. So the first
tile has no horizontal room, and a `LazyRow` clips to its own bounds, which cuts its scaled
left edge flat.

Give the tile its horizontal growth the way `Poster` has it. The lesson is the one Round 2
already recorded and this bug proves was only half applied: a fix that lives in one row
implementation is not applied to the other by being written down near it.

### #013 — the previous stream's last frame survives the switch

Media3 keeps the last decoded frame on the surface until the next one arrives, so zapping
shows the old picture over the new audio's loading. Clear the surface when the media item
changes and hold a shutter until `onRenderedFirstFrame`, rather than relying on the buffering
spinner, which draws *over* whatever is already there instead of replacing it. The player's
error state has a branch (`#011`); its transitional state does not.

**Exit criterion.** Between two channels, no frame belonging to the previous stream is on
screen after the switch is requested. AC-PLAY-01's 3-second bound is unaffected — this changes
what is shown while waiting, not how long the wait is.

### #018 — the genre chips take a visible pause

**Mechanism, confirmed.** `SearchRepository.genreIndex()` reads the whole metadata cache
(`SELECT searchTitle, kind, genres, isMiss FROM title_metadata`) and splits and dedupes every
genre string in Kotlin, on demand, each time Advanced is opened. On a scanned 67k-title account
that is the entire cache walked for a list of about twenty words.

**Fix, in the order to try it.** The report asks for a progress bar under the search bar,
removed when the chips appear — do that, because a wait that is explained is a different
experience from a screen that appears broken. Then make the wait shorter: the index is derived
from a cache that only a scan changes, so it can be computed once and kept, or narrowed to a
`SELECT DISTINCT` that does the splitting in SQL. **Measure before choosing** — the coverage
figure quoted beside the chips comes from the same read, and a fix that leaves it stale trades a
slow screen for a lying one.

---

## What closes this round

| # | Closed when |
| :---- | :---- |
| #012 | The first tile of the continue row is whole, focused and unfocused, on the panel |
| #013 | No frame of the previous stream is visible after a switch |
| #014 | The favourites label fits at every supported title length, and history can be removed from the detail screen |
| #015 | A detail screen opens showing its title and artwork, unscrolled |
| #016 | AC-PROF-01 passes after a force-stop, on both apps, with a centred chooser and circular avatars |
| #017 | No `kind.name` reaches a composable in either app |
| #018 | A progress indicator covers the wait, and the wait is measured before and after |
| #019 | Films are Movies, Series has its heading, and no title is cropped when focused |
| #020 | The amendment is dated **and** back pops one step, with the episode cursor restored from history |
| #021 | The settings list measures zero movement for a second with the keyboard up, in the harness and on the device |
| #022 | The field is aligned with the column it is in |
| #023 | Two audio tracks are listed and switchable in both players, and AC-PLAY-04 is run |

**Then, and only then, `013` starts.** The device sweep in `006` gate 1 runs after both.
