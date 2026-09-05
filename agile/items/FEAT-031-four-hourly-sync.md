# FEAT-031: the catalogue is re-read every four hours, and cheaply

## Problem & Motivation

The scheduled sync ran every four days. A provider adds a film and the household cannot see it
for most of a week — on a device whose whole purpose is to show what the provider carries.

Four days was never a measured number. `024` recorded it as a guess, in its own words: "a guess
about how often a provider adds things, made" without evidence. The guess was wrong in the
direction that matters.

Making it six times more frequent is not free, though, and the cost falls in exactly the place
that has already hurt: every run spent seven requests per source against a panel that answers
being asked too often with a block — which is how `BUG-033` erased a household's categories.

## Environment

- Both apps. The schedule is one per installation, not per profile.
- `CatalogueSyncWorker`, registered by `SyncScheduler`.

## Scope

- Four hours by default, and a setting so the viewer can choose 4 / 8 / 12 / 24
- A light pass: the grouping requests are spent only when the stream lists have changed
- Only rows that differ are written, rather than the whole catalogue every time
- Carrying installs already out there off the four-day schedule

## Explicit Non-Scope

- Category-scoped fetching (`category_id` on `get_vod_streams`). Smaller requests, more of them,
  which is the wrong trade against a panel that rate-limits.
- The M3U source's freshness. It carries no `added`, so it has nothing cheap to compare; an ETag
  pass is its own item.
- `PopularTitlesWorker`, which reads a list this project publishes rather than the viewer's
  provider. How often it runs is not a decision about their account.

## Acceptance Criteria

- A fresh install syncs the catalogue every four hours
- An install upgrading from a four-day build moves to the new interval rather than keeping it
- Choosing a new interval in Settings takes effect without waiting for the old one to elapse
- Opening the app does *not* restart the interval
- A source that has not changed costs four requests and writes nothing
- A source that has changed writes only the rows that differ
