**Grouping of Quiblo — what `014` needs that the tree does not have**

[`014`](014_One_Entry_Per_Title_of_Quiblo_—_duplicates,_qualities_and_languages.md) read against
the code it would be built on, the same way
[`015`](015_Pass_One_of_Quiblo_—_the_screens_012_leaves_open.md) reads `013`'s pass 1.

**Created:** 2026-08-12, against `4e4d56b` on `main`.
**Ships as:** `1.1.0`, unchanged — **except for one defect this found, which is `1.0.0` work
and is specified below as #024.**

**Written while the acceptance sweep is open, and not built.** Same rule as `015`.

---

## The finding: the merge `014` is written to prevent already happens

`014`'s governing failure is the false merge — "Dune (1984)" folding into "Dune (2021)", and a
viewer seeing not a bug but a catalogue that does not have their film. The document treats it as
a risk to design against.

**It is not a risk. It is current behaviour, in the metadata cache, on the shipping build.**

`TitleMetadataRepository.kt:114` builds its cache key as `title.cleanedForSearch().lowercase()`
and looks it up at `:133`. `title_metadata`'s primary key is `(searchTitle, kind)`
(`Entities.kt:254`). And `cleanedForSearch` strips bracketed groups whole
(`TitleCleanup.kt:34`, `BRACKETED` at `:56`) — **including the year**, because a year in a
provider title is almost always in brackets.

So `Dune (1984)` and `Dune (2021)` are one cache row. The first fetched wins. The other film
shows the winner's poster, overview, rating, genres and cast, permanently, because a cache hit
is never re-asked.

`yearInTitle()` exists (`TitleCleanup.kt:54`) and is used at
`TitleMetadataRepository.kt:169` — but only to narrow the *network query*, never as part of the
key. The right answer is fetched and then filed under a name that cannot hold it.

### What the cleaner does to `014`'s own danger table

| Title | Cleans to | `014` says |
| :---- | :---- | :---- |
| `Dune (1984)` | `dune` | **Must not merge** |
| `Dune (2021)` | `dune` | — and it does |
| `Dune 1984` | `dune 1984` | Same film as row 1, **different key** |
| `The Office US` / `UK` | `the office us` / `the office uk` | Correctly separate |
| `Rambo 2` / `Rambo II` | `rambo 2` / `rambo ii` | Correctly separate |
| `Batman` / `Batman Begins` | `batman` / `batman begins` | Correctly separate |
| `Spider-Man` / `Spider Man` | `spider man` / `spider man` | Correctly merged |

And the four rows `014` opens with:

```
Interstellar 2014 1080p     -> interstellar 2014
INTERSTELLAR - 4K           -> interstellar
Interstellar (2014) [AR]    -> interstellar
interstellar   FHD MULTI    -> interstellar
```

Three of four share a key and the fourth does not — **decided entirely by whether the provider
bracketed the year.** That is the wrong split and the wrong merge in one example.

**Method, stated plainly:** this is the regex chain evaluated outside Kotlin, not a JVM run.
It is strong enough to plan against and not strong enough to close a bug with, which is why the
first build step below is the test, not the fix.

### Why this is a defect and not a design note

- **The symptom is silent and looks like content, not failure.** A viewer sees the wrong plot
  for a film. Nothing is empty, nothing errors, nothing retries.
- **`INC-F7` does not fix it and will appear to.** The intake asks for a refresh button because
  "artwork not shown or details not updated" — plausibly this. Pressing refresh re-fetches into
  the same colliding key and overwrites the *other* film's row. Two viewers pressing it on two
  Dunes would fight.
- **It is `1.0.0` scope.** `AC-META` is a shipped feature and this is it returning wrong data.
- **It is exactly `013` INC-F12's shape inverted again** — the code does the right thing and the
  seam around it throws the answer away.

### #024 — the metadata cache key drops the year

| | |
| :---- | :---- |
| **Platform** | Both |
| **Criterion** | `AC-META` |
| **Mechanism** | `TitleMetadataRepository.kt:114` keys on `cleanedForSearch()`, which strips bracketed years (`TitleCleanup.kt:34`) |
| **Fix** | The cache key becomes `cleanedTitle + kind + year?`. `yearInTitle()` already extracts it and the call site already has it (`:169`) |
| **Schema** | `title_metadata`'s primary key gains `year`. Existing rows migrate with a null year and are re-fetched on next visit rather than trusted |
| **Test first** | A JVM test asserting *separation*: `Dune (1984)` and `Dune (2021)` produce different keys. There is no `TitleCleanup` test file today — `TmdbTest.kt:53`–`113` covers the cleaner well, and every assertion is about producing a good query string. **Nothing asserts identity.** That gap is why this survived |

**Recommendation: #024 joins `012` and ships in `1.0.0-beta.1`.** Not added to `012`'s table
here — the remote is being spent on that round's ten unswept defects, and growing the list under
a sweep in progress is Mahmoud's call, not a documentation edit.

---

## Four corrections to `014`

### 1. Favourites and resume points are not keyed by channel id

`014:120` says both are "per profile today, keyed by channel id", and plans a migration re-keying
them onto the group. They are keyed by **`stableKey`** — `favorites` on
`(profileId, sourceId, stableKey)` (`Entities.kt:131`), `resume_positions` on
`(profileId, stableKey)` (`:173`).

**This matters more than a correction usually does.** `stableKey` is deliberately not the row id
so that a favourite survives a refresh in which the stream URL changed — `AC-FAV-03`, and the
entity comment at `Entities.kt:121` says so outright. **`groupKey` is derived from the provider's
title string**, which is the thing providers change most often: a rename from
`Dune (2021)` to `Dune 2021 4K` moves the row to a different group.

So **re-keying favourites onto `groupKey` trades a key built to be durable for one built to be
derived**, and the failure is a viewer's favourites emptying after a provider tidied its titles.

**Correction to the design: do not re-key. Resolve at read.** Favourites and resume points keep
`stableKey`. A group answers "is any of my rows favourited" and "what is the furthest position
any of my rows holds". That satisfies both promises `014` makes — favourite once, resume across a
version switch — with no migration of user data at all, and it survives a regrouping because
nothing was rewritten.

This deletes the half of `014`'s schema work that it calls "most likely to go wrong quietly".

### 2. There is no TMDB id to win outright

`014:85` says an id beats the string key when both rows have one. **No id is stored.**
`TitleMetadataEntity` (`Entities.kt:254`) has no id column, and its own primary key is the
cleaned title — the metadata cache is *itself* keyed by the thing `014` is trying to replace.

Two ways out, and they are the same work as #024:

- **Store the id.** `title_metadata` gains `tmdbId`, filled on every fetch from a response the
  client already parses. Then `014`'s claim becomes true, and #024's key can use the id where one
  exists and the year where one does not.
- **Or drop the claim** and let the string key stand alone.

**Recommendation: store it, as part of #024's schema change.** Two schema bumps of the same table
for the same reason is one bump too many, and an id is the only identity in this problem that is
not an inference.

### 3. Ingest is already one place, and it is not the source layer

`014:100` asks that `groupKey` be computed "by the source layer when a playlist is parsed, so
both M3U and Xtream get it from one implementation". The sources do not build `ChannelEntity` at
all — `core/data/Mappers.kt` is the only file outside the database module that references it.

So the single implementation `014` wants already exists as a chokepoint, one layer up. **Compute
`groupKey` in the mapper.** Cheaper than the document assumes, and no source module changes.

### 4. The schema-10 precedent is accurate

`014:102` cites schema 10 as the lesson that a backfill costs a first launch. `MIGRATION_9_10`
is two `CREATE INDEX` statements over `channels` and `programmes` with no bound on catalogue
size. The warning stands, and `MIGRATION_10_11` is the better model for #024's own migration —
it rebuilds tables and **adopts existing data into a `Default` profile rather than dropping it**,
which is the shape any grouping migration should copy.

---

## What survives untouched

Most of `014`, and the parts that matter most:

- **A strict key, not a similarity score.** Confirmed by the cleaner's own results — it gets four
  of six danger rows right *because* it compares equality on a normalised string, and its one
  failure is the one field it throws away.
- **The year is what makes the key safe.** `014` says never strip it. The tree strips it. That is
  the whole finding, and it is `014` being right in advance.
- **Nothing is ever deleted; grouping is a view.** Untouched, and now cheaper — correction 1
  removes the user-data migration that made reversibility hard to promise.
- **One cleaner, one place.** Strengthened. `cleanedForSearch` is already shared by the metadata
  cache, search's genre index (`SearchRepository.kt:260`) and `013`'s autocomplete. Fixing its
  identity behaviour for #024 fixes grouping's key before grouping is written.
- **The fixture corpus, written before the grouping code**, synthetic per `AC-LEGAL-04`, with
  every "No" row asserting *two* groups. #024's test is the first entry in that corpus, arriving
  a release early.

## Order of execution

| Step | Work | When |
| :---- | :---- | :---- |
| 1 | The `TitleCleanup` identity test — assert separation, watch it fail | With `012`, if #024 is accepted into `1.0.0` |
| 2 | #024 — year and `tmdbId` in the cache key and the table | Same |
| 3 | `groupKey` in `Mappers.kt`, column and index on `channels` | `1.1.0`, after `012` and `013` pass 3 |
| 4 | Group resolution for favourites and resume, at read | Same |
| 5 | Catalogue card, Versions list, player switcher, the setting off by default | Same |
| 6 | The `FREEZE.md` amendment | When step 5 ships |

Steps 1 and 2 are the only ones that are not `1.1.0`, and they are the only ones with a viewer
looking at wrong data today.

## What this document does not decide

- **Whether #024 enters `012`.** Recommended, not done. It grows a sweep that is running.
- **Whether grouping ships at all.** `014`'s exit criteria are unchanged and still have to be met.
- **The token strip list.** `014` owns it, `cleanedForSearch`'s `QUALITY_MARKERS`
  (`TitleCleanup.kt:64`) is already most of it, and merging the two lists is step 3's work.
