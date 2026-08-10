**One Entry Per Title of Quiblo — duplicates, qualities and languages**

Item 13 of `docs/INC_AGILE.md`, which asks for its own document and earns it: one catalogue
entry per film, with its qualities and languages chosen inside the player.

**Created:** 2026-08-10, against commit `b7bcba4` on `main`.
**Ships as:** `1.1.0` at the earliest, behind a setting, and **it is the one item in the
increment that needs a `FREEZE.md` amendment when it lands** — see "Why this needs an
amendment" below.

**Depends on** [`012`](012%20Bug%20Round%20of%20Quiblo%20—%20Round%203.md) closing and on
[`013`](013%20Increment%20Round%20of%20Quiblo%20—%20the%20catalogue%20a%20viewer%20actually%20uses.md)
pass 3, whose language detector this shares.

---

## The problem, stated precisely

A panel lists one film several times. Four rows might read:

```
Interstellar 2014 1080p
INTERSTELLAR - 4K
Interstellar (2014) [AR]
interstellar   FHD MULTI
```

Four rows, one film, and a viewer scrolling a catalogue of 67,000 entries meets all four —
usually not adjacently, because they sort by whatever string the panel supplied. The intake
asks for one entry, with the four reachable from inside it.

**This is a grouping problem wearing a UI problem's clothes.** The interface is small: one card
instead of four, a version list on the detail screen, a switcher in the player. Everything hard
is in deciding that those four strings name the same film — and, far more importantly, that two
strings that look similar do **not**.

## The failure that governs the design

**A false merge hides content, and hidden content is invisible to the person it was hidden
from.** If "Dune (1984)" folds into "Dune (2021)", a viewer does not see a bug: they see a
catalogue that does not have the film they wanted, and they have no way to discover why. Every
decision below is made in that direction.

| Merge | Correct? | Why it is dangerous |
| :---- | :---- | :---- |
| `Dune 2021` + `DUNE FHD` | Yes | The easy case, and the one that makes the feature feel magic |
| `Dune (1984)` + `Dune (2021)` | **No** | Different films. The year is the only thing separating them |
| `Rambo 2` + `Rambo II` | **No** | Numerals and roman numerals both index sequels |
| `Spider-Man` + `Spider Man` | Yes | Punctuation only |
| `Batman` + `Batman Begins` | **No** | A prefix is not a title |
| `The Office US` + `The Office UK` | **No** | Different series, and the discriminator is two letters |

So: **a strict key, not a similarity score.** Fuzzy matching — edit distance, trigram overlap —
gets the first row right and the middle four wrong, and it fails silently. Two titles group only
when their normalised keys are *equal*.

## The key

```
groupKey = kind + ":" + normalisedTitle + ":" + year?
```

**`kind`** — films group with films. A panel that files a film as a one-episode series is a real
thing (`005` records it as a reason search covers all kinds at once), but folding across kinds
means folding an episode list into a film and it is not worth the blast radius.

**`normalisedTitle`** — lowercased, punctuation and repeated whitespace collapsed, and then a
list of tokens removed **only when they are whole tokens**:

- Quality: `4k`, `uhd`, `fhd`, `hd`, `sd`, `720p`, `1080p`, `2160p`, `hq`, `cam`, `ts`, `web`,
  `webdl`, `bluray`, `x264`, `x265`, `hevc`
- Language and treatment: `ar`, `en`, `fr`, `multi`, `sub`, `subbed`, `dub`, `dubbed`, `vo`,
  `vf`, `مترجم`, `مدبلج`
- Bracketed groups containing only the above

Whole tokens, because `hd` inside `Ghd` is a letter pair and `4k` inside a title is a title.
A token list is data, it lives in `:core`, and it grows as providers are met — which means it
is testable and correctable without touching the grouping logic.

**`year`** — kept, never stripped, and it is what makes the Dune row above safe. When one row
carries a year and another does not, they group only if nothing else claims that year: a bare
`Dune` beside `Dune 1984` and `Dune 2021` joins **neither**, and stands alone. That is the
conservative answer and it is deliberately the one that shows more rows rather than fewer.

**When a TMDB id is known for both rows, it wins outright.** After a scan most films have one,
and an id is an identity rather than an inference. The string key is what happens without one.

**One cleaner, one place.** `SearchRepository` already cleans titles to key the metadata cache,
and `013`'s autocomplete will suggest by cleaned title. If grouping uses a second cleaner, then
search, the metadata cache and the catalogue will disagree about what one film is, and the
disagreement will present as three unrelated bugs.

## Where the grouping happens

**At ingest, into a stored, indexed column — not at query time.** The catalogue is 67,000 rows
against a browse query that already has an index specifically so it can be fast; grouping in
Kotlin after the read puts the whole catalogue back on the main path and undoes that work.

- A `groupKey` column on `channels`, indexed alongside `(sourceId, kind, sortIndex)`.
- Computed by the source layer when a playlist is parsed, so both M3U and Xtream get it from
  one implementation.
- A schema bump with a one-off backfill for catalogues already stored. **Schema 10 already
  taught this cost**: the first launch after that upgrade built an index over the whole playlist
  and the old cold-start figures did not include it. Measure the backfill before shipping it,
  and do it once rather than on every launch.

The catalogue query then groups by `groupKey` and returns one row per group with a count. The
representative row is the highest quality available, so pressing play without opening the version
list does the obvious thing.

## What a group owns

This is the part that decides whether the feature is loved or reverted.

- **The resume position belongs to the group, not to the row.** Watching forty minutes in 1080p
  and then switching to 4K must continue at forty minutes. A position tied to the row makes the
  switcher lose the viewer's place, which is worse than the duplicates were.
- **A favourite belongs to the group.** Favouriting a film once must not leave three unfavourited
  copies of it.
- Both are per profile today, keyed by channel id. Re-keying them onto the group is a migration
  in the same schema bump, and it is the half most likely to go wrong quietly — `AC-PROF-05` is
  the precedent, and its lesson is that a migration fault presents as an empty screen rather than
  as an error.
- **Nothing is ever deleted.** Grouping is a view over rows that all still exist. Turning the
  setting off restores every row immediately, with no re-parse, because nothing was thrown away.

## What the viewer sees

- **The catalogue**: one card, with a small badge when a group has more than one version — the
  count is the honest signal that nothing was hidden.
- **The detail screen**: a **Versions** list — quality, language, and which one plays by default.
  Language before quality, because an Arabic dub and the English original are not two qualities
  of the same experience and sorting them as if they were is its own defect.
- **The player**: a switcher in the same menu as audio and subtitle tracks (`013` INC-F12), which
  keeps position across the switch.
- **The setting**: `Settings → Catalogue → Merge duplicate titles`, **off by default in the
  release that introduces it.** Turn it on by default only after a release with no false-merge
  reports. A viewer who never opens Settings should not have their catalogue silently reshaped by
  an upgrade.

## What INC-F14 borrows from this

`013`'s language filter and this feature both need to know what language a title is in, and both
answer it from the same three signals: the script of the title, the metadata service's
`original_language`, and the provider's category name. **Build the analyser once, in `:core`,
returning a language guess and a confidence.** This document consumes it to *label* the versions
inside a group; INC-F14 consumes it to *hide* rows. Labelling wrongly is a cosmetic fault;
hiding wrongly is the failure this whole document is written around — which is why INC-F14 hides
only on signal 1, the one that is near-certain when it fires.

## How this gets tested

**Against a fixture corpus, written before the grouping code.** `PLAN.md` M1 made the M3U parser
work by building a corpus of deliberately broken playlists first, and it is the reason that module
has never been the source of a release bug. This is the same shape of problem: dirty strings from
providers who agree on nothing.

The corpus is **synthetic** — AC-LEGAL-04 forbids real provider data anywhere in the repository,
including fixtures — and it carries every row in the danger table above plus every provider naming
pattern met so far. **Each of the "No" rows is a test that asserts two groups**, and those are the
tests that matter: the ones asserting a merge will pass from the first day, and the ones asserting
a *separation* are what stop a future token being added to the strip list from quietly folding a
franchise together.

## Why this needs an amendment

`FREEZE.md` §7 defines success for `1.0.0` as a viewer who "browses categorised Live/VOD/Series
content". This feature changes what browsing shows: rows a provider supplied are no longer rows on
screen. Every other item in the increment adds a screen or a control; this one changes the meaning
of the catalogue, and `FREEZE.md` §1 requires that to be a dated decision rather than a quiet edit.

The amendment is written when it ships, and it says three things: grouping is a view and nothing is
deleted, it is off unless turned on, and a group states how many versions it holds.

## Exit criteria

| | Passes when |
| :---- | :---- |
| Correctness | Every "No" row in the danger table remains two entries, on a corpus test, with the setting on |
| Coverage | A real 67k catalogue collapses its quality duplicates; the count is reported, not estimated by eye |
| Reversibility | Turning the setting off restores every original row with no re-parse and no data loss |
| Position | Switching version mid-film continues within a second of where it stopped |
| Favourites | A favourited group is favourited exactly once, and survives the migration that introduced grouping |
| Performance | The catalogue query is no slower than before grouping at 67k rows, and the one-off backfill is measured on the Haier and on an Android 11 device |
| Honesty | A grouped card says how many versions it holds; a group of one looks exactly as it does today |
