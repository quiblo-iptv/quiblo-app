**Bug Fix Plan of Quiblo**

Plan for the ten bugs raised in [`001 Bug Reporting of Quiblo.md`](001%20Bug%20Reporting%20of%20Quiblo.md).

**Created:** 2026-08-04, against commit `681f91c`.

---

## Why this document exists

The ten reported bugs are not one kind of problem. Five are defects in code that exists
(#001, #003, #008, #009, #010). Five are screens the television frontend never got at all
(#002, #004, #005, #006, #007).

`FREEZE.md` Amendment 1 admitted the television into v1.0 on the argument that it is "the
same player with a different frontend, not a different product". That claim does not
currently hold: on a television a viewer cannot open a film, cannot see a series' episodes,
cannot reach any setting, and cannot pick a category in the Live list. This bug sheet is the
evidence. Closing it is what makes Amendment 1 true.

Two bugs are severe on their own terms. **#009** — the player treating films and series as
live channels — is architectural rather than cosmetic. **#010** — a mobile ANR while
scrolling — is a hang on the phone build that the acceptance sweep would have to fail. Both
live in code the two apps share.

## Decisions taken

| Question | Chosen |
|---|---|
| Sequencing | Fix what is broken first, then build what is missing |
| TV settings (#004) | Full parity, including fixing the IME focus trap |
| Scope | `FREEZE.md` **Amendment 4**; v1.0.0 does not tag until this work passes |

## Status

**Phase 1 is merged.** Two items did not survive contact and are recorded honestly below
rather than ticked off.

| Bug | Phase | State |
|---|---|---|
| #001 Loading time in Movies & Series | 1.1 + 1.2 | **Fixed**, pending on-device confirmation |
| #002 Live list has no category | 2.1 | **Fixed** — verified on the emulator |
| #003 Hover touching the category title | 1.4 | **Fixed** |
| #004 No settings screen | 2.2 | **Fixed** — all settings but theme; see below |
| #005 Movies missing info | 2.3 | **Fixed** — verified on the emulator |
| #006 Movies missing history row | 2.4 | **Built**, not yet seen with real history |
| #007 Series missing everything Movies has | 2.3 | **Fixed** |
| #008 Screen wobble while scrolling | 1.3 | **Still open** — see below |
| #009 The player is broken | 1.5 | **Fixed** — architecture and playback lifecycle |
| #010 App frozen (mobile) | 1.1 | **Fixed**, pending on-device confirmation |

### #004 — done, and what it dragged out with it

Done in full: playback settings, the channel-logo switch, the metadata key, category
hide/rename, and backup export/import over SAF — which also exercises **AC-TV-07** for the
first time.

The two open add-source bugs from `PLAN-TV.md` went with it, because the settings screen
needed the same fix:

- **The IME ate the D-pad.** `TvField` intercepted up and down itself, which works only
  with the keyboard *hidden*. With it up — the normal state on a television — the IME takes
  the D-pad, so the interception never ran and every field typed landed in the same box.
  Field movement now goes through the keyboard's own `ImeAction.Next`. `TvTextField` is
  shared rather than copied, so this cannot be fixed in one screen and forgotten in another.
- **A rejected save was silent.** The form closed whether or not the save was accepted.
  Both add functions now report acceptance and the form says what is missing.

Two things found while building it, both worth knowing:

- **The gear could never have been reached.** The bar left the right-key unconsumed at the
  last tab expecting focus to land on the icon; the focus search walks past it into the
  content instead, because the icon is inside the bar's own focusable. The gear is now a
  position along the bar rather than a focusable, matching the tabs.
- **`TvSourcesScreen` stole focus on first composition**, so selecting the Sources tab
  pulled the remote out of the bar entirely. Now only a later change claims focus.

**Theme mode and dynamic colour are deliberately not on the television.** It is always dark
by design and has no wallpaper for a dynamic palette, so both would be controls that change
nothing — the hollow-feature shape this project has already deleted once. Say so rather than
ship them; reversing this is a few lines if wanted.

### #005 / #006 / #007 — done, with one gap

The film screen, the enriched series screen and the continue-watching row are built and
merged. Presentation is shared between the two detail screens rather than written twice, so
they cannot drift into showing different facts about the same kind of title.

**#006 has not been seen populated.** Nothing has been watched on the emulator, so the row
is correctly empty and its filled state — artwork, progress bar, the season and episode
label — is untested. Watch a few minutes of anything and it should appear at the top of
Movies and Series.

## Round two — the TV QA pass (2026-08-05)

Eleven faults were reported after running the television build on the Haier with the real
account. Ten are fixed and verified on the device; the shake is still open.

**Three root causes explained nine of them, and all three are the same species — something
outliving or overflowing what was meant to contain it.**

1. **The player was never told it had finished.** Its ViewModel is activity-scoped, so
   leaving the player composition never cleared it. Audio kept playing behind the catalogue,
   and because the same call persists the resume point, nothing was ever written — which
   surfaced as three separate-looking bugs: no resume, no continue-watching row, and audio
   in the background.
2. **The detail ViewModels outlived their screens**, so reopening a film reused state loaded
   before anything had been watched.
3. **The detail screens did not fit the screen.** The panel is 960x540dp; after overscan
   there are 444dp of height and the cover alone was 390dp. Seasons sat below the fold and a
   multi-season series could not be navigated at all.

Also fixed: aspect ratio had no key bound to it while the controls announced the current
mode; category rows followed stream order rather than the provider's category order; the
first poster in each row was clipped; long detail titles drew their two lines on top of each
other; and the settings screen was reworked, with category editing behind a pencil rather
than a text field per row.

### The shake — still open, and three wrong answers so far

It is **horizontal** — moving left and right along a poster row. An early analysis measured
the vertical axis on a wrong assumption, and cost a shipped fix that addressed nothing.

Do not retry: (a) "a Live row grows when its guide arrives" — disproven, the fixed-size logo
dominates the row height; (b) "the vertical list needs content padding"; (c) "bring-into-view
fights the focus scale, so reserve the growth inside each item" — sound reasoning, wrong
axis.

The marquee was removed from poster titles while hunting it. That is **not** confirmed as a
fix and was kept on its own merits. The measurement that appeared to implicate it was
invalid: screen recording on this TV ends early, so the clip's idle tail still contained key
presses. Always check frames divided by frame rate against the requested duration before
concluding anything from a recording.

**Next, and decisive rather than theoretical:** make the poster focus scale instant. If the
shake stops it is the scale animating against the row's scroll-into-view; if it does not, it
is the scroll itself.
