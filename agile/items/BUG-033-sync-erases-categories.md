# BUG-033: the scheduled sync erases every category

## Problem & Motivation

After the scheduled catalogue sync ran, a household saw its entire catalogue collapsed into a
single category named `__ungrouped__`. Every channel, film and series was in it. Nothing
recovered on its own; the only way out was opening Settings and pressing Refresh by hand.

Categories are not a table in this app. They are `channels.groupTitle` — `ChannelDao` groups by
that column to produce every category list on every screen — so a sync that writes the wrong
`groupTitle` does not merely mislabel rows, it destroys the whole structure. The hidden-categories
setting keys off the same string, so a viewer's hidden categories stop matching anything too.

## Environment

- Platform: both, but reported on Android TV (`:app-tv`)
- Source kind: Xtream Codes panel
- Trigger: `CatalogueSyncWorker`, which runs unattended every four days
- Symptom: one category, `__ungrouped__`, holding everything
- Recovery: a manual refresh from Settings

## Root cause

`XtreamSource.categories()` turned a failed category request into an empty list and let the load
continue. `titleFor()` then returned `Category.UNGROUPED_TITLE` for every stream, `assemble()`
still reported `SourceResult.Success`, and `SourceRepository.store()` wrote the result over a
catalogue that had been correct.

`isBlocked()` did not protect it. It is only consulted after live has already been mapped — the
live mapping was an eagerly evaluated argument — and it is only ever set for `ProviderBlocked`. A
timeout, a 502, or a rate-limit answer that is not the panel's firewall status passed straight
through.

It surfaced under the scheduled sync rather than under a manual refresh because that one walks
every source back to back with nobody watching, and panels rate-limit. The category calls are the
ones that lose.

## Scope

- A category list that fails, for a stream list that is not empty, fails the whole load
- The failure carries the panel's own error rather than being reported as a block
- Regression tests that fail without the fix

## Explicit Non-Scope

- The sync interval, and how much is written on each run — `FEAT-031`
- The M3U source, which carries its grouping inline and cannot reach this state
- Any change to how a manual refresh behaves

## Acceptance Criteria

- Live streams load, live categories fail → the load fails and the stored catalogue is untouched
- Film streams load, film categories fail → the load fails
- An account with no films is unaffected by its film category endpoint failing
- A load whose categories all answer still groups every channel as the provider intends
