**The Cache and the Detail Line of Quiblo — an hour that went missing, a row that claimed too much, and four facts nothing was showing**

One session on the television, four requests, and only the first of them is a defect. They
arrived together and they ship together.

**Created:** 2026-08-15, against `fa0747b` — the head of `fix/tv-live-guide-blank-and-silent`,
which carries `017`'s two branches and is not on `main` yet.
**Ships as:** `1.0.0` work. Part A is `fix:`, Parts B and C are `feat:`.

**Branches:** `fix/tmdb-cache-survives-restart`,
`feat/tv-recently-added-window-and-badges`, `feat/detail-year-and-duration`, integrated on
`build/018-integration`.

---

## Part A — an hour of scanning that did not survive a restart

### The report

> when I save info from TMDB then i update the app or shutdown the tv it loses the data that i
> downloaded thorugh an 1hour !! that is not acceptable

### What was actually lost

Asked which of four symptoms were seen, the answer was two of them: **the scan reports zero
progress again**, and **the search screen's coverage reads 0%**. Not the posters, not the saved
key.

That pair is diagnostic. Exactly two things in the app read the whole cache table:

- `TitleMetadataScanner.workList`, which subtracts `TitleMetadataRepository.freshlyCachedKeys()`,
  and that method filtered rows by the fourteen-day TTL.
- `SearchRepository.genreIndex`, which counts `allGenreRows()` with no TTL at all.

So two mechanisms produce that pair, and without the device neither can be ruled out:

**A1 — the rows are gone.** Room commits into a write-ahead log and folds it into the database
file later. A television cut at the mains, or an emulator killed rather than closed, loses
whatever the log still held. `quiblo-tv` is configured `fastboot.forceFastBoot=yes`, so an
unclean exit discards everything since the last snapshot.

**A2 — the rows are there and every one of them counts as stale.** A device whose clock is wrong
while the scan runs and right after it reboots stamps every row in the past and expires the lot
at once. Set-top boxes without a battery-backed clock and emulators resumed from old snapshots
both do this.

**A third fault, found on the way and independent of both:** `setApiKey` called `dao.clear()`
unconditionally, so pressing Save on a key that was already saved threw away every lookup behind
it.

### What was done

1. **Answered is not the same as fresh.** `answeredKeys` — the renamed `freshlyCachedKeys` —
   ignores age entirely. A title that has been asked about has been asked about, and re-walking
   thirty thousand of them is the reported failure. The TTL keeps its real job: deciding when one
   title is refetched for a screen that opens it. This closes A2 outright.
2. **`DurabilityCheckpoint`**, mirroring `TransactionRunner` in shape and for the same stated
   reason. It runs `PRAGMA wal_checkpoint(TRUNCATE)`; the scan calls it every 250 answers and once
   however it ends, cancellation included and under `NonCancellable` so the cancelled path is not
   a no-op. This bounds A1 to the last interval rather than the whole hour.
3. **`setApiKey` clears only on a genuine change.** Whitespace is not a change and neither is the
   same key typed twice.
4. **"Held now: N titles looked up"** on both settings screens, from one `COUNT(*)`. Read before
   a restart and after one, that number separates A1 from A2 in a glance — which is the fact
   neither app could report while this was being diagnosed.

### What this cannot fix, stated plainly

If the loss is an install that wipes app data — a differently signed build, an installer that
uninstalls first — or an emulator killed without saving its snapshot, then nothing in this
repository can help, because the app never got the chance to write anything. A9.2 in
`docs/TESTING-REQUIRED.md` is written to catch exactly that case rather than blame the app for it.

### Deliberately not done

**The metadata cache is not in the backup file.** It is the right answer if the loss turns out to
be a reinstall, and it is a backup-format version bump with its own migration story. Its own item.

---

## Part B — Recently Added: a window, a fallback, and a badge

### The three changes

**A thirty-day window.** The row was the newest forty titles whatever their age. A cap on count
alone leaves a service that added forty films last March answering "what is new" with last March,
for good.

**A fallback for a playlist that dates nothing.** The end of the provider's own list —
`sortIndex DESC` — twenty films and twenty series, interleaved.

**A kind badge**, Movie or Series, in the corner opposite the score.

### The conflict with `017`, declared

`017` decided:

> A source that supplies no dates gains no invented ordering.

and rejected a locally recorded "first seen" stamp because everything looks new on a first import
and a re-import resets it. **Part B overrides that decision**, at the author's request, and the
fallback it uses is a different one: the provider's own list order, not a date this app invented.

What keeps it honest is the heading. A dated row is titled "Recently added"; a fallback row is
titled **"Latest in your playlist"**. Where something sits in a playlist is not a date, and the
screen says which of the two it is looking at.

### Decisions worth keeping

**The choice is made on whether the source dates *anything*, not on whether the window is empty.**
`observeHasAddedDates` is a separate observed query for that reason: a panel that has added
nothing for six weeks still dates its catalogue, and falling back for it would replace an empty
row that is true with a full one that is not.

**One query per kind, interleaved in Kotlin.** A single query across both returns forty films and
no series on any catalogue that lists its films last, which is most of them.

**The badge is an overlay inside the artwork**, never a line in the column beneath it. A focused
tile whose measured bounds change is #008 — four wrong analyses and a wobble reported from the
sofa — and `TvBrowseScrollStabilityTest` now walks a badged row to hold that line.

**No season or episode number on the badge.** The catalogue lists series, not episodes: a panel's
`get_series` returns one row per series and episode numbers live only inside `get_series_info`,
one request per series. That was offered and refused, and the request budget is why.

---

## Part C — year, length, and episode length

### Where each fact comes from

| Fact | First | Then | Last |
| :---- | :---- | :---- | :---- |
| Film year | `VodDetails.releaseDate` | TMDB `release_date` | — |
| Film length | `VodDetails.durationSeconds` | TMDB `runtime` | — |
| Series year | Xtream series `releaseDate` | TMDB `first_air_date` | — |
| Episode length | Xtream `duration_secs`, else `duration` | — | — |

The provider comes first everywhere, on the rule that already governs artwork and plots: a panel
is describing the file it is about to serve, and a metadata service is describing whichever cut it
holds. On an Xtream account none of this costs a single extra request — every field arrives inside
`get_vod_info` and `get_series_info`, which the detail screens already call.

### Decisions worth keeping

**A year, not a date.** The television printed `2021-10-22`. Nobody choosing a film needs the day
of the month, and the line width it takes is what the length now uses.

**A series has no running time.** TMDB offers an average episode length and this app does not show
it: it is not the length of anything a viewer is about to watch.

**Both episode duration fields are read.** Panels split between `duration_secs` and a written
`00:47:15`, and a series whose every episode says nothing looks like a provider that supplies no
lengths rather than like an app looking for the wrong spelling. Zero means "not measured" in both
and becomes null, because `0m` on a row states something else.

**`releaseYearIn` lives in `:core:model`.** A source module reading a panel's field and a screen
reading a film's date need the same answer out of the same inconsistent strings — `2021-10-22`,
`22/10/2021`, `October 22, 2021`, `0000-00-00`, and a title whose only four digits are its
resolution. Two copies of "which four digits are the year" is two places to disagree.

**`runtimeLabel` lives in `:feature:browse`**, beside `TitleFacts`, for the reason that file
already gives: both apps draw this, and a length written two ways in one product reads as two
products.

### Schema 17

Two nullable columns on `title_metadata`, added in place. **Nothing is rewritten and nothing is
dropped** — deliberately the opposite of `MIGRATION_11_12`, which emptied this table and had its
reasons at the time. An upgrade that costs somebody an hour of scanning is the complaint Part A
starts from, and `MigrationTest` now asserts a cached row survives 16→17 with its overview and its
score intact.

Existing rows gain both facts as null and fill them in when next fetched. For an Xtream account
that is invisible: the panel's own fields are preferred and are always there.

---

## What was tested, and what was not

**On the JVM, and green.** The scanner counts a stale row as answered; a cancelled scan still
flushes; an unchanged key leaves the cache alone and a changed one empties it; the dated query
honours the window and the fallback query returns the tail per kind; one dated row makes a source
a dated source; the badge does not move a focused tile; 16→17 keeps its rows; the year parser
takes `22/10/2021` and refuses `1080p`; episode lengths are read from either field and a zero is
not a length.

**Not tested anywhere, and it cannot be from here.** Every symptom in this document was reported
from a room with a television in it. `docs/TESTING-REQUIRED.md` §A9 is the list, and A9.1 — the
number before switching off and the number after — is the one that decides whether Part A fixed
the thing that was actually happening.
