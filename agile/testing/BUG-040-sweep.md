# BUG-040 Manual Sweep

Needs a debug build and `adb` to look inside the database and DataStore.

## 1. Deleting a profile takes its settings and opinions

1. Make a profile "Test". As Test: change the theme, hide a tab, mark two titles "not for me", and
   let For You fill.
2. `adb shell run-as dev.quiblo.player.debug cat files/datastore/player_settings.preferences_pb | strings`
   (`dev.quiblo.tv.debug` on the television)
   — note the `@<id>` keys for Test.
3. Delete Test from Settings → Manage profiles.
4. **Expect:** none of Test's `@<id>` keys remain; `SELECT COUNT(*) FROM title_opinions WHERE
   profileId = <id>` and the same on `feed_rows` are 0.
5. **Expect:** another profile's theme, tabs and opinions are exactly as they were.

## 2. A guest leaves nothing

1. Start a guest session; change the theme; mark a title "not for me".
2. Switch profile (ending the guest).
3. **Expect:** no keys for the guest's id; no opinion rows for it.
4. Repeat, but force-stop the app instead of switching. Reopen. **Expect:** the same.

## 3. An upgrade clears old orphans

1. On a build from before this fix, delete a profile that had changed settings.
2. Install this build over it and open the app.
3. **Expect:** the deleted profile's `@<id>` keys are gone after the first launch, and every
   surviving profile's settings are unchanged — including the plain, unscoped values.
