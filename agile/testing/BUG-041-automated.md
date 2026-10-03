# BUG-041 Automated Tests

## Tests written

| Test | Module | Asserts |
| --- | --- | --- |
| `nothing a load returns carries the username or the password` | `:source:xtream` | No stored stream URL contains the username, password or host |
| `nor does an episode, whose stream url is also its identity in history` | `:source:xtream` | `xtream:series/501.mp4` |
| `the url is built when it is played, from the credential store` | `:source:xtream` | Live, film and episode locators resolve to the panel URLs |
| `a password changed in the store is the one the next play uses` | `:source:xtream` | Resolution reads the store each time |
| `something that is not a locator is played as it is` | `:source:xtream` | A real URL passes through |
| `a malformed locator is not turned into a url` | `:source:xtream` | Empty, unknown type, nested path → null |
| `XtreamSourceTest` (5 assertions changed) | `:source:xtream` | Stored URLs are now locators — those assertions pinned the leak |
| `XtreamUrlRewriteTest` (4) | `:core:database` | Live drops credentials and extension; films and episodes keep their container; non-Xtream values untouched; hosts matched however typed |
| `StoredUrlMigrationTest` (5) | `:core:database` | Over the exported v24 schema: Xtream channels get locators while a playlist row keeps its URL; an episode's resume point, watch event and picked subtitle follow it with the position intact; no history table keeps the password; two old URLs for one episode become one row; an install with no Xtream source is untouched |

`StoredUrlMigrationTest` builds its tables from `schemas/…/24.json` in an in-memory database
instead of using `MigrationTestHelper`, whose open path fails on Windows (see the class KDoc).
`MigrationTest`'s end-to-end run covers the schema step to 25 in CI.

## Verified to fail without the fix

`MIGRATION_24_25` reduced to rewriting nothing and `StoredUrlMigrationTest` re-run on 2026-10-03:

```
StoredUrlMigrationTest > no history table keeps the password FAILED
StoredUrlMigrationTest > an old url and a newer one for one episode become one row, not a failed upgrade FAILED
StoredUrlMigrationTest > an xtream channel stores a locator, and a playlist row keeps its url FAILED
StoredUrlMigrationTest > history follows an episode to its locator, so its resume point survives FAILED
5 tests completed, 4 failed
```

On the source side, the five `XtreamSourceTest` assertions changed here asserted the old
credentialed URLs; against the new code they fail, and the new assertions fail against the old.

## Run result

`detektAll` clean; `./gradlew test` passes in every module except `:core:database`'s
`MigrationTest`, all 14 of whose tests fail on this Windows machine before and after this branch
with Room's driver rejecting a backslash path. CI on Linux runs them.
