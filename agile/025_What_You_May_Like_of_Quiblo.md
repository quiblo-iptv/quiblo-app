**What You May Like of Quiblo — thirteen signals, four per taste, and the discipline to say nothing**

A viewer who had watched One Piece was offered The Boys, The Umbrella Academy, See, and a dubbed
Arabic family drama. Every one of those shares "series" and "Action & Adventure" with One Piece and
nothing else anybody would recognise, and the row had no way to see the difference — it scored on
genre and on nothing else. This round rebuilds the scoring, gives the app a way to be told what
somebody thought, and teaches the row to keep quiet until it has something to say.

**Created:** 2026-08-16, against `2267c58`.
**Ships as:** `1.0.0` work, `feat:`. A merge to `main` cuts a release; it is not merged until that
is asked for.

**Branch:** `feat/025-recommender`, on top of `feat/024-scheduled-work`.

---

## What was asked for, and what is honest to build

**BellKor is not built, and the reason is not preference.** Collaborative filtering — the family
BellKor belongs to — works by comparing one viewer against millions of others. That needs a server
collecting what everybody watched, and `FREEZE.md` §2 rules out a backend while §4.5 says the app
never phones home. Those are locked decisions, and they remove the whole class rather than this one
algorithm. Run on one household, a matrix factorisation is factorising a single row.

**MovieLens is not bundled either, and here the reason is legal before it is technical.** The
dataset's licence forbids redistribution without permission, which is a gate-1 failure under
Amendment 16 before it is anything else. `ml-25m` is also a quarter of a gigabyte against an APK
measured in tens of megabytes, and its item ids join to MovieLens and IMDb — nothing in this
database holds a TMDB id against a channel, so the join would be the same cleaned title string the
rest of this app already uses, applied to a foreign catalogue.

**What is built instead is everything the request listed as a signal.** All thirteen of them, from
data already on the device.

## The thirteen

Eight say how alike two titles are:

| Signal | Where it comes from | Weight |
| :---- | :---- | :---- |
| **Form** — film, series, **anime**, talk show | genres plus the original language | 0.32 with genre |
| Genre overlap | the metadata cache | 0.32 |
| Description keywords | the overview, tokenised, weighted by rarity | 0.22 |
| Form | see above | 0.16 |
| Language | the original language | 0.11 |
| Release year | how close in time | 0.07 |
| Runtime | how close in length | 0.05 |
| Popularity | rare or common, matched to what they watch | 0.04 |
| Rating | a nudge between two equal matches | 0.03 |

Five say how much a watched title counts as evidence: how much of it was watched, how recently
(halving every month), how many times, where it was chosen from, and whether the viewer said so —
a favourite, or a thumb.

## Decisions, and what they rule out

**Anime is Animation made in Japanese.** That is the one rule separating it from a Western cartoon
using only fields the cache already holds, and it is deliberately narrow. A viewer who watches
anime is saying something specific; widening this to "any animation" would answer One Piece with a
Pixar film, which is the same mistake in a nicer costume.

**Keywords come from the overview, not from a keyword endpoint.** TMDB has one. Using it would be
one request per title across a sixty-thousand-title catalogue — this project's provider account has
been blocked twice over requests it did not need, and there is no reason to learn that lesson again
against a second host. Rarity is computed over *this* provider's catalogue rather than from a
shipped list, because a service carrying nothing but Arabic drama has different common words to one
carrying nothing but Hollywood films.

**No stemming.** An English stemmer applied to a catalogue that is half Arabic and half French
produces confident nonsense, and the rarity weighting already discounts the words that inflect
most. What survives is nouns — *pirate*, *assassin*, *ninja*, *heist* — which is exactly what makes
one adventure story different from another.

**Seed-oriented, four apiece.** Each of the strongest five watched titles proposes its own best
four, rather than every candidate being scored against one blended profile. A blend is what makes
somebody who watches anime and one cookery programme get suggestions that are neither: the average
of two tastes is a taste nobody has. Built round by round rather than seed by seed, so a row that
runs out of room still has something from each taste.

**A thumbs-down is not a weak seed; it is not a seed.** Scoring it low would still let it choose
tiles whenever nothing else competed for them, and "I did not like this" must never produce a
suggestion. The title itself is also never suggested again.

**The cold start is the answer to "wait a bit and learn".** Below five distinct titles watched, or
three watched most of the way through, the row is not drawn at all. Both conditions are needed: a
viewer who has opened twenty things and finished none has said what they browse rather than what
they watch. One watched title produces four confident unrelated suggestions and no weighting fixes
that — only declining to answer does.

**An occasion is a sitting, not a heartbeat.** `watch_events` gets one row when playback of a title
ends, not one per position write: a row per ten seconds would make "how many times" mean "how
long". It is a log, so it is bounded — a year, well past the point where the scorer's own decay has
made an event noise.

**An opinion is about the film, not the row.** `title_opinions` is keyed by the cleaned title, so a
viewer who disliked something is not asked again when their provider re-lists it under a new id.
Absence is the third value: clearing removes the row, because a stored `NONE` and a missing row
would be two ways of saying the same thing and something would eventually treat one as a middle
rating.

**Origin is carried, not derived.** By the time the player has a stream URL, a title typed into a
search box and the first tile of a row look identical. Each tab says where the viewer was, and the
detail overlay carries it to the player.

**No recommendation worker, and that is a change from the plan.** `023` already moved this off the
composition thread and into a cache; a worker would recompute, on a schedule, an answer nothing is
waiting for and that only matters when somebody opens the tab — which is when it is computed. The
scheduled work that *does* earn its wake-up is `024`'s: fetching data the device does not have.

## What was built

| Layer | Change |
| :---- | :---- |
| `core/model` | `WatchOrigin`, `Opinion`, `WatchEvent` |
| `core/database` | `WatchEventEntity` + `WatchEventDao`, `TitleOpinionEntity` + `TitleOpinionDao`, `title_metadata.originalLanguage` and `.popularity`, `TitleFactRow`, `FavoriteDao.keysFor`, schema **22**, `MIGRATION_21_22` |
| `source/tmdb` | `original_language` and `popularity` parsed on all three payloads — no extra request |
| `core/data` | `Recommender` rewritten: `TitleForm`, `TitleFacts`, thirteen signals, seed-oriented, cold start; `WatchEventRepository`; `TitleOpinionRepository`; `keywordsOf` |
| `feature/player` | records one occasion per sitting, with its origin |
| `feature/vod`, `feature/series` | thumbs on both detail screens, on both apps |
| `app`, `app-tv` | origin threaded from the tab a viewer was on |

## Acceptance criteria

1. Below five watched titles, three of them finished, no suggestions row is drawn.
2. Above it, every tile names one of the viewer's own titles.
3. An anime viewer's row is anime, not superhero drama.
4. A thumbs-down title is never suggested and never causes a suggestion; a thumbs-up counts for
   more.
5. Pressing a lit thumb again clears the opinion, on both apps.
6. Something watched repeatedly weighs more than something watched once.
7. Something reached by search weighs more than something taken off a row.
8. The row is not one genre.
9. Nothing is sent anywhere: the only outbound traffic is the viewer's provider and their own
   metadata service.
10. An existing installation upgrades to schema 22 without losing anything, and its metadata cache
    keeps what it had.

## Testing

Automated, all on the JVM:

- `RecommenderTest` (rewritten, 20) — one case per signal, plus the two cold-start conditions,
  determinism, genre fatigue, both kinds as candidates, and a candidate the cache knows nothing
  about. **A whole-row assertion is deliberately rare**: a row is the product of thirteen weights,
  and a test that pins the row pins the weights, which turns tuning into a test failure rather than
  a measurement.
- `RecommendationRepositoryTest` — silence with no history and with no described catalogue, hidden
  categories never proposed out of, the last watch of each title across both kinds.
- `MigrationTest` (+1) — 21 → 22 adds both tables empty and both columns null, keeping the metadata
  cache. Null on both columns is asserted rather than assumed: a default would have made "nobody
  has fetched this" indistinguishable from "the service does not know".
- `PlayerResumeWriteTest`, `MovieDetailResumeTest`, `SeriesDetailViewModelTest` — carried forward
  through the new dependencies.

Gates: `detektAll`, `test`, `coverageAll` — `:core:data` at **72.3** against its floor of 70 —
`licenceCheck` and `lint`. All green.

Manual: `docs/TESTING-REQUIRED.md` §A18, nine tickets. **Every one of them needs a real watch
history**, which is days of use rather than an afternoon: there is no way to compress a scorer
whose whole subject is what somebody actually watched. A18.3 is the reported defect itself, and it
is the one judgement no test can make — whether a row *reads* as being about what you watch.

## Open, and owned by the author

- **The weights are argued, not measured.** Each has a sentence saying why it sits where it does,
  and none has been checked against a household. They are in one block in `Recommender` for exactly
  that reason.
- **Whether the cold-start threshold is right.** Five titles and three finished is a judgement. Too
  low and the row is confidently wrong; too high and a viewer never sees it.
- **Whether thumbs get used at all.** They are on the details screen because that is where the
  author asked for them, and a control nobody presses is a signal that stays empty. If they go
  unused after a sweep, the honest response is to remove them rather than to move them.
- **The keyword stop list is English.** The rarity weighting does most of the work, but a
  catalogue that is mostly Arabic will carry Arabic filler words that nothing discounts by name.
- **Whether to merge.** The branch carries `feat:` commits, so a merge to `main` publishes a
  release.
