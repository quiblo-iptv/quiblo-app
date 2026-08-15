**For You of Quiblo — three rows, three questions, and the one that needed a subsystem**

The television's Recently Added tab answers one question: what did my provider add. It is a good
question and it is not the only one somebody has when they sit down with nothing particular in
mind. This turns that tab into **For You**, keeps its row as the first of three, and adds the two
that were designed a fortnight ago and never built.

**Created:** 2026-08-15, against `e6090ec` on `main`.
**Ships as:** `1.0.0` work, `feat:`. A merge to `main` cuts a release; it is not merged until that
is asked for.

**Branch:** `feat/tv-for-you`.

The four bugs that arrived in the same request are
[`019`](019_Friction_on_the_Television_of_Quiblo.md). They are separate because they are not this,
and because they leave the app better whether or not this is built.

---

## The three rows

| Row | Question | Where it comes from |
| :---- | :---- | :---- |
| **Recently added** | What did my provider put on this month? | `017`'s feed, unchanged |
| **Now popular** | What is the world watching, of the things I can actually play? | TMDB's two popular lists, intersected with the catalogue |
| **You may like** | What would *I* want? | `013` INC-F2 — content-based scoring over this profile's watch history |

**Every one of them is absent rather than empty when it has nothing to say.** No metadata key, an
unscanned catalogue, a fresh profile, a provider that carries none of this week's popular titles:
each of those removes a row. Not an empty shelf, and not a spinner that never resolves. That rule
is `013`'s and `012`'s between them — *a screen is finished when its failure looks right.*

## Decisions, and what they rule out

**"You may like", with the reason on the card.** `013` designed this row as "Because you watched
X" — the heading naming the cause — because saying *why* is the one thing a scoring function can
do that a model cannot. The author asked for a fixed title, so the row is titled and each tile
carries its own one-line reason underneath. The honesty survives; the heading stops moving.

**Five films and five series, not a merged top ten.** TMDB returns two lists and neither says
anything about the other. Merging them into a single 1–10 would have needed a popularity score
compared across catalogues, which is a claim nobody measured. So the row is five of each, each
numbered by its own position, and the Movie/Series badge is what tells the two fives apart.

**Two requests a week, and off entirely without a key.** The popular lists are cached in the
database for seven days, so a household opening the app every evening still costs two requests
across the whole week. **This project's provider account has been blocked twice over requests it
did not need**, and a metadata service refusing is the same failure with a different host. A
refusal is never written down: it would otherwise stand for a week.

**The rank is drawn inside the artwork, and the caption's line is reserved for the whole row.**
Both are the same decision, and it is the most consequential one here. A numeral standing outside
the poster — the shape a commercial service uses — would make a tile's width depend on how many
digits its number has. A caption drawn only on the tiles that have one would make a tile's height
depend on its own content. Either is a focused tile whose measured rectangle differs from its
neighbour's, which is **defect #008** — the wobble that took four wrong analyses. The note inside
`TvPoster` says in as many words that *a label that grows a line will start the loop again*, and
`TvBrowseScrollStabilityTest` now walks both new shapes.

**Suggestions are arithmetic, and they run when the row is composed.** Not a model, not a daemon.
`013` INC-F2 argues both at length and the argument is unchanged: collaborative filtering needs a
server collecting what everybody watched, which `FREEZE.md` §2 and §4.5 rule out; an on-device
model would learn from one household's few hundred watch events, where a scoring function does as
well and can explain itself. A daemon would be a process burning battery to precompute something
that takes milliseconds on demand.

**Hidden categories are treated differently by the two new rows, on purpose.** The popular row
searches the whole catalogue: it is about what a provider carries, and tidying a shelf away is not
a statement that the films on it stopped existing. The suggestions row does not: a suggestion is
the app proposing something unprompted, and proposing out of a shelf the viewer has put away is
the app arguing with them.

## What was built

| Layer | Change |
| :---- | :---- |
| `source/tmdb` | `TmdbClient.popular(kind)` against `/movie/popular` and `/tv/popular`, through the existing rate limiter; `PopularTitle` and `TmdbPopular`; `title`/`name` read off the search DTO |
| `core/database` | `PopularTitleEntity` (`kind` + `rank` as the key), `PopularTitleDao`, schema **18**, `MIGRATION_17_18` |
| `core/data` | `PopularTitlesRepository`, `RecommendationRepository`, `Recommender` (pure), `CatalogueIndex`, `ChannelRepository.channelsByIds` |
| `feature/browse` | `BrowseScope.FOR_YOU`, `BrowseUiState.extraRows`, `FeedRow` / `FeedRowItem`, `forYouParams()` |
| `app-tv` | `TvTab.FOR_YOU`, `TvForYouScreen` (moved from `TvRecentlyAddedScreen`), `TvRowStyle`, the rank numeral and the caption slot inside `TvPoster` |

**`CatalogueIndex` is why the two new rows share a pass over the catalogue.** Nothing in this app
stores a TMDB id against a channel — the join has always been a cleaned title, made by the same
rules on both sides — so both rows need the same index, and building two would have been two
sixty-thousand-title passes and two chances to drift apart.

**Three duplications were found and removed on the way**: two `toMediaKindOrNull`, two
`toTmdbKind`, and — in `019` Part A — the second `tryRequestFocus` this project has grown. All
three are the same failure: a small helper written by somebody who did not know the first one
existed.

## Acceptance criteria

1. The tab reads **For You** and sits where Recently Added sat, after Live and before Movies.
2. Its first row is Recently Added exactly as before, with the same two headings and the same
   thirty-day window.
3. Now popular holds at most five films and five series, each numbered by its position in TMDB's
   list for its own kind, and holds only titles this provider carries.
4. Now popular costs at most two requests in any seven days, and none at all without a key.
5. A refused fetch leaves the previous week's row standing.
6. You may like is per profile, names the watched title behind each tile, and never suggests
   something already watched.
7. Any row with nothing in it is not drawn.
8. Walking along any of the three rows does not move the screen vertically.
9. An existing installation upgrades without losing a channel, a favourite or a resume point.

## Testing

Automated, all on the JVM:

- `RecommenderTest` (new, pure) — no history and no genres both give nothing; the strongest genre
  wins; each suggestion names its cause; watched titles are excluded; the saturation penalty gets
  a second genre into a row against five of the first; a year-old taste is outweighed but not
  deleted; a title barely started counts for less than one watched through; the same input gives
  the same row twice running; both kinds are candidates.
- `PopularTitlesRepositoryTest` (new) — no key means no request; a six-day-old list is not
  refetched and an eight-day-old one is, once per catalogue; a refusal is never written down and
  leaves the held row standing; titles the provider does not carry are dropped and TMDB's own
  rank is kept; five of each are taken **after** the match; nothing matched is an empty answer.
- `MigrationTest` — 17 to 18 adds the table empty and leaves the catalogue where it was.
- `TvBrowseScrollStabilityTest` — three new cases: a ranked row, a captioned row, and a captioned
  row with a gap in it. This is the #008 guard and it is the reason the rank and the caption are
  shaped the way they are.
- `TvTabOrderTest` — the bar's order with the renamed tab, and that the position did not move
  with the name.

**A note on the coverage gate.** `./gradlew coverageAll` verifies `:source:m3u` and
`:source:xtream` only — the parser modules, per `AC-NFR-07` — so it says nothing about the code
in this round and passing it is not evidence about this work. Amendment 10's floor is a different
measurement from the one this repository gates on, and that gap is worth the author's attention
rather than a number invented here.

Manual: `docs/TESTING-REQUIRED.md` §A13. **Rows two and three cannot be certified from this
machine** — `docs/STOPPERS.md` S2, no working Xtream account — and row three additionally needs a
scanned catalogue and a real watch history, which is days of use rather than an afternoon.

## Open, and owned by the author

- **Whether to merge.** The branch carries `feat:` commits, so a merge to `main` publishes a
  release.
- **Whether the phone gets these rows.** Scoped out for this round, deliberately. The two new
  repositories are shared code and a phone screen would be UI work only.
- **Whether "five of each" survives contact with a real account.** If a provider carries almost
  none of TMDB's popular films but most of its series, the row will look lopsided in a way the
  cap cannot fix — and the answer would be a merged ranking, which needs the popularity score
  this deliberately does not fetch.
