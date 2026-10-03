# BUG-041 Manual Sweep

Needs a debug build, `adb`, and an Xtream source. Use a password that is easy to search for.

## 1. An upgrade leaves no password behind

1. On a build from before this fix: add the Xtream source, play a live channel, a film, and half an
   episode; attach a subtitle file to the episode.
2. Install this build over it and open it.
3. Pull the database (`adb shell run-as dev.quiblo.player.debug cat databases/quiblo.db > q.db`,
   `dev.quiblo.tv.debug` on the television) and search it:
   `strings q.db | grep -c <password>`.
4. **Expect:** 0.
5. **Expect:** the episode still offers Resume at the same position, and its subtitle is still
   attached.

## 2. Everything still plays

1. Play a live channel, a film and an episode.
2. **Expect:** all three play exactly as before.

## 3. A changed password needs no refresh to be used

1. Change the stored password (after `BUG-042`, by editing the source; before it, by re-adding is
   not the test — skip this step).
2. Play a channel without refreshing.
3. **Expect:** it plays with the new password.

## 4. A backup has no password

1. Export a backup after the upgrade.
2. **Expect:** the password appears nowhere in the file.
