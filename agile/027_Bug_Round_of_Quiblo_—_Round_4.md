**Bug Round of Quiblo — Round 4**

Eight faults reported from the sofa, against the shipped `0.20.0` on the Haier panel, by
using the app for an evening rather than by reading it.

**Created:** 2026-08-20, against commit `6740997` on `main`.
**Ships as:** defect work. Seven of the eight are faults against behaviour the app already
claims; the remaining one (#5) adds a switch to a screen that already has one, for a setting
`FREEZE.md` already describes rather than for a new capability. **No freeze amendment is
required.**

---

**Numbered within the round**, as `022` and `025` number theirs: the code says ``027`` #2 where
it is fixing the second of these, because a defect is a thing a round dealt with rather than an
entry in a register nobody keeps.

| # | Platform | Criterion | Description |
| :---- | :---- | :---- | :---- |
| #1 | TV | **AC-TV-03** | Back from a title returns to the top of the catalogue, not to the tile it was opened from — everywhere but the series screen |
| #2 | TV | AC-TV-06 | The remote loses the controls inside the player: nothing highlighted, arrows dead, only Back-then-Down recovers it |
| #3 | TV | AC-PLAY-02 | The timeline can be scrubbed and nothing on it moves: no handle, no mark a viewer can see travelling |
| #4 | TV | AC-TV-14 | Result titles are sliced along their baseline when the tile is focused |
| #5 | TV | AC-TV-14 | Advanced search cannot look at live channels; the only switch for it is two screens away in Settings |
| #6 | TV | AC-TV-14 | *Include hidden* is drawn as a genre chip, and lives behind the suggestions in the same scrolling strip |
| #7 | TV | AC-TV-14 | A genre chip clears the filter when pressed a second time |
| #8 | TV | — | *You may like* never appears, on a profile with ten part-watched films and ten favourites |

## What connects them

**Six of the eight are the television being *used* rather than demonstrated.** Every one of
them needs a viewer to do a second thing: come back from a film, scrub while the tracks are
still being enumerated, walk onto the genre they are already filtering by, star ten titles
over a fortnight. None is reachable by opening a screen and looking at it, which is why the
sweep has not caught any of them and why this round exists.

**Two of them are the same mistake in two places** — #4 and #6 both come of a screen
being measured while it was empty. A row that fits until a tile is focused, and a strip that
is reachable until a term produces suggestions.

---

## #1 — the app forgets where you were

**Mechanism.** The shell is removed from composition whenever anything is drawn over it —
`TvApp`'s `if (current == null)`. That is deliberate and stays: a catalogue left composed
behind a film is one still measuring, still fetching artwork, still holding bitmaps. What was
missing is that *nothing survived it*. Scroll positions are `rememberSaveable` and were being
discarded with the tree; focus is a node and cannot be saved at all.

**Fix.** Two halves, in `TvApp` and `TvPosterRows`:

- a `rememberSaveableStateHolder` around the shell, and a second one around each tab, so every
  saved value in the subtree — every `LazyListState` among them — is kept while the shell is
  away and handed back on return. Tab switches get the same treatment for the same reason.
- `TvReturnSignal`, a one-shot flag armed when a pop lands back on the shell and consumed by
  whichever list is on screen, which then asks for the tile it remembered. It is one-shot
  because a boolean would also be true while a viewer walks the tab bar, and content stealing
  focus off the bar mid-walk is a worse fault than the one being fixed.

Series already did this by hand for episodes (`#020`, `focusEpisodeId`) and is untouched.

**Measured by** `TvReturnsToTheSameTileTest`, which composes the whole journey — list, holder,
overlay, signal — and fails on the unfixed tree.

## #2 — the remote escapes inside the player

**Mechanism.** Two, reported as one symptom. The screen asked for focus once, when the
controls appeared, with `runCatching { requestFocus() }` — and `requestFocus()` *returns
false* rather than throwing when its node is not placed yet, so on a slow first layout the
controls came up with nothing on them. Worse, the transport is not a fixed row: the seek
buttons and the timeline exist only once a duration has arrived, the subtitle button once the
tracks are enumerated, the episode steps once the run is known. A button can therefore be
removed from under the remote a second into playback, and Compose drops focus when that
happens rather than handing it to a neighbour. Nothing asked for it back, which is why the
only way out was Back and then Down — the one gesture that made the screen ask again.

**Fix.** `TvPlayerControlsHost` owns the controls' focus: it insists rather than asks, and it
re-asks whenever nothing inside the controls holds focus after a frame. The frame matters —
when Compose *does* rehome focus itself that is the better outcome and is left alone. It
stands down entirely while the track panel or the end-of-episode offer is up, since both take
the remote on purpose.

**Measured by** `TvControlsFocusRepairTest`.

## #3 — the timeline has nothing that moves

**Mechanism.** The bar drew two rectangles and no handle, so a rewind changed the length of a
white line on a dark panel and nothing else. From three metres that reads as nothing at all,
which is why the scrub was reported as invisible rather than as broken — it worked.

**Fix.** A circular handle on the *mark* — where the viewer is aiming — rather than on the
playhead, which is what makes a held Left visibly travel before the seek commits half a second
later. It grows when the bar takes focus, and the bar's own track brightens with it. The
timeline now occupies a lane of fixed height with the 4dp bar centred in it, so the handle can
grow without anything below it moving: a control whose measured height changes under focus is
what defect #008 was.

## #4 — titles sliced when focused

**Mechanism.** A tile reserves 14dp above and below itself for the focus scale to grow into
(#003). That reserve was `padding` on a `Box` *outside* the focusable, so the rectangle the
tile reported to the list above it was the 253dp column and not the 281dp it actually draws
into. A vertical list scrolls a focused thing until *that* rectangle is on screen and then
stops — which on search results, where the field and the chips leave a row barely enough
height, left the last ten millimetres below the viewport. The poster fitted; the title under it
was cut along its baseline.

**Fix.** The same padding, inside the focusable. It is still constant and still outside
`graphicsLayer`, so a tile's measured bounds still never move — the invariant #008 turns on.

**Measured by** `TvSearchResultsFitTest`, whose arithmetic was itself wrong and is corrected
in the same change: it scaled the whole tile including the reserve, and it scaled a position
that Compose had already scaled. It now measures the label, and it fails on the unfixed tree.

## #5 — searching live channels

**Mechanism.** Advanced search drops live channels, because a genre filter means nothing to a
television channel. The only way to change that was `showLiveInSearch` in Settings, which
decides it for *every* search anyone ever makes.

**Fix.** A switch on the search screen. It starts as the setting and it is never written back:
a switch on a search screen answers the search it is on. `SearchViewModel` keeps it as a
nullable override so that "nobody has said" stays distinguishable from "somebody said no", and
Clear puts it back to the former rather than to the latter.

## #6 — a switch drawn as a chip, behind the suggestions

**Mechanism.** *Include hidden* was a `TvChip`, and a chip says "chosen" by filling in —
which is exactly how the selected genre looks. Two questions with one answer drawn for both.
It also sat in the same `LazyRow` as the genres, *after* the autocomplete suggestions, so on a
common term reaching it meant walking the remote past six film titles.

**Fix.** `TvToggle`, a real switch with a knob that slides, on the field's own row where
Advanced reveals it. The coverage sentence that used to occupy that space moves under the
field, above the genres it explains — which is where it reads, and is what the report asked
for.

## #7 — a genre chip that unchooses itself

**Mechanism.** `selectGenre` cleared when handed the genre already chosen. On a remote that is
a trap rather than a shortcut: the chip a viewer walks onto first is the one they are already
filtering by.

**Fix.** Choosing is choosing. Clear is at the head of the strip and is the only thing that
means "no genre". **Measured by** `SearchViewModelTest`.

## #8 — no suggestions after ten favourites

**Mechanism.** `Recommender` declines to answer below five distinct titles *and* three watched
most of the way through — the cold start `025` added deliberately, and rightly. But the second
half read the watch history and nothing else: the favourites table was consulted only to
*weight* a title that was in both. A viewer who had opened ten films, left each part-way and
starred ten titles had made twenty statements about their taste, none of which counted.

**Fix.** Two lines of judgement, in two places. The cold start now counts a title as
deliberate if it was watched through, favourited, *or* given a thumb up — starring is not
weaker evidence than reaching the sixty-percent mark, it is plainer. And
`RecommendationRepository` seeds favourites that were never played at all, dated by when they
were starred, so they decay and rank like any other occasion.

**Measured by** `RecommenderTest` and `RecommendationRepositoryTest`, both of which fail on
the unfixed tree.

---

## What is owed the panel

Everything here has run on the JVM only. `docs/TESTING-REQUIRED.md` §E carries the rows, and
each is written against the symptom rather than the mechanism — which is the rule `012`
learned twice and this round has no reason to relearn.
