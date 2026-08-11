**Feature Round of Quiblo — Search, Scan and Profiles**

Three features built in one session, plus three defects they uncovered. Everything here is
merged and green against the local gate; **none of it has been run as an acceptance sweep**,
and the device notes at the end say which parts can only be judged on hardware.

**Created:** 2026-08-09, against commit `b4cc312`. **Ships as:** unreleased — still `0.2.0`
(`versionCode` 2), which is a deliberate omission recorded in "What is left" below.

---

| Item | Platform | Status | Description |
| :---- | :---- | :---- | :---- |
| Search across every kind | TV | **Built** | One screen searching live, films and series at once, with a genre filter behind Advanced. Resting shape: the name and a centred field; asking a question moves it up and gives the panel to the answer. |
| Sources leaves the tab bar | TV | **Built** | Now reached from Settings. The bar is for what is done every evening, not for typing a URL on a remote once. |
| Catalogue metadata scan | Both | **Built** | One control in Settings that looks up every film and series in the catalogue, so genres and scores exist before anyone browses. |
| Local profiles | Both | **Built** | Who is watching, chosen at launch. Favourites and resume positions per profile; guest keeps nothing. |
| TMDB failures cached as missing titles | Both | **Fixed** | Every failure was `null`, and `null` was written down as "this title matches nothing" for a fortnight. |
| Panel rate limiter at double its documented rate | Both | **Fixed** | The token bucket let its balance stop at zero, so waits accrued a token the next caller spent for free. |
| `advanceUntilIdle` does not run an eagerly-shared flow | — | **Fixed in tests** | Reading `.value` after it saw stale data; the tests now await the value, as a caller would. |

## Why this document exists

The three features are small in description and wide in blast radius — search touched the
browse tile, the scan touched every call this app makes to a metadata service, and profiles
touched the primary key of two tables. Each also turned up something already wrong. Those
are recorded here because the fixes are more interesting than the features: two of them had
been shipping quietly for weeks, and both were in code written specifically to prevent the
thing it was failing to prevent.

## Search

One screen rather than a box on each of Live, Movies and Series. A viewer looking for a title
does not know which of the three their provider filed it under, and panels routinely list the
same film as a film and as a one-episode series — so a search that answers for one kind is a
search that appears to have found nothing.

Decisions worth keeping:

- **The results reuse `TvCategoryList` whole.** That is the composable
  `TvBrowseScrollStabilityTest` measures, and the modifier order inside its poster is the fix
  for #008. A second row implementation would be a second place for the shake to return
  unmeasured.
- **The genre chips sit under the field, not beside it.** A text field keeps left and right
  for the cursor, so the only way out is down — and down finds what is below. Beside it they
  could not be reached at all, which is the "hollow feature" shape this project has already
  deleted nine of.
- **The resting and working shapes are one composition, animated.** Swapping layouts would
  rebuild the text field on the first keystroke and take focus and the keyboard with it,
  mid-word.

## The catalogue scan

The genre filter is built from the metadata cache, and the cache fills a poster at a time as
somebody browses — so a viewer who has looked at four rows can filter by the genres of four
rows. The scan walks the lot: one request per distinct cleaned title, paced, resumable,
cancellable.

**It is one request per title rather than two** because a search hit now carries genres. TMDB
returns genre *ids* there and the names live behind a list endpoint; two calls per catalogue,
learned once, is the difference between an hour and two hours on a real account.

Three reductions before anything is asked: live channels never enter the work list, rows
collapse to distinct cleaned titles (one film in four qualities is one lookup), and anything
already cached and fresh is removed — which is what makes an interrupted scan resume and a
repeated scan free.

## Profiles

Favourites and resume positions belong to a person; playlists, player settings, categories and
the metadata key stay app-wide. **Guest is a row rather than a mode**, with the two tables
carrying a foreign key onto it, so deleting the row deletes the data by cascade instead of by
a routine somebody has to remember to call. It is deleted on leaving and again at every
startup, because a television is switched off at the wall.

No PIN. `docs/PLAN.md` §6 pairs profiles with parental controls; this is the first half
arriving without the second, deliberately — a control that appears to restrict but does not is
worse than no control.

## The three defects

### TMDB failures were cached as missing titles

`TmdbClient` returned `null` for a rate limit, a rejected key, an unreachable host and a
genuine no-match alike, and `TitleMetadataRepository` wrote that `null` down as a miss with a
fortnight's TTL. Invisible one poster at a time. Across a catalogue scan it would have been
catastrophic: one rate limit half way through and tens of thousands of rows would claim a film
nobody had successfully asked about does not exist — and because misses count as *answered*,
the search screen would then report a described catalogue with no genres in it.

**A cache may hold answers. It may never hold failures.**

### The panel rate limiter ran at twice its documented rate

`PanelRateLimiter` exists because this project got a user's account blocked twice. Its bucket
let the balance stop at zero rather than go negative, so the wait a throttled caller paid
accrued a token that the *next* caller found and spent immediately: requests left in pairs,
one per 200 ms against a limiter written, reviewed and trusted to allow one per 400 ms.

It survived because its test asserted one request's wait. The new test asserts the sustained
rate over twenty — **a pacing test that measures a single request cannot see this class of
bug.** It was found only because the equivalent limiter was being written for TMDB.

### `advanceUntilIdle` does not run an eagerly-shared flow

Nine profile tests failed for a reason unconnected to profiles: `.value` read after
`advanceUntilIdle()` held the previous value, because the coroutine behind
`stateIn(scope, Eagerly)` needs a real suspension rather than a scheduler advance. The tests
now await the value, which is both correct and what a caller does.

## What is left

- **No acceptance sweep has been run.** AC-TV-14/15 (search, sources), AC-META-01…06 (the
  scan) and AC-PROF-01…06 (profiles) are all written and all unrun.
- **The version was not bumped.** Everything above is still `0.2.0`/`versionCode` 2, so the
  only way to tell these builds apart on a device is `lastUpdateTime`. That should be settled
  before anything else is installed.
- **AC-PROF-05 is the one to run first.** The 10 → 11 migration adopts existing favourites and
  resume points into a profile named Default; it can only be judged on a device that already
  has favourites on it, and a fault there looks like an empty catalogue rather than a
  cosmetic bug.
- **The scan's pacing has never met the real service.** Eight requests a second and the
  handling of `Retry-After` are asserted on a fake clock against mocked responses. A stop
  saying the service asked us to slow down is a result worth reporting, not a bug.
- ~~**Open suggestion:**~~ **Decided 2026-08-11 — not in `1.0.0`.** A rate limit ends the scan,
  and it goes on ending it. Backing off for the interval the service names and carrying on would
  let the scan find its own safe speed, and that is a real improvement — for `1.1`.

  Three reasons for stopping instead, in the order that matters. **Stopping is the conservative
  behaviour**, and the scan already keeps what it found, so the cost of stopping is one press to
  start again rather than any lost work. **This project has had a user's account blocked twice**,
  and both times the app was not the cause — which is exactly why it must not become one.
  **A beta is the wrong place to start pushing through a refusal:** the pacing has never met the
  real service at all, so the first thing to learn is what the service actually says, and a scan
  that stops and reports is the instrument for learning it.

  `006` gate 5 asked for this to be decided rather than left open. It is decided.
