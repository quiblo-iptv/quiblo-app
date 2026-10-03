# BUG-040: deleting a profile, or ending a guest session, leaves its data behind

Audit item **PR-3**.

## Problem & Motivation

Now that a profile can be deleted (`FEAT-039`), the delete has to do what its confirmation says:
take the profile's favourites, continue watching **and settings** with it. Three things survived:

- `feed_rows` — the remembered For You rows — for that profile
- `title_opinions` — its "not for me" marks. For a guest, these outlived the session they were said
  in, against `AC-PROF-04`'s promise that a guest leaves nothing behind.
- Every per-profile preference (`theme_mode@<id>`, `hidden_tabs@<id>`, …) in DataStore. A guest
  session gets a new id each time, so on a television used by visitors these only accumulated.

Not a leak between people: profile ids are `AUTOINCREMENT` and never reused. Orphaned data, and a
broken promise to guests.

## Environment

- Platform: both
- Trigger: `ProfileRepository.delete`, `endGuestSessions` (startup), `signOut` from a guest,
  `startGuestSession` replacing a guest

## Root cause

`FeedRowEntity` and `TitleOpinionEntity` have no foreign key to `profiles` — deliberately, for the
same reason `feed_rows` has none to `sources` — so the cascade that removes favourites and resume
points cannot reach them. Nothing removed them explicitly, and nothing ever removed scoped keys.

## Scope

- `FeedRowDao.clearForProfile`, `TitleOpinionDao.clearForProfile`, `ProfileDao.guestIds/allIds`
- `ProfileLeftovers`: rows cleared in the same transaction as the profile row; preferences cleared
  straight after (DataStore cannot join a database transaction)
- `ProfileScopedStore` on `PlayerSettingsStore` and `ChannelLogoStore`: `clearProfile(id)` and
  `clearProfilesOtherThan(living)`
- Guest end, by any route, clears the guest's rows and preferences
- Startup clears preferences of any profile that no longer exists — the ones deleted before this
  fix, and any delete a crash cut short between the row and the preferences

## Explicit Non-Scope

- Watch events: `WatchEventEntity` already cascades from `profiles`
- Making `feed_rows` and `title_opinions` cascade by foreign key. That is a table rebuild (schema
  25) for something an explicit delete does, and `SourceRepository.deleteSource` already sets the
  explicit pattern this follows.

## Acceptance Criteria

- Deleting a profile removes its `feed_rows`, its `title_opinions` and its scoped preferences, and
  nobody else's
- Ending a guest session — by leaving, or at the next startup — does the same for the guest
- An install upgraded with orphaned `@<id>` keys has them cleared at the next launch
- Preferences from before profiles owned settings (no `@`) are never touched
