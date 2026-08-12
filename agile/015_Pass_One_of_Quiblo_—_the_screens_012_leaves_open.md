**Pass One of Quiblo — the screens `012` leaves open**

Pass 1 of [`013`](013_Increment_Round_of_Quiblo_—_the_catalogue_a_viewer_actually_uses.md),
written out item by item: INC-F0, INC-F3, INC-F8, INC-F5 and INC-E1…E4.

**Created:** 2026-08-12, against the tree on `main`.
**Ships as:** `1.1.0`. Nothing here enters `1.0.0`, so no `FREEZE.md` amendment accompanies it —
same rule as `013`, which this document is a pass of rather than a successor to.

~~**Written while the acceptance sweep is open, and deliberately not built.**~~

**Built 2026-08-12 — steps 0 to 4, and the chooser half of step 6.** The reasoning for holding it did not
survive being written down: it says the sweep runs against a *published artefact*, and it does —
`STOPPERS.md` S1 step 1 sends the tester to `v0.2.7`. A moving `main` does not sweep the tree
twice, because the sweep is pinned to a version rather than to a branch.

**One thing this got wrong and it is worth keeping.** The first version of this paragraph added
that publishing a release mid-sweep *would* sweep the tree twice, and that nothing here did
that. The release lane publishes on every merge to `main`, so this work published `0.5.0` —
as did the four merges before it, `0.2.8` through `0.4.0`, every one of them after `v0.2.7` and
during this sweep. The lane is working as designed. What protects the sweep is S1 naming the
version to install; the risk is a tester reaching for the latest release instead. What is left unbuilt
is `STOPPERS.md` **S10**, **S11** and **S12**, and none of them is held back by the sweep — all
three are held back by needing a panel to judge them on.

What each item now owes a device is in [`TESTING-REQUIRED.md`](../docs/TESTING-REQUIRED.md) §A.

---

## What reading the tree changed

Four of the eight items are not what the intake and `013` assume they are. This is the whole
value of writing the pass out before building it.

| Item | `013` assumed | The tree says |
| :---- | :---- | :---- |
| INC-F5 | A README section to write | **Already built.** `README.md:41`–`63` — badge, download link, which APK is which, and `sha256sum -c`. Close it |
| INC-F8 | A scrollable container to add | **Already built, in the shape `013` warns against.** The work is to remove it |
| INC-F3 | A repository call to add | `WatchHistoryRepository.removeFromHistory` exists and already takes the resume point with it. UI only |
| INC-E4 | A shape scale to raise | Neither theme declares `Shapes` at all. There is no project scale to raise — there is one to write |

Pass 1 is therefore **five items of work, not eight**, and one of the five is a deletion.

**Building it found a fifth thing this table did not predict**, and it is recorded under INC-F0
below: `012` #016 had already rebuilt both chooser screens and left the television's tile a
round slot with a comment saying a picture goes here. The half of INC-F0 this document schedules
last was mostly waiting for the half it schedules first.

---

## INC-F5 — get it on GitHub

**Done. Close the row.**

`README.md:41`–`63` carries the release badge (`:13`), the Download link in the header rail
(`:19`), the Install section naming both APKs and what each installs on, the note that the
phone build never appears in a television launcher, and the checksum command. That is every
part of the intake's request and both parts `013` added to it.

**What is left is the wiki half of `006` gate 3**, which is a wiki task and not this one.
`013`'s rule stands — write the download once and link to it — and the README is now the copy
that exists. The wiki page links here rather than restating it.

## INC-F8 — category editing scrolls inside Settings

> **Rejected on the panel, 2026-08-12, and it is a reversal rather than a fault.** The request is
> the opposite of what is argued below: *"it should be fixed length and the items inside is
> scrollable, same on tv."* A fixed-height container with the list scrolling inside it — which is
> the shape this section describes and removes.
>
> **What has to be answered rather than repeated.** The argument below is that a bounded scroller
> inside a scroller swallows drags ambiguously, and that argument is not wrong — but it was made
> against a **320dp** box showing four categories out of several hundred, and the complaint it was
> answering may have been the height rather than the nesting. A taller fixed box with a clear edge
> is a third option neither version considered, and the television needs the same treatment now
> either way.
>
> Build it as asked. Keep the Robolectric test and invert what it asserts.


**The phone already does exactly what the intake asked for, and it is the wrong shape.**

`CategorySettingsCard.kt:114` puts a `LazyColumn` capped at `heightIn(max = 320.dp)`
(`:232`) inside the settings screen, which is itself a `verticalScroll` column
(`SettingsScreen.kt:112`). A bounded scroller inside a scroller is a drag that either moves the
inner list or the outer screen and never obviously which, and at 320dp the inner list shows
about four categories out of an Xtream account's several hundred.

**The television already has the answer.** `TvSettingsScreen.kt:294`–`308` emits the kind
selector and then every category as items of the settings `LazyColumn` itself. One scroller,
one focus order, no edge to hand focus back at — which is the trap `013` predicts for this item
and the television avoids by construction rather than by care.

**So the phone changes shape to match the television**, and the item stops being an addition:

- `SettingsScreen` becomes a `LazyColumn`, its existing cards emitted as `item { }`.
- The category rows become `items(categories)` at the top level. No inner list, no height cap.
- `CategorySettingsCard` keeps the heading, the kind selector and the rename dialog, and hands
  its rows out rather than drawing them into a box of its own.

**Test:** with 300 categories, one drag from the bottom of the screen reaches the last category
*and* one drag reaches the section above the categories. That is the assertion, and the current
build fails the second half of it.

**Cost:** S as `013` says, but it is a settings-screen rewrite rather than a card edit, so it
goes with a Robolectric scroll test rather than on its own.

## INC-F3 — remove one thing from continue-watching

**The data layer is finished.** `WatchHistoryRepository.removeFromHistory(stableKey)` deletes
from `resume_positions` scoped to the active profile, and `removeSeriesFromHistory` does the
same for every episode of a series. `013` warns that removing an entry must also remove its
resume position or the row comes back — that cannot happen here, because the history row *is*
the resume position. One table, one delete.

What is missing is two ways in.

**Phone** — `ContinueWatchingRow.kt:112` uses `clickable`. It becomes `combinedClickable` with
`onLongClick`, opening a one-action menu: **Remove from watch history**. One action is not a
menu worth a bottom sheet; a `DropdownMenu` anchored on the tile is the whole of it.

**Television** — `TvContinueWatchingRow.kt:148` also uses `clickable`, and its comment is
load-bearing: the `clickable` sits *outside* the `graphicsLayer` so the tile's focusable is not
inside an animating scale. That is `012`'s wobble lesson (#021's shape), and long-press must be
added on the same side of that line. `combinedClickable` keeps the focusable where it is; a
`Modifier.onKeyEvent` watching for a held centre key does not, and would put a second input path
on a tile that already has one.

**Both entry points call the same repository method** as `012` #014's detail-screen control.
Whichever of the three is built first owns the string.

**Depends on `012` #014**, which needs the remote. The phone half does not, and that split is
the reason this item is buildable before the television is.

## INC-F0 — avatars, and a way to change who is watching

The largest item in the pass, and the only one that touches the schema.

### The picture

**Generated, from a small fixed set**, as `013` recommends. Restating it here because it is the
decision that makes the rest of the item small: no SAF, no copy in app storage, no size cap, no
file to delete when a profile is deleted, and nothing of a viewer's that can end up inside an
export file. `AC-DATA` says a backup is portable; a backup carrying somebody's photograph is a
different promise than the one this project made.

**A fixed set of illustrated faces, plus an initial-on-a-colour fallback**, with the colour
derived from the profile name so it is stable across devices and across a restore.

### The schema

`ProfileEntity` (`Entities.kt:112`) gains one nullable column, and the database goes to **12**:

```
MIGRATION_11_12: ALTER TABLE `profiles` ADD COLUMN `avatar` TEXT
```

Nullable rather than defaulted, because null means "this profile predates avatars" and the
fallback draws the initial — a default value would claim every existing profile chose face #1.
`Profile` (`core/model`) gains the matching field.

**`AC-PROF-05` is watching this.** That criterion is upgrade-in-place over a build that has
profiles, and it currently has no published release to upgrade *from* — see
`docs/STOPPERS.md`. Adding a migration under an unrun upgrade criterion means the first person
to run it is testing two migrations at once. **The migration test goes in with the migration**,
not with the screen.

### The chooser

Both chooser screens fill their circles with the avatar. **`012` #016 is rewriting both of
them** — centring the phone one, making the television's avatars circular — so this lands on
that screen and not beside it. Laying either out twice is the cost `013` names and it is real:
the phone chooser today is a `LazyColumn` of `ListItem` cards (`ProfileGate.kt:96`–`110`) with
no avatar surface at all, and the television's is already a centred `LazyRow`
(`TvProfileScreen.kt:168`) of clipped circles (`:309`) with `Icons.Filled.Person` in them
(`:178`). **They are not the same amount
of work and they are not the same change.**

### The control beside the gear

**Television:** a second position on the tab bar, on the same rule as the gear — `TvApp.kt:447`
explains why the gear is a position along the bar and not a focusable of its own, and an avatar
in the corner would reintroduce exactly the unreachable control that comment records. It goes at
`lastIndex + 2`, after the gear, and selecting it returns to `TvProfileScreen`.

**Phone:** beside the settings icon at `QuibloApp.kt:83`. An `IconButton` with the avatar,
opening the chooser.

**Both:** the chooser is the same screen the gate shows, reached a second way. `ProfileGate`
switches on `active == null`; re-choosing means clearing the active profile and letting the gate
draw, not navigating to a copy of the chooser.

**Depends on `012` #016**, which needs the remote. The schema, the model, the generated-avatar
composable and its migration test do not, and are the part of this item that can be finished
first.

## INC-E1, E2, E3 — the search screen

All three are the television's search screen. The phone has no Advanced control and no resting
search state — its search is a field that takes over the filter row (`BrowseScreen.kt:414`) —
so these do not cross over, and should not be made to.

### E1 — Advanced beside the field

**`TvSearchScreen.kt:174` already answers this, and the answer is no as asked.** The chips sit
under the field because a text field keeps left and right for the cursor: a control beside the
field cannot be reached by a remote moving horizontally, only by going up or down first.
Advanced is a chip in that strip today (`:292`).

`013`'s condition is the one to hold to — put it beside the field *only if a remote can find a
way in*. There is one shape that qualifies: Advanced beside the field, on the field's own focus
row, reachable by **down from the field then left**, with the chip strip below it unchanged. If
that costs more than one focus rule to explain, it is not worth the row it saves.

**Recommendation: build E2 first and re-ask E1 afterwards.** E2 changes what the resting screen
looks like, and half of what E1 is asking for is that the field not fill the panel.

### E2 — the logo above the field

The logo, then the word, then the field. `TvSearchScreen.kt:102` already has the state this
needs — `isResting` — and the header already animates upward when a question is asked
(`:77`–`78`). **The logo is one more element in that existing animation, not a second layout**,
and it is the reason this item is S: an element that participates in a transition that exists.

The rest of the panel is the answer, so the logo is gone the moment there is one.

### E3 — the travelling glow

An animated brush along the field's border. Cheap to write and **the one item in this pass that
can only be judged on the panel**: a television is watched from three metres, and a moving
highlight beside the focus indicator competes with the single moving thing on screen a viewer
must never lose track of (`AC-TV-02`).

**Build it behind a constant, look at it on the panel, and keep it or delete it that evening.**
Not a setting — a viewer should not be asked to fix a decision we did not make.

## INC-E4 — the corner radius

**There is no project shape scale.** `Theme.kt` and `QuibloTvTheme.kt` declare colours and
typography and pass no `shapes` to `MaterialTheme`, so every card, dialog, chip and field in
both apps is drawing Material 3's defaults — 12dp medium, 16dp large.

So the item is not "raise the scale", it is **write one, in both themes, and raise it there**:

```kotlin
Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)
```

Two rules make this worth doing once rather than per component:

- **The same scale in both apps.** A phone and a television that round differently are two
  products. The values live in one place both themes read, not copied into each.
- **Continuous corners stay rejected**, as `013` decided — they are a custom `Shape` and then
  every surface in both apps drawn by hand. Revisit only if a raised radius turns out not to be
  what was wanted.

**This is the cheapest visible change in the pass and it touches every screen**, which is also
why it does not go in during a sweep: it changes the look of every criterion a tester is
photographing.

---

## Order of execution

Pass 1 splits along one line — what needs the remote and what does not. `012` closes first
either way, but this ordering means the parts that never needed hardware are finished before
the hardware arrives.

| Step | Items | Needs the remote | State |
| :---- | :---- | :---- | :---- |
| 0 | Close INC-F5. Delete the row from `013`'s table | No | **Done 2026-08-12** |
| 1 | INC-E4 — the shape scale, both themes | No | **Built.** In a new `:feature:designsystem`, because both themes must read one copy |
| 2 | INC-F8 — the phone settings screen becomes one scroller | No | **Built**, with the Robolectric reachability test |
| 3 | INC-F0 data half — migration, model field, generated avatar, migration test | No | **Built** — as **12→13**, not 11→12; see below |
| 4 | INC-F3 phone half — long-press on the continue-watching tile | No | **Built** |
| 5 | INC-E2, then re-ask INC-E1 | The panel, to look at | **Not built** — `STOPPERS.md` S10 |
| 6 | INC-F0 screen half — both choosers, both entry points | ~~Yes, on `012` #016~~ | **Choosers built** — #016 had already rebuilt them. Entry points and the television picker are `STOPPERS.md` S11 |
| 7 | INC-F3 television half | Yes, and not for the reason given | **Not built** — `STOPPERS.md` **S12**. The dependency on `012` #014 was real but minor; what stops it is that `app-tv` contains no dialog at all |
| 8 | INC-E3 — build, look, keep or delete | Yes | **Not built** — `STOPPERS.md` S10 |

**The "needs the remote" column was wrong on both of its qualified yeses, in opposite
directions.** Step 6 was marked as depending on `012` #016, which turned out to be *already built
and merely unswept* — a dependency on unfinished work and a dependency on unverified work look
identical in a table and are not the same thing. Step 7 was marked as depending on `012` #014,
which was also already built, so by the same reasoning it should have fallen out easily; it did
not, and the real obstacle was never in this column at all. See **S12**.

**Built 2026-08-12 — steps 0 to 4, and the choosers from step 6.**

### The migration number moved

This document says `MIGRATION_11_12` for the avatar column. It is **12→13**. `016`'s #024 took
11→12 the same day, because it is a `1.0.0` defect and the avatars are `1.1.0`, so the `1.0.0`
item goes first in the schema as it does in the release.

That collision is the sort of thing two documents written against the same tree on the same day
will produce, and the cheap defence turned out to be the one that was missing entirely: **nothing
in this repository had ever run a migration.** Eleven were declared and every one had been signed
off by reading it. `core/database` has a test source set now, and `MigrationTest` walks 1 through
13 against the exported schemas. It is the second thing #024 paid for.

## What this document does not decide

- **When any of it is built.** `012` closes first, in full, and the sweep runs on a tree that is
  not moving underneath it. This is a plan, not a start.
- **Acceptance criteria.** Per `013`, each item gets its own in `ACCEPTANCE.md` under the release
  that carries it. Writing them now grows the sweep list that `006` gate 5 exists to clear.
- **Whether E1 survives.** It is the one item here whose answer is "probably not as asked", and
  E2 is what settles it.
