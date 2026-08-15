**The Catalogue Under Load of Quiblo — what 0.18.0 broke, and what was always slow**

Four reports came back from `v0.18.0` running on the television against a 67,000-title account.
They are not four independent faults. Two of them share one root — the catalogue's cleaned title
and its writing system are recomputed in Kotlin on every question asked of it — one is a race that
has been in For You since the tab was built, and one is a feature that shipped without a switch.

**Created:** 2026-08-16, against `8ec3055` on `main` (`v0.18.0`).
**Ships as:** `1.0.0` work. Three `fix:` items and one `feat:`.

**Branches**, in dependency order:

| # | Item | Branch |
| :--- | :--- | :--- |
| 1 | `BUG-021` — For You builds its rows before the key is read | `bugfix/BUG-021-for-you-rows-never-retry` |
| 2 | `BUG-022` — the catalogue's identity is recomputed per read | `bugfix/BUG-022-catalogue-identity-columns` |
| 3 | `BUG-023` — browse loads the whole kind | `bugfix/BUG-023-paged-browse-feeds` |
| 4 | `FEAT-024` — ambient is unconditional and two seconds late | `feature/FEAT-024-ambient-switch-and-latency` |

The repository has no `dev` branch and cuts from `main`, as `019` and `020` did. That diverges
from the amendments' Git Flow and is recorded here rather than acted on alone.

**Nothing here was reproduced on a device.** The wheel stayed with the author, so every diagnosis
below is read off the code and every fix names the test that pins it.

---

## 1. For You draws only "Recently added"

**Reported:** the tab shows the first row and neither of the two that `020` added.

**What it actually was — a race with nothing behind it to ask again.**

`BrowseViewModel.extraRowsFor` was a one-shot `flow { emit(empty); emit(build) }`. For
`BrowseScope.FOR_YOU` every flow feeding the surrounding `flatMapLatest` emits exactly once —
`categoriesFor` returns `flowOf(emptyList())`, and neither the category nor the query flow ever
moves — so the build ran once per ViewModel and never again.

That build reaches `PopularTitlesRepository.refreshIfStale`, which opens by *sampling*
`metadata.apiKey.value`. The key lives in `TmdbKeyStore`, backed by `EncryptedSharedPreferences`:
its value starts null and is only filled by `load()`, which is a `MasterKey` build plus a keystore
round trip. `BrowseViewModel.init` fires that `load()` in a `viewModelScope.launch` racing the
flow above.

Lose the race — which on a television SoC is the ordinary outcome — and the key reads null, the
fetch is skipped, `dao.all()` is empty, no row is drawn, and nothing asks again for the life of
the screen. The rating badges visible in the report prove the key *is* configured, which rules out
the honest "no key" case and leaves the race.

**The fix.** The rows are keyed on the value rather than sampled from it:

```kotlin
flow {
    emit(emptyList())
    metadataRepository.load()
    emitAll(metadataRepository.apiKey.map { buildExtraRows(sourceId) })
}
```

Awaiting `load()` removes the race. Keying on the value afterwards is what makes a key pasted into
Settings mid-session fill the row rather than wait for a relaunch. It is one build per distinct
key and not two — waiting is what stops the null pass, a full suggestions match across the
catalogue, from being paid for and thrown away. No `distinctUntilChanged`: `apiKey` is a
`StateFlow` and already conflates.

**Tests.** Two in `BrowseViewModelTest`, both red against `main` and green against the fix:

- *the popular row is built after the metadata key has been read, not before* — the fake store
  takes 50ms of virtual time to answer. **That delay is load-bearing.** Without it the scheduler
  runs `init`'s fire-and-forget `load()` to completion before the feed's first build, and the race
  the device loses is one the test wins every time.
- *a key configured while the tab is open fills the popular row* — the other half, and the reason
  the fix is a keyed flow rather than a longer wait.

**What was left alone, deliberately.** "You may like" has two further silent emptinesses —
`RecommendationRepository.suggestions` returns nothing when the profile has no watch history, and
nothing when `title_metadata` holds no genres yet. Both are legitimate and both are `013`'s design
("a row with nothing in it is not drawn"). The plan called for a log line naming which one fired;
**`core/data` has no logging at all**, and introducing `android.util.Log` into that layer for one
diagnostic line is a worse trade than the diagnosis is worth. It stays undiagnosable from the
device until there is a reason to give that layer logging properly.

---

## 2 and 3. One root: the catalogue is re-cleaned on every question

**Advanced search spins.** `SearchRepository.byGenre` reads `channelDao.titlesForMetadata(...)` —
every film and series on the account, ~50,000 rows here — and calls `cacheIdentity()` on each.
That is `titleIdentity()`: eight regex passes, a `lowercase()`, and a year strip. Roughly 400,000
regex applications per genre press, with nothing cached between presses, and `genreIndex` performs
the same full pass at screen init for the coverage figure. It is not hung. It is doing far too
much work, twice.

**Browse is slow.** `ChannelDao.observeBrowse` has no `LIMIT` and nothing pages it, so a Movies tab
materialises every row of the kind, maps each to a domain object, then runs
`hidingUnreadableScripts` across the lot. `String.isInHiddenScript` changed in `1098cbe` — shipped
in 0.18.0 — from `firstStrongScript()`, which stopped at the first strong letter, to
`withoutTrailingTags().strongScripts()`: a regex strip loop, a full codepoint walk and a `Set`
allocation, per title, per emission. That is the half the author felt as *"why does this take so
long now"*. The unbounded query is the older half underneath it.

**The fix both share.** Three columns on `channels`, filled at import and backfilled by
`MIGRATION_18_19`: `searchTitle`, `identityYear`, `scriptMask`. With them stored, the genre filter
becomes an indexed join against `title_metadata` with `LIMIT` per kind, script hiding becomes
`(:scriptMask & c.scriptMask) = 0`, coverage becomes two `COUNT`s, and the overscans that exist
only to pay for a Kotlin-side filter — `SCRIPT_OVERSCAN`, `LIVE_OVERSCAN` — go with them.
`titleIdentity()` and `isInHiddenScript` are unchanged; they are still the definition, they simply
move to import time.

### What `BUG-022` actually did

Schema `18 → 19`. `MIGRATION_18_19` adds `searchTitle`, `identityYear` and `scriptMask` to
`channels`, adds the two indexes that make them worth having, and **reads not one row**. Filling
fifty thousand rows inside a migration is ten to twenty seconds of a television frozen on its own
splash, because a migration runs on the first access to the database with every screen waiting
behind it.

**Unknown is a third value, and that is the load-bearing decision.** Rows arrive carrying
`SCRIPT_MASK_UNKNOWN`; `CatalogueIdentityBackfill` fills them in the background at app start, in
batches of five hundred, cancellable at every row; and every query that reads the mask passes an
unfilled row through to the Kotlin filter that decided every row before schema 19. So a catalogue
mid-backfill hides exactly what it always hid. Defaulting the column to `0` would have been a
window in which hiding quietly stopped — which is the defect the release before this one was spent
fixing — and `-1` filtered naively would have hidden the entire catalogue.

**What moved into SQL.**

| Was | Is |
| :--- | :--- |
| Clean 50,000 titles in Kotlin, intersect with cached genres | `searchByGenre`: an indexed join on `(searchTitle, kind, identityYear)`, `LIMIT` per kind |
| Regex strip + codepoint walk + `Set` alloc per title per emission | `(:hiddenMask & c.scriptMask) = 0` in `observeBrowse` and `search` |
| Clean the whole catalogue again to compute coverage | `countDistinctTitles` and `countDescribedTitles` |

Three details in the genre join are each one test in `GenreAndScriptQueryTest`, because each of
them fails as a plausible list of the wrong titles rather than as an error: genres are matched
newline-wrapped so `Drama` does not match `Crime Drama`; the year is half the key so two films
called Dune stay two films; and a blank `searchTitle` is excluded, because it is the shared "not
worth looking up" value and joining on it would file every junk row under one genre.

**Tests.** Ten in `GenreAndScriptQueryTest` against real SQLite — a mocked DAO proves nothing about
a join. Six in `CatalogueIdentityBackfillTest`, including that a backfilled row gets the same two
answers `Channel.toEntity` computes at import, so an upgraded catalogue and a re-imported one end
up identical. `MigrationTest` gains 18→19 and asserts the refusal to read rows. The mocked
`SearchRepositoryTest` cases that used to assert the Kotlin matching now assert its *absence* —
that this layer no longer reaches for the catalogue at all.

**Not touched, and worth an item of its own.** `PopularTitlesRepository.popular` and
`TitleMetadataScanner` both still read `titlesForMetadata` and clean in Kotlin. Neither is on a
per-press path — the popular row is built at most twice a session and the scanner is explicitly
started — but both could now join on the stored key instead. Raised rather than absorbed.

### Paging — `BUG-023`, and the one place it does not fit

`observeBrowse` had no `LIMIT` and nothing paged it, so opening Movies read every row of the kind,
allocated a domain object per row and handed the lot to a lazy grid that draws about twelve. Two
shapes were needed, not one:

- **Flat lists take Paging 3.** The phone's browse grid and list, and the television's Live list.
  `pagedBrowse` in the DAO, `Pager` in the repository, `cachedIn(viewModelScope)` on
  `BrowseViewModel.pagedItems`. It is deliberately *not* a field on `BrowseUiState`: `PagingData`
  is a stream of loads rather than a value, and putting one inside screen state would hand the
  grid a fresh pager — and throw away its scroll position and every loaded page — every time a
  rating arrived or a poster resolved.
- **The television's poster grid cannot be paged**, because `groupIntoRows` buckets its whole
  answer into a row per category and a paged list has no whole to group. It gets
  `BrowseScope.CATEGORY_ROWS` and one window-function query capping each category at forty —
  `ROW_NUMBER() OVER (PARTITION BY groupTitle …)`, ordered back into the provider's own order by
  the outer query, because `ROW_NUMBER`'s partition would otherwise hand the screen its categories
  alphabetically. Forty is `BrowseFeed.recentLimit`'s reasoning: nobody presses right forty times.
  The screen's grouping and its measured scroll behaviour are untouched; it simply reads about as
  many rows as it draws.

**The guide prefetch moved rather than being dropped.** `017` fixed a television Live list that
drew blank until somebody focused a row, by asking for the first ten channels' guides as soon as
the list existed. That lived in the ViewModel because the ViewModel could see the list. The list
is paged now, so `TvLiveScreen` calls `onRowVisible` for the first ten loaded rows instead —
and nothing about the guard moved: the kind check, the stream-id check, the once-per-session
dedupe and the concurrency limit are all still behind that one door, which is what the rewritten
`BrowseViewModelTest` cases now assert directly.

**Consequence accepted:** `onPlay(items, index)` handed the player everything on screen to zap
along. It is now the pages loaded so far. A real behaviour change, and the honest one — the old
list was only complete because the screen was paying to load the whole catalogue.

**Two copies of one query, and the test that keeps them honest.** Room cannot return both a
`Flow<List<…>>` and a `PagingSource` from one declaration, so the browse predicates are written
twice. A predicate added to one and forgotten in the other is not a build failure and not a crash
— it is one app hiding a writing system the other does not.
`PagedBrowseMatchesUnpagedTest` asserts the two answers are the same list across category, search,
favourites, a hidden script and an uncomputed row.

**Not touched:** `TvBrowseScrollStabilityTest` needed no change, because the poster grid's own
shape did not change — only the size of what it is handed.

New dependencies: `androidx.paging:paging-runtime`, `paging-compose`, `paging-common`,
`androidx.room:room-paging`. All Apache-2.0, so gate 1 is satisfied.

---

## 4. Ambient, on by default and closer to the frame

Shipped in `0.14.0` and never given a switch. `TvPlayerScreen.VideoSurface` sampled the surface
every `1_500ms` and `ambientBackdrop` crossfaded over `700ms`, so the light could sit ~2.2s behind
the picture — near enough to look deliberate, far enough to read as two separate things rather
than one.

**The switch.** `ambientPlayer` in `PlayerSettingsStore`, defaulting to **on**, following
`showLiveInSearch` exactly — including its rule that off means the work is not done rather than
done and discarded: the sampling loop never starts. It sits under Playback on the television's
settings, not buried, because it changes what the screen looks like for the whole length of a film.

**The seed matters and is the one thing worth a test.** Every layer mirroring the stored value has
to start from `true`. A layer seeding `false` is invisible on inspection — the store answers a
moment later and corrects it — and produces a switch that flickers off as settings opens and a
flash of dead black bars at the start of every film. `AmbientSettingTest` pins it, with a store
that never answers so the seed is the only thing under test.

**The timings.** `ambientBackdrop` takes its crossfade as a parameter rather than a file constant,
because the two callers answer different things. The browse grid keeps `GRID_CROSSFADE_MILLIS =
700` — it follows *focus*, and that number was chosen against D-pad key repeat so a held press
drifts rather than strobes. The player gets `PLAYER_CROSSFADE_MILLIS = 300`, because it follows the
*picture*, where the same 700ms reads as the light arriving after the scene it belongs to. Sampling
goes from 1500ms to 400ms: `PixelCopy` at 32×18 is a trivial copy and the sampler reads six pixels
of it, so the cost of asking four times as often is the call itself — nothing next to decoding the
frame it copies.

The phone player has no ambient at all and does not get one here — that belongs with the episode
controls already waiting on the phone.

---

## What a device still has to answer

Ten tickets, `A14.1` to `A14.10`, in
[`docs/TESTING-REQUIRED.md`](../docs/TESTING-REQUIRED.md). Every one of them is about how long
something takes or whether a row is there at all, and neither can be answered on a small
catalogue — which is exactly how three of these four defects reached a release.

Two of them are worth naming here because they are the ones a passing build says least about:

- **`A14.3`** wants a *number*. Three separate changes claim the browse load time between them —
  the script mask, the per-category cap, and paging — and only a device can say which of them
  mattered. "Still slow" cannot be acted on.
- **`A14.8`** is the upgrade, with a writing system hidden. The whole design of `MIGRATION_18_19`
  is that hiding keeps working while the backfill runs, and there is no way to see that except by
  installing over 0.18.0 and looking twice, a minute apart.

## What was not measured

**Coverage is not reported for this round, and that is the project's existing rule rather than an
omission here.** `coverageAll` covers `:source:m3u` and `:source:xtream` only — the parsers, which
are pure functions over text where a covered line is genuinely an exercised line. Nothing in this
round touched a parser, so the gate is green and says nothing about this work. The Amendment 10
floor is written against first-party code generally; this repository narrowed it deliberately and
wrote the reasoning into `build.gradle.kts`. Recorded as a divergence rather than acted on alone.

What this round has instead is **31 new tests and four rewritten ones**, of which 20 run against
real SQLite — because the work moved two predicates and a join *into* SQL, and a mocked DAO proves
nothing about any of them. Two of the four reports have a test that is red against `v0.18.0` and
green against the fix; the other two are load times, which no test on this machine can settle
(`A14.3`).
