# BUG-061: 0.27.0 sits on an empty profile chooser after the upgrade

Hotfix for **0.27.0**, reported by the owner on 2026-10-04: viewers with profiles could not get
past "who is watching", which showed no profiles, and could not update from there either.

## Problem & Motivation

After upgrading to 0.27.0, the chooser opened empty for anybody with a real Xtream catalogue. No
profile could be picked, nothing else could be reached, and closing and reopening the app changed
nothing. Viewers who had used the app for months were locked out of it, and some gave up on it.

## Environment

- Platform: both
- Source kind: Xtream, with a catalogue of tens of thousands of channels and films
- Version: 0.27.0 (database 24 → 26), upgraded from any earlier release

## Root cause

`MIGRATION_24_25` (`BUG-041`) rewrites every stored Xtream stream URL to a locator. It read the
URLs, then issued one `UPDATE … WHERE streamUrl = ? AND sourceId = ?` per URL. `channels` has no
index on `streamUrl`; the planner's best was the `sourceId` index, which matches the whole
catalogue. Each update therefore read every row of the source: **n titles cost n² row reads**.
For 50 000 titles that is over a billion, on a television's CPU and storage.

Room runs a migration inside one transaction on the first database access. Every query —
including `ProfileDao.observeAll`, which the chooser waits on — queued behind it, so the chooser
drew its empty state for as long as the upgrade took. A viewer who closed the app rolled the
transaction back, and the next launch started it again from the first row.

The way out was hidden too. The launch update offer (`029` #7) is composed inside the shell on
both apps, which is behind the profile gate, so the release that fixes this could not be offered
to anybody stuck in front of it.

## Scope

- Each table's rewrite runs behind a temporary index on the rewritten column, created before the
  updates and dropped after them, with one compiled statement re-bound per row. The work becomes
  linear. The index must not outlive the migration: Room checks the indices at 25 against the
  exported schema.
- The update offer is drawn over the profile chooser as well as the shell, on the phone
  (`LaunchUpdatePrompt`, from `ProfileGate`) and on the television (`TvUpdatePrompt` over
  `TvProfileScreen`).
- `MIGRATION_24_25` keeps its number and its result. A device stuck on 0.27.0 is still at 24,
  because the transaction never committed, and runs the fixed migration on its first launch of
  the new version. A device that finished the slow one is at 26 and runs nothing.

## Explicit Non-Scope

- A "getting your library ready" screen during a long upgrade. Worth doing, but the upgrade is
  now seconds long; that is a separate item.
- Moving the rewrite out of the migration into a background job. The point of `BUG-041` is that
  no password survives on disk once the app has started, and a job can be killed halfway.

## Acceptance Criteria

- Upgrading a database at 24 with 50 000 Xtream titles takes seconds, not minutes, and leaves no
  password in any stored URL (`StoredUrlMigrationTest`).
- The upgraded schema validates at 26 (`MigrationTest` on CI).
- With an update available and no profile chosen, the update offer appears on both apps.

## Verification

- The new `StoredUrlMigrationTest` case was run on CI against the 0.27.0 migration without the
  fix, where it fails on its time bound, and with the fix, where it passes.
- Robolectric's SQLite and `aapt2` cannot load on the development machine (a Windows Application
  Control policy), so the database and app tests for this item were run on CI only.
