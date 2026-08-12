**Increment Round of Quiblo — the catalogue a viewer actually uses**

Fourteen features and four enhancements from `docs/INC_AGILE.md`, planned but not started.
The fifteenth feature — one entry per title, whatever its quality or language — is large
enough to have its own document and lives in
[`014`](014_One_Entry_Per_Title_of_Quiblo_—_duplicates,_qualities_and_languages.md).

**Created:** 2026-08-10, against commit `b7bcba4` on `main`.
**Ships as:** `1.1.0` and later. **Nothing here enters `1.0.0`**, which is why no `FREEZE.md`
amendment accompanies this document — the freeze governs `1.0.0`, and each item that is
later pulled into a release needs its own dated amendment at that point.

**Starts after [`012`](012_Bug_Round_of_Quiblo_—_Round_3.md) closes, in full.**
Every item below lands on a screen that round is repairing.

---

| Item | Platform | Cost | Depends on | Description |
| :---- | :---- | :---- | :---- | :---- |
| INC-F0 | Both | S | #016 | ~~Avatars for profiles~~ — **built 2026-08-12**, schema 13, both choosers, phone picker. The television picker and the control beside the gear are `STOPPERS.md` **S11** |
| INC-F1 | Both | M | — | Autocomplete in search, from the local catalogue |
| INC-F2 | Both | L | The scan | Suggestions from what has been watched |
| INC-F3 | Both | S | #014 | Long-press a history entry to remove it — **phone half built 2026-08-12**. The television half is `STOPPERS.md` **S12**: that app has no dialog anywhere, and a one-action menu on a remote is a pattern it does not yet have |
| INC-F4 | Both | M | — | Long-press a channel for its full guide, on a timeline |
| INC-F6 | Both | M | #015 | Merge seasons into one list, and reverse the order — remembered per profile |
| INC-F7 | Both | S | A key | Refresh this title's information from the metadata service |
| INC-F8 | Both | S | — | ~~Category editing scrolls inside Settings~~ — **built 2026-08-12**, and it was a removal rather than an addition |
| INC-F9 | Both | M | — | ~~A section whose text is right-to-left is laid out right-to-left~~ — **built 2026-08-12**. First-strong detector in `:core:common`, `AutoDirection` in `:feature:designsystem`, applied to the title and plot on all four detail screens |
| INC-F10 | Both | L | — | Subtitle files: the provider's, or one the viewer picks |
| INC-F11 | Both | M | INC-F10 | Subtitle appearance, set from the player |
| INC-F12 | Both | S | — | **Audio track selection. Not new scope — see below** |
| INC-F14 | Both | M | INC-F9 | ~~Filter the catalogue to one language~~ — **built 2026-08-12** as a subtraction, not a selection: hide titles written in scripts the viewer does not read. App-wide, never applied to favourites |
| INC-E4 | Both | S | — | ~~The corner radius~~ — **built 2026-08-12**. There was no scale to raise; there was one to write |
| INC-E1…E3 | TV | S | The panel | The search screen's shape. **Not built** — each is a question about how a screen reads from three metres. `STOPPERS.md` **S10** |

**Cost is S/M/L against this codebase**, not against a calendar: S is a screen and its test,
M is a screen plus a repository or a schema addition, L is either a new subsystem or a change
that touches every catalogue screen.

## INC-F12 is a defect, not a feature

`Media3PlayerController` already exposes `audioTracks`, `textTracks`, `selectAudioTrack` and
`selectTextTrack`, and has since the player was built. What is missing is the UI: the phone
cycles *text* tracks from one button (`PlayerScreen.kt:293`) and offers nothing for audio, and
the television player offers neither.

**AC-PLAY-04 says a viewer can switch between multiple audio or subtitle tracks.** It is not
run, and it does not pass — the capability is in the engine and unreachable from the remote,
which is the "hollow feature" shape inverted: working code with no way in. That makes it
`1.0.0` work rather than `1.1.0` work, and it is recorded as **#023** in `012` rather than
here. The row is kept in the table above so the intake item is not lost.

The genuinely new part of the intake's request — reading tracks out of a container that
declares no language for them, and labelling them usefully — stays in this document, under
INC-F10.

---

## The profile and history items

### INC-F0 — avatars, and a way to change who is watching

An avatar chosen when a profile is created, shown on the chooser and beside the settings gear;
selecting it opens the chooser again.

**Build it on #016's screen, not beside it.** That bug centres the chooser and makes its
avatars circular; this fills them. Doing them in either order means laying the screen out twice.

**Choose the source of the picture deliberately.** A picked image means SAF, a copy inside app
storage, a size cap, and a file to clean up when a profile is deleted — for a 40dp circle. A
generated avatar — an initial on a colour derived from the name — costs a composable, is
correct at every density, has nothing to delete, and cannot ship a viewer's photo into a backup
file. **Recommendation: generated, with a small fixed set of illustrated faces to pick from.**
Revisit only if somebody asks for their own picture.

**Where it goes on the television.** The gear is a position along the tab bar rather than a
focusable of its own — that is what made it reachable at all. The avatar is a second position
on the same bar, on the same rule. It is not a floating control in the corner.

### INC-F3 — remove one thing from continue-watching

Long-press a tile on the continue-watching row; a menu opens with one action, **Remove from
watch history**. On the television that is the D-pad's centre held down; on the phone, a long
press.

`012` #014 adds the same action to the detail screen. **One repository call, two entry points.**
Removing an entry must also remove its resume position, or the row reappears the next time the
title is opened, which reads as the feature not working.

## The search screen

### INC-F1 — autocomplete from the local catalogue

Suggestions under the field as a term is typed, drawn from cached live, film and series titles.

**The constraint is AC-TV-14**: a term typed on an on-screen keyboard must not put a query to
the database per keystroke. Search already debounces and caps for that reason, and autocomplete
is the same query with a smaller limit — so it goes through the same path with the same
debounce, not a second path beside it. A suggestion list refreshing per keystroke is precisely
the defect that criterion exists to prevent.

**Suggest titles, not rows.** Four qualities of one film are one suggestion. That is `014`'s
cleaned title doing double duty, and it is the reason this item is cheap once `014` exists — but
it does not wait for it: `SearchRepository` already cleans titles for the metadata cache.

### INC-E1 to INC-E3 — the shape of the screen

- **E1** — Advanced moves to the right of the field, and the two share a sensible width rather
  than the field filling the panel. On the television, remember that a text field keeps left and
  right for the cursor: a control *beside* the field can only be reached by going up or down to
  it first, and this is exactly why the genre chips were put underneath. Put Advanced on the
  same focus row as the field only if there is a way in that a remote can find.
- **E2** — the logo, then the word, then the field, stacked above the search area. This is the
  resting shape that already animates upward when a question is asked; the logo is one more
  element in that animation, not a second layout.
- **E3** — a glow that travels along the edge of the field. Cheap in Compose with an animated
  brush; **check it on the panel before keeping it**, because a television is watched from three
  metres and a moving highlight competes with the focus indicator, which is the one moving thing
  on screen a viewer must never lose track of (AC-TV-02).

### INC-E4 — the corner radius

The current radius comes from the Material 3 shape scale. The question in the intake is worth
answering once, in the theme, rather than per component: raise the scale's medium and large
values and every card, dialog and field follows. Continuous ("squircle") corners are not in the
Material shape API — they are a custom `Shape`, which is a small amount of maths and then every
surface in both apps drawn by hand. **Recommendation: increase the radius on the existing scale.**
Revisit the custom shape only if the increased radius is not the thing that was actually wanted.

## The detail screens

### INC-F6 — one list of seasons, and an order that is remembered

Two controls on the series detail screen itself, not behind a menu: **merge seasons** into one
continuous list, and **reverse the order**. Both are remembered per series, per profile.

**This is the item on this page with the clearest reason to exist.** A series with a thousand
episodes is unusable if the newest is a thousand rows away, and a preference that is not
remembered is a preference re-entered every evening.

**Store it where profiles already are.** A small table keyed by profile and series, holding two
booleans. It is not a global setting — one household member reading a long-running series from
the end does not decide how anyone else reads it.

**Merged numbering has to stay honest.** Merging seasons means episode 3 of season 2 and episode
3 of season 5 sit in one list; the label has to keep the season or the list becomes ambiguous at
exactly the point it is most useful.

### INC-F7 — refresh this title from the metadata service

A button on the film and series detail screens that re-asks the metadata service for this one
title and overwrites the cached row. **Shown only when a key is configured** — AC-META-01 says
that with no key nothing here issues a request, and a button that cannot work is a hollow
feature.

Two rules carried over from the scan, both of which were bought with defects (`005`):

- **A refusal is never cached.** A rate limit or a rejected key leaves the existing row alone
  and says so on screen.
- **It goes through the same rate limiter as everything else.** A button a viewer can press
  repeatedly is a request source like any other.

The reason the intake asks for it is stale or missing artwork, so the button's success state has
to be visible: the poster changes, or a message says the service knows nothing about this title.

## Settings

### INC-F8 — category editing scrolls inside Settings

Category hide and rename currently opens as its own thing; the intake asks for a scrollable
container within the settings screen. On a television this is a focus problem before it is a
layout problem — a scrollable region inside a scrollable screen has to hand focus back at its
edges, or the remote gets stuck in it. Nested scrolling with a clear exit at the top and bottom,
tested with the D-pad, or it is worse than what exists.

## Language and text direction

### INC-F9 — right-to-left where the text is right-to-left

The app's own direction stays whatever the system says. What changes is per section: a title,
plot or description whose characters are Arabic, Hebrew, Persian or Urdu is laid out and aligned
right-to-left, inside an otherwise left-to-right screen.

**This is a detector plus a `CompositionLocalProvider`, and the detector belongs in `:core`.**
Read the first strong directional character of the string — the Unicode bidirectional algorithm's
own rule (`Character.getDirectionality`, or `TextUtils.getLayoutDirectionFromLocale` for a
locale) — and provide `LocalLayoutDirection` for that section only. Do not guess from the
presence of any Arabic character: a title like "Dune 2 مترجم" is a left-to-right title with an
Arabic word in it, and the first-strong rule gets that right where a contains-check does not.

AC-NFR-08 already requires full RTL support; this is finer-grained than that criterion and does
not replace it.

### INC-F14 — filter the catalogue to one language

The intake asks the right question: *is this possible when we do not know what language anything
is in?* **Partly, and the honest answer decides the design.**

Three signals, in descending order of reliability:

1. **The script of the title.** A title written in Arabic script is Arabic content. This is the
   same first-strong detector as INC-F9 and it is close to certain when it fires.
2. **The metadata service's `original_language`.** Available for films and series that have been
   described — which after a scan is most of them, and before one is almost none. Live channels
   have no metadata at all.
3. **The provider's category name.** Panels routinely file content under "AR | Movies" or
   "EN — Series". Unreliable across providers, useful within one, and free.

**What cannot be solved:** an Arabic film titled in Latin script — a transliteration, or an
international title — is indistinguishable from an English film by signal 1, and signal 2 only
answers if that title has been described. So the filter is **best-effort and must say so**.

**Which makes the design a subtraction, not a selection.** Do not offer "show only English",
which promises completeness the data cannot support and hides titles the viewer wanted. Offer
**"hide titles written in scripts I do not read"** — a set of scripts, defaulting to none hidden
— which is a promise the first-strong detector can keep exactly. Signals 2 and 3 refine it where
they are available, and the setting is app-wide rather than per profile so that it cannot silently
differ between two people looking at the same catalogue.

## Subtitles

### INC-F10 — subtitle files

Sidecar subtitles, from two sources: the ones a panel supplies with a film, and one the viewer
picks from the device via SAF. Media3 takes both as a `SubtitleConfiguration` on the media item,
so the engine work is small and the work that is not small is everything around it — MIME
sniffing for SRT and VTT, encodings that are not UTF-8 (an Arabic `.srt` in windows-1256 is
common and renders as mojibake if assumed), and a picked file that must be remembered against
the title rather than the session.

`selectTextTrack` then treats them like any other text track, which is the reason this is worth
doing on the existing seam rather than beside it.

### INC-F11 — subtitle appearance, from the player

Size, colour, background colour and opacity, changed from a menu inside the player where the
effect is visible, and persisted. Media3 renders through `CaptionStyleCompat`, so this is a
settings object the player applies rather than a rendering feature to build.

**One thing to decide first:** Android has a system-wide caption style that the platform expects
apps to honour. Overriding it silently is wrong; ignoring the viewer's request is also wrong.
Start from the system style and let the in-app menu override it, with a "match system" option
that returns to it.

## The guide

### INC-F4 — a channel's full programme guide

Long-press a channel in the Live list to see its programme catalogue on a timeline aligned to
the television's clock.

**Three things this needs that do not exist**, and they are the reason this is M rather than S:

- **More than now and next.** `get_short_epg` supplies a small window; the full listing is
  `get_simple_data_table`. Another call to the user's panel, so it is made on the long-press and
  never on a scroll — AC-TV-05's whole point.
- **A timeline, which is a new composable.** Programmes are drawn against elapsed time, with a
  now-marker. On a television it must be navigable by D-pad, which means the marker cannot be
  the only way to know where "now" is.
- **Time zones.** AC-EPG-03 already requires programme times to render in the device's zone, and
  a timeline makes an offset error visible in a way a now/next label does not.

Nothing here changes the EPG storage layer — it is source-agnostic by `FREEZE.md` §4.3, and a
fuller listing from Xtream is more rows of the same shape, not a schema change.

## Suggestions from what has been watched

### INC-F2 — what the intake calls "a learning algorithm/daemon"

The intake asks what this would be. The short answer: **not machine learning, and not a daemon.**

**Why collaborative filtering — the thing that powers Netflix's row — is impossible here.** It
works by comparing one viewer's history against millions of others. That requires a server
collecting what everybody watched. `FREEZE.md` §2 rules out a backend, accounts and telemetry,
and §4.5 says the app never phones home. This is a locked decision, not an omission, and it
removes the entire class.

**Why an on-device model is the wrong tool.** A trained model would have to ship inside a 25 MB
APK budget (AC-NFR-02) and would be learning from one household's few hundred watch events. At
that data scale a model cannot outperform a scoring function, and it costs the ability to say
*why* a title was suggested.

**What actually works, and what to build: content-based scoring.** Every described title in the
cache carries genres, and the scan fills that cache for the whole catalogue. So:

1. Build a taste vector per profile from watch history — genres of what was watched, weighted by
   how much of each title was actually watched, decayed by age so last month matters less than
   last week.
2. Score every undescribed-as-watched title by how well its genres match, penalising the genres
   already saturating the top of the list so the row is not five thrillers.
3. Show the top handful in a **"Because you watched X"** row, naming the title that caused it.

This is arithmetic over rows already in the database. It runs when the row is composed, on a
background dispatcher, and needs nothing running in the background — which answers the "daemon"
half: a daemon would be a process burning battery to precompute something that takes milliseconds
on demand.

**Three constraints it inherits:**

- **Per profile.** Watch history is already per profile; a shared suggestion row would leak one
  person's viewing to another, which is the failure profiles exist to prevent (`AC-PROF-02`).
- **It says why.** "Because you watched X" is both a better row and the cheap consequence of the
  method — a model cannot say that, and this can.
- **It is honest when it knows nothing.** With no key, an unscanned catalogue, or a fresh profile,
  there are no genres and there is no row. Not an empty row with a spinner: no row.

**Recommendation: build steps 1–3 and stop there.** Revisit only with a concrete complaint about
the suggestions, and revisit it as a better scoring function rather than as a model.

## The README

### INC-F5 — get it on GitHub — **closed 2026-08-12**

**It was already built, and reading the tree is what found that.** `README.md:41`–`63` carries
the release badge, the Download link in the header rail, the Install section naming both APKs
and what each installs on, the note that the phone build never appears in a television launcher,
and the checksum command. That is every part of the intake's request and both parts this document
added to it.

What is left is the wiki half of `006` gate 3, which is a wiki task and not this one. The rule
this item was written around still stands — write the download once and link to it — and the
README is now the copy that exists.

---

## Order of execution

Bugs first — `012`, all eleven, plus #023. Then this document, in four passes, each of which
leaves the app releasable:

| Pass | Items | Why together |
| :---- | :---- | :---- |
| 1 | INC-F0, INC-F3, INC-F8, INC-F5, INC-E1…E4 | **Built 2026-08-12, apart from INC-E1…E3.** Written out in [`015`](015_Pass_One_of_Quiblo_—_the_screens_012_leaves_open.md), which found four of the eight were not the work this table assumed — and building it found a fifth. What each item owes a device is in [`TESTING-REQUIRED.md`](../docs/TESTING-REQUIRED.md) §A |
| 2 | INC-F6, INC-F7, INC-F1 | The detail screens and search, each one repository call deep |
| 3 | INC-F9, INC-F14, INC-F10, INC-F11 | Text and language, in that order: F9's detector is F14's, and F10 has to exist before F11 can style it |
| 4 | INC-F4, INC-F2 | The two that need a new composable and a new subsystem. Last because they are the two most likely to be cut |

`014` runs beside pass 3 or after pass 4 — it shares the title cleaner with INC-F1 and the
language signals with INC-F14, and it is the only item here that changes what a catalogue screen
*contains* rather than how it looks.

## What this document does not decide

- **Whether any of this reaches `1.0.0`.** The default is no. `006` is the road to that release
  and nothing here is on it.
- **Acceptance criteria.** Each item gets its own when it is built, in `ACCEPTANCE.md`, under the
  release that carries it. Writing eighteen criteria for work that has not started produces a
  sweep list nobody has run, which is the state `006` gate 5 exists to clear rather than to grow.
