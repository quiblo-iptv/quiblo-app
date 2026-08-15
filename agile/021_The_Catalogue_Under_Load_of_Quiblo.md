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

*Not yet implemented — this section is the diagnosis the next two branches are built from.*

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

**Paging, and the one place it does not fit.** The phone's browse grid and the television's Live
list are flat `items(...)` calls and take Paging 3 cleanly. The television's Movies and Series
grid does not: `groupIntoRows` buckets the whole flat list into per-category rows in Kotlin, and a
paged list has no whole to group. That screen instead takes the shape it already implies — the
category list is a cheap query, so each row becomes its own bounded query of about forty items,
fetched as the row scrolls into view. Nobody walks three thousand tiles along one row with a
D-pad, which is the reasoning `BrowseFeed.recentLimit` already uses.

*Consequence to accept:* `onPlay(state.items, item.flatIndex)` hands the player everything on
screen to zap along. It becomes the rows loaded so far. That is a real behaviour change and the
honest one — the old list was only complete because the screen was paying to load the whole
catalogue.

*At risk and to be re-run rather than edited around:* `TvBrowseScrollStabilityTest` (defect #008,
four wrong analyses), `SearchRepositoryTest`, `HiddenCategorySearchTest`, `BrowseViewModelTest`.

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
