**Friction on the Television of Quiblo — the keyboard, the box, the filter and the search**

Four reports, and none of them is about something being broken. Every one is about the app
charging a viewer for something they did not ask for: a keyboard for walking past a field, two
hundred presses for walking past a box, a filter that reads one letter of a title and stops, and
a search that answers half the question and hides the other half behind a category nobody can
see.

**Created:** 2026-08-15, against `e6090ec` on `main`.
**Ships as:** `1.0.0` work. All three parts are `fix:`, so a merge to `main` cuts a release;
neither branch is merged until that is asked for.

**Branches:** `fix/tv-press-to-type-and-category-box`, `fix/hidden-means-hidden`,
`fix/advanced-search-kinds-and-live`.

The feature that arrived in the same request — the For You tab — is
[`020`](020_For_You_of_Quiblo.md). It is separate because it is a feature and these are not, and
because these three leave the app better whether or not it is built.

---

## Part A — the television asks before it types

### The reports

> each text input box don't popup the keyboard until the user press

> on category edit container the user should press to enter it because it benefits nothing it
> scrolls with me so i have to scroll loooong to get to end, also add some padding

### What the code did

Every field on the television goes through one composable, `TvTextField`, and it was a
`BasicTextField` — which asks for the IME the moment it is focused. On a phone that is right,
because focus arrives by a tap. On a television focus is something a viewer passes *through*:
moving down the settings list to reach the backup rows opened and dismissed a keyboard at the
metadata key on the way, and each dismissal cost a Back press.

Nothing in the tree was already trying to prevent this — there is no `showSoftInputOnFocus`
anywhere in the repository — so this is a behaviour that was never chosen, only inherited.

The category editor had the matching problem one level up. It was an inner scroller marked
`focusGroup()`, which hands focus back at its edges and is the right thing for a region a viewer
wants to be in. It is the wrong thing for a region they are walking past, and passing it cost one
press per category.

### What was built

| Layer | Change |
| :---- | :---- |
| `app-tv` | `TvTextField` has two stages: a resting box that is focusable and inert, and an editor composed on a centre press. Both draw the same `FieldBox`, so the swap changes no measured bound |
| `app-tv` | Every way out of the editor — Done, Next, Back, up, down, the search field's Right — lands focus back on the field first and moves it afterwards. Moving focus from a node that has just left the composition moves it from nowhere |
| `app-tv` | `CategoryBox`: shut by default with a summary line, opened by a press, closed by Back or by walking off either end. `16dp`/`12dp` inside the border, and `6dp` between rows instead of `2dp` |
| `app-tv` | Five strings for the two states and the empty case |

**Two things this found on the way.**

**A `clickable` is already a focus target, and putting `focusable()` in front of it makes a
second one.** The remote then focuses the outer of the two while the key handling sits on the
inner, so the control takes focus and centre does nothing. Both new composables had this and both
were fixed by deleting a modifier.

**Asking a lazy list for focus in a `LaunchedEffect` asks it before it has laid out a row.** The
effect runs when the composition is applied, which is earlier than that, and `tryRequestFocus`
swallows the failure — so the box opened and focus stayed on the settings row underneath. A frame
is waited for first.

**Trapping focus inside the open box was tried and rejected.** `FocusProperties.onExit` is what
exists for it; it did not hold inside a lazy list, and it was the wrong thing to want anyway. A
region the D-pad enters and cannot leave is the worst failure this app can have, and one held
shut by an experimental API is a bad bet on top of that. The box is left the way every other
region is left — by walking out of it — and it closes itself rather than standing open behind a
viewer who has moved on.

### Acceptance criteria

1. Moving the remote onto any television text field opens no keyboard.
2. Pressing that field opens the keyboard and puts the cursor in it.
3. Leaving the editor by any route puts the keyboard down and leaves focus on the field, or on
   whatever that route asked for next — never on nothing.
4. The category editor is passed in one press when it is shut.
5. Pressing it opens it onto the first category; Back closes it and returns focus to it.
6. Walking off either end of an open editor leaves it and closes it.

### Testing

Automated, all on the JVM:

- `TvCategoryBoxFocusEscapeTest` — rewritten around the new shape, and it now composes the real
  `CategoryBox` rather than a reproduction of it. A copy is free to drift away from the thing it
  copies, and this box is exactly the sort of code that gets adjusted. Four cases: the shut box
  is one press to pass, a press opens it onto the first category's controls, Back closes it and
  returns focus, and walking off the end of a three-hundred-category list leaves it *and* shuts
  it.
- `TvSettingsFieldStabilityTest` — two new cases: arriving on the field opens no editor, and
  pressing it opens exactly one.
  **It also found that this file's typing trace was measuring nothing.** The harness arrives on
  the field by pressing down from the row above, and that press does not land on the field: the
  row above sits at the far left of a wide panel while the field sits in the right-hand column,
  and the focus search picks the Save chip under it. So the keystrokes went to Save and the trace
  reported a flat line for them. The arrival is left as it is — what it measures is the scroll
  that arrival causes — and anything wanting the field now asks for it by name.

Manual: `docs/TESTING-REQUIRED.md` §A4.

---

## Part B — hidden means hidden

### The reports

> in Writing system hiding functionality we only hide if the title is fully arabic and so, make
> it of part of the title is Arabic for example we hide it also

> Hidden categories/languages should be hidden from search, but in advanced i shall have a
> toggle to search hidden also

### What the code does

**The script filter reads one letter.** `String.firstStrongScript()` takes the first strong
letter of a title and stops, and `isInHiddenScript` asks whether that one letter's script is
hidden. `TitleScriptTest` asserts in as many words that "Dune 2 مترجم" stays visible for a viewer
hiding Arabic. That was a deliberate choice and it is the one being reversed.

**Hidden categories were never applied to anything but the category list.** `CategoryRepository`
filters the chips; the item queries take a `groupTitle` and nothing else.
`ChannelDao.observeHiddenTitles` exists and is called from nowhere at all. So a hidden category's
titles come back from every search, and — separately — from browsing with "all categories"
selected.

**There are no hidden languages**, and there never were. `TitleScript` is a reading list rather
than a language list and says so; the report's "languages" is this filter.

### The rule, as decided

**Any strong letter of a hidden script hides the title, after a trailing tag is stripped.**
Trailing `[...]`, `(...)`, `{...}` groups and a trailing `| …` segment come off first, so a panel
that files an English film as `Oppenheimer [عربي]` does not lose it. A bare tag does not survive
that — `Dune 2024 مترجم` hides — and that is the author's decision, taken with the consequence
stated: an English film tagged in Arabic without brackets disappears for a viewer hiding Arabic.

`firstStrongScript()` stays exactly as it is. The right-to-left layout work (INC-F9) is built on
first-strong and must not move with this.

### Acceptance criteria

1. A title with any Arabic letter outside a trailing tag is hidden when Arabic is hidden.
2. A title whose only Arabic is inside a trailing bracketed or piped tag is not.
3. The rule is symmetric: hiding Latin hides a title with a Latin word in it.
4. Search returns nothing from a hidden category.
5. A toggle in advanced search returns hidden categories *and* hidden scripts, for that search.
6. Hiding a script does not silently shrink a page of results — the query overscans and filters.

### What was built

| Layer | Change |
| :---- | :---- |
| `core/common` | `String.strongScripts()`, `String.withoutTrailingTags()`, and `isInHiddenScript` rewritten around them. `firstStrongScript()` untouched |
| `core/database` | `ChannelDao.search` and `titlesForMetadata` take `includeHidden` and exclude channels whose `groupTitle` is hidden *for that kind* |
| `core/data` | `SearchRepository.search(includeHidden)`; the script filter moved inside `matches` with a `SCRIPT_OVERSCAN` of 2; the five arguments a search carries became one `Ask` |
| `feature/browse` | `SearchViewModel.setIncludeHidden`, reset by `clear()`, in the state |
| `app-tv` | An Include hidden chip, first among the advanced controls |

**Two decisions worth keeping.**

**The trailing-pipe form is not stripped**, although the shape of the rule invites it. Providers
write `AR | <title>` far more often than `<title> | AR`, so a rule that removed the last
pipe-separated segment would take the title and keep the tag — the exact inversion of what the
bracket rule is for. A test pins it.

**The genre index and the metadata scan both read the *whole* catalogue**, hidden categories
included. Coverage answers "how much of what you own has been described", which a hiding choice
does not change; and a scan that skipped hidden categories would make unhiding one cost an hour
of lookups.

### Testing

- `TitleScriptTest` — rewritten. Any letter hides; each of the three bracket shapes and a stack
  of them survive; the unbracketed tag hides and is pinned as the cost; the rule is symmetric;
  an unclosed bracket is left alone; a *leading* provider tag does not save an Arabic title.
- `HiddenCategorySearchTest` (new, real Room on Robolectric) — a hidden category is not searched,
  the toggle brings it back, hiding a category of one kind leaves the same name under another
  alone, a renamed-but-visible category is still searched, and the genre filter's title list
  obeys the same rule. Mocked-DAO tests can prove none of this: the whole behaviour is one
  correlated subquery.
- `SearchRepositoryTest` — the overscan asks for twice what it keeps and still returns a full
  page with a script hidden; `includeHidden` reaches the DAO and stops the script filter; an
  ordinary search asks for `includeHidden = false`.

Manual: `docs/TESTING-REQUIRED.md` §A11.

### Out of scope, and its own item

Browsing with "all categories" selected still shows items from hidden categories. Same defect
family, different screen, and it wants its own branch. **The phone is unaffected by everything
above except the script rule** — its in-tab search goes through `BrowseViewModel` and
`observeBrowse`, not through `SearchRepository`, so it is the same item.

---

## Part C — advanced search answers both halves

**Not yet built.** Root cause located.

### The report

> a huge issue here that its even series or movies that show not both and it's random!

### What the code does

It is not random, and finding that took reading three files.

`ChannelDao.titlesForMetadata` has no `ORDER BY`, so SQLite returns rows in rowid order. Rows are
inserted in source order — live, then films, then series — so rowid order clusters every film
ahead of every series. `SearchRepository.byGenre` then takes `limit * 2` (eighty) rows from that
clustered sequence **before** splitting them into the films column and the series column.

On a small catalogue eighty rows reach the series. On a real one they do not, and the series
column is empty. Which of the two is empty depends on nothing the viewer can see, which is what
made it look random.

The comment on that line states the opposite intent — "so a genre held mostly by series still
returns films" — and it is a fair description of what the author of it meant. The take is simply
on the wrong side of the split.

### And live channels

> In advanced search hide Livetv by default and put a toggle in settings to show it

A live channel has no metadata and never will, so in a genre search it is matched on the genre
word appearing in its own name — a deliberately weaker rule that fills a column which would
otherwise always be empty. It is also the column nobody searching for a genre wanted. It goes off
by default, with a setting to bring it back, and off means the query is not made at all.

### Acceptance criteria

1. A genre held by both films and series past the cap fills both columns.
2. Neither kind can starve the other, whatever the catalogue's insertion order.
3. Advanced search shows no live channels by default.
4. A setting brings them back, and it is one of the television's chip rows like every other
   television boolean.
5. With live off, no live query is issued.

---

## Open, and owned by the author

- **Whether to merge.** All three branches carry `fix:` commits, so any merge to `main` publishes
  a release.
- **The browse leak** in Part B's last section: worth its own item now, or left recorded?
